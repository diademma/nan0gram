package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import java.io.File
import java.io.FileOutputStream

// Стелс-кэш для скрытой передачи файлов между WebView
object StealthCache {
    var pendingUris: Array<Uri>? = null
    var pendingSysBlock: String? = null
}

// Утилита копирования медиафайла в скрытый системный .bin
fun createStealthCopy(context: Context, originalUri: Uri): Uri? {
    return try {
        val inputStream = context.contentResolver.openInputStream(originalUri) ?: return null
        val file = File(context.cacheDir, "sys_data_${System.currentTimeMillis()}.bin")
        val outputStream = FileOutputStream(file)
        inputStream.copyTo(outputStream)
        inputStream.close()
        outputStream.close()
        Uri.fromFile(file)
    } catch (e: Exception) {
        null
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AppScreen(
    isBgServiceActive: Boolean,
    isLogPanelExpanded: Boolean,
    uiAlpha: Float,
    isParserEnabled: Boolean,
    logList: List<String>,
    logListState: LazyListState,
    coords: DomCoords,
    onLogPanelToggle: (Boolean) -> Unit,
    onUiAlphaChange: (Float) -> Unit,
    onParserToggle: () -> Unit,
    onLogClear: () -> Unit,
    onUkrnetViewReady: (WebView) -> Unit,
    onMessengerViewReady: (WebView) -> Unit,
    onUkrnetReload: () -> Unit,
    onMessengerReload: () -> Unit,
    ukrnetInterface: UkrnetJsInterface,
    messengerInterface: MessengerJsInterface,
    coroutineScope: CoroutineScope,
    log: (String) -> Unit
) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF130E19))
        ) {
            WebViewLayer(
                isBgServiceActive = isBgServiceActive,
                uiAlpha = uiAlpha,
                ukrnetInterface = ukrnetInterface,
                messengerInterface = messengerInterface,
                onUkrnetViewReady = onUkrnetViewReady,
                onMessengerViewReady = onMessengerViewReady,
                log = log
            )

            LogPanel(
                isExpanded = isLogPanelExpanded,
                onToggle = onLogPanelToggle,
                logList = logList,
                logListState = logListState,
                onClear = onLogClear,
                isBgServiceActive = isBgServiceActive,
                uiAlpha = uiAlpha,
                onUiAlphaChange = onUiAlphaChange,
                isParserEnabled = isParserEnabled,
                onParserToggle = onParserToggle,
                coords = coords,
                onUkrnetReload = onUkrnetReload,
                onMessengerReload = onMessengerReload,
                coroutineScope = coroutineScope,
                log = log
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebViewLayer(
    isBgServiceActive: Boolean,
    uiAlpha: Float,
    ukrnetInterface: UkrnetJsInterface,
    messengerInterface: MessengerJsInterface,
    onUkrnetViewReady: (WebView) -> Unit,
    onMessengerViewReady: (WebView) -> Unit,
    log: (String) -> Unit
) {
    val context = LocalContext.current
    var filePathCallback by remember { mutableStateOf<android.webkit.ValueCallback<Array<Uri>>?>(null) }
    
    // Перехватчик файлов мессенджера
    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data?.data
        val dataClip = result.data?.clipData
        val originalUris = mutableListOf<Uri>()
        val stealthUris = mutableListOf<Uri>()

        try {
            if (data != null) {
                originalUris.add(data)
                createStealthCopy(context, data)?.let { stealthUris.add(it) }
            } else if (dataClip != null) {
                for (i in 0 until dataClip.itemCount) {
                    val u = dataClip.getItemAt(i).uri
                    originalUris.add(u)
                    createStealthCopy(context, u)?.let { stealthUris.add(it) }
                }
            }
            if (stealthUris.isNotEmpty()) {
                StealthCache.pendingUris = stealthUris.toTypedArray()
                log("[Stealth] Файлы закэшированы как .bin (${stealthUris.size} шт)")
            }
            if (originalUris.isNotEmpty()) {
                filePathCallback?.onReceiveValue(originalUris.toTypedArray())
            } else {
                filePathCallback?.onReceiveValue(null)
            }
        } catch (e: Exception) {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    AndroidView(
        factory = { ctx ->
            FrameLayout(ctx).apply {
                val uWebView = WebView(ctx).apply {
                    tag = "ukrnet"
                    settings.apply {
                        javaScriptEnabled    = true
                        domStorageEnabled    = true
                        databaseEnabled      = true
                        useWideViewPort      = true
                        loadWithOverviewMode = true
                        allowFileAccess      = true
                        allowContentAccess   = true
                        javaScriptCanOpenWindowsAutomatically = true
                        userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    }
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    addJavascriptInterface(ukrnetInterface, "Android")
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            if (url != null && url.contains("sendmsg")) {
                                view?.evaluateJavascript("window._n0gFilled = false;", null)
                                view?.evaluateJavascript(SENDMSG_FILL_JS, null)
                                log("[Compose] sendmsg загружен — заполняем поля")
                                val bufferedBody = messengerInterface.lastComposeBody
                                if (bufferedBody.isNotEmpty()) {
                                    val esc = bufferedBody.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
                                    val js = """
                                        (function(text) {
                                            var el = document.querySelector('.sm-editor__area')
                                                || document.querySelector('[contenteditable="true"]')
                                                || document.querySelector('textarea[name="body"]')
                                                || document.querySelector('textarea');
                                            if (!el) return;
                                            el.innerHTML = '';
                                            if (el.getAttribute('contenteditable') === 'true') { el.innerText = text; }
                                            else { try { Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value').set.call(el,text); } catch(e) { el.value=text; } }
                                            el.dispatchEvent(new Event('input',{bubbles:true}));
                                            el.dispatchEvent(new Event('change',{bubbles:true}));
                                        })('$esc');
                                    """.trimIndent()
                                    view?.evaluateJavascript(js, null)
                                    log("[Compose] Восстановлен текст из буфера быстрого ввода")
                                }
                            }
                        }
                        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                            super.doUpdateVisitedHistory(view, url, isReload)
                            if (url != null) ukrnetInterface.onUrlChange(url)
                        }
                        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                            handler?.cancel()
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(m: ConsoleMessage?): Boolean {
                            val level = m?.messageLevel()?.name ?: "LOG"
                            log("[Ukrnet JS] [$level] ${m?.message()} (${m?.lineNumber()})")
                            return true
                        }
                        
                        override fun onShowFileChooser(
                            webView: WebView?,
                            filePathCallbackParams: android.webkit.ValueCallback<Array<Uri>>?,
                            fileChooserParams: FileChooserParams?
                        ): Boolean {
                            if (StealthCache.pendingUris != null) {
                                filePathCallbackParams?.onReceiveValue(StealthCache.pendingUris)
                                StealthCache.pendingUris = null
                                log("[Stealth] УкрНету успешно скормлен .bin файл!")
                                return true
                            }
                            filePathCallbackParams?.onReceiveValue(null)
                            return true
                        }
                    }
                    loadUrl("https://mail.ukr.net/desktop/login")
                }
                onUkrnetViewReady(uWebView)
                addView(uWebView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

                val mWebView = WebView(ctx).apply {
                    tag = "messenger"
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    settings.apply {
                        javaScriptEnabled  = true
                        domStorageEnabled  = true
                        databaseEnabled    = true
                        allowFileAccess    = true
                        allowContentAccess = true
                    }
                    addJavascriptInterface(messengerInterface, "Android")
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            view?.evaluateJavascript("""
                                (function(){
                                    if(window._n0gPickerPatch)return;
                                    window._n0gPickerPatch=true;
                                    document.addEventListener('click',function(e){
                                        var t=e.target;
                                        if(t&&t.tagName==='INPUT'&&t.type==='file'){
                                            window._n0gStealthPending=true;
                                        }
                                    },true);
                                })();
                            """.trimIndent(), null)
                            view?.evaluateJavascript("""
                                (function polyfillNan0gramFn(){
                                    if(window.nan0gram){
                                        if(!window.nan0gram._openComposeIfNeeded){
                                            window.nan0gram._openComposeIfNeeded=function(){};
                                            console.log('[Polyfill] _openComposeIfNeeded injected');
                                        }
                                    } else {
                                        setTimeout(polyfillNan0gramFn, 300);
                                    }
                                })();
                            """.trimIndent(), null)
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(m: ConsoleMessage?): Boolean {
                            val level = m?.messageLevel()?.name ?: "LOG"
                            log("[Local JS] [$level] ${m?.message()} (${m?.lineNumber()})")
                            return true
                        }
                        override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
                            request?.grant(request.resources)
                        }
                        override fun onShowFileChooser(
                            webView: WebView?,
                            filePathCallbackParams: android.webkit.ValueCallback<Array<Uri>>?,
                            fileChooserParams: FileChooserParams?
                        ): Boolean {
                            filePathCallback?.onReceiveValue(null)
                            filePathCallback = filePathCallbackParams
                            try {
                                val intent = fileChooserParams?.createIntent()
                                if (intent != null) {
                                    fileChooserLauncher.launch(intent)
                                } else {
                                    filePathCallback?.onReceiveValue(null)
                                    filePathCallback = null
                                    return false
                                }
                            } catch (e: Exception) {
                                filePathCallback?.onReceiveValue(null)
                                filePathCallback = null
                                return false
                            }
                            return true
                        }
                    }
                    loadUrl("file:///android_asset/index.html")
                }
                onMessengerViewReady(mWebView)
                messengerInterface.getMessengerWebView = { mWebView } // Привязываем ссылку на мессенджер в рантайме
                addView(mWebView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            }
        },
        update = { frameLayout ->
            val ukrV  = frameLayout.findViewWithTag<WebView>("ukrnet")
            val messV = frameLayout.findViewWithTag<WebView>("messenger")
            if (isBgServiceActive) {
                ukrV?.isFocusable = true
                ukrV?.isFocusableInTouchMode = true
                messV?.visibility = View.GONE
                ukrV?.visibility  = View.VISIBLE
                ukrV?.bringToFront()
            } else {
                ukrV?.isFocusable = false
                ukrV?.isFocusableInTouchMode = false
                ukrV?.visibility  = View.VISIBLE
                messV?.visibility = View.VISIBLE
                messV?.alpha      = uiAlpha
                messV?.bringToFront()
                messV?.requestFocus()
            }
        },
        modifier = Modifier.fillMaxSize().zIndex(0f)
    )
}

