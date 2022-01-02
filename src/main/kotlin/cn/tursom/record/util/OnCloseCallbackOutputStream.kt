package cn.tursom.record.util

import java.io.OutputStream

class OnCloseCallbackOutputStream(
  private val os: OutputStream,
  private val onCloseCallback: () -> Unit,
) : OutputStream() {
  override fun close() {
    os.close()
    onCloseCallback()
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