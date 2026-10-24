package `in`.roomcontroller.app

import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

/**
 * Complete built-in WLED 0.14–0.16 + AudioReactive catalog & local state engine.
 * Used whenever the controller has not yet returned live `/json/eff`, `/json/pal`, `/json/fxdata`,
 * `/json/si`, `/presets.json` or `/json/cfg` (including offline/preview mode), so every single
 * WLED screen, tab, button, slider, effect, palette, segment and config control is always visible
 * and interactive.
 */
object WledCatalogDefaults {

    val EFFECTS: List<String> = listOf(
        "Solid", "Blink", "Breathe", "Wipe", "Wipe Random", "Random Colors", "Sweep", "Dynamic",
        "Colorloop", "Rainbow", "Scan", "Scan Dual", "Fade", "Theater", "Theater Rainbow", "Running",
        "Saw", "Twinkle", "Dissolve", "Dissolve Rnd", "Sparkle", "Sparkle Dark", "Sparkle+", "Strobe",
        "Strobe Rainbow", "Strobe Mega", "Blink Rainbow", "Android", "Chase", "Chase Random", "Chase Rainbow",
        "Chase Flash", "Chase Flash Rnd", "Rainbow Runner", "Colorful", "Traffic Light", "Sweep Random",
        "Chase 2", "Aurora", "Stream", "Scanner", "Lighthouse", "Fireworks", "Rain", "Tetrix", "Fire Flicker",
        "Gradient", "Loading", "Rolling Balls", "Fairy", "Two Dots", "Fairytwinkle", "Running Dual", "RSVD",
        "Chase 3", "Tri Wipe", "Tri Fade", "Lightning", "ICU", "Multi Comet", "Scanner Dual", "Stream 2",
        "Oscillate", "Pride 2015", "Juggle", "Palette", "Fire 2012", "Colorwaves", "Bpm", "Fill Noise",
        "Noise 1", "Noise 2", "Noise 3", "Noise 4", "Colortwinkles", "Lake", "Meteor", "Meteor Smooth",
        "Railway", "Ripple", "Twinklefox", "Twinklecat", "Halloween Eyes", "Solid Pattern", "Solid Pattern Tri",
        "Spots", "Spots Fade", "Glitter", "Candle", "Fireworks Starburst", "Fireworks 1D", "Bouncing Balls",
        "Sinelon", "Sinelon Dual", "Sinelon Rainbow", "Popcorn", "Drip", "Plasma", "Percent", "Ripple Rainbow",
        "Heartbeat", "Pacifica", "Candle Multi", "Solid Glitter", "Sunrise", "Phased", "Twinkleup", "Noise Pal",
        "Sine", "Phased Noise", "Flow", "Chunchun", "Dancing Shadows", "Washing Machine", "Candy Cane", "Blends",
        "TV Simulator", "Dynamic Smooth", "Spaceships", "Crazy Bees", "Ghost Rider", "Blobs", "Scrolling Text",
        "Drift Rose", "Distortion Waves", "Soap", "Octopus", "Waving Cell", "Pixels", "Pixelwave", "Juggle Audio",
        "Matripix", "Gravimeter", "Plasmoid", "Puddles", "Midnoise", "Noisemeter", "Freqwave", "Freqmatrix",
        "GEQ", "Waterfall", "Freqpixels", "Binmap", "Noisefire", "Puddlepeak", "Noisemove", "Noise2D",
        "Perlin Move", "Ripple Peak", "Firenoise", "Squared Swirl", "DNA", "Matrix", "Metaballs", "Freqmap",
        "Gravcenter", "Gravcentric", "Gravfreq", "DJ Light", "Funky Plank", "Akemi", "Pulser", " Sindots",
        "DNA Spiral", "Black Hole", "Wavesins", "Rocktaves", "Waverly", "Hiphotic", "Sindots 2D", "Swirl"
    )

