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
    fun notifyMediaSelection(sysBlock: String) {
        StealthCache.pendingSysBlock = sysBlock
        log("[Stealth] Файлы выбраны, метаданные сохранены.")
    }

    // Умный Убийца Окон: ждёт чип адресата перед отправкой
    private val POPUP_CRUSHER_JS = """
        function ensureSent() {
            var toEl = document.querySelector('.sm-auto-complete__input');
            var hasChip = document.querySelector('.sm-auto-complete__item, .sm-auto-complete__token');
            if (toEl && !hasChip) {
                try { Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(toEl, '270232@ukr.net'); } catch(e) { toEl.value = '270232@ukr.net'; }
                toEl.dispatchEvent(new Event('input',{bubbles:true}));
                toEl.dispatchEvent(new KeyboardEvent('keydown',{bubbles:true,key:'Enter',keyCode:13}));
                toEl.dispatchEvent(new KeyboardEvent('keyup',{bubbles:true,key:'Enter',keyCode:13}));
            }
            var waited = 0;
            var chipWait = setInterval(function() {
                waited++;
                var chip = document.querySelector('.sm-auto-complete__item, .sm-auto-complete__token');
                if (chip || waited > 30) {
                    clearInterval(chipWait);
                    var btn = document.querySelector('.sm-header__send') || document.querySelector('[data-id="send"]') || document.querySelector('[aria-label="Відправити"]') || document.querySelector('[aria-label="Отправить"]');
                    if (btn) btn.click();
                }
            }, 150);
            var checkCount = 0;
            var errIntv = setInterval(function() {
                checkCount++;
                if (checkCount > 20) { clearInterval(errIntv); return; }
                var popups = document.querySelectorAll('.popup, .modal, .dialog');
                for(var j=0; j<popups.length; j++) {
                    var text = popups[j].innerText.toLowerCase();
                    if (text.indexOf('получателя') !== -1 || text.indexOf('одержувача') !== -1) {
                        clearInterval(errIntv);
                        var okBtn = popups[j].querySelector('button.default, button.button');
                        if (okBtn) okBtn.click();
                        setTimeout(function() {
                            var toEl2 = document.querySelector('.sm-auto-complete__input');
                            var hasChip2 = document.querySelector('.sm-auto-complete__item, .sm-auto-complete__token');
                            if (toEl2 && !hasChip2) {
                                try { Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(toEl2, '270232@ukr.net'); } catch(e) { toEl2.value = '270232@ukr.net'; }
                                toEl2.dispatchEvent(new Event('input',{bubbles:true}));
                                toEl2.dispatchEvent(new KeyboardEvent('keydown',{bubbles:true,key:'Enter',keyCode:13}));
                                toEl2.dispatchEvent(new KeyboardEvent('keyup',{bubbles:true,key:'Enter',keyCode:13}));
                            }
                            var w2 = 0;
                            var wc2 = setInterval(function() {
                                w2++;
                                if (document.querySelector('.sm-auto-complete__item, .sm-auto-complete__token') || w2 > 20) {
                                    clearInterval(wc2);
                                    var b = document.querySelector('.sm-header__send') || document.querySelector('[aria-label="Відправити"]');
                                    if (b) b.click();
                                }
                            }, 150);
                        }, 300);
                        return;
                    }
                }
            }, 300);
        }
    """.trimIndent()

    // Наблюдатель загрузки: ждёт исчезновения progress-bar и появления /attach/get/ ссылки
    private val UPLOAD_OBSERVER_JS = """
        var uploadCheckCount = 0;
        var checkInterval = setInterval(function() {
            uploadCheckCount++;
            if (uploadCheckCount > 120) { clearInterval(checkInterval); return; }
            var stillUploading = document.querySelectorAll('.sm-attachments__progress-bar, .sm-attachments__upload-icon');
            var doneLinks = document.querySelectorAll('a[href*="/attach/get/"]');
            if (stillUploading.length > 0) { return; }
            if (doneLinks.length > 0) {
                clearInterval(checkInterval);
                setTimeout(ensureSent, 400);
            }
        }, 400);
    """.trimIndent()

    // Умный запуск загрузки медиа
    fun startMediaUploadSequence() {
        ui.post {
            // Проверяем, открыто ли уже окно создания письма в УкрНэте
            getUkrnetWebView()?.evaluateJavascript("(function(){ return document.querySelector('.sm-editor__area') !== null; })();") { value ->
                val isAlreadyOpen = value?.toBoolean() ?: false
                
                scope.launch {
                    val sysBlock = StealthCache.pendingSysBlock ?: return@launch
                    StealthCache.pendingSysBlock = null
                    
                    val c = getCoords()
                    if (c.composeX == null || c.composeY == null) return@launch
                    
                    // Кликаем по кнопке создания черновика только если он еще не открыт!
                    if (!isAlreadyOpen) {
                        log("[Stealth] Окно закрыто. Открываем новое...")
                        getUkrnetWebView()?.evaluateJavascript(FOCUS_PATCH_JS, null)
                        delay(60)
                        simulateTouch(getUkrnetWebView(), c.composeX, c.composeY, log = log)
                        delay(400)
                    } else {
                        log("[Stealth] Окно уже открыто. Используем текущую сессию...")
                    }
                    
                    // Всегда маскируем тему под Re[X]! Никаких статичных тем нанограм чат!
                    val subject = "Re[${(2..30).random()}]:"
                    val escSys = sysBlock.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
                    
                    val js = """
                        (function(){
                            // Очищаем форму от мусора старых черновиков
                            var bodyEl = document.querySelector('.sm-editor__area');
                            if(bodyEl) {
                                bodyEl.innerHTML = '';
                                if (bodyEl.getAttribute('contenteditable') === 'true') { bodyEl.innerText = '$escSys'; } 
                                else { try { Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value').set.call(bodyEl, '$escSys'); } catch(e) { bodyEl.value = '$escSys'; } }
                                bodyEl.dispatchEvent(new Event('input',{bubbles:true}));
                            }
                            
                            var subjEl = document.querySelector('#sendmsg__subject');
                            if(subjEl) {
                                try { Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(subjEl, '$subject'); } catch(e) { subjEl.value = '$subject'; }
                                subjEl.dispatchEvent(new Event('input',{bubbles:true}));
                            }
                            
                            // Динамическое ожидание появления инпута скрепки в DOM перед кликом
                            var findFileInput = setInterval(function() {
                                var fileInput = document.querySelector('input[type="file"][multiple]') || document.querySelector('input[type="file"]');
                                if (fileInput) {
                                    clearInterval(findFileInput);
                                    fileInput.click();
                                }
                            }, 100);
                            setTimeout(function() { clearInterval(findFileInput); }, 5000);
                            
                            $POPUP_CRUSHER_JS
                            $UPLOAD_OBSERVER_JS
                        })();
                    """.trimIndent()
                    getUkrnetWebView()?.evaluateJavascript(js, null)
                }
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
                // Всегда генерируем стелс-тему Re[X]: при вводе сообщений!
                val subject = "Re[${(2..30).random()}]:"
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
                    el.innerHTML = '';
                    if (el.getAttribute('contenteditable') === 'true') { el.innerText = text; } 
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
            val js = """
                (function(){
                    var btn = document.querySelector('.sm-header__send') || document.querySelector('[data-id="send"]') || document.querySelector('[aria-label="Відправити"]') || document.querySelector('[aria-label="Отправить"]');
                    var toEl = document.querySelector('.sm-auto-complete__input');
                    var hasChip = document.querySelector('.sm-auto-complete__item, .sm-auto-complete__token');
                    if (toEl && !hasChip) {
                        try { Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(toEl, '270232@ukr.net'); } catch(e) { toEl.value = '270232@ukr.net'; }
                        toEl.dispatchEvent(new Event('input',{bubbles:true}));
                        toEl.dispatchEvent(new KeyboardEvent('keydown',{bubbles:true,key:'Enter',keyCode:13}));
                        toEl.dispatchEvent(new KeyboardEvent('keyup',{bubbles:true,key:'Enter',keyCode:13}));
                    }
                    setTimeout(function() { if (btn) btn.click(); }, 150);
                })();
            """.trimIndent()
            getUkrnetWebView()?.evaluateJavascript(js, null)
        }
    }

    @JavascriptInterface
    fun cancelCompose() {
        ui.post {
            scope.launch {
                getUkrnetWebView()?.evaluateJavascript("(function(){ var btn = document.querySelector('.sm-header__cancel') || document.querySelector('[aria-label=\"Відмінити\"]') || document.querySelector('[aria-label=\"Отменить\"]'); if (btn) { btn.click(); return 'ok'; } history.back(); return 'fallback'; })();", null)
            }
        }
    }

    @JavascriptInterface
    fun setBgServiceActive(active: Boolean) { onBgServiceChange(active) }

    @JavascriptInterface
    fun postMessage(type: String, key: String, value: String) {}
}
