
package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject

private const val UKRNET_SENDMSG_URL = "https://mail.ukr.net/touch/u0/sendmsg/"

private fun String?.isUkrnetTouchUrl(): Boolean {
    val value = this?.lowercase(Locale.getDefault()) ?: return false
    return value.contains("mail.ukr.net/touch/u0/")
}

private fun String?.isSendMsgUrl(): Boolean {
    val value = this?.lowercase(Locale.getDefault()) ?: return false
    return value.contains("mail.ukr.net/touch/u0/sendmsg/")
}

private fun WebView?.forceSendMsg(log: (String) -> Unit, source: String) {
    val view = this ?: return
    val currentUrl = view.url ?: return
    if (!currentUrl.isUkrnetTouchUrl() || currentUrl.isSendMsgUrl()) return

    log("[ComposeGuardian] $source → возвращаемся на sendmsg")
    view.post {
        try {
            view.stopLoading()
        } catch (_: Throwable) {}
        view.loadUrl(UKRNET_SENDMSG_URL)
    }
}

object StealthCache {
    var pendingUris: Array<Uri>? = null
    var pendingSysBlock: String? = null
}

fun getOriginalFileName(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) result = cursor.getString(index)
            }
        } catch (_: Exception) {} finally { cursor?.close() }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != null && cut != -1) result = result.substring(cut + 1)
    }
    return result ?: "media_file"
}

fun createEncryptedStealthCopy(context: Context, originalUri: Uri, keyStr: String): Uri? {
    return try {
        val inputStream = context.contentResolver.openInputStream(originalUri) ?: return null
        val originalName = getOriginalFileName(context, originalUri)
        val baseName = if (originalName.contains(".")) originalName.substringBeforeLast(".") else originalName
        val file = File(context.cacheDir, "${baseName}.bin")
        
        val keyBytes = "$keyStr:media".toByteArray(Charsets.UTF_8)
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val keyBytes32 = digest.digest(keyBytes)
        val secretKey = javax.crypto.spec.SecretKeySpec(keyBytes32, "AES")
        
        val iv = ByteArray(12)
        java.security.SecureRandom().nextBytes(iv)
        
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        val spec = javax.crypto.spec.GCMParameterSpec(128, iv)
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey, spec)
        
        val ciphertext = cipher.doFinal(inputStream.readBytes())
        inputStream.close()
        
        val outputStream = FileOutputStream(file)
        outputStream.write(iv)
        outputStream.write(ciphertext)
        outputStream.flush()
        outputStream.close()
        Uri.fromFile(file)
    } catch (e: Exception) {
        android.util.Log.e("nan0gram", "Encryption failed: ${e.message}")
        null
    }
}

fun createStealthCopy(context: Context, originalUri: Uri): Uri? {
    return try {
        val inputStream = context.contentResolver.openInputStream(originalUri) ?: return null
        val file = File(context.cacheDir, "sys_data_${System.currentTimeMillis()}.bin")
        val outputStream = FileOutputStream(file)
        inputStream.copyTo(outputStream)
        inputStream.close()
        outputStream.close()
        Uri.fromFile(file)
    } catch (e: Exception) {
        null
    }
}

fun uriToBase64(context: Context, uri: Uri): String {
    return try {
        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
        val inputStream = context.contentResolver.openInputStream(uri) ?: return ""
        
        if (mimeType.startsWith("video")) {
            val bytes = inputStream.readBytes()
            inputStream.close()
            val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            return "data:$mimeType;base64,$b64"
        }
        
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeStream(inputStream, null, options)
        inputStream.close()
        
        val maxDimension = 800
        var scale = 1
        if (options.outHeight > maxDimension || options.outWidth > maxDimension) {
            val halfHeight = options.outHeight / 2
            val halfWidth = options.outWidth / 2
            while ((halfHeight / scale) >= maxDimension && (halfWidth / scale) >= maxDimension) {
                scale *= 2
            }
        }
        
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = scale
        }
        val secondStream = context.contentResolver.openInputStream(uri) ?: return ""
        val bitmap = BitmapFactory.decodeStream(secondStream, null, decodeOptions) ?: return ""
        secondStream.close()
        
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        val bytes = outputStream.toByteArray()
        bitmap.recycle()
        
        val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        "data:image/jpeg;base64,$b64"
    } catch (e: Exception) {
        ""
    }
}