    val FXDATA: List<String> = EFFECTS.mapIndexed { idx, name ->
        when {
            idx == 0 -> ";;;;" // Solid
            idx == 1 || idx == 2 -> "Speed,Duty cycle,,,;!,!;!;1;sx=128,ix=128"
            idx == 9 || idx == 63 || idx == 67 -> "Speed,Scale,,,;!,!;!;1;sx=128,ix=128"
            idx == 45 || idx == 66 -> "Cooling,Sparking,Boost,,;!,!,!;!;1;sx=128,ix=160"
            idx == 88 || idx == 102 -> "Speed,Intensity,,,;!,!;!;1;sx=96,ix=224"
            idx in 118..127 || idx in 146..153 || idx in 163..170 ->
                "Speed,Intensity,Scale,Custom 2,Custom 3,Overlay;!,!,!;!;2;sx=128,ix=128"
            idx in listOf(128, 129, 130, 131, 132, 133, 134, 135, 143, 144, 148, 155, 156) ->
                "Speed,Sensitivity,Decay,Custom 1,Custom 2,Overlay;Fx,Bg,Cs;!;1v;sx=128,ix=160"
            idx in listOf(136, 137, 138, 139, 140, 141, 142, 154, 157, 158, 159, 160, 166) ->
                "Rate,Sound sensitivity,Band decay,Low bin,High bin,Peak hold;Fx,Bg,Cs;!;1vf;sx=140,ix=180"
            else -> "Effect speed,Effect intensity,Custom 1,Custom 2,Custom 3,Option 1,Option 2;Fx,Bg,Cs;!;1;sx=128,ix=128"
        }
    }

    val PALETTES: List<String> = listOf(
        "Default", "* Random Cycle", "* Color 1", "* Colors 1&2", "* Color Gradient", "* Colors Only",
        "Party", "Cloud", "Lava", "Ocean", "Forest", "Rainbow", "Rainbow Bands", "Sunset", "Rivendell",
        "Breeze", "Red & Blue", "Yellowout", "Analogous", "Splash", "Pastel", "Sunset 2", "Beach",
        "Vintage", "Departure", "Landscape", "Beech", "Sherbet", "Hult", "Hult 64", "Drywet", "Jul",
        "Grintage", "Rewhi", "Tertiary", "Fire", "Icefire", "Cyane", "Light Pink", "Autumn", "Magenta",
        "Magred", "Yelmag", "Yelblu", "Orange & Teal", "Tiamat", "April Night", "Orangery", "C9",
        "Sakura", "Aurora", "Atlantica", "C9 2", "C9 New", "Temperature", "Aurora 2", "Retro Clown",
        "Candy", "Toxy Reaf", "Fairy Reaf", "Semi Blue", "Pink Candy", "Red Reaf", "Aqua Flash",
        "Yelblu Hot", "Lite Light", "Red Flash", "Blink Red", "Red Shift", "Red Tide", "Candy2"
    )

    fun luminousLedColor(raw: Color, brightnessFraction: Float = 1f): Color {
        val r = raw.red.coerceIn(0f, 1f)
        val g = raw.green.coerceIn(0f, 1f)
        val b = raw.blue.coerceIn(0f, 1f)
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        val h = when {
            delta <= 0.0001f -> 0f
            max == r -> 60f * (((g - b) / delta + 6f) % 6f)
            max == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }
        val s = if (max <= 0.0001f) 0f else (delta / max).coerceIn(0f, 1f)
        val baseV = max.coerceAtLeast(0.28f)
        val gammaBri = Math.pow(brightnessFraction.coerceIn(0.05f, 1f).toDouble(), 0.42).toFloat()
        val finalV = (0.32f + 0.68f * baseV * gammaBri).coerceIn(0.25f, 1f)
        val finalS = (s * 0.96f).coerceIn(0f, 1f)
        return hsvToComposeColor(h, finalS, finalV)
    }

