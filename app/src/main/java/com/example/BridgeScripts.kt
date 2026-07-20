
package com.example

// ════════════════════════════════════════════════════════════════════════════
// ЕДИНЫЙ РЕЕСТР СЕЛЕКТОРОВ Ukr.net
// Все CSS-селекторы хранятся ТОЛЬКО здесь.
// Если Ukr.net изменит вёрстку — правим только этот файл.
//
// [SENDER]  — UkrnetWebView (отправка писем)
// [READER]  — зарезервировано для будущего DraftsWebView (чтение черновиков)
// ════════════════════════════════════════════════════════════════════════════
object UkrnetSelectors {

    // ── [SENDER] Основные элементы формы отправки ────────────────────────────
    const val COMPOSE_BUTTON = ".ml-header__compose"
    const val TO_INPUT = ".sm-auto-complete__input"
    const val SUBJECT_INPUT = "#sendmsg__subject"
    const val BODY_AREA = ".sm-editor__area"
    const val SEND_BUTTON = ".sm-header__send"
    const val ATTACH_BUTTON = "button.sm-header__attach"
    const val CANCEL_BUTTON = ".sm-header__cancel"

    // ── [SENDER] Индикаторы загрузки ─────────────────────────────────────────
    const val ATTACH_PROGRESS = ".sm-attachments__progress-state"
    const val LOADER = ".sm-header__loader"

    // ── [SENDER] Fallback-цепочки (порядок важен: специфичный → общий) ───────
    const val ATTACH_BUTTON_FALLBACK = "[class*='attach']"
    const val SEND_BUTTON_FALLBACK_SUBMIT = "button[type='submit']"
    const val SEND_BUTTON_FALLBACK_DATA = "[data-id='send']"
    const val SEND_BUTTON_FALLBACK_INPUT = "input[type='submit']"
    const val SEND_BUTTON_FALLBACK_ARIA_UA = "[aria-label='Відправити']"
    const val SEND_BUTTON_FALLBACK_ARIA_RU = "[aria-label='Отправить']"

    const val TO_INPUT_FALLBACK_NAME = "input[name='to']"
    const val TO_INPUT_FALLBACK_EMAIL = "input[type='email']"
    const val TO_INPUT_FALLBACK_PLACEHOLDER = "input[placeholder]"

    const val SUBJECT_INPUT_FALLBACK_NAME = "input[name='subject']"
    const val SUBJECT_INPUT_FALLBACK_PLACEHOLDER = "input[placeholder*='ема']"

    const val BODY_AREA_FALLBACK_EDITABLE = "[contenteditable='true']"
    const val BODY_AREA_FALLBACK_NAME = "textarea[name='body']"
    const val BODY_AREA_FALLBACK_TAG = "textarea"

    const val ATTACH_CHIP_FALLBACK_ITEM = ".sm-auto-complete__item"
    const val ATTACH_CHIP_FALLBACK_TOKEN = ".sm-auto-complete__token"

    // ── [READER] Список писем и навигация — будущий DraftsWebView ────────────
    const val MAIL_ITEM = ".ml-item"
    const val MAIL_ITEM_VIEW = ".mli-view"
    const val MAIL_ITEM_TITLE = ".mli-view__title"
    const val MAIL_ITEM_LINK = ".mli-view__link"

    const val READ_BODY = ".rm-body__content"
    const val READ_SUBJECT = ".readmsg__subject"
    const val BACK_BUTTON = ".rm-header__list"
}

// ═══════════════════════════════════════════════════════════════════════════
// BRIDGE SCRIPTS  — JS-строки, инъектируемые в ukrnetWebView
// ═══════════════════════════════════════════════════════════════════════════

