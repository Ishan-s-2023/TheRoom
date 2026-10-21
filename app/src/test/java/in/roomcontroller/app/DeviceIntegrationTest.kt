package `in`.roomcontroller.app

import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.CertificateException
import java.security.cert.X509Certificate

class DeviceIntegrationTest {
    private fun pairing() = obj("ssid" to "Room-test", "ap_password" to "setup-password",
        "token" to "a".repeat(43), "hostname" to "room-test.local", "setup_ip" to "192.168.4.1",
        "certificate_sha256" to "ab".repeat(32))

    @Test fun importsFirmwarePairingFormatAndRejectsUnsafeIdentity() {
        assertEquals("room-test.local", DevicePairing.parse(pairing().toString()).hostname)
        for ((key, value) in listOf("certificate_sha256" to "ab", "hostname" to "evil.example/path", "token" to "x\r\ny", "setup_ip" to "999.1.1.1")) {
            assertThrows(IllegalArgumentException::class.java) { DevicePairing.parse(pairing().put(key,value).toString()) }
        }
    }

    @Test fun convertsDeviceTelemetryAndMicrophoneUnitsWithoutMutatingDeviceState() {
        val raw = obj("microphone" to obj("noise_floor" to .01,"smoothing" to .7),
            "settings" to obj("timezone" to "IST-5:30", "display_timeout_seconds" to 60),
            "runtime" to obj("uptime_ms" to 9000,"drivers" to obj("led" to true,"ir" to false,"microphone" to true)))
        val ui = deviceUiState(raw)
        assertEquals(1.0,ui.getJSONObject("microphone").getDouble("noise_floor"),.00001)
        assertEquals(70.0,ui.getJSONObject("microphone").getDouble("smoothing"),.00001)
        assertEquals(.01,raw.getJSONObject("microphone").getDouble("noise_floor"),.00001)
        assertEquals(9,ui.getJSONObject("telemetry").getInt("uptime"))
        assertEquals("[\"lighting\",\"microphone\"]",ui.getJSONArray("capabilities").toString())
    }

    @Test fun trustsOnlyExactImportedLeafCertificate() {
        val cert = javaClass.getResourceAsStream("/test-device.pem")!!.use {
            CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
        }
        val pin = MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString("") { "%02x".format(it.toInt() and 255) }
        verifyDeviceCertificate(cert,pin)
        assertThrows(CertificateException::class.java) { verifyDeviceCertificate(cert,"00".repeat(32)) }
    }
}
