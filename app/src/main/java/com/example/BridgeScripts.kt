package com.example

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
            compose: getCoords(document.querySelector('.ml-header__compose')),
            to:      getCoords(document.querySelector('.sm-auto-complete__input')),
            subject: getCoords(document.querySelector('#sendmsg__subject')),
            body:    getCoords(document.querySelector('.sm-editor__area')),
            send:    getCoords(document.querySelector('.sm-header__send'))
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
                var view = item.querySelector('.mli-view');
                if (view && (view.classList.contains('unread') || view.classList.contains('mli-view_unread'))) return true;
                if (item.classList.contains('unread') || item.classList.contains('ml-item_unread')) return true;
                var titleEl = item.querySelector('.mli-view__title');
                if (titleEl) {
                    var w = window.getComputedStyle(titleEl).fontWeight;
                    if (w === 'bold' || parseInt(w) >= 600) return true;
                }
                return false;
            }
            if (window.location.href.indexOf('login') !== -1) return;
            if (window.nState === 'IDLE') {
                var items = document.querySelectorAll('.ml-item');
                for (var i = items.length - 1; i >= 0; i--) {
                    var item = items[i];
                    var id = item.id;
                    if (!id || window.nProcessed.has(id)) continue;
                    var titleEl = item.querySelector('.mli-view__title');
                    var hasEmoji = titleEl && /\p{Emoji}/u.test(titleEl.innerText || '');
                    if (hasEmoji && isMsgUnread(item)) {
                        window.nState = 'READING';
                        window.nTargetId = id;
                        var link = item.querySelector('.mli-view__link') || item;
                        link.click();
                        return;
                    } else {
                        markProcessed(id);
                    }
                }
            } else if (window.nState === 'READING') {
                var bodyEl    = document.querySelector('.rm-body__content');
                var subjectEl = document.querySelector('.readmsg__subject');
                var backBtn   = document.querySelector('.rm-header__list');
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
// Ждёт появления полей (setInterval 200ms), заполняет нативным setter,
// снимает focus-patch, вызывает Android.onComposeReady().
// Параметры %TO% и %SUBJECT% заменяются в Kotlin перед инъекцией.

internal val COMPOSE_FILL_JS = """
    (function(to, subject) {
        var attempts = 0;
        var t = setInterval(function() {
            attempts++;
            var toEl   = document.querySelector('.sm-auto-complete__input');
            var subjEl = document.querySelector('#sendmsg__subject');
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
        }, 200);
    })('%TO%', '%SUBJECT%');
""".trimIndent()
