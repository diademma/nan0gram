package com.example

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

class MainActivity : ComponentActivity() {

    private var ukrnetWebView: WebView? = null
    private var messengerWebView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            var isBgServiceActive by remember { mutableStateOf(true) } // Start with true so user can log in to UkrNet first
            var loginStatusMsg by remember { mutableStateOf("Ожидание авторизации...") }

            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(Color(0xFF130E19))
                ) {
                    // 1. Local Local Messenger client WebView
                    AndroidView(
                        factory = { context ->
                            WebView(context).apply {
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    allowFileAccess = true
                                }
                                addJavascriptInterface(object : Any() {
                                    @JavascriptInterface
                                    fun setBgServiceActive(active: Boolean) {
                                        runOnUiThread {
                                            isBgServiceActive = active
                                        }
                                    }

                                    @JavascriptInterface
                                    fun postMessage(type: String, key: String, value: String) {
                                        runOnUiThread {
                                            if (type == "ui" && key == "login_success" && value == "true") {
                                                loginStatusMsg = "Авторизация пройдена!"
                                                isBgServiceActive = false
                                                evaluateJavascript("if (window.onLoginSuccess) { window.onLoginSuccess(); }", null)
                                            }
                                        }
                                    }
                                }, "Android")

                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        return false
                                    }
                                }
                                loadUrl("file:///android_asset/index.html")
                                messengerWebView = this
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // 2. Background UkrNet WebView (hides in 1x1 or shown on top based on isBgServiceActive)
                    AndroidView(
                        factory = { context ->
                            WebView(context).apply {
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    // Set standard mobile UserAgent to force the touch layout
                                    userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                                }

                                // Enable cookies
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                                addJavascriptInterface(object : Any() {
                                    @JavascriptInterface
                                    fun postMessage(type: String, key: String, value: String) {
                                        runOnUiThread {
                                            if (type == "ui" && key == "login_success" && value == "true") {
                                                loginStatusMsg = "Авторизация пройдена!"
                                                isBgServiceActive = false
                                                // Inform local client
                                                messengerWebView?.evaluateJavascript("if (window.onLoginSuccess) { window.onLoginSuccess(); }", null)
                                            }
                                        }
                                    }
                                }, "Android")

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        // Inject automated monitoring script to detect successful login
                                        // Checks DOM for div[role="main"] once every 1.5s
                                        val monitoringJs = """
                                            (function() {
                                                if (window.nan0gramBridgeInjected) return;
                                                window.nan0gramBridgeInjected = true;
                                                console.log("nan0gram bridge injected into UkrNet");
                                                setInterval(function() {
                                                    var mainElement = document.querySelector('div[role="main"]') || document.querySelector('.app__content') || document.querySelector('.sendmsg');
                                                    if (mainElement) {
                                                        console.log("nan0gram detected success login view!");
                                                        if (window.Android && window.Android.postMessage) {
                                                            window.Android.postMessage("ui", "login_success", "true");
                                                        }
                                                    }
                                                }, 1500);
                                            })();
                                        """.trimIndent()
                                        evaluateJavascript(monitoringJs, null)
                                    }
                                }
                                webChromeClient = WebChromeClient()
                                loadUrl("https://mail.ukr.net/desktop/login") // Can also use mobile touch entrypoint
                                ukrnetWebView = this
                            }
                        },
                        modifier = if (isBgServiceActive) {
                            Modifier.fillMaxSize()
                        } else {
                            Modifier.size(1.dp) // Hidden size: 1x1 as requested
                        }
                    )

                    // Minimal aesthetic indicator overlay when background service is visible
                    if (isBgServiceActive) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xE01C1524))
                                .padding(12.dp)
                                .align(Alignment.BottomCenter)
                        ) {
                            Text(
                                text = "nan0gram: $loginStatusMsg",
                                color = Color(0xFFA773D1),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
            }
        }
    }
}
