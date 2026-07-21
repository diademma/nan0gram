(function(W) {
    "use strict";
    try {

        if (!W.nanoCipher || typeof W.nanoCipher.encryptRaw !== 'function') {
            console.error('[nan0gram:core] СТОП: cipher.js не загружен. bridge-core отключён.');
            return;
        }

        const APP_NAME = W.APP_NAME || "nan0gram";
        const DEFAULT_RECIPIENT = W.DEFAULT_RECIPIENT || "270232@ukr.net";
        const SERVER_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtqwfyqcW4PcAMckgRd5l9SSNiCkGJTWfyZfXoolmqh+3h5rcU9Quh9qMVtXWDFLO0XEo3+tf7e8ctjGONl2od5HuPMYI/ytPrctnGKrogyjIApMEBzTb2bq7mhPNDkm8zXGP02usl81/kWjeH02rYNpdrRS5Iu1mC5MS52XMSp25uAkta8aRsIGoPLdCbRU8Dtt1nAZ2JuM36NDChfvrjPg80czWuRxH8UTfGgrEa+PitVdhgWjT05izwfR7tpGMUmW7/QvBB9Rquf+PcqM3deUgS5PvUepZL24cLMqtZocmeUCsufk4b7FYlz7M5ekEjXMZrJrbzJ5carLwvBlxswIDAQAB";

        const ui = (fn) => { try { return W.setTimeout(fn, 0); } catch (_) { return fn(); } };
        function log(...args) { try { console.log(`[${APP_NAME}]`, ...args); } catch (_) {} }
        function warn(...args) { try { console.warn(`[${APP_NAME}]`, ...args); } catch (_) {} }
        function callAndroid(method, ...args) {
            try {
                if (W.Android && typeof W.Android[method] === "function") {
                    return W.Android[method](...args);
                }
            } catch (e) {
                warn(`Android.${method} failed:`, e && e.message ? e.message : e);
            }
            return undefined;
        }

        function getInputValue(el) {
            if (!el) return "";
            if (typeof el.value === "string") return el.value;
            return el.textContent || "";
        }

        function isEditable(el) {
            if (!el || !el.tagName) return false;
            const tag = el.tagName.toUpperCase();
            return (el.getAttribute("contenteditable") === "true" ||
                tag === "TEXTAREA" ||
                (tag === "INPUT" && !["hidden", "submit", "button", "checkbox", "radio", "range", "color", "file"].includes((el.type || "").toLowerCase()))
            );
        }

        function hash32(str) {
            let h = 2166136261 >>> 0;
            for (let i = 0; i < str.length; i++) {
                h ^= str.charCodeAt(i);
                h = Math.imul(h, 16777619);
            }
            h ^= h >>> 16;
            h = Math.imul(h, 2246822507);
            h ^= h >>> 13;
            h = Math.imul(h, 3266489909);
            h ^= h >>> 16;
            return h >>> 0;
        }

        function xorshift32(seed) {
            let x = seed >>> 0;
            return () => {
                x ^= x << 13;
                x ^= x >>> 17;
                x ^= x << 5;
                return x >>> 0;
            };
        }

        function getFormattedDeviceId(rawId) {
            if (!rawId) return "4f0Q67gPe86N";
            const seed = hash32(rawId);
            const nextVal = xorshift32(seed);
            
            const digits = [];
            for (let i = 0; i < 6; i++) digits.push((nextVal() % 10).toString());
            
            const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
            const letters = [];
            for (let i = 0; i < 6; i++) letters.push(alphabet[nextVal() % 52]);
            
            const pool = digits.concat(letters);
            for (let i = pool.length - 1; i > 0; i--) {
                const j = nextVal() % (i + 1);
                const temp = pool[i];
                pool[i] = pool[j];
                pool[j] = temp;
            }
            return pool.join("");
        }

        let _pendingActions = [];
        let _flushTimer = null;

        function queueAction(chatId, actionObj) {
            let actItem = null;
            if (actionObj.type === "reaction") {
                actItem = {
                    t: W.MsgTypes.REACT,
                    ref: String(actionObj.targetMessageId),
                    e: actionObj.value || ""
                };
                _pendingActions = _pendingActions.filter(a => !(a.t === W.MsgTypes.REACT && a.ref === actItem.ref));
            } else if (actionObj.type === "pin") {
                actItem = {
                    t: W.MsgTypes.PIN,
                    ref: String(actionObj.targetMessageId),
                    p: actionObj.value ? 1 : 0
                };
                _pendingActions = _pendingActions.filter(a => !(a.t === W.MsgTypes.PIN && a.ref === actItem.ref));
            } else if (actionObj.type === "delete") {
                actItem = {
                    t: W.MsgTypes.DELETE,
                    ref: String(actionObj.targetMessageId)
                };
                _pendingActions = _pendingActions.filter(a => a.ref !== actItem.ref);
            }

            if (actItem) {
                _pendingActions.push(actItem);
                log("[Stealth] Queue updated, item added, size: " + _pendingActions.length);
            }

            W.clearTimeout(_flushTimer);
            _flushTimer = W.setTimeout(() => {
                flushPendingActions();
            }, 8000);
        }

        function flushPendingActions() {
            W.clearTimeout(_flushTimer);
            _flushTimer = null;

            if (_pendingActions.length === 0) return;

            let actionsToSend = [];
            try {
                actionsToSend = [..._pendingActions];
                _pendingActions = [];

                log("[Stealth] Debounce timer expired. Flushing queued actions: " + actionsToSend.length);

                const targetChatId = NanoBridge.state.chatId;
                const recipient = NanoBridge.state.recipient || DEFAULT_RECIPIENT;

                if (!targetChatId) {
                    throw new Error("Active chatId is not set on bridge. Cannot flush actions.");
                }

                const meta = {
                    v: 1,
                    app: APP_NAME,
                    deviceId: W.nan0gram ? W.nan0gram.getDeviceId() : "4f0Q67gPe86N",
                    senderName: localStorage.getItem("nan0gram_username") || "Я",
                    to: recipient,
                    chatId: targetChatId,
                    blocks: actionsToSend,
                    subjectX: NanoBridge.state.subjectX || (W.nanoUtils ? W.nanoUtils.nextSubjectX() : Math.floor(Math.random() * 100)),
                    ts: Date.now()
                };

                const messageKey = (W.nanoUtils ? W.nanoUtils.randomKey() : ("k" + Math.random().toString(36).substr(2, 16)));
                const payloadStr = JSON.stringify({ meta: meta, text: "" });
                
                const payloadBlock = W.nanoCipher.encryptRaw(payloadStr, messageKey, "msg");
                const keyBlock = W.nanoCipher.encryptKeyRsa(messageKey, SERVER_PUBLIC_KEY);
                
                window.nan0gram_pendingMediaBody = W.nanoCipher.mask(payloadBlock + keyBlock);
                
                NanoBridge._openComposeIfNeeded(true);
            } catch (e) {
                _pendingActions = actionsToSend.concat(_pendingActions);
                _flushTimer = W.setTimeout(flushPendingActions, 8000);
                log("[Stealth Error] Flush failed: " + e.message + ". Actions rolled back.");
            }
        }

        function resetFlushTimer() {
            if (_pendingActions.length === 0) return;
            W.clearTimeout(_flushTimer);
            _flushTimer = W.setTimeout(() => {
                flushPendingActions();
            }, 8000);
            log(`[Stealth] Таймер сброшен: пользователь активно печатает. Отправка метаданных отложена.`);
        }

        function setActiveChat(chatId, recipient) {
            try {
                NanoBridge.state.chatId = String(chatId || "");
                if (recipient) {
                    NanoBridge.state.recipient = String(recipient);
                } else {
                    NanoBridge.state.recipient = DEFAULT_RECIPIENT;
                }
                log(`[Stealth] Активный чат обновлен на мосту: chatId="${NanoBridge.state.chatId}", recipient="${NanoBridge.state.recipient}"`);
            } catch (e) {
                log(`[Stealth Error] Ошибка установки активного чата: ${e.message}`);
            }
        }

        const NanoBridge = {
            _inputEl: null,
            _composeOpen: false,
            _sendPending: false,
            _debTimer: null,
            _pollTimer: null,
            _isComposing: false,
            _lastText: "",
            _lastChatId: "",
            _lastSentSignature: "",
            _wired: false,
            _initialized: false,

            state: {
                chatId: "", recipient: DEFAULT_RECIPIENT, subjectX: 0,
                subject: "", messageKey: "", systemBlock: "", keyBlock: "", openedAt: 0
            },
            _replyToId: null,

            _findInput() {
                if (this._inputEl && W.document.contains(this._inputEl)) return this._inputEl;
                const candidates = W.document.querySelectorAll('[contenteditable="true"], textarea, input[type="text"], input:not([type])');
                for (const el of candidates) {
                    if (isEditable(el)) { this._inputEl = el; return el; }
                }
                return null;
            },

            _getChatId() {
                const activeEl = W.document.querySelector('.chat-item.active');
                if (activeEl && activeEl.dataset.chatId) {
                    return activeEl.dataset.chatId;
                }
                const selectors = ['[class*="peer-title"]', '[class*="chat-title"]', '[class*="peer-name"]', '[class*="header"] h1', '[class*="header"] span', 'header h1', 'header span'];
                for (const selector of selectors) {
                    const el = W.document.querySelector(selector);
                    const text = (el && el.textContent ? el.textContent : "").trim();
                    if (text && text !== "Настройки" && text !== "Оформление" && text !== "Конфиденциальность" && text !== "Память и данные") return text;
                }
                return "chat";
            },

            _buildMeta() {
                const meta = {
                    v: 1,
                    app: APP_NAME,
                    deviceId: W.nan0gram ? W.nan0gram.getDeviceId() : "4f0Q67gPe86N",
                    senderName: localStorage.getItem("nan0gram_username") || "Я",
                    to: this.state.recipient,
                    chatId: this.state.chatId,
                    blocks: [],
                    subjectX: this.state.subjectX,
                    ts: Date.now()
                };
                if (this._replyToId) {
                    meta.blocks.push({
                        t: W.MsgTypes.REPLY,
                        ref: String(this._replyToId)
                    });
                }
                return meta;
            },

            _buildBody(plainText) {
                W.clearTimeout(_flushTimer);
                _flushTimer = null;

                let actionsToSend = [];
                try {
                    actionsToSend = [..._pendingActions];
                    _pendingActions = [];

                    const meta = this._buildMeta();
                    meta.blocks.push({ t: W.MsgTypes.TEXT });
                    
                    if (actionsToSend.length > 0) {
                        meta.blocks = meta.blocks.concat(actionsToSend);
                    }

                    const payload = JSON.stringify({ meta: meta, text: plainText });
                    
                    const payloadBlock = W.nanoCipher.encryptRaw(payload, this.state.messageKey, "msg");
                    const keyBlock = W.nanoCipher.encryptKeyRsa(this.state.messageKey, SERVER_PUBLIC_KEY);
                    
                    const combined = payloadBlock + keyBlock;
                    return W.nanoCipher.mask(combined);
                } catch (e) {
                    _pendingActions = actionsToSend.concat(_pendingActions);
                    log("[Stealth Error] Encryption failed in _buildBody: " + e.message);
                    throw e;
                }
            },

            _pushBody(plainText) {
                if (!this._composeOpen) return;
                if (window.nan0gram_pendingMediaBody) return; // Защита от затирания голосового пакета
                this._lastText = plainText || "";
                if (!this._lastText.trim()) {
                    callAndroid("setComposeBody", "");
                    this.state.lastBody = "";
                    return;
                }
                const body = this._buildBody(plainText);
                callAndroid("setComposeBody", body);
                this.state.lastBody = body;
            },

            _openComposeIfNeeded(force = false) {
                const chatId = this.state.chatId || this._getChatId();
                if (!force && this._composeOpen && this.state.chatId === chatId) return;

                if (this._composeOpen && this.state.chatId && this.state.chatId !== chatId) {
                    this._cancelCompose(true);
                }

                this.state.chatId = chatId;
                this.state.recipient = DEFAULT_RECIPIENT;
                this.state.subjectX = W.nanoUtils.nextSubjectX();
                this.state.subject = `Re[${this.state.subjectX}]:`;
                this.state.messageKey = W.nanoUtils.randomKey();
                this.state.openedAt = Date.now();

                const payload = { to: this.state.recipient, subject: this.state.subject };
                callAndroid("openCompose", JSON.stringify(payload));
                this._composeOpen = true;
                this._sendPending = false;

                W.nanoUtils.writeState({
                    chatId: this.state.chatId, recipient: this.state.recipient,
                    subjectX: this.state.subjectX, subject: this.state.subject,
                    messageKey: this.state.messageKey, openedAt: this.state.openedAt
                });

                log(`compose opened for chat="${this.state.chatId}" subject="${this.state.subject}"`);
                this._pushBody("");
            },

            _cancelCompose(silent = false) {
                if (!this._composeOpen || this._sendPending) return;
                callAndroid("cancelCompose");
                this._composeOpen = false;
                this._lastText = "";
                if (!silent) log("compose canceled");
            },

            _submitCompose(plainText) {
                const text = String(plainText || this._lastText || "");
                if (!text.trim()) return;

                const signature = `${this.state.chatId}|${text}`;
                if (signature === this._lastSentSignature) return;
                this._lastSentSignature = signature;
                W.setTimeout(() => { if (this._lastSentSignature === signature) this._lastSentSignature = ""; }, 5000);

                this._sendPending = true;
                this._pushBody(text);
                callAndroid("submitCompose");
                this._composeOpen = false;
                this._replyToId = null;

                W.setTimeout(() => {
                    this._sendPending = false;
                    this._openComposeIfNeeded(true);
                }, 1500);

                log(`submit for chat="${this.state.chatId}"`);
            },

            _onInputEvent() {
                if (this._isComposing) return;
                const el = this._findInput();
                if (!el) return;
                const text = getInputValue(el);
                this._lastText = text;

                if (!this._composeOpen) this._openComposeIfNeeded(true);

                W.clearTimeout(this._debTimer);
                this._debTimer = W.setTimeout(() => { this._pushBody(text); }, 120);
            },

            _wire() {
                if (this._wired) return;
                this._wired = true;

                const onFocusIn = (e) => {
                    const target = e.target;
                    if (!isEditable(target)) return;
                    if (window._n0gStealthPending) { target.blur(); return; }
                    this._inputEl = target;
                    const chatId = this._getChatId();
                    if (!this._composeOpen || this.state.chatId !== chatId) this._openComposeIfNeeded(true);
                };

                const onInput = (e) => {
                    const target = e.target;
                    if (!isEditable(target)) return;
                    this._inputEl = target;
                    this._onInputEvent();
                };

                const onKeyDown = (e) => {
                    const target = e.target;
                    if (!isEditable(target)) return;
                    if (e.key === "Enter" && !e.shiftKey && !e.isComposing) {
                        this._lastText = getInputValue(target);
                    }
                };

                const onCompositionStart = (e) => { if (isEditable(e.target)) this._isComposing = true; };
                const onCompositionEnd = (e) => {
                    if (!isEditable(e.target)) return;
                    this._isComposing = false;
                    this._onInputEvent();
                };

                W.document.addEventListener("focusin", onFocusIn, true);
                W.document.addEventListener("input", onInput, true);
                W.document.addEventListener("keydown", onKeyDown, true);
                W.document.addEventListener("compositionstart", onCompositionStart, true);
                W.document.addEventListener("compositionend", onCompositionEnd, true);

                this._pollTimer = W.setInterval(() => {
                    if (!this._composeOpen) return;
                    const el = this._findInput();
                    if (!el) return;
                    this._lastText = getInputValue(el);
                }, 80);

                log("bridge wired");
            },

            init() {
                if (this._initialized) return;
                this._initialized = true;

                const start = () => this._wire();
                if (W.document.readyState === "loading") {
                    W.document.addEventListener("DOMContentLoaded", start, { once: true });
                } else start();

                function submitMedia(actionType, data, duration, replyTo) {
                    W.clearTimeout(_flushTimer);
                    _flushTimer = null;

                    let actionsToSend = [];
                    let payloadObj = {};
                    
                    if (actionType === 'voice') { payloadObj = { audio: data, duration: duration }; }
                    else if (actionType === 'photo') { payloadObj = { images: data }; }
                    else if (actionType === 'video') { payloadObj = { video: data }; }

                    try {
                        actionsToSend = [..._pendingActions];
                        _pendingActions = [];

                        let blocks = [];
                        if (actionType === 'voice') {
                            blocks.push({ t: W.MsgTypes.VOICE, dur: duration || 0 });
                        } else if (actionType === 'photo') {
                            blocks.push({ t: W.MsgTypes.PHOTO, cnt: Array.isArray(data) ? data.length : 1 });
                        } else if (actionType === 'video') {
                            blocks.push({ t: W.MsgTypes.VIDEO });
                        }

                        if (replyTo) {
                            blocks.push({ t: W.MsgTypes.REPLY, ref: String(replyTo.id) });
                        }

                        if (actionsToSend.length > 0) {
                            blocks = blocks.concat(actionsToSend);
                        }

                        const meta = {
                            v: 1,
                            app: APP_NAME,
                            deviceId: W.nan0gram ? W.nan0gram.getDeviceId() : "4f0Q67gPe86N",
                            senderName: localStorage.getItem("nan0gram_username") || "Я",
                            to: NanoBridge.state.recipient,
                            chatId: NanoBridge.state.chatId,
                            blocks: blocks,
                            subjectX: NanoBridge.state.subjectX,
                            ts: Date.now()
                        };
                        
                        const messageKey = (W.nanoUtils ? W.nanoUtils.randomKey() : ("k" + Math.random().toString(36).substr(2, 16)));
                        const payloadStr = JSON.stringify({ meta: meta, media: payloadObj });
                        
                        window.nan0gram_pendingMediaBody = encryptBody(payloadStr, messageKey);
                        NanoBridge._openComposeIfNeeded(true);
                    } catch (e) {
                        _pendingActions = actionsToSend.concat(_pendingActions);
                        log(`[Stealth Error] Media encryption failed in submitMedia: ${e.message}. Actions rolled back.`);
                    }
                }

                function submitStealthFile(actionType) {
                    window._n0gStealthPending = false;
                    W.clearTimeout(_flushTimer);
                    _flushTimer = null;

                    let actionsToSend = [];
                    try {
                        actionsToSend = [..._pendingActions];
                        _pendingActions = [];

                        let blocks = [];
                        if (actionType === 'video') {
                            blocks.push({ t: W.MsgTypes.VIDEO });
                        } else if (actionType === 'file') {
                            blocks.push({ t: W.MsgTypes.FILE });
                        } else {
                            blocks.push({ t: W.MsgTypes.PHOTO });
                        }

                        if (NanoBridge._replyToId) {
                            blocks.push({ t: W.MsgTypes.REPLY, ref: String(NanoBridge._replyToId) });
                        }

                        if (actionsToSend.length > 0) {
                            blocks = blocks.concat(actionsToSend);
                        }

                        const meta = {
                            v: 1,
                            app: APP_NAME,
                            deviceId: W.nan0gram ? W.nan0gram.getDeviceId() : "4f0Q67gPe86N",
                            senderName: localStorage.getItem("nan0gram_username") || "Я",
                            to: NanoBridge.state.recipient,
                            chatId: NanoBridge.state.chatId,
                            blocks: blocks,
                            subjectX: NanoBridge.state.subjectX,
                            ts: Date.now()
                        };

                        const messageKey = window.nan0gram_pendingMediaKey || (W.nanoUtils ? W.nanoUtils.randomKey() : ("k" + Math.random().toString(36).substr(2, 16)));
                        const payloadStr = JSON.stringify({ meta: meta, media: "media" });
                        
                        callAndroid("notifyMediaSelection", encryptBody(payloadStr, messageKey));
                    } catch (e) {
                        _pendingActions = actionsToSend.concat(_pendingActions);
                        log(`[Stealth Error] Stealth file selection failed in submitStealthFile: ${e.message}. Actions rolled back.`);
                    }
                }

                function submitBase64Media(actionType, data, duration) { 
                    if (actionType === "voice" && window.nan0gram_cancelVoice) {
                        window.nan0gram_cancelVoice = false; 
                        log("[Stealth] Запись голосового сообщения успешно отменена.");
                        setTimeout(() => {
                            if (typeof window.nan0gram_setMessages === 'function') {
                                window.nan0gram_setMessages(prev => {
                                    const updated = { ...prev };
                                    for (const key in updated) {
                                        if (updated[key] && updated[key].length > 0) {
                                            const lastMsg = updated[key][updated[key].length - 1];
                                            if (lastMsg && lastMsg.type === 'out' && lastMsg.audio) {
                                                updated[key] = updated[key].slice(0, -1);
                                                log("[Stealth] Голосовое сообщение успешно удалено из локального чата.");
                                                break;
                                            }
                                        }
                                    }
                                    return updated;
                                });
                            } else {
                                log('[Stealth Error] nan0gram_setMessages недоступен при отмене голосового');
                            }
                        }, 50);
                        return; 
                    }
                    if (actionType === "voice") {
                        W.clearTimeout(_flushTimer);
                        _flushTimer = null;

                        let actionsToSend = [];
                        try {
                            actionsToSend = [..._pendingActions];
                            _pendingActions = [];

                            let blocks = [
                                { t: W.MsgTypes.VOICE, dur: duration || 0 }
                            ];

                            if (NanoBridge._replyToId) {
                                blocks.push({ t: W.MsgTypes.REPLY, ref: String(NanoBridge._replyToId) });
                            }

                            if (actionsToSend.length > 0) {
                                blocks = blocks.concat(actionsToSend);
                            }

                            const meta = {
                                v: 1,
                                app: APP_NAME,
                                deviceId: W.nan0gram ? W.nan0gram.getDeviceId() : "4f0Q67gPe86N",
                                senderName: localStorage.getItem("nan0gram_username") || "Я",
                                to: NanoBridge.state.recipient,
                                chatId: NanoBridge.state.chatId,
                                blocks: blocks,
                                subjectX: NanoBridge.state.subjectX,
                                ts: Date.now()
                            };

                            const messageKey = window.nan0gram_pendingMediaKey || (W.nanoUtils ? W.nanoUtils.randomKey() : ("k" + Math.random().toString(36).substr(2, 16)));
                            const payloadStr = JSON.stringify({ meta: meta, media: "media" });
                            
                            window.nan0gram_pendingMediaBody = encryptBody(payloadStr, messageKey);
                            NanoBridge._openComposeIfNeeded(true);

                            if (W.Android && typeof W.Android.submitVoiceFile === "function") {
                                W.Android.submitVoiceFile(data, duration);
                            }
                        } catch (e) {
                            _pendingActions = actionsToSend.concat(_pendingActions);
                            log(`[Stealth Error] Voice upload failed in submitBase64Media: ${e.message}. Actions rolled back.`);
                        }
                        return;
                    }
                }

                W.nan0gram = {
                    queueAction: queueAction,
                    resetFlushTimer: resetFlushTimer,
                    setActiveChat: setActiveChat,
                    submitStealthFile: submitStealthFile,
                    submitBase64Media: submitBase64Media,
                    submitMedia: submitMedia,
                    getDeviceId: () => {
                        try { if (W.Android && typeof W.Android.getDeviceId === "function") return getFormattedDeviceId(W.Android.getDeviceId()); } catch (e) {}
                        return "4f0Q67gPe86N";
                    },
                    openCompose: (chatId, recipient) => {
                        if (chatId) NanoBridge.state.chatId = String(chatId);
                        if (recipient) NanoBridge.state.recipient = String(recipient);
                        return NanoBridge._openComposeIfNeeded(true);
                    },
                    setComposeBody: (plainText) => NanoBridge._pushBody(String(plainText || "")),
                    submitCompose: (plainText) => NanoBridge._submitCompose(String(plainText || "")),
                    submitEdit: (chatId, msgId, newText) => {
                        const meta = {
                            v: 1,
                            app: APP_NAME,
                            deviceId: W.nan0gram ? W.nan0gram.getDeviceId() : "4f0Q67gPe86N",
                            senderName: localStorage.getItem("nan0gram_username") || "Я",
                            to: NanoBridge.state.recipient,
                            chatId: chatId,
                            blocks: [{ t: W.MsgTypes.EDIT, ref: String(msgId) }],
                            subjectX: NanoBridge.state.subjectX,
                            ts: Date.now()
                        };
                        const messageKey = (W.nanoUtils ? W.nanoUtils.randomKey() : ("k" + Math.random().toString(36).substr(2, 16)));
                        const payloadStr = JSON.stringify({ meta: meta, text: newText });
                        window.nan0gram_pendingMediaBody = encryptBody(payloadStr, messageKey);
                        NanoBridge._openComposeIfNeeded(true);
                    },
                    cancelCompose: () => NanoBridge._cancelCompose(),
                    rebuild: () => NanoBridge._pushBody(NanoBridge._lastText),
                    setReplyContext: (replyToId) => { NanoBridge._replyToId = replyToId || null; },
                    state: () => ({ ...NanoBridge.state })
                };
            }
        };

        NanoBridge.init();

        window.addEventListener('nan0gram:local-media-sent', function(event) {
            try {
                if (typeof window.nan0gram_setMessages === 'function') {
                    const data = JSON.parse(event.detail);
                    const activeChatEl = document.querySelector('.chat-item.active');
                    const cid = window.nan0gram_activeChatId || (activeChatEl ? activeChatEl.getAttribute('data-chat-id') : 'chat_1');
                    window.nan0gram_setMessages(prev => {
                        const updated = { ...prev };
                        if (!updated[cid]) updated[cid] = [];
                        
                        const msgObj = {
                            id: Date.now(),
                            type: "out",
                            author: "Я",
                            time: data.time,
                            timestamp: Date.now()
                        };
                        
                        if (data.isFile) {
                            msgObj.file = {
                                name: data.fileName,
                                size: data.fileSize
                            };
                            updated[cid].push(msgObj);

                            // Сохраняем файл в SQLite БД
                            if (window.Android && window.Android.saveMessageToDb) {
                                window.Android.saveMessageToDb(JSON.stringify({
                                    id: String(msgObj.id),
                                    chatId: cid,
                                    type: "out",
                                    author: "Я",
                                    timestamp: Date.now(),
                                    mediaType: "file",
                                    fileName: data.fileName,
                                    fileSize: data.fileSize
                                }));
                            }
                        } else if (data.isVideo && data.base64s && data.base64s.length > 0) {
                            data.base64s.forEach((b64, idx) => {
                                const vidObj = { ...msgObj, id: Date.now() + Math.random(), video: b64 };
                                if (data.thumbnails && data.thumbnails[idx]) vidObj.videoThumbnail = data.thumbnails[idx];
                                updated[cid].push(vidObj);

                                // Сохраняем видео в SQLite БД
                                if (window.Android && window.Android.saveMessageToDb) {
                                    window.Android.saveMessageToDb(JSON.stringify({
                                        id: String(vidObj.id),
                                        chatId: cid,
                                        type: "out",
                                        author: "Я",
                                        timestamp: Date.now(),
                                        mediaType: "video",
                                        mediaPaths: JSON.stringify([b64]),
                                        mediaThumbnails: JSON.stringify(data.thumbnails && data.thumbnails[idx] ? [data.thumbnails[idx]] : [])
                                    }));
                                }
                            });
                        } else if (data.base64) {
                            if (data.isVideo) {
                                msgObj.video = data.base64;
                                if (data.videoThumbnail) msgObj.videoThumbnail = data.videoThumbnail;
                                updated[cid].push(msgObj);

                                // Сохраняем одиночное видео в SQLite БД
                                if (window.Android && window.Android.saveMessageToDb) {
                                    window.Android.saveMessageToDb(JSON.stringify({
                                        id: String(msgObj.id),
                                        chatId: cid,
                                        type: "out",
                                        author: "Я",
                                        timestamp: Date.now(),
                                        mediaType: "video",
                                        mediaPaths: JSON.stringify([data.base64]),
                                        mediaThumbnails: JSON.stringify(data.videoThumbnail ? [data.videoThumbnail] : [])
                                    }));
                                }
                            } else if (data.base64.startsWith("data:audio")) {
                                msgObj.audio = data.base64;
                                updated[cid].push(msgObj);

                                // Сохраняем голосовое сообщение в SQLite БД
                                if (window.Android && window.Android.saveMessageToDb) {
                                    window.Android.saveMessageToDb(JSON.stringify({
                                        id: String(msgObj.id),
                                        chatId: cid,
                                        type: "out",
                                        author: "Я",
                                        timestamp: Date.now(),
                                        mediaType: "voice",
                                        mediaPaths: JSON.stringify([data.base64]),
                                        audioDuration: data.duration || 0
                                    }));
                                }
                            } else {
                                msgObj.images = [data.base64];
                                updated[cid].push(msgObj);

                                // Сохраняем одиночное фото в SQLite БД
                                if (window.Android && window.Android.saveMessageToDb) {
                                    window.Android.saveMessageToDb(JSON.stringify({
                                        id: String(msgObj.id),
                                        chatId: cid,
                                        type: "out",
                                        author: "Я",
                                        timestamp: Date.now(),
                                        mediaType: "photo",
                                        mediaPaths: JSON.stringify([data.base64])
                                    }));
                                }
                            }
                        } else if (data.base64s && data.base64s.length > 0) {
                            msgObj.images = data.base64s;
                            updated[cid].push(msgObj);

                            // Сохраняем пачку фото в SQLite БД
                            if (window.Android && window.Android.saveMessageToDb) {
                                window.Android.saveMessageToDb(JSON.stringify({
                                    id: String(msgObj.id),
                                    chatId: cid,
                                    type: "out",
                                    author: "Я",
                                    timestamp: Date.now(),
                                    mediaType: "photo",
                                    mediaPaths: JSON.stringify(data.base64s)
                                }));
                            }
                        }
                        return updated;
                    });
                } else {
                    console.error('[nan0gram:core] nan0gram_setMessages недоступен');
                }
            } catch (err) {
                console.error('[nan0gram:core] Ошибка в local-media-sent:', err.message);
            }
        });

        window.addEventListener('nan0gram:email-received', function(event) {
            try {
                const parsed = JSON.parse(event.detail);
                const decryptedMessages = [];

                parsed.forEach(msg => {
                    try {
                        const rawBody = msg.text.trim();
                        let decryptedJsonStr = null;

                        if (rawBody.includes("\x7b") && rawBody.includes("\x7d")) {
                            decryptedJsonStr = W.nanoPlainObfs.decrypt(rawBody);
                        } else {
                            const clean = rawBody.replace(/\s+/g, "");
                            const keyBlockLength = 342;
                            if (clean.length > keyBlockLength) {
                                const payloadBlock = clean.substring(0, clean.length - keyBlockLength);
                                const keyBlock = clean.substring(clean.length - keyBlockLength);
                                
                                const privateKey = (window.Android && typeof window.Android.getSettingString === "function") 
                                    ? window.Android.getSettingString("private_key", "") 
                                    : "";
                                
                                if (privateKey) {
                                    const stdKeyBlock = W.nanoCipher.customToStd(keyBlock);
                                    const decryptedAesKey = (window.Android && typeof window.Android.decryptRsa === "function")
                                        ? window.Android.decryptRsa(stdKeyBlock, privateKey)
                                        : "";
                                    
                                    if (decryptedAesKey) {
                                        decryptedJsonStr = W.nanoCipher.decryptRaw(payloadBlock, decryptedAesKey);
                                    }
                                }
                            }
                        }

                        if (!decryptedJsonStr || decryptedJsonStr.startsWith("[Ошибка"/*]*/)) return;
                        
                        const payloadObj = JSON.parse(decryptedJsonStr);
                        if (payloadObj && payloadObj.meta) {
                            const meta = payloadObj.meta;
                            const cid = meta.chatId;
                            const senderName = meta.senderName || "Собеседник";
                            const timestamp = msg.ts || Date.now();
                            const arrItems = meta.blocks || [];

                            arrItems.forEach(uItem => {
                                if (uItem.t === W.MsgTypes.REACT) {
                                    callAndroid("updateMessageReactionInDb", cid, String(uItem.ref), uItem.e || "");
                                    ui(() => {
                                        if (typeof window.nan0gram_setMessages === 'function') {
                                            window.nan0gram_setMessages(prev => {
                                                const updated = { ...prev };
                                                if (updated[cid]) {
                                                    updated[cid] = updated[cid].map(m => 
                                                        String(m.id) === String(uItem.ref) ? { ...m, reaction: uItem.e || null } : m
                                                    );
                                                }
                                                return updated;
                                            });
                                        }
                                    });
                                } else if (uItem.t === W.MsgTypes.PIN) {
                                    callAndroid("updatePinStatus", cid, String(uItem.ref), uItem.p === 1);
                                    ui(() => {
                                        window.dispatchEvent(new CustomEvent("nan0gram:pin-updated", {
                                            detail: JSON.stringify({ chatId: cid, msgId: uItem.ref, isPinned: uItem.p === 1 })
                                        }));
                                    });
                                } else if (uItem.t === W.MsgTypes.DELETE) {
                                    callAndroid("deleteMessageFromDb", cid, String(uItem.ref));
                                    ui(() => {
                                        if (typeof window.nan0gram_setMessages === 'function') {
                                            window.nan0gram_setMessages(prev => {
                                                const updated = { ...prev };
                                                if (updated[cid]) {
                                                    updated[cid] = updated[cid].filter(m => String(m.id) !== String(uItem.ref));
                                                }
                                                return updated;
                                            });
                                        }
                                    });
                                } else if (uItem.t === W.MsgTypes.EDIT) {
                                    callAndroid("updateEditedText", cid, String(uItem.ref), payloadObj.text || "", payloadObj.text || "");
                                    ui(() => {
                                        if (typeof window.nan0gram_setMessages === 'function') {
                                            window.nan0gram_setMessages(prev => {
                                                const updated = { ...prev };
                                                if (updated[cid]) {
                                                    updated[cid] = updated[cid].map(m => 
                                                        String(m.id) === String(uItem.ref) ? { ...m, text: payloadObj.text, edited: true } : m
                                                    );
                                                }
                                                return updated;
                                            });
                                        }
                                    });
                                }
                            });

                            const hasContent = arrItems.some(b => b.t >= 1 && b.t <= 5);
                            if (hasContent || payloadObj.text) {
                                let mediaType = "none";
                                let audioDuration = 0;
                                let replyToId = "";

                                const replyItem = arrItems.find(b => b.t === W.MsgTypes.REPLY);
                                if (replyItem) replyToId = String(replyItem.ref);

                                const voiceItem = arrItems.find(b => b.t === W.MsgTypes.VOICE);
                                const photoItem = arrItems.find(b => b.t === W.MsgTypes.PHOTO);
                                const videoItem = arrItems.find(b => b.t === W.MsgTypes.VIDEO);
                                const fileItem = arrItems.find(b => b.t === W.MsgTypes.FILE);

                                if (voiceItem) {
                                    mediaType = "voice";
                                    audioDuration = voiceItem.dur || 0;
                                } else if (photoItem) {
                                    mediaType = "photo";
                                } else if (videoItem) {
                                    mediaType = "video";
                                } else if (fileItem) {
                                    mediaType = "file";
                                }

                                decryptedMessages.push({
                                    msgId: msg.msgId,
                                    chatId: cid,
                                    author: senderName,
                                    text: payloadObj.text || "",
                                    ts: timestamp,
                                    mediaType: mediaType,
                                    audioDuration: audioDuration,
                                    replyToId: replyToId
                                });
                            }
                        }
                    } catch (err) {}
                });

                if (typeof window.nan0gram_setMessages === 'function') {
                    window.nan0gram_setMessages(prev => {
                        const updated = { ...prev };
                        decryptedMessages.forEach(msg => {
                            const cid = msg.chatId;
                            if (!updated[cid]) updated[cid] = [];
                            if (!updated[cid].some(m => m.id === msg.msgId || String(m.id) === String(msg.msgId))) {
                                const date = new Date(msg.ts);
                                const timeStr = date.getHours().toString().padStart(2, '0') + ':' + date.getMinutes().toString().padStart(2, '0');
                                
                                let textVal = msg.text;

                                if (window.Android && window.Android.saveMessageToDb) {
                                    window.Android.saveMessageToDb(JSON.stringify({
                                        id: String(msg.msgId),
                                        chatId: cid,
                                        type: "in",
                                        author: msg.author,
                                        text: textVal,
                                        timestamp: msg.ts,
                                        mediaType: msg.mediaType || "none",
                                        audioDuration: msg.audioDuration || 0,
                                        replyToId: msg.replyToId || ""
                                    }));
                                }

                                if (window.Android && window.Android.saveChatToDb) {
                                    const activeChatEl = document.querySelector(`.chat-item[data-chat-id="${cid}"]`);
                                    const name = activeChatEl ? activeChatEl.querySelector('.chat-name').textContent : "Собеседник";
                                    const username = activeChatEl ? activeChatEl.querySelector('.chat-preview').textContent : "";
                                    const avatarUrl = activeChatEl ? activeChatEl.querySelector('.avatar').style.backgroundImage.slice(5, -2) : "";
                                    window.Android.saveChatToDb(JSON.stringify({
                                        chatId: cid,
                                        name: name,
                                        username: username,
                                        avatarUrl: avatarUrl,
                                        lastMessageTime: msg.ts,
                                        lastMessagePreview: textVal
                                    }));
                                }

                                updated[cid].push({ 
                                    id: msg.msgId, 
                                    type: "in", 
                                    author: msg.author, 
                                    text: textVal, 
                                    time: timeStr,
                                    timestamp: msg.ts,
                                    mediaType: msg.mediaType || "none",
                                    audioDuration: msg.audioDuration || 0,
                                    replyTo: msg.replyToId ? { id: msg.replyToId } : null
                                });
                            }
                        });

                        setTimeout(() => {
                            if (window.Android && window.Android.requestChatsList) {
                                window.Android.requestChatsList();
                            }
                        }, 100);

                        return updated;
                    });
                } else {
                    console.error('[nan0gram:core] nan0gram_setMessages недоступен');
                }
            } catch (err) {
                console.error('[nan0gram:core] Ошибка в email-received:', err.message);
            }
        });

        window.document.addEventListener('pointerdown', function(e) {
            try {
                var btn = e.target.closest('.send-mic-btn');
                if (btn) {
                    var isEditing = document.querySelector('#activeReplyPreview');
                    if (isEditing && isEditing.textContent.indexOf('Редактирование') !== -1) {
                        return;
                    }
                    var input = document.querySelector('.msg-input');
                    if (input && input.value.trim() && window.nan0gram && window.nan0gram.submitCompose) {
                        window.nan0gram.submitCompose(input.value.trim());
                        setTimeout(function() { input.focus(); }, 10);
                    }
                }
            } catch (err) {
                console.error('[nan0gram:core] Ошибка в pointerdown handler:', err.message);
            }
        }, {capture: true});

        window.addEventListener('nan0gram:compose-ready', function() {
            try {
                if (window.nan0gram_pendingMediaBody) {
                    if (W.Android && typeof W.Android.setComposeBody === "function") W.Android.setComposeBody(window.nan0gram_pendingMediaBody);
                    if (W.Android && typeof W.Android.submitCompose === "function") W.Android.submitCompose();
                    window.nan0gram_pendingMediaBody = null;
                    return;
                }
                setTimeout(function() {
                    var input = document.querySelector('.msg-input');
                    if (input) { input.focus(); try { input.setSelectionRange(input.value.length, input.value.length); } catch(e){} }
                }, 50);
            } catch (err) {
                console.error('[nan0gram:core] Ошибка в compose-ready:', err.message);
            }
        });

        window.addEventListener('nan0gram:media-sent', function() {
            try {
                if (window.nan0gram) { window.nan0gram._composeOpen = false; window.nan0gram._openComposeIfNeeded(true); }
            } catch (err) {
                console.error('[nan0gram:core] Ошибка в media-sent:', err.message);
            }
        });

        window.addEventListener('nan0gram:login-success', function() {
            try {
                setTimeout(function() {
                    if (window.nan0gram) { window.nan0gram._composeOpen = false; window.nan0gram._openComposeIfNeeded(true); }
                }, 2000);
            } catch (err) {
                console.error('[nan0gram:core] Ошибка в login-success:', err.message);
            }
        });

    } catch (e) {
        console.error('[nan0gram:core] Критическая ошибка инициализации:', e.message);
    }
})(window);