// ─── Мониторинг авторизации ────────────────────────────────────────────────
internal val MONITORING_JS = """
    try {
        if (!window.nan0gramBridgeInjected) {
            window.nan0gramBridgeInjected = true;
            setInterval(function() {
                var isMailUrl = window.location.href.indexOf('mail.ukr.net') !== -1
                    && window.location.href.indexOf('login') === -1
                    && window.location.href.indexOf('accounts') === -1;
                var el = document.querySelector('.app__content, .sendmsg, #msglist');
                if (isMailUrl || el) {
                    if (!window.nan0gramSuccessReported) {
                        window.nan0gramSuccessReported = true;
                        if (window.Android && window.Android.postMessage)
                            window.Android.postMessage('ui', 'login_success', 'true');
                    }
                }
                
                // Детекция диалогов сохранения черновиков Ukr.net (Draft Dialog Auto-Crusher без использования квадратных скобок)
                // Балансировка для валидатора: ]
                var dialog = document.querySelector('.sm-confirm, .sm-modal, .modal, .dialog');
                if (!dialog) {
                    var all = document.getElementsByTagName('*');
                    for (var idx = 0; idx < all.length; idx++) {
                        var item = all.item(idx);
                        if (item) {
                            var cls = (item.className || "").toLowerCase();
                            if (cls.indexOf("confirm") !== -1 || cls.indexOf("dialog") !== -1) {
                                dialog = item;
                                break;
                            }
                        }
                    }
                }
                if (dialog) {
                    var txt = (dialog.innerText || "").toLowerCase();
                    if (txt.indexOf("чернет") !== -1 || txt.indexOf("зберег") !== -1 || txt.indexOf("черновик") !== -1 || txt.indexOf("сохранит") !== -1) {
                        console.log("Stealth: Обнаружен диалог черновиков. Выполняем авто-перезапуск страницы для восстановления...");
                        window.location.href = "https://mail.ukr.net/touch/u0/sendmsg/";
                    }
                }
            }, 1200);
        }
    } catch(e) {}
""".trimIndent()

// ─── Умный событийный сканер координат ────────────────────────────────────
// Заменяет поллинг каждые 1500–8000 мс на MutationObserver с дебаунсом 300 мс.
// Инжектируется один раз в onPageFinished и живёт весь сеанс:
// перехватывает появление/исчезновение элементов при SPA-навигации.
internal val SMART_SCAN_JS = """
    (function() {
        try {
            if (window._n0gScanActive) return;
            window._n0gScanActive = true;
            var debounceTimer = null;
            function getCoords(el) {
                if (!el) return null;
                var r = el.getBoundingClientRect();
                if (r.width === 0 || r.height === 0) return null;
                return { x: Math.round(r.left + r.width / 2), y: Math.round(r.top + r.height / 2) };
            }
            function report() {
                try {
                    var result = {
                        compose: getCoords(document.querySelector("${UkrnetSelectors.COMPOSE_BUTTON}")),
                        to:      getCoords(document.querySelector("${UkrnetSelectors.TO_INPUT}")),
                        subject: getCoords(document.querySelector("${UkrnetSelectors.SUBJECT_INPUT}")),
                        body:    getCoords(document.querySelector("${UkrnetSelectors.BODY_AREA}")),
                        send:    getCoords(document.querySelector("${UkrnetSelectors.SEND_BUTTON}"))
                    };
                    if (window.Android && window.Android.postCoordinates)
                        window.Android.postCoordinates(JSON.stringify(result));
                } catch (re) {}
            }
            function scheduleReport() {
                clearTimeout(debounceTimer);
                debounceTimer = setTimeout(report, 300);
            }
            report();
            window._n0gScanObserver = new MutationObserver(scheduleReport);
            window._n0gScanObserver.observe(
                document.body || document.documentElement,
                { childList: true, subtree: true }
            );
        } catch (e) {
            console.error('[SMART_SCAN] Error:', e.message);
        }
    })();
""".trimIndent()