    fun palettePreviewColors(palId: Int, primary: Color, secondary: Color, tertiary: Color): List<Color> {
        val p1 = luminousLedColor(primary, 1f)
        val p2 = if (secondary.red + secondary.green + secondary.blue < 0.15f) {
            luminousLedColor(primary, 0.75f)
        } else {
            luminousLedColor(secondary, 1f)
        }
        val p3 = if (tertiary.red + tertiary.green + tertiary.blue < 0.15f) {
            Color(0xFFFFD54F)
        } else {
            luminousLedColor(tertiary, 1f)
        }
        return when (palId) {
            0 -> listOf(p1, p2, p3)
            1 -> listOf(Color(0xFFFF5252), Color(0xFFFFD740), Color(0xFF69F0AE), Color(0xFF40C4FF), Color(0xFFE040FB))
            2 -> listOf(p1, p1)
            3 -> listOf(p1, p2)
            4, 5 -> listOf(p1, p2, p3)
            6 -> listOf(Color(0xFF8E24AA), Color(0xFFD81B60), Color(0xFFFF1744), Color(0xFFFF6D00), Color(0xFFFFAB00), Color(0xFFFFEA00))
            7 -> listOf(Color(0xFF2979FF), Color(0xFF40C4FF), Color(0xFF80D8FF), Color(0xFFFFFFFF))
            8 -> listOf(Color(0xFFD50000), Color(0xFFFF3D00), Color(0xFFFF9100), Color(0xFFFFEA00), Color(0xFFFFFFFF))
            9 -> listOf(Color(0xFF0066CC), Color(0xFF00B0FF), Color(0xFF00E5FF), Color(0xFF84FFFF), Color(0xFFE0FFFF))
            10 -> listOf(Color(0xFF00C853), Color(0xFF69F0AE), Color(0xFFB5F36A), Color(0xFFDCEDC8))
            11, 12 -> listOf(Color(0xFFFF1744), Color(0xFFFF9100), Color(0xFFFFEA00), Color(0xFF00E676), Color(0xFF00E5FF), Color(0xFF2979FF), Color(0xFFD500F9))
            13, 21 -> listOf(Color(0xFFAA00FF), Color(0xFFF50057), Color(0xFFFF5252), Color(0xFFFF9100), Color(0xFFFFD740))
            14 -> listOf(Color(0xFF00BFA5), Color(0xFF1DE9B6), Color(0xFF69F0AE), Color(0xFFD4E157))
            15 -> listOf(Color(0xFF0091EA), Color(0xFF00B8D4), Color(0xFF00E5FF), Color(0xFFA7FFEB))
            35 -> listOf(Color(0xFFD50000), Color(0xFFFF3D00), Color(0xFFFF6D00), Color(0xFFFFAB00), Color(0xFFFFF59D))
            36 -> listOf(Color(0xFF2979FF), Color(0xFF00E5FF), Color(0xFFFFFFFF), Color(0xFFFF9100), Color(0xFFFF1744))
            37 -> listOf(Color(0xFF00B0FF), Color(0xFF00E5FF), Color(0xFF1DE9B6), Color(0xFFA7FFEB))
            44 -> listOf(Color(0xFFFF6D00), Color(0xFFFFAB40), Color(0xFF1DE9B6), Color(0xFF00E5FF))
            49 -> listOf(Color(0xFFAA00FF), Color(0xFFF50057), Color(0xFFFF80AB), Color(0xFFFCE4EC))
            50, 55 -> listOf(Color(0xFF00E5FF), Color(0xFF1DE9B6), Color(0xFF00E676), Color(0xFFB5F36A), Color(0xFF7C4DFF))
            else -> {
                val hue1 = (palId * 37) % 360
                val hue2 = (hue1 + 75) % 360
                val hue3 = (hue1 + 160) % 360
                val hue4 = (hue1 + 240) % 360
                listOf(
                    hsvToComposeColor(hue1.toFloat(), 0.85f, 1.0f),
                    hsvToComposeColor(hue2.toFloat(), 0.78f, 1.0f),
                    hsvToComposeColor(hue3.toFloat(), 0.85f, 1.0f),
                    hsvToComposeColor(hue4.toFloat(), 0.75f, 1.0f)
                )
            }
        }
    }

    private fun sampleGradient(stops: List<Color>, t: Float): Color {
        if (stops.isEmpty()) return Color(0xFFB5F36A)
        if (stops.size == 1) return stops.first()
        val pos = t.coerceIn(0f, 1f) * (stops.size - 1)
        val idx = pos.toInt().coerceIn(0, stops.size - 2)
        val frac = (pos - idx).coerceIn(0f, 1f)
        val a = stops[idx]
        val b = stops[idx + 1]
        return Color(
            red = a.red + (b.red - a.red) * frac,
            green = a.green + (b.green - a.green) * frac,
            blue = a.blue + (b.blue - a.blue) * frac,
            alpha = 1f
        )
    }

    fun hsvToComposeColor(hue: Float, saturation: Float, value: Float): Color {
        val h = ((hue % 360f) + 360f) % 360f
        val s = saturation.coerceIn(0f, 1f)
        val v = value.coerceIn(0f, 1f)
        val c = v * s
        val x = c * (1f - kotlin.math.abs(((h / 60f) % 2f) - 1f))
        val m = v - c
        val (r1, g1, b1) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Color(
            red = (r1 + m).coerceIn(0f, 1f),
            green = (g1 + m).coerceIn(0f, 1f),
            blue = (b1 + m).coerceIn(0f, 1f),
            alpha = 1f
        )
    }

