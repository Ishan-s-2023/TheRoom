package `in`.roomcontroller.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** WLED origins are independent of Room's pinned HTTPS identity and bearer token. */
internal fun wledOrigin(input: String): String {
    val text = input.trim().trimEnd('/')
    val uri = URI(if (text.contains("://")) text else "http://$text")
    require(
        uri.scheme?.lowercase() in listOf("http", "https") && !uri.host.isNullOrBlank() &&
            uri.userInfo == null && uri.query == null && uri.fragment == null &&
            uri.path.orEmpty().isEmpty() && (uri.port == -1 || uri.port in 1..65535)
    ) {
        "Enter a controller address, such as 192.168.1.105 or wled.local"
    }
    return URI(uri.scheme.lowercase(), null, uri.host.lowercase(), uri.port, null, null, null).toASCIIString()
}

internal fun sameWledOrigin(origin: String, candidate: String): Boolean = runCatching {
    val base = URI(origin)
    val url = URI(candidate)
    fun port(uri: URI) = if (uri.port >= 0) uri.port else if (uri.scheme == "https") 443 else 80
    url.userInfo == null && base.scheme == url.scheme && base.host.equals(url.host, true) && port(base) == port(url)
}.getOrDefault(false)

data class WledDevice(
    val id: String,
    val origin: String,
    val name: String,
    val hidden: Boolean = false,
    val linkedRoomHost: String? = null
) {
    fun json(): JSONObject = obj(
        "id" to id,
        "origin" to origin,
        "name" to name,
        "hidden" to hidden
    ).apply {
        if (!linkedRoomHost.isNullOrBlank()) put("linkedRoomHost", linkedRoomHost)
    }

    companion object {
        fun parse(json: JSONObject) = WledDevice(
            id = json.getString("id"),
            origin = wledOrigin(json.getString("origin")),
            name = json.getString("name"),
            hidden = json.optBoolean("hidden"),
            linkedRoomHost = json.optString("linkedRoomHost").takeIf { it.isNotBlank() }
        )
    }
}

data class WledNightlight(
    val on: Boolean = false,
    val durationMinutes: Int = 60,
    val mode: Int = 1,
    val targetBrightness: Int = 0,
    val remainingSeconds: Int = -1
) {
    companion object {
        fun parse(json: JSONObject?): WledNightlight {
            if (json == null) return WledNightlight()
            return WledNightlight(
                on = json.optBoolean("on", false),
                durationMinutes = json.optInt("dur", 60).coerceIn(1, 255),
                mode = json.optInt("mode", json.optInt("fade", 1)).coerceIn(0, 3),
                targetBrightness = json.optInt("tbri", 0).coerceIn(0, 255),
                remainingSeconds = json.optInt("rem", -1)
            )
        }
    }
}

data class WledUdpSync(
    val send: Boolean = false,
    val recv: Boolean = true,
    val sendGroup: Int = 1,
    val recvGroup: Int = 1
) {
    companion object {
        fun parse(json: JSONObject?): WledUdpSync {
            if (json == null) return WledUdpSync()
            return WledUdpSync(
                send = json.optBoolean("send", false),
                recv = json.optBoolean("recv", true),
                sendGroup = json.optInt("sgrp", 1),
                recvGroup = json.optInt("rgrp", 1)
            )
        }
    }
}

