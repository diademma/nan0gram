/**
 * DEFAULT_AVATAR_URL
 * Default avatar URL generated via ui-avatars.com API.
 * Corrected from malformed %D0%Я sequence to standard %D0%AF percent-encoding.
 */
export const DEFAULT_AVATAR_URL = "https://ui-avatars.com/api/?name=%D0%AF&background=2f65e0&color=fff&size=128";

/**
 * INITIAL_CHATS_FALLBACK
 * Initial array of chat items to use if database sync is empty during first run.
 */
export const INITIAL_CHATS_FALLBACK = [
    {
        id: "chat_1",
        name: "Кай",
        username: "@ka1jus",
        userId: "109823746",
        status: "150 участников",
        avatar: "https://images.unsplash.com/photo-1555854877-bab0e564b8d5?ixlib=rb-4.0.3&auto=format&fit=crop&w=500&q=60",
        preview: "",
        unread: 0
    }, 
    {
        id: "chat_2",
        name: "Алиса",
        username: "@alisa_wonder",
        userId: "543812993",
        status: "в сети",
        avatar: "https://images.unsplash.com/photo-1494790108377-be9c29b29330?ixlib=rb-4.0.3&auto=format&fit=crop&w=500&q=60",
        preview: "",
        unread: 0
    }, 
    {
        id: "chat_3",
        name: "Работа",
        username: "@work_chat",
        userId: "88339211",
        status: "12 участников",
        avatar: "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?ixlib=rb-4.0.3&auto=format&fit=crop&w=500&q=60",
        preview: "",
        unread: 0
    }
];

/**
 * QUICK_REACTIONS
 * Standard fast emojis available for double tap and reaction bar.
 */
export const QUICK_REACTIONS = ["👍", "❤️", "🔥", "🥰", "👏", "😁", "🤔", "🤯"];

/**
 * ALL_REACTIONS
 * Expanded catalog of emojis for the full reaction grid.
 */
export const ALL_REACTIONS = [
    "🙊", "❤️", "👍", "👎", "🔥", "🥰", "👏", "😁", "🤔", "🤯", "😱", "🤬", "😢", "🎉", "🤩", "🤮", "💩", "🙏", "👌", 
    "🕊️", "🤡", "🥱", "🥴", "😍", "🐳", "❤️‍🔥", "🌚", "🌭", "💯", "🤣", "⚡", "🍌", "🏆", "💔", "🤨", "😐", "🍓", "🍾", 
    "💋", "🖕", "😈", "😴", "😭", "🤓", "👻", "👨‍💻", "👀", "🎃", "😇", "😨", "🤝", "✍️", "🤗", "🫡", "🎅", "🎄", "☃️", 
    "💅", "🤪", "🗿", "🆒", "💘", "🙉", "🦄", "😘", "💊", "😎", "👾", "🤷‍♂️", "🤷", "🤷‍♀️", "😡"
];

/**
 * DEFAULT_THEME
 * Default purple visual configuration for bubbles and schemes.
 */
export const DEFAULT_THEME = {
    schemeId: "custom",
    inRgb: "65,35,80",
    outRgb: "65,35,80",
    opacity: 0.88,
    customHue: 260
};

/**
 * Converts an HSL color value to RGB.
 * Conversion formula adapted from http://en.wikipedia.org/wiki/HSL_color_space.
 * Assumes h, s, and l are contained in the set [0, 1] and
 * returns r, g, and b in the set [0, 255].
 *
 * @param {number} h - The hue (0 to 1)
 * @param {number} s - The saturation (0 to 1)
 * @param {number} l - The lightness (0 to 1)
 * @returns {Array<number>} The RGB representation [r, g, b]
 */
export function hslToRgb(h, s, l) {
    let r, g, b;
    if (s === 0) {
        r = g = b = l; // achromatic
    } else {
        const hue2rgb = (p, q, t) => {
            if (t < 0) t += 1;
            if (t > 1) t -= 1;
            if (t < 1 / 6) return p + (q - p) * 6 * t;
            if (t < 1 / 2) return q;
            if (t < 2 / 3) return p + (q - p) * (2 / 3 - t) * 6;
            return p;
        };
        const q = l < 0.5 ? l * (1 + s) : l + s - l * s;
        const p = 2 * l - q;
        r = hue2rgb(p, q, h + 1 / 3);
        g = hue2rgb(p, q, h);
        b = hue2rgb(p, q, h - 1 / 3);
    }
    return [Math.round(r * 255), Math.round(g * 255), Math.round(b * 255)];
}

