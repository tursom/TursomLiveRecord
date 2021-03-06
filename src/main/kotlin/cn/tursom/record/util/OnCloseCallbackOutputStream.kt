package cn.tursom.record.util

import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

class OnCloseCallbackOutputStream(
  private val os: OutputStream,
  private val onCloseCallback: () -> Unit,
) : OutputStream() {
  private val closed = AtomicBoolean()

  override fun close() {
    os.close()
    if (closed.compareAndSet(false, true)) {
      onCloseCallback()
    }
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