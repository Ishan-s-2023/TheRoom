package `in`.roomcontroller.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * Enforces the approved ESP32 Room + WLED hardware pin map so user configuration edits
 * cannot assign LED buses or I2S signals onto SPI flash pins, input-only pins, or
 * Room's reserved IR / OLED / encoder / button GPIOs.
 */
object RoomHardwarePins {
    const val LED_DATA = 23
    const val MIC_BCLK = 26
    const val MIC_WS = 25
    const val MIC_SD = 35

    val RESERVED_ROLES: Map<Int, String> = linkedMapOf(
        4 to "Tactile Button 4 (Favourites)",
        13 to "Tactile Button 1 (Lighting)",
        14 to "Tactile Button 2 (Music)",
        16 to "Rotary Encoder A (CLK)",
        17 to "Rotary Encoder B (DT)",
        18 to "IR Transmitter (38 kHz LED)",
        19 to "Rotary Encoder Push Switch",
        21 to "OLED I2C SDA (SSD1306)",
        22 to "OLED I2C SCL (SSD1306)",
        23 to "Addressable LED Strip Data (WS2812B)",
        25 to "INMP441 I2S WS / LRCLK",
        26 to "INMP441 I2S BCLK / SCK",
        27 to "Tactile Button 3 (IR)",
        34 to "IR Receiver Input (Demodulated)",
        35 to "INMP441 I2S SD / DOUT (Input-only)"
    )

    private val SPI_FLASH_PINS = setOf(6, 7, 8, 9, 10, 11)
    private val INPUT_ONLY_PINS = setOf(34, 35, 36, 39)

    private val ROLE_DEFAULT_PIN = mapOf(
        "led" to LED_DATA,
        "mic-sck" to MIC_BCLK,
        "mic-ws" to MIC_WS,
        "mic-sd" to MIC_SD
    )

    fun validatePinForRole(pin: Int, role: String, requireOutput: Boolean): String? {
        if (pin !in 0..39) return "GPIO $pin is outside the ESP32 valid range (0–39)."
        if (pin in SPI_FLASH_PINS) return "GPIO $pin is reserved for internal ESP32 SPI flash and cannot be used."
        if (requireOutput && pin in INPUT_ONLY_PINS) {
            return "GPIO $pin is input-only on ESP32 (GPIOs 34–39 have no output driver)."
        }
        val expectedPinForRole = ROLE_DEFAULT_PIN[role]
        val reservedDescription = RESERVED_ROLES[pin]
        if (reservedDescription != null && pin != expectedPinForRole) {
            return "GPIO $pin is reserved for $reservedDescription on the Room board."
        }
        return null
    }
}

/**
 * Recursively merges [patch] into a deep copy of [base] so firmware-specific keys
 * in `/json/cfg` are preserved when saving a partial section.
 */
internal fun deepMergeJson(base: JSONObject, patch: JSONObject): JSONObject {
    val result = base.copyJson()
    patch.keys().forEach { key ->
        val patchVal = patch.opt(key)
        val baseVal = result.opt(key)
        when {
            patchVal is JSONObject && baseVal is JSONObject -> {
                result.put(key, deepMergeJson(baseVal, patchVal))
            }
            patchVal is JSONArray && baseVal is JSONArray -> {
                val mergedArr = JSONArray()
                val maxLen = maxOf(baseVal.length(), patchVal.length())
                for (i in 0 until maxLen) {
                    val pElem = patchVal.opt(i)
                    val bElem = baseVal.opt(i)
                    when {
                        pElem is JSONObject && bElem is JSONObject -> mergedArr.put(deepMergeJson(bElem, pElem))
                        pElem != null -> mergedArr.put(pElem)
                        bElem != null -> mergedArr.put(bElem)
                    }
                }
                result.put(key, mergedArr)
            }
            else -> result.put(key, patchVal)
        }
    }
    return result
}

/**
 * Removes write-only password fields (`psk`, `pskl`) from [merged] unless they were
 * explicitly supplied in [explicitPatch]. WLED returns masked `"********"` placeholders
 * on GET `/json/cfg` and would overwrite real credentials if echoed back unchanged.
 */