/**
 * Calculates row configuration layout according to image collection length.
 * 
 * @param {number} imagesCount - Length of the attached image array
 * @returns {Array<number>} Elements representing amount of pictures in each consecutive grid row
 */
export function getAlbumLayout(imagesCount) {
    const layoutConfig = {
        1: [1],
        2: [2],
        3: [3],
        4: [2, 2],
        5: [2, 3],
        6: [3, 3],
        7: [3, 4],
        8: [4, 4]
    };
    return imagesCount in layoutConfig ? layoutConfig[imagesCount] : [4, 4];
}

/**
 * Formats a duration from total seconds to readable "MM:SS" time.
 * 
 * @param {number} totalSeconds - Time duration value in seconds
 * @returns {string} Formatted output string (e.g., "02:45")
 */
export function formatDuration(totalSeconds) {
    const minutes = Math.floor(totalSeconds / 60).toString().padStart(2, "0");
    const seconds = (totalSeconds % 60).toString().padStart(2, "0");
    return `${minutes}:${seconds}`;
}

/**
 * Evaluates context menu absolute position markers bounded inside viewport and chat borders.
 * 
 * @param {number} clientX - Absolute tap X coordinate 
 * @param {number} clientY - Absolute tap Y coordinate
 * @returns {{left: number, top: number}} Adjusted coordinates safe to paint inside the screen boundaries
 */
export function calculateMenuPosition(clientX, clientY) {
    let chatAreaRect = {
        left: 0,
        top: 0,
        width: typeof window !== 'undefined' ? window.innerWidth : 1024,
        height: typeof window !== 'undefined' ? window.innerHeight : 768
    };

    if (typeof document !== 'undefined') {
        const chatAreaElement = document.querySelector('.chat-area');
        if (chatAreaElement) {
            chatAreaRect = chatAreaElement.getBoundingClientRect();
        }
    }

    const menuWidth = 270;
    const menuHeight = 320;
    const boundaryPadding = 8;

    let targetLeft = (clientX - chatAreaRect.left) - menuWidth / 2;
    targetLeft = Math.max(boundaryPadding, Math.min(targetLeft, chatAreaRect.width - menuWidth - boundaryPadding));

    let targetTop = (clientY - chatAreaRect.top) + 16;
    if (targetTop + menuHeight > chatAreaRect.height - boundaryPadding) {
        targetTop = (clientY - chatAreaRect.top) - menuHeight - 16;
    }
    targetTop = Math.max(boundaryPadding, Math.min(targetTop, chatAreaRect.height - menuHeight - boundaryPadding));

    return {
        left: targetLeft,
        top: targetTop
    };
}

/**
 * Resolves current device system clock into classic timeline tag.
 * 
 * @returns {string} Formatted system time as "HH:MM"
 */
export function getCurrentTime() {
    const currentDate = new Date();
    const hours = currentDate.getHours().toString().padStart(2, "0");
    const minutes = currentDate.getMinutes().toString().padStart(2, "0");
    return `${hours}:${minutes}`;
}

/**
 * Evaluates message variables to extract condensed overview text for navigation list.
 * 
 * @param {object} message - Message payload data object
 * @returns {string} Formatted short overview line representation of the content
 */
export function getMessagePreviewText(message) {
    if (!message) return "";
    if (message.text) {
        return message.text;
    }
    if (message.images && message.images.length > 0) {
        return message.images.length > 1 ? `📷 ${message.images.length} фото` : "📷 Фото";
    }
    if (message.audio) {
        return "🎵 Голосовое";
    }
    if (message.video) {
        return "🎬 Видео";
    }
    if (message.file) {
        return "📄 Файл";
    }
    return "";
}

/**
 * Generates pseudo-random and timeline dependent ID for message structures.
 * 
 * @returns {string} Random payload ID formatted "msg_[timestamp]_[hash]"
 */
export function generateUniqueId() {
    return "msg_" + Date.now() + "_" + Math.random().toString(36).substr(2, 9);
}