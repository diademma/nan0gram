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
                val escaped = JSONArray().apply { put(msg) }.toString().replace("\\", "\\\\").replace("\"", "\\\"")
                getMessengerWebView()?.evaluateJavascript("window.dispatchEvent(new CustomEvent('nan0gram:email-received', { detail: \"$escaped\" }));", null)
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
    fun notifyMediaSelection(sysBlock: String) {
        StealthCache.pendingSysBlock = sysBlock
        log("[Stealth] Выбираем файл...")
    }

    // Убийца окон с гарантированным созданием чипа почты
    private val POPUP_CRUSHER_JS = """
        function ensureSent() {
            var btn = document.querySelector('.sm-header__send') || document.querySelector('[data-id="send"]') || document.querySelector('[aria-label="Відправити"]') || document.querySelector('[aria-label="Отправить"]');
            if (btn) btn.click();
            
            var checkCount = 0;
            var errIntv = setInterval(function() {
                checkCount++;
                if (checkCount > 10) { clearInterval(errIntv); return; }
                
                var popups = document.querySelectorAll('.popup, .modal, .dialog');
                for(var j=0; j<popups.length; j++) {
                    var text = popups[j].innerText.toLowerCase();
                    if (text.indexOf('получателя') !== -1 || text.indexOf('одержувача') !== -1) {
                        clearInterval(errIntv); // ОСТАНАВЛИВАЕМ ЦИКЛ!
                        var okBtn = popups[j].querySelector('button.default, button.button');
                        if (okBtn) okBtn.click();
                        
                        var toEl = document.querySelector('.sm-auto-complete__input');
                        if (toEl) {
                            try { Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(toEl, '270232@ukr.net'); } catch(e) { toEl.value = '270232@ukr.net'; }
                            toEl.dispatchEvent(new Event('input',{bubbles:true}));
                            toEl.dispatchEvent(new KeyboardEvent('keydown',{bubbles:true,cancelable:true,key:'Enter',keyCode:13}));
                        }
                        // Ждем 800мс чтобы УкрНет создал чип, и кликаем
                        setTimeout(function(){ if(btn) btn.click(); }, 800);
                        return;
                    }
                }
            }, 300);
        }
    """.trimIndent()

    // Наблюдатель за загрузкой файлов на основе ваших точных DOM-селекторов
    private val UPLOAD_OBSERVER_JS = """
        var checkInterval = setInterval(function() {
            // Ищем элементы в процессе загрузки (шкала или блок upload)
            var uploading = document.querySelectorAll('.sm-attachments__upload, .sm-attachments__progress-bar');
            
            // Ищем готовые вложения (ссылка на сервер или превью)
            var uploaded = document.querySelectorAll('a[href*="/attach/get/"], .attachment-preview, .sm-attachments__attach-preview');
            
            // Если есть хоть один элемент загрузки - ждем
            if (uploading.length > 0) {
                return;
            }
            
            // Если загрузок нет, но появился готовый файл - жмем Отправить!
            if (uploaded.length > 0) {
                clearInterval(checkInterval);
                setTimeout(ensureSent, 600); // 600мс задержка на анимацию
            }
        }, 500);
    """.trimIndent()

    fun startMediaUploadSequence() {
        ui.post {
            scope.launch {
                val sysBlock = StealthCache.pendingSysBlock ?: return@launch
                StealthCache.pendingSysBlock = null
                
                val c = getCoords()
                if (c.composeX == null || c.composeY == null) return@launch
                
                getUkrnetWebView()?.evaluateJavascript(FOCUS_PATCH_JS, null)
                delay(60)
                simulateTouch(getUkrnetWebView(), c.composeX, c.composeY, log = log)
                delay(400)
                
                val subject = "Re[${(2..30).random()}]:"
                val escSys = sysBlock.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
                
                val js = """
                    (function(){
                        var toEl = document.querySelector('.sm-auto-complete__input');
                        if(toEl) {
                            try { Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(toEl, '270232@ukr.net'); } catch(e) { toEl.value = '270232@ukr.net'; }
                            toEl.dispatchEvent(new Event('input',{bubbles:true}));
                            toEl.dispatchEvent(new KeyboardEvent('keydown',{bubbles:true,key:'Enter',keyCode:13}));
                            toEl.dispatchEvent(new KeyboardEvent('keyup',{bubbles:true,key:'Enter',keyCode:13}));
                        }
                        var subjEl = document.querySelector('#sendmsg__subject');
                        if(subjEl) {
                            try { Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(subjEl, '$subject'); } catch(e) { subjEl.value = '$subject'; }
                            subjEl.dispatchEvent(new Event('input',{bubbles:true}));
                        }
                        var bodyEl = document.querySelector('.sm-editor__area');
                        if(bodyEl) {
                            if (bodyEl.getAttribute('contenteditable') === 'true') { bodyEl.innerHTML = ''; bodyEl.innerText = '$escSys'; } 
                            else { try { Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value').set.call(bodyEl, '$escSys'); } catch(e) { bodyEl.value = '$escSys'; } }
                            bodyEl.dispatchEvent(new Event('input',{bubbles:true}));
                        }
                        
                        setTimeout(function() {
                            var fileInput = document.querySelector('input[type="file"][multiple]') || document.querySelector('input[type="file"]');
                            if (fileInput) fileInput.click();
                        }, 300);
                        
                        $POPUP_CRUSHER_JS
                        $UPLOAD_OBSERVER_JS
                    })();
                """.trimIndent()
                getUkrnetWebView()?.evaluateJavascript(js, null)
            }
        }
    }

    @JavascriptInterface
    fun openCompose(configJson: String) {
        ui.post {
            scope.launch {
                val c = getCoords()
                if (c.composeX == null || c.composeY == null) return@launch
                getUkrnetWebView()?.evaluateJavascript(FOCUS_PATCH_JS, null)
                delay(60)
                simulateTouch(getUkrnetWebView(), c.composeX, c.composeY, log = log)
                val to = "270232@ukr.net"
                val subject = "💬 [nan0gram] chat"
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
                    if (el.getAttribute('contenteditable') === 'true') { el.innerHTML = ''; el.innerText = text; } 
                    else { try { Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value').set.call(el, text); } catch(e) { el.value = text; } }
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
            val js = """(function(){ $POPUP_CRUSHER_JS; ensureSent(); })();"""
            getUkrnetWebView()?.evaluateJavascript(js, null)
        }
    }

    @JavascriptInterface
    fun cancelCompose() {
        ui.post {
            scope.launch {
                getUkrnetWebView()?.evaluateJavascript("(function(){ var btn = document.querySelector('.sm-header__cancel') || document.querySelector('[aria-label=\"Відмінити\"]') || document.querySelector('[aria-label=\"Отменить\"]); if (btn) { btn.click(); return 'ok'; } history.back(); return 'fallback'; })();", null)
            }
        }
    }

    @JavascriptInterface
    fun setBgServiceActive(active: Boolean) { ui.post { onBgServiceChange(active) } }

    @JavascriptInterface
    fun postMessage(type: String, key: String, value: String) {}
}