    /**
     * Generates vivid, real-time WLED LED strip preview pixels matching WLED's built-in live peek
     * renderer across segments, effects, palettes, speed, intensity, mirror, and reverse.
     */
    fun computeStripPreviewPixels(snap: WledSnapshot, phase: Float, pixelCount: Int = 60): List<Color> {
        val count = pixelCount.coerceIn(16, 120)
        if (!snap.on || snap.bri <= 0) {
            return List(count) { Color(0xFF141814) }
        }
        val masterBriFrac = (snap.bri / 255f).coerceIn(0.08f, 1f)
        val totalLeds = snap.ledCount.coerceAtLeast(1)
        val result = MutableList(count) { Color(0xFF141814) }

        snap.segments.filter { it.on && it.stop > it.start && it.bri > 0 }.forEach { seg ->
            val segBriFrac = ((seg.bri / 255f) * masterBriFrac).coerceIn(0.12f, 1f)
            val c0 = parseColor(seg.colorHex(0))
            val c1 = parseColor(seg.colorHex(1))
            val c2 = parseColor(seg.colorHex(2))
            val palStops = palettePreviewColors(seg.pal, c0, c1, c2)
            val speedFactor = 0.35f + (seg.sx / 255f) * 2.4f
            val intensityFactor = (seg.ix / 255f).coerceIn(0.15f, 1f)
            val startCell = ((seg.start.toFloat() / totalLeds) * count).toInt().coerceIn(0, count - 1)
            val stopCell = ((seg.stop.toFloat() / totalLeds) * count).toInt().coerceIn(startCell + 1, count)
            val segCells = (stopCell - startCell).coerceAtLeast(1)

            for (cell in startCell until stopCell) {
                val relIdx = cell - startCell
                var norm = relIdx.toFloat() / segCells.toFloat()
                if (seg.rev) norm = 1f - norm
                if (seg.mi) norm = if (norm < 0.5f) norm * 2f else (1f - norm) * 2f
                val animT = (norm + phase * speedFactor) % 1f
                val positiveT = if (animT < 0f) animT + 1f else animT

                val rawPixel = when {
                    seg.fx == 0 -> c0 // Solid
                    seg.fx in listOf(1, 2, 12, 23, 100) -> {
                        // Blink / Breathe / Fade / Heartbeat
                        val pulse = (0.55f + 0.45f * kotlin.math.sin((phase * speedFactor * 6.2831855f).toDouble()).toFloat())
                        luminousLedColor(c0, segBriFrac * pulse)
                    }
                    seg.fx in listOf(8, 9, 14, 30, 33, 63, 94, 99) -> {
                        // Rainbow / Colorloop / Pride
                        val hue = ((positiveT * 360f * (0.5f + intensityFactor)) % 360f).coerceIn(0f, 360f)
                        hsvToComposeColor(hue, 0.9f, 1f)
                    }
                    seg.fx in listOf(10, 11, 28, 29, 37, 40, 60) -> {
                        // Scan / Chase / Scanner
                        val head = (phase * speedFactor) % 1f
                        val dist = kotlin.math.abs(norm - head)
                        val spot = (1f - (dist * (3.5f + intensityFactor * 4f))).coerceIn(0f, 1f)
                        val base = sampleGradient(palStops, positiveT)
                        Color(
                            red = (base.red * (0.45f + 0.55f * spot)).coerceIn(0f, 1f),
                            green = (base.green * (0.45f + 0.55f * spot)).coerceIn(0f, 1f),
                            blue = (base.blue * (0.45f + 0.55f * spot)).coerceIn(0f, 1f),
                            alpha = 1f
                        )
                    }
                    seg.fx in listOf(45, 66, 88, 102, 142, 148) -> {
                        // Fire / Candle / Flicker
                        val flicker = 0.72f + 0.28f * kotlin.math.sin(((norm * 13f + phase * speedFactor * 9f)).toDouble()).toFloat()
                        val fireStops = if (seg.pal == 0) palettePreviewColors(35, c0, c1, c2) else palStops
                        luminousLedColor(sampleGradient(fireStops, (norm * 0.7f + 0.3f * flicker).coerceIn(0f, 1f)), segBriFrac * flicker)
                    }
                    else -> {
                        // Palette / Wave / Aurora / Pacifica / AudioReactive
                        val wave = 0.8f + 0.2f * kotlin.math.sin(((norm * 6.2831855f * (1f + intensityFactor)) + phase * speedFactor * 6.2831855f).toDouble()).toFloat()
                        luminousLedColor(sampleGradient(palStops, positiveT), segBriFrac * wave)
                    }
                }
                result[cell] = luminousLedColor(rawPixel, segBriFrac)
            }
        }
        return result
    }

