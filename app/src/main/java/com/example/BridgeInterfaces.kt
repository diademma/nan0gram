package com.example

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class UkrnetJsInterface(
    private val log: (String) -> Unit,
    private val onLoginSuccess: () -> Unit,
    private val onCoordsUpdate: (DomCoords) -> Unit,
    private val onFirstCoordsLogged: () -> Unit,
    private val getMessengerWebView: () -> WebView?,
    private val isLoginHandled: () -> Boolean,
    private val getCurrentCoords: () -> DomCoords
) {
    private val ui = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun postMessage(type: String, key: String, value: String) {
        ui.post {
            if (type == "ui" && key == "login_success" && value == "true" && !isLoginHandled()) {
                log("[UkrNet] Вход выполнен — переключаем на мессенджер")
                onLoginSuccess()
                getMessengerWebView()?.evaluateJavascript("window.dispatchEvent(new CustomEvent('nan0gram:login-success'));", null)
            }
        }
    }

    @JavascriptInterface
    fun onComposeReady() {
        ui.post {
            log("[Bridge] openCompose готов ✓ (focus-patch снят)")
            getMessengerWebView()?.requestFocus() // <-- ВОЗВРАЩАЕМ ФОКУС ВЕРХНЕМУ ОКНУ
            getMessengerWebView()?.evaluateJavascript("window.dispatchEvent(new CustomEvent('nan0gram:compose-ready'));", null)
        }
    }

    @JavascriptInterface
    fun postCoordinates(json: String) {
        ui.post {
            try {
                val obj = JSONObject(json)
                fun f(key: String, sub: String) = obj.optJSONObject(key)?.optDouble(sub)?.toFloat()?.takeIf { it > 0 }
                val coords = DomCoords(
                    composeX = f("compose","x"), composeY = f("compose","y"),
                    toX      = f("to","x"),      toY      = f("to","y"),
                    subjectX = f("subject","x"), subjectY = f("subject","y"),
                    bodyX    = f("body","x"),    bodyY    = f("body","y"),
                    sendX    = f("send","x"),    sendY    = f("send","y")
                )
                val prev = getCurrentCoords()
                onCoordsUpdate(coords)
                if (coords.composeX != null && prev.composeX == null) {
                    onFirstCoordsLogged()
                    log("[Scanner] Координаты получены ✓ compose=(${coords.composeX.toInt()},${coords.composeY?.toInt()})")
                }
            } catch (e: Exception) { log("[Scanner] Ошибка: ${e.message}") }
        }
    }

    @JavascriptInterface
    fun onIncomingMessage(id: String, subject: String, body: String) {
        ui.post {
            val payload = body.trim()
            if (payload.isNotEmpty()) {
                val msg = JSONObject().apply {
                    put("chatId", Regex("\\[nan0gram\\]\\s*(.+)").find(subject)?.groupValues?.get(1)?.trim() ?: "inbox")
                    put("author",  "Собеседник")
                    put("text",    payload)
                    put("ts",      System.currentTimeMillis())
                    put("subject", subject)
                    put("msgId",   id)
                }
                val escaped = JSONArray().apply { put(msg) }.toString()
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                
                getMessengerWebView()?.evaluateJavascript(
                    "window.dispatchEvent(new CustomEvent('nan0gram:email-received', { detail: \"$escaped\" }));", null
                )
            }
        }
    }

    fun onUrlChange(url: String) {
        if (url.contains("mail.ukr.net") && !url.contains("login") && !url.contains("accounts") && !isLoginHandled()) {
            ui.post {
                onLoginSuccess()
                getMessengerWebView()?.evaluateJavascript("window.dispatchEvent(new CustomEvent('nan0gram:login-success'));", null)
            }
        }
    }
}