data class WledSegment(
    val id: Int,
    val start: Int,
    val stop: Int,
    val len: Int,
    val grp: Int = 1,
    val spc: Int = 0,
    val of: Int = 0,
    val on: Boolean = true,
    val frz: Boolean = false,
    val bri: Int = 255,
    val cct: Int = 127,
    val name: String = "",
    val colors: List<List<Int>> = listOf(listOf(255, 160, 0), listOf(0, 0, 0), listOf(0, 0, 0)),
    val fx: Int = 0,
    val sx: Int = 128,
    val ix: Int = 128,
    val pal: Int = 0,
    val c1: Int = 128,
    val c2: Int = 128,
    val c3: Int = 16,
    val sel: Boolean = true,
    val rev: Boolean = false,
    val mi: Boolean = false,
    val o1: Boolean = false,
    val o2: Boolean = false,
    val o3: Boolean = false,
    val m12: Int = 0,
    val si: Int = 0,
    val bm: Int = 0,
    val tp: Boolean = false
) {
    val supportsWhite: Boolean get() = colors.any { it.size >= 4 && it[3] > 0 }

    fun colorHex(slot: Int = 0): String {
        val c = colors.getOrNull(slot) ?: listOf(255, 160, 0)
        val r = c.getOrElse(0) { 0 }.coerceIn(0, 255)
        val g = c.getOrElse(1) { 0 }.coerceIn(0, 255)
        val b = c.getOrElse(2) { 0 }.coerceIn(0, 255)
        return String.format(Locale.US, "#%02X%02X%02X", r, g, b)
    }

    fun whiteChannel(slot: Int = 0): Int =
        colors.getOrNull(slot)?.getOrNull(3)?.coerceIn(0, 255) ?: 0

    companion object {
        fun parse(json: JSONObject, index: Int): WledSegment {
            val id = json.optInt("id", index)
            val start = json.optInt("start", 0).coerceAtLeast(0)
            val stop = json.optInt("stop", 300).coerceAtLeast(0)
            val len = json.optInt("len", (stop - start).coerceAtLeast(0))
            val colArray = json.optJSONArray("col")
            val parsedColors = (0..2).map { slot ->
                val arr = colArray?.optJSONArray(slot)
                if (arr != null && arr.length() >= 3) {
                    buildList {
                        add(arr.optInt(0, 0).coerceIn(0, 255))
                        add(arr.optInt(1, 0).coerceIn(0, 255))
                        add(arr.optInt(2, 0).coerceIn(0, 255))
                        if (arr.length() >= 4) add(arr.optInt(3, 0).coerceIn(0, 255))
                    }
                } else if (slot == 0) {
                    listOf(255, 160, 0)
                } else {
                    listOf(0, 0, 0)
                }
            }
            return WledSegment(
                id = id,
                start = start,
                stop = stop,
                len = len,
                grp = json.optInt("grp", 1).coerceIn(1, 255),
                spc = json.optInt("spc", 0).coerceIn(0, 255),
                of = json.optInt("of", 0),
                on = json.optBoolean("on", true),
                frz = json.optBoolean("frz", false),
                bri = json.optInt("bri", 255).coerceIn(0, 255),
                cct = json.optInt("cct", 127).coerceIn(0, 255),
                name = json.optString("n", ""),
                colors = parsedColors,
                fx = json.optInt("fx", 0).coerceAtLeast(0),
                sx = json.optInt("sx", 128).coerceIn(0, 255),
                ix = json.optInt("ix", 128).coerceIn(0, 255),
                pal = json.optInt("pal", 0).coerceAtLeast(0),
                c1 = json.optInt("c1", 128).coerceIn(0, 255),
                c2 = json.optInt("c2", 128).coerceIn(0, 255),
                c3 = json.optInt("c3", 16).coerceIn(0, 31),
                sel = json.optBoolean("sel", index == 0),
                rev = json.optBoolean("rev", false),
                mi = json.optBoolean("mi", false),
                o1 = json.optBoolean("o1", false),
                o2 = json.optBoolean("o2", false),
                o3 = json.optBoolean("o3", false),
                m12 = json.optInt("m12", 0).coerceIn(0, 3),
                si = json.optInt("si", 0).coerceIn(0, 3),
                bm = json.optInt("bm", 0).coerceIn(0, 8),
                tp = json.optBoolean("tp", false)
            )
        }
    }
}

data class WledSnapshot(val state: JSONObject, val info: JSONObject) {
    val on: Boolean get() = state.optBoolean("on", false)
    val bri: Int get() = state.optInt("bri", 0).coerceIn(0, 255)
    val transition: Int get() = state.optInt("transition", 7).coerceIn(0, 65535)
    val activePreset: Int get() = state.optInt("ps", -1)
    val activePlaylist: Int get() = state.optInt("pl", -1)
    val liveOverride: Int get() = state.optInt("lor", 0).coerceIn(0, 2)
    val mainSegmentId: Int get() = state.optInt("mainseg", 0)
    val nightlight: WledNightlight get() = WledNightlight.parse(state.optJSONObject("nl"))
    val udpSync: WledUdpSync get() = WledUdpSync.parse(state.optJSONObject("udpn"))

