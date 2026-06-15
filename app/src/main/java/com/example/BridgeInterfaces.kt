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

// ═══════════════════════════════════════════════════════════════════════════
// UkrnetJsInterface  — события из ukrnetWebView в Kotlin
// Передает события в React-мессенджер через механизм CustomEvent
// ═══════════════════════════════════════════════════════════════════════════

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
                getMessengerWebView()?.evaluateJavascript(
                    "window.dispatchEvent(new CustomEvent('nan0gram:login-success'));", null
                )
            }
        }
    }

    @JavascriptInterface
    fun onComposeReady() {
        ui.post {
            log("[Bridge] openCompose готов ✓ (focus-patch снят)")
            getMessengerWebView()?.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('nan0gram:compose-ready'));", null
            )
        }
    }

    @JavascriptInterface
    fun postCoordinates(json: String) {
        ui.post {
            try {
                val obj = JSONObject(json)
                fun f(key: String, sub: String) =
                    obj.optJSONObject(key)?.optDouble(sub)?.toFloat()?.takeIf { it > 0 }
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
                    log("[Scanner] Координаты получены ✓ compose=(${coords.composeX.toInt()},${coords.composeY?.toInt()}) send=(${coords.sendX?.toInt()},${coords.sendY?.toInt()})")
                }
            } catch (e: Exception) { log("[Scanner] Ошибка: ${e.message}") }
        }
    }

    @JavascriptInterface
    fun onIncomingMessage(id: String, subject: String, body: String) {
        ui.post {
            log("[Parser] Входящее письмо: $id")
            val ruMatch  = Regex("===НАНО===\\s*([\\s\\S]+?)\\s*===НАНО===").find(body)
            val aesMatch = Regex("===\\[([\\s\\S]+?)\\]===").find(body)
            val payload  = ruMatch?.groupValues?.get(1)?.trim()
                        ?: aesMatch?.groupValues?.get(1)?.trim()
            if (payload != null) {
                log("[Parser] Payload извлечен, отправляем в мессенджер")
                val msg = JSONObject().apply {
                    put("chatId", Regex("\\[nan0gram\\]\\s*(.+)")
                        .find(subject)?.groupValues?.get(1)?.trim() ?: "inbox")
                    put("author",  "Собеседник")
                    put("text",    payload)
                    put("ts",      System.currentTimeMillis())
                    put("subject", subject)
                    put("msgId",   id)
                }
                val escaped = JSONArray().apply { put(msg) }.toString().replace("\"", "\\\"")
                
                getMessengerWebView()?.evaluateJavascript(
                    "window.dispatchEvent(new CustomEvent('nan0gram:email-received', { detail: \"$escaped\" }));", null
                )
            } else {
                log("[Parser] Скрытые маркеры в письме не найдены — пропуск")
            }
        }
    }

    fun onUrlChange(url: String) {
        if (url.contains("mail.ukr.net")
            && !url.contains("login")
            && !url.contains("accounts")
            && !isLoginHandled()
        ) {
            ui.post {
                log("[UkrNet] Вход обнаружен по изменению URL — переключаем")
                onLoginSuccess()
                getMessengerWebView()?.evaluateJavascript(
                    "window.dispatchEvent(new CustomEvent('nan0gram:login-success'));", null
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// MessengerJsInterface  — команды из messengerWebView (React) в Kotlin
// ═══════════════════════════════════════════════════════════════════════════

class MessengerJsInterface(
    private val log: (String) -> Unit,
    private val onBgServiceChange: (Boolean) -> Unit,
    private val getUkrnetWebView: () -> WebView?,
    private val getCoords: () -> DomCoords
) {
    lateinit var scope: CoroutineScope
    private val ui = Handler(Looper.getMainLooper())

    // Открыть compose (фокус-патч предотвращает скрытие клавиатуры мессенджера)
    @JavascriptInterface
    fun openCompose(configJson: String) {
        ui.post {
            log("[Bridge] openCompose...")
            scope.launch {
                val c = getCoords()
                if (c.composeX == null || c.composeY == null) {
                    log("[Bridge] openCompose: composeX/Y недоступны"); return@launch
                }
                val cfg     = try { JSONObject(configJson) } catch (e: Exception) { JSONObject() }
                val to      = cfg.optString("to",      "270232@ukr.net").replace("'", "\\'")
                val subject = cfg.optString("subject", "💬 [nan0gram] chat").replace("'", "\\'")

                getUkrnetWebView()?.evaluateJavascript(FOCUS_PATCH_JS, null)
                delay(60)
                simulateTouch(getUkrnetWebView(), c.composeX, c.composeY, log = log)
                val fillJs = COMPOSE_FILL_JS
                    .replace("%TO%",      to)
                    .replace("%SUBJECT%", subject)
                getUkrnetWebView()?.evaluateJavascript(fillJs, null)
            }
        }
    }

    // Обновление тела письма (выполняется плавно, в фоновом режиме)
    @JavascriptInterface
    fun setComposeBody(encodedText: String) {
        ui.post {
            val esc = encodedText
                .replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
            val bodyText = "Добрый день! Направляю данные.\\n\\n===НАНО===\\n${esc}\\n===НАНО===\\n\\nС уважением."
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
                })('$bodyText');
            """.trimIndent()
            getUkrnetWebView()?.evaluateJavascript(js, null)
        }
    }

    // Отправка письма (нажатие на нативную кнопку "Отправить" в UkrNet)
    @JavascriptInterface
    fun submitCompose() {
        ui.post {
            val js = """
                (function(){
                    var btn = document.querySelector('.sm-header__send')
                           || document.querySelector('[data-id="send"]')
                           || document.querySelector('[aria-label="Відправити"]')
                           || document.querySelector('[aria-label="Отправить"]');
                    if (btn) { btn.click(); return 'sent'; }
                    return 'no_btn';
                })();
            """.trimIndent()
            getUkrnetWebView()?.evaluateJavascript(js) { r ->
                log("[Bridge OUT] submitCompose → ${r?.trim('"')}")
            }
        }
    }

    // Отмена черновика
    @JavascriptInterface
    fun cancelCompose() {
        ui.post {
            scope.launch {
                val js = """
                    (function(){
                        var btn = document.querySelector('.sm-header__cancel')
                               || document.querySelector('[aria-label="Відмінити"]')
                               || document.querySelector('[aria-label="Отменить"]');
                        if (btn) { btn.click(); return 'ok'; }
                        history.back(); return 'fallback';
                    })();
                """.trimIndent()
                getUkrnetWebView()?.evaluateJavascript(js, null)
                log("[Bridge] cancelCompose → inbox")
            }
        }
    }

    @JavascriptInterface
    fun setBgServiceActive(active: Boolean) {
        ui.post { onBgServiceChange(active) }
    }

    @JavascriptInterface
    fun postMessage(type: String, key: String, value: String) {}
}
