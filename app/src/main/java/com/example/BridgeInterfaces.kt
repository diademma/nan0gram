package com.example

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement

class UkrnetJsInterface(
    private val log: (String) -> Unit,
    private val onLoginSuccess: () -> Unit,
    private val onCoordsUpdate: (DomCoords) -> Unit,
    private val onFirstCoordsLogged: () -> Unit,
    private val getMessengerWebView: () -> WebView?,
    private val isLoginHandled: () -> Boolean,
    private val getCurrentCoords: () -> DomCoords,
    private val clearComposeBody: () -> Unit = {}
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

class MessengerJsInterface(
    private val log: (String) -> Unit,
    private val onBgServiceChange: (Boolean) -> Unit,
    private val getUkrnetWebView: () -> WebView?,
    private val getCoords: () -> DomCoords,
    private val androidId: String
) {
    lateinit var scope: CoroutineScope
    var repository: NanogramRepository? = null
    var mediaManager: MediaManager? = null
    private val ui = Handler(Looper.getMainLooper())
    @Volatile var lastComposeBody: String = ""
    @Volatile var getMessengerWebView: (() -> WebView?)? = null
    @Volatile var isVoicePending: Boolean = false
    @Volatile var pendingVoiceUri: android.net.Uri? = null
    @Volatile var pendingStealthMode: String = "media"
    @Volatile 
    @set:kotlin.jvm.JvmName("setWallpaperPendingInternal")
    var isWallpaperPending: Boolean = false
    @Volatile var isWallpaperJustSelected: Boolean = false

    @JavascriptInterface
    fun setWallpaperPending(pending: Boolean) {
        isWallpaperPending = pending
    }

    @android.webkit.JavascriptInterface
    fun submitVoiceFile(base64Data: String, duration: Int) {
        log("[Stealth] Получено голосовое сообщение из JS. Начинаем упаковку...")
        val context = getUkrnetWebView()?.context ?: return
        try {
            val cleanB64 = if (base64Data.contains("base64,")) base64Data.split("base64,")[1] else base64Data
            val audioBytes = android.util.Base64.decode(cleanB64, android.util.Base64.NO_WRAP)
            val tempFile = java.io.File(context.cacheDir, "voice_msg.webm")
            java.io.FileOutputStream(tempFile).use { it.write(audioBytes) }
            val originalUri = android.net.Uri.fromFile(tempFile)
            val mediaKey = pendingMediaKey.ifEmpty {
                val arr = ByteArray(16)
                java.security.SecureRandom().nextBytes(arr)
                arr.joinToString("") { "%02x".format(it) }
            }
            pendingMediaKey = mediaKey
            val encryptedUri = createEncryptedStealthCopy(context, originalUri, mediaKey)
            if (encryptedUri != null) {
                isVoicePending = true
                pendingVoiceUri = encryptedUri
                ui.post {
                    val ukr = getUkrnetWebView()
                    ukr?.evaluateJavascript("window._n0gStealthUpload = true;", null)
                    startMediaUploadSequence()
                    prepareForDirectAttach(mediaKey)
                }
            } else {
                log("[Stealth Error] Не удалось зашифровать голосовой файл.")
            }
        } catch (e: Exception) {
            log("[Stealth Error] Ошибка подготовки голосового файла: ${"$"}{e.message}")
        }
    }

    @Volatile private var lastSubmitMs = 0L
    private val RECOIL_MS = 3000L

    @Volatile private var lastOpenMs = 0L
    private val DEBOUNCE_MS = 300L

    // Системное зашифрованное хранилище ключей
    private val prefs by lazy {
        getUkrnetWebView()?.context?.getSharedPreferences("nan0gram_crypto_prefs", Context.MODE_PRIVATE)
    }

    // Временный ключ медиафайлов
    @Volatile var pendingMediaKey: String = ""

    @JavascriptInterface
    fun encryptRsa(plainText: String, publicKeyB64: String): String {
        return try {
            val keyBytes = android.util.Base64.decode(publicKeyB64, android.util.Base64.NO_WRAP)
            val spec = X509EncodedKeySpec(keyBytes)
            val kf = KeyFactory.getInstance("RSA")
            val publicKey = kf.generatePublic(spec)

            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            android.util.Base64.encodeToString(encryptedBytes, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            log("[Crypto Error] RSA encryption failed: ${e.message}")
            ""
        }
    }

    @JavascriptInterface
    fun decryptRsa(encryptedB64: String, privateKeyB64: String): String {
        return try {
            val keyBytes = android.util.Base64.decode(privateKeyB64, android.util.Base64.NO_WRAP)
            val spec = java.security.spec.PKCS8EncodedKeySpec(keyBytes)
            val kf = java.security.KeyFactory.getInstance("RSA")
            val privateKey = kf.generatePrivate(spec)

            val cipher = javax.crypto.Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, privateKey)
            val encryptedBytes = android.util.Base64.decode(encryptedB64, android.util.Base64.NO_WRAP)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            log("[Crypto Error] RSA decryption failed: ${e.message}")
            ""
        }
    }

    @JavascriptInterface
    fun encryptGcm(plainText: String, keyStr: String): String {
        return try {
            val keyBytes = keyStr.toByteArray(Charsets.UTF_8)
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val keyBytes32 = digest.digest(keyBytes)
            val secretKey = javax.crypto.spec.SecretKeySpec(keyBytes32, "AES")
            
            val iv = ByteArray(12)
            java.security.SecureRandom().nextBytes(iv)
            
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            val spec = javax.crypto.spec.GCMParameterSpec(128, iv)
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey, spec)
            
            val ciphertext = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            
            val combined = ByteArray(iv.size + ciphertext.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
            
            android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            log("[Crypto Error] Encryption failed: ${e.message}")
            ""
        }
    }

    @JavascriptInterface
    fun decryptGcm(combinedBase64: String, keyStr: String): String {
        return try {
            val combined = android.util.Base64.decode(combinedBase64, android.util.Base64.NO_WRAP)
            if (combined.size < 12) return "[Ошибка дешифрования]"
            
            val iv = ByteArray(12)
            System.arraycopy(combined, 0, iv, 0, 12)
            
            val ciphertext = ByteArray(combined.size - 12)
            System.arraycopy(combined, 12, ciphertext, 0, ciphertext.size)
            
            val keyBytes = keyStr.toByteArray(Charsets.UTF_8)
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val keyBytes32 = digest.digest(keyBytes)
            val secretKey = javax.crypto.spec.SecretKeySpec(keyBytes32, "AES")
            
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            val spec = javax.crypto.spec.GCMParameterSpec(128, iv)
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey, spec)
            
            val decrypted = cipher.doFinal(ciphertext)
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            log("[Crypto Error] Decryption failed: ${e.message}")
            "[Ошибка дешифрования]"
        }
    }

    @JavascriptInterface
    fun getDeviceId(): String { return androidId }

    @JavascriptInterface
    fun notifyMediaSelection(sysBlock: String) {
        if (isWallpaperJustSelected) {
            log("[Stealth] Wallpaper режим — пропускаем отправку метаданных, сбрасываем флаг")
            isWallpaperJustSelected = false
            pendingMediaKey = ""
            return
        }
        log("[Stealth] Получены метаданные медиа. Прикрепляем к письму...")
        pendingMediaKey = "" // Очищаем временный ключ после подтверждения отправки из JS
        ui.post {
            val ukr = getUkrnetWebView()
            ukr?.evaluateJavascript("window._n0gStealthUpload = true;", null)
            setComposeBody(sysBlock)
            startMediaUploadSequence()
        }
    }

    @JavascriptInterface
    fun prepareForDirectAttach(mediaKey: String) {
        prepareForDirectAttachWithMode(mediaKey, "media")
    }

    @JavascriptInterface
    fun prepareForDirectAttachWithMode(mediaKey: String, mode: String) {
        pendingMediaKey = mediaKey
        pendingStealthMode = mode
        ui.post {
            val ukr = getUkrnetWebView()
            val mess = getMessengerWebView?.invoke()
            if (ukr != null) {
                log("[Stealth] Сканируем координаты кнопки-скрепки...")
                ukr.evaluateJavascript("""
                    (function(){
                        var el = document.querySelector(".sm-header__attach:not(input)") || document.querySelector("[class*='attach']:not(input)");
                        if (!el) return 'not_found';
                        var r = el.getBoundingClientRect();
                        if (r.width === 0 && r.height === 0) return 'not_found';
                        return JSON.stringify({
                            x: Math.round(r.left + r.width/2),
                            y: Math.round(r.top + r.height/2)
                        });
                    })();
                """.trimIndent()) { result ->
                    if (result != null && result != "null" && result != "\"not_found\"") {
                        try {
                            val cleanJson = if (result.startsWith("\"") && result.endsWith("\"")) {
                                result.substring(1, result.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
                            } else {
                                result
                            }
                            val coordsObj = JSONObject(cleanJson)
                            val x = coordsObj.getDouble("x").toFloat()
                            val y = coordsObj.getDouble("y").toFloat()
                            
                            log("[Stealth] Скрепка найдена на X=$x, Y=$y. Активируем скрытый тап в режиме $mode...")
                            
                            ukr.isFocusable = true
                            ukr.isFocusableInTouchMode = true
                            ukr.requestFocus()
                            
                            simulateTouch(ukr, x, y, stealFocus = false, log = log)
                            
                            ui.postDelayed({
                                ukr.isFocusable = false
                                ukr.isFocusableInTouchMode = false
                                mess?.requestFocus()
                            }, 1500)
                        } catch(e: Exception) {
                            log("[Stealth] Ошибка разбора координат: ${e.message}")
                        }
                    } else {
                        log("[Stealth] Кнопка скрепки не найдена на странице УкрНета!")
                    }
                }
            }
        }
    }

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

    @Volatile private var uploadSequenceActive = false

    fun startMediaUploadSequence() {
        ui.post {
            val ukr = getUkrnetWebView()
            log("[Upload] Начинаем отслеживание загрузки файла...")
            ukr?.evaluateJavascript(POPUP_CRUSHER_JS + '\n' + UPLOAD_OBSERVER_JS, null)
        }
    }

    @JavascriptInterface
    fun openCompose(configJson: String) {
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
                        getUkrnetWebView()?.evaluateJavascript(SENDMSG_FILL_JS, null)
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

    @JavascriptInterface
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
                val js = """(function(){ var btn = document.querySelector(".sm-header__cancel") || document.querySelector("[aria-label='Відмінити']") || document.querySelector("[aria-label='Отменить']"); if (btn) { btn.click(); return 'ok'; } history.back(); return 'fallback'; })();"""
                getUkrnetWebView()?.evaluateJavascript(js, null)
            }
        }
    }

    @JavascriptInterface
    fun setBgServiceActive(active: Boolean) { onBgServiceChange(active) }

    @JavascriptInterface
    fun postMessage(type: String, key: String, value: String) {}

    @JavascriptInterface
    fun saveSettingString(key: String, value: String) {
        prefs?.edit()?.putString(key, value)?.apply()
    }

    @JavascriptInterface
    fun getSettingString(key: String, defValue: String): String {
        return prefs?.getString(key, defValue) ?: defValue
    }

    @JavascriptInterface
    fun saveMessageToDb(jsonString: String) {
        val repo = repository ?: return
        val mm = mediaManager ?: return
        scope.launch {
            try {
                val obj = JSONObject(jsonString)
                
                val mediaPathsArray = obj.optJSONArray("mediaPaths") ?: if (obj.optString("mediaPaths").startsWith("[")) JSONArray(obj.optString("mediaPaths")) else JSONArray()
                val savedPaths = JSONArray()
                for (i in 0 until mediaPathsArray.length()) {
                    val path = mediaPathsArray.getString(i)
                    if (path.startsWith("data:")) {
                        val ext = when {
                            path.startsWith("data:video") -> "mp4"
                            path.startsWith("data:audio") -> "webm"
                            else -> "jpg"
                        }
                        savedPaths.put(mm.saveBase64Media(path, ext))
                    } else {
                        savedPaths.put(path)
                    }
                }
                
                val thumbsArray = obj.optJSONArray("mediaThumbnails") ?: if (obj.optString("mediaThumbnails").startsWith("[")) JSONArray(obj.optString("mediaThumbnails")) else JSONArray()
                val savedThumbs = JSONArray()
                for (i in 0 until thumbsArray.length()) {
                    val thumb = thumbsArray.getString(i)
                    if (thumb.startsWith("data:")) {
                        savedThumbs.put(mm.saveBase64Media(thumb, "jpg"))
                    } else {
                        savedThumbs.put(thumb)
                    }
                }

                val msg = MessageEntity(
                    msgId = obj.optString("id", java.util.UUID.randomUUID().toString()),
                    chatId = obj.optString("chatId", "inbox"),
                    type = obj.optString("type", "in"),
                    author = obj.optString("author", "Собеседник"),
                    text = obj.optString("text", ""),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    mediaType = obj.optString("mediaType", "none"),
                    mediaPaths = savedPaths.toString(),
                    mediaThumbnails = savedThumbs.toString(),
                    fileName = obj.optString("fileName", ""),
                    fileSize = obj.optLong("fileSize", 0L),
                    audioDuration = obj.optInt("audioDuration", 0),
                    replyToId = obj.optString("replyToId", ""),
                    reaction = obj.optString("reaction", "")
                )
                repo.saveMessage(msg)
            } catch (e: Exception) {
                log("[DB Error] JS saveMessageToDb: ${e.message}")
            }
        }
    }

    @JavascriptInterface
    fun saveChatToDb(jsonString: String) {
        val repo = repository ?: return
        scope.launch {
            try {
                val obj = JSONObject(jsonString)
                val chat = ChatEntity(
                    chatId = obj.optString("chatId"),
                    name = obj.optString("name"),
                    username = obj.optString("username"),
                    avatarUrl = obj.optString("avatarUrl"),
                    unreadCount = obj.optInt("unreadCount", 0),
                    lastMessageTime = obj.optLong("lastMessageTime", System.currentTimeMillis()),
                    lastMessagePreview = obj.optString("lastMessagePreview", ""),
                    isPinned = obj.optBoolean("isPinned", false)
                )
                repo.saveChat(chat)
            } catch (e: Exception) {
                log("[DB Error] JS saveChatToDb: ${e.message}")
            }
        }
    }

    @JavascriptInterface
    fun requestChatHistory(chatId: String, offset: Int, limit: Int) {
        val repo = repository ?: return
        scope.launch {
            val msgs = repo.getMessages(chatId, limit, offset)
            val jsonArray = JSONArray()
            for (msg in msgs) {
                val obj = JSONObject().apply {
                    put("id", msg.msgId)
                    put("chatId", msg.chatId)
                    put("type", msg.type)
                    put("author", msg.author)
                    put("text", msg.text)
                    put("timestamp", msg.timestamp)
                    put("mediaType", msg.mediaType)
                    
                    val pathsStr = if (msg.mediaPaths.trim().startsWith("[")) msg.mediaPaths else "[]"
                    val thumbsStr = if (msg.mediaThumbnails.trim().startsWith("[")) msg.mediaThumbnails else "[]"
                    put("mediaPaths", JSONArray(pathsStr))
                    put("mediaThumbnails", JSONArray(thumbsStr))
                    
                    put("fileName", msg.fileName)
                    put("fileSize", msg.fileSize)
                    put("audioDuration", msg.audioDuration)
                    put("replyToId", msg.replyToId)
                    put("reaction", msg.reaction)
                }
                jsonArray.put(obj)
            }
            
            // Заворачиваем историю во внешний JSON объект для жесткой привязки к chatId
            val responseObj = JSONObject().apply {
                put("chatId", chatId)
                put("offset", offset)
                put("messages", jsonArray)
            }
            val escaped = responseObj.toString().replace("\\", "\\\\").replace("\"", "\\\"")
            
            ui.post {
                getMessengerWebView?.invoke()?.evaluateJavascript(
                    "window.dispatchEvent(new CustomEvent('nan0gram:chat-history', { detail: \"$escaped\" }));", null
                )
            }
        }
    }

    @JavascriptInterface
    fun requestChatsList() {
        val repo = repository ?: return
        scope.launch {
            val chats = repo.getChats()
            val jsonArray = JSONArray()
            for (chat in chats) {
                val obj = JSONObject().apply {
                    put("chatId", chat.chatId)
                    put("name", chat.name)
                    put("username", chat.username)
                    put("avatarUrl", chat.avatarUrl)
                    put("unreadCount", chat.unreadCount)
                    put("lastMessageTime", chat.lastMessageTime)
                    put("lastMessagePreview", chat.lastMessagePreview)
                    put("isPinned", chat.isPinned)
                }
                jsonArray.put(obj)
            }
            val escaped = jsonArray.toString().replace("\\", "\\\\").replace("\"", "\\\"")
            ui.post {
                getMessengerWebView?.invoke()?.evaluateJavascript(
                    "window.dispatchEvent(new CustomEvent('nan0gram:chats-list', { detail: \"$escaped\" }));", null
                )
            }
        }
    }

    @JavascriptInterface
    fun clearMediaCache() {
        val mm = mediaManager ?: return
        scope.launch {
            val bytesFreed = mm.clearMediaCache()
            val mbFreed = bytesFreed / (1024 * 1024)
            ui.post {
                getMessengerWebView?.invoke()?.evaluateJavascript(
                    "alert('Кэш медиа очищен. Освобождено: $mbFreed MB');", null
                )
            }
        }
    }

    @JavascriptInterface
    fun clearAllHistoryLog() {
        val repo = repository ?: return
        scope.launch {
            repo.clearAllHistoryLog()
            ui.post {
                getMessengerWebView?.invoke()?.evaluateJavascript(
                    "alert('История всех переписок полностью очищена.'); window.dispatchEvent(new CustomEvent('nan0gram:history-cleared'));", null
                )
            }
        }
    }

    @JavascriptInterface
    fun deleteMessageFromDb(chatId: String, msgId: String) {
        val repo = repository ?: return
        scope.launch {
            repo.deleteMessage(chatId, msgId)
        }
    }

    @JavascriptInterface
    fun updateMessageReactionInDb(chatId: String, msgId: String, reaction: String) {
        val repo = repository ?: return
        scope.launch {
            repo.updateReaction(chatId, msgId, reaction)
        }
    }

    private val focusChangeListener = android.media.AudioManager.OnAudioFocusChangeListener { }
            private var focusRequest: Any? = null

            @android.webkit.JavascriptInterface
            fun requestTransientFocus() {
                ui.post {
                    val context = getUkrnetWebView()?.context ?: return@post
                    val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        val attrib = android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                        val request = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                            .setAudioAttributes(attrib)
                            .setOnAudioFocusChangeListener { }
                            .build()
                        focusRequest = request
                        am.requestAudioFocus(request)
                    } else {
                        @Suppress("DEPRECATION")
                        am.requestAudioFocus(focusChangeListener, android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    }
                }
            }

            @android.webkit.JavascriptInterface
            fun abandonFocus() {
                ui.post {
                    val context = getUkrnetWebView()?.context ?: return@post
                    val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        val request = focusRequest as? android.media.AudioFocusRequest
                        if (request != null) {
                            am.abandonAudioFocusRequest(request)
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        am.abandonAudioFocus(focusChangeListener)
                    }
                }
            }
}