    val version: String get() = info.optString("ver", "unknown")
    val mac: String get() = info.optString("mac", "")
    val ip: String get() = info.optString("ip", "")
    val uptimeSeconds: Long get() = info.optLong("uptime", 0L)
    val isLiveRealtime: Boolean get() = info.optBoolean("live", false)
    val liveSource: String get() = info.optString("lm", "")
    val liveIp: String get() = info.optString("lip", "")

    private val ledsObj: JSONObject? get() = info.optJSONObject("leds")
    val ledCount: Int get() = (ledsObj?.optInt("count", 300) ?: 300).coerceAtLeast(1)
    val estimatedPowerMa: Int get() = ledsObj?.optInt("pwr", 0) ?: 0
    val maxPowerMa: Int get() = ledsObj?.optInt("maxpwr", 0) ?: 0
    val fps: Int get() = ledsObj?.optInt("fps", 0) ?: 0
    val maxSegments: Int get() = (ledsObj?.optInt("maxseg", 16) ?: 16).coerceAtLeast(1)

    private val lightCapabilities: Int
        get() {
            val lc = ledsObj?.optInt("lc", 1) ?: 1
            return ledsObj?.optJSONArray("seglc")?.optInt(0, lc) ?: lc
        }

    val supportsWhite: Boolean get() = (lightCapabilities and 0x02) != 0
    val supportsCct: Boolean get() = (lightCapabilities and 0x04) != 0
    val has2dMatrix: Boolean
        get() = ledsObj?.optJSONObject("matrix") != null || (lightCapabilities and 0x08) != 0

    val segments: List<WledSegment>
        get() {
            val arr = state.optJSONArray("seg")
            if (arr == null || arr.length() == 0) {
                return listOf(WledSegment(id = 0, start = 0, stop = ledCount, len = ledCount))
            }
            val parsed = (0 until arr.length()).mapNotNull { idx ->
                arr.optJSONObject(idx)?.let { WledSegment.parse(it, idx) }
            }.filter { it.stop > it.start }
            return parsed.ifEmpty { listOf(WledSegment(id = 0, start = 0, stop = ledCount, len = ledCount)) }
        }

    val primarySegment: WledSegment
        get() = segments.firstOrNull { it.id == mainSegmentId }
            ?: segments.firstOrNull { it.sel }
            ?: segments.first()

    val selectedSegments: List<WledSegment>
        get() = segments.filter { it.sel }.ifEmpty { listOf(primarySegment) }

    companion object {
        fun parse(json: JSONObject): WledSnapshot {
            val state = json.getJSONObject("state")
            val info = json.getJSONObject("info")
            require(info.has("ver") && info.optJSONObject("leds") != null && state.has("on") && state.has("bri")) {
                "This address is not serving WLED. Install the WLED-based Room firmware first."
            }
            return WledSnapshot(state, info)
        }
    }
}

/**
 * Parses WLED `/json/fxdata` strings (`<sliders>;<colors>;<palette>;<flags>;<defaults>`).
 * Used by the native Effects and Colour tabs to display exact upstream parameter labels.
 */
