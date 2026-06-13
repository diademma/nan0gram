package com.example

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyCharacterMap
import android.view.KeyEvent
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
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val logList = mutableStateListOf<String>()
    private var nullLogCounter = 0 // Счетчик для неспамящего лога сканера

    private fun log(message: String) {
        runOnUiThread {
            val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            logList.add("[$timestamp] $message")
            if (logList.size > 300) {
                logList.removeAt(0)
            }
        }
    }

    // Симуляция аппаратного клика по координатам
    private fun simulateTouch(webView: WebView?, cssX: Float, cssY: Float) {
        if (webView == null) return
        
        val downTime = SystemClock.uptimeMillis()
        val eventTime = SystemClock.uptimeMillis()
        
        val density = webView.resources.displayMetrics.density
        val physicalX = cssX * density
        val physicalY = cssY * density

        val downEvent = MotionEvent.obtain(
            downTime, eventTime,
            MotionEvent.ACTION_DOWN, physicalX, physicalY, 0
        )
        val upEvent = MotionEvent.obtain(
            downTime, eventTime + 100,
            MotionEvent.ACTION_UP, physicalX, physicalY, 0
        )

        webView.post {
            webView.dispatchTouchEvent(downEvent)
            webView.dispatchTouchEvent(upEvent)
            downEvent.recycle()
            upEvent.recycle()
            log("[Autoclicker] Имитация клика: X=${physicalX.toInt()}px (CSS ${cssX.toInt()}), Y=${physicalY.toInt()}px (CSS ${cssY.toInt()})")
        }
    }

    // ЭТАП 4: Симуляция нативного посимвольного ввода текста в сфокусированное поле
    private fun simulateType(webView: WebView?, text: String) {
        if (webView == null) return
        
        // Создаем ивент ввода строки на уровне прерываний клавиатуры
        val event = KeyEvent(
            SystemClock.uptimeMillis(),
            text,
            KeyCharacterMap.VIRTUAL_KEYBOARD,
            0
        )
        
        webView.post {
            webView.dispatchKeyEvent(event)
            log("[Autoclicker] Набран текст: \"$text\"")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log("[System] Инициализация (ЭТАП 4: Посимвольный ввод)")
        enableEdgeToEdge()

        setContent {
            var isBgServiceActive by remember { mutableStateOf(true) } // true = режим логина
            var loginStatusMsg by remember { mutableStateOf("Ожидание авторизации...") }
            var hasHandledLogin by remember { mutableStateOf(false) }
            
            var ukrnetWebView by remember { mutableStateOf<WebView?>(null) }
            var messengerWebView by remember { mutableStateOf<WebView?>(null) }
            
            var isLogPanelExpanded by remember { mutableStateOf(false) }
            var uiAlpha by remember { mutableStateOf(0.95f) }
            
            // Координаты элементов для автоматизации
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

            // Периодический инжектор авторизации (до входа)
            LaunchedEffect(isBgServiceActive, ukrnetWebView) {
                val monitoringJs = """
                    try {
                        if (!window.nan0gramBridgeInjected) {
                            window.nan0gramBridgeInjected = true;
                            console.log("nan0gram bridge injected into UkrNet (Periodic)");
                            setInterval(function() {
                                var isMailUrl = window.location.href.indexOf("mail.ukr.net") !== -1 && window.location.href.indexOf("login") === -1 && window.location.href.indexOf("accounts") === -1;
                                var el = document.querySelector('div[role="main"], .app__content, .sendmsg, #msglist, .message-list, .layout, .screen__head, .mail-app, .xf-list');
                                if (isMailUrl || el) {
                                    if (!window.nan0gramSuccessReported) {
                                        window.nan0gramSuccessReported = true;
                                        console.log("nan0gram detected success login view (JS)!");
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

            // ЭТАП 2: DOM-сканер координат (активируется после входа)
            LaunchedEffect(isBgServiceActive, ukrnetWebView) {
                val scanningJs = """
                    (function() {
                        function findCompose() {
                            var exact = [
                                '.ml-header__compose', 
                                'a.sendmsg', '.sendmsg', 'a[href*="sendmsg"]', 
                                '[aria-label="Написати"]', '[aria-label="Написать"]', 
                                '[aria-label*="Написати"]', '[aria-label*="Написать"]',
                                '.screen__head-btn_write', '.header__btn_write',
                                '.write-btn', '.compose-btn', '.btn-write', '.icon-write'
                            ];
                            for (var i = 0; i < exact.length; i++) {
                                var el = document.querySelector(exact[i]);
                                if (el) {
                                    var r = el.getBoundingClientRect();
                                    if (r.width > 0 && r.height > 0) return el;
                                }
                            }
                            
                            var all = document.getElementsByTagName('*');
                            for (var i = 0; i < all.length; i++) {
                                var el = all[i];
                                var r = el.getBoundingClientRect();
                                if (r.width > 0 && r.height > 0) {
                                    var aria = (el.getAttribute('aria-label') || '').toLowerCase();
                                    if (aria.indexOf('написа') !== -1 || aria.indexOf('створи') !== -1 || aria.indexOf('compose') !== -1 || aria.indexOf('write') !== -1) {
                                        return el;
                                    }
                                    var cls = el.className;
                                    if (typeof cls === 'string') {
                                        cls = cls.toLowerCase();
                                        if ((cls.indexOf('write') !== -1 || cls.indexOf('compose') !== -1 || cls.indexOf('sendmsg') !== -1) && (cls.indexOf('btn') !== -1 || cls.indexOf('icon') !== -1)) {
                                            return el;
                                        }
                                    }
                                }
                            }
                            return null;
                        }

                        function findElement(selectors) {
                            for (var i = 0; i < selectors.length; i++) {
                                var el = document.querySelector(selectors[i]);
                                if (el) {
                                    var r = el.getBoundingClientRect();
                                    if (r.width > 0 && r.height > 0) return el;
                                }
                            }
                            return null;
                        }

                        function getCoords(element) {
                            if (!element) return null;
                            var r = element.getBoundingClientRect();
                            return {
                                x: Math.round(r.left + r.width / 2),
                                y: Math.round(r.top + r.height / 2),
                                w: Math.round(r.width),
                                h: Math.round(r.height)
                            };
                        }

                        var selectors = {
                            to: ['.sm-auto-complete__input', '#to', 'input#to', 'textarea#to', 'input[name="to"]', '[placeholder*="Кому"]', '[placeholder*="To"]', '.sendmsg__to input'],
                            subject: ['#sendmsg__subject', '.sendmsg__subject', '#subject', 'input#subject', 'input[name="subject"]', '[placeholder*="Тема"]', '[placeholder*="Subject"]', '.sendmsg__subject input'],
                            body: ['.sm-editor__area', '#body', 'textarea#body', 'textarea[name="body"]', 'div[contenteditable="true"]', '.sendmsg__text', '.editor', '.sendmsg__body'],
                            send: ['.sm-header__send', '#send', 'button#send', '.sendmsg__send', '[aria-label="Відправити"]', '[aria-label="Отправить"]', '.btn-send']
                        };

                        var result = {
                            compose: getCoords(findCompose()),
                            to: getCoords(findElement(selectors.to)),
                            subject: getCoords(findElement(selectors.subject)),
                            body: getCoords(findElement(selectors.body)),
                            send: getCoords(findElement(selectors.send))
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
                                                        log("[UkrNet JS API] Успешный вход! Поднимаем ExteraGram поверх Укрнета.")
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
                                                    log("[Scanner API] Обнаружены элементы: $json")
                                                    
                                                    try {
                                                        val obj = JSONObject(json)
                                                        
                                                        val compose = obj.optJSONObject("compose")
                                                        if (compose != null) {
                                                            composeX = compose.optDouble("x", -1.0).toFloat()
                                                            composeY = compose.optDouble("y", -1.0).toFloat()
                                                        } else {
                                                            composeX = null
                                                            composeY = null
                                                        }

                                                        val to = obj.optJSONObject("to")
                                                        if (to != null) {
                                                            toX = to.optDouble("x", -1.0).toFloat()
                                                            toY = to.optDouble("y", -1.0).toFloat()
                                                        } else {
                                                            toX = null
                                                            toY = null
                                                        }

                                                        val subject = obj.optJSONObject("subject")
                                                        if (subject != null) {
                                                            subjectX = subject.optDouble("x", -1.0).toFloat()
                                                            subjectY = subject.optDouble("y", -1.0).toFloat()
                                                        } else {
                                                            subjectX = null
                                                            subjectY = null
                                                        }

                                                        val body = obj.optJSONObject("body")
                                                        if (body != null) {
                                                            bodyX = body.optDouble("x", -1.0).toFloat()
                                                            bodyY = body.optDouble("y", -1.0).toFloat()
                                                        } else {
                                                            bodyX = null
                                                            bodyY = null
                                                        }

                                                        val send = obj.optJSONObject("send")
                                                        if (send != null) {
                                                            sendX = send.optDouble("x", -1.0).toFloat()
                                                            sendY = send.optDouble("y", -1.0).toFloat()
                                                        } else {
                                                            sendX = null
                                                            sendY = null
                                                        }
                                                    } catch (e: Exception) {
                                                        log("[Scanner Error] Ошибка парсинга JSON: ${e.message}")
                                                    }
                                                } else {
                                                    nullLogCounter++
                                                    if (nullLogCounter >= 5) {
                                                        nullLogCounter = 0
                                                        log("[Scanner API] Поиск... (пока пусто, ждем появления элементов)")
                                                    }
                                                }
                                            }
                                        }
                                    }, "Android")

                                    webViewClient = object : WebViewClient() {
                                        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                            super.doUpdateVisitedHistory(view, url, isReload)
                                            log("[UkrNet] Смена URL: $url")
                                            
                                            if (url != null && url.contains("mail.ukr.net") && !url.contains("login") && !url.contains("accounts.ukr.net")) {
                                                if (isBgServiceActive && !hasHandledLogin) {
                                                    hasHandledLogin = true
                                                    log("[System] Успешный вход по URL: $url. Переключаем интерфейс.")
                                                    loginStatusMsg = "Авторизация пройдена!"
                                                    isBgServiceActive = false
                                                    messengerWebView?.evaluateJavascript("if (window.onLoginSuccess) { window.onLoginSuccess(); }", null)
                                                }
                                            }
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            log("[UkrNet] Загрузка завершена: $url")
                                        }

                                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                            super.onReceivedError(view, request, error)
                                            val url = request?.url.toString()
                                            if (!url.contains("tracker") && 
                                                !url.contains("ad") && 
                                                !url.contains("fwdcdn") && 
                                                !url.contains("criteo") && 
                                                !url.contains("bidmatic") && 
                                                !url.contains("mgaru") && 
                                                !url.contains("doubleclick") && 
                                                !url.contains("crwdcntrl") &&
                                                !url.contains("google") &&
                                                !url.contains("facebook")
                                            ) {
                                                log("[UkrNet ERROR] ${error?.description} (Код: ${error?.errorCode}) URL: $url")
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
                                            if (!msg.contains("Unknown event type") && !msg.contains("Criteo")) {
                                                log("[UkrNet JS Console] [$level] $msg")
                                            }
                                            return true
                                        }
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

                                        // ЭТАП 3 ТЕСТ: Кнопка нативного клика по "Написать"
                                        if (composeX != null && composeY != null) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                                    .height(30.dp)
                                            ) {
                                                Text(
                                                    text = "Кнопка 'Написать' найдена",
                                                    color = Color(0xFF81C784),
                                                    fontSize = 11.sp
                                                )
                                                TextButton(
                                                    onClick = {
                                                        simulateTouch(ukrnetWebView, composeX!!, composeY!!)
                                                    },
                                                    modifier = Modifier.background(Color(0x334CAF50), shape = RoundedCornerShape(4.dp))
                                                ) {
                                                    Text(
                                                        text = "🎯 Тап 'Написать'",
                                                        color = Color(0xFF81C784),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }

                                        // ЭТАП 4 ТЕСТ: Кнопка автоматического заполнения полей ввода
                                        if (toX != null && subjectX != null && bodyX != null) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                                    .height(30.dp)
                                            ) {
                                                Text(
                                                    text = "Поля ввода обнаружены",
                                                    color = Color(0xFF64B5F6),
                                                    fontSize = 11.sp
                                                )
                                                TextButton(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            log("[Autofill] Запуск цепочки ввода...")
                                                            
                                                            // 1. Поле "Кому"
                                                            simulateTouch(ukrnetWebView, toX!!, toY!!)
                                                            delay(600)
                                                            simulateType(ukrnetWebView, "270232@ukr.net")
                                                            delay(1200)
                                                            
                                                            // 2. Поле "Тема"
                                                            simulateTouch(ukrnetWebView, subjectX!!, subjectY!!)
                                                            delay(600)
                                                            simulateType(ukrnetWebView, "[nan0gram_v1] Тест автокликера")
                                                            delay(1200)
                                                            
                                                            // 3. Поле "Тело сообщения"
                                                            simulateTouch(ukrnetWebView, bodyX!!, bodyY!!)
                                                            delay(600)
                                                            simulateType(ukrnetWebView, "Привет! Это сообщение напечатано нативным методом ввода.")
                                                        }
                                                    },
                                                    modifier = Modifier.background(Color(0x332196F3), shape = RoundedCornerShape(4.dp))
                                                ) {
                                                    Text(
                                                        text = "✍️ Начать автозаполнение",
                                                        color = Color(0xFF64B5F6),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }

                                        // ЭТАП 5 ТЕСТ: Кнопка нативной отправки письма
                                        if (sendX != null && sendY != null) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                                    .height(30.dp)
                                            ) {
                                                Text(
                                                    text = "Кнопка 'Отправить' найдена",
                                                    color = Color(0xFFE57373),
                                                    fontSize = 11.sp
                                                )
                                                TextButton(
                                                    onClick = {
                                                        simulateTouch(ukrnetWebView, sendX!!, sendY!!)
                                                    },
                                                    modifier = Modifier.background(Color(0x33F44336), shape = RoundedCornerShape(4.dp))
                                                ) {
                                                    Text(
                                                        text = "🚀 Нажать 'Отправить'",
                                                        color = Color(0xFFE57373),
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
