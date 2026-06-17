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
            getMessengerWebView()?.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('nan0gram:media-sent'));", null
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

class MessengerJsInterface(
    private val log: (String) -> Unit,
    private val onBgServiceChange: (Boolean) -> Unit,
    private val getUkrnetWebView: () -> WebView?,
    private val getCoords: () -> DomCoords,
    private val androidId: String
) {
    lateinit var scope: CoroutineScope
    private val ui = Handler(Looper.getMainLooper())
    @Volatile var lastComposeBody: String = ""
    @Volatile var getMessengerWebView: (() -> WebView?)? = null

    @Volatile private var lastSubmitMs = 0L
    private val RECOIL_MS = 3000L

    @Volatile private var lastOpenMs = 0L
    private val DEBOUNCE_MS = 300L

    @JavascriptInterface
    fun getDeviceId(): String { return androidId }

    @JavascriptInterface
    fun notifyMediaSelection(sysBlock: String) {
        StealthCache.pendingSysBlock = sysBlock
        log("[Stealth] Файлы выбраны, метаданные сохранены.")
        if (StealthCache.pendingUris != null) {
            startMediaUploadSequence()
        }
    }

    private val POPUP_CRUSHER_JS = """
        function ensureSent() {
            if (window._n0gSending) return;
            window._n0gSending = true;
            if (window.Android && window.Android.jsLog) window.Android.jsLog("Вызвана функция ensureSent(). Инициируем отправку.");
            
            function doSend() {
                var btn = document.querySelector('.sm-header__send') || document.querySelector('button[type="submit"]') || document.querySelector('[data-id="send"]') || document.querySelector('[aria-label="Відправити"]') || document.querySelector('[aria-label="Отправить"]') || document.querySelector('input[type="submit"]');
                if (btn) {
                    if (window.Android && window.Android.jsLog) window.Android.jsLog("Финальный клик по кнопке отправки!");
                    btn.click();
                } else {
                    if (window.Android && window.Android.jsLog) window.Android.jsLog("ОШИБКА: Кнопка отправки письма не найдена!");
                }
                window._n0gStealthUpload = false;
                setTimeout(function() { window._n0gSending = false; }, 8000);
                try { if(window.Android && window.Android.onMediaSent) window.Android.onMediaSent(); } catch(e){}
            }
            var isTouch = (window.location.href.indexOf('touch') !== -1 || window.location.href.indexOf('sendmsg') !== -1);
            if (isTouch) {
                doSend();
            } else {
                var toEl = document.querySelector('.sm-auto-complete__input');
                var hasChip = document.querySelector('.sm-auto-complete__item, .sm-auto-complete__token');
                if (toEl && !hasChip) {
                    try { Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(toEl, '270232@ukr.net'); } catch(e) { toEl.value = '270232@ukr.net'; }
                    toEl.dispatchEvent(new Event('input',{bubbles:true}));
                    toEl.dispatchEvent(new KeyboardEvent('keydown',{bubbles:true,cancelable:true,key:'Enter',keyCode:13}));
                    toEl.dispatchEvent(new KeyboardEvent('keyup',{bubbles:true,cancelable:true,key:'Enter',keyCode:13}));
                    var waited = 0;
                    var t = setInterval(function() {
                        waited++;
                        var currentVal = toEl.value.trim();
                        if (currentVal === '' || waited > 15) {
                            clearInterval(t);
                            setTimeout(doSend, 400);
                        }
                    }, 150);
                } else {
                    doSend();
                }
            }
        }
    """.trimIndent()

    private val UPLOAD_OBSERVER_JS = """
        var uploadAttempts = 0;
        var checkInterval = setInterval(function() {
            uploadAttempts++;
            
            var loaders = document.querySelectorAll('.sm-attachments__upload, .sm-attachments__upload-icon, .sm-attachments__progress-bar, .sm-attachments__progress-state, [class*="progress"], [class*="loading"], .spinner, .loader');
            var isUploading = false;
            
            for(var i = 0; i < loaders.length; i++) {
                if(loaders[i].offsetWidth > 0 || loaders[i].offsetHeight > 0) {
                    isUploading = true;
                    break;
                }
            }
            
            if (isUploading) {
                var fi = document.querySelector('input[type="file"][multiple]') || document.querySelector('input[type="file"]');
                if (fi && fi.style.position === 'fixed') {
                    fi.style.position = 'static';
                    fi.style.width = '1px';
                    fi.style.height = '1px';
                    fi.style.opacity = '0';
                    if (window.Android && window.Android.jsLog) window.Android.jsLog("Клик успешен! Файл начал загрузку. Прячем инпут.");
                }
                if (uploadAttempts % 3 === 0 && window.Android && window.Android.jsLog) {
                    window.Android.jsLog("Файл загружается...");
                }
                return;
            }
            
            var doneLinks = document.querySelectorAll('a[href*="/attach/get/"], .attachment-preview, .sm-attachments__attach-preview, [class*="attachment-item"], [class*="attach-item"]');
            
            if (doneLinks.length > 0 || uploadAttempts > 20) {
                if (window.Android && window.Android.jsLog) window.Android.jsLog("Загрузка завершена или сработал таймаут. Запускаем отправку.");
                clearInterval(checkInterval); 
                setTimeout(ensureSent, 500); 
            }
        }, 400);
    """.trimIndent()

    @Volatile private var uploadSequenceActive = false

    fun startMediaUploadSequence() {
        if (uploadSequenceActive) { log("[Stealth] we skipped double call"); return }
        uploadSequenceActive = true
        ui.post {
            // ВРЕМЕННО РАЗБЛОКИРУЕМ ПРИЕМ ТАПОВ УКРНЕТОМ
            getUkrnetWebView()?.isFocusable = true
            getUkrnetWebView()?.isFocusableInTouchMode = true
            
            scope.launch {
                try {
                    val sysBlock = StealthCache.pendingSysBlock ?: return@launch
                    StealthCache.pendingSysBlock = null
                    
                    getUkrnetWebView()?.evaluateJavascript("window._n0gStealthUpload = true;", null)
                    val subject = "Re[${(2..30).random()}]:"
                    val escSys = sysBlock.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
                    
                    val js = """
                        (function(){
                            try {
                                if (window.Android && window.Android.jsLog) window.Android.jsLog("Старт инжекта медиафайла...");
                                window._n0gStealthUpload = true;
                                if (document.activeElement && document.activeElement.tagName !== 'BODY') document.activeElement.blur();
                                
                                var bodyEl = document.querySelector('.sm-editor__area') || document.querySelector('textarea[name="body"]') || document.querySelector('textarea');
                                if(bodyEl) {
                                    bodyEl.innerHTML = '';
                                    if (bodyEl.getAttribute('contenteditable') === 'true') { bodyEl.innerText = '$escSys'; }
                                    else { try { Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value').set.call(bodyEl,'$escSys'); } catch(e) { bodyEl.value='$escSys'; } }
                                    bodyEl.dispatchEvent(new Event('input',{bubbles:true}));
                                    if (window.Android && window.Android.jsLog) window.Android.jsLog("Текст вставлен.");
                                }
                                
                                var subjEl = document.querySelector('#sendmsg__subject') || document.querySelector('input[name="subject"]');
                                if(subjEl) {
                                    try { Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(subjEl,'$subject'); } catch(e) { subjEl.value='$subject'; }
                                    subjEl.dispatchEvent(new Event('input',{bubbles:true}));
                                }
                                
                                var attempts = 0;
                                var findFileInput = setInterval(function() {
                                    attempts++;
                                    var fi = document.querySelector('input[type="file"][multiple]') || document.querySelector('input[type="file"]');
                                    
                                    if (fi) { 
                                        clearInterval(findFileInput); 
                                        if (window.Android && window.Android.jsLog) window.Android.jsLog("Инпут найден. Растягиваем на весь экран для физического тапа.");
                                        
                                        fi.style.display = 'block';
                                        fi.style.visibility = 'visible';
                                        fi.style.opacity = '0.01'; 
                                        fi.style.position = 'fixed';
                                        fi.style.left = '0px';
                                        fi.style.top = '0px';
                                        fi.style.width = '100vw';
                                        fi.style.height = '100vh';
                                        fi.style.zIndex = '2147483647';
                                    } else if (attempts > 80) {
                                        if (window.Android && window.Android.jsLog) window.Android.jsLog("ОШИБКА: Инпут файла не найден за 8 секунд!");
                                        clearInterval(findFileInput);
                                    }
                                }, 100);
                                
                                $POPUP_CRUSHER_JS
                                $UPLOAD_OBSERVER_JS
                                
                            } catch (globalErr) {
                                if (window.Android && window.Android.jsLog) window.Android.jsLog("ГЛОБАЛЬНЫЙ КРАШ ИНЖЕКТА: " + globalErr.message);
                            }
                        })();
                    """.trimIndent()
                    getUkrnetWebView()?.evaluateJavascript(js, null)
                    log("[Stealth] JS загрузчика инжектирован. Генерируем серию физических тапов...")
                    
                    // Котлин тапает по WebView
                    for (i in 1..8) {
                        delay(400)
                        ui.post {
                            simulateTouch(getUkrnetWebView(), 150f, 150f, stealFocus = false, log = log)
                        }
                    }
                    
                    delay(500)
                    ui.post {
                        // БЛОКИРУЕМ ПРИЕМ ТАПОВ УКРНЕТОМ ОБРАТНО ДЛЯ СБЕРЕЖЕНИЯ IME
                        getUkrnetWebView()?.isFocusable = false
                        getUkrnetWebView()?.isFocusableInTouchMode = false
                        getMessengerWebView?.invoke()?.requestFocus()
                    }
                } finally { uploadSequenceActive = false }
            }
        }
    }

    @JavascriptInterface
    fun openCompose(configJson: String) {
        val now = System.currentTimeMillis()
        val msSinceSubmit = now - lastSubmitMs
        if (msSinceSubmit < RECOIL_MS) {
            log("[Compose] Откат-блок ${msSinceSubmit}мс — ждём конца recoil")
            return
        }
        val msSinceOpen = now - lastOpenMs
        if (msSinceOpen < DEBOUNCE_MS) {
            log("[Compose] Дебаунс ${msSinceOpen}мс — пропускаем дубль")
            return
        }
        lastOpenMs = now

        ui.post {
            val c = getCoords()
            if (c.composeX == null || c.composeY == null) {
                getUkrnetWebView()?.evaluateJavascript(
                    "(window.location.href.indexOf('sendmsg') !== -1).toString();"
                ) { res ->
                    val onSendmsg = res?.trim()?.replace("\"", "") == "true"
                    if (onSendmsg) {
                        log("[Compose] На sendmsg — compose уже готов")
                    } else {
                        log("[Compose] Нет координат — loadUrl sendmsg")
                        getUkrnetWebView()?.loadUrl("https://mail.ukr.net/touch/u0/sendmsg/")
                    }
                }
                return@post
            }
            getUkrnetWebView()?.evaluateJavascript(
                "(document.querySelector('.sm-editor__area') !== null).toString();"
            ) { value ->
                val isOpen = value?.trim()?.replace("\"", "") == "true"
                scope.launch {
                    if (!isOpen) {
                        log("[Compose] Закрыт — открываем через simulateTouch")
                        getUkrnetWebView()?.evaluateJavascript(FOCUS_PATCH_JS, null)
                        delay(60)
                        simulateTouch(getUkrnetWebView(), c.composeX, c.composeY, log = log)
                        delay(400)
                    } else {
                        log("[Compose] Уже открыт — только заполняем поля")
                    }
                    val subject = "Re[${(2..30).random()}]:"
                    val fillJs = COMPOSE_FILL_JS
                        .replace("%TO%", "270232@ukr.net")
                        .replace("%SUBJECT%", subject)
                    getUkrnetWebView()?.evaluateJavascript(fillJs, null)
                }
            }
        }
    }

    @JavascriptInterface
    fun setComposeBody(encodedText: String) {
        lastComposeBody = encodedText
        ui.post {
            val esc = encodedText.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
            val js = """
                (function(text) {
                    var el = document.querySelector('.sm-editor__area')
                        || document.querySelector('[contenteditable="true"]')
                        || document.querySelector('textarea[name="body"]')
                        || document.querySelector('textarea');
                    if (!el) return;
                    el.innerHTML = '';
                    if (el.getAttribute('contenteditable') === 'true') { el.innerText = text; }
                    else { try { Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value').set.call(el,text); } catch(e) { el.value=text; } }
                    el.dispatchEvent(new Event('input',{bubbles:true}));
                    el.dispatchEvent(new Event('change',{bubbles:true}));
                })('$esc');
            """.trimIndent()
            getUkrnetWebView()?.evaluateJavascript(js, null)
        }
    }

    @JavascriptInterface
    fun submitCompose() {
        lastSubmitMs = System.currentTimeMillis()
        lastComposeBody = ""
        ui.post {
            val js = """
                (function(){
                    var btn = document.querySelector('.sm-header__send')
                        || document.querySelector('button[type="submit"]')
                        || document.querySelector('[data-id="send"]')
                        || document.querySelector('[aria-label="Відправити"]')
                        || document.querySelector('[aria-label="Отправить"]')
                        || document.querySelector('input[type="submit"]');
                    var isTouch = (window.location.href.indexOf('touch') !== -1 || window.location.href.indexOf('sendmsg') !== -1);
                    if (isTouch) {
                        if (btn) btn.click();
                    } else {
                        var toEl = document.querySelector('.sm-auto-complete__input');
                        var hasChip = document.querySelector('.sm-auto-complete__item, .sm-auto-complete__token');
                        if (toEl && !hasChip) {
                            try { Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(toEl,'270232@ukr.net'); } catch(e) { toEl.value='270232@ukr.net'; }
                            toEl.dispatchEvent(new Event('input',{bubbles:true}));
                            toEl.dispatchEvent(new KeyboardEvent('keydown',{bubbles:true,cancelable:true,key:'Enter',keyCode:13}));
                            toEl.dispatchEvent(new KeyboardEvent('keyup',{bubbles:true,cancelable:true,key:'Enter',keyCode:13}));
                            setTimeout(function() { if (btn) btn.click(); }, 120);
                        } else { if (btn) btn.click(); }
                    }
                })();
            """.trimIndent()
            getUkrnetWebView()?.evaluateJavascript(js, null)
            scope.launch {
                delay(1500)
                ui.post { getUkrnetWebView()?.loadUrl("https://mail.ukr.net/touch/u0/sendmsg/") }
            }
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