    fun defaultSnapshot(): WledSnapshot = WledSnapshot.parse(
        obj(
            "state" to obj(
                "on" to true,
                "bri" to 200,
                "transition" to 7,
                "ps" to 1,
                "pl" to -1,
                "lor" to 0,
                "mainseg" to 0,
                "nl" to obj("on" to false, "dur" to 60, "mode" to 1, "tbri" to 0, "rem" to -1),
                "udpn" to obj("send" to false, "recv" to true, "sgrp" to 1, "rgrp" to 1),
                "seg" to JSONArray()
                    .put(
                        obj(
                            "id" to 0,
                            "start" to 0,
                            "stop" to 150,
                            "len" to 150,
                            "grp" to 1,
                            "spc" to 0,
                            "of" to 0,
                            "on" to true,
                            "frz" to false,
                            "bri" to 255,
                            "cct" to 127,
                            "n" to "Ceiling Cove",
                            "col" to JSONArray()
                                .put(JSONArray(listOf(181, 243, 106, 0)))
                                .put(JSONArray(listOf(0, 229, 255, 0)))
                                .put(JSONArray(listOf(255, 170, 64, 0))),
                            "fx" to 65,
                            "sx" to 128,
                            "ix" to 160,
                            "pal" to 50,
                            "c1" to 128,
                            "c2" to 128,
                            "c3" to 16,
                            "sel" to true,
                            "rev" to false,
                            "mi" to false,
                            "o1" to false,
                            "o2" to false,
                            "o3" to false
                        )
                    )
                    .put(
                        obj(
                            "id" to 1,
                            "start" to 150,
                            "stop" to 300,
                            "len" to 150,
                            "grp" to 1,
                            "spc" to 0,
                            "of" to 0,
                            "on" to true,
                            "frz" to false,
                            "bri" to 255,
                            "cct" to 127,
                            "n" to "Desk Backlight",
                            "col" to JSONArray()
                                .put(JSONArray(listOf(181, 243, 106, 0)))
                                .put(JSONArray(listOf(0, 229, 255, 0)))
                                .put(JSONArray(listOf(255, 170, 64, 0))),
                            "fx" to 65,
                            "sx" to 128,
                            "ix" to 160,
                            "pal" to 50,
                            "c1" to 128,
                            "c2" to 128,
                            "c3" to 16,
                            "sel" to true,
                            "rev" to false,
                            "mi" to false,
                            "o1" to false,
                            "o2" to false,
                            "o3" to false
                        )
                    )
            ),
            "info" to obj(
                "ver" to "0.16.0-room",
                "vid" to 2610210,
                "cn" to "Kosen",
                "release" to "ESP32-WLED-Room",
                "name" to "Bedroom WLED",
                "udpport" to 21324,
                "live" to false,
                "lm" to "",
                "lip" to "",
                "ws" to 1,
                "fxcount" to EFFECTS.size,
                "palcount" to PALETTES.size,
                "cpalcount" to 0,
                "arch" to "esp32",
                "core" to "v4.4.7",
                "freeheap" to 168420,
                "uptime" to 14820,
                "opt" to 79,
                "brand" to "WLED",
                "product" to "FOSS",
                "mac" to "aabbcc112233",
                "ip" to "192.168.1.105",
                "fs" to obj("u" to 24, "t" to 983, "pmt" to 1729550000),
                "wifi" to obj("bssid" to "AA:BB:CC:DD:EE:FF", "rssi" to -52, "signal" to 92, "channel" to 6),
                "leds" to obj(
                    "count" to 300,
                    "pwr" to 640,
                    "fps" to 42,
                    "maxpwr" to 8500,
                    "maxseg" to 32,
                    "lc" to 7,
                    "seglc" to JSONArray(listOf(7))
                )
            )
        )
    )

    fun defaultPresets(): JSONObject = JSONObject(
        """{
            "0": {},
            "1": {"n": "Aurora Focus", "ql": "AF", "on": true, "bri": 200, "ib": true, "sb": true, "seg": [{"id": 0, "fx": 65, "pal": 50, "col": [[181, 243, 106, 0], [0, 229, 255, 0], [255, 170, 64, 0]]}]},
            "2": {"n": "Warm Candlelight", "ql": "WC", "on": true, "bri": 175, "ib": true, "sb": true, "seg": [{"id": 0, "fx": 88, "pal": 35, "col": [[255, 147, 41, 35], [255, 87, 34, 0], [255, 200, 100, 0]]}]},
            "3": {"n": "Sunset Pacifica", "ql": "SP", "on": true, "bri": 215, "ib": true, "sb": true, "seg": [{"id": 0, "fx": 101, "pal": 13, "col": [[255, 94, 77, 0], [170, 0, 255, 0], [255, 209, 102, 0]]}]},
            "4": {"n": "Audio Gravcenter ♫", "ql": "AG", "on": true, "bri": 225, "ib": true, "sb": true, "seg": [{"id": 0, "fx": 155, "pal": 11, "sx": 140, "ix": 185}]},
            "5": {"n": "Audio GEQ Spectrum ♫", "ql": "EQ", "on": true, "bri": 220, "ib": true, "sb": true, "seg": [{"id": 0, "fx": 139, "pal": 37, "sx": 150, "ix": 195}]},
            "10": {"n": "Evening Ambient Cycle", "playlist": {"ps": [1, 2, 3], "dur": [150, 200, 180], "transition": [12, 15, 12], "repeat": 0, "end": 1, "r": false}}
        }"""
    )