private fun getVideoThumbnailBase64(context: Context, uri: Uri): String {
    val retriever = android.media.MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val bitmap = retriever.getFrameAtTime(1000000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: retriever.getFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: return ""
        val outputStream = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, outputStream)
        val bytes = outputStream.toByteArray()
        bitmap.recycle()
        android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    } catch (e: Exception) {
        ""
    } finally {
        try {
            retriever.release()
        } catch (_: Exception) {}
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AppScreen(
    isBgServiceActive: Boolean,
    isLogPanelExpanded: Boolean,
    uiAlpha: Float,
    isParserEnabled: Boolean,
    logList: List<String>,
    logListState: LazyListState,
    coords: DomCoords,
    onLogPanelToggle: (Boolean) -> Unit,
    onUiAlphaChange: (Float) -> Unit,
    onParserToggle: () -> Unit,
    onLogClear: () -> Unit,
    onUkrnetViewReady: (WebView) -> Unit,
    onMessengerViewReady: (WebView) -> Unit,
    onUkrnetReload: () -> Unit,
    onMessengerReload: () -> Unit,
    ukrnetInterface: UkrnetJsInterface,
    messengerInterface: MessengerJsInterface,
    coroutineScope: CoroutineScope,
    log: (String) -> Unit
) {
    Scaffold(containerColor = Color(0xFF130E19), modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF130E19))
        ) {
            WebViewLayer(
                isBgServiceActive = isBgServiceActive,
                uiAlpha = uiAlpha,
                ukrnetInterface = ukrnetInterface,
                messengerInterface = messengerInterface,
                onUkrnetViewReady = onUkrnetViewReady,
                onMessengerViewReady = onMessengerViewReady,
                log = log
            )

            LogPanel(
                isExpanded = isLogPanelExpanded,
                onToggle = onLogPanelToggle,
                logList = logList,
                logListState = logListState,
                onClear = onLogClear,
                isBgServiceActive = isBgServiceActive,
                uiAlpha = uiAlpha,
                onUiAlphaChange = onUiAlphaChange,
                isParserEnabled = isParserEnabled,
                onParserToggle = onParserToggle,
                coords = coords,
                onUkrnetReload = onUkrnetReload,
                onMessengerReload = onMessengerReload,
                coroutineScope = coroutineScope,
                log = log
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebViewLayer(
    isBgServiceActive: Boolean,
    uiAlpha: Float,
    ukrnetInterface: UkrnetJsInterface,
    messengerInterface: MessengerJsInterface,
    onUkrnetViewReady: (WebView) -> Unit,
    onMessengerViewReady: (WebView) -> Unit,
    log: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ukrnetFilePathCallback by remember { mutableStateOf<android.webkit.ValueCallback<Array<Uri>>?>(null) }
    var messengerFilePathCallback by remember { mutableStateOf<android.webkit.ValueCallback<Array<Uri>>?>(null) }

    val messengerFileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        val clipData = result.data?.clipData
        val selectedUris = mutableListOf<Uri>()
        if (uri != null) {
            selectedUris.add(uri)
        } else if (clipData != null) {
            for (i in 0 until clipData.itemCount) {
                selectedUris.add(clipData.getItemAt(i).uri)
            }
        }
        if (selectedUris.isNotEmpty()) {
            messengerFilePathCallback?.onReceiveValue(selectedUris.toTypedArray())
        } else {
            messengerFilePathCallback?.onReceiveValue(null)
        }
        messengerFilePathCallback = null
    }
    
    var ukrnetWebViewInstance by remember { mutableStateOf<WebView?>(null) }
    var messengerWebViewInstance by remember { mutableStateOf<WebView?>(null) }
    
    val ukrnetFileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        val clipData = result.data?.clipData
        val selectedUris = mutableListOf<Uri>()
        if (uri != null) {
            selectedUris.add(uri)
        } else if (clipData != null) {
            for (i in 0 until clipData.itemCount) {
                selectedUris.add(clipData.getItemAt(i).uri)
            }
        }
        
        if (selectedUris.isNotEmpty()) {
            val finalUris = mutableListOf<Uri>()
            val mediaKey = messengerInterface.pendingMediaKey
            for (originalUri in selectedUris) {
                val processedUri = if (mediaKey.isNotEmpty()) {
                    createEncryptedStealthCopy(context, originalUri, mediaKey)
                } else {
                    createStealthCopy(context, originalUri)
                }
                if (processedUri != null) {
                    finalUris.add(processedUri)
                }
            }
            messengerInterface.pendingMediaKey = ""
            if (finalUris.isNotEmpty()) {
                ukrnetFilePathCallback?.onReceiveValue(finalUris.toTypedArray())
                log("[Stealth] Все выбранные медиафайлы зашифрованы AES-GCM-256 и переданы.")
            } else {
                ukrnetFilePathCallback?.onReceiveValue(null)
            }
            
            val firstUri = selectedUris.first()
            val isVideo = context.contentResolver.getType(firstUri)?.startsWith("video") == true
            val typeStr = if (isVideo) "video" else "photo"
            messengerWebViewInstance?.evaluateJavascript("if(window.nan0gram && window.nan0gram.submitStealthFile) window.nan0gram.submitStealthFile('$typeStr');", null)
            
            scope.launch(Dispatchers.IO) {
                val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                val chatData = JSONObject().apply {
                    put("time", timeStr)
                    if (isVideo) {
                        val b64 = uriToBase64(context, firstUri)
                        if (b64.isNotEmpty()) {
                            put("base64", b64)
                            put("isVideo", true)
                            val thumbB64 = getVideoThumbnailBase64(context, firstUri)
                            if (thumbB64.isNotEmpty()) {
                                put("videoThumbnail", "data:image/jpeg;base64,$thumbB64")
                            }
                        }
                    } else {
                        val b64List = org.json.JSONArray()
                        for (imgUri in selectedUris) {
                            val b64 = uriToBase64(context, imgUri)
                            if (b64.isNotEmpty()) {
                                b64List.put(b64)
                            }
                        }
                        put("base64s", b64List)
                    }
                }
                val escaped = chatData.toString().replace("\\", "\\\\").replace("\"", "\\\"")
                withContext(Dispatchers.Main) {
                    messengerWebViewInstance?.evaluateJavascript(
                        "window.dispatchEvent(new CustomEvent('nan0gram:local-media-sent', { detail: \"$escaped\" }));", null
                    )
                }
            }
        } else {
            ukrnetFilePathCallback?.onReceiveValue(null)
            messengerWebViewInstance?.evaluateJavascript("window._n0gStealthPending = false;", null)
        }
        ukrnetFilePathCallback = null
        
        ukrnetWebViewInstance?.isFocusable = false
        ukrnetWebViewInstance?.isFocusableInTouchMode = false
        messengerWebViewInstance?.bringToFront()
        messengerWebViewInstance?.requestFocus()

        ukrnetWebViewInstance.forceSendMsg(log, "file chooser result")
    }

    AndroidView(
        factory = { ctx ->
            FrameLayout(ctx).apply {
                val uWebView = WebView(ctx).apply {
                    tag = "ukrnet"
                    settings.apply {
                        javaScriptEnabled    = true
                        domStorageEnabled    = true
                        databaseEnabled      = true
                        useWideViewPort      = true
                        loadWithOverviewMode = true
                        allowFileAccess      = true
                        allowContentAccess   = true
                        javaScriptCanOpenWindowsAutomatically = true
                        userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    }
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    addJavascriptInterface(ukrnetInterface, "Android")
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            if (url.isSendMsgUrl()) {
                                view?.evaluateJavascript(SENDMSG_FILL_JS, null)
                                log("[Compose] sendmsg загружен — заполняем поля")
                                val bufferedBody = messengerInterface.lastComposeBody
                                if (bufferedBody.isNotEmpty()) {
                                    // ПУЛЕНЕПРОБИВАЕМОЕ ЭКРАНИРОВАНИЕ
                                    val esc = bufferedBody
                                        .replace("\\", "\\\\")
                                        .replace("'", "\\'")
                                        .replace("\"", "\\\"")
                                        .replace("\n", "\\n")
                                        .replace("\r", "")
                                        .replace("\u2028", "")
                                        .replace("\u2029", "")
                                        
                                    val js = """
                                        (function(text) {
                                            var el = document.querySelector("${UkrnetSelectors.BODY_AREA}")
                                                || document.querySelector("${UkrnetSelectors.BODY_AREA_FALLBACK_EDITABLE}")
                                                || document.querySelector("${UkrnetSelectors.BODY_AREA_FALLBACK_NAME}")
                                                || document.querySelector("${UkrnetSelectors.BODY_AREA_FALLBACK_TAG}");
                                            if (!el) return;
                                            el.innerHTML = '';
                                            if (el.getAttribute('contenteditable') === 'true') { el.innerText = text; }
                                            else { try { Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value').set.call(el,text); } catch(e) { el.value=text; } }
                                            el.dispatchEvent(new Event('input',{bubbles:true}));
                                            el.dispatchEvent(new Event('change',{bubbles:true}));
                                        })('$esc');
                                    """.trimIndent()
                                    view?.evaluateJavascript(js, null)
                                    log("[Compose] Восстановлен текст из буфера быстрого ввода")
                                }
                                return
                            }

                            view.forceSendMsg(log, "onPageFinished")
                        }
                        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                            super.doUpdateVisitedHistory(view, url, isReload)
                            if (url != null) ukrnetInterface.onUrlChange(url)
                            view.forceSendMsg(log, "history")
                        }
                        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                            handler?.cancel()
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(m: ConsoleMessage?): Boolean {
                            val level = m?.messageLevel()?.name ?: "LOG"
                            log("[Ukrnet JS] [$level] ${m?.message()} (${m?.lineNumber()})")
                            return true
                        }
                        
                        override fun onShowFileChooser(
                            webView: WebView?,
                            filePathCallbackParams: android.webkit.ValueCallback<Array<Uri>>?,
                            fileChooserParams: FileChooserParams?
                        ): Boolean {
                            if (ukrnetFilePathCallback != null) {
                                log("[Stealth] Блокировка двойного вызова FileChooser")
                                filePathCallbackParams?.onReceiveValue(null)
                                return true
                            }
                            ukrnetFilePathCallback = filePathCallbackParams
                            try {
                                val intent = android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
                                    addCategory(android.content.Intent.CATEGORY_OPENABLE)
                                    type = "image/*"
                                    putExtra(android.content.Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
                                    putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, true)
                                }
                                ukrnetFileChooserLauncher.launch(intent)
                            } catch (e: Exception) {
                                ukrnetFilePathCallback?.onReceiveValue(null)
                                ukrnetFilePathCallback = null
                                messengerWebViewInstance?.evaluateJavascript("window._n0gStealthPending = false;", null)
                                webView.forceSendMsg(log, "file chooser exception")
                                return false
                            }
                            return true
                        }
                    }
                    loadUrl("https://mail.ukr.net/desktop/login")
                }
                onUkrnetViewReady(uWebView)
                ukrnetWebViewInstance = uWebView
                addView(uWebView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

                val mWebView = WebView(ctx).apply {
                    tag = "messenger"
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    settings.apply {
                        javaScriptEnabled  = true
                        domStorageEnabled  = true
                        databaseEnabled    = true
                        allowFileAccess    = true
                        allowContentAccess = true
                        mediaPlaybackRequiresUserGesture = false
                        }
                    addJavascriptInterface(messengerInterface, "Android")
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            view?.evaluateJavascript("""
                                (function(){
                                    if(window._n0gDirectPickerPatch)return;
                                    window._n0gDirectPickerPatch=true;
                                    
                                    var _attachDebounce = false;
                                    function triggerStealthAttach() {
                                        if (_attachDebounce) return;
                                        _attachDebounce = true;
                                        setTimeout(function(){ _attachDebounce = false; }, 2000);
                                        
                                        if (window.Android && window.Android.prepareForDirectAttach) {
                                            window.Android.prepareForDirectAttach();
                                        }
                                    }
                                    
                                    document.addEventListener('touchstart', function(e) {
                                        var t = e.target.closest('.input-icon');
                                        if (t) {
                                            window._n0gStealthPending = true;
                                            triggerStealthAttach();
                                        }
                                    }, true);

                                    document.addEventListener('mousedown', function(e) {
                                        var t = e.target.closest('.input-icon');
                                        if (t) {
                                            window._n0gStealthPending = true;
                                            triggerStealthAttach();
                                        }
                                    }, true);

                                    document.addEventListener('click', function(e) {
                                        var t = e.target.closest('.input-icon');
                                        if (t) {
                                            e.preventDefault();
                                            e.stopPropagation();
                                        }
                                    }, true);
                                })();
                            """.trimIndent(), null)
                            
                            view?.evaluateJavascript("""
                                (function polyfillNan0gramFn(){
                                    if(window.nan0gram){
                                        if(!window.nan0gram._openComposeIfNeeded){
                                            window.nan0gram._openComposeIfNeeded=function(){};
                                            console.log('[Polyfill] _openComposeIfNeeded injected');
                                        }
                                    } else {
                                        setTimeout(polyfillNan0gramFn, 300);
                                    }
                                })();
                            """.trimIndent(), null)
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(m: ConsoleMessage?): Boolean {
                            val level = m?.messageLevel()?.name ?: "LOG"
                            log("[Local JS] [$level] ${m?.message()} (${m?.lineNumber()})")
                            return true
                        }
                        override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
                            request?.grant(request.resources)
                        }
                        override fun onShowFileChooser(
                            webView: WebView?,
                            filePathCallbackParams: android.webkit.ValueCallback<Array<Uri>>?,
                            fileChooserParams: FileChooserParams?
                        ): Boolean {
                            if (messengerFilePathCallback != null) {
                                filePathCallbackParams?.onReceiveValue(null)
                                return true
                            }
                            messengerFilePathCallback = filePathCallbackParams
                            try {
                                val intent = android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
                                    addCategory(android.content.Intent.CATEGORY_OPENABLE)
                                    type = "image/*"
                                }
                                messengerFileChooserLauncher.launch(intent)
                            } catch (e: Exception) {
                                messengerFilePathCallback?.onReceiveValue(null)
                                messengerFilePathCallback = null
                                return false
                            }
                            return true
                        }
                    }
                    loadUrl("file:///android_asset/index.html")
                }
                onMessengerViewReady(mWebView)
                messengerWebViewInstance = mWebView
                messengerInterface.getMessengerWebView = { mWebView }
                addView(mWebView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            }
        },
        update = { frameLayout ->
            val ukrV  = frameLayout.findViewWithTag<WebView>("ukrnet")
            val messV = frameLayout.findViewWithTag<WebView>("messenger")
            if (isBgServiceActive) {
                ukrV?.isFocusable = true
                ukrV?.isFocusableInTouchMode = true
                messV?.visibility = View.GONE
                ukrV?.visibility  = View.VISIBLE
                ukrV?.bringToFront()
            } else {
                ukrV?.isFocusable = false
                ukrV?.isFocusableInTouchMode = false
                ukrV?.visibility  = View.VISIBLE
                messV?.visibility = View.VISIBLE
                messV?.alpha      = uiAlpha
                messV?.bringToFront()
                messV?.requestFocus()
            }
        },
        modifier = Modifier.fillMaxSize().zIndex(0f)
    )
}

