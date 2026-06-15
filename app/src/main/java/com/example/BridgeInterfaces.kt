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
// Использует механизм CustomEvent для безопасной передачи данных в React.
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
            val ruMatch  = Regex("===НАНО===\s*([\s\S]+?)\s*===НАНО===").find(body)
            val aesMatch = Regex("===\[([\s\S]+?)\]===").find(body)
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
