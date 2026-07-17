/**
 * @file n0g_msg_bridge_102.js
 * @description Отказоустойчивый сервисный модуль взаимодействия с нативным слоем Android Kotlin.
 */

/**
 * Получает уникальный идентификатор устройства из нативного слоя.
 * @returns {string} Уникальный ID устройства (fallback: "4f0Q67gPe86N").
 */
export function getDeviceId() {
    if (window.nan0gram && typeof window.nan0gram.getDeviceId === 'function') {
        return window.nan0gram.getDeviceId();
    }
    console.warn("Native method window.nan0gram.getDeviceId is not available. Using fallback.");
    return "4f0Q67gPe86N";
}

/**
 * Читает строковую настройку из SharedPreferences (нативный слой).
 * @param {string} key Ключ настройки.
 * @param {string} [defaultValue=""] Значение по умолчанию.
 * @returns {string} Сохраненное значение или defaultValue.
 */
export function getSettingString(key, defaultValue = "") {
    if (window.Android && typeof window.Android.getSettingString === 'function') {
        return window.Android.getSettingString(key, defaultValue);
    }
    console.warn(`Native method window.Android.getSettingString is not available. Returning default: ${defaultValue}`);
    return defaultValue;
}

/**
 * Очищает кэш медиафайлов устройства через нативный слой.
 * @returns {boolean} true при успешном вызове, false при недоступности моста.
 */
export function clearMediaCache() {
    if (window.Android && typeof window.Android.clearMediaCache === 'function') {
        window.Android.clearMediaCache();
        return true;
    }
    console.warn("Native method window.Android.clearMediaCache is not available.");
    return false;
}

/**
 * Очищает всю историю переписок (сохраняя последние 100 сообщений) через нативный слой.
 * @returns {boolean} true при успешном вызове, false при недоступности моста.
 */
export function clearAllHistoryLog() {
    if (window.Android && typeof window.Android.clearAllHistoryLog === 'function') {
        window.Android.clearAllHistoryLog();
        return true;
    }
    console.warn("Native method window.Android.clearAllHistoryLog is not available.");
    return false;
}

/**
 * Сохраняет данные чата в БД SQLite. Самостоятельно сериализует объект в JSON.
 * @param {Object} chatDataObj Объект с данными чата.
 * @returns {boolean} true при успешном вызове, false при недоступности моста или ошибке сериализации.
 */
export function saveChatToDb(chatDataObj) {
    if (window.Android && typeof window.Android.saveChatToDb === 'function') {
        try {
            const jsonString = JSON.stringify(chatDataObj);
            window.Android.saveChatToDb(jsonString);
            return true;
        } catch (error) {
            console.warn("Failed to stringify chatDataObj for saveChatToDb", error);
            return false;
        }
    }
    console.warn("Native method window.Android.saveChatToDb is not available.");
    return false;
}

/**
 * Запрашивает список чатов из нативного слоя.
 * @returns {boolean} true при успешном вызове, false при недоступности моста.
 */
export function requestChatsList() {
    if (window.Android && typeof window.Android.requestChatsList === 'function') {
        window.Android.requestChatsList();
        return true;
    }
    console.warn("Native method window.Android.requestChatsList is not available.");
    return false;
}

/**
 * Запрашивает историю сообщений конкретного чата.
 * @param {string} chatId Идентификатор чата.
 * @param {number} offset Смещение (для пагинации).
 * @param {number} [limit=100] Лимит загружаемых сообщений.
 * @returns {boolean} true при успешном вызове, false при недоступности моста.
 */
export function requestChatHistory(chatId, offset, limit = 100) {
    if (window.Android && typeof window.Android.requestChatHistory === 'function') {
        window.Android.requestChatHistory(chatId, offset, limit);
        return true;
    }
    console.warn("Native method window.Android.requestChatHistory is not available.");
    return false;
}

/**
 * Сохраняет сообщение в БД SQLite. Самостоятельно сериализует объект в JSON.
 * @param {Object} messageDataObj Объект с данными сообщения.
 * @returns {boolean} true при успешном вызове, false при недоступности моста или ошибке сериализации.
 */