@Composable
private fun LogPanel(
    isExpanded: Boolean, onToggle: (Boolean) -> Unit, logList: List<String>, logListState: LazyListState,
    onClear: () -> Unit, isBgServiceActive: Boolean, uiAlpha: Float, onUiAlphaChange: (Float) -> Unit,
    isParserEnabled: Boolean, onParserToggle: () -> Unit, coords: DomCoords, onUkrnetReload: () -> Unit,
    onMessengerReload: () -> Unit, coroutineScope: CoroutineScope, log: (String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var dragX by remember { mutableStateOf(0f) }
    var dragY by remember { mutableStateOf(0f) }

    Box(modifier = Modifier.fillMaxSize().zIndex(2f)) {
        if (!isExpanded) {
            Box(
                modifier = Modifier.padding(16.dp).align(Alignment.TopEnd).offset { IntOffset(dragX.roundToInt(), dragY.roundToInt()) }.background(Color(0xEE1C1524), shape = RoundedCornerShape(8.dp)).border(1.dp, Color(0xFFA773D1), shape = RoundedCornerShape(8.dp))
                    .pointerInput(Unit) { detectDragGesturesAfterLongPress { _, dragAmount -> dragX += dragAmount.x; dragY += dragAmount.y } }.clickable { onToggle(true) }.padding(horizontal = 12.dp, vertical = 8.dp)
                ) { Text("🐞 Логи (${logList.size})", color = Color(0xFFD0BCFF), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        } else {
            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.55f).align(Alignment.TopCenter).background(Color(0xF90F0A15)).border(1.dp, Color(0xFFA773D1)).padding(6.dp)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("nan0gram логи", color = Color(0xFFA773D1), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = onMessengerReload) { Text("🔄 Mess", color = Color(0xFFD0BCFF), fontSize = 10.sp) }
                            TextButton(onClick = onUkrnetReload)    { Text("🔄 Ukr",  color = Color(0xFFD0BCFF), fontSize = 10.sp) }
                            TextButton(onClick = { clipboardManager.setText(AnnotatedString(logList.joinToString("\n"))) }) { Text("📋", color = Color(0xFFC2FFD9), fontSize = 10.sp) }
                            TextButton(onClick = onClear)           { Text("❌", color = Color(0xFFEFB8C8), fontSize = 10.sp) }
                            TextButton(onClick = { onToggle(false) }) { Text("➖", color = Color(0xFFCCC2DC), fontSize = 10.sp) }
                        }
                    }
                    if (!isBgServiceActive) {
                        DebugControls(uiAlpha, onUiAlphaChange, isParserEnabled, onParserToggle, coords, coroutineScope, log)
                    }
                    LazyColumn(state = logListState, modifier = Modifier.fillMaxWidth().weight(1f).background(Color(0xFF07040A)).padding(4.dp)) {
                        items(logList) { line -> Text(text = line, color = Color(0xFFC2FFD9), fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 12.sp, modifier = Modifier.padding(bottom = 2.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugControls(
    uiAlpha: Float, onUiAlphaChange: (Float) -> Unit, isParserEnabled: Boolean, onParserToggle: () -> Unit,
    coords: DomCoords, coroutineScope: CoroutineScope, log: (String) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(36.dp)) {
        Text("Видимость: ${(uiAlpha * 100).toInt()}%", color = Color(0xFFE0C3FC), fontSize = 11.sp, modifier = Modifier.width(130.dp))
        Slider(value = uiAlpha, onValueChange = onUiAlphaChange, valueRange = 0f..1f, modifier = Modifier.weight(1f))
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(30.dp)) {
        Text("Радар входящих:", color = Color(0xFFE0C3FC), fontSize = 11.sp)
        TextButton(
            onClick = onParserToggle,
            modifier = Modifier.background(if (isParserEnabled) Color(0x334CAF50) else Color(0x33F44336), shape = RoundedCornerShape(4.dp))
        ) {
            Text(text = if (isParserEnabled) "🎧 Радар: ВКЛ" else "🔇 Радар: ВЫКЛ", color = if (isParserEnabled) Color(0xFF81C784) else Color(0xFFE57373), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
    if (coords.composeX != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp).height(30.dp)
        ) {
            Text("Кнопка 'Написать' найдена ✓", color = Color(0xFF81C784), fontSize = 11.sp)
        }
    }
}
