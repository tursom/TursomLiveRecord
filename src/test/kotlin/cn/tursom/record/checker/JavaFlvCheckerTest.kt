package cn.tursom.record.checker

import cn.tursom.core.pool.HeapMemoryPool
import cn.tursom.core.reflect.getType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File


class JavaFlvCheckerTest {
  private var f: Int = 1

  @Test
  fun testNothing() {
    println(f.javaClass)
    println(getType<Int?>())

    val list: List<String> = intArrayOf(1, 2, 3).map {
      it.toString()
    }
  }

  @Test
  fun test(): Unit = runBlocking {
    val pool = HeapMemoryPool(32 * 1024, 4)

    val file = File("E:\\Downloads\\盐咪yami录播-2021-07-16 04-24-09.flv").inputStream()
    val out = File("E:\\Downloads\\o.flv").outputStream()
    // out.create()

    val flvChecker = JavaFlvChecker()

    launch(Dispatchers.IO) {
      while (true) {
        val buffer = flvChecker.read()
        while (buffer.isReadable) {
          buffer.writeTo(out)
        }
        buffer.close()
      }
    }

    repeat(2048) {
      //while (true) {
      val buffer = pool.get()
      buffer.put(file)
      flvChecker.put(buffer)
    }
  }
}
