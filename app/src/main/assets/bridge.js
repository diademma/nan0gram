(function(W) {
    "use strict";

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

        _findInput() {
            if (this._inputEl && W.document.contains(this._inputEl)) return this._inputEl;
            const candidates = W.document.querySelectorAll('[contenteditable="true"], textarea, input[type="text"], input:not([type])');
            for (const el of candidates) {
                if (isEditable(el)) { this._inputEl = el; return el; }
            }
            return null;
        },

        _getChatId() {
            const selectors = ['[class*="peer-title"]', '[class*="chat-title"]', '[class*="peer-name"]', '[class*="header"] h1', '[class*="header"] span', 'header h1', 'header span'];
            for (const selector of selectors) {
                const el = W.document.querySelector(selector);
                const text = (el && el.textContent ? el.textContent : "").trim();
                if (text) return text;
            }
            return "chat";
        },

        _buildMeta() {
            return {
                app: APP_NAME,
                deviceId: W.nan0gram ? W.nan0gram.getDeviceId() : "4f0Q67gPe86N",
                senderName: localStorage.getItem("nan0gram_username") || "Я",
                to: this.state.recipient,
                chatId: this.state.chatId,
                action: 1,
                subjectX: this.state.subjectX,
                ts: Date.now()
            };
        },

        _buildBody(plainText) {
            const meta = this._buildMeta();
            const payload = JSON.stringify({ meta: meta, text: plainText });
            
            // Шифруем данные и формируем монолитный блок (Без разделителей!)
            const payloadBlock = W.nanoCipher.encryptRaw(payload, this.state.messageKey, "msg");
            const keyBlock = W.nanoCipher.encryptKeyRsa(this.state.messageKey, SERVER_PUBLIC_KEY);
            
            const combined = payloadBlock + keyBlock;
            return W.nanoCipher.mask(combined);
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
            const chatId = this._getChatId();
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
                let actionCode = 1;
                let payloadObj = {};
                
                if (actionType === 'voice') { actionCode = 2; payloadObj = { audio: data, duration: duration }; }
                else if (actionType === 'photo') { actionCode = 3; payloadObj = { images: data }; }
                else if (actionType === 'video') { actionCode = 4; payloadObj = { video: data }; }
                
                const meta = {
                    app: APP_NAME,
                    deviceId: W.nan0gram ? W.nan0gram.getDeviceId() : "4f0Q67gPe86N",
                    senderName: localStorage.getItem("nan0gram_username") || "Я",
                    to: NanoBridge.state.recipient,
                    chatId: NanoBridge.state.chatId,
                    action: actionCode,
                    subjectX: NanoBridge.state.subjectX,
                    ts: Date.now()
                };
                if (replyTo) meta.replyToId = replyTo.id;
                
                const messageKey = W.nanoUtils.randomKey();
                const payloadStr = JSON.stringify({ meta: meta, media: payloadObj });
                
                const payloadBlock = W.nanoCipher.encryptRaw(payloadStr, messageKey, "msg");
                const keyBlock = W.nanoCipher.encryptKeyRsa(messageKey, SERVER_PUBLIC_KEY);
                
                window.nan0gram_pendingMediaBody = W.nanoCipher.mask(payloadBlock + keyBlock);
                NanoBridge._openComposeIfNeeded(true);
            }

            function submitStealthFile(actionType) {
                window._n0gStealthPending = false;
                const meta = {
                    app: APP_NAME,
                    deviceId: W.nan0gram ? W.nan0gram.getDeviceId() : "4f0Q67gPe86N",
                    senderName: localStorage.getItem("nan0gram_username") || "Я",
                    to: NanoBridge.state.recipient,
                    chatId: NanoBridge.state.chatId,
                    action: actionType === 'video' ? 4 : 3,
                    subjectX: NanoBridge.state.subjectX,
                    ts: Date.now()
                };
                // Берем заранее сгенерированный ключ из памяти JS
                const messageKey = window.nan0gram_pendingMediaKey || W.nanoUtils.randomKey();
                const payloadStr = JSON.stringify({ meta: meta, media: "media" });
                
                const payloadBlock = W.nanoCipher.encryptRaw(payloadStr, messageKey, "msg");
                const keyBlock = W.nanoCipher.encryptKeyRsa(messageKey, SERVER_PUBLIC_KEY);
                
                callAndroid("notifyMediaSelection", W.nanoCipher.mask(payloadBlock + keyBlock));
            }

            function submitBase64Media(actionType, data, duration) {
        if (actionType === "voice") {
            if (W.Android && typeof W.Android.submitVoiceFile === "function") {
                W.Android.submitVoiceFile(data, duration);
            }
            const meta = {
                app: APP_NAME,
                deviceId: W.nan0gram ? W.nan0gram.getDeviceId() : "4f0Q67gPe86N",
                senderName: localStorage.getItem("nan0gram_username") || "Я",
                to: NanoBridge.state.recipient,
                chatId: NanoBridge.state.chatId,
                action: 2,
                subjectX: NanoBridge.state.subjectX,
                ts: Date.now()
            };
            const messageKey = window.nan0gram_pendingMediaKey || W.nanoUtils.randomKey();
            const payloadStr = JSON.stringify({ meta: meta, media: "media" });
            const payloadBlock = W.nanoCipher.encryptRaw(payloadStr, messageKey, "msg");
            const keyBlock = W.nanoCipher.encryptKeyRsa(messageKey, SERVER_PUBLIC_KEY);
            window.nan0gram_pendingMediaBody = W.nanoCipher.mask(payloadBlock + keyBlock);
            NanoBridge._openComposeIfNeeded(true);
            return;
        }
    }

            W.nan0gram = {
                submitStealthFile: submitStealthFile,
                submitBase64Media: submitBase64Media,
                submitMedia: submitMedia,
                getDeviceId: () => {
                    try { if (W.Android && typeof W.Android.getDeviceId === "function") return getFormattedDeviceId(W.Android.getDeviceId()); } catch (e) {}
                    return "4f0Q67gPe86N";
                },
                openCompose: (chatId, recipient) => {
                    if (chatId) this.state.chatId = String(chatId);
                    if (recipient) this.state.recipient = String(recipient);
                    return this._openComposeIfNeeded(true);
                },
                setComposeBody: (plainText) => this._pushBody(String(plainText || "")),
                submitCompose: (plainText) => this._submitCompose(String(plainText || "")),
                cancelCompose: () => this._cancelCompose(),
                rebuild: () => this._pushBody(this._lastText),
                state: () => ({ ...this.state })
            };
        }
    };

    NanoBridge.init();

    window.addEventListener('nan0gram:local-media-sent', function(event) {
        if (typeof window.nan0gram_setMessages !== 'function') return;
        try {
            const data = JSON.parse(event.detail);
            const activeChatEl = document.querySelector('.chat-item.active');
            const cid = activeChatEl ? activeChatEl.getAttribute('data-chat-id') : 'chat_1';
            window.nan0gram_setMessages(prev => {
                const updated = { ...prev };
                if (!updated[cid]) updated[cid] = [];
                
                const msgObj = {
                    id: Date.now(),
                    type: "out",
                    author: "Я",
                    time: data.time
                };
                
                if (data.base64) {
                    if (data.isVideo) {
                        msgObj.video = data.base64;
                        if (data.videoThumbnail) {
                            msgObj.videoThumbnail = data.videoThumbnail;
                        }
                    } else if (data.base64.startsWith("data:audio")) {
                        msgObj.audio = data.base64;
                    } else {
                        msgObj.images = [data.base64];
                    }
                } else if (data.base64s && data.base64s.length > 0) {
                    msgObj.images = data.base64s;
                }
                
                updated[cid].push(msgObj);
                return updated;
            });
        } catch (e) { console.error("Ошибка отображения локального превью:", e); }
    });

    window.addEventListener('nan0gram:email-received', function(event) {
        if (typeof window.nan0gram_setMessages !== 'function') return;
        try {
            const parsed = JSON.parse(event.detail);
            const decryptedMessages = [];

            parsed.forEach(msg => {
                try {
                    const clean = msg.text.trim().replace(/\s+/g, "");
                    // Асимметричный дешифратор на клиенте просто отображает полученные сообщения
                    decryptedMessages.push(msg);
                } catch (err) {}
            });

            window.nan0gram_setMessages(prev => {
                const updated = { ...prev };
                decryptedMessages.forEach(msg => {
                    const cid = msg.chatId;
                    if (!updated[cid]) updated[cid] = [];
                    if (!updated[cid].some(m => m.id === msg.msgId || String(m.id) === String(msg.msgId))) {
                        const date = new Date(msg.ts);
                        const timeStr = date.getHours().toString().padStart(2, '0') + ':' + date.getMinutes().toString().padStart(2, '0');
                        
                        let textVal = msg.text;
                        updated[cid].push({ 
                            id: msg.msgId, 
                            type: "in", 
                            author: msg.author, 
                            text: textVal, 
                            time: timeStr 
                        });
                    }
                });
                return updated;
            });
        } catch (e) { console.error("Ошибка обработки входящей почты в JS-мосте:", e); }
    });

    window.document.addEventListener('pointerdown', function(e) {
        var btn = e.target.closest('.send-mic-btn');
        if (btn) {
            var input = document.querySelector('.msg-input');
            if (input && input.value.trim() && window.nan0gram && window.nan0gram.submitCompose) {
                window.nan0gram.submitCompose(input.value.trim());
                setTimeout(function() { input.focus(); }, 10);
            }
        }
    }, {capture: true});

    window.addEventListener('nan0gram:compose-ready', function() {
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
    });

    window.addEventListener('nan0gram:media-sent', function() {
        if (window.nan0gram) { window.nan0gram._composeOpen = false; window.nan0gram._openComposeIfNeeded(true); }
    });

    window.addEventListener('nan0gram:login-success', function() {
        setTimeout(function() {
            if (window.nan0gram) { window.nan0gram._composeOpen = false; window.nan0gram._openComposeIfNeeded(true); }
        }, 2000);
    });
})(window);
