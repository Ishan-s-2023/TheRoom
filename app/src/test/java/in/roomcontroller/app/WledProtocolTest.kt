package `in`.roomcontroller.app

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class WledProtocolTest {
    private fun state(power: Boolean = true) = obj(
        "state" to obj(
            "on" to power,
            "bri" to 128,
            "transition" to 7,
            "ps" to 2,
            "pl" to -1,
            "lor" to 0,
            "mainseg" to 0,
            "nl" to obj("on" to true, "dur" to 45, "mode" to 2, "tbri" to 10, "rem" to 1800),
            "udpn" to obj("send" to false, "recv" to true, "sgrp" to 1, "rgrp" to 1),
            "seg" to JSONArray().put(
                obj(
                    "id" to 0, "start" to 0, "stop" to 150, "len" to 150, "grp" to 1, "spc" to 0, "of" to 0,
                    "on" to true, "frz" to false, "bri" to 255, "cct" to 127, "n" to "Desk Strip",
                    "col" to JSONArray().put(JSONArray(listOf(255, 136, 0, 12))).put(JSONArray(listOf(0, 0, 0, 0))).put(JSONArray(listOf(0, 255, 128, 0))),
                    "fx" to 14, "sx" to 140, "ix" to 180, "pal" to 11, "c1" to 64, "c2" to 128, "c3" to 16,
                    "sel" to true, "rev" to true, "mi" to false, "o1" to true, "o2" to false, "o3" to false
                )
            )
        ),
        "info" to obj(
            "ver" to "0.16.0-room",
            "vid" to 2610210,
            "mac" to "AA:BB:CC:11:22:33",
            "name" to "Bedroom WLED",
            "leds" to obj("count" to 300, "pwr" to 420, "fps" to 42, "maxpwr" to 8500, "maxseg" to 16, "lc" to 7)
        )
    )

    @Test fun acceptsIpv4HostnamesPortsAndIpv6() {
        assertEquals("http://192.168.1.105", wledOrigin("192.168.1.105/"))
        assertEquals("http://wled.local:8080", wledOrigin("HTTP://WLED.local:8080"))
        assertEquals("http://[fd00::1]", wledOrigin("http://[fd00::1]"))
    }

    @Test fun rejectsCredentialsPathsQueriesAndNonWebSchemes() {
        listOf(
            "http://owner:secret@wled.local", "http://wled.local/settings", "http://wled.local?token=abc",
            "http://wled.local#anything", "file:///etc/config", "javascript:alert(1)", "http://wled.local:99999"
        ).forEach {
            assertTrue(it, runCatching { wledOrigin(it) }.isFailure)
        }
    }

    @Test fun originBoundaryIncludesSchemeAndPort() {
        assertTrue(sameWledOrigin("http://wled.local", "http://wled.local:80/settings"))
        assertFalse(sameWledOrigin("http://wled.local", "http://wled.local.evil/settings"))
        assertFalse(sameWledOrigin("http://wled.local", "https://wled.local/settings"))
        assertFalse(sameWledOrigin("http://wled.local", "http://wled.local:8080/settings"))
        assertFalse(sameWledOrigin("http://wled.local", "http://user@wled.local/settings"))
    }

    @Test fun roomFirmwareResponseIsNotAcceptedAsWled() {
        assertTrue(runCatching { WledSnapshot.parse(obj("runtime" to obj("version" to "1.1.3-controls"))) }.isFailure)
        assertEquals("aabbcc112233", wledDeviceId(state().getJSONObject("info")))
    }

    @Test fun parsesFullWledSnapshotSegmentsNightlightAndCapabilities() {
        val snap = WledSnapshot.parse(state(true))
        assertTrue(snap.on)
        assertEquals(128, snap.bri)
        assertEquals(7, snap.transition)
        assertEquals(2, snap.activePreset)
        assertEquals(300, snap.ledCount)
        assertEquals(420, snap.estimatedPowerMa)
        assertEquals(8500, snap.maxPowerMa)
        assertTrue(snap.supportsWhite)
        assertTrue(snap.supportsCct)
        assertFalse(snap.has2dMatrix)

        assertTrue(snap.nightlight.on)
        assertEquals(45, snap.nightlight.durationMinutes)
        assertEquals(2, snap.nightlight.mode)
        assertEquals(10, snap.nightlight.targetBrightness)
        assertEquals(1800, snap.nightlight.remainingSeconds)

        val seg = snap.primarySegment
        assertEquals(0, seg.id)
        assertEquals("Desk Strip", seg.name)
        assertEquals(0, seg.start)
        assertEquals(150, seg.stop)
        assertEquals("#FF8800", seg.colorHex(0))
        assertEquals(12, seg.whiteChannel(0))
        assertEquals("#00FF80", seg.colorHex(2))
        assertEquals(14, seg.fx)
        assertEquals(140, seg.sx)
        assertEquals(180, seg.ix)
        assertEquals(11, seg.pal)
        assertTrue(seg.rev)
        assertTrue(seg.o1)
    }

    @Test fun parsesDynamicFxdataSlidersOptionsColorsAndAudioFlags() {
        val gravMeta = WledEffectMetadata.parse(
            id = 142,
            name = "Gravcenter",
            fxdataRaw = "Rate,Sensitivity,Decay,,;Center,Bg,!;!;1vf;sx=128,ix=160,pal=11"
        )
        assertEquals(listOf("Rate", "Sensitivity", "Decay", null, null), gravMeta.sliderLabels)
        assertEquals(listOf("Center", "Bg", "Tertiary (Cs)"), gravMeta.colorLabels)
        assertTrue(gravMeta.usesPalette)
        assertTrue(gravMeta.supports1D)
        assertFalse(gravMeta.supports2D)
        assertTrue(gravMeta.isVolumeReactive)
        assertTrue(gravMeta.isFrequencyReactive)
        assertTrue(gravMeta.isAudioReactive)
        assertEquals(160, gravMeta.defaults["ix"])

        val solidMeta = WledEffectMetadata.parse(0, "Solid", ";;;;")
        assertEquals(listOf(null, null, null, null, null), solidMeta.sliderLabels)
        assertFalse(solidMeta.usesPalette)
        assertFalse(solidMeta.isAudioReactive)
    }

    @Test fun buildsIsolatedSegmentPatchWithoutClobberingOtherSegments() {
        val patch = buildSegmentPatch(listOf(1, 3), obj("fx" to 9, "sx" to 200))
        val segArr = patch.getJSONArray("seg")
        assertEquals(2, segArr.length())
        assertEquals(1, segArr.getJSONObject(0).getInt("id"))
        assertEquals(9, segArr.getJSONObject(0).getInt("fx"))
        assertEquals(3, segArr.getJSONObject(1).getInt("id"))
        assertEquals(200, segArr.getJSONObject(1).getInt("sx"))
    }

    @Test fun parsesAndBuildsWledPresetsAndPlaylists() {
        val presetsJson = JSONObject(
            """{
                "0": {},
                "1": {"n": "Warm Focus", "ql": "WF", "on": true, "bri": 190, "ib": true, "sb": true},
                "2": {"n": "Cool Calm", "on": true, "bri": 120},
                "10": {"n": "Evening Cycle", "playlist": {"ps": [1, 2], "dur": [150, 200], "transition": [10, 15], "repeat": 0, "end": 1, "r": true}}
            }"""
        )
        val parsed = parseWledPresets(presetsJson)
        assertEquals(3, parsed.size)
        assertEquals("Warm Focus", parsed[0].name)
        assertEquals("WF", parsed[0].quickLabel)
        assertFalse(parsed[0].isPlaylist)

        val pl = parsed[2]
        assertTrue(pl.isPlaylist)
        assertEquals(listOf(1, 2), pl.playlist!!.presets)
        assertEquals(listOf(150, 200), pl.playlist!!.durationsTenths)
        assertTrue(pl.playlist!!.shuffle)

        val savePreset = buildPresetSavePayload(5, "Night Glow", "NG", includeBrightness = true, saveSegmentBounds = false)
        assertEquals(5, savePreset.getInt("psave"))
        assertEquals("Night Glow", savePreset.getString("n"))
        assertFalse(savePreset.getBoolean("sb"))
    }

    @Test fun enforcesReservedRoomGpioPinsAndPreservesUnknownConfigFields() {
        assertNull(RoomHardwarePins.validatePinForRole(23, "led", requireOutput = true))
        assertNotNull(RoomHardwarePins.validatePinForRole(18, "led", requireOutput = true)) // Reserved for IR TX
        assertNotNull(RoomHardwarePins.validatePinForRole(35, "led", requireOutput = true)) // Input-only & Mic SD
        assertNull(RoomHardwarePins.validatePinForRole(35, "mic-sd", requireOutput = false))
        assertNull(RoomHardwarePins.validatePinForRole(26, "mic-sck", requireOutput = true))

        val baseCfg = JSONObject("""{"rev":[1,0],"hw":{"led":{"total":300,"custom_fw_flag":42}},"nw":{"ins":[{"ssid":"Home","psk":"********"}]}}""")
        val patch = obj("hw" to obj("led" to obj("total" to 240, "maxpwr" to 6000)))
        val merged = deepMergeJson(baseCfg, patch)
        stripUnchangedSecrets(merged, patch)
        assertEquals(240, merged.getJSONObject("hw").getJSONObject("led").getInt("total"))
        assertEquals(42, merged.getJSONObject("hw").getJSONObject("led").getInt("custom_fw_flag"))
        assertFalse(merged.getJSONObject("nw").getJSONArray("ins").getJSONObject(0).has("psk"))
    }

    @Test fun evaluatesAudioReadinessWithoutFabricatingTelemetryForDisabledOrFailedMic() {
        val disabledCfg = obj("um" to obj("AudioReactive" to obj("enabled" to false)))
        val statusDisabled = evaluateWledAudioStatus(
            wledOnline = true,
            snapshot = WledSnapshot.parse(state()),
            cfg = disabledCfg,
            roomState = obj("telemetry" to obj("bands" to JSONArray(listOf(0.8, 0.7, 0.6)))),
            roomStale = false
        )
        assertEquals(AudioReadinessState.MIC_DISABLED, statusDisabled.readiness)
        assertTrue(statusDisabled.bands.isEmpty())

        val healthyCfg = obj("um" to obj("AudioReactive" to obj("enabled" to true, "squelch" to 12, "gain" to 64)))
        val statusHealthy = evaluateWledAudioStatus(
            wledOnline = true,
            snapshot = WledSnapshot.parse(state()),
            cfg = healthyCfg,
            roomState = obj("runtime" to obj("audio_age_ms" to 250, "drivers" to obj("microphone" to true)), "telemetry" to obj("bands" to JSONArray(listOf(0.5, 0.4, 0.3)))),
            roomStale = false
        )
        assertEquals(AudioReadinessState.HEALTHY_LOCAL, statusHealthy.readiness)
        assertEquals(3, statusHealthy.bands.size)
    }

    @Test fun parsesBinaryAndJsonLiveLedPreviewFrames() {
        val binaryFrame = byteArrayOf('L'.code.toByte(), 1, 255.toByte(), 0, 128.toByte(), 0, 255.toByte(), 64)
        val leds = parseLiveLedFrame(binaryFrame)
        assertEquals(listOf(0xFF0080, 0x00FF40), leds)

        val jsonFrame = obj("leds" to JSONArray(listOf("FF8800", "00FF2A")))
        assertEquals(listOf(0xFF8800, 0x00FF2A), parseLiveLedJson(jsonFrame))
    }

    @Test fun sendsRealCommandThenReadsAuthoritativeState() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{\"success\":true}"))
            server.enqueue(MockResponse().setBody(state(false).toString())); server.start()
            val result = WledApi().command(server.url("/").toString(), obj("on" to false))
            val command = server.takeRequest(); val payload = JSONObject(command.body.readUtf8())
            assertEquals("POST", command.method); assertEquals("/json/state", command.path)
            assertNull(command.getHeader("Authorization")); assertFalse(payload.getBoolean("on")); assertTrue(payload.getBoolean("v"))
            assertFalse(result.state.getBoolean("on")); assertEquals("/json/si", server.takeRequest().path)
        }
    }

    @Test fun failedAcknowledgementDoesNotBecomeSuccess() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{\"error\":9}")); server.start()
            assertTrue(runCatching { WledApi().command(server.url("/").toString(), obj("ps" to 1)) }.isFailure)
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun redirectCannotForwardACommandToAnotherHost() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(307).setHeader("Location", "/redirected")); server.start()
            assertTrue(runCatching { WledApi().command(server.url("/").toString(), obj("on" to true)) }.isFailure)
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun disconnectDoesNotReplayAPost() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)); server.start()
            val client = OkHttpClient.Builder().readTimeout(300, java.util.concurrent.TimeUnit.MILLISECONDS).retryOnConnectionFailure(false).build()
            assertTrue(runCatching { WledApi(client).command(server.url("/").toString(), obj("ps" to 4)) }.isFailure)
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun brightnessIsClampedToTheWledByteRange() {
        assertEquals(0, wledByte(-1f)); assertEquals(255, wledByte(101f)); assertEquals(255, wledByte(100f))
    }

    @Test fun noWebViewOrWledPortalRemainsInMainSources() {
        val srcDir = listOf(File("src/main/java/in/roomcontroller/app"), File("app/src/main/java/in/roomcontroller/app"))
            .first { it.isDirectory }
        val ktFiles = srcDir.listFiles { f -> f.extension == "kt" }.orEmpty()
        assertTrue(ktFiles.isNotEmpty())
        assertFalse(File(srcDir, "WledBrowser.kt").exists())
        assertFalse(File(srcDir, "WledTheme.kt").exists())
        ktFiles.forEach { file ->
            val content = file.readText()
            assertFalse("${file.name} still references android.webkit.WebView", content.contains("android.webkit.WebView"))
            assertFalse("${file.name} still references androidx.webkit", content.contains("androidx.webkit"))
            assertFalse("${file.name} still calls WledBrowser", content.contains("WledBrowser("))
        }
    }

    @Test fun catalogDefaultsAndLocalCommandEngineSupportFullWledWorkspaceOffline() {
        assertTrue(WledCatalogDefaults.EFFECTS.size >= 170)
        assertEquals(71, WledCatalogDefaults.PALETTES.size)
        assertEquals(WledCatalogDefaults.EFFECTS.size, WledCatalogDefaults.FXDATA.size)

        val snap0 = WledCatalogDefaults.defaultSnapshot()
        val presets0 = WledCatalogDefaults.defaultPresets()
        assertTrue(snap0.on)
        assertEquals(2, snap0.segments.size)

        val (snap1, presets1) = WledCatalogDefaults.applyLocalCommand(
            snap0,
            presets0,
            obj(
                "on" to false,
                "bri" to 90,
                "nl" to obj("on" to true, "dur" to 30),
                "udpn" to obj("send" to true, "recv" to true),
                "seg" to JSONArray().put(obj("id" to 0, "fx" to 155, "pal" to 11, "m12" to 2))
            )
        )
        assertFalse(snap1.on)
        assertEquals(90, snap1.bri)
        assertTrue(snap1.nightlight.on)
        assertEquals(30, snap1.nightlight.durationMinutes)
        assertTrue(snap1.udpSync.send)
        assertEquals(155, snap1.primarySegment.fx)
        assertEquals(11, snap1.primarySegment.pal)
        assertEquals(2, snap1.primarySegment.m12)

        val (snap2, presets2) = WledCatalogDefaults.applyLocalCommand(
            snap1,
            presets1,
            buildPresetSavePayload(9, "Custom Test", "CT", includeBrightness = true, saveSegmentBounds = true)
        )
        assertEquals(9, snap2.activePreset)
        assertTrue(parseWledPresets(presets2).any { it.id == 9 && it.name == "Custom Test" })
    }

    @Test fun luminousStripPreviewPixelsNeverRenderMuddyDarkColors() {
        val snap = WledCatalogDefaults.defaultSnapshot()
        val pixels = WledCatalogDefaults.computeStripPreviewPixels(snap, phase = 0.25f, pixelCount = 60)
        assertEquals(60, pixels.size)
        pixels.forEach { c ->
            val maxChannel = maxOf(c.red, c.green, c.blue)
            assertTrue("Expected bright luminous LED pixel, got $c", maxChannel > 0.35f)
        }

        val lowBrightnessEmission = WledCatalogDefaults.luminousLedColor(androidx.compose.ui.graphics.Color(0xFFFF8800), 0.1f)
        assertTrue("Expected luminous color even at low brightness", lowBrightnessEmission.red > 0.35f)
    }

    @Test fun presetCategoriesOrganizeAndPersistCustomAssignments() {
        val entries = parseWledPresets(WledCatalogDefaults.defaultPresets())
        assertTrue(entries.isNotEmpty())

        val auroraFocus = entries.first { it.id == 1 }
        val warmCandlelight = entries.first { it.id == 2 }
        val soundParty = entries.first { it.id == 4 }
        val playlistEntry = entries.first { it.isPlaylist }

        assertEquals(PresetCategories.FOCUS_WORK, PresetCategories.inferForWledPreset(auroraFocus, emptyMap()))
        assertEquals(PresetCategories.AMBIENT, PresetCategories.inferForWledPreset(warmCandlelight, emptyMap()))
        assertEquals(PresetCategories.MUSIC_SOUND, PresetCategories.inferForWledPreset(soundParty, emptyMap()))
        assertEquals(PresetCategories.PLAYLISTS, PresetCategories.inferForWledPreset(playlistEntry, emptyMap()))

        val customMap = mapOf("wled:1" to PresetCategories.VIBRANT_FX, "room:focus" to PresetCategories.AMBIENT)
        val serialized = PresetCategories.serializeCategoryMap(customMap)
        val parsedMap = PresetCategories.parseCategoryMap(serialized)
        assertEquals(customMap, parsedMap)
        assertEquals(PresetCategories.VIBRANT_FX, PresetCategories.inferForWledPreset(auroraFocus, parsedMap))

        assertTrue(
            PresetCategories.matchesFilter(
                filterCategory = PresetCategories.FAVORITES,
                assignedCategory = PresetCategories.AMBIENT,
                isFavorite = true,
                isPlaylist = false,
                isRoomScene = false
            )
        )
        assertFalse(
            PresetCategories.matchesFilter(
                filterCategory = PresetCategories.MUSIC_SOUND,
                assignedCategory = PresetCategories.AMBIENT,
                isFavorite = true,
                isPlaylist = false,
                isRoomScene = false
            )
        )
    }

    @Test fun homeScreenCapsAndEsp32NativeMusicPipelinePayloadsMatchFirmwareContract() {
        assertEquals(4, MAX_HOME_FAVORITES)
        assertEquals(4, MAX_HOME_PRESETS)

        val nativeEffects = listOf("solid", "beat", "bass", "level", "bands")
        nativeEffects.forEach { eff ->
            val lightingAction = action(
                "lighting",
                obj(
                    "power" to true,
                    "color" to "#6EE7C8",
                    "brightness" to 82,
                    "effect" to eff,
                    "speed" to 58,
                    "intensity" to 76
                )
            )
            assertEquals("lighting", lightingAction.getString("type"))
            val nestedLighting = lightingAction.getJSONObject("lighting")
            assertEquals(eff, nestedLighting.getString("effect"))
            assertEquals(82, nestedLighting.getInt("brightness"))
            assertEquals(58, nestedLighting.getInt("speed"))
            assertEquals(76, nestedLighting.getInt("intensity"))
        }

        val micAction = action(
            "microphone",
            obj(
                "automatic" to true,
                "sensitivity" to 68,
                "noise_floor" to 14,
                "smoothing" to 65,
                "quiet_behavior" to "dim",
                "quiet_seconds" to 12
            )
        )
        assertEquals("microphone", micAction.getString("type"))
        val nestedMic = micAction.getJSONObject("microphone")
        assertTrue(nestedMic.getBoolean("automatic"))
        assertEquals("dim", nestedMic.getString("quiet_behavior"))
        assertEquals(12, nestedMic.getInt("quiet_seconds"))
    }
}