    fun defaultConfig(): JSONObject = JSONObject(
        """{
            "rev": [1, 0],
            "vid": 2610210,
            "id": {"mdns": "wled-room", "name": "Bedroom WLED", "inv": "Room Light"},
            "nw": {"ins": [{"ssid": "Home-WiFi-2.4G", "pskl": 12, "ip": [0, 0, 0, 0], "gw": [0, 0, 0, 0], "sn": [255, 255, 255, 0]}]},
            "ap": {"ssid": "WLED-AP", "pskl": 8, "chan": 1, "hide": 0, "apbeh": 0, "ip": [4, 3, 2, 1]},
            "wifi": {"sleep": false, "phy": false},
            "hw": {
                "led": {
                    "total": 300,
                    "maxpwr": 8500,
                    "ledma": 55,
                    "cct": false,
                    "cr": false,
                    "cb": 0,
                    "fps": 42,
                    "rgbwm": 0,
                    "ins": [{"start": 0, "len": 300, "pin": [23], "order": 0, "rev": false, "skip": 0, "type": 22, "ref": false, "rgbwm": 0}]
                },
                "btn": {
                    "max": 4,
                    "pull": true,
                    "ins": [
                        {"type": 2, "pin": [0], "macros": [1, 2, 0]},
                        {"type": 0, "pin": [-1], "macros": [0, 0, 0]},
                        {"type": 0, "pin": [-1], "macros": [0, 0, 0]},
                        {"type": 0, "pin": [-1], "macros": [0, 0, 0]}
                    ],
                    "tt": 32,
                    "mqtt": false
                },
                "ir": {"pin": 19, "type": 0, "sel": true},
                "relay": {"pin": -1, "rev": false}
            },
            "light": {
                "scale-bri": 100,
                "pal-mode": 0,
                "aseg": false,
                "gc": {"bri": 1.0, "col": 2.8, "val": 2.8},
                "tr": {"mode": true, "dur": 7, "pal": 0, "rpc": 5},
                "nl": {"mode": 1, "dur": 60, "tbri": 0, "macro": 0}
            },
            "def": {"ps": 1, "on": true, "bri": 180},
            "if": {
                "sync": {
                    "port0": 21324,
                    "port1": 65506,
                    "recv": {"bri": true, "col": true, "fx": true, "grp": 1, "seg": false, "sb": false},
                    "send": {"dir": false, "btn": false, "va": false, "hue": true, "macro": false, "grp": 1, "ret": 0}
                },
                "live": {
                    "en": true,
                    "mso": false,
                    "port": 5568,
                    "mc": false,
                    "dmx": {"uni": 1, "seqskip": false, "e131prio": 0, "addr": 1, "dss": 0, "mode": 4},
                    "timeout": 25,
                    "maxbri": false,
                    "no-gc": true,
                    "offset": 0
                },
                "va": {"alexa": false, "macros": [0, 0], "p": 0},
                "mqtt": {"en": false, "broker": "", "port": 1883, "user": "", "pskl": 0, "cid": "WLED-Room", "topics": {"device": "wled/room", "group": "wled/all"}, "rtn": false},
                "hue": {"en": false, "id": 1, "ip": [192, 168, 1, 1], "hiv": 25, "recv": {"on": true, "bri": true, "col": true}}
            },
            "ntp": {"en": true, "host": "pool.ntp.org", "tz": 0, "offset": 19800, "ampm": false, "ln": 77.2, "lt": 28.6},
            "timers": {
                "cntdwn": {"goal": [2026, 1, 1, 0, 0, 0], "macro": 0},
                "ins": [
                    {"en": 1, "hour": 19, "min": 0, "macro": 1, "dow": 127, "ps": 1},
                    {"en": 0, "hour": 23, "min": 30, "macro": 2, "dow": 127, "ps": 2}
                ]
            },
            "ota": {"lock": false, "lock-wifi": false, "pskl": 0, "aota": false},
            "um": {
                "AudioReactive": {
                    "enabled": true,
                    "squelch": 10,
                    "gain": 64,
                    "agc": 1,
                    "dmic": {"type": 1},
                    "resources": {"pin-sd": 35, "pin-ws": 25, "pin-sck": 26},
                    "dynamics": {"limiter": true, "rise": 80, "fall": 1400},
                    "frequency": {"scale": 3},
                    "sync": {"port": 11988, "mode": 0}
                },
                "RoomBridge": {
                    "quiet_behavior": "dim",
                    "quiet_seconds": 10
                }
            }
        }"""
    )

