package com.example

import android.annotation.SuppressLint
import android.net.Uri
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.ValueCallback
import android.webkit.WebView
import android.widget.FrameLayout
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.fillMaxSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import androidx.webkit.WebViewAssetLoader

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun WebViewLayer(
    isBgServiceActive: Boolean,
    uiAlpha: Float,
    ukrnetInterface: UkrnetJsInterface,
    messengerInterface: MessengerJsInterface,
    mediaManager: MediaManager,
    onUkrnetViewReady: (WebView) -> Unit,
    onMessengerViewReady: (WebView) -> Unit,
    log: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var ukrnetFilePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var messengerFilePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    
    var ukrnetWebViewInstance by remember { mutableStateOf<WebView?>(null) }
    var messengerWebViewInstance by remember { mutableStateOf<WebView?>(null) }

    val webAssetsDir = remember { File(context.filesDir, "web_assets") }
    val assetLoader = remember(webAssetsDir) {
        WebViewAssetLoader.Builder()
            .setDomain("appassets.androidlocal")
            .addPathHandler("/media/", WebViewAssetLoader.InternalStoragePathHandler(context, mediaManager.getMediaDir()))
            .addPathHandler("/assets/", FallbackAssetsPathHandler(context, webAssetsDir))
            .build()
    }

    val messengerFileChooserLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        val clipData = result.data?.clipData
        val rawUris = mutableListOf<Uri>()
        if (uri != null) rawUris.add(uri) else if (clipData != null) {
            for (i in 0 until clipData.itemCount) rawUris.add(clipData.getItemAt(i).uri)
        }
        
        if (rawUris.isNotEmpty()) {
            if (messengerInterface.isWallpaperPending) {
                messengerInterface.isWallpaperPending = false
                messengerInterface.isWallpaperJustSelected = true
                messengerFilePathCallback?.onReceiveValue(rawUris.toTypedArray())
                messengerFilePathCallback = null
                return@rememberLauncherForActivityResult
            }
            processUrisWithLimits(
                context = context,
                rawUris = rawUris,
                messengerInterface = messengerInterface,
                messengerWebView = messengerWebViewInstance
            ) { validUris ->
                if (validUris.isNotEmpty()) {
                    val firstUri = validUris.first()
                    val finalUris = mutableListOf<Uri>()
                    val mediaKey = messengerInterface.pendingMediaKey
                    for (originalUri in validUris) {
                        val processedUri = if (mediaKey.isNotEmpty()) {
                            createEncryptedStealthCopy(context, originalUri, mediaKey)
                        } else {
                            createStealthCopy(context, originalUri)
                        }
                        if (processedUri != null) finalUris.add(processedUri)
                    }
                    messengerInterface.pendingMediaKey = ""
                    
                    if (finalUris.isNotEmpty()) {
                        ukrnetFilePathCallback?.onReceiveValue(finalUris.toTypedArray())
                        log("[Stealth] Файлы прошли проверку лимитов, зашифрованы и переданы.")
                    } else {
                        ukrnetFilePathCallback?.onReceiveValue(null)
                    }
                    ukrnetFilePathCallback = null
                    
                    val typeStr = if (messengerInterface.pendingStealthMode == "file") "file" else {
                        val isVideo = context.contentResolver.getType(firstUri)?.startsWith("video") == true
                        if (isVideo) "video" else "photo"
                    }
                    messengerWebViewInstance?.post { messengerWebViewInstance?.evaluateJavascript("if(window.nan0gram && window.nan0gram.submitStealthFile) window.nan0gram.submitStealthFile('$typeStr');", null) }
                    
                    scope.launch(Dispatchers.IO) {
                        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                        val chatData = JSONObject().apply {
                            put("time", timeStr)
                            if (messengerInterface.pendingStealthMode == "file") {
                                put("isFile", true)
                                put("fileName", getOriginalFileName(context, firstUri))
                                put("fileSize", getUriSize(context, firstUri))
                            } else {
                                val isVideo = context.contentResolver.getType(firstUri)?.startsWith("video") == true
                                if (isVideo) {
                                    put("isVideo", true)
                                    val b64List = org.json.JSONArray()
                                    val thumbList = org.json.JSONArray()
                                    for (vidUri in validUris) {
                                        val b64 = uriToBase64(context, vidUri)
                                        if (b64.isNotEmpty()) {
                                            b64List.put(b64)
                                            val thumbB64 = getVideoThumbnailBase64(context, vidUri)
                                            if (thumbB64.isNotEmpty()) thumbList.put("data:image/jpeg;base64,$thumbB64")
                                        }
                                    }
                                    put("base64s", b64List)
                                    put("thumbnails", thumbList)
                                } else {
                                    val b64List = org.json.JSONArray()
                                    for (imgUri in validUris) {
                                        val b64 = uriToBase64(context, imgUri)
                                        if (b64.isNotEmpty()) b64List.put(b64)
                                    }
                                    put("base64s", b64List)
                                }
                            }
                        }
                        val escaped = chatData.toString().replace("\\", "\\\\").replace("\"", "\\\"")
                        withContext(Dispatchers.Main) {
                            val jsDispatch = "window.dispatchEvent(new CustomEvent('nan0gram:local-media-sent', { detail: \"$escaped\" }));"
                            messengerWebViewInstance?.evaluateJavascript(jsDispatch, null)
                        }
                    }
                    
                    messengerFilePathCallback?.onReceiveValue(validUris.toTypedArray())
                    messengerFilePathCallback = null
                } else {
                    ukrnetFilePathCallback?.onReceiveValue(null)
                    ukrnetFilePathCallback = null
                    messengerFilePathCallback?.onReceiveValue(null)
                    messengerFilePathCallback = null
                    messengerWebViewInstance?.post { messengerWebViewInstance?.evaluateJavascript("window._n0gStealthPending = false;", null) }
                }
                ukrnetWebViewInstance?.post {
                    ukrnetWebViewInstance?.isFocusable = false
                    ukrnetWebViewInstance?.isFocusableInTouchMode = false
                    messengerWebViewInstance?.bringToFront()
                    messengerWebViewInstance?.requestFocus()
                    ukrnetWebViewInstance?.forceSendMsg(log, "file chooser result")
                }
            }
        } else {
            ukrnetFilePathCallback?.onReceiveValue(null)
            ukrnetFilePathCallback = null
            messengerFilePathCallback?.onReceiveValue(null)
            messengerFilePathCallback = null
            messengerWebViewInstance?.post { messengerWebViewInstance?.evaluateJavascript("window._n0gStealthPending = false;", null) }
            ukrnetWebViewInstance?.post {
                ukrnetWebViewInstance?.isFocusable = false
                ukrnetWebViewInstance?.isFocusableInTouchMode = false
                messengerWebViewInstance?.bringToFront()
                messengerWebViewInstance?.requestFocus()
                ukrnetWebViewInstance?.forceSendMsg(log, "file chooser result")
            }
        }
    }

    val ukrnetFileChooserLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        val clipData = result.data?.clipData
        val rawUris = mutableListOf<Uri>()
        if (uri != null) rawUris.add(uri) else if (clipData != null) {
            for (i in 0 until clipData.itemCount) rawUris.add(clipData.getItemAt(i).uri)
        }
        
        if (rawUris.isNotEmpty()) {
            processUrisWithLimits(
                context = context,
                rawUris = rawUris,
                messengerInterface = messengerInterface,
                messengerWebView = messengerWebViewInstance
            ) { validUris ->
                if (validUris.isNotEmpty()) {
                    val finalUris = mutableListOf<Uri>()
                    val mediaKey = messengerInterface.pendingMediaKey
                    for (originalUri in validUris) {
                        val processedUri = if (mediaKey.isNotEmpty()) {
                            createEncryptedStealthCopy(context, originalUri, mediaKey)
                        } else {
                            createStealthCopy(context, originalUri)
                        }
                        if (processedUri != null) finalUris.add(processedUri)
                    }
                    messengerInterface.pendingMediaKey = ""
                    
                    if (finalUris.isNotEmpty()) {
                        ukrnetFilePathCallback?.onReceiveValue(finalUris.toTypedArray())
                        log("[Stealth] Все медиафайлы зашифрованы AES-GCM-256 (с оригинальными именами) и переданы.")
                    } else {
                        ukrnetFilePathCallback?.onReceiveValue(null)
                    }
                    
                    val firstUri = validUris.first()
                    val typeStr = if (messengerInterface.pendingStealthMode == "file") "file" else {
                        val isVideo = context.contentResolver.getType(firstUri)?.startsWith("video") == true
                        if (isVideo) "video" else "photo"
                    }
                    messengerWebViewInstance?.evaluateJavascript("if(window.nan0gram && window.nan0gram.submitStealthFile) window.nan0gram.submitStealthFile('$typeStr');", null)
                    
                    scope.launch(Dispatchers.IO) {
                        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                        val chatData = JSONObject().apply {
                            put("time", timeStr)
                            if (messengerInterface.pendingStealthMode == "file") {
                                put("isFile", true)
                                put("fileName", getOriginalFileName(context, firstUri))
                                put("fileSize", getUriSize(context, firstUri))
                            } else {
                                val isVideo = context.contentResolver.getType(firstUri)?.startsWith("video") == true
                                if (isVideo) {
                                    put("isVideo", true)
                                    val b64List = org.json.JSONArray()
                                    val thumbList = org.json.JSONArray()
                                    for (vidUri in validUris) {
                                        val b64 = uriToBase64(context, vidUri)
                                        if (b64.isNotEmpty()) {
                                            b64List.put(b64)
                                            val thumbB64 = getVideoThumbnailBase64(context, vidUri)
                                            if (thumbB64.isNotEmpty()) thumbList.put("data:image/jpeg;base64,$thumbB64")
                                        }
                                    }
                                    put("base64s", b64List)
                                    put("thumbnails", thumbList)
                                } else {
                                    val b64List = org.json.JSONArray()
                                    for (imgUri in validUris) {
                                        val b64 = uriToBase64(context, imgUri)
                                        if (b64.isNotEmpty()) b64List.put(b64)
                                    }
                                    put("base64s", b64List)
                                }
                            }
                        }
                        val escaped = chatData.toString().replace("\\", "\\\\").replace("\"", "\\\"")
                        withContext(Dispatchers.Main) {
                            val jsDispatch = "window.dispatchEvent(new CustomEvent('nan0gram:local-media-sent', { detail: \"$escaped\" }));"
                            messengerWebViewInstance?.evaluateJavascript(jsDispatch, null)
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
                ukrnetWebViewInstance?.forceSendMsg(log, "file chooser result")
            }
        } else {
            ukrnetFilePathCallback?.onReceiveValue(null)
            ukrnetFilePathCallback = null
            messengerWebViewInstance?.evaluateJavascript("window._n0gStealthPending = false;", null)
            ukrnetWebViewInstance?.isFocusable = false
            ukrnetWebViewInstance?.isFocusableInTouchMode = false
            messengerWebViewInstance?.bringToFront()
            messengerWebViewInstance?.requestFocus()
            ukrnetWebViewInstance?.forceSendMsg(log, "file chooser result")
        }
    }

    AndroidView(
        factory = { ctx ->
            try {
                val layout = FrameLayout(ctx)
                
                val uWebView = buildUkrnetWebView(
                    ctx = ctx,
                    ukrnetInterface = ukrnetInterface,
                    messengerInterface = messengerInterface,
                    getUkrnetFilePathCallback = { ukrnetFilePathCallback },
                    setUkrnetFilePathCallback = { ukrnetFilePathCallback = it },
                    onShowChooser = { _ ->
                        try {
                            if (messengerInterface.pendingStealthMode == "file") {
                                val intent = android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
                                    addCategory(android.content.Intent.CATEGORY_OPENABLE)
                                    type = "*/*"
                                    putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, false)
                                }
                                ukrnetFileChooserLauncher.launch(intent)
                            } else {
                                val intent = android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
                                    addCategory(android.content.Intent.CATEGORY_OPENABLE)
                                    type = "*/*"
                                    putExtra(android.content.Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
                                    putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, true)
                                }
                                ukrnetFileChooserLauncher.launch(intent)
                            }
                        } catch (e: Exception) {
                            ukrnetFilePathCallback?.onReceiveValue(null)
                            ukrnetFilePathCallback = null
                            messengerWebViewInstance?.evaluateJavascript("window._n0gStealthPending = false;", null)
                        }
                    },
                    log = log
                )
                onUkrnetViewReady(uWebView)
                ukrnetWebViewInstance = uWebView
                layout.addView(uWebView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

                val mWebView = buildMessengerWebView(
                    ctx = ctx,
                    messengerInterface = messengerInterface,
                    assetLoader = assetLoader,
                    getMessengerFilePathCallback = { messengerFilePathCallback },
                    setMessengerFilePathCallback = { messengerFilePathCallback = it },
                    onShowChooser = { _ ->
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
                                addCategory(android.content.Intent.CATEGORY_OPENABLE)
                                type = "image/*"
                            }
                            messengerFileChooserLauncher.launch(intent)
                        } catch (e: Exception) {
                            messengerFilePathCallback?.onReceiveValue(null)
                            messengerFilePathCallback = null
                        }
                    },
                    log = log
                )
                onMessengerViewReady(mWebView)
                messengerWebViewInstance = mWebView
                messengerInterface.getMessengerWebView = { mWebView }
                layout.addView(mWebView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

                layout
            } catch (e: Exception) {
                log("[❌ WebViewLayer] Error building WebViews: ${e.message}")
                FrameLayout(ctx)
            }
        },
        update = { frameLayout ->
            try {
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
            } catch (e: Exception) {
                log("[❌ WebViewLayer Update] Visibility state update failure: ${e.message}")
            }
        },
        modifier = Modifier.fillMaxSize().zIndex(0f)
    )
}