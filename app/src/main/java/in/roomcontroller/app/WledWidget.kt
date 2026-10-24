package `in`.roomcontroller.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.RemoteViews
import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray

class WledWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { ids.forEach { render(context, it) } }
    override fun onDeleted(context: Context, ids: IntArray) {
        val prefs = context.getSharedPreferences("room-wled-widgets", 0)
        ids.forEach { prefs.edit().remove("device-$it").remove("preset-$it").apply() }
    }
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION) return
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id !in AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, WledWidget::class.java))) return
        val pending = goAsync()
        render(context, id, "Sending…", false)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val prefs = context.getSharedPreferences("room-wled-widgets", 0)
                val device = devices(context).firstOrNull { it.id == prefs.getString("device-$id", null) } ?: error("Reconnect in Room")
                val manager = context.getSystemService(ConnectivityManager::class.java)
                val wifi = manager.allNetworks.firstOrNull { manager.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
                val builder = WledApi().client.newBuilder()
                    .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                    .callTimeout(4500, java.util.concurrent.TimeUnit.MILLISECONDS)
                if (wifi != null) builder.socketFactory(wifi.socketFactory).dns(object : okhttp3.Dns {
                    override fun lookup(hostname: String) = wifi.getAllByName(hostname).toList()
                })
                val api = WledApi(builder.build())
                val before = api.state(device.origin)
                check(wledDeviceId(before.info) == device.id) { "Controller identity changed" }
                val preset = prefs.getInt("preset-$id", 0)
                val after = api.command(device.origin, if (preset > 0) obj("ps" to preset) else obj("on" to !before.state.getBoolean("on")))
                check(wledDeviceId(after.info) == device.id)
                if (preset > 0) {
                    val psNow = after.state.optInt("ps", -1)
                    val plNow = after.state.optInt("pl", -1)
                    check(psNow == preset || plNow == preset || after.state.optBoolean("on", false) || after.state.length() > 0) {
                        "Preset result unconfirmed"
                    }
                }
                render(context, id, if (preset > 0) "Preset $preset applied" else if (after.state.optBoolean("on")) "Lights on" else "Lights off")
            } catch (_: Exception) { render(context, id, "Result unconfirmed · open Room") }
            finally { pending.finish() }
        }
    }
    companion object {
        private const val ACTION = "in.roomcontroller.app.WLED_WIDGET_COMMAND"
        fun devices(context: Context): List<WledDevice> = runCatching {
            val spRaw = context.getSharedPreferences("room-wled", 0).getString("devices", null)
            val jsonStr = if (!spRaw.isNullOrBlank() && spRaw != "[]") {
                spRaw
            } else {
                runCatching { runBlocking { RoomDataStore.readDevicesJson(context) } }.getOrNull().orEmpty()
            }
            if (jsonStr.isBlank()) emptyList()
            else JSONArray(jsonStr).objects().map { WledDevice.parse(it) }
        }.getOrDefault(emptyList())

        fun refresh(context: Context) {
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, WledWidget::class.java)).forEach { render(context, it) }
        }
        fun render(context: Context, id: Int, status: String? = null, enabled: Boolean = true) {
            val prefs = context.getSharedPreferences("room-wled-widgets", 0)
            val device = devices(context).firstOrNull { it.id == prefs.getString("device-$id", null) }
            val preset = prefs.getInt("preset-$id", 0)
            val views = RemoteViews(context.packageName, R.layout.wled_widget)
            val selectedTheme = context.getSharedPreferences("room-appearance", 0).getString("theme", "forest").orEmpty()
            val palette = roomPalette(selectedTheme, context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES)
            val bgRes = when {
                palette.light -> R.drawable.wled_widget_background_light
                selectedTheme.lowercase() in setOf("obsidian", "midnight", "black") -> R.drawable.wled_widget_background_obsidian
                else -> R.drawable.wled_widget_background
            }
            views.setInt(R.id.wled_widget_root, "setBackgroundResource", bgRes)
            views.setTextColor(R.id.wled_widget_title, palette.text.toArgb())
            views.setTextColor(R.id.wled_widget_status, palette.muted.toArgb())
            views.setTextColor(R.id.wled_widget_action, if (palette.light) android.graphics.Color.WHITE else palette.background.toArgb())
            views.setTextViewText(R.id.wled_widget_title, device?.name ?: "Room lighting")
            views.setTextViewText(R.id.wled_widget_status, status ?: if (device == null) "Open Room to connect" else "WLED · ${if (preset > 0) "preset $preset" else "lighting"}")
            views.setTextViewText(R.id.wled_widget_action, if (preset > 0) "Apply preset" else "Switch lights")
            views.setBoolean(R.id.wled_widget_action, "setEnabled", enabled && device != null)
            val open = Intent(context, MainActivity::class.java).putExtra("open_wled", true).putExtra("wled_device", device?.id)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            views.setOnClickPendingIntent(R.id.wled_widget_open, PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            views.setOnClickPendingIntent(R.id.wled_widget_action, PendingIntent.getBroadcast(context, id,
                Intent(context, WledWidget::class.java).setAction(ACTION).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            AppWidgetManager.getInstance(context).updateAppWidget(id, views)
        }
    }
}

class WledWidgetConfigureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(Activity.RESULT_CANCELED)
        if (AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)?.provider != ComponentName(this, WledWidget::class.java)) { finish(); return }
        setContent { RoomTheme {
            val devices = remember { WledWidget.devices(this).filterNot { it.hidden } }
            var selected by remember { mutableStateOf(devices.firstOrNull()?.id) }
            var preset by remember { mutableStateOf("") }
            Surface(color = Ink) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PageTitle("Home screen", "Your room, a tap away.", "Choose a controller and an action for this widget.")
                devices.forEach { device -> Row { RadioButton(selected == device.id, { selected = device.id }); Text(device.name) } }
                if (devices.isEmpty()) Text("Connect a WLED controller in Room first.")
                OutlinedTextField(preset, { preset = it.filter(Char::isDigit).take(3) }, label = { Text("Preset number (optional)") },
                    supportingText = { Text("Leave empty to switch power. Preset numbers are shown in WLED.") })
                ActionButton("Add widget", enabled = selected != null && (preset.isEmpty() || preset.toIntOrNull() in 1..250)) {
                    getSharedPreferences("room-wled-widgets", 0).edit().putString("device-$widgetId", selected).putInt("preset-$widgetId", preset.toIntOrNull() ?: 0).apply()
                    WledWidget.render(this@WledWidgetConfigureActivity, widgetId)
                    setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)); finish()
                }
            } }
        } }
    }
}
