package com.example

import android.webkit.WebView
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import android.content.Context

internal class MessengerComposeHelper(
    private val log: (String) -> Unit,
    private val getScope: () -> CoroutineScope,
    private val getUkrnetWebView: () -> WebView?,
    private val getCoords: () -> DomCoords,
    private val getMessengerWebView: () -> WebView?
) {
    @Volatile var lastSubmitMs = 0L
    val RECOIL_MS = 3000L
    @Volatile var lastOpenMs = 0L
    val DEBOUNCE_MS = 300L
    @Volatile var uploadSequenceActive = false
    @Volatile var lastComposeBody: String = ""
    @Volatile var currentRecipient: String = ""

    private val POPUP_CRUSHER_JS = """
        function ensureSent() {
            if (window._n0gSending) return;
            window._n0gSending = true;
            function doSend() {
                console.log('[Upload] Нажимаем кнопку Отправить!');
                var btn = document.querySelector(".sm-header__send") 
                    || document.querySelector("button[type='submit']") 
                    || document.querySelector("[data-id='send']") 
                    || document.querySelector("input[type='submit']");
                if (btn) { btn.disabled = false; btn.click(); }
                window._n0gStealthUpload = false;
                setTimeout(function() { window._n0gSending = false; }, 8000);
                try { if(window.Android && window.Android.onMediaSent) window.Android.onMediaSent(); } catch(e){}
            }
            doSend();
        }
    """.trimIndent()

    private val UPLOAD_OBSERVER_JS = """
        if (window._n0gUploadInt) clearInterval(window._n0gUploadInt);
        var uploadCheckCount = 0;
        var hasStarted = false;
        window._n0gUploadInt = setInterval(function() {
            uploadCheckCount++;
            
            var isUploading = document.querySelectorAll(".sm-attachments__progress-state").length > 0;
            var isSaving = document.querySelectorAll(".sm-header__loader").length > 0;
            if (isSaving) { isUploading = true; }
            
            var sendBtn = document.querySelector(".sm-header__send") 
                || document.querySelector("button[type='submit']") 
                || document.querySelector("[data-id='send']");
            if (sendBtn && sendBtn.disabled) { isUploading = true; }
            
            if (isUploading) {
                hasStarted = true;
                return;
            }
            
            if (hasStarted) {
                clearInterval(window._n0gUploadInt);
                setTimeout(ensureSent, 600);
                return;
            }
            
            if (uploadCheckCount > 30) {
                clearInterval(window._n0gUploadInt);
                setTimeout(ensureSent, 500);
            }
        }, 500);
    """.trimIndent()

    private val ui = Handler(Looper.getMainLooper())

    fun startMediaUploadSequence() {
        ui.post {
            val ukr = getUkrnetWebView()
            log("[Upload] Начинаем отслеживание загрузки файла...")
            ukr?.evaluateJavascript(POPUP_CRUSHER_JS + '\n' + UPLOAD_OBSERVER_JS, null)
        }
    }

    fun openCompose(configJson: String) {
        try {
            if (configJson.startsWith("{")) {
                val obj = org.json.JSONObject(configJson)
                val to = obj.optString("to", "")
                if (to.isNotEmpty()) currentRecipient = to
            }
        } catch(e: Exception) {}

        if (configJson == "RETRY_UKRNET") {
            ukrnetRetryCounter = 0
            isUkrnetRetrying = false
            log("[Compose] Получена команда перезапуска сети. Сбрасываем счетчики и перезагружаем Ukrnet.")
            ui.post {
                val ukr = getUkrnetWebView()
                ukr?.loadUrl("https://mail.ukr.net/touch/u0/sendmsg/")
            }
            return
        }
        if (configJson.startsWith("COPY_ERROR:")) {
            val err = configJson.substring(11)
            ui.post {
                val ukr = getUkrnetWebView()
                val ctx = ukr?.context
                if (ctx != null) {
                    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Ukrnet Error", err)
                    clipboard?.setPrimaryClip(clip)
                    log("[Stealth] Текст ошибки скопирован в буфер обмена natively.")
                } else {
                    log("[Stealth] Ошибка копирования: Context недоступен.")
                }
            }
            return
        }

        val now = System.currentTimeMillis()
        val msSinceSubmit = now - lastSubmitMs
        if (msSinceSubmit < RECOIL_MS) {
            log("[Compose] Откат-блок $msSinceSubmit мс — ждём конца recoil")
            return
        }
        val msSinceOpen = now - lastOpenMs
        if (msSinceOpen < DEBOUNCE_MS) {
            log("[Compose] Дебаунс $msSinceOpen мс — пропускаем дубль")
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
                        log("[Compose] На sendmsg — compose уже готов, заполняем...")
                        getUkrnetWebView()?.evaluateJavascript("delete window._n0gFilled;", null)
                        getUkrnetWebView()?.evaluateJavascript("window._n0gTargetRecipient = '$currentRecipient';", null)
                        getUkrnetWebView()?.evaluateJavascript(SENDMSG_FILL_JS.replace("%TO%", currentRecipient), null)
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
                getScope().launch {
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
                        .replace("%TO%", currentRecipient)
                        .replace("%SUBJECT%", subject)
                    getUkrnetWebView()?.evaluateJavascript(fillJs, null)
                }
            }
        }
    }

    fun setComposeBody(encodedText: String) {
        lastComposeBody = encodedText
        ui.post {
            val esc = encodedText
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "")
                .replace("\u2028", "")
                .replace("\u2029", "")

            val js = """
                (function(text) {
                    var el = document.querySelector(".sm-editor__area")
                        || document.querySelector("[contenteditable='true']")
                        || document.querySelector("textarea[name='body']")
                        || document.querySelector("textarea");
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

    fun submitCompose() {
        lastSubmitMs = System.currentTimeMillis()
        lastComposeBody = ""
        ui.post {
            val js = """
                (function(){
                    var btn = document.querySelector(".sm-header__send")
                        || document.querySelector("button[type='submit']")
                        || document.querySelector("[data-id='send']")
                        || document.querySelector("[aria-label='Відправити']")
                        || document.querySelector("[aria-label='Отправить']")
                        || document.querySelector("input[type='submit']");
                    var isTouch = (window.location.href.indexOf('touch') !== -1 || window.location.href.indexOf('sendmsg') !== -1);
                    if (isTouch) {
                        if (btn) btn.click();
                    } else {
                        var toEl = document.querySelector(".sm-auto-complete__input");
                        var hasChip = document.querySelector(".sm-auto-complete__item, .sm-auto-complete__token");
                        if (toEl && !hasChip) {
                            var targetTo = window._n0gTargetRecipient || '$currentRecipient';
                            try { Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(toEl, targetTo); } catch(e) { toEl.value = targetTo; }
                            toEl.dispatchEvent(new Event('input',{bubbles:true}));
                            toEl.dispatchEvent(new KeyboardEvent('keydown',{bubbles:true,cancelable:true,key:'Enter',keyCode:13}));
                            toEl.dispatchEvent(new KeyboardEvent('keyup',{bubbles:true,cancelable:true,key:'Enter',keyCode:13}));
                            setTimeout(function() { if (btn) btn.click(); }, 120);
                        } else { if (btn) btn.click(); }
                    }
                })();
            """.trimIndent()
            getUkrnetWebView()?.evaluateJavascript(js, null)
            getScope().launch {
                delay(1500)
                ui.post { getUkrnetWebView()?.loadUrl("https://mail.ukr.net/touch/u0/sendmsg/") }
            }
        }
    }

    fun cancelCompose() {
        ui.post {
            getScope().launch {
                val js = """(function(){ var btn = document.querySelector(".sm-header__cancel") || document.querySelector("[aria-label='Відмінити']") || document.querySelector("[aria-label='Отменить']"); if (btn) { btn.click(); return 'ok'; } history.back(); return 'fallback'; })();"""
                getUkrnetWebView()?.evaluateJavascript(js, null)
            }
        }
    }
}