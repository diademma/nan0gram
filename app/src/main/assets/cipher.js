(function(W) {
    "use strict";

    W.APP_NAME = "nan0gram";
    W.DEFAULT_RECIPIENT = "270232@ukr.net";
    W.MASTER_SEED = "nan0gram::master-seed-v1";
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
            if (ch === "=") { out += "="; continue; }
            const idx = STD64.indexOf(ch);
            out += idx >= 0 ? ALPHABET_64[idx] : ch;
        }
        return out;
    }

    function custom64ToStd(masked) {
        let out = "";
        for (let i = 0; i < masked.length; i++) {
            const ch = masked[i];
            if (ch === "=") { out += "="; continue; }
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
        encode: function(text, key, domain = "msg") {
            const encrypted = encryptPayload(text, `${key}:${domain}`);
            return maskToPseudoWords(encrypted);
        },
        decode: function(maskedText, key, domain = "msg") {
            const clean = String(maskedText || "").replace(/\s+/g, "");
            return decryptPayload(clean, `${key}:${domain}`);
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
})(window);