    /**
     * Applies a `/json/state` command payload directly to a local `WledSnapshot` and `presets` object
     * so that when the user interacts with any WLED control in offline/preview mode, the UI updates
     * immediately and accurately.
     */
    fun applyLocalCommand(
        current: WledSnapshot,
        presets: JSONObject,
        body: JSONObject
    ): Pair<WledSnapshot, JSONObject> {
        val nextState = current.state.copyJson()
        val nextPresets = presets.copyJson()

        if (body.has("on")) {
            val onVal = body.opt("on")
            if (onVal == "t") nextState.put("on", !nextState.optBoolean("on", true))
            else if (onVal is Boolean) nextState.put("on", onVal)
        }
        if (body.has("bri")) nextState.put("bri", body.optInt("bri", nextState.optInt("bri", 180)).coerceIn(0, 255))
        if (body.has("transition")) nextState.put("transition", body.optInt("transition", 7).coerceIn(0, 65535))
        if (body.has("lor")) nextState.put("lor", body.optInt("lor", 0).coerceIn(0, 2))
        if (body.has("mainseg")) nextState.put("mainseg", body.optInt("mainseg", 0).coerceAtLeast(0))

        body.optJSONObject("nl")?.let { nlPatch ->
            val nl = (nextState.optJSONObject("nl") ?: JSONObject()).copyJson()
            nlPatch.keys().forEach { k -> nl.put(k, nlPatch.get(k)) }
            if (nl.optBoolean("on", false) && nl.optInt("rem", -1) <= 0) {
                nl.put("rem", nl.optInt("dur", 60) * 60)
            } else if (!nl.optBoolean("on", false)) {
                nl.put("rem", -1)
            }
            nextState.put("nl", nl)
        }

        body.optJSONObject("udpn")?.let { udpPatch ->
            val udp = (nextState.optJSONObject("udpn") ?: JSONObject()).copyJson()
            udpPatch.keys().forEach { k -> udp.put(k, udpPatch.get(k)) }
            nextState.put("udpn", udp)
        }

        if (body.has("psave")) {
            val saveId = body.optInt("psave", 0)
            if (saveId in 1..250) {
                if (body.has("playlist")) {
                    nextPresets.put(
                        saveId.toString(),
                        obj(
                            "n" to body.optString("n", "Playlist $saveId"),
                            "playlist" to body.optJSONObject("playlist")?.copyJson()
                        )
                    )
                } else {
                    val savedEntry = obj(
                        "n" to body.optString("n", "Preset $saveId"),
                        "ql" to body.optString("ql", ""),
                        "on" to nextState.optBoolean("on", true),
                        "bri" to nextState.optInt("bri", 180),
                        "ib" to body.optBoolean("ib", true),
                        "sb" to body.optBoolean("sb", true),
                        "seg" to (nextState.optJSONArray("seg")?.copyJsonArray() ?: JSONArray())
                    )
                    nextPresets.put(saveId.toString(), savedEntry)
                    nextState.put("ps", saveId)
                }
            }
        }

        if (body.has("pdel")) {
            val delId = body.optInt("pdel", 0)
            if (delId in 1..250) {
                nextPresets.remove(delId.toString())
            }
        }

        if (body.has("pl")) {
            val plId = body.optInt("pl", -1)
            nextState.put("pl", plId)
        }

        if (body.has("ps")) {
            val psId = body.optInt("ps", -1)
            nextState.put("ps", psId)
            val entry = nextPresets.optJSONObject(psId.toString())
            if (entry != null) {
                if (entry.has("playlist")) {
                    nextState.put("pl", psId)
                    nextState.put("on", true)
                } else {
                    nextState.put("pl", -1)
                    if (entry.has("on")) nextState.put("on", entry.optBoolean("on", true))
                    if (entry.has("bri")) nextState.put("bri", entry.optInt("bri", nextState.optInt("bri", 180)))
                    entry.optJSONArray("seg")?.let { savedSegs ->
                        val curSegs = nextState.optJSONArray("seg") ?: JSONArray()
                        for (i in 0 until savedSegs.length()) {
                            val sPatch = savedSegs.optJSONObject(i) ?: continue
                            val targetId = sPatch.optInt("id", i)
                            var found = false
                            for (j in 0 until curSegs.length()) {
                                val existing = curSegs.optJSONObject(j) ?: continue
                                val matchesTarget = existing.optInt("id", j) == targetId
                                val propagateVisualsToSelected = savedSegs.length() == 1 && existing.optBoolean("sel", true)
                                if (matchesTarget) {
                                    sPatch.keys().forEach { k -> existing.put(k, sPatch.get(k)) }
                                    found = true
                                } else if (propagateVisualsToSelected) {
                                    listOf("col", "fx", "pal", "sx", "ix", "c1", "c2", "c3", "cct", "on").forEach { k ->
                                        if (sPatch.has(k)) existing.put(k, sPatch.get(k))
                                    }
                                }
                            }
                            if (!found) curSegs.put(sPatch.copyJson())
                        }
                        nextState.put("seg", curSegs)
                    }
                }
            }
        }

        body.optJSONArray("seg")?.let { segPatches ->
            val curSegs = nextState.optJSONArray("seg") ?: JSONArray()
            val updatedList = mutableListOf<JSONObject>()
            for (i in 0 until curSegs.length()) {
                curSegs.optJSONObject(i)?.let { updatedList.add(it.copyJson()) }
            }
            for (i in 0 until segPatches.length()) {
                val patch = segPatches.optJSONObject(i) ?: continue
                val id = patch.optInt("id", i)
                val existingIdx = updatedList.indexOfFirst { it.optInt("id", -1) == id }
                if (patch.has("stop") && patch.optInt("stop", -1) == 0) {
                    if (existingIdx >= 0 && updatedList.size > 1) {
                        updatedList.removeAt(existingIdx)
                    }
                } else if (existingIdx >= 0) {
                    val target = updatedList[existingIdx]
                    patch.keys().forEach { k -> target.put(k, patch.get(k)) }
                    val start = target.optInt("start", 0)
                    val stop = target.optInt("stop", 300)
                    target.put("len", (stop - start).coerceAtLeast(1))
                    // If this is a pure visual patch (color/effect/palette/speed/intensity) without geometry bounds,
                    // mirror the visual attributes to all other selected segments so the second half never desyncs.
                    val isGeometryEdit = patch.has("start") || patch.has("stop") || patch.has("n") || patch.has("sel") || patch.has("grp") || patch.has("spc")
                    if (segPatches.length() == 1 && !isGeometryEdit && target.optBoolean("sel", true)) {
                        updatedList.forEachIndexed { idx, other ->
                            if (idx != existingIdx && other.optBoolean("sel", true)) {
                                listOf("col", "fx", "pal", "sx", "ix", "c1", "c2", "c3", "cct").forEach { k ->
                                    if (patch.has(k)) other.put(k, patch.get(k))
                                }
                            }
                        }
                    }
                } else {
                    val start = patch.optInt("start", 0)
                    val stop = patch.optInt("stop", 300)
                    val newSeg = obj(
                        "id" to id,
                        "start" to start,
                        "stop" to stop,
                        "len" to (stop - start).coerceAtLeast(1),
                        "grp" to patch.optInt("grp", 1),
                        "spc" to patch.optInt("spc", 0),
                        "of" to patch.optInt("of", 0),
                        "on" to patch.optBoolean("on", true),
                        "frz" to patch.optBoolean("frz", false),
                        "bri" to patch.optInt("bri", 255),
                        "cct" to patch.optInt("cct", 127),
                        "n" to patch.optString("n", "Segment ${id + 1}"),
                        "col" to (patch.optJSONArray("col") ?: JSONArray()
                            .put(JSONArray(listOf(181, 243, 106, 0)))
                            .put(JSONArray(listOf(0, 0, 0, 0)))
                            .put(JSONArray(listOf(0, 0, 0, 0)))),
                        "fx" to patch.optInt("fx", 0),
                        "sx" to patch.optInt("sx", 128),
                        "ix" to patch.optInt("ix", 128),
                        "pal" to patch.optInt("pal", 0),
                        "c1" to patch.optInt("c1", 128),
                        "c2" to patch.optInt("c2", 128),
                        "c3" to patch.optInt("c3", 16),
                        "sel" to patch.optBoolean("sel", true),
                        "rev" to patch.optBoolean("rev", false),
                        "mi" to patch.optBoolean("mi", false),
                        "o1" to patch.optBoolean("o1", false),
                        "o2" to patch.optBoolean("o2", false),
                        "o3" to patch.optBoolean("o3", false)
                    )
                    updatedList.add(newSeg)
                }
            }
            nextState.put("seg", JSONArray(updatedList))
        }

        return WledSnapshot(nextState, current.info) to nextPresets
    }
}

private fun JSONArray.copyJsonArray(): JSONArray = JSONArray(toString())
