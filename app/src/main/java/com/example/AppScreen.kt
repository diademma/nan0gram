package com.example

import android.annotation.SuppressLint
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════════════════════
// APP SCREEN  — весь UI: WebView-слой + панель логов + дебаг-контролы
// Добавляй новые экраны, панели, второй ukrnet WebView — сюда.
// Никакой бизнес-логики. Никаких JS-инъекций. Только рендер.
// ═══════════════════════════════════════════════════════════════════════════

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AppScreen(
    // Состояние
    isBgServiceActive: Boolean,
    isLogPanelExpanded: Boolean,
    uiAlpha: Float,
    isParserEnabled: Boolean,
    logList: List<String>,
    logListState: LazyListState,
    coords: DomCoords,
    // Колбэки
    onLogPanelToggle: (Boolean) -> Unit,
    onUiAlphaChange: (Float) -> Unit,
    onParserToggle: () -> Unit,
    onLogClear: () -> Unit,
    onUkrnetViewReady: (WebView) -> Unit,
    onMessengerViewReady: (WebView) -> Unit,
    onUkrnetReload: () -> Unit,
    onMessengerReload: () -> Unit,
    // Зависимости
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

// ─── WebView слой ──────────────────────────────────────────────────────────
// Сюда добавлять ukrnet2WebView со слайдером (панорамный ридер).

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
    AndroidView(
        factory = { context ->
            FrameLayout(context).apply {

                // ── UKRNET WEBVIEW ──────────────────────────────────────────
                val uWebView = WebView(context).apply {
                    settings.apply {
                        javaScriptEnabled    = true
                        domStorageEnabled    = true
                        databaseEnabled      = true
                        useWideViewPort      = true
                        loadWithOverviewMode = true
                        userAgentString      = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    }
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    addJavascriptInterface(ukrnetInterface, "Android")
                    webViewClient = object : WebViewClient() {
                        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                            super.doUpdateVisitedHistory(view, url, isReload)
                            if (url != null) ukrnetInterface.onUrlChange(url)
                        }
                        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                            handler?.cancel()
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(m: ConsoleMessage?) = true
                    }
                    loadUrl("https://mail.ukr.net/desktop/login")
                }
                onUkrnetViewReady(uWebView)
                addView(uWebView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

                // ── MESSENGER WEBVIEW ───────────────────────────────────────
                val mWebView = WebView(context).apply {
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    settings.apply {
                        javaScriptEnabled  = true
                        domStorageEnabled  = true
                        databaseEnabled    = true
                        allowFileAccess    = true
                        allowContentAccess = true
                    }
                    addJavascriptInterface(messengerInterface, "Android")
                    webViewClient = WebViewClient()
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(m: ConsoleMessage?): Boolean {
                            val level = m?.messageLevel()?.name ?: "LOG"
                            log("[Local JS] [$level] ${m?.message()} (${m?.lineNumber()})")
                            return true
                        }
                    }
                    loadUrl("file:///android_asset/index.html")
                }
                onMessengerViewReady(mWebView)
                addView(mWebView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            }
        },
        update = { frameLayout ->
            val ukrV  = frameLayout.getChildAt(0) as? WebView
            val messV = frameLayout.getChildAt(1) as? WebView
            if (isBgServiceActive) {
                messV?.visibility = View.GONE
                ukrV?.visibility  = View.VISIBLE
                ukrV?.bringToFront()
            } else {
                ukrV?.visibility  = View.VISIBLE
                messV?.visibility = View.VISIBLE
                messV?.alpha      = uiAlpha
                messV?.bringToFront()
            }
        },
        modifier = Modifier.fillMaxSize().zIndex(0f)
    )
}

// ─── Панель логов ──────────────────────────────────────────────────────────

