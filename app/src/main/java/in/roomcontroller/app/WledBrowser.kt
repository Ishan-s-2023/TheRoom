package `in`.roomcontroller.app

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.JsResult
import android.webkit.JsPromptResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private data class WledDownload(val name: String, val mime: String, val bytes: ByteArray)
private data class WledJsDialog(val message: String, val result: JsResult, val prompt: String? = null, val confirm: Boolean = false)

/** Full upstream control surface, hosted by this controller, with Room's appearance. */
@SuppressLint("SetJavaScriptEnabled")
@Composable fun WledBrowser(device: WledDevice, initialPath: String = "/", onClose: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalRoomPalette.current
    val scope = rememberCoroutineScope()
    var web by remember(device.origin) { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var upload by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var pendingDownload by remember { mutableStateOf<WledDownload?>(null) }
    val history = rememberSaveable(device.origin) { Bundle() }
    var dialog by remember { mutableStateOf<WledJsDialog?>(null) }
    var promptText by remember { mutableStateOf("") }
    var latestPath by remember(device.origin) { mutableStateOf(initialPath) }
    val latestScript by rememberUpdatedState(wledThemeScript(palette))
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        upload?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        upload = null
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val target = if (result.resultCode == android.app.Activity.RESULT_OK) result.data?.data else null
        val download = pendingDownload
        pendingDownload = null
        if (target != null && download != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(target, "wt")?.use { it.write(download.bytes) }
                    ?: error("Cannot open the selected file")
            } }.onFailure { error = it.message ?: "Could not save the file" }
        }
    }
    fun saveFile(download: WledDownload) {
        if (pendingDownload != null) { error = "Finish the current file export first"; return }
        pendingDownload = download
        save.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = download.mime
            putExtra(Intent.EXTRA_TITLE, download.name.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(100).ifBlank { "wled-backup.json" })
        })
    }
    // Android may route a no-Internet setup network to cellular. Bind only while viewing WLED.
    DisposableEffect(device.origin) {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val previous = manager.boundNetworkForProcess
        val wifi = manager.allNetworks.firstOrNull {
            manager.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        if (wifi != null) manager.bindProcessToNetwork(wifi)
        onDispose { if (manager.boundNetworkForProcess == wifi) manager.bindProcessToNetwork(previous) }
    }
    BackHandler { if (web?.canGoBack() == true) web?.goBack() else onClose() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, web) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) web?.saveState(history) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(palette, web) { web?.evaluateJavascript(latestScript, null) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { if (canGoBack) web?.goBack() else onClose() }) { Text(if (canGoBack) "Back" else "Lights") }
            TextButton(onClick = { error = null; web?.reload() }) { Text("Refresh") }
            TextButton(onClick = onClose) { Text("Done") }
        }
        if (progress < 100) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { message ->
            Column(Modifier.padding(16.dp)) {
                Text(message, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { error = null; web?.reload() }) { Text("Try again") }
            }
        }
        AndroidView(modifier = Modifier.fillMaxWidth().weight(1f), factory = { ctx ->
            WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = true // URI grants come from Android's file picker; file:// navigation stays disabled.
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.setSupportMultipleWindows(false)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                setBackgroundColor(palette.background.toArgb())
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                        progress = 0
                    }
                    override fun onPageFinished(view: WebView, url: String) {
                        canGoBack = view.canGoBack()
                        if (sameWledOrigin(device.origin, url)) {
                            view.evaluateJavascript(latestScript, null)
                            view.evaluateJavascript(WLED_DOWNLOAD_SCRIPT, null)
                        }
                    }
                    override fun onReceivedError(view: WebView, request: WebResourceRequest, issue: WebResourceError) {
                        if (request.isForMainFrame) error = "Cannot reach ${device.name}. Check the ESP32 Wi-Fi connection. ${issue.description}"
                    }
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val url = request.url.toString()
                        if (sameWledOrigin(device.origin, url)) return false
                        if (request.isForMainFrame && request.url.scheme in listOf("https", "http")) {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                        }
                        return true
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView, value: Int) { progress = value }
                    override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                        dialog?.result?.cancel(); dialog = WledJsDialog(message, result); return true
                    }
                    override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
                        dialog?.result?.cancel(); dialog = WledJsDialog(message, result, confirm = true); return true
                    }
                    override fun onJsPrompt(view: WebView, url: String, message: String, defaultValue: String?, result: JsPromptResult): Boolean {
                        dialog?.result?.cancel(); promptText = defaultValue.orEmpty()
                        dialog = WledJsDialog(message, result, prompt = defaultValue.orEmpty(), confirm = true); return true
                    }
                    override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                        if (!sameWledOrigin(device.origin, view.url.orEmpty())) return false
                        upload?.onReceiveValue(null); upload = callback
                        try { picker.launch(params.createIntent()) }
                        catch (e: Exception) { error = e.message; upload?.onReceiveValue(null); upload = null }
                        return true
                    }
                }
                if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                    WebViewCompat.addWebMessageListener(this, "RoomFiles", setOf(device.origin)) { view, message, source, mainFrame, _ ->
                        if (mainFrame && sameWledOrigin(device.origin, source.toString()) && sameWledOrigin(device.origin, view.url.orEmpty())) {
                            runCatching {
                                val payload = message.data ?: error("Empty download")
                                require(payload.length <= 16_000_000) { "Export is too large" }
                                val data = JSONObject(payload)
                                data.optString("error").takeIf { it.isNotBlank() }?.let { throw IllegalStateException(it) }
                                val encoded = data.getString("data")
                                require(encoded.startsWith("data:") && encoded.substringBefore(',').endsWith(";base64"))
                                val mime = encoded.substringAfter("data:").substringBefore(';').ifBlank { "application/octet-stream" }
                                WledDownload(data.optString("name", "wled-export.json"), mime, Base64.decode(encoded.substringAfter(','), Base64.DEFAULT))
                            }.onSuccess { saveFile(it) }.onFailure { error = it.message ?: "Could not export file" }
                        }
                    }
                }
                setDownloadListener { url, _, disposition, mime, _ ->
                    if (url.startsWith("blob:") || url.startsWith("data:")) {
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER))
                            evaluateJavascript(wledExportScript(url, "wled-export.json"), null)
                        else error = "Update Android System WebView to export generated files."
                    } else if (sameWledOrigin(device.origin, url)) {
                        val name = android.webkit.URLUtil.guessFileName(url, disposition, mime)
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                                    try {
                                        CookieManager.getInstance().getCookie(device.origin)?.let { connection.setRequestProperty("Cookie", it) }
                                        connection.connectTimeout = 5000; connection.readTimeout = 8000
                                        connection.instanceFollowRedirects = false
                                        check(connection.responseCode in 200..299) { "Download failed (${connection.responseCode})" }
                                        val bytes = connection.inputStream.use { it.readNBytesCompat(12_000_000) }
                                        WledDownload(name, mime ?: "application/json", bytes)
                                    } finally { connection.disconnect() }
                                }
                            }.onSuccess { saveFile(it) }.onFailure { error = it.message ?: "Download failed" }
                        }
                    }
                }
                web = this
                if (history.isEmpty || restoreState(history) == null) loadUrl(device.origin + initialPath)
            }
        }, onRelease = { view ->
            // AndroidView releases this after detaching it. An effect keyed by the
            // WebView reference can destroy a just-created view on its first recomposition.
            upload?.onReceiveValue(null); upload = null
            dialog?.result?.cancel(); dialog = null
            view.saveState(history); view.stopLoading(); view.destroy()
        }, update = { view ->
            view.setBackgroundColor(palette.background.toArgb())
            if (latestPath != initialPath) {
                latestPath = initialPath
                view.loadUrl(device.origin + initialPath)
            }
        })
    }
    dialog?.let { value -> AlertDialog(onDismissRequest = { value.result.cancel(); dialog = null },
        title = { Text(device.name) },
        text = { Column { Text(value.message); if (value.prompt != null) OutlinedTextField(promptText, { promptText = it }) } },
        confirmButton = { TextButton(onClick = {
            if (value.result is JsPromptResult) value.result.confirm(promptText) else value.result.confirm()
            dialog = null
        }) { Text("OK") } },
        dismissButton = { if (value.confirm) TextButton(onClick = { value.result.cancel(); dialog = null }) { Text("Cancel") } }) }
}

