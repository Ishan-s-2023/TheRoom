package `in`.roomcontroller.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
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

@Composable fun RoomApp(model: RoomModel = viewModel(), preview: String? = null,
    wled: WledModel = viewModel(), openWled: Boolean = false, wledDevice: String? = null) {
    val ui by model.ui.collectAsStateWithLifecycle()
    val wledUi by wled.ui.collectAsStateWithLifecycle()
    LaunchedEffect(preview) { if (preview != null) model.demo(preview) }
    var wledMode by rememberSaveable { mutableStateOf(openWled) }
    var page by rememberSaveable { mutableStateOf(if (openWled) "Lights" else "Home") }
    var wledPath by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(wledDevice) { wledDevice?.let(wled::select) }
    LaunchedEffect(model.endpoint, ui.signedIn) {
        if (ui.signedIn && model.directDevice && !ui.demo) wled.probeRoomPeer(model.endpoint)
    }
    fun showWled(path: String) { if (wledUi.device != null) wledPath = path else page = "Lights" }
    val usingWled = !ui.demo && (page in setOf("Lights", "Sound") || wledPath != null)
    LaunchedEffect(usingWled) { model.pollRoom(!usingWled) }
    val scrollState = remember(page) { ScrollState(0) }
    BackHandler(enabled=(ui.signedIn || wledMode) && page!="Home" && wledPath == null) { page=if(page in morePages) "More" else "Home" }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(ui.error, ui.notice, usingWled) {
        if (!usingWled) (ui.error ?: ui.notice)?.let { snackbar.showSnackbar(it, duration=SnackbarDuration.Long); model.dismiss() }
    }
    LaunchedEffect(wledUi.error, wledUi.notice) {
        (wledUi.error ?: wledUi.notice)?.let { snackbar.showSnackbar(it, duration=SnackbarDuration.Long); wled.dismiss() }
    }
    Scaffold(containerColor=Ink, snackbarHost={ SnackbarHost(snackbar) }, bottomBar={
        if (ui.signedIn || wledMode) NavigationBar(containerColor=Ink, tonalElevation=0.dp) {
            listOf("Home" to Icons.Outlined.Home, "Lights" to Icons.Outlined.LightMode, "Remotes" to Icons.Outlined.SettingsRemote,
                "Music" to Icons.Outlined.MusicNote, "More" to Icons.Outlined.GridView).forEach { (label, icon) ->
                NavigationBarItem(selected=page==label || (label=="More" && page in morePages), onClick={ wledPath=null; page=label }, icon={ Icon(icon,label) }, label={Text(label,fontSize=11.sp)},
                    colors=NavigationBarItemDefaults.colors(indicatorColor=Panel, selectedIconColor=Lime, selectedTextColor=Lime, unselectedIconColor=Muted, unselectedTextColor=Muted))
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth(),color=Lime,trackColor=Panel)
            if (wledPath != null && wledUi.device != null) WledBrowser(wledUi.device!!, wledPath!!) {
                wledPath=null; wledUi.selected?.let(wled::select)
            }
            else if (!ui.signedIn && !wledMode) {
                TextButton(onClick={ wledMode=true; page="Lights" }, modifier=Modifier.fillMaxWidth()) { Text("Connect WLED lighting") }
                PairDeviceScreen(model,ui.busy)
            }
            else {
                if (ui.stale && !usingWled) StatusStrip("Room services offline · showing last received settings", Color(0xFFF6BC7B))
                Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal=24.dp).padding(top=24.dp,bottom=32.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                    when {
                        !ui.signedIn && page !in setOf("Home", "Lights", "More", "System", "Sound") -> {
                            PageTitle("Room controller", "Bring it all together.", "Connect the Room services on your WLED-based ESP32 for remotes, Spotify and combined routines.")
                            ActionButton("Connect Room services") { wledMode=false }
                        }
                        page == "Lights" && !ui.demo -> WledLightsScreen(wled,wledUi,::showWled)
                        page == "Sound" && !ui.demo -> {
                            PageTitle("Sound", "In tune with you.", "Tune the microphone and audio response on your WLED controller.")
                            ActionButton("Open sound & usermods", enabled=wledUi.device!=null) { showWled("/settings/um") }
                        }
                        !ui.signedIn && page == "Home" -> WledQuickControls(wled,wledUi,::showWled)
                        !ui.signedIn && page == "System" -> {
                            ThemeChooser(); WledDevicesCard(wled,wledUi)
                            ActionButton("Connect Room services") { wledMode=false }
                        }
                        model.directDevice && ui.state.length() == 0 && page !in setOf("More", "System") -> { DeviceSystemScreen(model,ui); ConnectionGuides(model.endpoint,ui.demo) }
                        else -> when(page) {
                        "Home" -> HomeScreen(model,ui,wled,wledUi,::showWled) { page=it }
                        "Lights" -> LightsScreen(model,ui)
                        "Remotes" -> RemotesScreen(model,ui)
                        "Routines" -> RoutinesScreen(model,ui)
                        "Music" -> if(model.directDevice) { ConnectionHelpButton("spotify-direct",model.endpoint); DeviceMusicScreen(model,ui) } else MusicScreen(model,ui)
                        "Sound" -> SoundTuningScreen(model,ui)
                        "System" -> {
                            WledDevicesCard(wled,wledUi)
                            if(model.directDevice) { DeviceSystemScreen(model,ui); ConnectionGuides(model.endpoint,ui.demo) } else SystemScreen(model,ui)
                        }
                        else -> MoreSettingsScreen { page=it }
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
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(eyebrow.uppercase(), color=Lime,fontSize=10.sp,letterSpacing=2.sp,fontWeight=FontWeight.Bold)
        Text(title,fontFamily=FontFamily.Serif,fontSize=36.sp,lineHeight=42.sp,color=Cream)
        Text(subtitle,color=Muted,fontSize=13.sp,lineHeight=20.sp)
    }
}

@Composable fun RoomCard(modifier: Modifier=Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Panel).border(1.dp,Line,RoundedCornerShape(24.dp)).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp),content=content)
}

@Composable fun SectionLabel(title: String, trailing: String="") {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
        Text(title,fontSize=18.sp,fontWeight=FontWeight.Medium)
        if (trailing.isNotEmpty()) Text(trailing,color=Muted,fontSize=11.sp)
    }
}

@Composable fun SmallLabel(text: String) { Text(text.uppercase(),color=Muted,fontSize=10.sp,letterSpacing=1.5.sp) }

@Composable fun ActionButton(text: String, icon: ImageVector?=null, enabled: Boolean=true, onClick: ()->Unit) {
    Button(onClick=onClick,enabled=enabled,shape=RoundedCornerShape(14.dp),contentPadding=PaddingValues(horizontal=18.dp,vertical=14.dp)) {
        icon?.let { Icon(it,null,Modifier.size(17.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text)
    }
}

@Composable fun SettingSlider(title: String, value: Float, onChange: (Float)->Unit, range: ClosedFloatingPointRange<Float> = 0f..100f, suffix: String="%") {
    Column {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text(title,color=Muted,fontSize=13.sp); Text("${value.toInt()}$suffix",fontSize=13.sp)
        }
        Slider(value=value,onValueChange=onChange,valueRange=range,modifier=Modifier.fillMaxWidth())
    }
}

@Composable fun Choices(values: List<String>, selected: String, onSelect: (String)->Unit) {
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        values.forEach { value -> FilterChip(selected=value==selected,onClick={onSelect(value)},label={Text(value.replace('_',' ').replaceFirstChar { it.uppercase() },fontSize=12.sp)}) }
    }
}

@Composable fun ConfirmDialog(title: String, description: String, onDismiss: ()->Unit, onConfirm: ()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={Text(description)},
        confirmButton={TextButton(onClick={onConfirm();onDismiss()}) {Text("Confirm")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
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