internal fun stripUnchangedSecrets(merged: JSONObject, explicitPatch: JSONObject) {
    val nwIns = merged.optJSONObject("nw")?.optJSONArray("ins")
    val patchNwIns = explicitPatch.optJSONObject("nw")?.optJSONArray("ins")
    if (nwIns != null) {
        for (i in 0 until nwIns.length()) {
            val item = nwIns.optJSONObject(i) ?: continue
            val patchItem = patchNwIns?.optJSONObject(i)
            if (patchItem == null || !patchItem.has("psk")) {
                item.remove("psk")
            }
        }
    }
    val ap = merged.optJSONObject("ap")
    val patchAp = explicitPatch.optJSONObject("ap")
    if (ap != null && (patchAp == null || !patchAp.has("psk"))) {
        ap.remove("psk")
    }
    val mqtt = merged.optJSONObject("if")?.optJSONObject("mqtt")
    val patchMqtt = explicitPatch.optJSONObject("if")?.optJSONObject("mqtt")
    if (mqtt != null && (patchMqtt == null || !patchMqtt.has("psk"))) {
        mqtt.remove("psk")
    }
    val ota = merged.optJSONObject("ota")
    val patchOta = explicitPatch.optJSONObject("ota")
    if (ota != null && (patchOta == null || !patchOta.has("pskl"))) {
        ota.remove("pskl")
    }
}

internal fun validateWledBackupJson(kind: String, rawText: String): JSONObject {
    require(rawText.length in 2..1_048_576) { "Backup file is empty or exceeds the 1 MiB limit." }
    val parsed = runCatching { JSONObject(rawText) }.getOrElse {
        error("Selected file is not valid JSON.")
    }
    when (kind) {
        "cfg" -> {
            require(parsed.has("hw") || parsed.has("nw") || parsed.has("def") || parsed.has("rev")) {
                "This file does not look like a WLED cfg.json configuration backup."
            }
            val ledPins = parsed.optJSONObject("hw")?.optJSONObject("led")?.optJSONArray("ins")
            if (ledPins != null && ledPins.length() > 0) {
                val pin0 = ledPins.optJSONObject(0)?.optJSONArray("pin")?.optInt(0, -1) ?: -1
                if (pin0 >= 0) {
                    val err = RoomHardwarePins.validatePinForRole(pin0, "led", requireOutput = true)
                    require(err == null) { "Backup rejected: $err" }
                }
            }
        }
        "presets" -> {
            val validKeys = parsed.keys().asSequence().any { it.toIntOrNull() in 0..250 }
            require(validKeys) { "This file does not contain valid WLED preset slots (0–250)." }
        }
        else -> error("Unsupported backup type: $kind")
    }
    return parsed
}

enum class AudioReadinessState(val badge: String, val summary: String) {
    HEALTHY_LOCAL(
        "I2S MIC READY",
        "INMP441 I2S microphone and WLED AudioReactive FFT pipeline are active on the ESP32."
    ),
    SILENT_ROOM(
        "QUIET / BELOW SQUELCH",
        "Microphone driver is ready and listening; room ambient level is currently below the squelch gate."
    ),
    UDP_SYNC_ONLY(
        "UDP AUDIO SYNC",
        "Controller is receiving external AudioReactive UDP sync frames over Wi-Fi."
    ),
    STALE_TELEMETRY(
        "STALE TELEMETRY",
        "Audio telemetry sample age exceeds 2.5 seconds. Check microphone wiring or refresh controller state."
    ),
    MIC_INIT_FAILED(
        "MIC DRIVER FAULT",
        "AudioReactive is enabled in configuration, but the ESP32 microphone driver reported not ready."
    ),
    MIC_DISABLED(
        "MIC DISABLED",
        "WLED AudioReactive is currently disabled. Enable it below to activate sound-reactive effects."
    ),
    OFFLINE(
        "CONTROLLER OFFLINE",
        "Connect to your ESP32 over Wi-Fi to inspect live microphone telemetry and configure AudioReactive."
    )
}

data class WledAudioStatus(
    val readiness: AudioReadinessState,
    val enabled: Boolean,
    val dmicType: Int,
    val pinSd: Int,
    val pinWs: Int,
    val pinSck: Int,
    val squelch: Int,
    val gain: Int,
    val agcMode: Int,
    val limiter: Boolean,
    val riseMs: Int,
    val fallMs: Int,
    val freqScale: Int,
    val syncMode: Int,
    val syncPort: Int,
    val quietBehavior: String,
    val quietSeconds: Int,
    val audioAgeMs: Long?,
    val bands: List<Float>
)