class MessengerJsInterface(
    private val log: (String) -> Unit,
    private val onBgServiceChange: (Boolean) -> Unit,
    private val getUkrnetWebView: () -> WebView?,
    private val getCoords: () -> DomCoords,
    private val androidId: String  // Принимаем ID устройства
) {
    lateinit var scope: CoroutineScope
    private val ui = Handler(Looper.getMainLooper())

    // Открываем метод для чтения уникального ID из JS
    @JavascriptInterface
    fun getDeviceId(): String {
        return androidId
    }

    @JavascriptInterface
    fun openCompose(configJson: String) {
        ui.post {
            scope.launch {
                val c = getCoords()
                if (c.composeX == null || c.composeY == null) return@launch
                val cfg     = try { JSONObject(configJson) } catch (e: Exception) { JSONObject() }
                val to      = cfg.optString("to", "270232@ukr.net").replace("'", "\\'")
                val subject = cfg.optString("subject", "💬 [nan0gram] chat").replace("'", "\\'")

                getUkrnetWebView()?.evaluateJavascript(FOCUS_PATCH_JS, null)
                delay(60)
                simulateTouch(getUkrnetWebView(), c.composeX, c.composeY, log = log)
                val fillJs = COMPOSE_FILL_JS.replace("%TO%", to).replace("%SUBJECT%", subject)
                getUkrnetWebView()?.evaluateJavascript(fillJs, null)
            }
        }
    }

    @JavascriptInterface
    fun setComposeBody(encodedText: String) {
        ui.post {
            val esc = encodedText.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
            val js = """
                (function(text) {
                    var el = document.querySelector('.sm-editor__area');
                    if (!el) return;
                    if (el.getAttribute('contenteditable') === 'true') {
                        el.textContent = text;
                    } else {
                        try {
                            var ns = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value').set;
                            ns.call(el, text);
                        } catch(e) { el.value = text; }
                    }
                    el.dispatchEvent(new Event('input',{bubbles:true}));
                    el.dispatchEvent(new Event('change',{bubbles:true}));
                })('$esc');
            """.trimIndent()
            getUkrnetWebView()?.evaluateJavascript(js, null)
        }
    }

    @JavascriptInterface
    fun submitCompose() {
        ui.post {
            val js = """
                (function(){
                    var btn = document.querySelector('.sm-header__send') 
                           || document.querySelector('[data-id="send"]') 
                           || document.querySelector('[aria-label="Відправити"]') 
                           || document.querySelector('[aria-label="Отправить"]')
                           || document.querySelector('.sendmsg__bottom-controls .button')
                           || document.querySelector('.header__action-btn--send')
                           || document.querySelector('button.send');
                    if (btn) { btn.click(); return 'sent_selector'; }
                    
                    var buttons = document.querySelectorAll('button');
                    for (var i = 0; i < buttons.length; i++) {
                        var txt = buttons[i].innerText.toLowerCase();
                        if (txt.indexOf('отправить') !== -1 || txt.indexOf('відправити') !== -1) {
                            buttons[i].click(); return 'sent_text';
                        }
                    }
                    var svgs = document.querySelectorAll('svg');
                    for (var j = 0; j < svgs.length; j++) {
                        var parent = svgs[j].closest('button');
                        if (parent && parent.className && parent.className.indexOf('send') !== -1) {
                            parent.click(); return 'sent_svg';
                        }
                    }
                    return 'no_btn';
                })();
            """.trimIndent()
            getUkrnetWebView()?.evaluateJavascript(js) { r ->
                log("[Bridge OUT] submitCompose → ${r?.trim('"')}")
            }
        }
    }

    @JavascriptInterface
    fun cancelCompose() {
        ui.post {
            scope.launch {
                val js = "(function(){ var btn = document.querySelector('.sm-header__cancel') || document.querySelector('[aria-label=\"Відмінити\"]') || document.querySelector('[aria-label=\"Отменить\"]); if (btn) { btn.click(); return 'ok'; } history.back(); return 'fallback'; })();"
                getUkrnetWebView()?.evaluateJavascript(js, null)
            }
        }
    }

    @JavascriptInterface
    fun setBgServiceActive(active: Boolean) { ui.post { onBgServiceChange(active) } }

    @JavascriptInterface
    fun postMessage(type: String, key: String, value: String) {}
}
