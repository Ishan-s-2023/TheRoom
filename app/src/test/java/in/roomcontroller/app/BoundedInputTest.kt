package `in`.roomcontroller.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream

class BoundedInputTest {
    @Test fun acceptsExactlyTheLimitAndPreservesUtf8() {
        val bytes="Room \uD83C\uDFB5".toByteArray(Charsets.UTF_8)
        assertEquals("Room \uD83C\uDFB5",ByteArrayInputStream(bytes).readBounded(bytes.size))
    }
    @Test fun rejectsOversizedBackupBeforeReadingTheWholeFile() {
        val input=ByteArrayInputStream(ByteArray(100000))
        assertThrows(IllegalArgumentException::class.java) {input.readBounded(16000)}
        assertEquals(83999,input.available())
    }
    @Test fun acceptsEmptyInputForParserToValidate() {
        assertEquals("",ByteArrayInputStream(byteArrayOf()).readBounded(10))
    }
}
