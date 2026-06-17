package com.example

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.StrictMode
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.rememberLazyListState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val logList = mutableStateListOf<String>()

    fun log(message: String) {
        runOnUiThread {
            val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            logList.add("[$ts] $message")
            if (logList.size > 300) logList.removeAt(0)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {

        // ── StrictMode: ловим медленные операции на главном потоке ──────
        // Все нарушения видны в логах (тег StrictMode) — не влияет на работу
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()       // диск, сеть, медленные вызовы
                    .penaltyLog()      // пишем в лог, не крашим
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .detectActivityLeaks()
                    .penaltyLog()
                    .build()
            )
        }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {

            // Запрашиваем доступ к микрофону при старте
            val micPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                log(if (isGranted) "[System] Доступ к микрофону разрешен ✓" else "[System] Доступ к микрофону отклонен ❌")
            }
            LaunchedEffect(Unit) {
                micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            }

            var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
            LaunchedEffect(Unit) {
                val info = UpdateChecker.checkForUpdate(BuildConfig.VERSION_CODE)
                if (info != null && info.isUpdateAvailable) {
                    updateInfo = info
                }
            }
            updateInfo?.let { info ->
                UpdateDialog(updateInfo = info, onDismiss = { updateInfo = null })
            }

            var isBgServiceActive  by remember { mutableStateOf(true) }
            var hasHandledLogin    by remember { mutableStateOf(false) }
            var isLogPanelExpanded by remember { mutableStateOf(false) }
            var uiAlpha            by remember { mutableStateOf(0.95f) }
            var isParserEnabled    by remember { mutableStateOf(false) }

            var ukrnetWebView    by remember { mutableStateOf<WebView?>(null) }
            var messengerWebView by remember { mutableStateOf<WebView?>(null) }

            var coords by remember { mutableStateOf(DomCoords()) }

            val coroutineScope = rememberCoroutineScope()
            val logListState   = rememberLazyListState()

            LaunchedEffect(logList.size) {
                if (logList.isNotEmpty() && isLogPanelExpanded)
                    logListState.animateScrollToItem(logList.size - 1)
            }

            val ukrnetInterface = remember {
                UkrnetJsInterface(
                    log                 = ::log,
                    onLoginSuccess      = {
                  hasHandledLogin = true
                  isBgServiceActive = false
                  // Навигация к форме отправки — мгновенно, без кликов
                  android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                      ukrnetWebView?.loadUrl("https://mail.ukr.net/touch/u0/sendmsg/")
                  }, 600)
              },
                    onCoordsUpdate      = { coords = it },
                    onFirstCoordsLogged = { },
                    getMessengerWebView = { messengerWebView },
                    isLoginHandled      = { hasHandledLogin },
                    getCurrentCoords    = { coords }
                )
            }

            // Считываем системный ANDROID_ID (SSAID) для уникального отпечатка
            val context = androidx.compose.ui.platform.LocalContext.current
            val androidId = remember {
                android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                ) ?: "unknown_device"
            }

            val messengerInterface = remember {
                MessengerJsInterface(
                    log               = ::log,
                    onBgServiceChange = { isBgServiceActive = it },
                    getUkrnetWebView  = { ukrnetWebView },
                    getCoords         = { coords },
                    androidId         = androidId  // Передаем ID в мост
                ).also { it.scope = coroutineScope }
            }

            BridgeEffects(
                isBgServiceActive = isBgServiceActive,
                isParserEnabled   = isParserEnabled,
                ukrnetWebView     = ukrnetWebView,
                coords            = coords
            )

            AppScreen(
                isBgServiceActive    = isBgServiceActive,
                isLogPanelExpanded   = isLogPanelExpanded,
                uiAlpha              = uiAlpha,
                isParserEnabled      = isParserEnabled,
                logList              = logList,
                logListState         = logListState,
                coords               = coords,
                onLogPanelToggle     = { isLogPanelExpanded = it },
                onUiAlphaChange      = { uiAlpha = it },
                onParserToggle       = { isParserEnabled = !isParserEnabled },
                onLogClear           = { logList.clear() },
                onUkrnetViewReady    = { ukrnetWebView = it },
                onMessengerViewReady = { messengerWebView = it },
                onUkrnetReload       = { ukrnetWebView?.reload() },
                onMessengerReload    = { messengerWebView?.reload() },
                ukrnetInterface      = ukrnetInterface,
                messengerInterface   = messengerInterface,
                coroutineScope       = coroutineScope,
                log                  = ::log
            )
        }
    }
}