@Composable
private fun LogPanel(
    isExpanded: Boolean,
    onToggle: (Boolean) -> Unit,
    logList: List<String>,
    logListState: LazyListState,
    onClear: () -> Unit,
    isBgServiceActive: Boolean,
    uiAlpha: Float,
    onUiAlphaChange: (Float) -> Unit,
    isParserEnabled: Boolean,
    onParserToggle: () -> Unit,
    coords: DomCoords,
    onUkrnetReload: () -> Unit,
    onMessengerReload: () -> Unit,
    coroutineScope: CoroutineScope,
    log: (String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

    Box(modifier = Modifier.fillMaxSize().zIndex(2f)) {
        if (!isExpanded) {
            // Свёрнутый бейдж
            Box(
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.TopEnd)
                    .background(Color(0xEE1C1524), shape = RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFFA773D1), shape = RoundedCornerShape(8.dp))
                    .clickable { onToggle(true) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text("🐞 Логи (${logList.size})", color = Color(0xFFD0BCFF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            // Развёрнутая панель
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.55f)
                    .align(Alignment.TopCenter)
                    .background(Color(0xF90F0A15))
                    .border(1.dp, Color(0xFFA773D1))
                    .padding(6.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {

                    // Тулбар
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "nan0gram логи",
                            color = Color(0xFFA773D1), fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = onMessengerReload) { Text("🔄 Mess", color = Color(0xFFD0BCFF), fontSize = 10.sp) }
                            TextButton(onClick = onUkrnetReload)    { Text("🔄 Ukr",  color = Color(0xFFD0BCFF), fontSize = 10.sp) }
                            TextButton(onClick = { clipboardManager.setText(AnnotatedString(logList.joinToString("\n"))) }) {
                                Text("📋", color = Color(0xFFC2FFD9), fontSize = 10.sp)
                            }
                            TextButton(onClick = onClear)           { Text("❌", color = Color(0xFFEFB8C8), fontSize = 10.sp) }
                            TextButton(onClick = { onToggle(false) }) { Text("➖", color = Color(0xFFCCC2DC), fontSize = 10.sp) }
                        }
                    }

                    // Дебаг-контролы (только в режиме мессенджера)
                    if (!isBgServiceActive) {
                        DebugControls(
                            uiAlpha = uiAlpha,
                            onUiAlphaChange = onUiAlphaChange,
                            isParserEnabled = isParserEnabled,
                            onParserToggle = onParserToggle,
                            coords = coords,
                            coroutineScope = coroutineScope,
                            log = log
                        )
                    }

                    // Список логов
                    LazyColumn(
                        state = logListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color(0xFF07040A))
                            .padding(4.dp)
                    ) {
                        items(logList) { line ->
                            Text(
                                text = line,
                                color = if (line.contains("error", ignoreCase = true) || line.contains("ошибка", ignoreCase = true))
                                    Color(0xFFFF8A8A) else Color(0xFFC2FFD9),
                                fontFamily = FontFamily.Monospace,
                                fontSize   = 10.sp,
                                lineHeight = 12.sp,
                                modifier   = Modifier.padding(bottom = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Дебаг-контролы ────────────────────────────────────────────────────────
// Слайдер прозрачности, кнопка «Радар», статус координат.
// Добавляй сюда новые дебаг-кнопки: test encrypt, ping, export logs, и т.д.

@Composable
private fun DebugControls(
    uiAlpha: Float,
    onUiAlphaChange: (Float) -> Unit,
    isParserEnabled: Boolean,
    onParserToggle: () -> Unit,
    coords: DomCoords,
    coroutineScope: CoroutineScope,
    log: (String) -> Unit
) {
    // Слайдер прозрачности мессенджера
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(36.dp)
    ) {
        Text(
            "Видимость: ${(uiAlpha * 100).toInt()}%",
            color = Color(0xFFE0C3FC), fontSize = 11.sp,
            modifier = Modifier.width(130.dp)
        )
        Slider(value = uiAlpha, onValueChange = onUiAlphaChange, valueRange = 0f..1f, modifier = Modifier.weight(1f))
    }

    // Кнопка «Радар входящих»
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(30.dp)
    ) {
        Text("Радар входящих:", color = Color(0xFFE0C3FC), fontSize = 11.sp)
        TextButton(
            onClick = onParserToggle,
            modifier = Modifier.background(
                if (isParserEnabled) Color(0x334CAF50) else Color(0x33F44336),
                shape = RoundedCornerShape(4.dp)
            )
        ) {
            Text(
                text  = if (isParserEnabled) "🎧 Радар: ВКЛ" else "🔇 Радар: ВЫКЛ",
                color = if (isParserEnabled) Color(0xFF81C784) else Color(0xFFE57373),
                fontSize = 10.sp, fontWeight = FontWeight.Bold
            )
        }
    }

    // Статус: кнопка «Написать» найдена
    if (coords.composeX != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp).height(30.dp)
        ) {
            Text("Кнопка 'Написать' найдена ✓", color = Color(0xFF81C784), fontSize = 11.sp)
            // Тап-тест передаётся снаружи через колбэк в будущем
        }
    }
}
