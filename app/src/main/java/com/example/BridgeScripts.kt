package com.example

// ════════════════ ДОМ-селекторы веб-версии Ukr.net ═════════════════════════
object UkrnetSelectors {
    const val COMPOSE_BUTTON = ".ml-header__compose"
    const val TO_INPUT = ".sm-auto-complete__input"
    const val SUBJECT_INPUT = "#sendmsg__subject"
    const val BODY_AREA = ".sm-editor__area"
    const val SEND_BUTTON = ".sm-header__send"
    const val ATTACH_BUTTON = "button.sm-header__attach"
    const val CANCEL_BUTTON = ".sm-header__cancel"

    // Инфикаторы загрузки
    const val ATTACH_PROGRESS = ".sm-attachments__progress-state"
    const val LOADER = ".sm-header__loader"

    // Селекторы списка писем и чтения для Радара
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
// Этот файл почти не меняется. Новые JS-скрипты добавляй сюда же.
// Максимум 700 строк — если больше, выделяй тематический файл
// (например, BridgeReaderScripts.kt для логики чтения писем).
// ═══════════════════════════════════════════════════════════════════════════

// ─── Мониторинг авторизации ────────────────────────────────────────────────
// Запускается в цикле пока isBgServiceActive == true.
// Сигналит Kotlin через Android.postMessage когда mail.ukr.net открылся.

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
            }, 1500);
        }
    } catch(e) {}
""".trimIndent()

// ─── DOM-сканер координат ──────────────────────────────────────────────────
// Запускается после логина. Находит CSS-координаты кнопок compose/send/etc.
// Результат → Android.postCoordinates(json).

internal val SCANNING_JS = """
    (function() {
        function getCoords(el) {
            if (!el) return null;
            var r = el.getBoundingClientRect();
            if (r.width === 0 || r.height === 0) return null;
            return { x: Math.round(r.left + r.width/2), y: Math.round(r.top + r.height/2) };
        }
        var result = {
            compose: getCoords(document.querySelector('${UkrnetSelectors.COMPOSE_BUTTON}')),
            to:      getCoords(document.querySelector('${UkrnetSelectors.TO_INPUT}')),
            subject: getCoords(document.querySelector('${UkrnetSelectors.SUBJECT_INPUT}')),
            body:    getCoords(document.querySelector('${UkrnetSelectors.BODY_AREA}')),
            send:    getCoords(document.querySelector('${UkrnetSelectors.SEND_BUTTON}'))
        };
        if (window.Android && window.Android.postCoordinates)
            window.Android.postCoordinates(JSON.stringify(result));
    })();
""".trimIndent()

// ─── Ридер входящих писем ──────────────────────────────────────────────────
// Машина состояний: IDLE → READING → RETURNING → IDLE.
// Находит непрочитанные письма с эмодзи в теме (💬 nan0gram маркер),
// открывает, извлекает тело и сигналит → Android.onIncomingMessage.

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
                var view = item.querySelector('${UkrnetSelectors.MAIL_ITEM_VIEW}');
                if (view && (view.classList.contains('unread') || view.classList.contains('mli-view_unread'))) return true;
                if (item.classList.contains('unread') || item.classList.contains('ml-item_unread')) return true;
                var titleEl = item.querySelector('${UkrnetSelectors.MAIL_ITEM_TITLE}');
                if (titleEl) {
                    var w = window.getComputedStyle(titleEl).fontWeight;
                    if (w === 'bold' || parseInt(w) >= 600) return true;
                }
                return false;
            }
            if (window.location.href.indexOf('login') !== -1) return;
            if (window.nState === 'IDLE') {
                var items = document.querySelectorAll('${UkrnetSelectors.MAIL_ITEM}');
                for (var i = items.length - 1; i >= 0; i--) {
                    var item = items[i];
                    var id = item.id;
                    if (!id || window.nProcessed.has(id)) continue;
                    var titleEl = item.querySelector('${UkrnetSelectors.MAIL_ITEM_TITLE}');
                            var titleText = titleEl ? (titleEl.innerText || '') : '';
                            var isTarget = titleText.indexOf('Re[') !== -1 || /\p{Emoji}/u.test(titleText);
                            if (isTarget && isMsgUnread(item)) {
                        window.nState = 'READING';
                        window.nTargetId = id;
                        var link = item.querySelector('${UkrnetSelectors.MAIL_ITEM_LINK}') || item;
                        link.click();
                        return;
                    } else {
                        markProcessed(id);
                    }
                }
            } else if (window.nState === 'READING') {
                var bodyEl    = document.querySelector('${UkrnetSelectors.READ_BODY}');
                var subjectEl = document.querySelector('${UkrnetSelectors.READ_SUBJECT}');
                var backBtn   = document.querySelector('${UkrnetSelectors.BACK_BUTTON}');
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
// Временно отключает HTMLElement.prototype.focus в ukrnet
// чтобы форма compose не могла украсть IME у мессенджера.
// Вставляется ПЕРЕД кликом на кнопку «Написать», снимается в fillJs.

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
// Вставляется ПОСЛЕ simulateTouch на кнопку «Написать».
// Ждёт появления полей (setInterval 80ms), заполняет нативным setter,
// снимает focus-patch, вызывает Android.onComposeReady().
// Параметры %TO% и %SUBJECT% заменяются в Kotlin перед инъекцией.

internal val COMPOSE_FILL_JS = """
    (function(to, subject) {
        var attempts = 0;
        var t = setInterval(function() {
            attempts++;
            // Kill-switch: стелс-режим активен — не заполняем и не вызываем onComposeReady
            if (window._n0gStealthUpload) { clearInterval(t); return; }
            var toEl   = document.querySelector('${UkrnetSelectors.TO_INPUT}');
            var subjEl = document.querySelector('${UkrnetSelectors.SUBJECT_INPUT}');
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
// Вставляется из onPageFinished когда URL содержит "sendmsg".
// AppScreen.kt сбрасывает window._n0gFilled = false перед каждой вставкой
// чтобы гарантировать заполнение при каждой новой загрузке страницы.
//
// КРИТИЧНО: НЕ вызывает onComposeReady() — иначе мессенджер снова
// вызовет openCompose(), что приведёт к двойному заполнению поля To
// (два адреса 270232@ukr.net) и случайному тексту в строке ввода.

internal val SENDMSG_FILL_JS = """
    (function() {
        if (window._n0gFilled) return;
        var count = 0;
        var t = setInterval(function() {
            count++;
            if (count > 60) { clearInterval(t); return; }

            var toEl = document.querySelector('input[name="to"]')
                || document.querySelector('input[type="email"]')
                || document.querySelector('${UkrnetSelectors.TO_INPUT}')
                || document.querySelector('input[placeholder]');

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

            var subjEl = document.querySelector('input[name="subject"]')
                || document.querySelector('${UkrnetSelectors.SUBJECT_INPUT}')
                || document.querySelector('input[placeholder*="ема"]');
            if (subjEl) {
                try {
                    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')
                        .set.call(subjEl, subj);
                } catch(e) { subjEl.value = subj; }
                subjEl.dispatchEvent(new Event('input',  {bubbles:true}));
                subjEl.dispatchEvent(new Event('change', {bubbles:true}));
            }
            // НЕ вызываем onComposeReady() — это разрывает петлю обратной связи:
            // onComposeReady → nan0gram:compose-ready → messenger → openCompose()
            // → двойное заполнение → два адреса в To → мусорный текст в input
        }, 100);
    })();
""".trimIndent()