data class WledEffectMetadata(
    val id: Int,
    val name: String,
    val sliderLabels: List<String?>,
    val colorLabels: List<String>,
    val usesPalette: Boolean,
    val optionLabels: List<String?>,
    val flags: String,
    val defaults: Map<String, Int>
) {
    val supports1D: Boolean get() = flags.isEmpty() || flags.contains('1') || !flags.contains('2')
    val supports2D: Boolean get() = flags.contains('2')
    val isVolumeReactive: Boolean get() = flags.contains('v')
    val isFrequencyReactive: Boolean get() = flags.contains('f')
    val isAudioReactive: Boolean get() = isVolumeReactive || isFrequencyReactive

    companion object {
        private val defaultSliders = listOf("Effect speed", "Effect intensity", "Custom 1", "Custom 2", "Custom 3")
        private val defaultColors = listOf("Primary (Fx)", "Secondary (Bg)", "Tertiary (Cs)")
        private val defaultOptions = listOf("Option 1", "Option 2", "Option 3")

        fun parse(id: Int, name: String, fxdataRaw: String?): WledEffectMetadata {
            if (fxdataRaw == null) {
                val isSolid = id == 0 || name.equals("Solid", ignoreCase = true)
                return WledEffectMetadata(
                    id = id,
                    name = name,
                    sliderLabels = if (isSolid) listOf(null, null, null, null, null) else listOf("Effect speed", "Effect intensity", null, null, null),
                    colorLabels = defaultColors,
                    usesPalette = !isSolid,
                    optionLabels = listOf(null, null, null),
                    flags = "1",
                    defaults = emptyMap()
                )
            }
            val sections = fxdataRaw.split(';')
            val rawControls = sections.getOrNull(0).orEmpty().split(',')
            val sliders = (0..4).map { idx ->
                when (val token = rawControls.getOrNull(idx)?.trim().orEmpty()) {
                    "" -> null
                    "!" -> defaultSliders[idx]
                    else -> token
                }
            }
            val options = (0..2).map { idx ->
                when (val token = rawControls.getOrNull(5 + idx)?.trim().orEmpty()) {
                    "" -> null
                    "!" -> defaultOptions[idx]
                    else -> token
                }
            }

            val rawColors = sections.getOrNull(1).orEmpty().split(',')
            val colors = (0..2).map { idx ->
                when (val token = rawColors.getOrNull(idx)?.trim().orEmpty()) {
                    "" -> defaultColors[idx]
                    "!" -> defaultColors[idx]
                    else -> token
                }
            }

            val palSec = sections.getOrNull(2)?.trim().orEmpty()
            val usesPalette = palSec.isNotEmpty() && palSec != "0"

            val flags = sections.getOrNull(3)?.trim().ifNullOrBlank { "1" }
            val defaultsMap = mutableMapOf<String, Int>()
            sections.getOrNull(4).orEmpty().split(',').forEach { kv ->
                val parts = kv.split('=')
                if (parts.size == 2) {
                    val k = parts[0].trim()
                    val v = parts[1].trim().toIntOrNull()
                    if (k.isNotEmpty() && v != null) defaultsMap[k] = v
                }
            }

            return WledEffectMetadata(
                id = id,
                name = name,
                sliderLabels = sliders,
                colorLabels = colors,
                usesPalette = usesPalette,
                optionLabels = options,
                flags = flags,
                defaults = defaultsMap
            )
        }
    }
}

private fun String?.ifNullOrBlank(fallback: () -> String): String =
    if (this.isNullOrBlank()) fallback() else this

data class WledPlaylistSpec(
    val presets: List<Int>,
    val durationsTenths: List<Int>,
    val transitionsTenths: List<Int>,
    val repeat: Int = 0,
    val endPreset: Int = 0,
    val shuffle: Boolean = false
)

data class WledPresetEntry(
    val id: Int,
    val name: String,
    val quickLabel: String = "",
    val on: Boolean? = null,
    val bri: Int? = null,
    val includeBrightness: Boolean = true,
    val saveSegmentBounds: Boolean = true,
    val playlist: WledPlaylistSpec? = null,
    val raw: JSONObject = JSONObject()
) {
    val isPlaylist: Boolean get() = playlist != null
}

internal fun parseWledPresets(json: JSONObject): List<WledPresetEntry> {
    return json.keys().asSequence()
        .mapNotNull { key ->
            val id = key.toIntOrNull() ?: return@mapNotNull null
            if (id !in 1..250) return@mapNotNull null
            val entry = json.optJSONObject(key) ?: return@mapNotNull null
            val name = entry.optString("n").trim()
            val plObj = entry.optJSONObject("playlist")
            if (name.isEmpty() && plObj == null && !entry.has("seg") && !entry.has("bri")) return@mapNotNull null
            val playlist = plObj?.let { pl ->
                val psArr = pl.optJSONArray("ps")
                val psList = (0 until (psArr?.length() ?: 0)).mapNotNull { idx ->
                    psArr?.optInt(idx, 0)?.takeIf { it in 1..250 }
                }
                if (psList.isEmpty()) null else {
                    val durArr = pl.optJSONArray("dur")
                    val durs = (0 until (durArr?.length() ?: 0)).map { durArr!!.optInt(it, 100).coerceAtLeast(1) }
                        .ifEmpty { List(psList.size) { 100 } }
                    val trArr = pl.optJSONArray("transition") ?: pl.optJSONArray("tr")
                    val trs = (0 until (trArr?.length() ?: 0)).map { trArr!!.optInt(it, 7).coerceAtLeast(0) }
                        .ifEmpty { List(psList.size) { 7 } }
                    WledPlaylistSpec(
                        presets = psList,
                        durationsTenths = durs,
                        transitionsTenths = trs,
                        repeat = pl.optInt("repeat", 0),
                        endPreset = pl.optInt("end", 0),
                        shuffle = pl.optBoolean("r", false) || pl.optInt("r", 0) == 1
                    )
                }
            }
            WledPresetEntry(
                id = id,
                name = name.ifEmpty { if (playlist != null) "Playlist $id" else "Preset $id" },
                quickLabel = entry.optString("ql", "").trim(),
                on = if (entry.has("on")) entry.optBoolean("on") else null,
                bri = if (entry.has("bri")) entry.optInt("bri").coerceIn(0, 255) else null,
                includeBrightness = entry.optBoolean("ib", true),
                saveSegmentBounds = entry.optBoolean("sb", true),
                playlist = playlist,
                raw = entry
            )
        }
        .sortedBy { it.id }
        .toList()
}