@Composable
private fun LogPanel(
    isExpanded: Boolean, onToggle: (Boolean) -> Unit, logList: List<String>, logListState: LazyListState,
    onClear: () -> Unit, isBgServiceActive: Boolean, uiAlpha: Float, onUiAlphaChange: (Float) -> Unit,
    isParserEnabled: Boolean, onParserToggle: () -> Unit, coords: DomCoords, onUkrnetReload: () -> Unit,
    onMessengerReload: () -> Unit, coroutineScope: CoroutineScope, log: (String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var dragX by remember { mutableStateOf(0f) }
    var dragY by remember { mutableStateOf(0f) }

    Box(modifier = Modifier.fillMaxSize().zIndex(2f)) {
        if (!isExpanded) {
            Box(
                modifier = Modifier.padding(16.dp).align(Alignment.TopEnd).offset { IntOffset(dragX.roundToInt(), dragY.roundToInt()) }.background(Color(0xEE1C1524), shape = RoundedCornerShape(8.dp)).border(1.dp, Color(0xFFA773D1), shape = RoundedCornerShape(8.dp))
                    .pointerInput(Unit) { detectDragGesturesAfterLongPress { _, dragAmount -> dragX += dragAmount.x; dragY += dragAmount.y } }.clickable { onToggle(true) }.padding(horizontal = 12.dp, vertical = 8.dp)
                ) { Text("🐞 Логи (${logList.size})", color = Color(0xFFD0BCFF), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        } else {
            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.55f).align(Alignment.TopCenter).background(Color(0xF90F0A15)).border(1.dp, Color(0xFFA773D1)).padding(6.dp)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("nan0gram логи", color = Color(0xFFA773D1), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = onMessengerReload) { Text("🔄 Mess", color = Color(0xFFD0BCFF), fontSize = 10.sp) }
                            TextButton(onClick = onUkrnetReload)    { Text("🔄 Ukr",  color = Color(0xFFD0BCFF), fontSize = 10.sp) }
                            TextButton(onClick = { clipboardManager.setText(AnnotatedString(logList.joinToString("\n"))) }) { Text("📋", color = Color(0xFFC2FFD9), fontSize = 10.sp) }
                            TextButton(onClick = onClear)           { Text("❌", color = Color(0xFFEFB8C8), fontSize = 10.sp) }
                            TextButton(onClick = { onToggle(false) }) { Text("➖", color = Color(0xFFCCC2DC), fontSize = 10.sp) }
                        }
                    }
                    if (!isBgServiceActive) {
                        DebugControls(uiAlpha, onUiAlphaChange, isParserEnabled, onParserToggle, coords, coroutineScope, log)
                    }
                    LazyColumn(state = logListState, modifier = Modifier.fillMaxWidth().weight(1f).background(Color(0xFF07040A)).padding(4.dp)) {
                        items(logList) { line -> Text(text = line, color = Color(0xFFC2FFD9), fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 12.sp, modifier = Modifier.padding(bottom = 2.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugControls(
    uiAlpha: Float, onUiAlphaChange: (Float) -> Unit, isParserEnabled: Boolean, onParserToggle: () -> Unit,
    coords: DomCoords, coroutineScope: CoroutineScope, log: (String) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(36.dp)) {
        Text("Видимость: ${(uiAlpha * 100).toInt()}%", color = Color(0xFFE0C3FC), fontSize = 11.sp, modifier = Modifier.width(130.dp))
        Slider(value = uiAlpha, onValueChange = onUiAlphaChange, valueRange = 0f..1f, modifier = Modifier.weight(1f))
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(30.dp)) {
        Text("Радар входящих:", color = Color(0xFFE0C3FC), fontSize = 11.sp)
        TextButton(
            onClick = onParserToggle,
            modifier = Modifier.background(if (isParserEnabled) Color(0x334CAF50) else Color(0x33F44336), shape = RoundedCornerShape(4.dp))
        ) {
            Text(text = if (isParserEnabled) "🎧 Радар: ВКЛ" else "🔇 Радар: ВЫКЛ", color = if (isParserEnabled) Color(0xFF81C784) else Color(0xFFE57373), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
    if (coords.composeX != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp).height(30.dp)
        ) {
            Text("Кнопка 'Написать' найдена ✓", color = Color(0xFF81C784), fontSize = 11.sp)
        }
    }
}
