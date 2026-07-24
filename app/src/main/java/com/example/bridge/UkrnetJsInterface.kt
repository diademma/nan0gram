package com.example

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject

class UkrnetJsInterface(
    private val log: (String) -> Unit,
    private val onLoginSuccess: () -> Unit,
    private val onCoordsUpdate: (DomCoords) -> Unit,
    private val onFirstCoordsLogged: () -> Unit,
    private val getMessengerWebView: () -> WebView?,
    private val isLoginHandled: () -> Boolean,
    private val getCurrentCoords: () -> DomCoords,
    private val clearComposeBody: () -> Unit = {},
    private val onLoginRequired: () -> Unit = {},
    private val onSaveSetting: (String, String) -> Unit = { _, _ -> }
) {
    private val ui = Handler(Looper.getMainLooper())

    fun onLoginPageLoaded() {
        ui.post {
            onLoginRequired()
        }
    }

    @JavascriptInterface
    fun jsLog(msg: String) {
        log("[Ukrnet JS] $msg")
    }

    @JavascriptInterface
    fun postMessage(type: String, key: String, value: String) {
        ui.post {
            if (type == "ui" && key == "login_success" && value == "true" && !isLoginHandled()) {
                onLoginSuccess()
                getMessengerWebView()?.evaluateJavascript("window.dispatchEvent(new CustomEvent('nan0gram:login-success'));", null)
            }
        }
    }

    @JavascriptInterface
    fun onComposeReady() {
        ui.post {
            getMessengerWebView()?.requestFocus()
            getMessengerWebView()?.evaluateJavascript("window.dispatchEvent(new CustomEvent('nan0gram:compose-ready'));", null)
        }
    }

    @JavascriptInterface
    fun onMediaSent() {
        ui.post {
            clearComposeBody()
            getMessengerWebView()?.evaluateJavascript(
                "if(window.nan0gram) window.nan0gram._composeOpen = false; window.dispatchEvent(new CustomEvent('nan0gram:media-sent'));", null
            )
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
                if (coords.composeX != null && prev.composeX == null) onFirstCoordsLogged()
            } catch (e: Exception) {}
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
                    .replace("\\", "\\\\").replace("\"", "\\\"")
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