internal fun evaluateWledAudioStatus(
    wledOnline: Boolean,
    snapshot: WledSnapshot?,
    cfg: JSONObject?,
    roomState: JSONObject?,
    roomStale: Boolean
): WledAudioStatus {
    val ar = cfg?.optJSONObject("um")?.optJSONObject("AudioReactive")
    val enabled = ar?.optBoolean("enabled", true) ?: true
    val dmicType = ar?.optJSONObject("dmic")?.optInt("type", 1) ?: 1
    val res = ar?.optJSONObject("resources")
    val pinSd = res?.optInt("pin-sd", RoomHardwarePins.MIC_SD) ?: RoomHardwarePins.MIC_SD
    val pinWs = res?.optInt("pin-ws", RoomHardwarePins.MIC_WS) ?: RoomHardwarePins.MIC_WS
    val pinSck = res?.optInt("pin-sck", RoomHardwarePins.MIC_BCLK) ?: RoomHardwarePins.MIC_BCLK
    val squelch = (ar?.optInt("squelch", 10) ?: 10).coerceIn(0, 255)
    val gain = (ar?.optInt("gain", 64) ?: 64).coerceIn(0, 255)
    val agcMode = (ar?.optInt("agc", 1) ?: 1).coerceIn(0, 3)
    val dyn = ar?.optJSONObject("dynamics")
    val limiter = dyn?.optBoolean("limiter", true) ?: true
    val riseMs = (dyn?.optInt("rise", 80) ?: 80).coerceIn(0, 1000)
    val fallMs = (dyn?.optInt("fall", 300) ?: 300).coerceIn(0, 5000)
    val freqScale = (ar?.optJSONObject("frequency")?.optInt("scale", 3) ?: 3).coerceIn(0, 3)
    val syncObj = ar?.optJSONObject("sync")
    val syncMode = (syncObj?.optInt("mode", 0) ?: 0).coerceIn(0, 2)
    val syncPort = (syncObj?.optInt("port", 11988) ?: 11988).coerceIn(1, 65535)

    val roomMic = roomState?.optJSONObject("microphone")
    val quietBehavior = roomMic?.optString("quiet_behavior", "dim")?.takeIf { it in listOf("off", "dim", "hold") } ?: "dim"
    val quietSeconds = (roomMic?.optInt("quiet_seconds", 10) ?: 10).coerceIn(1, 300)

    val runtime = roomState?.optJSONObject("runtime")
    val audioAgeMs = if (runtime != null && runtime.has("audio_age_ms")) runtime.optLong("audio_age_ms") else null
    val drivers = runtime?.optJSONObject("drivers")
    val driverMicExplicitFalse = drivers != null && drivers.has("microphone") && !drivers.optBoolean("microphone")

    val rawBandsArr = roomState?.optJSONObject("telemetry")?.optJSONArray("bands")
    val parsedBands = if (rawBandsArr != null && !roomStale) {
        (0 until rawBandsArr.length()).map { rawBandsArr.optDouble(it, 0.0).toFloat().coerceIn(0f, 1f) }
    } else emptyList()

    val readiness = when {
        !wledOnline && roomStale -> AudioReadinessState.OFFLINE
        !enabled -> AudioReadinessState.MIC_DISABLED
        driverMicExplicitFalse -> AudioReadinessState.MIC_INIT_FAILED
        syncMode == 2 -> AudioReadinessState.UDP_SYNC_ONLY
        audioAgeMs != null && audioAgeMs > 2500L -> AudioReadinessState.STALE_TELEMETRY
        parsedBands.isNotEmpty() && parsedBands.all { it <= 0.02f } -> AudioReadinessState.SILENT_ROOM
        else -> AudioReadinessState.HEALTHY_LOCAL
    }

    // Never fabricate or display band bars when the microphone is disabled, failed, or offline.
    val effectiveBands = when (readiness) {
        AudioReadinessState.MIC_DISABLED,
        AudioReadinessState.MIC_INIT_FAILED,
        AudioReadinessState.OFFLINE,
        AudioReadinessState.STALE_TELEMETRY -> emptyList()
        else -> parsedBands
    }

    return WledAudioStatus(
        readiness = readiness,
        enabled = enabled,
        dmicType = dmicType,
        pinSd = pinSd,
        pinWs = pinWs,
        pinSck = pinSck,
        squelch = squelch,
        gain = gain,
        agcMode = agcMode,
        limiter = limiter,
        riseMs = riseMs,
        fallMs = fallMs,
        freqScale = freqScale,
        syncMode = syncMode,
        syncPort = syncPort,
        quietBehavior = quietBehavior,
        quietSeconds = quietSeconds,
        audioAgeMs = audioAgeMs,
        bands = effectiveBands
    )
}
