package cn.tursom.record.provider

import cn.tursom.channel.enhance.BufferedChannelReader
import cn.tursom.core.buffer.ByteBuffer

interface LiveProvider : BufferedChannelReader<ByteBuffer>
