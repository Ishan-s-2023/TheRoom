package `in`.roomcontroller.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream

/**
 * Reads at most [limit] bytes from this [InputStream].
 *
 * When [expectedContentLength] >= 0 (from an HTTP `Content-Length` header):
 * - Reads up to [expectedContentLength] bytes and stops immediately without issuing a trailing
 *   read that would trigger a TLS `close_notify` / reset exception on ESP32 `esp_https_server`.
 * - Throws [EOFException] if the stream ends or resets before [expectedContentLength] bytes arrive.
 *
 * When [expectedContentLength] < 0 (no `Content-Length` header):
 * - Reads until EOF. If an [IOException] occurs after bytes were already received (for example,
 *   an abrupt socket close after the body), accepts the payload only if it parses as a complete
 *   JSON object or array; otherwise throws [EOFException].
 */
fun InputStream.readBounded(limit: Int, expectedContentLength: Int = -1): String {
    val bytes = readHttpBodyBytes(maxBytes = limit, contentLength = expectedContentLength.toLong())
    return bytes.toString(Charsets.UTF_8)
}

fun InputStream.readHttpBodyBytes(maxBytes: Int, contentLength: Long = -1L): ByteArray {
    if (contentLength > maxBytes) {
        throw IllegalArgumentException("File exceeds the allowed size")
    }
    if (contentLength == 0L) {
        return ByteArray(0)
    }
    val targetBytes = if (contentLength > 0L) contentLength.toInt() else -1
    val output = ByteArrayOutputStream(if (targetBytes > 0) minOf(targetBytes, 16384) else 4096)
    val buffer = ByteArray(8192)
    while (true) {
        if (targetBytes >= 0 && output.size() >= targetBytes) {
            break
        }
        val remainingAllowed = if (targetBytes >= 0) {
            minOf(buffer.size, targetBytes - output.size())
        } else {
            minOf(buffer.size, maxBytes + 1 - output.size())
        }
        if (remainingAllowed <= 0) break
        val count = try {
            read(buffer, 0, remainingAllowed)
        } catch (e: IOException) {
            if (targetBytes >= 0) {
                if (output.size() >= targetBytes) break
                throw EOFException("Truncated HTTP response: expected $targetBytes bytes, got ${output.size()}")
            }
            val partial = output.toByteArray()
            if (partial.isNotEmpty() && isCompleteJsonPayload(partial)) {
                return partial
            }
            throw EOFException("Truncated HTTP response before end of stream: ${e.message}")
        }
        if (count < 0) {
            if (targetBytes >= 0 && output.size() < targetBytes) {
                throw EOFException("Truncated HTTP response: expected $targetBytes bytes, got ${output.size()}")
            }
            break
        }
        if (count == 0) continue
        output.write(buffer, 0, count)
        require(output.size() <= maxBytes) { "File exceeds the allowed size" }
    }
    return output.toByteArray()
}

private fun isCompleteJsonPayload(bytes: ByteArray): Boolean {
    val text = runCatching { bytes.toString(Charsets.UTF_8).trim() }.getOrNull() ?: return false
    if (text.length < 2) return false
    return when {
        text.startsWith("{") && text.endsWith("}") -> runCatching { JSONObject(text) }.isSuccess
        text.startsWith("[") && text.endsWith("]") -> runCatching { JSONArray(text) }.isSuccess
        else -> false
    }
}
