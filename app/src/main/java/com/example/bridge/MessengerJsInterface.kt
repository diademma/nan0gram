package com.example

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import org.json.JSONArray
import org.json.JSONObject

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
    @Volatile var pendingVoiceUri: Uri? = null
    @Volatile var pendingStealthMode: String = "media"

    @Volatile 
    @set:kotlin.jvm.JvmName("setWallpaperPendingInternal")
    var isWallpaperPending: Boolean = false
    @Volatile var isWallpaperJustSelected: Boolean = false
    @Volatile var pendingMediaKey: String = ""

    private val prefs: SharedPreferences? by lazy {
        getUkrnetWebView()?.context?.getSharedPreferences("nan0gram_crypto_prefs", Context.MODE_PRIVATE)
    }

    private val crypto by lazy { MessengerCryptoHelper(log) }
    private val download by lazy { MessengerDownloadHelper(log, scope, { getMessengerWebView?.invoke() }, getUkrnetWebView, { mediaManager }) }
    private val compose by lazy { MessengerComposeHelper(log, scope, getUkrnetWebView, getCoords, { getMessengerWebView?.invoke() }) }
    private val db by lazy { MessengerDbHelper(log, scope, { getMessengerWebView?.invoke() }, { repository }, { mediaManager }) }
    private val audio by lazy { MessengerAudioHelper(getUkrnetWebView) }

    @JavascriptInterface
    fun setWallpaperPending(pending: Boolean) {
        isWallpaperPending = pending
    }

    @JavascriptInterface
    fun getDeviceId(): String { 
        return androidId 
    }

    @JavascriptInterface
    fun saveSettingString(key: String, value: String) {
        prefs?.edit()?.putString(key, value)?.apply()
    }

    @JavascriptInterface
    fun getSettingString(key: String, defValue: String): String {
        return prefs?.getString(key, defValue) ?: defValue
    }

    @JavascriptInterface
    fun setBgServiceActive(active: Boolean) { 
        onBgServiceChange(active) 
    }

    @JavascriptInterface
    fun postMessage(type: String, key: String, value: String) {}

    @JavascriptInterface
    fun notifyMediaSelection(sysBlock: String) {
        if (isWallpaperJustSelected) {
            log("[Stealth] Wallpaper режим — пропускаем отправку метаданных, сбрасываем флаг")
            isWallpaperJustSelected = false
            pendingMediaKey = ""
            return
        }
        log("[Stealth] Получены метаданные медиа. Прикрепляем к письму...")
        pendingMediaKey = ""
        ui.post {
            val ukr = getUkrnetWebView()
            ukr?.evaluateJavascript("window._n0gStealthUpload = true;", null)
            compose.setComposeBody(sysBlock)
            compose.startMediaUploadSequence()
        }
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
            val originalUri = Uri.fromFile(tempFile)
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
                    compose.startMediaUploadSequence()
                    prepareForDirectAttach(mediaKey)
                }
            } else {
                log("[Stealth Error] Не удалось зашифровать голосовой файл.")
            }
        } catch (e: Exception) {
            log("[Stealth Error] Ошибка подготовки голосового файла: ${e.message}")
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

    @JavascriptInterface
    fun encryptRsa(plainText: String, publicKeyB64: String): String =
        crypto.encryptRsa(plainText, publicKeyB64)

    @JavascriptInterface
    fun decryptRsa(encryptedB64: String, privateKeyB64: String): String =
        crypto.decryptRsa(encryptedB64, privateKeyB64)

    @JavascriptInterface
    fun encryptGcm(plainText: String, keyStr: String): String =
        crypto.encryptGcm(plainText, keyStr)

    @JavascriptInterface
    fun decryptGcm(combinedBase64: String, keyStr: String): String =
        crypto.decryptGcm(combinedBase64, keyStr)

    @android.webkit.JavascriptInterface
    fun saveMediaToDownloads(urlOrBase64: String, suggestedName: String) {
        download.saveMediaToDownloads(urlOrBase64, suggestedName)
    }

    @JavascriptInterface
    fun openCompose(configJson: String) {
        compose.openCompose(configJson)
    }

    @JavascriptInterface
    fun setComposeBody(encodedText: String) {
        compose.setComposeBody(encodedText)
    }

    @JavascriptInterface
    fun submitCompose() {
        compose.submitCompose()
    }

    @JavascriptInterface
    fun cancelCompose() {
        compose.cancelCompose()
    }

    @JavascriptInterface
    fun saveMessageToDb(jsonString: String) {
        db.saveMessageToDb(jsonString)
    }

    @JavascriptInterface
    fun saveChatToDb(jsonString: String) {
        db.saveChatToDb(jsonString)
    }

    @JavascriptInterface
    fun requestChatHistory(chatId: String, offset: Int, limit: Int) {
        db.requestChatHistory(chatId, offset, limit)
    }

    @JavascriptInterface
    fun requestChatsList() {
        db.requestChatsList()
    }

    @JavascriptInterface
    fun clearMediaCache() {
        db.clearMediaCache()
    }

    @JavascriptInterface
    fun clearAllHistoryLog() {
        db.clearAllHistoryLog()
    }

    @JavascriptInterface
    fun deleteMessageFromDb(chatId: String, msgId: String) {
        db.deleteMessageFromDb(chatId, msgId)
    }

    @JavascriptInterface
    fun updateMessageReactionInDb(chatId: String, msgId: String, reaction: String) {
        db.updateMessageReactionInDb(chatId, msgId, reaction)
    }

    @android.webkit.JavascriptInterface
    fun requestTransientFocus() {
        audio.requestTransientFocus()
    }

    @android.webkit.JavascriptInterface
    fun abandonFocus() {
        audio.abandonFocus()
    }
}