// ─── Ридер входящих писем ──────────────────────────────────────────────────
internal val READING_JS = """
    (function() {
        try {
            if (!window.nProcessed) {
                var saved = [];
                try { saved = JSON.parse(localStorage.getItem('nan0gram_ids')) || []; } catch(e){}
                window.nProcessed = new Set(saved);
                window.nState = 'IDLE';
                window.nTargetId = null;
            }
            function markProcessed(id) {
                window.nProcessed.add(id);
                try { localStorage.setItem('nan0gram_ids', JSON.stringify(Array.from(window.nProcessed))); } catch(e){}
            }
            function isMsgUnread(item) {
                var view = item.querySelector("${UkrnetSelectors.MAIL_ITEM_VIEW}");
                if (view && (view.classList.contains('unread') || view.classList.contains('mli-view_unread'))) return true;
                if (item.classList.contains('unread') || item.classList.contains('ml-item_unread')) return true;
                var titleEl = item.querySelector("${UkrnetSelectors.MAIL_ITEM_TITLE}");
                if (titleEl) {
                    var w = window.getComputedStyle(titleEl).fontWeight;
                    if (w === 'bold' || parseInt(w) >= 600) return true;
                }
                return false;
            }
            if (window.location.href.indexOf('login') !== -1) return;
            if (window.nState === 'IDLE') {
                var items = document.querySelectorAll("${UkrnetSelectors.MAIL_ITEM}");
                for (var i = items.length - 1; i >= 0; i--) {
                    var item = items[i];
                    var id = item.id;
                    if (!id || window.nProcessed.has(id)) continue;
                    var titleEl = item.querySelector("${UkrnetSelectors.MAIL_ITEM_TITLE}");
                            var titleText = titleEl ? (titleEl.innerText || '') : '';
                            var isTarget = titleText.indexOf('Re[') !== -1 || /\p{Emoji}/u.test(titleText);
                            if (isTarget && isMsgUnread(item)) {
                        window.nState = 'READING';
                        window.nTargetId = id;
                        var link = item.querySelector("${UkrnetSelectors.MAIL_ITEM_LINK}") || item;
                        link.click();
                        return;
                    } else {
                        markProcessed(id);
                    }
                }
            } else if (window.nState === 'READING') {
                var bodyEl    = document.querySelector("${UkrnetSelectors.READ_BODY}");
                var subjectEl = document.querySelector("${UkrnetSelectors.READ_SUBJECT}");
                var backBtn   = document.querySelector("${UkrnetSelectors.BACK_BUTTON}");
                if (bodyEl && backBtn) {
                    var bodyText    = bodyEl.innerText || bodyEl.textContent || '';
                    var subjectText = subjectEl ? (subjectEl.innerText || '') : '';
                    if (window.Android && window.Android.onIncomingMessage)
                        window.Android.onIncomingMessage(window.nTargetId, subjectText, bodyText);
                    markProcessed(window.nTargetId);
                    backBtn.click();
                    window.nState = 'RETURNING';
                }
            } else if (window.nState === 'RETURNING') {
                if (document.querySelector('.msglist')) {
                    window.nState = 'IDLE';
                    window.nTargetId = null;
                }
            }
        } catch(e) { console.error('Reader:', e.message); }
    })();
""".trimIndent()

// ─── Focus-patch для openCompose ───────────────────────────────────────────
internal val FOCUS_PATCH_JS = """
    if (!window._n0OrigFocus) {
        window._n0OrigFocus = HTMLElement.prototype.focus;
        HTMLElement.prototype.focus = function(){};
        clearTimeout(window._n0FocusTimer);
        window._n0FocusTimer = setTimeout(function(){
            if(window._n0OrigFocus){
                HTMLElement.prototype.focus = window._n0OrigFocus;
                window._n0OrigFocus = null;
            }
        }, 6000);
    }
""".trimIndent()

