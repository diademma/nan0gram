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

// ═══════════════════════════════════════════════════════════════════════════
// MAIN ACTIVITY  — тонкий оркестратор
// Только: верхнеуровневый state, создание интерфейсов, вызов BridgeEffects и AppScreen.
// Новые экраны/фичи → AppScreen.kt.  Новые JS-операции → BridgeEngine.kt.
// ═══════════════════════════════════════════════════════════════════════════

class MainActivity : ComponentActivity() {

    // Лог живёт здесь чтобы пережить рекомпозиции
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
        log("[System] nan0gram v3 инициализация")
        enableEdgeToEdge()

        setContent {

            // ── Верхнеуровневый state ──────────────────────────────────────
            var isBgServiceActive  by remember { mutableStateOf(true) }
            var hasHandledLogin    by remember { mutableStateOf(false) }
            var isLogPanelExpanded by remember { mutableStateOf(false) }
            var uiAlpha            by remember { mutableStateOf(0.95f) }
            var isParserEnabled    by remember { mutableStateOf(false) }

            // WebView-ссылки (устанавливаются из AppScreen через колбэки)
            var ukrnetWebView    by remember { mutableStateOf<WebView?>(null) }
            var messengerWebView by remember { mutableStateOf<WebView?>(null) }

            // Координаты DOM ukr.net (обновляются через UkrnetJsInterface)
            var coords by remember { mutableStateOf(DomCoords()) }

            val coroutineScope = rememberCoroutineScope()
            val logListState   = rememberLazyListState()

            // Авто-скролл логов вниз при добавлении новой строки
            LaunchedEffect(logList.size) {
                if (logList.isNotEmpty() && isLogPanelExpanded)
                    logListState.animateScrollToItem(logList.size - 1)
            }

            // ── JS-интерфейсы ──────────────────────────────────────────────
            // Создаются один раз — remember гарантирует стабильность ссылок.

            val ukrnetInterface = remember {
                UkrnetJsInterface(
                    log                = ::log,
                    onLoginSuccess     = { hasHandledLogin = true; isBgServiceActive = false },
                    onCoordsUpdate     = { coords = it },
                    onFirstCoordsLogged = { /* координаты уже залогированы внутри интерфейса */ },
                    getMessengerWebView = { messengerWebView },
                    isLoginHandled     = { hasHandledLogin },
                    getCurrentCoords   = { coords }
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

            // ── Фоновые JS-циклы (auth monitor, scanner, reader) ──────────
            BridgeEffects(
                isBgServiceActive = isBgServiceActive,
                isParserEnabled   = isParserEnabled,
                ukrnetWebView     = ukrnetWebView,
                coords            = coords
            )

            // ── Главный UI ─────────────────────────────────────────────────
            AppScreen(
                // State
                isBgServiceActive  = isBgServiceActive,
                isLogPanelExpanded = isLogPanelExpanded,
                uiAlpha            = uiAlpha,
                isParserEnabled    = isParserEnabled,
                logList            = logList,
                logListState       = logListState,
                coords             = coords,
                // Колбэки
                onLogPanelToggle     = { isLogPanelExpanded = it },
                onUiAlphaChange      = { uiAlpha = it },
                onParserToggle       = { isParserEnabled = !isParserEnabled },
                onLogClear           = { logList.clear() },
                onUkrnetViewReady    = { ukrnetWebView = it },
                onMessengerViewReady = { messengerWebView = it },
                onUkrnetReload       = { ukrnetWebView?.reload() },
                onMessengerReload    = { messengerWebView?.reload() },
                // Зависимости
                ukrnetInterface    = ukrnetInterface,
                messengerInterface = messengerInterface,
                coroutineScope     = coroutineScope,
                log                = ::log
            )
        }
    }
}