export function saveMessageToDb(messageDataObj) {
    if (window.Android && typeof window.Android.saveMessageToDb === 'function') {
        try {
            const jsonString = JSON.stringify(messageDataObj);
            window.Android.saveMessageToDb(jsonString);
            return true;
        } catch (error) {
            console.warn("Failed to stringify messageDataObj for saveMessageToDb", error);
            return false;
        }
    }
    console.warn("Native method window.Android.saveMessageToDb is not available.");
    return false;
}

/**
 * Удаляет сообщение из БД SQLite.
 * @param {string} chatId Идентификатор чата.
 * @param {string} messageId Идентификатор сообщения.
 * @returns {boolean} true при успешном вызове, false при недоступности моста.
 */
export function deleteMessageFromDb(chatId, messageId) {
    if (window.Android && typeof window.Android.deleteMessageFromDb === 'function') {
        window.Android.deleteMessageFromDb(chatId, String(messageId));
        return true;
    }
    console.warn("Native method window.Android.deleteMessageFromDb is not available.");
    return false;
}

/**
 * Обновляет реакцию на сообщении в БД SQLite.
 * @param {string} chatId Идентификатор чата.
 * @param {string} messageId Идентификатор сообщения.
 * @param {string} reaction Текст или эмодзи реакции.
 * @returns {boolean} true при успешном вызове, false при недоступности моста.
 */
export function updateMessageReactionInDb(chatId, messageId, reaction) {
    if (window.Android && typeof window.Android.updateMessageReactionInDb === 'function') {
        window.Android.updateMessageReactionInDb(chatId, String(messageId), reaction);
        return true;
    }
    console.warn("Native method window.Android.updateMessageReactionInDb is not available.");
    return false;
}

/**
 * Сообщает нативному слою об ожидании установки обоев.
 * @param {boolean} isPending Флаг ожидания.
 * @returns {boolean} true при успешном вызове, false при недоступности моста.
 */
export function setWallpaperPending(isPending) {
    if (window.Android && typeof window.Android.setWallpaperPending === 'function') {
        window.Android.setWallpaperPending(isPending);
        return true;
    }
    console.warn("Native method window.Android.setWallpaperPending is not available.");
    return false;
}

/**
 * Скачивает медиафайл. Пытается использовать нативный мост Android,
 * при его отсутствии эмулирует стандартное браузерное скачивание (fallback).
 * @param {string} mediaUrl Ссылка или Data URI на медиафайл.
 * @param {string} suggestedName Предлагаемое имя скачиваемого файла.
 * @returns {boolean} Возвращает true после попытки сохранения.
 */
export function saveMediaToDownloads(mediaUrl, suggestedName) {
    if (window.Android && typeof window.Android.saveMediaToDownloads === 'function') {
        window.Android.saveMediaToDownloads(mediaUrl, suggestedName);
        return true;
    }
    console.warn("Native method window.Android.saveMediaToDownloads is not available. Falling back to browser download.");
    try {
        const link = document.createElement("a");
        link.href = mediaUrl;
        link.download = suggestedName;
        link.style.cssText = "position:fixed;opacity:0";
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
    } catch (error) {
        console.warn("Browser fallback for saveMediaToDownloads failed", error);
    }
    return true;
}

/**
 * Отправляет файл в stealth-режиме через мост nan0gram.
 * @param {string} fileType Тип медиа (например, "photo" или "video").
 * @returns {boolean} true при успешном вызове, false при недоступности моста.
 */
export function submitStealthFile(fileType) {
    if (window.nan0gram && typeof window.nan0gram.submitStealthFile === 'function') {
        window.nan0gram.submitStealthFile(fileType);
        return true;
    }
    console.warn("Native method window.nan0gram.submitStealthFile is not available.");
    return false;
}

/**
 * Отправляет Base64-данные медиафайла (например, голос) через мост nan0gram.
 * @param {string} mediaType Тип контента (например, "voice").
 * @param {string} base64Data Строка с данными в формате Base64.
 * @param {number} durationSeconds Длительность в секундах.
 * @returns {boolean} true при успешном вызове, false при недоступности моста.
 */
export function submitBase64Media(mediaType, base64Data, durationSeconds) {
    if (window.nan0gram && typeof window.nan0gram.submitBase64Media === 'function') {
        window.nan0gram.submitBase64Media(mediaType, base64Data, durationSeconds);
        return true;
    }
    console.warn("Native method window.nan0gram.submitBase64Media is not available.");
    return false;
}