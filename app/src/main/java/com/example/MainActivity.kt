package com.example

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var ukrnetWebView: WebView? = null
    private var messengerWebView: WebView? = null
    
    private val logList = mutableStateListOf<String>()

    private fun log(message: String) {
        runOnUiThread {
            val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            logList.add("[$timestamp] $message")
            if (logList.size > 300) {
                logList.removeAt(0)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log("[System] Инициализация (ЭТАП 1: Нативный FrameLayout)")
        enableEdgeToEdge()

        setContent {
            var isBgServiceActive by remember { mutableStateOf(true) } // true = режим логина
            var loginStatusMsg by remember { mutableStateOf("Ожидание авторизации...") }
            var hasHandledLogin by remember { mutableStateOf(false) }
            
            var isLogPanelExpanded by remember { mutableStateOf(false) }
            var uiAlpha by remember { mutableStateOf(0.9f) } // Чуть прозрачный по умолчанию
            
            val clipboardManager = LocalClipboardManager.current
            val lazyListState = rememberLazyListState()

            LaunchedEffect(logList.size) {
                if (logList.isNotEmpty() && isLogPanelExpanded) {
                    lazyListState.animateScrollToItem(logList.size - 1)
                }
            }

            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(Color(0xFF130E19))
                ) {
                    
                    // ЕДИНЫЙ ANDROID VIEW С NATIVE FRAMELAYOUT
                    AndroidView(
                        factory = { context ->
                            FrameLayout(context).apply {
                                
                                // 1. УКРНЕТ WEBVIEW (НИЖНИЙ СЛОЙ)
                                ukrnetWebView = WebView(context).apply {
                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        databaseEnabled = true
                                        useWideViewPort = true
                                        loadWithOverviewMode = true
                                        userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                                    }
                                    CookieManager.getInstance().setAcceptCookie(true)
                                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                                    addJavascriptInterface(object : Any() {
                                        @JavascriptInterface
                                        fun postMessage(type: String, key: String, value: String) {
                                            runOnUiThread {
                                                if (type == "ui" && key == "login_success" && value == "true") {
                                                    if (!hasHandledLogin) {
                                                        hasHandledLogin = true
                                                        log("[UkrNet JS API] Успешный вход! Поднимаем ExteraGram поверх Укрнета.")
                                                        loginStatusMsg = "Авторизация пройдена!"
                                                        isBgServiceActive = false
                                                        messengerWebView?.evaluateJavascript("if (window.onLoginSuccess) { window.onLoginSuccess(); }", null)
                                                    }
                                                }
                                            }
                                        }
                                    }, "Android")

                                    webViewClient = object : WebViewClient() {
                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            log("[UkrNet] Загрузка завершена: $url")
                                            
                                            val monitoringJs = """
                                                (function() {
                                                    if (window.nan0gramBridgeInjected) return;
                                                    window.nan0gramBridgeInjected = true;
                                                    console.log("nan0gram bridge injected into UkrNet");
                                                    var checkInterval = setInterval(function() {
                                                        var mainElement = document.querySelector('div[role="main"]') || document.querySelector('.app__content') || document.querySelector('.sendmsg');
                                                        if (mainElement) {
                                                            console.log("nan0gram detected success login view!");
                                                            clearInterval(checkInterval);
                                                            if (window.Android && window.Android.postMessage) {
                                                                window.Android.postMessage("ui", "login_success", "true");
                                                            }
                                                        }
                                                    }, 1500);
                                                })();
                                            """.trimIndent()
                                            evaluateJavascript(monitoringJs, null)
                                        }

                                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                            super.onReceivedError(view, request, error)
                                            val url = request?.url.toString()
                                            if (!url.contains("tracker") && !url.contains("ad")) {
                                                log("[UkrNet ERROR] ${error?.description} URL: $url")
                                            }
                                        }

                                        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                                            handler?.cancel()
                                        }
                                    }
                                    
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                            val msg = consoleMessage?.message() ?: ""
                                            val level = consoleMessage?.messageLevel()?.name ?: "LOG"
                                            // Скрываем только совсем мусорные варнинги, всё остальное выводим!
                                            if (!msg.contains("Unknown event type")) {
                                                log("[UkrNet JS Console] [$level] $msg")
                                            }
                                            return true
                                        }
                                    }
                                    loadUrl("https://mail.ukr.net/desktop/login")
                                }
                                addView(ukrnetWebView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

                                // 2. ЛОКАЛЬНЫЙ MESSENGER WEBVIEW (ВЕРХНИЙ СЛОЙ)
                                messengerWebView = WebView(context).apply {
                                    setBackgroundColor(android.graphics.Color.TRANSPARENT) // Чтобы было видно, что под ним
                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        databaseEnabled = true
                                        allowFileAccess = true
                                        allowContentAccess = true
                                    }
                                    addJavascriptInterface(object : Any() {
                                        @JavascriptInterface
                                        fun setBgServiceActive(active: Boolean) {
                                            runOnUiThread { isBgServiceActive = active }
                                        }
                                        @JavascriptInterface
                                        fun postMessage(type: String, key: String, value: String) {}
                                    }, "Android")

                                    webViewClient = WebViewClient()
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                            val level = consoleMessage?.messageLevel()?.name ?: "LOG"
                                            log("[Local JS] [$level] ${consoleMessage?.message()} (${consoleMessage?.lineNumber()})")
                                            return true
                                        }
                                    }
                                    loadUrl("file:///android_asset/index.html")
                                }
                                addView(messengerWebView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                            }
                        },
                        update = { _ ->
                            // Магия нативного управления:
                            if (isBgServiceActive) {
                                // Этап авторизации: Мессенджера физически "нет", Укрнет принимает все клики
                                messengerWebView?.visibility = View.GONE
                                ukrnetWebView?.visibility = View.VISIBLE
                                ukrnetWebView?.bringToFront()
                            } else {
                                // Мессенджер работает: Укрнет остается видимым внизу, мессенджер ловит все клики поверх него
                                ukrnetWebView?.visibility = View.VISIBLE 
                                messengerWebView?.visibility = View.VISIBLE
                                messengerWebView?.alpha = uiAlpha // Устанавливаем прозрачность
                                messengerWebView?.bringToFront()
                            }
                        },
                        modifier = Modifier.fillMaxSize().zIndex(0f)
                    )

                    // Индикатор при логине
                    if (isBgServiceActive) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xE01C1524))
                                .padding(12.dp)
                                .align(Alignment.BottomCenter)
                                .zIndex(1f)
                        ) {
                            Text(
                                text = "nan0gram: $loginStatusMsg",
                                color = Color(0xFFA773D1),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }

                    // ==========================================
                    // ПАНЕЛЬ ЛОГОВ (ВСЕГДА НА САМОМ ВЕРХУ)
                    // ==========================================
                    Box(modifier = Modifier.fillMaxSize().zIndex(2f)) {
                        if (!isLogPanelExpanded) {
                            Box(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .align(Alignment.TopEnd)
                                    .background(Color(0xEE1C1524), shape = RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0xFFA773D1), shape = RoundedCornerShape(8.dp))
                                    .clickable { isLogPanelExpanded = true }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "🐞 Логи (${logList.size})",
                                    color = Color(0xFFD0BCFF),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(0.5f)
                                    .align(Alignment.TopCenter)
                                    .background(Color(0xF90F0A15))
                                    .border(1.dp, Color(0xFFA773D1))
                                    .padding(6.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Системные логи",
                                            color = Color(0xFFA773D1),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(start = 4.dp)
                                        )
                                        
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            TextButton(onClick = { messengerWebView?.reload() }) { Text("🔄 Mess", color = Color(0xFFD0BCFF), fontSize = 10.sp) }
                                            TextButton(onClick = { ukrnetWebView?.reload() }) { Text("🔄 Ukr", color = Color(0xFFD0BCFF), fontSize = 10.sp) }
                                            TextButton(onClick = {
                                                clipboardManager.setText(AnnotatedString(logList.joinToString("\n")))
                                            }) { Text("📋", color = Color(0xFFC2FFD9), fontSize = 10.sp) }
                                            TextButton(onClick = { logList.clear() }) { Text("❌", color = Color(0xFFEFB8C8), fontSize = 10.sp) }
                                            TextButton(onClick = { isLogPanelExpanded = false }) { Text("➖", color = Color(0xFFCCC2DC), fontSize = 10.sp) }
                                        }
                                    }

                                    if (!isBgServiceActive) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp)
                                                .height(36.dp)
                                        ) {
                                            Text(
                                                text = "Видимость UI: ${(uiAlpha * 100).toInt()}%", 
                                                color = Color(0xFFE0C3FC), 
                                                fontSize = 11.sp, 
                                                modifier = Modifier.width(130.dp)
                                            )
                                            Slider(
                                                value = uiAlpha,
                                                onValueChange = { uiAlpha = it },
                                                valueRange = 0f..1f,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }

                                    LazyColumn(
                                        state = lazyListState,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .background(Color(0xFF07040A))
                                            .padding(4.dp)
                                    ) {
                                        items(logList) { logLine ->
                                            val isError = logLine.contains("ERROR", ignoreCase = true)
                                            val textColor = if (isError) Color(0xFFFF8A8A) else Color(0xFFC2FFD9)

                                            Text(
                                                text = logLine,
                                                color = textColor,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                lineHeight = 12.sp,
                                                modifier = Modifier.padding(bottom = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
