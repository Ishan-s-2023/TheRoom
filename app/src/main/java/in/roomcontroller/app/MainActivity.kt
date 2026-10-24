package `in`.roomcontroller.app

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle=SystemBarStyle.dark(android.graphics.Color.rgb(16,19,16))
        )
        setContent {
            RoomTheme {
                val preview = if (BuildConfig.DEBUG && intent.getBooleanExtra("ui_preview", false))
                    intent.getStringExtra("preview_scenario") ?: "default" else null
                RoomApp(preview = preview, openWled = intent.getBooleanExtra("open_wled", false), wledDevice = intent.getStringExtra("wled_device"))
            }
        }
    }
}

private data class BottomNavTab(
    val route: String,
    val label: String,
    val icon: ImageVector
)

@Composable fun RoomApp(model: RoomModel = viewModel(), preview: String? = null,
    wled: WledModel = viewModel(), openWled: Boolean = false, wledDevice: String? = null) {
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val notificationPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { /* Notification permission granted or declined */ }

        LaunchedEffect(Unit) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val ui by model.ui.collectAsStateWithLifecycle()
    val wledUi by wled.ui.collectAsStateWithLifecycle()
    LaunchedEffect(preview) { if (preview != null) model.demo(preview) }
    var wledMode by rememberSaveable { mutableStateOf(openWled) }
    var page by rememberSaveable {
        mutableStateOf(if (openWled) "Lights" else ui.selectedPage.ifBlank { "Home" })
    }

    fun selectPage(target: String) {
        val normalized = when (target) {
            "Light" -> "Lights"
            "Remote" -> "Remotes"
            else -> target
        }
        if (page != normalized) {
            page = normalized
        }
    }
    LaunchedEffect(page) {
        model.setPage(page)
    }

    LaunchedEffect(wledDevice) { wledDevice?.let(wled::select) }
    LaunchedEffect(model.endpoint, ui.signedIn) {
        if (ui.signedIn && model.directDevice && !ui.demo) wled.probeRoomPeer(model.endpoint)
    }
    val usingWled = !ui.demo && page in setOf("Lights", "Sound")
    LaunchedEffect(usingWled) { model.pollRoom(!usingWled) }
    val scrollStates = remember { mutableMapOf<String, ScrollState>() }
    val scrollState = scrollStates.getOrPut(page) { ScrollState(0) }

    BackHandler(enabled=(ui.signedIn || wledMode) && page!="Home") {
        selectPage(if (page in morePages) "More" else "Home")
    }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(ui.error, ui.notice, usingWled) {
        if (!usingWled) {
            (ui.error ?: ui.notice)?.let { msg ->
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(msg, duration = SnackbarDuration.Short)
                model.dismiss()
            }
        }
    }
    LaunchedEffect(wledUi.error, wledUi.notice) {
        (wledUi.error ?: wledUi.notice)?.let { msg ->
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(msg, duration = SnackbarDuration.Short)
            wled.dismiss()
        }
    }

    val activeFavoriteWarning = wledUi.favoriteLimitWarning ?: ui.favoriteLimitWarning
    if (activeFavoriteWarning != null) {
        AlertDialog(
            onDismissRequest = {
                wled.dismissFavoriteWarning()
                model.dismissFavoriteWarning()
            },
            icon = {
                Icon(Icons.Outlined.StarOutline, contentDescription = null, tint = Lime)
            },
            title = {
                Text("Favorite Limit Reached ($MAX_FAVORITE_LIMIT/$MAX_FAVORITE_LIMIT)", fontWeight = FontWeight.SemiBold)
            },
            text = {
                Text(
                    text = activeFavoriteWarning,
                    color = Cream,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        wled.dismissFavoriteWarning()
                        model.dismissFavoriteWarning()
                    },
                    modifier = Modifier.testTag("dismiss_favorite_limit_warning")
                ) {
                    Text("Got it", color = Lime, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    // Exact Bottom Navigation Bar Order requested:
    // 1. Home  2. Music  3. Light (Center)  4. Remote  5. More
    val navTabs = remember {
        listOf(
            BottomNavTab("Home", "Home", Icons.Outlined.Home),
            BottomNavTab("Music", "Music", Icons.Outlined.MusicNote),
            BottomNavTab("Lights", "Light", Icons.Outlined.LightMode),
            BottomNavTab("Remotes", "Remote", Icons.Outlined.SettingsRemote),
            BottomNavTab("More", "More", Icons.Outlined.GridView)
        )
    }

    Scaffold(
        containerColor = Ink,
        snackbarHost = {
            SnackbarHost(hostState = snackbar) { data ->
                Surface(
                    color = Panel,
                    contentColor = Cream,
                    shape = RoundedCornerShape(14.dp),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .border(1.dp, Lime.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = data.visuals.message,
                            color = Cream,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.weight(1f)
                        )
                        val view = androidx.compose.ui.platform.LocalView.current
                        IconButton(
                            onClick = {
                                performSubtleHaptic(view)
                                data.dismiss()
                                model.dismiss()
                                wled.dismiss()
                            },
                            modifier = Modifier.testTag("snackbar_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Dismiss notification",
                                tint = Muted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
        if (ui.signedIn || wledMode) {
            val activeNavIndex = navTabs.indexOfFirst { tab ->
                page == tab.route || (tab.route == "More" && page in morePages)
            }.coerceAtLeast(0)
            LimelightNavBar(
                items = navTabs.map { tab ->
                    LimelightNavItem(
                        id = tab.route,
                        label = tab.label,
                        icon = tab.icon,
                        onClick = { selectPage(tab.route) }
                    )
                },
                activeIndex = activeNavIndex
            )
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.fillMaxWidth().height(3.dp)) {
                if (ui.busy || wledUi.busy) {
                    LinearProgressIndicator(Modifier.fillMaxSize(), color = Lime, trackColor = Panel)
                }
            }
            if (!ui.signedIn && !wledMode) {
                TextButton(onClick={ wledMode=true; selectPage("Lights") }, modifier=Modifier.fillMaxWidth()) { Text("Connect WLED lighting") }
                PairDeviceScreen(model,ui.busy)
            }
            else {
                if (ui.stale && !usingWled) StatusStrip("Room services offline · showing last received settings", Color(0xFFF6BC7B))
                Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal=20.dp).padding(top=20.dp,bottom=32.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    key(page) {
                        when {
                            !ui.signedIn && page !in setOf("Home", "Lights", "More", "System", "Sound") -> {
                                PageTitle("Room controller", "Bring it all together.", "Connect the Room services on your WLED-based ESP32 for remotes, Spotify and combined routines.")
                                ActionButton("Connect Room services") { wledMode=false }
                            }
                            page == "Lights" -> WledLightsScreen(wled, wledUi, model, ui) { selectPage(it) }
                            page == "Sound" -> SoundTuningScreen(model, ui, wled, wledUi)
                            !ui.signedIn && page == "Home" -> WledQuickControls(wled, wledUi) { selectPage("Lights") }
                            !ui.signedIn && page == "System" -> {
                                ThemeChooser()
                                WledDevicesCard(wled, wledUi, model, ui)
                                WledControllerSettingsSection(wled, wledUi, model, ui)
                                ActionButton("Connect Room services") { wledMode=false }
                            }
                            model.directDevice && ui.state.length() == 0 && page !in setOf("Home", "Lights", "Sound", "More", "System") -> { DeviceSystemScreen(model, ui, wled, wledUi) }
                            else -> when(page) {
                                "Home" -> HomeScreen(model, ui, wled, wledUi) { selectPage(it) }
                                "Lights" -> WledLightsScreen(wled, wledUi, model, ui) { selectPage(it) }
                                "Remotes" -> RemotesScreen(model, ui)
                                "Routines" -> RoutinesScreen(model, ui)
                                "Music" -> if(model.directDevice) { DeviceMusicScreen(model, ui) { selectPage(it) } } else MusicScreen(model, ui) { selectPage(it) }
                                "Sound" -> SoundTuningScreen(model, ui, wled, wledUi)
                                "System" -> {
                                    WledDevicesCard(wled, wledUi, model, ui)
                                    if (model.directDevice) {
                                        DeviceSystemScreen(model, ui, wled, wledUi)
                                    } else {
                                        WledControllerSettingsSection(wled, wledUi, model, ui)
                                        SystemScreen(model, ui, wled)
                                    }
                                }
                                else -> MoreSettingsScreen { selectPage(it) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable fun StatusStrip(text: String, tint: Color) {
    Text(text, Modifier.fillMaxWidth().background(tint.copy(alpha=.09f)).padding(horizontal=24.dp,vertical=9.dp), color=tint,fontSize=11.sp)
}

@Composable fun PageTitle(eyebrow: String, title: String, subtitle: String) {
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(eyebrow.uppercase(), color=Lime,fontSize=10.sp,letterSpacing=2.sp,fontWeight=FontWeight.Bold)
        Text(title,fontFamily=FontFamily.Serif,fontSize=34.sp,lineHeight=40.sp,color=Cream)
        Text(subtitle,color=Muted,fontSize=13.sp,lineHeight=19.sp)
    }
}

@Composable fun RoomCard(modifier: Modifier=Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Panel).border(1.dp,Line,RoundedCornerShape(22.dp)).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
}

@Composable fun SectionLabel(title: String, trailing: String="") {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (trailing.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = trailing,
                color = Muted,
                fontSize = 10.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable fun SmallLabel(text: String, maxLines: Int = 2) {
    Text(
        text = text.uppercase(),
        color = Muted,
        fontSize = 10.sp,
        letterSpacing = 1.2.sp,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        softWrap = true
    )
}

@Composable fun ActionButton(
    text: String,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val view = androidx.compose.ui.platform.LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = {
            performSubtleHaptic(view)
            onClick()
        },
        enabled = enabled,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        modifier = modifier.livelyPressScale(interactionSource, enabled = enabled, pressedScale = 0.94f)
    ) {
        icon?.let { Icon(it, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
        Text(
            text = text,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable fun SettingToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    variant: String = "default",
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                color = if (enabled) Cream else Muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 17.sp
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = Muted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
        LiquidToggle(
            checked = checked,
            enabled = enabled,
            variant = variant,
            onCheckedChange = onCheckedChange,
            label = title
        )
    }
}

@Composable fun SettingSlider(
    title: String,
    value: Float,
    onChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float> = 0f..100f,
    suffix: String = "%",
    enabled: Boolean = true,
    onFinished: () -> Unit = {}
) {
    val span = (range.endInclusive - range.start).coerceAtLeast(1f)
    var displayVal by remember { mutableFloatStateOf(value.coerceIn(range.start, range.endInclusive)) }
    var lastUserEditMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(value) {
        if (System.currentTimeMillis() - lastUserEditMs > 750L) {
            displayVal = value.coerceIn(range.start, range.endInclusive)
        }
    }
    val fraction = ((displayVal - range.start) / span).coerceIn(0f, 1f)
    Column(
        modifier = Modifier.alpha(if (enabled) 1f else 0.42f),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Muted, fontSize = 13.sp)
            Text(
                text = "${displayVal.toInt()}$suffix",
                fontSize = 13.sp,
                color = if (enabled) Cream else Muted,
                fontWeight = FontWeight.Medium
            )
        }
        LedSlider(
            value = fraction,
            onValueChange = { f ->
                if (!enabled) return@LedSlider
                lastUserEditMs = System.currentTimeMillis()
                val next = range.start + f * span
                displayVal = next
                onChange(next)
            },
            onValueChangeFinished = {
                lastUserEditMs = System.currentTimeMillis()
                onFinished()
            },
            trackBrush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                listOf(Lime.copy(alpha = 0.35f), WledCatalogDefaults.luminousLedColor(Lime, fraction.coerceAtLeast(0.35f)))
            ),
            contentDescription = title,
            enabled = enabled,
            thumbColor = Lime
        )
    }
}

@Composable fun Choices(values: List<String>, selected: String, onSelect: (String)->Unit) {
    val view = androidx.compose.ui.platform.LocalView.current
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp), verticalArrangement=Arrangement.spacedBy(6.dp)) {
        values.forEach { value ->
            val isSelected = value == selected
            val interactionSource = remember { MutableInteractionSource() }
            val formattedLabel = value
                .replace('_', ' ')
                .replaceFirstChar { it.uppercase() }
                .replace(Regex("^Ir\\b"), "IR")
            FilterChip(
                selected = isSelected,
                onClick = {
                    performSubtleHaptic(view)
                    onSelect(value)
                },
                interactionSource = interactionSource,
                modifier = Modifier.livelyPressScale(interactionSource, pressedScale = 0.92f),
                label = { Text(formattedLabel, fontSize=12.sp, maxLines=1, softWrap=false) }
            )
        }
    }
}

@Composable fun ConfirmDialog(title: String, description: String, onDismiss: ()->Unit, onConfirm: ()->Unit) {
    val view = androidx.compose.ui.platform.LocalView.current
    AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={Text(description)},
        confirmButton={TextButton(onClick={performSubtleHaptic(view, heavy = true);onConfirm();onDismiss()}) {Text("Confirm")}},dismissButton={TextButton(onClick={performSubtleHaptic(view);onDismiss()}){Text("Cancel")}})
}

@Composable fun LoginScreen(model: RoomModel, busy: Boolean) {
    var endpoint by rememberSaveable { mutableStateOf(model.endpoint) }
    var username by rememberSaveable { mutableStateOf("owner") }
    var password by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
        Spacer(Modifier.height(22.dp))
        Box(Modifier.size(60.dp).clip(RoundedCornerShape(20.dp)).background(Lime),contentAlignment=Alignment.Center) {Icon(Icons.Outlined.Home,"Room",tint=Ink,modifier=Modifier.size(30.dp))}
        PageTitle("A little more in tune", "Your room.\nYour rhythm.", "Lighting, sound, and everyday rituals.\nTogether in one quiet place.")
        RoomCard {
            SectionLabel("Connect your room")
            OutlinedTextField(value=endpoint,onValueChange={endpoint=it},label={Text("Server URL")},placeholder={Text("https://room.example.com")},singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(value=username,onValueChange={username=it},label={Text("Username")},singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(value=password,onValueChange={password=it},label={Text("Password")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())
            ActionButton("Connect securely",Icons.Outlined.ArrowForward,!busy && endpoint.isNotBlank() && password.length>=12) { model.login(endpoint,username,password); password="" }
        }
        Text("Connect over your home Wi-Fi. Install your local server's CA certificate before using HTTPS. Control stays on your local network.",color=Muted,fontSize=12.sp,lineHeight=19.sp)
        TextButton(onClick={ model.demo() },modifier=Modifier.align(Alignment.CenterHorizontally)) {Text("Explore the interface",color=Lime)}
        SmallLabel("ESP32 · WI-FI ONLY · LOCAL FIRST")
    }
}
