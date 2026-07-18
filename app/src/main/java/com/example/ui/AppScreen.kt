package com.example

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope

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
    repository: NanogramRepository,
    mediaManager: MediaManager,
    log: (String) -> Unit
) {
    // Подключаем БД и медиа-менеджер к JS-мосту перед отрисовкой
    messengerInterface.repository = repository
    messengerInterface.mediaManager = mediaManager

    Scaffold(containerColor = Color(0xFF130E19), modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding).background(Color(0xFF130E19))) {
            WebViewLayer(isBgServiceActive, uiAlpha, ukrnetInterface, messengerInterface, mediaManager, onUkrnetViewReady, onMessengerViewReady, log)
            LogPanel(isLogPanelExpanded, onLogPanelToggle, logList, logListState, onLogClear, isBgServiceActive, uiAlpha, onUiAlphaChange, isParserEnabled, onParserToggle, coords, onUkrnetReload, onMessengerReload, coroutineScope, log)
        }
    }
}