package com.example

import android.content.Context
import android.net.Uri
import android.net.http.SslError
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebChromeClient.FileChooserParams
import android.webkit.WebView
import android.webkit.WebViewClient

@Volatile internal var ukrnetRetryCounter = 0
@Volatile internal var isUkrnetRetrying = false
@Volatile internal var lastUkrnetErrorDescription = "Неизвестная ошибка сети"

internal fun buildUkrnetWebView(
    ctx: Context,
    ukrnetInterface: UkrnetJsInterface,
    messengerInterface: MessengerJsInterface,
    getUkrnetFilePathCallback: () -> ValueCallback<Array<Uri>>?,
    setUkrnetFilePathCallback: (ValueCallback<Array<Uri>>?) -> Unit,
    onShowChooser: (ValueCallback<Array<Uri>>?) -> Unit,
    log: (String) -> Unit
): WebView {
    return try {
        WebView(ctx).apply {
            tag = "ukrnet"
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                allowFileAccess = true
                allowContentAccess = true
                javaScriptCanOpenWindowsAutomatically = true
                userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            addJavascriptInterface(ukrnetInterface, "Android")
            webViewClient = object : WebViewClient() {
                private val retryIntervals = listOf(2000L, 3000L, 4000L, 5000L, 3000L)

                private fun handleNetworkError(view: WebView?, description: String?) {
                    lastUkrnetErrorDescription = description ?: "Неизвестная ошибка сети"
                    if (isUkrnetRetrying) return
                    isUkrnetRetrying = true

                    if (ukrnetRetryCounter < retryIntervals.size) {
                        val delayMs = retryIntervals[ukrnetRetryCounter]
                        log("[Ukrnet Error] Сбой подключения. Попытка ${ukrnetRetryCounter + 1} из ${retryIntervals.size} через ${delayMs / 1000} сек. Причина: $lastUkrnetErrorDescription")
                        ukrnetRetryCounter++
                        view?.postDelayed({
                            isUkrnetRetrying = false
                            log("[Ukrnet Error] Выполняем авто-перезапуск... Попытка $ukrnetRetryCounter")
                            view.reload()
                        }, delayMs)
                    } else {
                        isUkrnetRetrying = false
                        log("[Ukrnet Error] Все $ukrnetRetryCounter попыток перезапуска исчерпаны. Выводим ошибку в UI.")
                        val messengerWebView = findMessengerWebView(ctx)
                        messengerWebView?.post {
                            val esc = lastUkrnetErrorDescription.replace("'", "\\'").replace("\n", " ")
                            messengerWebView.evaluateJavascript(
                                "window.dispatchEvent(new CustomEvent('nan0gram:ukrnet-error', { detail: '$esc' }));", 
                                null
                            )
                        }
                    }
                }

                override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                    log("[Ukrnet Error] Legacy error: $errorCode - $description")
                    handleNetworkError(view, description)
                }

                override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        val desc = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                            error?.description?.toString()
                        } else {
                            "Ошибка подключения"
                        }
                        log("[Ukrnet Error] Modern error: $desc")
                        handleNetworkError(view, desc)
                    }
                }

                override fun onReceivedHttpError(view: WebView?, request: android.webkit.WebResourceRequest?, errorResponse: android.webkit.WebResourceResponse?) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    if (request?.isForMainFrame == true) {
                        val status = errorResponse?.statusCode ?: 500
                        val phrase = errorResponse?.reasonPhrase ?: "HTTP Error"
                        log("[Ukrnet Error] HTTP error: $status - $phrase")
                        handleNetworkError(view, "HTTP $status: $phrase")
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.evaluateJavascript(MONITORING_JS, null)
                    view?.evaluateJavascript(SMART_SCAN_JS, null)
                    
                    if (url != null && !url.startsWith("chrome-error") && !url.startsWith("data:") && !url.contains("error") && !isUkrnetRetrying) {
                        ukrnetRetryCounter = 0
                    }

                    if (url != null && (url.contains("login") || url.contains("accounts"))) {
                        ukrnetInterface.onLoginPageLoaded()
                        return
                    }
                    if (url.isSendMsgUrl()) {
                        view?.evaluateJavascript(SENDMSG_FILL_JS, null)
                        val bufferedBody = messengerInterface.lastComposeBody
                        if (bufferedBody.isNotEmpty()) {
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
                                    var el = document.querySelector(".sm-editor__area") || document.querySelector("[contenteditable='true']") || document.querySelector("textarea");
                                    if (!el) return;
                                    el.innerHTML = '';
                                    if (el.getAttribute('contenteditable') === 'true') { el.innerText = text; }
                                    else { try { Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value').set.call(el,text); } catch(e) { el.value=text; } }
                                    el.dispatchEvent(new Event('input',{bubbles:true})); el.dispatchEvent(new Event('change',{bubbles:true}));
                                })('$esc');
                            """.trimIndent()
                            view?.evaluateJavascript(js, null)
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
                    log("[Ukrnet JS] [${m?.messageLevel()?.name ?: "LOG"}] ${m?.message()} (${m?.lineNumber()})")
                    return true
                }
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallbackParams: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    if (messengerInterface.isVoicePending) {
                        val voiceUri = messengerInterface.pendingVoiceUri
                        if (voiceUri != null) {
                            filePathCallbackParams?.onReceiveValue(arrayOf(voiceUri))
                            log("[Stealth] Голосовое сообщение автоматически подставлено под капотом.")
                        } else {
                            filePathCallbackParams?.onReceiveValue(null)
                        }
                        messengerInterface.isVoicePending = false
                        messengerInterface.pendingVoiceUri = null
                        return true
                    }
                    if (getUkrnetFilePathCallback() != null) {
                        filePathCallbackParams?.onReceiveValue(null)
                        return true
                    }
                    setUkrnetFilePathCallback(filePathCallbackParams)
                    try {
                        onShowChooser(filePathCallbackParams)
                    } catch (e: Exception) {
                        filePathCallbackParams?.onReceiveValue(null)
                        setUkrnetFilePathCallback(null)
                        return false
                    }
                    return true
                }
            }
            loadUrl("https://mail.ukr.net/desktop/login")
        }
    } catch (e: Exception) {
        log("[❌ UkrnetWebView] ${e.message}")
        WebView(ctx)
    }
}

private fun findMessengerWebView(context: Context): WebView? {
    var currentContext = context
    while (currentContext is android.content.ContextWrapper) {
        if (currentContext is android.app.Activity) {
            val rootView = currentContext.window.decorView
            return findWebViewByTag(rootView, "messenger")
        }
        currentContext = currentContext.baseContext
    }
    return null
}

private fun findWebViewByTag(view: android.view.View, tag: String): WebView? {
    if (view is WebView && view.tag == tag) {
        return view
    }
    if (view is android.view.ViewGroup) {
        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i)
            val result = findWebViewByTag(child, tag)
            if (result != null) return result
        }
    }
    return null
}