internal fun buildSegmentPatch(segmentIds: List<Int>, fields: JSONObject): JSONObject {
    val segArray = JSONArray()
    segmentIds.distinct().forEach { id ->
        segArray.put(fields.copyJson().put("id", id))
    }
    return obj("seg" to segArray)
}

internal fun buildPresetSavePayload(
    id: Int,
    name: String,
    quickLabel: String = "",
    includeBrightness: Boolean = true,
    saveSegmentBounds: Boolean = true
): JSONObject {
    require(id in 1..250) { "Preset ID must be between 1 and 250" }
    val cleanName = name.trim().take(32)
    require(cleanName.isNotEmpty()) { "Preset name is required" }
    return obj(
        "psave" to id,
        "n" to cleanName,
        "ib" to includeBrightness,
        "sb" to saveSegmentBounds
    ).apply {
        if (quickLabel.isNotBlank()) put("ql", quickLabel.trim().take(2))
    }
}

internal fun buildPlaylistSavePayload(
    id: Int,
    name: String,
    spec: WledPlaylistSpec
): JSONObject {
    require(id in 1..250) { "Playlist ID must be between 1 and 250" }
    require(spec.presets.isNotEmpty()) { "Select at least one preset for the playlist" }
    val cleanName = name.trim().take(32).ifEmpty { "Playlist $id" }
    return obj(
        "psave" to id,
        "n" to cleanName,
        "o" to true,
        "playlist" to obj(
            "ps" to JSONArray(spec.presets),
            "dur" to JSONArray(spec.durationsTenths),
            "transition" to JSONArray(spec.transitionsTenths),
            "repeat" to spec.repeat,
            "end" to spec.endPreset,
            "r" to spec.shuffle
        )
    )
}

/**
 * Decodes WLED WebSocket live LED binary frames (`'L'` = 0x4C header, v1 2-byte offset or v2 4-byte 1D/2D header)
 * into packed 24-bit RGB ints (`0xRRGGBB`).
 */
internal fun parseLiveLedFrame(bytes: ByteArray): List<Int> {
    if (bytes.size < 5 || bytes[0] != 'L'.code.toByte()) return emptyList()
    val version = bytes[1].toInt() and 0xFF
    val offset = when (version) {
        1 -> 2
        2 -> if (bytes.size >= 4) 4 else return emptyList()
        else -> 2
    }
    val pixelCount = ((bytes.size - offset) / 3).coerceAtMost(1500)
    if (pixelCount <= 0) return emptyList()
    val result = ArrayList<Int>(pixelCount)
    var idx = offset
    repeat(pixelCount) {
        val r = bytes[idx].toInt() and 0xFF
        val g = bytes[idx + 1].toInt() and 0xFF
        val b = bytes[idx + 2].toInt() and 0xFF
        result.add((r shl 16) or (g shl 8) or b)
        idx += 3
    }
    return result
}

