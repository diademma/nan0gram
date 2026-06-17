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
    const EMOJI_RE = /^(?:\p{Extended_Pictographic}|\uFE0F|\u200D)+$/u;
    const TOKEN_RE = /(\r?\n|\s+|(?:[\p{Extended_Pictographic}\uFE0F\u200D]+)|[^\s\p{Extended_Pictographic}]+)/gu;

    function textToUtf8Bytes(text) { return new TextEncoder().encode(String(text)); }
    function utf8BytesToText(bytes) { return new TextDecoder().decode(bytes); }
    
    function bytesToBase64(bytes) {
        let bin = "";
        for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
        return W.btoa(bin);
    }
    
    function base64ToBytes(b64) {
        const bin = W.atob(b64);
        const out = new Uint8Array(bin.length);
        for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
        return out;
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

    function makeStream(key, domain, index, length) {
        const seed = hash32(`${domain}|${index}|${length}|${key}`);
        const next = xorshift32(seed);
        const bytes = new Uint8Array(length);
        for (let i = 0; i < length; i++) bytes[i] = next() & 255;
        return bytes;
    }

    function xorBytes(bytes, stream) {
        const out = new Uint8Array(bytes.length);
        for (let i = 0; i < bytes.length; i++) {
            out[i] = bytes[i] ^ stream[i % stream.length];
        }
        return out;
    }

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

    function isEmojiToken(token) {
        if (!token) return false;
        if (/\s/u.test(token)) return false;
        return EMOJI_RE.test(token) || /\p{Extended_Pictographic}/u.test(token);
    }

    function splitPlainText(text) {
        const normalized = String(text || "").replace(/\r\n/g, "\n");
        return normalized.match(TOKEN_RE) || [];
    }

    function encodeAtom(plain, key, domain, index = 0) {
        const raw = String(plain ?? "");
        if (!raw) return "";
        const bytes = textToUtf8Bytes(raw);
        const stream = makeStream(key, domain, index, Math.max(8, bytes.length));
        const xored = xorBytes(bytes, stream);
        const b64 = bytesToBase64(xored);
        return std64ToCustom(b64);
    }

    function decodeAtom(masked, key, domain, index = 0) {
        const raw = String(masked ?? "");
        if (!raw) return "";
        const b64 = custom64ToStd(raw);
        const bytes = base64ToBytes(b64);
        const stream = makeStream(key, domain, index, Math.max(8, bytes.length));
        const plainBytes = xorBytes(bytes, stream);
        return utf8BytesToText(plainBytes);
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

    function encodeLine(line, key, domain, lineIndex) {
        const tokens = splitPlainText(line).filter((t) => t !== "" && t !== "\r");
        const encoded = [];
        let tokenIndex = 0;
        for (const token of tokens) {
            if (/^\s+$/u.test(token)) continue;
            if (isEmojiToken(token)) encoded.push(token);
            else encoded.push(encodeAtom(token, key, `${domain}:line:${lineIndex}`, tokenIndex));
            tokenIndex++;
        }
        return chaosJoin(encoded);
    }

    function decodeLine(line, key, domain, lineIndex) {
        const tokens = String(line || "").split(/\s+/).filter(Boolean);
        const decoded = [];
        let tokenIndex = 0;
        for (const token of tokens) {
            if (token === "@") decoded.push("\n");
            else if (isEmojiToken(token)) decoded.push(token);
            else decoded.push(decodeAtom(token, key, `${domain}:line:${lineIndex}`, tokenIndex));
            tokenIndex++;
        }
        return decoded.join(" ");
    }

    function encodeMultiline(text, key, domain) {
        const lines = String(text || "").replace(/\r\n/g, "\n").split("\n");
        return lines.map((line, idx) => encodeLine(line, key, domain, idx)).join(" @ ");
    }

    function decodeMultiline(masked, key, domain) {
        const lines = String(masked || "").split(/\s@\s/);
        return lines.map((line, idx) => decodeLine(line, key, domain, idx)).join("\n");
    }

    // Экспортируем методы наружу
    W.nanoCipher = {
        encode: function(text, key, domain = "msg") { return encodeMultiline(text, key, domain); },
        decode: function(text, key, domain = "msg") { return decodeMultiline(text, key, domain); }
    };

    W.nanoUtils = {
        hash32: hash32,
        xorshift32: xorshift32,
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