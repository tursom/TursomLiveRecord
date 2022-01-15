package cn.tursom.record.util

import java.io.OutputStream

class LazyOutputStream(
  provider: () -> OutputStream,
) : OutputStream() {
  private val os by lazy(provider)
  override fun close() {
    os.close()
  }

  override fun flush() {
    os.flush()
  }

  override fun write(b: Int) {
    os.write(b)
  }

  override fun write(b: ByteArray) {
    os.write(b)
  }

  override fun write(b: ByteArray, off: Int, len: Int) {
    os.write(b, off, len)
  }
}