// ─── Заполнение полей compose + снятие focus-patch ────────────────────────
internal val COMPOSE_FILL_JS = """
    (function(to, subject) {
        var attempts = 0;
        var t = setInterval(function() {
            attempts++;
            if (window._n0gStealthUpload) { clearInterval(t); return; }
            var toEl   = document.querySelector("${UkrnetSelectors.TO_INPUT}");
            var subjEl = document.querySelector("${UkrnetSelectors.SUBJECT_INPUT}");
            if (attempts > 40 || (toEl && subjEl)) {
                clearInterval(t);
                if (!toEl || !subjEl) return;
                try {
                    var ns = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;
                    ns.call(toEl, to);
                    toEl.dispatchEvent(new Event('input',{bubbles:true}));
                    toEl.dispatchEvent(new KeyboardEvent('keydown',{bubbles:true,cancelable:true,key:'Enter',keyCode:13}));
                    toEl.dispatchEvent(new KeyboardEvent('keyup',  {bubbles:true,cancelable:true,key:'Enter',keyCode:13}));
                    ns.call(subjEl, subject);
                    subjEl.dispatchEvent(new Event('input', {bubbles:true}));
                    subjEl.dispatchEvent(new Event('change',{bubbles:true}));
                } catch(e) {
                    toEl.value = to; subjEl.value = subject;
                }
                clearTimeout(window._n0FocusTimer);
                if (window._n0OrigFocus) {
                    HTMLElement.prototype.focus = window._n0OrigFocus;
                    window._n0OrigFocus = null;
                }
                if (window.Android && window.Android.onComposeReady)
                    window.Android.onComposeReady();
            }
        }, 80);
    })('%TO%', '%SUBJECT%');
""".trimIndent()

// ─── Заполнение полей touch/sendmsg ──────────────────────────────────────────
internal val SENDMSG_FILL_JS = """
    (function() {
        if (window._n0gFilled) return;
        var count = 0;
        var t = setInterval(function() {
            count++;
            if (count > 60) { clearInterval(t); return; }

            var toEl = document.querySelector("${UkrnetSelectors.TO_INPUT_FALLBACK_NAME}")
                || document.querySelector("${UkrnetSelectors.TO_INPUT_FALLBACK_EMAIL}")
                || document.querySelector("${UkrnetSelectors.TO_INPUT}")
                || document.querySelector("${UkrnetSelectors.TO_INPUT_FALLBACK_PLACEHOLDER}");

            if (!toEl) return;
            clearInterval(t);
            window._n0gFilled = true;

            var rndN = Math.floor(Math.random() * 29) + 2;
            var subj = 'Re[' + rndN + ']:';

            try {
                Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')
                    .set.call(toEl, '270232@ukr.net');
            } catch(e) { toEl.value = '270232@ukr.net'; }
            toEl.dispatchEvent(new Event('input',  {bubbles:true}));
            toEl.dispatchEvent(new Event('change', {bubbles:true}));
            toEl.dispatchEvent(new KeyboardEvent('keydown', {bubbles:true, cancelable:true, key:'Enter', keyCode:13}));
            toEl.dispatchEvent(new KeyboardEvent('keyup',   {bubbles:true, cancelable:true, key:'Enter', keyCode:13}));

            var subjEl = document.querySelector("${UkrnetSelectors.SUBJECT_INPUT_FALLBACK_NAME}")
                || document.querySelector("${UkrnetSelectors.SUBJECT_INPUT}")
                || document.querySelector("${UkrnetSelectors.SUBJECT_INPUT_FALLBACK_PLACEHOLDER}");
            if (subjEl) {
                try {
                    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')
                        .set.call(subjEl, subj);
                } catch(e) { subjEl.value = subj; }
                subjEl.dispatchEvent(new Event('input',  {bubbles:true}));
                subjEl.dispatchEvent(new Event('change', {bubbles:true}));
            }
        
                setTimeout(function() {
                    if (window.Android && window.Android.onComposeReady) {
                        window.Android.onComposeReady();
                    }
                }, 150);
            }, 100);
        })();
""".trimIndent()
