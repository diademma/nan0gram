package com.example

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var ukrnetWebView: WebView? = null
    private var messengerWebView: WebView? = null
    
    // Потокобезопасный список для хранения логов, наблюдаемый Jetpack Compose
    private val logList = mutableStateListOf<String>()

    private fun log(message: String) {
        runOnUiThread {
            val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            logList.add("[$timestamp] $message")
            // Ограничиваем размер логов для избежания переполнения памяти
            if (logList.size > 300) {
                logList.removeAt(0)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log("[System] Инициализация приложения (onCreate)")
        enableEdgeToEdge()

        setContent {
            var isBgServiceActive by remember { mutableStateOf(true) } // Начинаем с true, чтобы сначала авторизоваться в UkrNet
            var loginStatusMsg by remember { mutableStateOf("Ожидание авторизации...") }
            var hasHandledLogin by remember { mutableStateOf(false) } // ПРЕДОХРАНИТЕЛЬ
            
            // Состояние панели логов
            var isLogPanelExpanded by remember { mutableStateOf(false) }
            val clipboardManager = LocalClipboardManager.current
            val lazyListState = rememberLazyListState()

            // Автоматическая прокрутка логов вниз при добавлении новых записей
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
                    // 1. Локальный клиент Messenger WebView
                    AndroidView(
                        factory = { context ->
                            WebView(context).apply {
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    allowFileAccess = true
                                }
                                addJavascriptInterface(object : Any() {
                                    @JavascriptInterface
                                    fun setBgServiceActive(active: Boolean) {
                                        log("[JS Local API] setBgServiceActive вызван с параметром: $active")
                                        runOnUiThread {
                                            isBgServiceActive = active
                                        }
                                    }

                                    @JavascriptInterface
                                    fun postMessage(type: String, key: String, value: String) {
                                        log("[JS Local API] postMessage -> type: $type, key: $key, value: $value")
                                        runOnUiThread {
                                            if (type == "ui" && key == "login_success" && value == "true") {
                                                log("[JS Local API] Авторизация успешна. Скрываем фоновую службу.")
                                                loginStatusMsg = "Авторизация пройдена!"
                                                isBgServiceActive = false
                                                hasHandledLogin = true
                                                evaluateJavascript("if (window.onLoginSuccess) { window.onLoginSuccess(); }", null)
                                            }
                                        }
                                    }
                                }, "Android")

                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        log("[Local WebView] Переход по ссылке: ${request?.url}")
                                        return false
                                    }

                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        log("[Local WebView] Начало загрузки страницы: $url")
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        log("[Local WebView] Завершена загрузка страницы: $url")
                                    }

                                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                        super.onReceivedError(view, request, error)
                                        log("[Local WebView ERROR] Ошибка: ${error?.description} (Код: ${error?.errorCode}) для URL: ${request?.url}")
                                    }

                                    override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                                        super.onReceivedHttpError(view, request, errorResponse)
                                        log("[Local WebView ERROR] HTTP ошибка: ${errorResponse?.statusCode} для URL: ${request?.url}")
                                    }
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                        val level = consoleMessage?.messageLevel()?.name ?: "LOG"
                                        log("[Local JS Console] [$level] ${consoleMessage?.message()} (${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()})")
                                        return true
                                    }
                                }

                                log("[System] Загрузка локального index.html...")
                                loadUrl("file:///android_asset/index.html")
                                messengerWebView = this
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // 2. Фоновый UkrNet WebView (скрывается в 1x1 или разворачивается на весь экран)
                    AndroidView(
                        factory = { context ->
                            WebView(context).apply {
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
                                        log("[UkrNet JS API] postMessage -> type: $type, key: $key, value: $value")
                                        runOnUiThread {
                                            if (type == "ui" && key == "login_success" && value == "true") {
                                                if (!hasHandledLogin) {
                                                    hasHandledLogin = true
                                                    log("[UkrNet JS API] Обнаружен успешный вход. Сворачиваем UkrNet.")
                                                    loginStatusMsg = "Авторизация пройдена!"
                                                    isBgServiceActive = false
                                                    log("[System] Передача сигнала onLoginSuccess локальному клиенту...")
                                                    messengerWebView?.evaluateJavascript("if (window.onLoginSuccess) { window.onLoginSuccess(); }", null)
                                                } else {
                                                    log("[System] Сигнал успешного входа проигнорирован (уже обработан).")
                                                }
                                            }
                                        }
                                    }
                                }, "Android")

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        log("[UkrNet WebView] Начало загрузки: $url")
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        log("[UkrNet WebView] Загрузка завершена: $url")
                                        
                                        // Внедрение скрипта мониторинга (с остановкой цикла!)
                                        val monitoringJs = """
                                            (function() {
                                                if (window.nan0gramBridgeInjected) return;
                                                window.nan0gramBridgeInjected = true;
                                                console.log("nan0gram bridge injected into UkrNet");
                                                var checkInterval = setInterval(function() {
                                                    var mainElement = document.querySelector('div[role="main"]') || document.querySelector('.app__content') || document.querySelector('.sendmsg');
                                                    if (mainElement) {
                                                        console.log("nan0gram detected success login view!");
                                                        clearInterval(checkInterval); // <--- ВОТ ЗДЕСЬ ОСТАНАВЛИВАЕМ СПАМ!
                                                        if (window.Android && window.Android.postMessage) {
                                                            window.Android.postMessage("ui", "login_success", "true");
                                                        }
                                                    }
                                                }, 1500);
                                            })();
                                        """.trimIndent()
                                        
                                        log("[System] Инъекция JS-скрипта мониторинга в UkrNet...")
                                        evaluateJavascript(monitoringJs, null)
                                    }

                                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                        super.onReceivedError(view, request, error)
                                        log("[UkrNet WebView ERROR] Ошибка: ${error?.description} (Код: ${error?.errorCode}) для URL: ${request?.url}")
                                    }

                                    override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                                        super.onReceivedHttpError(view, request, errorResponse)
                                        log("[UkrNet WebView ERROR] HTTP ошибка: ${errorResponse?.statusCode} для URL: ${request?.url}")
                                    }

                                    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                                        log("[UkrNet WebView SSL ERROR] Ошибка SSL: $error на странице ${view?.url}")
                                        handler?.cancel()
                                    }
                                }
                                
                                webChromeClient = object : WebChromeClient() {
                                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                        val level = consoleMessage?.messageLevel()?.name ?: "LOG"
                                        log("[UkrNet JS Console] [$level] ${consoleMessage?.message()} (${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()})")
                                        return true
                                    }
                                }
                                
                                log("[System] Загрузка UkrNet страницы логина...")
                                loadUrl("https://mail.ukr.net/desktop/login")
                                ukrnetWebView = this
                            }
                        },
                        modifier = if (isBgServiceActive) {
                            Modifier.fillMaxSize()
                        } else {
                            Modifier.size(1.dp) // Скрытый режим: 1x1
                        }
                    )

                    // Эстетичный индикатор статуса
                    if (isBgServiceActive) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xE01C1524))
                                .padding(12.dp)
                                .align(Alignment.BottomCenter)
                        ) {
                            Text(
                                text = "nan0gram: $loginStatusMsg",
                                color = Color(0xFFA773D1),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }

                    // ПАНЕЛЬ ЛОГОВ
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
                                .fillMaxHeight(0.45f)
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
                                        text = "Системные логи (${logList.size})",
                                        color = Color(0xFFA773D1),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                    
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        TextButton(onClick = {
                                            log("[System] Ручной перезапуск Messenger WebView")
                                            messengerWebView?.reload()
                                        }) { Text("🔄 Mess", color = Color(0xFFD0BCFF), fontSize = 10.sp) }

                                        TextButton(onClick = {
                                            log("[System] Ручной перезапуск UkrNet WebView")
                                            ukrnetWebView?.reload()
                                        }) { Text("🔄 Ukr", color = Color(0xFFD0BCFF), fontSize = 10.sp) }

                                        TextButton(onClick = {
                                            val allLogs = logList.joinToString("\n")
                                            clipboardManager.setText(AnnotatedString(allLogs))
                                            log("[System] Логи скопированы!")
                                        }) { Text("📋 Копировать", color = Color(0xFFC2FFD9), fontSize = 10.sp) }

                                        TextButton(onClick = { logList.clear() }) { Text("❌", color = Color(0xFFEFB8C8), fontSize = 10.sp) }
                                        TextButton(onClick = { isLogPanelExpanded = false }) { Text("➖", color = Color(0xFFCCC2DC), fontSize = 10.sp) }
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
                                        val isError = logLine.contains("ERROR", ignoreCase = true) || logLine.contains("Ssl Error", ignoreCase = true) || (logLine.contains("Console") && logLine.contains("ERROR"))
                                        val textColor = when {
                                            isError -> Color(0xFFFF8A8A)
                                            logLine.contains("Console") -> Color(0xFFE0C3FC)
                                            else -> Color(0xFFC2FFD9)
                                        }

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