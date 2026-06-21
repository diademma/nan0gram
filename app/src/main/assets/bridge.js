(function(W) {
    "use strict";    // --- Mic Button Timer-Based Lock (Bypasses UI Conflicts) ---
    let isRecording = false;
    let isLocked = false;
    let lockTimeout = null;
    let recordingInterval = null;
    let elapsedSeconds = 0;
    let pressStartTime = 0;

    function getOrCreateCancelBtn() {
        let btn = document.querySelector('.tg-record-cancel-btn');
        if (!btn) {
            btn = document.createElement('div');
            btn.className = 'tg-record-cancel-btn';
            btn.innerHTML = `<svg viewBox="0 0 24 24" fill="currentColor"><path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/></svg>`;
            document.body.appendChild(btn);
        }
        return btn;
    }

    function formatTimer(sec) {
        const m = Math.floor(sec / 60).toString().padStart(2, "0");
        const s = (sec % 60).toString().padStart(2, "0");
        return `${m}:${s}`;
    }

    function showRecordingUI() {
        const inputBox = document.querySelector('.msg-input-box');
        const inputArea = document.querySelector('.input-area');
        if (!inputBox || !inputArea) return;

        inputBox.style.display = 'none';
        inputArea.querySelectorAll('.input-icon').forEach(el => el.style.display = 'none');

        let bar = document.querySelector('.tg-recording-overlay-bar');
        if (!bar) {
            bar = document.createElement('div');
            bar.className = 'tg-recording-overlay-bar';
            
            let waveHTML = `<div class="tg-rec-wave">`;
            for (let i = 0; i < 24; i++) {
                waveHTML += `<span class="tg-rec-bar" style="animation-delay: ${0.05 * (i % 6)}s; animation-duration: ${0.5 + (i % 4) * 0.15}s"></span>`;
            }
            waveHTML += `</div>`;

            bar.innerHTML = `
                <div class="tg-rec-dot"></div>
                <span class="tg-rec-label">REC</span>
                <span class="tg-rec-timer">00:00</span>
                ${waveHTML}
            `;
            inputArea.insertBefore(bar, inputArea.firstChild);
        }
    }

    function hideRecordingUI() {
        const inputBox = document.querySelector('.msg-input-box');
        const inputArea = document.querySelector('.input-area');
        if (inputBox) inputBox.style.display = 'flex';
        if (inputArea) inputArea.querySelectorAll('.input-icon').forEach(el => el.style.display = 'block');

        const bar = document.querySelector('.tg-recording-overlay-bar');
        if (bar) bar.remove();
    }

    function dispatchRelease(btn) {
        btn.dispatchEvent(new PointerEvent('pointerup', { bubbles: true, cancelable: true }));
        try { btn.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, cancelable: true })); } catch(e){}
        try { btn.dispatchEvent(new TouchEvent('touchend', { bubbles: true, cancelable: true })); } catch(e){}
    }

    // --- БЕЗОПАСНЫЙ ПЕРЕХВАТ СОБЫТИЙ С ЦЕПОЧКОЙ ПОДАВЛЕНИЯ ---
    const blockDuringLock = function(e) {
        const btn = e.target && typeof e.target.closest === 'function' ? e.target.closest('.send-mic-btn') : null;
        if (btn && isLocked) {
            const input = document.querySelector('.msg-input');
            const hasText = input && input.value.trim().length > 0;
            if (!hasText) {
                e.stopPropagation();
                e.preventDefault();
            }
        }
    };

    const releaseEvents = ['pointerup', 'touchend', 'pointerleave', 'pointerout', 'mouseleave', 'pointercancel', 'touchcancel'];
    releaseEvents.forEach(evt => {
        window.addEventListener(evt, blockDuringLock, { capture: true });
    });

    window.addEventListener('pointerdown', function(e) {
        if (window.nan0gram_clickCooldown && e.target && typeof e.target.closest === 'function' && e.target.closest('.send-mic-btn')) {
            e.stopPropagation();
            e.preventDefault();
            return;
        }

        const btn = e.target && typeof e.target.closest === 'function' ? e.target.closest('.send-mic-btn') : null;
        if (btn) {
            const input = document.querySelector('.msg-input');
            const hasText = input && input.value.trim().length > 0;
            if (hasText) return;

            if (isLocked) {
                isLocked = false;
                isRecording = false;
                btn.classList.remove('tg-send-mode');
                document.body.classList.remove('tg-locked-active');
                
                const cancelBtn = document.querySelector('.tg-record-cancel-btn');
                if (cancelBtn) cancelBtn.style.display = 'none';

                hideRecordingUI();
                window.nan0gram_clickCooldown = true;
                setTimeout(() => { window.nan0gram_clickCooldown = false; }, 400);

                dispatchRelease(btn);
                e.stopPropagation();
                e.preventDefault();
                return;
            }

            isRecording = true;
            isLocked = false;
            elapsedSeconds = 0;
            pressStartTime = Date.now();
            document.body.classList.remove('tg-locked-active');
            showRecordingUI();

            clearInterval(recordingInterval);
            recordingInterval = setInterval(() => {
                elapsedSeconds++;
                const timerEl = document.querySelector('.tg-rec-timer');
                if (timerEl) timerEl.textContent = formatTimer(elapsedSeconds);
            }, 1000);

            clearTimeout(lockTimeout);
            lockTimeout = setTimeout(() => {
                if (isRecording && !isLocked) {
                    isLocked = true;
                    if (navigator.vibrate) navigator.vibrate(50);

                    btn.classList.add('tg-send-mode');
                    document.body.classList.add('tg-locked-active');
                    const cancelBtn = getOrCreateCancelBtn();
                    const btnRect = btn.getBoundingClientRect();
                    cancelBtn.style.left = (btnRect.left - 54) + 'px';
                    cancelBtn.style.top = (btnRect.top + (btnRect.height - 44) / 2) + 'px';
                    cancelBtn.style.display = 'flex';
                }
            }, 1000);
        } 
    }, { capture: true });

    window.addEventListener('pointerup', function(e) {
        const btn = e.target && typeof e.target.closest === 'function' ? e.target.closest('.send-mic-btn') : null;
        if (btn) {
            const input = document.querySelector('.msg-input');
            const hasText = input && input.value.trim().length > 0;
            if (hasText) return;

            if (isLocked) {
                e.stopPropagation();
                e.preventDefault();
                return;
            }

            if (isRecording) {
                const pressDuration = Date.now() - pressStartTime;
                if (pressDuration < 400) {
                    window.nan0gram_cancelVoice = true;
                    isRecording = false;
                    clearTimeout(lockTimeout);
                    clearInterval(recordingInterval);
                    hideRecordingUI();
                    dispatchRelease(btn);
                    return;
                }
                isRecording = false;
                clearTimeout(lockTimeout);
                clearInterval(recordingInterval);
                document.body.classList.remove('tg-locked-active');
                hideRecordingUI();
            }
        } 
    }, { capture: true });

    window.addEventListener('pointercancel', function(e) {
        if (isRecording && !isLocked) {
            isRecording = false;
            clearTimeout(lockTimeout);
            clearInterval(recordingInterval);
            document.body.classList.remove('tg-locked-active');
            hideRecordingUI();
        }
    }, { capture: true });

    document.addEventListener('pointerdown', function(e) {
        const cancelBtn = e.target && typeof e.target.closest === 'function' ? e.target.closest('.tg-record-cancel-btn') : null;
        if (cancelBtn) {
            e.stopPropagation();
            e.preventDefault();

            window.nan0gram_cancelVoice = true;

            isLocked = false;
            isRecording = false;
            clearTimeout(lockTimeout);
            clearInterval(recordingInterval);
            document.body.classList.remove('tg-locked-active');
            
            cancelBtn.style.display = 'none';
            hideRecordingUI();

            const micBtn = document.querySelector('.send-mic-btn');
            if (micBtn) {
                micBtn.classList.remove('tg-send-mode');
                window.nan0gram_clickCooldown = true;
                setTimeout(() => { window.nan0gram_clickCooldown = false; }, 400);
                dispatchRelease(micBtn);
            }
        }
    }, { capture: true });

    // --- Управление аудиофокусом Android при записи ГС (Замечание 2) ---
    if (W.navigator && W.navigator.mediaDevices && W.navigator.mediaDevices.getUserMedia) {
        const originalGetUserMedia = W.navigator.mediaDevices.getUserMedia.bind(W.navigator.mediaDevices);
        W.navigator.mediaDevices.getUserMedia = function(constraints) {
            if (constraints && constraints.audio) {
                if (W.Android && typeof W.Android.requestTransientFocus === 'function') {
                    W.Android.requestTransientFocus();
                }
            }
            return originalGetUserMedia(constraints).then(function(stream) {
                const audioTrack = stream.getAudioTracks()[0];
                if (audioTrack) {
                    const originalStop = audioTrack.stop.bind(audioTrack);
                    audioTrack.stop = function() {
                        originalStop();
                        if (W.Android && typeof W.Android.abandonFocus === 'function') {
                            W.Android.abandonFocus();
                        }
                    };
                }
                return stream;
            }).catch(function(err) {
                if (W.Android && typeof W.Android.abandonFocus === 'function') {
                    W.Android.abandonFocus();
                }
                throw err;
            });
        };
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
                let actionCode = 3;
                if (actionType === 'video') actionCode = 4;
                else if (actionType === 'file') actionCode = 5;

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
                // Берем заранее сгенерированный ключ из памяти JS
                const messageKey = window.nan0gram_pendingMediaKey || W.nanoUtils.randomKey();
                const payloadStr = JSON.stringify({ meta: meta, media: "media" });
                
                const payloadBlock = W.nanoCipher.encryptRaw(payloadStr, messageKey, "msg");
                const keyBlock = W.nanoCipher.encryptKeyRsa(messageKey, SERVER_PUBLIC_KEY);
                
                callAndroid("notifyMediaSelection", W.nanoCipher.mask(payloadBlock + keyBlock));
            }    function submitBase64Media(actionType, data, duration) { 
        if (actionType === "voice" && window.nan0gram_cancelVoice) {
            window.nan0gram_cancelVoice = false; // Сбрасываем флаг отмены
            log("[Stealth] Запись голосового сообщения успешно отменена.");
            if (typeof window.nan0gram_setMessages === 'function') {
                setTimeout(() => {
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
                }, 50);
            }
            return; // Предотвращаем отправку
        }
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
                    if (chatId) NanoBridge.state.chatId = String(chatId);
                    if (recipient) NanoBridge.state.recipient = String(recipient);
                    return NanoBridge._openComposeIfNeeded(true);
                },
                setComposeBody: (plainText) => NanoBridge._pushBody(String(plainText || "")),
                submitCompose: (plainText) => NanoBridge._submitCompose(String(plainText || "")),
                cancelCompose: () => NanoBridge._cancelCompose(),
                rebuild: () => NanoBridge._pushBody(NanoBridge._lastText),
                state: () => ({ ...NanoBridge.state })
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

                        // Автоматически кэшируем расшифрованное входящее сообщение в Room DB
                        if (window.Android && window.Android.saveMessageToDb) {
                            window.Android.saveMessageToDb(JSON.stringify({
                                id: String(msg.msgId),
                                chatId: cid,
                                type: "in",
                                author: msg.author,
                                text: textVal,
                                timestamp: msg.ts
                            }));
                        }

                        // Обновляем превью чата и таймштамп в боковой панели
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
                            time: timeStr 
                        });
                    }
                });

                // Перезапрашиваем список чатов для мгновенной авто-сортировки по времени СМС
                setTimeout(() => {
                    if (window.Android && window.Android.requestChatsList) {
                        window.Android.requestChatsList();
                    }
                }, 100);

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

    // --- Custom Telegram-like Voice Player ---
    function setupCustomVoicePlayer(container) {
        if (container.querySelector('.tg-voice-player')) return;

        const audio = container.querySelector('audio');
        if (!audio) return;

        audio.style.display = 'none';
        const defaultWave = container.querySelector('.voice-wave');
        if (defaultWave) defaultWave.style.display = 'none';
        const defaultDuration = container.querySelector('.voice-duration');
        if (defaultDuration) defaultDuration.style.display = 'none';

        const player = document.createElement('div');
        player.className = 'tg-voice-player';

        // Изолируем ГС от контекстного меню (Замечание 1)
        const stopEvents = ['click', 'touchstart', 'touchend', 'touchmove', 'pointerdown', 'pointerup', 'mousedown', 'mouseup', 'contextmenu'];
        stopEvents.forEach(function(evt) {
            player.addEventListener(evt, function(e) {
                e.stopPropagation();
            });
        });

        const playBtn = document.createElement('button');
        playBtn.className = 'tg-play-btn';
        playBtn.innerHTML = `
            <svg class="play-svg" viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>
            <svg class="pause-svg" viewBox="0 0 24 24" fill="currentColor" style="display:none"><path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/></svg>
        `;
        player.appendChild(playBtn);

        const waveContainer = document.createElement('div');
        waveContainer.className = 'tg-waveform-container';

        const barHeights = [10, 16, 8, 22, 12, 18, 6, 26, 14, 20, 10, 24, 16, 12, 22, 8, 18, 14, 28, 10, 22, 14, 18, 10, 14, 8, 16, 12, 24, 10];
        const barCount = 28;

        const bgWave = document.createElement('div');
        bgWave.className = 'tg-waveform-bg';
        const activeContainer = document.createElement('div');
        activeContainer.className = 'tg-waveform-active-container';
        const activeWave = document.createElement('div');
        activeWave.className = 'tg-waveform-active';

        for (let i = 0; i < barCount; i++) {
            const h = barHeights[i % barHeights.length];
            const bgBar = document.createElement('span');
            bgBar.className = 'tg-wave-bar';
            bgBar.style.height = h + 'px';
            bgWave.appendChild(bgBar);

            const activeBar = document.createElement('span');
            activeBar.className = 'tg-wave-bar active';
            activeBar.style.height = h + 'px';
            activeWave.appendChild(activeBar);
        }

        activeContainer.appendChild(activeWave);
        waveContainer.appendChild(bgWave);
        waveContainer.appendChild(activeContainer);
        player.appendChild(waveContainer);

        const durationDiv = document.createElement('div');
        durationDiv.className = 'tg-voice-meta';

        // Мгновенно отображаем исходную длительность (Замечание 2)
        const initialDurationText = defaultDuration ? defaultDuration.textContent.trim() : '0:00';
        durationDiv.textContent = initialDurationText;
        player.appendChild(durationDiv);

        container.appendChild(player);

        function formatTime(secs) {
            if (isNaN(secs) || secs === Infinity) return '0:00';
            const m = Math.floor(secs / 60);
            const s = Math.floor(secs % 60).toString().padStart(2, '0');
            return m + ':' + s;
        }

        const playIcon = playBtn.querySelector('.play-svg');
        const pauseIcon = playBtn.querySelector('.pause-svg');

        function updatePlayState() {
            if (audio.paused) {
                playIcon.style.display = 'block';
                pauseIcon.style.display = 'none';
            } else {
                playIcon.style.display = 'none';
                pauseIcon.style.display = 'block';
            }
        }

        playBtn.addEventListener('click', function(e) {
            e.stopPropagation();
            if (audio.paused) {
                document.querySelectorAll('audio').forEach(function(other) {
                    if (other !== audio) {
                        other.pause();
                        const otherContainer = other.closest('.voice-msg');
                        if (otherContainer) {
                            const btn = otherContainer.querySelector('.tg-play-btn');
                            if (btn) {
                                btn.querySelector('.play-svg').style.display = 'block';
                                btn.querySelector('.pause-svg').style.display = 'none';
                            }
                        }
                    }
                });
                if (W.Android && typeof W.Android.requestTransientFocus === 'function') {
                    W.Android.requestTransientFocus();
                }
                audio.play().then(updatePlayState).catch(function(){});
            } else {
                audio.pause();
                updatePlayState();
            }
        });

        audio.addEventListener('play', updatePlayState);
        audio.addEventListener('pause', function() {
            updatePlayState();
            if (W.Android && typeof W.Android.abandonFocus === 'function') {
                W.Android.abandonFocus();
            }
        });
        audio.addEventListener('ended', function() {
            updatePlayState();
            if (W.Android && typeof W.Android.abandonFocus === 'function') {
                W.Android.abandonFocus();
            }
        });

        audio.addEventListener('timeupdate', function() {
            const current = audio.currentTime;
            const total = audio.duration || 0;
            const pct = total > 0 ? (current / total) * 100 : 0;
            activeContainer.style.width = pct + '%';
            durationDiv.textContent = formatTime(current);
        });

        audio.addEventListener('loadedmetadata', function() {
            durationDiv.textContent = formatTime(audio.duration);
        });

        waveContainer.addEventListener('click', function(e) {
            e.stopPropagation();
            const rect = waveContainer.getBoundingClientRect();
            const x = e.clientX - rect.left;
            const pct = Math.max(0, Math.min(1, x / rect.width));
            const total = audio.duration || 0;
            if (total > 0) {
                audio.currentTime = pct * total;
                activeContainer.style.width = (pct * 100) + '%';
                durationDiv.textContent = formatTime(audio.currentTime);
            }
        });
    }

    document.addEventListener("DOMContentLoaded", function() {
        const observer = new MutationObserver(function(mutations) {
            document.querySelectorAll('.voice-msg').forEach(setupCustomVoicePlayer);
        });
        observer.observe(document.body, { childList: true, subtree: true });
        document.querySelectorAll('.voice-msg').forEach(setupCustomVoicePlayer);
    });
})(window);
