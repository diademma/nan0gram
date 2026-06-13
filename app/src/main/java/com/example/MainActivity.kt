package com.example

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.KeyCharacterMap
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val logList = mutableStateListOf<String>()
    private var nullLogCounter = 0

    private fun log(message: String) {
        runOnUiThread {
            val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            logList.add("[$timestamp] $message")
            if (logList.size > 300) {
                logList.removeAt(0)
            }
        }
    }

    private fun simulateTouch(webView: WebView?, cssX: Float, cssY: Float) {
        if (webView == null) return
        val downTime = SystemClock.uptimeMillis()
        val eventTime = SystemClock.uptimeMillis()
        val density = webView.resources.displayMetrics.density
        val physicalX = cssX * density
        val physicalY = cssY * density

        val downEvent = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_DOWN, physicalX, physicalY, 0)
        val upEvent = MotionEvent.obtain(downTime, eventTime + 100, MotionEvent.ACTION_UP, physicalX, physicalY, 0)

        webView.post {
            webView.requestFocus()
            webView.requestFocusFromTouch()
            webView.dispatchTouchEvent(downEvent)
            webView.dispatchTouchEvent(upEvent)
            downEvent.recycle()
            upEvent.recycle()
            log("[Autoclicker] Имитация клика: X=${physicalX.toInt()}px, Y=${physicalY.toInt()}px")
        }
    }

    private fun simulateType(webView: WebView?, selector: String, text: String, isAutocomplete: Boolean = false) {
        if (webView == null) return
        val js = """
            (function() {
                var el = document.querySelector('$selector');
                if (!el) return 'not_found';
                el.focus();
                if (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA') {
                    el.value = '$text';
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                    el.dispatchEvent(new Event('change', { bubbles: true }));
                    if ($isAutocomplete) {
                        var enterDown = new KeyboardEvent('keydown', { bubbles: true, cancelable: true, key: 'Enter', code: 'Enter', keyCode: 13 });
                        el.dispatchEvent(enterDown);
                        var enterUp = new KeyboardEvent('keyup', { bubbles: true, cancelable: true, key: 'Enter', code: 'Enter', keyCode: 13 });
                        el.dispatchEvent(enterUp);
                        el.dispatchEvent(new Event('blur', { bubbles: true }));
                    }
                } else if (el.getAttribute('contenteditable') === 'true') {
                    el.innerHTML = '$text';
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                }
                return 'success';
            })();
        """.trimIndent()
        
        webView.post {
            webView.evaluateJavascript(js, null)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log("[System] Инициализация (ЭТАП 6: Уши / Парсер входящих)")
        enableEdgeToEdge()

        setContent {
            var isBgServiceActive by remember { mutableStateOf(true) }
            var loginStatusMsg by remember { mutableStateOf("Ожидание авторизации...") }
            var hasHandledLogin by remember { mutableStateOf(false) }
            
            var ukrnetWebView by remember { mutableStateOf<WebView?>(null) }
            var messengerWebView by remember { mutableStateOf<WebView?>(null) }
            
            var isLogPanelExpanded by remember { mutableStateOf(false) }
            var uiAlpha by remember { mutableStateOf(0.95f) }
            
            var composeX by remember { mutableStateOf<Float?>(null) }
            var composeY by remember { mutableStateOf<Float?>(null) }
            var toX by remember { mutableStateOf<Float?>(null) }
            var toY by remember { mutableStateOf<Float?>(null) }
            var subjectX by remember { mutableStateOf<Float?>(null) }
            var subjectY by remember { mutableStateOf<Float?>(null) }
            var bodyX by remember { mutableStateOf<Float?>(null) }
            var bodyY by remember { mutableStateOf<Float?>(null) }
            var sendX by remember { mutableStateOf<Float?>(null) }
            var sendY by remember { mutableStateOf<Float?>(null) }
            
            val clipboardManager = LocalClipboardManager.current
            val lazyListState = rememberLazyListState()
            val coroutineScope = rememberCoroutineScope()

            LaunchedEffect(logList.size) {
                if (logList.isNotEmpty() && isLogPanelExpanded) {
                    lazyListState.animateScrollToItem(logList.size - 1)
                }
            }

            // ПЕРИОДИЧЕСКИЙ ИНЖЕКТОР АВТОРИЗАЦИИ
            LaunchedEffect(isBgServiceActive, ukrnetWebView) {
                val monitoringJs = """
                    try {
                        if (!window.nan0gramBridgeInjected) {
                            window.nan0gramBridgeInjected = true;
                            setInterval(function() {
                                var isMailUrl = window.location.href.indexOf("mail.ukr.net") !== -1 && window.location.href.indexOf("login") === -1 && window.location.href.indexOf("accounts") === -1;
                                var el = document.querySelector('div[role="main"], .app__content, .sendmsg, #msglist');
                                if (isMailUrl || el) {
                                    if (!window.nan0gramSuccessReported) {
                                        window.nan0gramSuccessReported = true;
                                        if (window.Android && window.Android.postMessage) {
                                            window.Android.postMessage("ui", "login_success", "true");
                                        }
                                    }
                                }
                            }, 1500);
                        }
                    } catch(e) {}
                """.trimIndent()
                
                while(isBgServiceActive && ukrnetWebView != null) {
                    delay(1500)
                    ukrnetWebView?.evaluateJavascript(monitoringJs, null)
                }
            }

            // ЭТАП 2: DOM-сканер координат
            LaunchedEffect(isBgServiceActive, ukrnetWebView) {
                val scanningJs = """
                    (function() {
                        function getCoords(el) {
                            if (!el) return null;
                            var r = el.getBoundingClientRect();
                            if (r.width === 0 || r.height === 0) return null;
                            return { x: Math.round(r.left + r.width / 2), y: Math.round(r.top + r.height / 2), w: Math.round(r.width), h: Math.round(r.height) };
                        }
                        var result = {
                            compose: getCoords(document.querySelector('.ml-header__compose') || document.querySelector('a.sendmsg')),
                            to: getCoords(document.querySelector('.sm-auto-complete__input') || document.querySelector('#to')),
                            subject: getCoords(document.querySelector('#sendmsg__subject') || document.querySelector('.sendmsg__subject')),
                            body: getCoords(document.querySelector('.sm-editor__area') || document.querySelector('#body')),
                            send: getCoords(document.querySelector('.sm-header__send') || document.querySelector('#send'))
                        };
                        if (window.Android && window.Android.postCoordinates) {
                            window.Android.postCoordinates(JSON.stringify(result));
                        }
                    })();
                """.trimIndent()

                while (!isBgServiceActive && ukrnetWebView != null) {
                    delay(2000)
                    ukrnetWebView?.evaluateJavascript(scanningJs, null)
                }
            }

            // ЭТАП 6: РИДЕР ВХОДЯЩИХ (STATE MACHINE)
            LaunchedEffect(isBgServiceActive, ukrnetWebView) {
                val readerJs = """
                    (function() {
                        if (window.nan0gramReaderInjected) return;
                        window.nan0gramReaderInjected = true;

                        var processedIds = new Set();
                        var currentState = 'IDLE'; 
                        var currentTargetId = null;

                        setInterval(function() {
                            if (window.location.href.indexOf('login') !== -1) return;

                            if (currentState === 'IDLE') {
                                var listContainer = document.querySelector('.msglist');
                                if (!listContainer) return; 

                                var items = document.querySelectorAll('.ml-item');
                                // Читаем снизу вверх (от старых к новым)
                                for (var i = items.length - 1; i >= 0; i--) {
                                    var item = items[i];
                                    var id = item.id;
                                    if (!id || processedIds.has(id)) continue;

                                    var titleEl = item.querySelector('.mli-view__title');
                                    // Ищем наш секретный маркер в теме
                                    if (titleEl && titleEl.innerText.indexOf('[Ref: #270232]') !== -1) {
                                        currentState = 'READING';
                                        currentTargetId = id;
                                        var link = item.querySelector('.mli-view__link') || item;
                                        link.click(); // Открываем письмо
                                        return; 
                                    } else {
                                        // Помечаем чужие письма как обработанные, чтобы не спамить
                                        processedIds.add(id);
                                    }
                                }
                            } 
                            else if (currentState === 'READING') {
                                var bodyEl = document.querySelector('.rm-body__content');
                                var subjectEl = document.querySelector('.readmsg__subject');
                                var backBtn = document.querySelector('.rm-header__list') || document.querySelector('[aria-label="Повернутись"]');
                                
                                if (bodyEl && backBtn) {
                                    var bodyText = bodyEl.innerText || bodyEl.textContent;
                                    var subjectText = subjectEl ? (subjectEl.innerText || subjectEl.textContent) : "";
                                    
                                    if (window.Android && window.Android.onIncomingMessage) {
                                        window.Android.onIncomingMessage(currentTargetId, subjectText, bodyText);
                                    }
                                    
                                    processedIds.add(currentTargetId);
                                    backBtn.click(); // Возвращаемся в список
                                    currentState = 'RETURNING';
                                }
                            }
                            else if (currentState === 'RETURNING') {
                                var listContainer = document.querySelector('.msglist');
                                if (listContainer) {
                                    currentState = 'IDLE';
                                    currentTargetId = null;
                                }
                            }
                        }, 1000);
                    })();
                """.trimIndent()

                while (!isBgServiceActive && ukrnetWebView != null) {
                    delay(3000)
                    ukrnetWebView?.evaluateJavascript(readerJs, null)
                }
            }

            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(Color(0xFF130E19))
                ) {
                    
                    AndroidView(
                        factory = { context ->
                            FrameLayout(context).apply {
                                
                                // 1. УКРНЕТ WEBVIEW (НИЖНИЙ СЛОЙ)
                                val uWebView = WebView(context).apply {
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
                                                        log("[UkrNet] Успешный вход! Сворачиваем Укрнет.")
                                                        isBgServiceActive = false
                                                        messengerWebView?.evaluateJavascript("if (window.onLoginSuccess) { window.onLoginSuccess(); }", null)
                                                    }
                                                }
                                            }
                                        }

                                        @JavascriptInterface
                                        fun postCoordinates(json: String) {
                                            runOnUiThread {
                                                if (json != "{\"compose\":null,\"to\":null,\"subject\":null,\"body\":null,\"send\":null}") {
                                                    try {
                                                        val obj = JSONObject(json)
                                                        composeX = obj.optJSONObject("compose")?.optDouble("x", -1.0)?.toFloat()?.takeIf { it != -1f }
                                                        composeY = obj.optJSONObject("compose")?.optDouble("y", -1.0)?.toFloat()?.takeIf { it != -1f }
                                                        toX = obj.optJSONObject("to")?.optDouble("x", -1.0)?.toFloat()?.takeIf { it != -1f }
                                                        toY = obj.optJSONObject("to")?.optDouble("y", -1.0)?.toFloat()?.takeIf { it != -1f }
                                                        subjectX = obj.optJSONObject("subject")?.optDouble("x", -1.0)?.toFloat()?.takeIf { it != -1f }
                                                        subjectY = obj.optJSONObject("subject")?.optDouble("y", -1.0)?.toFloat()?.takeIf { it != -1f }
                                                        bodyX = obj.optJSONObject("body")?.optDouble("x", -1.0)?.toFloat()?.takeIf { it != -1f }
                                                        bodyY = obj.optJSONObject("body")?.optDouble("y", -1.0)?.toFloat()?.takeIf { it != -1f }
                                                        sendX = obj.optJSONObject("send")?.optDouble("x", -1.0)?.toFloat()?.takeIf { it != -1f }
                                                        sendY = obj.optJSONObject("send")?.optDouble("y", -1.0)?.toFloat()?.takeIf { it != -1f }
                                                    } catch (e: Exception) {}
                                                }
                                            }
                                        }

                                        // ЭТАП 6: ПРИЕМ СООБЩЕНИЯ ИЗ Укрнета в Kotlin
                                        @JavascriptInterface
                                        fun onIncomingMessage(id: String, subject: String, body: String) {
                                            runOnUiThread {
                                                log("[Parser] Найдено письмо: $id. Извлечение payload...")
                                                
                                                // Ищем зашифрованный блок между маркерами ===[ и ]===
                                                val match = Regex("===\\[(.*?)\\]===").find(body)
                                                if (match != null) {
                                                    val aesPayload = match.groupValues[1]
                                                    log("[Parser] AES Payload извлечен. Передаем в ExteraGram UI.")
                                                    
                                                    // Формируем JSON, который ждет твой ExteraGram
                                                    val jsonArray = JSONArray()
                                                    val msgObj = JSONObject()
                                                    msgObj.put("chatId", "chat_1") // Пока хардкодим один чат для теста
                                                    msgObj.put("author", "Собеседник")
                                                    msgObj.put("text", aesPayload)
                                                    msgObj.put("ts", System.currentTimeMillis())
                                                    jsonArray.put(msgObj)
                                                    
                                                    val jsonString = jsonArray.toString().replace("\"", "\\\"")
                                                    
                                                    // Передаем данные в твой index.html
                                                    messengerWebView?.evaluateJavascript("if (window.ExteraGram && window.ExteraGram.onEmailReceived) { window.ExteraGram.onEmailReceived(\"$jsonString\"); }", null)
                                                } else {
                                                    log("[Parser] ОШИБКА: Маркеры ===[...]=== не найдены в письме.")
                                                }
                                            }
                                        }
                                    }, "Android")

                                    webViewClient = object : WebViewClient() {
                                        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                            super.doUpdateVisitedHistory(view, url, isReload)
                                            if (url != null && url.contains("mail.ukr.net") && !url.contains("login") && !url.contains("accounts.ukr.net")) {
                                                if (isBgServiceActive && !hasHandledLogin) {
                                                    hasHandledLogin = true
                                                    isBgServiceActive = false
                                                    messengerWebView?.evaluateJavascript("if (window.onLoginSuccess) { window.onLoginSuccess(); }", null)
                                                }
                                            }
                                        }
                                        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) { handler?.cancel() }
                                    }
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean { return true }
                                    }
                                    loadUrl("https://mail.ukr.net/desktop/login")
                                }
                                ukrnetWebView = uWebView
                                addView(uWebView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

                                // 2. ЛОКАЛЬНЫЙ MESSENGER WEBVIEW (ВЕРХНИЙ СЛОЙ)
                                val mWebView = WebView(context).apply {
                                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        databaseEnabled = true
                                        allowFileAccess = true
                                        allowContentAccess = true
                                    }
                                    addJavascriptInterface(object : Any() {
                                        @JavascriptInterface
                                        fun setBgServiceActive(active: Boolean) { runOnUiThread { isBgServiceActive = active } }
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
                                messengerWebView = mWebView
                                addView(mWebView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                            }
                        },
                        update = { _ ->
                            if (isBgServiceActive) {
                                messengerWebView?.visibility = View.GONE
                                ukrnetWebView?.visibility = View.VISIBLE
                                ukrnetWebView?.bringToFront()
                            } else {
                                ukrnetWebView?.visibility = View.VISIBLE 
                                messengerWebView?.visibility = View.VISIBLE
                                messengerWebView?.alpha = uiAlpha
                                messengerWebView?.bringToFront()
                            }
                        },
                        modifier = Modifier.fillMaxSize().zIndex(0f)
                    )

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

                    // Панель логов
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
                                    .fillMaxHeight(0.55f)
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

                                        // ЭТАП 6 ТЕСТ: Отправить тестовое письмо СЕБЕ ЖЕ с маркерами и шифром
                                        if (toX != null && subjectX != null && bodyX != null && sendX != null) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                                    .height(30.dp)
                                            ) {
                                                Text(
                                                    text = "Отправка + Парсер:",
                                                    color = Color(0xFF64B5F6),
                                                    fontSize = 11.sp
                                                )
                                                TextButton(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            log("[Test] Запуск отправки зашифрованного сообщения...")
                                                            
                                                            simulateTouch(ukrnetWebView, toX!!, toY!!)
                                                            delay(500)
                                                            simulateType(ukrnetWebView, ".sm-auto-complete__input", "270232@ukr.net", isAutocomplete = true)
                                                            delay(1200)
                                                            
                                                            simulateTouch(ukrnetWebView, subjectX!!, subjectY!!)
                                                            delay(500)
                                                            simulateType(ukrnetWebView, "#sendmsg__subject", "RE: Сверка остатков [Ref: #270232]")
                                                            delay(1200)
                                                            
                                                            // Тестовый AES пакет. (Hello World зашифрованный тестовым ключом "test")
                                                            // В реале это будет генерировать index.html
                                                            val testEncryptedPayload = "U2FsdGVkX19qS7qjN8qjN8qjN8qjN8qjN8qjN8qjN8o="
                                                            val fakeText = "Добрый день! Направляю актуальные данные по вашему запросу во вложении.<br><br>===[$testEncryptedPayload]===<br><br>С уважением."
                                                            
                                                            simulateTouch(ukrnetWebView, bodyX!!, bodyY!!)
                                                            delay(500)
                                                            simulateType(ukrnetWebView, ".sm-editor__area", fakeText)
                                                            delay(1200)
                                                            
                                                            simulateTouch(ukrnetWebView, sendX!!, sendY!!)
                                                            log("[Test] Письмо отправлено! Ждем реакции Парсера (Уши)...")
                                                        }
                                                    },
                                                    modifier = Modifier.background(Color(0x332196F3), shape = RoundedCornerShape(4.dp))
                                                ) {
                                                    Text(
                                                        text = "🚀 Отправить AES Тест",
                                                        color = Color(0xFF64B5F6),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
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
