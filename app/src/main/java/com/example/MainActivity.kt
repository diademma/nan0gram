package com.example

import android.annotation.SuppressLint
import android.os.Bundle
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
        super.onCreate(savedInstanceState)
        log("[System] nan0gram старт, сборка #${BuildConfig.VERSION_CODE}")
        enableEdgeToEdge()

        setContent {

            // ── Проверка обновлений ────────────────────────────────────────
            // Сравниваем номер сборки (build-42 → 42) с текущим VERSION_CODE
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

            // ── Верхнеуровневый state ──────────────────────────────────────
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
                    onLoginSuccess      = { hasHandledLogin = true; isBgServiceActive = false },
                    onCoordsUpdate      = { coords = it },
                    onFirstCoordsLogged = { },
                    getMessengerWebView = { messengerWebView },
                    isLoginHandled      = { hasHandledLogin },
                    getCurrentCoords    = { coords }
                )
            }

            val messengerInterface = remember {
                MessengerJsInterface(
                    log               = ::log,
                    onBgServiceChange = { isBgServiceActive = it },
                    getUkrnetWebView  = { ukrnetWebView },
                    getCoords         = { coords }
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
