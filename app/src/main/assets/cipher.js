(function(W) {
    "use strict";
    try {

    W.MsgTypes = Object.freeze({
        TEXT: 1,
        VOICE: 2,
        PHOTO: 3,
        VIDEO: 4,
        FILE: 5,
        REPLY: 6,
        REACT: 7,
        PIN: 8,
        DELETE: 9,
        EDIT: 10
    });

    W.MsgFields = Object.freeze({
        REF: "ref",
        CHAT: "cid",
        EMOJI: "e",
        PIN_ACTIVE: "p",
        DUR: "dur",
        CNT: "cnt",
        NAME: "name",
        SIZE: "size"
    });

    W.APP_NAME = "nan0gram";
    W.DEFAULT_RECIPIENT = "270232@ukr.net";
    W.STORAGE = {
        lastSubjectX: "nan0gram_last_subject_x",
        session: "nan0gram_session_v1"
    };

    const ALPHABET_64 = [
        "A","B","C","D","E","F","G","H","I","J","K","L","M","N",
        "a","b","c","d","e","f","g","h","i","j","k","l","m","n",
        "А","Б","В","Г","Д","Е","Ж","З","И","Й",
        "а","б","в","г","д","е","ж","з","и","й",
        "Є","І","Ї","Ґ","є","і","ї","ґ",
        "~","•","$","€","£","¢","∆","*"
    ].join("");

    const STD64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    function std64ToCustom(b64) {
        let out = "";
        for (let i = 0; i < b64.length; i++) {
            const ch = b64[i];
            if (ch === "=") { continue; }
            const idx = STD64.indexOf(ch);
            out += idx >= 0 ? ALPHABET_64[idx] : ch;
        }
        return out;
    }

    function custom64ToStd(masked) {
        let out = "";
        for (let i = 0; i < masked.length; i++) {
            const ch = masked[i];
            if (ch === "=") { continue; }
            const idx = ALPHABET_64.indexOf(ch);
            out += idx >= 0 ? STD64[idx] : ch;
        }
        return out;
    }

    function encryptPayload(plainText, keyStr) {
        if (W.Android && typeof W.Android.encryptGcm === "function") {
            const b64 = W.Android.encryptGcm(plainText, keyStr);
            return std64ToCustom(b64);
        }
        return "";
    }

    function decryptPayload(customB64, keyStr) {
        if (W.Android && typeof W.Android.decryptGcm === "function") {
            const stdB64 = custom64ToStd(customB64);
            return W.Android.decryptGcm(stdB64, keyStr);
        }
        return "[Ошибка дешифрования]";
    }

    function encryptKeyWithRsa(aesKey, serverPublicKeyB64) {
        if (W.Android && typeof W.Android.encryptRsa === "function") {
            const b64 = W.Android.encryptRsa(aesKey, serverPublicKeyB64);
            return std64ToCustom(b64);
        }
        return "";
    }

    function maskToPseudoWords(str) {
        if (!str) return "";
        const words = [];
        let idx = 0;
        while (idx < str.length) {
            const rand = Math.random();
            let len;
            if (rand < 0.15) {
                len = W.nanoUtils.randInt(1, 2);
            } else if (rand < 0.65) {
                len = W.nanoUtils.randInt(4, 7);
            } else {
                len = W.nanoUtils.randInt(8, 12);
            }
            const word = str.substr(idx, len);
            if (word) words.push(word);
            idx += len;
        }
        return chaosJoin(words);
    }

    function shouldBreak(totalTokens) {
        if (totalTokens <= 2) return 0;
        if (totalTokens <= 6) return W.nanoUtils.randInt(1, 3);
        if (totalTokens <= 12) return W.nanoUtils.randInt(3, 6);
        return W.nanoUtils.randInt(5, 10);
    }

    function chaosJoin(tokens) {
        if (!tokens.length) return "";
        const maxPos = Math.max(0, tokens.length - 2);
        const breaks = Math.min(shouldBreak(tokens.length), maxPos + 1);
        const positions = new Set();
        while (positions.size < breaks && maxPos >= 0) {
            positions.add(W.nanoUtils.randInt(0, maxPos));
        }
        const out = [];
        for (let i = 0; i < tokens.length; i++) {
            out.push(tokens[i]);
            if (i < tokens.length - 1) {
                out.push(positions.has(i) ? (Math.random() < 0.25 ? String.fromCharCode(10, 10) : String.fromCharCode(10)) : " ");
            }
        }
        return out.join("");
    }

    W.nanoCipher = {
        encryptRaw: function(text, key, domain = "msg") {
            return encryptPayload(text, `${key}:${domain}`);
        },
        decryptRaw: function(customB64, key, domain = "msg") {
            return decryptPayload(customB64, `${key}:${domain}`);
        },
        encryptKeyRsa: function(aesKey, publicKeyB64) {
            return encryptKeyWithRsa(aesKey, publicKeyB64);
        },
        mask: function(str) {
            return maskToPseudoWords(str);
        },
        encode: function(text, key, domain = "msg") {
            const raw = encryptPayload(text, `${key}:${domain}`);
            return maskToPseudoWords(raw);
        },
        decode: function(maskedText, key, domain = "msg") {
            const clean = String(maskedText || "").replace(/\s+/g, "");
            return decryptPayload(clean, `${key}:${domain}`);
        },
        customToStd: function(customB64) {
            return custom64ToStd(customB64);
        }
    };

    function getShiftValue(text) {
        const L = text ? text.length : 0;
        if (L === 0) return 5;
        const sum = String(L).split('').reduce((acc, char) => {
            const digit = parseInt(char, 10);
            return isNaN(digit) ? acc : acc + digit;
        }, 0);
        return sum % 10;
    }

    function shiftType(type, shift) {
        return ((type - 1 + shift) % 10) + 1;
    }

    function unshiftType(typePrime, shift) {
        return ((typePrime - 1 - shift + 10) % 10) + 1;
    }

    function shiftDigits(str, shift) {
        if (typeof str !== "string") str = String(str);
        return str.split('').map(char => {
            const val = parseInt(char, 10);
            if (isNaN(val)) return char;
            return String((val + shift) % 10);
        }).join('');
    }

    function unshiftDigits(str, shift) {
        if (typeof str !== "string") str = String(str);
        return str.split('').map(char => {
            const val = parseInt(char, 10);
            if (isNaN(val)) return char;
            return String((val - shift + 10) % 10);
        }).join('');
    }

    W.nanoPlainObfs = {
        encrypt: function(payloadStr) {
            try {
                const payload = JSON.parse(payloadStr);
                const meta = payload.meta;
                const text = payload.text || "";
                const shift = getShiftValue(text);
                const chatId = meta.chatId || "";
                const blocks = meta.blocks || [];
                const formattedBlocks = blocks.map(bUnit => {
                    const tPrime = shiftType(bUnit.t, shift);
                    let args = [tPrime, chatId];
                    if (bUnit.ref) {
                        args.push(shiftDigits(bUnit.ref, shift));
                    }
                    if (bUnit.e) {
                        args.push(bUnit.e);
                    }
                    if (bUnit.p !== undefined) {
                        args.push(shiftDigits(bUnit.p, shift));
                    }
                    if (bUnit.dur !== undefined) {
                        args.push(shiftDigits(bUnit.dur, shift));
                    }
                    if (bUnit.cnt !== undefined) {
                        args.push(shiftDigits(bUnit.cnt, shift));
                    }
                    return '\x7b' + args.join('\u00f7') + '\x7d';
                }).join('');
                if (text) {
                    return text + " " + formattedBlocks;
                }
                return formattedBlocks;
            } catch (e) {
                console.error('[PlainObfs Error] encrypt failed:', e.message);
                return payloadStr;
            }
        },
        decrypt: function(plainTextWithMetadata) {
            try {
                const blockRegex = new RegExp('\x7b([^\x7d]+)\x7d', 'g');
                const matches = [];
                let match;
                while ((match = blockRegex.exec(plainTextWithMetadata)) !== null) {
                    matches.push(match);
                }
                const cleanText = plainTextWithMetadata.replace(blockRegex, '').trim();
                const shift = getShiftValue(cleanText);
                const parsedBlocks = matches.map(m => {
                    const parts = m[1].split('÷');
                    const tPrime = parseInt(parts[0], 10);
                    const t = unshiftType(tPrime, shift);
                    const bUnit = { t: t, chatId: parts[1] || "" };
                    if (t === W.MsgTypes.REPLY) {
                        bUnit.ref = unshiftDigits(parts[2], shift);
                    } else if (t === W.MsgTypes.REACT) {
                        bUnit.ref = unshiftDigits(parts[2], shift);
                        bUnit.e = parts[3];
                    } else if (t === W.MsgTypes.PIN) {
                        bUnit.ref = unshiftDigits(parts[2], shift);
                        bUnit.p = parseInt(unshiftDigits(parts[3], shift), 10);
                    } else if (t === W.MsgTypes.DELETE) {
                        bUnit.ref = unshiftDigits(parts[2], shift);
                    } else if (t === W.MsgTypes.EDIT) {
                        bUnit.ref = unshiftDigits(parts[2], shift);
                    } else if (t === W.MsgTypes.VOICE) {
                        bUnit.dur = parseInt(unshiftDigits(parts[2], shift), 10);
                    } else if (t === W.MsgTypes.PHOTO) {
                        bUnit.cnt = parseInt(unshiftDigits(parts[2], shift), 10);
                    } else if (t === W.MsgTypes.FILE) {
                        bUnit.name = parts[2];
                        bUnit.size = parseInt(unshiftDigits(parts[3], shift), 10);
                    }
                    return bUnit;
                });
                const payloadObj = {
                    meta: {
                        v: 1,
                        blocks: parsedBlocks
                    },
                    text: cleanText
                };
                return JSON.stringify(payloadObj);
            } catch (e) {
                console.error('[PlainObfs Error] decrypt failed:', e.message);
                return JSON.stringify({ meta: { v: 1, blocks: [] }, text: plainTextWithMetadata });
            }
        }
    };

    W.nanoUtils = {
        clamp: function(value, min, max) { return Math.max(min, Math.min(max, value)); },
        randInt: function(min, max) {
            const lo = Math.ceil(min);
            const hi = Math.floor(max);
            return Math.floor(Math.random() * (hi - lo + 1)) + lo;
        },
        safeJsonParse: function(raw, fallback) {
            try { return JSON.parse(raw); } catch (_) { return fallback; }
        },
        readLS: function(key, fallback = "") {
            try {
                const raw = W.localStorage.getItem(key);
                return raw == null ? fallback : raw;
            } catch (_) { return fallback; }
        },
        writeLS: function(key, value) {
            try { W.localStorage.setItem(key, value); } catch (_) {}
        },
        readState: function() {
            return W.nanoUtils.safeJsonParse(W.nanoUtils.readLS(W.STORAGE.session, "{}"), {});
        },
        writeState: function(state) {
            W.nanoUtils.writeLS(W.STORAGE.session, JSON.stringify(state));
        },
        randomKey: function() {
            const buf = new Uint8Array(16);
            W.crypto.getRandomValues(buf);
            return Array.from(buf, (n) => n.toString(16).padStart(2, "0")).join("");
        },
        nextSubjectX: function() {
            const previous = W.nanoUtils.clamp(parseInt(W.nanoUtils.readLS(W.STORAGE.lastSubjectX, ""), 10) || W.nanoUtils.randInt(2, 30), 2, 30);
            const delta = W.nanoUtils.randInt(-5, 5);
            const next = W.nanoUtils.clamp(previous + delta, 2, 30);
            W.nanoUtils.writeLS(W.STORAGE.lastSubjectX, String(next));
            return next;
        }
    };

    } catch (e) { console.error('[nan0gram:cipher] Критическая ошибка инициализации:', e.message); }
})(window);