private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val n = read(buffer)
        if (n < 0) return out.toByteArray()
        require(out.size() + n <= limit) { "Export is too large" }
        out.write(buffer, 0, n)
    }
}

private fun wledExportScript(url: String, name: String) = """
    (function(){fetch(${JSONObject.quote(url)}).then(function(r){return r.blob();}).then(function(blob){
      var r=new FileReader();r.onload=function(){RoomFiles.postMessage(JSON.stringify({name:${JSONObject.quote(name)},data:r.result}));};r.readAsDataURL(blob);
    }).catch(function(e){RoomFiles.postMessage(JSON.stringify({error:String(e)}));});})();
""".trimIndent()

// WLED generates some downloads using Blob URLs. Capture the filename before WebView loses it.
private val WLED_DOWNLOAD_SCRIPT = """
    (function(){if(window.__roomDownloads||!window.RoomFiles)return;window.__roomDownloads=true;
      function download(a){
        if(!a||!a.hasAttribute('download'))return false;
        var url=new URL(a.href,location.href);
        if(url.origin!==location.origin&&!(/^data:/.test(a.href)))return false;
        var name=a.download||'wled-export.json';
        fetch(a.href).then(function(r){if(!r.ok)throw new Error('Export failed: '+r.status);return r.blob();}).then(function(blob){
          if(blob.size>12000000)throw new Error('Export is too large');
          var r=new FileReader();r.onload=function(){RoomFiles.postMessage(JSON.stringify({name:name,data:r.result}));};r.readAsDataURL(blob);
        }).catch(function(err){RoomFiles.postMessage(JSON.stringify({error:String(err)}));});
        return true;
      }
      document.addEventListener('click',function(e){
        var a=e.target.closest&&e.target.closest('a[download]');
        if(download(a))e.preventDefault();
      },true);
      var original=HTMLAnchorElement.prototype.click;
      HTMLAnchorElement.prototype.click=function(){if(!download(this))return original.apply(this,arguments);};
    })();
""".trimIndent()
