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

    private fun log(message: String) {
        runOnUiThread {
            val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            logList.add("[$timestamp] $message")
            if (logList.size > 300) logList.removeAt(0)
        }
    }

    // stealFocus=false — НЕ воровать фокус у messengerWebView (фикс мерцания клавиатуры)
    private fun simulateTouch(webView: WebView?, cssX: Float, cssY: Float, stealFocus: Boolean = false) {
        if (webView == null) return
        val downTime = SystemClock.uptimeMillis()
        val eventTime = SystemClock.uptimeMillis()
        val density = webView.resources.displayMetrics.density
        val physicalX = cssX * density
        val physicalY = cssY * density

        val downEvent = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_DOWN, physicalX, physicalY, 0)
        val upEvent   = MotionEvent.obtain(downTime, eventTime + 100, MotionEvent.ACTION_UP, physicalX, physicalY, 0)

        webView.post {
            if (stealFocus) {
                webView.requestFocus()
                webView.requestFocusFromTouch()
            }
            webView.dispatchTouchEvent(downEvent)
            webView.dispatchTouchEvent(upEvent)
            downEvent.recycle()
            upEvent.recycle()
            log("[Autoclicker] Клик: X=${physicalX.toInt()}px, Y=${physicalY.toInt()}px")
        }
    }

    private fun simulateType(webView: WebView?, selector: String, text: String, isAutocomplete: Boolean = false) {
        if (webView == null) return
        val escaped = text.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "")
        // КРИТИЧНО: НЕ вызываем el.focus() — это тригерит Android IME на ukrnet WebView
        // и вызывает мерцание клавиатуры в мессенджере.
        // Используем нативный setter через Object.getOwnPropertyDescriptor — работает с React/ukrnet.
        val js = """
            (function() {
                var el = document.querySelector('$selector');
                if (!el) return 'not_found';
                if (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA') {
                    try {
                        var nativeSetter = Object.getOwnPropertyDescriptor(
                            el.tagName === 'INPUT' ? HTMLInputElement.prototype : HTMLTextAreaElement.prototype, 'value'
                        ).set;
                        nativeSetter.call(el, '$escaped');
                    } catch(e) { el.value = '$escaped'; }
                    el.dispatchEvent(new Event('input',  { bubbles: true }));
                    el.dispatchEvent(new Event('change', { bubbles: true }));
                    if ($isAutocomplete) {
                        el.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true, cancelable: true, key: 'Enter', keyCode: 13 }));
                        el.dispatchEvent(new KeyboardEvent('keyup',   { bubbles: true, cancelable: true, key: 'Enter', keyCode: 13 }));
                    }
                    el.blur();
                } else if (el.getAttribute('contenteditable') === 'true') {
                    el.innerHTML = '$escaped';
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                    el.blur();
                }
                return 'success';
            })();
        """.trimIndent()
        webView.post { webView.evaluateJavascript(js, null) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log("[System] nan0gram v3 инициализация")
        enableEdgeToEdge()

        setContent {
            var isBgServiceActive by remember { mutableStateOf(true) }
            var hasHandledLogin  by remember { mutableStateOf(false) }

            var ukrnetWebView    by remember { mutableStateOf<WebView?>(null) }
            var messengerWebView by remember { mutableStateOf<WebView?>(null) }

            var isLogPanelExpanded by remember { mutableStateOf(false) }
            var uiAlpha            by remember { mutableStateOf(0.95f) }
            var isParserEnabled    by remember { mutableStateOf(false) }

            // Координаты элементов ukr.net (из DOM-сканера)
            var composeX by remember { mutableStateOf<Float?>(null) }
            var composeY by remember { mutableStateOf<Float?>(null) }
            var toX      by remember { mutableStateOf<Float?>(null) }
            var toY      by remember { mutableStateOf<Float?>(null) }
            var subjectX by remember { mutableStateOf<Float?>(null) }
            var subjectY by remember { mutableStateOf<Float?>(null) }
            var bodyX    by remember { mutableStateOf<Float?>(null) }
            var bodyY    by remember { mutableStateOf<Float?>(null) }
            var sendX    by remember { mutableStateOf<Float?>(null) }
            var sendY    by remember { mutableStateOf<Float?>(null) }
            var coordinatesLogged by remember { mutableStateOf(false) }

            val clipboardManager = LocalClipboardManager.current
            val lazyListState    = rememberLazyListState()
            val coroutineScope   = rememberCoroutineScope()

            LaunchedEffect(logList.size) {
                if (logList.isNotEmpty() && isLogPanelExpanded)
                    lazyListState.animateScrollToItem(logList.size - 1)
            }

            // Мониторинг авторизации ukr.net
            LaunchedEffect(isBgServiceActive, ukrnetWebView) {
                val monitoringJs = """
                    try {
                        if (!window.nan0gramBridgeInjected) {
                            window.nan0gramBridgeInjected = true;
                            setInterval(function() {
                                var isMailUrl = window.location.href.indexOf('mail.ukr.net') !== -1
                                    && window.location.href.indexOf('login') === -1
                                    && window.location.href.indexOf('accounts') === -1;
                                var el = document.querySelector('.app__content, .sendmsg, #msglist');
                                if (isMailUrl || el) {
                                    if (!window.nan0gramSuccessReported) {
                                        window.nan0gramSuccessReported = true;
                                        if (window.Android && window.Android.postMessage)
                                            window.Android.postMessage('ui', 'login_success', 'true');
                                    }
                                }
                            }, 1500);
                        }
                    } catch(e) {}
                """.trimIndent()
                while (isBgServiceActive && ukrnetWebView != null) {
                    delay(1500)
                    ukrnetWebView?.evaluateJavascript(monitoringJs, null)
                }
            }

            // DOM-сканер координат (запускается после логина)
            LaunchedEffect(isBgServiceActive, ukrnetWebView) {
                val scanningJs = """
                    (function() {
                        function getCoords(el) {
                            if (!el) return null;
                            var r = el.getBoundingClientRect();
                            if (r.width === 0 || r.height === 0) return null;
                            return { x: Math.round(r.left + r.width/2), y: Math.round(r.top + r.height/2) };
                        }
                        var result = {
                            compose: getCoords(document.querySelector('.ml-header__compose')),
                            to:      getCoords(document.querySelector('.sm-auto-complete__input')),
                            subject: getCoords(document.querySelector('#sendmsg__subject')),
                            body:    getCoords(document.querySelector('.sm-editor__area')),
                            send:    getCoords(document.querySelector('.sm-header__send'))
                        };
                        if (window.Android && window.Android.postCoordinates)
                            window.Android.postCoordinates(JSON.stringify(result));
                    })();
                """.trimIndent()
                while (!isBgServiceActive && ukrnetWebView != null) {
                    // Если compose уже найден — сканируем редко (только для обновления bodyX/sendX при открытии compose)
                    delay(if (composeX != null) 8000L else 1500L)
                    ukrnetWebView?.evaluateJavascript(scanningJs, null)
                }
            }

            // Ридер входящих (ЭТАП 6.2 — фильтр непрочитанных)
            LaunchedEffect(isBgServiceActive, ukrnetWebView, isParserEnabled) {
                val readerJs = """
                    (function() {
                        try {
                            if (!window.nProcessed) {
                                var saved = [];
                                try { saved = JSON.parse(localStorage.getItem('nan0gram_ids')) || []; } catch(e){}
                                window.nProcessed = new Set(saved);
                                window.nState = 'IDLE';
                                window.nTargetId = null;
                            }
                            function markProcessed(id) {
                                window.nProcessed.add(id);
                                try { localStorage.setItem('nan0gram_ids', JSON.stringify(Array.from(window.nProcessed))); } catch(e){}
                            }
                            function isMsgUnread(item) {
                                var view = item.querySelector('.mli-view');
                                if (view && (view.classList.contains('unread') || view.classList.contains('mli-view_unread'))) return true;
                                if (item.classList.contains('unread') || item.classList.contains('ml-item_unread')) return true;
                                var titleEl = item.querySelector('.mli-view__title');
                                if (titleEl) {
                                    var w = window.getComputedStyle(titleEl).fontWeight;
                                    if (w === 'bold' || parseInt(w) >= 600) return true;
                                }
                                return false;
                            }
                            if (window.location.href.indexOf('login') !== -1) return;
                            if (window.nState === 'IDLE') {
                                var items = document.querySelectorAll('.ml-item');
                                for (var i = items.length - 1; i >= 0; i--) {
                                    var item = items[i];
                                    var id = item.id;
                                    if (!id || window.nProcessed.has(id)) continue;
                                    var titleEl = item.querySelector('.mli-view__title');
                                    // Принимаем: письма с эмодзи в теме (💬 nan0gram маркер)
                                    var hasEmoji = titleEl && /\p{Emoji}/u.test(titleEl.innerText || '');
                                    if (hasEmoji && isMsgUnread(item)) {
                                        window.nState = 'READING';
                                        window.nTargetId = id;
                                        var link = item.querySelector('.mli-view__link') || item;
                                        link.click();
                                        return;
                                    } else {
                                        markProcessed(id);
                                    }
                                }
                            } else if (window.nState === 'READING') {
                                var bodyEl    = document.querySelector('.rm-body__content');
                                var subjectEl = document.querySelector('.readmsg__subject');
                                var backBtn   = document.querySelector('.rm-header__list');
                                if (bodyEl && backBtn) {
                                    var bodyText    = bodyEl.innerText || bodyEl.textContent || '';
                                    var subjectText = subjectEl ? (subjectEl.innerText || '') : '';
                                    if (window.Android && window.Android.onIncomingMessage)
                                        window.Android.onIncomingMessage(window.nTargetId, subjectText, bodyText);
                                    markProcessed(window.nTargetId);
                                    backBtn.click();
                                    window.nState = 'RETURNING';
                                }
                            } else if (window.nState === 'RETURNING') {
                                if (document.querySelector('.msglist')) {
                                    window.nState = 'IDLE';
                                    window.nTargetId = null;
                                }
                            }
                        } catch(e) { console.error('Reader:', e.message); }
                    })();
                """.trimIndent()
                while (!isBgServiceActive && ukrnetWebView != null && isParserEnabled) {
                    delay(1500)
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

                                // ── UKRNET WEBVIEW ────────────────────────────────────
                                val uWebView = WebView(context).apply {
                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        databaseEnabled   = true
                                        useWideViewPort   = true
                                        loadWithOverviewMode = true
                                        userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                                    }
                                    CookieManager.getInstance().setAcceptCookie(true)
                                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                                    addJavascriptInterface(object : Any() {
                                        @JavascriptInterface
                                        fun postMessage(type: String, key: String, value: String) {
                                            runOnUiThread {
                                                if (type == "ui" && key == "login_success" && value == "true" && !hasHandledLogin) {
                                                    hasHandledLogin  = true
                                                    isBgServiceActive = false
                                                    log("[UkrNet] Вход выполнен — переключаем на мессенджер")
                                                    messengerWebView?.evaluateJavascript("if(window.onLoginSuccess)window.onLoginSuccess();", null)
                                                }
                                            }
                                        }

                                        @JavascriptInterface
                                        fun postCoordinates(json: String) {
                                            runOnUiThread {
                                                try {
                                                    val obj = JSONObject(json)
                                                    fun coord(key: String) = obj.optJSONObject(key)
                                                    composeX = coord("compose")?.optDouble("x")?.toFloat()?.takeIf { it > 0 }
                                                    composeY = coord("compose")?.optDouble("y")?.toFloat()?.takeIf { it > 0 }
                                                    toX      = coord("to")?.optDouble("x")?.toFloat()?.takeIf { it > 0 }
                                                    toY      = coord("to")?.optDouble("y")?.toFloat()?.takeIf { it > 0 }
                                                    subjectX = coord("subject")?.optDouble("x")?.toFloat()?.takeIf { it > 0 }
                                                    subjectY = coord("subject")?.optDouble("y")?.toFloat()?.takeIf { it > 0 }
                                                    bodyX    = coord("body")?.optDouble("x")?.toFloat()?.takeIf { it > 0 }
                                                    bodyY    = coord("body")?.optDouble("y")?.toFloat()?.takeIf { it > 0 }
                                                    sendX    = coord("send")?.optDouble("x")?.toFloat()?.takeIf { it > 0 }
                                                    sendY    = coord("send")?.optDouble("y")?.toFloat()?.takeIf { it > 0 }
                                                    // Логируем только один раз при первом получении координат
                                                    if (composeX != null && !coordinatesLogged) {
                                                        coordinatesLogged = true
                                                        log("[Scanner] Координаты получены ✓ compose=(${composeX?.toInt()},${composeY?.toInt()}) send=(${sendX?.toInt()},${sendY?.toInt()})")
                                                    }
                                                } catch (e: Exception) { log("[Scanner] Ошибка: ${e.message}") }
                                            }
                                        }

                                        @JavascriptInterface
                                        fun onIncomingMessage(id: String, subject: String, body: String) {
                                            runOnUiThread {
                                                log("[Parser] Входящее: $id")
                                                // Ищем ===НАНО=== (RuCipher) или старый ===[ ]===
                                                val ruMatch  = Regex("===НАНО===\\s*([\\s\\S]+?)\\s*===НАНО===").find(body)
                                                val aesMatch = Regex("===\\[([\\s\\S]+?)\\]===").find(body)
                                                val payload  = ruMatch?.groupValues?.get(1)?.trim()
                                                            ?: aesMatch?.groupValues?.get(1)?.trim()
                                                if (payload != null) {
                                                    log("[Parser] Payload → мессенджер")
                                                    val jsonArray = JSONArray()
                                                    val msg = JSONObject()
                                                    // chatId из темы: 💬 [nan0gram] ChatName
                                                    val chatId = Regex("\\[nan0gram\\]\\s*(.+)").find(subject)?.groupValues?.get(1)?.trim() ?: "inbox"
                                                    msg.put("chatId", chatId)
                                                    msg.put("author", "Собеседник")
                                                    msg.put("text", payload)
                                                    msg.put("ts", System.currentTimeMillis())
                                                    msg.put("subject", subject)
                                                    msg.put("msgId", id)
                                                    jsonArray.put(msg)
                                                    val escaped = jsonArray.toString().replace("\"", "\\\"")
                                                    messengerWebView?.evaluateJavascript(
                                                        "if(window.ExteraGram&&window.ExteraGram.onEmailReceived)window.ExteraGram.onEmailReceived(\"$escaped\");", null
                                                    )
                                                } else {
                                                    log("[Parser] Маркеры не найдены — пропуск")
                                                }
                                            }
                                        }
                                    }, "Android")

                                    webViewClient = object : WebViewClient() {
                                        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                            super.doUpdateVisitedHistory(view, url, isReload)
                                            if (url != null && url.contains("mail.ukr.net") && !url.contains("login") && !hasHandledLogin) {
                                                hasHandledLogin  = true
                                                isBgServiceActive = false
                                                messengerWebView?.evaluateJavascript("if(window.onLoginSuccess)window.onLoginSuccess();", null)
                                            }
                                        }
                                        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) { handler?.cancel() }
                                    }
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onConsoleMessage(m: ConsoleMessage?) = true
                                    }
                                    loadUrl("https://mail.ukr.net/desktop/login")
                                }
                                ukrnetWebView = uWebView
                                addView(uWebView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

                                // ── MESSENGER WEBVIEW ─────────────────────────────────
                                val mWebView = WebView(context).apply {
                                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                    settings.apply {
                                        javaScriptEnabled  = true
                                        domStorageEnabled  = true
                                        databaseEnabled    = true
                                        allowFileAccess    = true
                                        allowContentAccess = true
                                    }

                                    addJavascriptInterface(object : Any() {

                                        @JavascriptInterface
                                        fun setBgServiceActive(active: Boolean) {
                                            runOnUiThread { isBgServiceActive = active }
                                        }

                                        // ── СТАРЫЙ метод: полный цикл (fallback) ──────
                                        // Теперь тоже по CSS-селекторам — не зависит от координат формы
                                        @JavascriptInterface
                                        fun sendEmail(payload: String) {
                                            runOnUiThread {
                                                log("[Bridge OUT] sendEmail (legacy)")
                                                coroutineScope.launch {
                                                    if (composeX == null || composeY == null) {
                                                        log("[Bridge OUT] sendEmail: composeX/Y недоступны"); return@launch
                                                    }
                                                    simulateTouch(ukrnetWebView, composeX!!, composeY!!, stealFocus = false)
                                                    delay(1400)
                                                    simulateType(ukrnetWebView, ".sm-auto-complete__input", "270232@ukr.net", isAutocomplete = true)
                                                    delay(900)
                                                    simulateType(ukrnetWebView, "#sendmsg__subject", "RE: Сверка остатков [Ref: #270232]")
                                                    delay(500)
                                                    val body = "Добрый день! Направляю данные.\\n\\n===НАНО===\\n${payload}\\n===НАНО===\\n\\nС уважением."
                                                    simulateType(ukrnetWebView, ".sm-editor__area", body)
                                                    delay(600)
                                                    // Клик «Отправить» по CSS-селектору
                                                    val sendJs = "(function(){ var b=document.querySelector('.sm-header__send')||document.querySelector('[aria-label=\"Відправити\"]')||document.querySelector('[aria-label=\"Отправить\"]'); if(b){b.click();return 'ok';}return 'no_btn'; })();"
                                                    ukrnetWebView?.evaluateJavascript(sendJs) { r -> log("[Bridge OUT] legacy → ${r?.trim('"')}") }
                                                }
                                            }
                                        }

                                        // ── НОВЫЙ метод: открыть черновик заранее ─────
                                        // Не ждём координаты формы — заполняем по CSS-селекторам сразу
                                        @JavascriptInterface
                                        fun openCompose(configJson: String) {
                                            runOnUiThread {
                                                log("[Bridge] openCompose...")
                                                coroutineScope.launch {
                                                    if (composeX == null || composeY == null) {
                                                        log("[Bridge] openCompose: composeX/Y недоступны"); return@launch
                                                    }
                                                    // 1. Кликаем кнопку «Написать» (не воруем фокус!)
                                                    simulateTouch(ukrnetWebView, composeX!!, composeY!!, stealFocus = false)
                                                    // 2. Ждём появления формы (фиксированная пауза, без опроса координат)
                                                    delay(1400)
                                                    try {
                                                        val cfg = JSONObject(configJson)
                                                        val to      = cfg.optString("to", "270232@ukr.net")
                                                        val subject = cfg.optString("subject", "💬 [nan0gram] chat")
                                                        // 3. Заполняем Кому по CSS-селектору
                                                        simulateType(ukrnetWebView, ".sm-auto-complete__input", to, isAutocomplete = true)
                                                        delay(850)
                                                        // 4. Заполняем Тему по CSS-селектору
                                                        simulateType(ukrnetWebView, "#sendmsg__subject", subject)
                                                        delay(300)
                                                        log("[Bridge] openCompose готов ✓")
                                                    } catch (e: Exception) {
                                                        log("[Bridge] openCompose error: ${e.message}")
                                                    }
                                                }
                                            }
                                        }

                                        // ── НОВЫЙ метод: обновить тело на лету ───────
                                        // Работает только по CSS-селектору — координаты не нужны
                                        @JavascriptInterface
                                        fun setComposeBody(encodedText: String) {
                                            runOnUiThread {
                                                val body = "Добрый день! Направляю данные.\\n\\n===НАНО===\\n${encodedText}\\n===НАНО===\\n\\nС уважением."
                                                simulateType(ukrnetWebView, ".sm-editor__area", body)
                                            }
                                        }

                                        // ── НОВЫЙ метод: нажать отправить ─────────────
                                        // Использует JS-клик по CSS-селектору — не нужны координаты sendX/Y
                                        @JavascriptInterface
                                        fun submitCompose() {
                                            runOnUiThread {
                                                val clickSendJs = """
                                                    (function(){
                                                        var btn = document.querySelector('.sm-header__send')
                                                               || document.querySelector('[data-id="send"]')
                                                               || document.querySelector('[aria-label="Відправити"]')
                                                               || document.querySelector('[aria-label="Отправить"]');
                                                        if (btn) { btn.click(); return 'sent'; }
                                                        return 'no_btn';
                                                    })();
                                                """.trimIndent()
                                                ukrnetWebView?.evaluateJavascript(clickSendJs) { result ->
                                                    log("[Bridge OUT] submitCompose → ${result?.trim('"')}")
                                                }
                                            }
                                        }

                                        // ── НОВЫЙ метод: отмена черновика → inbox ────
                                        @JavascriptInterface
                                        fun cancelCompose() {
                                            runOnUiThread {
                                                coroutineScope.launch {
                                                    val cancelJs = """
                                                        (function(){
                                                            var btn = document.querySelector('.sm-header__cancel')
                                                                   || document.querySelector('[aria-label="Відмінити"]')
                                                                   || document.querySelector('[aria-label="Отменить"]');
                                                            if (btn) { btn.click(); return 'ok'; }
                                                            // Fallback: назад по истории браузера
                                                            history.back();
                                                            return 'fallback';
                                                        })();
                                                    """.trimIndent()
                                                    ukrnetWebView?.evaluateJavascript(cancelJs, null)
                                                    log("[Bridge] cancelCompose → inbox")
                                                }
                                            }
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
                                ukrnetWebView?.visibility   = View.VISIBLE
                                ukrnetWebView?.bringToFront()
                            } else {
                                ukrnetWebView?.visibility   = View.VISIBLE
                                messengerWebView?.visibility = View.VISIBLE
                                messengerWebView?.alpha     = uiAlpha
                                messengerWebView?.bringToFront()
                            }
                        },
                        modifier = Modifier.fillMaxSize().zIndex(0f)
                    )

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
                                Text("🐞 Логи (${logList.size})", color = Color(0xFFD0BCFF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                                        Text("nan0gram логи", color = Color(0xFFA773D1), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            TextButton(onClick = { messengerWebView?.reload() }) { Text("🔄 Mess", color = Color(0xFFD0BCFF), fontSize = 10.sp) }
                                            TextButton(onClick = { ukrnetWebView?.reload()    }) { Text("🔄 Ukr",  color = Color(0xFFD0BCFF), fontSize = 10.sp) }
                                            TextButton(onClick = { clipboardManager.setText(AnnotatedString(logList.joinToString("\n"))) }) { Text("📋", color = Color(0xFFC2FFD9), fontSize = 10.sp) }
                                            TextButton(onClick = { logList.clear() })           { Text("❌", color = Color(0xFFEFB8C8), fontSize = 10.sp) }
                                            TextButton(onClick = { isLogPanelExpanded = false }) { Text("➖", color = Color(0xFFCCC2DC), fontSize = 10.sp) }
                                        }
                                    }

                                    if (!isBgServiceActive) {
                                        // Слайдер прозрачности
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(36.dp)
                                        ) {
                                            Text("Видимость: ${(uiAlpha * 100).toInt()}%", color = Color(0xFFE0C3FC), fontSize = 11.sp, modifier = Modifier.width(130.dp))
                                            Slider(value = uiAlpha, onValueChange = { uiAlpha = it }, valueRange = 0f..1f, modifier = Modifier.weight(1f))
                                        }

                                        // Радар входящих
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(30.dp)
                                        ) {
                                            Text("Радар входящих:", color = Color(0xFFE0C3FC), fontSize = 11.sp)
                                            TextButton(
                                                onClick = { isParserEnabled = !isParserEnabled },
                                                modifier = Modifier.background(
                                                    if (isParserEnabled) Color(0x334CAF50) else Color(0x33F44336),
                                                    shape = RoundedCornerShape(4.dp)
                                                )
                                            ) {
                                                Text(
                                                    text = if (isParserEnabled) "🎧 Радар: ВКЛ" else "🔇 Радар: ВЫКЛ",
                                                    color = if (isParserEnabled) Color(0xFF81C784) else Color(0xFFE57373),
                                                    fontSize = 10.sp, fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        // Статус координат и кнопка теста
                                        if (composeX != null) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp).height(30.dp)
                                            ) {
                                                Text("Кнопка 'Написать' найдена ✓", color = Color(0xFF81C784), fontSize = 11.sp)
                                                TextButton(
                                                    onClick = { simulateTouch(ukrnetWebView, composeX!!, composeY!!, stealFocus = false) },
                                                    modifier = Modifier.background(Color(0x334CAF50), shape = RoundedCornerShape(4.dp))
                                                ) { Text("🎯 Тап", color = Color(0xFF81C784), fontSize = 10.sp) }
                                            }
                                        }

                                        if (toX != null && sendX != null) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp).height(30.dp)
                                            ) {
                                                Text("Мост готов ✓", color = Color(0xFF64B5F6), fontSize = 11.sp)
                                                TextButton(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            simulateType(ukrnetWebView, ".sm-auto-complete__input", "270232@ukr.net", isAutocomplete = true)
                                                            delay(900)
                                                            simulateType(ukrnetWebView, "#sendmsg__subject", "💬 [nan0gram] тест")
                                                            delay(400)
                                                            simulateType(ukrnetWebView, ".sm-editor__area", "Тест nan0gram v3<br>===НАНО===<br>ТестНаноПэйлоад<br>===НАНО===")
                                                            delay(800)
                                                            simulateTouch(ukrnetWebView, sendX!!, sendY!!, stealFocus = false)
                                                        }
                                                    },
                                                    modifier = Modifier.background(Color(0x332196F3), shape = RoundedCornerShape(4.dp))
                                                ) { Text("🚀 Тест отправки", color = Color(0xFF64B5F6), fontSize = 10.sp) }
                                            }
                                        }
                                    }

                                    LazyColumn(
                                        state = lazyListState,
                                        modifier = Modifier.fillMaxWidth().weight(1f).background(Color(0xFF07040A)).padding(4.dp)
                                    ) {
                                        items(logList) { line ->
                                            Text(
                                                text  = line,
                                                color = if (line.contains("error", ignoreCase = true) || line.contains("ошибка", ignoreCase = true)) Color(0xFFFF8A8A) else Color(0xFFC2FFD9),
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
            }
        }
    }
}
