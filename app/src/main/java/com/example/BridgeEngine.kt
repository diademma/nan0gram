package com.example

import android.os.SystemClock
import android.view.MotionEvent
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay

// ═══════════════════════════════════════════════════════════════════════════
// BRIDGE ENGINE  — ядро: типы, утилиты ввода, фоновые эффекты
//
// Структура Bridge-слоя:
//   BridgeEngine.kt      ← этот файл  (типы + утилиты + эффекты)
//   BridgeScripts.kt     ← JS-строки  (почти не меняются)
//   BridgeInterfaces.kt  ← @JavascriptInterface классы (растут с новыми операциями)
//
// Правило роста: если любой из этих файлов подходит к 700 строкам →
//   выноси тематическую группу в новый файл (BridgeCompose.kt, BridgeReader.kt и т.д.)
// ═══════════════════════════════════════════════════════════════════════════

// ─── Координаты DOM-элементов ukr.net ──────────────────────────────────────

data class DomCoords(
    val composeX: Float? = null, val composeY: Float? = null,
    val toX:      Float? = null, val toY:      Float? = null,
    val subjectX: Float? = null, val subjectY: Float? = null,
    val bodyX:    Float? = null, val bodyY:    Float? = null,
    val sendX:    Float? = null, val sendY:    Float? = null
)

// ─── simulateTouch — физический тап по WebView ────────────────────────────
// stealFocus = false: НЕ забирать фокус у messengerWebView (фикс IME)

internal fun simulateTouch(
    webView: WebView?,
    cssX: Float,
    cssY: Float,
    stealFocus: Boolean = false,
    log: (String) -> Unit = {}
) {
    if (webView == null) return
    val downTime = SystemClock.uptimeMillis()
    val density  = webView.resources.displayMetrics.density
    val px = cssX * density
    val py = cssY * density
    val dn = MotionEvent.obtain(downTime, downTime,       MotionEvent.ACTION_DOWN, px, py, 0)
    val up = MotionEvent.obtain(downTime, downTime + 100, MotionEvent.ACTION_UP,   px, py, 0)
    webView.post {
        if (stealFocus) { webView.requestFocus(); webView.requestFocusFromTouch() }
        webView.dispatchTouchEvent(dn); webView.dispatchTouchEvent(up)
        dn.recycle(); up.recycle()
        log("[Autoclicker] Клик: X=${px.toInt()}px, Y=${py.toInt()}px")
    }
}

// ─── simulateType — заполнение поля через нативный JS setter ──────────────
// НЕ вызывает el.focus() — не тригерит Android IME в ukrnetWebView.
// Работает с React/ukrnet через Object.getOwnPropertyDescriptor.

internal fun simulateType(
    webView: WebView?,
    selector: String,
    text: String,
    isAutocomplete: Boolean = false
) {
    if (webView == null) return
    val esc = text
        .replace("\\", "\\\\").replace("'", "\\'")
        .replace("\n", "\\n").replace("\r", "")
    val js = """
        (function() {
            var el = document.querySelector('$selector');
            if (!el) return 'not_found';
            if (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA') {
                try {
                    var ns = Object.getOwnPropertyDescriptor(
                        el.tagName === 'INPUT' ? HTMLInputElement.prototype : HTMLTextAreaElement.prototype,
                        'value'
                    ).set;
                    ns.call(el, '$esc');
                } catch(e) { el.value = '$esc'; }
                el.dispatchEvent(new Event('input',  { bubbles: true }));
                el.dispatchEvent(new Event('change', { bubbles: true }));
                if ($isAutocomplete) {
                    el.dispatchEvent(new KeyboardEvent('keydown', { bubbles:true, cancelable:true, key:'Enter', keyCode:13 }));
                    el.dispatchEvent(new KeyboardEvent('keyup',   { bubbles:true, cancelable:true, key:'Enter', keyCode:13 }));
                }
                el.blur();
            } else if (el.getAttribute('contenteditable') === 'true') {
                el.innerHTML = '$esc';
                el.dispatchEvent(new Event('input', { bubbles: true }));
                el.blur();
            }
            return 'success';
        })();
    """.trimIndent()
    webView.post { webView.evaluateJavascript(js, null) }
}

// ═══════════════════════════════════════════════════════════════════════════
// BridgeEffects  — три фоновых JS-цикла (нет ни одного UI-элемента)
// Вызывается из MainActivity один раз внутри setContent { }.
// Добавляй новые фоновые петли здесь (healthCheck, drafts sync, и т.д.)
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun BridgeEffects(
    isBgServiceActive: Boolean,
    isParserEnabled: Boolean,
    ukrnetWebView: WebView?,
    coords: DomCoords
) {
    // Мониторинг авторизации ukr.net
    LaunchedEffect(isBgServiceActive, ukrnetWebView) {
        while (isBgServiceActive && ukrnetWebView != null) {
            delay(1500)
            ukrnetWebView.evaluateJavascript(MONITORING_JS, null)
        }
    }

    // DOM-сканер координат удалён: SMART_SCAN_JS инжектируется в onPageFinished
    // и работает через MutationObserver без фонового поллинга.

    // Ридер входящих (включается кнопкой «Радар» в панели логов)
    LaunchedEffect(isBgServiceActive, ukrnetWebView, isParserEnabled) {
        while (!isBgServiceActive && ukrnetWebView != null && isParserEnabled) {
            delay(1500)
            ukrnetWebView.evaluateJavascript(READING_JS, null)
        }
    }
}
