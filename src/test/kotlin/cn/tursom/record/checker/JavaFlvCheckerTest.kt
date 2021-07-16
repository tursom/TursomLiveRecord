package cn.tursom.record.checker

import cn.tursom.core.pool.HeapMemoryPool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File


class JavaFlvCheckerTest {
  @Test
  fun test(): Unit = runBlocking {
    val pool = HeapMemoryPool(32 * 1024, 4)

    // val file = File("C:\\Users\\tursom\\Downloads\\盐咪yami录播-2021-07-16 02-33-20.flv").inputStream()
    val file = File("C:\\Users\\tursom\\Downloads\\盐咪yami录播-2021-07-16 04-24-09.flv").inputStream()
    // val file = File("C:\\Users\\tursom\\Downloads\\o.flv").inputStream()
    // val out = File("o.flv").outputStream()
    // out.create()

    val flvChecker = JavaFlvChecker()

    launch(Dispatchers.IO) {
      while (true) {
        val buffer = flvChecker.read()
        // while (buffer.isReadable) {
        //   buffer.writeTo(out)
        // }
        buffer.close()
      }
    }

    while (true) {
      val buffer = pool.get()
      buffer.put(file)
      flvChecker.put(buffer)
    }
  }
}
