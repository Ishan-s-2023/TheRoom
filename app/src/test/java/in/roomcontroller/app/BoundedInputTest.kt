package `in`.roomcontroller.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream

class BoundedInputTest {
    @Test fun acceptsExactlyTheLimitAndPreservesUtf8() {
        val bytes = "Room \uD83C\uDFB5".toByteArray(Charsets.UTF_8)
        assertEquals("Room \uD83C\uDFB5", ByteArrayInputStream(bytes).readBounded(bytes.size))
    }

    @Test fun rejectsOversizedBackupBeforeReadingTheWholeFile() {
        val input = ByteArrayInputStream(ByteArray(100000))
        assertThrows(IllegalArgumentException::class.java) { input.readBounded(16000) }
        assertEquals(83999, input.available())
    }

    @Test fun acceptsEmptyInputForParserToValidate() {
        assertEquals("", ByteArrayInputStream(byteArrayOf()).readBounded(10))
    }

    @Test fun stopsAtExpectedContentLengthBeforePostBodyTlsReset() {
        val payload = """{"ok":true,"runtime":{"version":"2.0.1-wled-room"}}""".toByteArray(Charsets.UTF_8)
        var postBodyReadAttempted = false
        val stream = object : InputStream() {
            var pos = 0
            override fun read(): Int = error("unused")
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (pos >= payload.size) {
                    postBodyReadAttempted = true
                    throw IOException("Connection reset by peer without TLS close_notify")
                }
                val n = minOf(len, payload.size - pos)
                System.arraycopy(payload, pos, b, off, n)
                pos += n
                return n
            }
        }
        val result = stream.readBounded(150_000, expectedContentLength = payload.size)
        assertEquals("""{"ok":true,"runtime":{"version":"2.0.1-wled-room"}}""", result)
        assertTrue(!postBodyReadAttempted)
    }

    @Test fun rejectsTruncatedResponseWhenFewerBytesThanContentLengthArrive() {
        val truncated = """{"ok":true,"runtime":{"ver""".toByteArray(Charsets.UTF_8)
        assertThrows(EOFException::class.java) {
            ByteArrayInputStream(truncated).readBounded(150_000, expectedContentLength = 64)
        }
    }

    @Test fun rejectsTruncatedJsonOnAbruptCloseWithoutContentLength() {
        val truncated = """{"ok":true,"runtime":{"ver""".toByteArray(Charsets.UTF_8)
        val stream = object : InputStream() {
            var sent = false
            override fun read(): Int = error("unused")
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (!sent) {
                    sent = true
                    System.arraycopy(truncated, 0, b, off, truncated.size)
                    return truncated.size
                }
                throw IOException("unexpected end of stream")
            }
        }
        assertThrows(EOFException::class.java) {
            stream.readBounded(150_000, expectedContentLength = -1)
        }
    }
}
