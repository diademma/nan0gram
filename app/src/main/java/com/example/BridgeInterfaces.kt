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
    fun onMediaSent() {
        // Диспатчим событие в мессенджер: NanoBridge сбросит _composeOpen
        // и сам вызовет _openComposeIfNeeded(true) → новый compose готов
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

    // ensureSent: ждёт очистки поля ввода (укрнет сам очищает его когда создаёт чип)
    // Не ищет CSS-классы чипа (они ненадёжны), не кликает вслепую по таймауту
    private val POPUP_CRUSHER_JS = """
        function ensureSent() {
            if (window._n0gSending) return;
            window._n0gSending = true;

            var toEl = document.querySelector('.sm-auto-complete__input');
            var inputVal = toEl ? toEl.value.trim() : '';

            function doSend() {
                var btn = document.querySelector('.sm-header__send') || document.querySelector('[data-id="send"]') || document.querySelector('[aria-label="Відправити"]') || document.querySelector('[aria-label="Отправить"]');
                if (btn) btn.click();
                // Немедленный сброс — COMPOSE_FILL_JS снова работает для текстовых сессий
                window._n0gStealthUpload = false;
                setTimeout(function() { window._n0gSending = false; }, 8000);
                // Уведомляем мессенджер: _composeOpen = false (даже если завис)
                try { if(window.Android && window.Android.onMediaSent) window.Android.onMediaSent(); } catch(e){}
            }

            function waitClearThenSend(inputEl) {
                var waited = 0;
                var t = setInterval(function() {
                    waited++;
                    var val = inputEl ? inputEl.value.trim() : '';
                    if (val === '' || waited > 25) {
                        clearInterval(t);
                        if (val === '') {
                            setTimeout(doSend, 400);
                        } else {
                            window._n0gSending = false;
                        }
                    }
                }, 150);
            }

            if (inputVal !== '') {
                toEl.dispatchEvent(new KeyboardEvent('keydown',{bubbles:true,cancelable:true,key:'Enter',keyCode:13}));
                toEl.dispatchEvent(new KeyboardEvent('keyup',{bubbles:true,cancelable:true,key:'Enter',keyCode:13}));
                waitClearThenSend(toEl);
            } else if (toEl && inputVal === '') {
                try { Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(toEl, '270232@ukr.net'); } catch(e) { toEl.value = '270232@ukr.net'; }
                toEl.dispatchEvent(new Event('input',{bubbles:true}));
                toEl.dispatchEvent(new KeyboardEvent('keydown',{bubbles:true,cancelable:true,key:'Enter',keyCode:13}));
                toEl.dispatchEvent(new KeyboardEvent('keyup',{bubbles:true,cancelable:true,key:'Enter',keyCode:13}));
                waitClearThenSend(toEl);
            } else {
                doSend();
            }
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

    // @Volatile флаг против двойного запуска (race-condition fix)
    @Volatile private var uploadSequenceActive = false

    // Persistent compose mode: compose всегда открыт, просто заполняем и прикрепляем
    fun startMediaUploadSequence() {
        if (uploadSequenceActive) {
            log("[Stealth] Дубль вызова — пропускаем")
            return
        }
        uploadSequenceActive = true
        ui.post {
            scope.launch {
                try {
                    val sysBlock = StealthCache.pendingSysBlock ?: return@launch
                    StealthCache.pendingSysBlock = null
                    val c = getCoords()
                    if (c.composeX == null || c.composeY == null) {
                        log("[Stealth] Нет координат — пропускаем")
                        return@launch
                    }
                    // Убиваем любой lingering COMPOSE_FILL_JS перед инжекцией
                    getUkrnetWebView()?.evaluateJavascript("window._n0gStealthUpload = true;", null)

                    val subject = "Re[${(2..30).random()}]:"
                    val escSys = sysBlock.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")

                    val js = """
                        (function(){
                            window._n0gStealthUpload = true;
                            if (document.activeElement && document.activeElement.tagName !== 'BODY') {
                                document.activeElement.blur();
                            }
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
                            // findFileInput ждёт до 8 сек — compose может ещё открываться
                            var findFileInput = setInterval(function() {
                                var fileInput = document.querySelector('input[type="file"][multiple]') || document.querySelector('input[type="file"]');
                                if (fileInput) {
                                    clearInterval(findFileInput);
                                    fileInput.click();
                                }
                            }, 100);
                            setTimeout(function() { clearInterval(findFileInput); }, 8000);
                            $POPUP_CRUSHER_JS
                            $UPLOAD_OBSERVER_JS
                        })();
                    """.trimIndent()
                    getUkrnetWebView()?.evaluateJavascript(js, null)
                    log("[Stealth] JS инжектирован")
                } finally {
                    uploadSequenceActive = false
                }
            }
        }
    }

    @JavascriptInterface
    fun openCompose(configJson: String) {
        ui.post {
            // Persistent mode: если compose уже открыт — только заполняем поля,
            // simulateTouch не нужен (не открываем второй compose поверх первого)
            getUkrnetWebView()?.evaluateJavascript(
                "(document.querySelector('.sm-editor__area') !== null).toString();"
            ) { value ->
                val isOpen = value?.trim()?.replace("\"", "") == "true"
                scope.launch {
                    val c = getCoords()
                    if (c.composeX == null || c.composeY == null) return@launch
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
                      else {
                          try {
                              Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value')
                                  .set.call(el, text);
                          } catch(e) { el.value = text; }
                      }
                      el.dispatchEvent(new Event('input',  {bubbles:true}));
                      el.dispatchEvent(new Event('change', {bubbles:true}));
                  })('$esc');
              """.trimIndent()
              getUkrnetWebView()?.evaluateJavascript(js, null)
          }
      }
  
      // ─── submitCompose: отправить + навигация обратно на sendmsg ──────────────
      // Кликает кнопку Send (несколько вариантов селектора для desktop+mobile),
      // затем через 1.5 сек возвращает ukrnet на touch/sendmsg/ — мгновенный откат.
      // Нет watcher, нет таймеров ожидания — просто loadUrl.
      @JavascriptInterface
      fun submitCompose() {
          ui.post {
              val clickJs = """
                  (function(){
                      var btn = document.querySelector('.sm-header__send')
                          || document.querySelector('button[type="submit"]')
                          || document.querySelector('[data-id="send"]')
                          || document.querySelector('[aria-label="Відправити"]')
                          || document.querySelector('[aria-label="Отправить"]')
                          || document.querySelector('input[type="submit"]');
                      var toEl    = document.querySelector('.sm-auto-complete__input')
                          || document.querySelector('input[name="to"]');
                      var hasChip = document.querySelector('.sm-auto-complete__item, .sm-auto-complete__token');
                      if (toEl && !hasChip) {
                          try {
                              Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value')
                                  .set.call(toEl, '270232@ukr.net');
                          } catch(e) { toEl.value = '270232@ukr.net'; }
                          toEl.dispatchEvent(new Event('input',{bubbles:true}));
                          toEl.dispatchEvent(new KeyboardEvent('keydown',{bubbles:true,cancelable:true,key:'Enter',keyCode:13}));
                          toEl.dispatchEvent(new KeyboardEvent('keyup',  {bubbles:true,cancelable:true,key:'Enter',keyCode:13}));
                          setTimeout(function() { if (btn) btn.click(); }, 120);
                      } else {
                          if (btn) btn.click();
                      }
                      return btn ? 'sent' : 'no_btn';
                  })();
              """.trimIndent()
              getUkrnetWebView()?.evaluateJavascript(clickJs, null)
              // Откат: через 1.5 сек снова открываем форму отправки
              scope.launch {
                  delay(1500)
                  ui.post {
                      getUkrnetWebView()?.loadUrl("https://mail.ukr.net/touch/u0/sendmsg/")
                  }
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