internal fun parseLiveLedJson(json: JSONObject): List<Int> {
    val arr = json.optJSONArray("leds") ?: return emptyList()
    val count = arr.length().coerceAtMost(1500)
    val result = ArrayList<Int>(count)
    for (i in 0 until count) {
        val item = arr.opt(i)
        when (item) {
            is String -> {
                val clean = item.removePrefix("#").trim()
                val rgb = clean.toIntOrNull(16)
                if (rgb != null) result.add(rgb and 0xFFFFFF)
            }
            is Number -> result.add(item.toInt() and 0xFFFFFF)
        }
    }
    return result
}

/** No automatic POST retries: a transport failure can occur after a command was applied. */
class WledApi(
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(9, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
) {
    suspend fun request(origin: String, path: String, body: JSONObject? = null): String = withContext(Dispatchers.IO) {
        require(path.startsWith('/') && !path.startsWith("//"))
        val base = wledOrigin(origin)
        val builder = Request.Builder().url(base + path).header("Accept", "application/json")
        if (body != null) {
            builder.post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        }
        client.newCall(builder.build()).execute().use { response ->
            check(response.isSuccessful) {
                "WLED returned HTTP ${response.code}. Check the connection and controller access settings."
            }
            val stream = response.body ?: error("WLED returned an empty response")
            val contentLength = stream.contentLength()
            require(contentLength <= 2_097_152) { "WLED response is too large" }
            val bytes = stream.byteStream().use {
                it.readHttpBodyBytes(maxBytes = 2_097_152, contentLength = contentLength)
            }
            bytes.toString(Charsets.UTF_8)
        }
    }

    suspend fun state(origin: String) = WledSnapshot.parse(JSONObject(request(origin, "/json/si")))

    suspend fun command(origin: String, body: JSONObject): WledSnapshot {
        val result = JSONObject(request(origin, "/json/state", body.copyJson().put("v", true)))
        check(!result.has("error") && (!result.has("success") || result.optBoolean("success"))) {
            "WLED rejected the command: ${result.opt("error") ?: "unsuccessful response"}"
        }
        // Read authoritative state. Never treat an acknowledgement as a confirmed display state.
        return state(origin)
    }

    suspend fun effects(origin: String): List<String> {
        val arr = JSONArray(request(origin, "/json/eff"))
        return (0 until arr.length()).map { arr.optString(it, "Effect #$it") }
    }

    suspend fun palettes(origin: String): List<String> {
        val arr = JSONArray(request(origin, "/json/pal"))
        return (0 until arr.length()).map { arr.optString(it, "Palette #$it") }
    }

    suspend fun fxdata(origin: String): List<String> {
        val arr = JSONArray(request(origin, "/json/fxdata"))
        return (0 until arr.length()).map { arr.optString(it, "") }
    }

    suspend fun presets(origin: String): JSONObject =
        runCatching { JSONObject(request(origin, "/presets.json")) }
            .recoverCatching { JSONObject(request(origin, "/json/presets")) }
            .getOrThrow()

    suspend fun config(origin: String): JSONObject =
        runCatching { JSONObject(request(origin, "/json/cfg")) }
            .recoverCatching { JSONObject(request(origin, "/cfg.json")) }
            .getOrThrow()

    suspend fun saveConfig(origin: String, patch: JSONObject): JSONObject {
        val unwrappedPatch = if (patch.has("cfg") && patch.length() == 1) {
            patch.optJSONObject("cfg") ?: patch
        } else {
            patch
        }
        val rawCurrent = runCatching { config(origin) }.getOrDefault(JSONObject())
        val current = if (rawCurrent.has("cfg") && rawCurrent.length() == 1) {
            rawCurrent.optJSONObject("cfg") ?: rawCurrent
        } else {
            rawCurrent
        }
        val merged = deepMergeJson(current, unwrappedPatch)
        stripUnchangedSecrets(merged, unwrappedPatch)
        request(origin, "/json/cfg", merged)
        return runCatching { config(origin) }.getOrDefault(merged)
    }
}

internal fun wledDeviceId(info: JSONObject) = info.optString("mac").lowercase().filter { it.isLetterOrDigit() }
    .ifBlank { error("WLED did not provide its hardware identity") }

internal fun wledByte(percent: Float) = (percent.coerceIn(0f, 100f) * 255f / 100f).toInt()

internal fun wledPercent(byteVal: Int) = (byteVal.coerceIn(0, 255) * 100f / 255f).roundToInt()
