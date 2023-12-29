package cn.tursom.record.provider

import cn.tursom.core.util.ShutdownHook
import cn.tursom.core.buffer.ByteBuffer
import cn.tursom.core.buffer.impl.NettyByteBuffer
import cn.tursom.core.pool.HeapMemoryPool
import cn.tursom.core.pool.MemoryPool
import cn.tursom.log.impl.Slf4jImpl
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.CompositeByteBuf
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketCloseStatus
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler
import io.netty.handler.logging.LoggingHandler
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.IOException
import java.net.URI
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.*


@Suppress("MemberVisibilityCanBePrivate", "CanBeParameter")
open class NettyLiveProvider(
  val log: Boolean = false,
  val compressed: Boolean = true,
  private val headers: Map<String, String>? = null,
  private val dataChannel: Channel<ByteBuffer> = Channel(32),
  var onException: suspend (e: Exception) -> Unit = {},
) : LiveProvider {
  companion object : Slf4jImpl() {
    private val defaultMemoryPool: MemoryPool = HeapMemoryPool(1, 1)
    private val threadId = AtomicInteger()
    private val group: EventLoopGroup = NioEventLoopGroup(0, ThreadFactory {
      val thread = Thread(it, "WebSocketClient-${threadId.incrementAndGet()}")
      thread.isDaemon = true
      thread
    })
  }

  inner class NettyLiveProviderInboundHandler(
    private val cont: Continuation<String?>,
  ) : SimpleChannelInboundHandler<HttpObject>() {
    private var compositeByteBuf: CompositeByteBuf? = null
    private var throwCloseException: Boolean = false

    @OptIn(DelicateCoroutinesApi::class)
    override fun channelInactive(ctx: ChannelHandlerContext) {
      compositeByteBuf?.release()
      if (throwCloseException) GlobalScope.launch {
        onException(IOException())
      }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun channelRead0(ctx: ChannelHandlerContext, msg: HttpObject) {
      if (compositeByteBuf == null) {
        compositeByteBuf = ctx.alloc().compositeBuffer(256)
      }
      val compositeByteBuf = this.compositeByteBuf!!
      when (msg) {
        is HttpResponse -> {
          //logger.info("http response: {}", msg)
          when (msg.status()) {
            HttpResponseStatus.OK -> {
              throwCloseException = true
              cont.resume(null)
            }
            HttpResponseStatus.MOVED_PERMANENTLY, HttpResponseStatus.FOUND -> {
              val movedLocation = msg.headers()[HttpHeaderNames.LOCATION]
              logger.debug("live location moved to {}", movedLocation)
              ctx.close().addListener {
                cont.resume(movedLocation)
              }
            }
            else -> {
              ctx.close().addListener {
                cont.resumeWithException(IOException(msg.status().toString()))
              }
            }
          }
        }
        is HttpContent -> {
          val byteBuf = msg.content()
          if (byteBuf.readableBytes() == 0) {
            return
          }
          byteBuf.retain()
          compositeByteBuf.addComponent(byteBuf)
          compositeByteBuf.readerIndex(compositeByteBuf.readerIndex() + byteBuf.readerIndex())
          compositeByteBuf.writerIndex(compositeByteBuf.writerIndex() + byteBuf.writerIndex())

          if (compositeByteBuf.numComponents() == compositeByteBuf.maxNumComponents()) {
            logger.debug("provide data: {}", compositeByteBuf)
            val provideByteBuffer = NettyByteBuffer(compositeByteBuf, autoClose = true)
            this.compositeByteBuf = ctx.alloc().compositeBuffer(256)
            runBlocking {
              dataChannel.send(provideByteBuffer)
            }
          }
        }
      }
    }
  }

  override val memoryPool: MemoryPool = defaultMemoryPool

  private val coroutineScope = CoroutineScope(EmptyCoroutineContext)

  var ch: SocketChannel? = null
    internal set
  var closed: Boolean = false
    private set

  private val hook = ShutdownHook.addSoftShutdownHook {
    close()
  }

  suspend fun startRecord(url: String) {
    val uri: URI = URI.create(url)

    val scheme = if (uri.scheme == null) "http" else uri.scheme

    if (!"http".equals(scheme, ignoreCase = true) && !"https".equals(scheme, ignoreCase = true)) {
      //System.err.println("Only http(s) is supported.")
      throw Exception("Only http(s) is supported.")
    }

    val host = if (uri.host == null) "127.0.0.1" else uri.host
    val port = if (uri.port == -1) {
      when {
        "http".equals(scheme, ignoreCase = true) -> 80
        "https".equals(scheme, ignoreCase = true) -> 443
        else -> -1
      }
    } else {
      uri.port
    }


    @Suppress("BlockingMethodInNonBlockingContext")
    val sslCtx = if ("https".equals(scheme, ignoreCase = true)) {
      SslContextBuilder.forClient()
        .trustManager(InsecureTrustManagerFactory.INSTANCE).build()
    } else {
      null
    }

    val movedUrl = suspendCoroutine<String?> { cont ->
      val bootstrap = Bootstrap()
        .group(group)
        .channel(NioSocketChannel::class.java)
        .handler(object : ChannelInitializer<SocketChannel>() {
          override fun initChannel(ch: SocketChannel) {
            this@NettyLiveProvider.ch = ch
            ch.pipeline().apply {
              if (log) {
                addLast(LoggingHandler())
              }
              if (sslCtx != null) {
                addLast(sslCtx.newHandler(ch.alloc(), host, port))
              }
              addLast(HttpClientCodec(4096, 8192, 256 * 1024))
              if (compressed) {
                addLast(WebSocketClientCompressionHandler.INSTANCE)
              }
              addLast(NettyLiveProviderInboundHandler(cont))
            }
          }
        })

      val connect = bootstrap.connect(uri.host, port)
      connect.addListener {
        //System.err.println(uri)
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri.toURL().file)
        val httpHeaders = request.headers()
        mapOf(
          "Accept" to " */*",
          "Accept-Encoding" to "gzip, deflate, br",
          "Accept-Language" to "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7,ja;q=0.6",
          "Connection" to "keep-alive",
          "Origin" to "https://live.bilibili.com",
          "Referer" to "https://live.bilibili.com/",
          "sec-ch-ua" to "\" Not;A Brand\";v=\"99\", \"Google Chrome\";v=\"91\", \"Chromium\";v=\"91\"",
          "sec-ch-ua-mobile" to "?0",
          "Sec-Fetch-Dest" to "empty",
          "Sec-Fetch-Mode" to "cors",
          "Sec-Fetch-Site" to "cross-site",
          "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
        ).forEach { (k, v) ->
          httpHeaders[k] = v
        }
        httpHeaders[HttpHeaderNames.HOST] = host
        httpHeaders[HttpHeaderNames.CONNECTION] = HttpHeaderValues.KEEP_ALIVE
        httpHeaders[HttpHeaderNames.CONTENT_LENGTH] = 0
        headers?.forEach { (k, v) ->
          httpHeaders[k] = v
        }

        ch!!.writeAndFlush(request)
      }
    }
    if (movedUrl != null) {
      startRecord(movedUrl)
    }
  }

  fun close(reasonText: String? = null): ChannelFuture? {
    if (reasonText == null) {
      ch?.writeAndFlush(CloseWebSocketFrame())
    } else {
      ch?.writeAndFlush(CloseWebSocketFrame(WebSocketCloseStatus.NORMAL_CLOSURE, reasonText))
    }?.addListener(ChannelFutureListener.CLOSE)
    return ch?.closeFuture()
  }

  override suspend fun read(pool: MemoryPool, timeout: Long): ByteBuffer {
    return dataChannel.receive()
  }

  override fun close() {
    ch?.close()
    coroutineScope.cancel()
  }
}