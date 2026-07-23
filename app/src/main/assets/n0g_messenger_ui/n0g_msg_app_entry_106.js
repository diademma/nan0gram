import { INITIAL_CHATS_FALLBACK, DEFAULT_THEME } from './n0g_msg_utils_101.js';
import { 
    getSettingString, 
    saveSettingString,
    requestChatsList, 
    requestChatHistory, 
    saveChatToDb, 
    saveMessageToDb, 
    deleteMessageFromDb, 
    updateMessageReactionInDb,
    pinMessage
} from './n0g_msg_bridge_102.js';
import { Sidebar, ProfileModal, Lightbox, Toast } from './n0g_msg_ui_modals_104.js';
import { ChatArea } from './n0g_msg_chat_area_105.js';

const T = window.T || window.React;
const Q0 = window.Q0 || window.ReactDOM || window.ReactDom;
const f = window.f || {
    jsx: (type, props, key) => T.createElement(type, { ...props, key }),
    jsxs: (type, props, key) => T.createElement(type, { ...props, key })
};

const generateUniqueId = () => "msg_" + Date.now() + "_" + Math.random().toString(36).substr(2, 9);

function getFormattedTime() {
    const now = new Date();
    return now.getHours().toString().padStart(2, "0") + ":" + now.getMinutes().toString().padStart(2, "0");
}

function getMessagePreview(msg) {
    return msg.text 
        ? msg.text 
        : msg.images?.length 
            ? msg.images.length > 1 
                ? `📷 ${msg.images.length} фото` 
                : "📷 Фото" 
            : msg.audio 
                ? "🎵 Голосовое" 
                : msg.video 
                    ? "🎬 Видео" 
                    : msg.file 
                        ? "📄 Файл" 
                        : "";
}

function AppController() {
    const [chats, setChats] = T.useState([]);
    const [messages, setMessages] = T.useState({});
    const [activeChatId, setActiveChatId] = T.useState(null);
    const [activeProfile, setActiveProfile] = T.useState(null);
    const [lightboxItems, setLightboxItems] = T.useState(null);
    const [lightboxInitialIndex, setLightboxInitialIndex] = T.useState(0);
    const [toastMessage, setToastMessage] = T.useState(null);
    const [wallpaper, setWallpaper] = T.useState(() => getSettingString("wp", ""));
    const [chatThemes, setChatThemes] = T.useState({});
    const [defaultTheme, setDefaultTheme] = T.useState(DEFAULT_THEME);
    const [windowWidth, setWindowWidth] = T.useState(() => window.innerWidth);
    const [offset, setOffset] = T.useState(0);
    const [ukrnetError, setUkrnetError] = T.useState(null);

    const chatsRef = T.useRef(chats);
    T.useEffect(() => { chatsRef.current = chats; }, [chats]);

    T.useEffect(() => {
        window.nan0gram_activeChatId = activeChatId;
        if (activeChatId && window.nan0gram && typeof window.nan0gram.setActiveChat === 'function') {
            const activeChatObj = chatsRef.current.find(c => c.id === activeChatId);
            const recipient = activeChatObj ? activeChatObj.username : undefined;
            window.nan0gram.setActiveChat(activeChatId, recipient);
        }
    }, [activeChatId]);

    const [pinnedMsgs, setPinnedMsgs] = T.useState(() => {
        try {
            return JSON.parse(localStorage.getItem("nan0gram_pinned_messages") || "{}");
        } catch (e) {
            return {};
        }
    });

    T.useEffect(() => {
        const handleResize = () => setWindowWidth(window.innerWidth);
        window.addEventListener("resize", handleResize);
        window.addEventListener("orientationchange", handleResize);
        return () => {
            window.removeEventListener("resize", handleResize);
            window.removeEventListener("orientationchange", handleResize);
        };
    }, []);

    T.useEffect(() => {
        const handleUkrnetError = (e) => {
            setUkrnetError(e.detail || "Неизвестная ошибка подключения");
        };
        window.addEventListener('nan0gram:ukrnet-error', handleUkrnetError);
        return () => {
            window.removeEventListener('nan0gram:ukrnet-error', handleUkrnetError);
        };
    }, []);

    T.useEffect(() => {
        window.nan0gram_setMessages = setMessages;
    }, [setMessages]);

    T.useEffect(() => {
        const handleChatsList = (e) => {
            try {
                const list = JSON.parse(e.detail);
                if (list && list.length > 0) {
                    const formatted = list.map(c => ({
                        id: c.chatId,
                        name: c.name,
                        username: c.username,
                        avatar: c.avatarUrl,
                        preview: c.lastMessagePreview,
                        unread: c.unreadCount
                    }));
                    setChats(formatted);
                } else {
                    INITIAL_CHATS_FALLBACK.forEach(c => {
                        saveChatToDb({
                            chatId: c.id,
                            name: c.name,
                            username: c.username,
                            avatarUrl: c.avatar,
                            unreadCount: 0,
                            lastMessageTime: Date.now(),
                            lastMessagePreview: ""
                        });
                    });

                    setTimeout(() => {
                        requestChatsList();
                    }, 200);
                }
            } catch (err) {
                console.error(err);
            }
        };

        const handleHistoryCleared = () => {
            requestChatsList();
            if (activeChatId) {
                setOffset(0);
                requestChatHistory(activeChatId, 0, 100);
            }
        };

        window.addEventListener('nan0gram:chats-list', handleChatsList);
        window.addEventListener('nan0gram:history-cleared', handleHistoryCleared);
        
        try {
            const savedTheme = getSettingString("nan0gram_theme", "");
            if (savedTheme) {
                setDefaultTheme(JSON.parse(savedTheme));
            }
            const savedChatThemes = getSettingString("nan0gram_chat_themes", "");
            if (savedChatThemes) {
                setChatThemes(JSON.parse(savedChatThemes));
            }
        } catch (e) {}

        requestChatsList();

        return () => {
            window.removeEventListener('nan0gram:chats-list', handleChatsList);
            window.removeEventListener('nan0gram:history-cleared', handleHistoryCleared);
        };
    }, [activeChatId]);

    T.useEffect(() => {
        if (activeChatId) {
            setOffset(0);
            requestChatHistory(activeChatId, 0, 100);
        }
    }, [activeChatId]);

    T.useEffect(() => {
        const handleChatHistory = (e) => {
            try {
                const { chatId, offset: incomingOffset, messages: incomingMessages } = JSON.parse(e.detail);
                const formatTime = (ts) => {
                    if (!ts) return getFormattedTime();
                    const d = new Date(ts);
                    const hrs = d.getHours().toString().padStart(2, "0");
                    const mins = d.getMinutes().toString().padStart(2, "0");
                    return `${hrs}:${mins}`;
                };

                const formatted = incomingMessages.map(msg => {
                    const hasEditedText = !!msg.editedText;
                    const isEditedHack = msg.text && msg.text.endsWith("\u200E");
                    const cleanText = hasEditedText ? msg.editedText : (isEditedHack ? msg.text.slice(0, -1) : msg.text);
                    const mapped = {
                        id: String(msg.id),
                        type: msg.type,
                        author: msg.author,
                        text: cleanText,
                        edited: hasEditedText || isEditedHack,
                        time: formatTime(msg.timestamp),
                        timestamp: msg.timestamp,
                        mediaType: msg.mediaType,
                        file: msg.fileName ? { name: msg.fileName, size: msg.fileSize } : null,
                        reaction: msg.reaction || null
                    };

                    if (msg.replyToId) {
                        const parentMsg = incomingMessages.find(p => String(p.id) === String(msg.replyToId));
                        if (parentMsg) {
                            mapped.replyTo = {
                                id: String(parentMsg.id),
                                author: parentMsg.author,
                                text: parentMsg.text || (parentMsg.mediaType !== "none" ? "Медиа" : "")
                            };
                        } else {
                            mapped.replyTo = {
                                id: String(msg.replyToId),
                                author: "Сообщение",
                                text: "Предыдущее сообщение"
                            };
                        }
                    }

                    if (msg.mediaType === "photo" && Array.isArray(msg.mediaPaths)) {
                        mapped.images = msg.mediaPaths.map(p => (p.startsWith("http") || p.startsWith("data:")) ? p : "https://appassets.androidlocal/media/" + p);
                    } else if (msg.mediaType === "voice" && Array.isArray(msg.mediaPaths) && msg.mediaPaths.length > 0) {
                        mapped.audio = (msg.mediaPaths[0].startsWith("http") || msg.mediaPaths[0].startsWith("data:")) ? msg.mediaPaths[0] : "https://appassets.androidlocal/media/" + msg.mediaPaths[0];
                        mapped.audioDuration = msg.audioDuration || 0;
                    } else if (msg.mediaType === "video" && Array.isArray(msg.mediaPaths) && msg.mediaPaths.length > 0) {
                        mapped.video = (msg.mediaPaths[0].startsWith("http") || msg.mediaPaths[0].startsWith("data:")) ? msg.mediaPaths[0] : "https://appassets.androidlocal/media/" + msg.mediaPaths[0];
                        if (Array.isArray(msg.mediaThumbnails) && msg.mediaThumbnails.length > 0) {
                            mapped.videoThumbnail = (msg.mediaThumbnails[0].startsWith("http") || msg.mediaThumbnails[0].startsWith("data:")) ? msg.mediaThumbnails[0] : "https://appassets.androidlocal/media/" + msg.mediaThumbnails[0];
                        }
                    }
                    return mapped;
                }).reverse();

                setMessages(prev => {
                    const current = prev[chatId] || [];
                    if (incomingOffset === 0) {
                        return { ...prev, [chatId]: formatted };
                    } else {
                        return { ...prev, [chatId]: [...formatted, ...current] };
                    }
                });
            } catch (err) {}
        };

        window.addEventListener('nan0gram:chat-history', handleChatHistory);
        return () => window.removeEventListener('nan0gram:chat-history', handleChatHistory);
    }, [activeChatId, offset]);

    const handleLoadMore = T.useCallback(() => {
        const nextOffset = offset + 100;
        setOffset(nextOffset);
        requestChatHistory(activeChatId, nextOffset, 100);
    }, [activeChatId, offset]);

    T.useEffect(() => {
        const blur = getSettingString("nan0gram_sidebar_blur", "20");
        const darkness = getSettingString("nan0gram_sidebar_darkness", "50");
        document.documentElement.style.setProperty("--sidebar-blur", blur + "px");
        document.documentElement.style.setProperty("--sidebar-brightness", (100 - Number(darkness)) / 100);
    }, []);

    T.useEffect(() => {
        const bgUrl = wallpaper || "./Wallpaper.jpg";
        document.documentElement.style.setProperty("--wallpaper-url", `url('${bgUrl}')`);
    }, [wallpaper]);

    T.useEffect(() => {
        const handleWallpaperChange = (e) => {
            const newWp = e.detail;
            setWallpaper(newWp);
            saveSettingString("wp", newWp || "");
        };
        window.addEventListener("wallpaper-change", handleWallpaperChange);
        return () => window.removeEventListener("wallpaper-change", handleWallpaperChange);
    }, []);

    const isMobile = windowWidth < 768;

    const chatPreviews = T.useMemo(() => {
        const previews = {};
        for (const chat of chats) {
            const chatMsgs = messages[chat.id] ?? [];
            const lastMsg = chatMsgs[chatMsgs.length - 1];
            previews[chat.id] = lastMsg ? getMessagePreview(lastMsg) : chat.preview;
        }
        return previews;
    }, [chats, messages]);

    const activeChat = chats.find(chat => chat.id === activeChatId) ?? null;
    const activeChatMessages = activeChatId ? messages[activeChatId] ?? [] : [];
    const activeTheme = activeChatId ? chatThemes[activeChatId] ?? defaultTheme : defaultTheme;

    const handleSelectChat = T.useCallback(chatId => {
        setActiveChatId(chatId);
    }, []);

    const handleCloseChat = T.useCallback(() => {
        setActiveChatId(null);
    }, []);

    const handleOpenLightbox = T.useCallback((src, srcList, index, poster) => {
        const isVideo = src.startsWith("data:video") || src.includes("video/") || /\.(mp4|webm|mov|avi|mkv)(\?|#|$)/i.test(src);
        if (srcList && srcList.length > 1) {
            setLightboxItems(srcList.map(item => ({ src: item, isVideo: false })));
            setLightboxInitialIndex(index ?? 0);
        } else {
            setLightboxItems([{ src: src, isVideo: isVideo, poster: poster }]);
            setLightboxInitialIndex(0);
        }
    }, []);

    const handleCloseLightbox = T.useCallback(() => {
        window.nan0gram_clickCooldown = true;
        setTimeout(() => { window.nan0gram_clickCooldown = false; }, 400);
        setLightboxItems(null);
    }, []);

    const handleSendMessage = T.useCallback((text, replyTo) => {
        if (activeChatId) {
            const newId = generateUniqueId();
            const timeStr = getFormattedTime();
            const msgObj = {
                id: newId,
                type: "out",
                author: "Я",
                text: text,
                time: timeStr,
                timestamp: Date.now(),
                replyTo: replyTo
            };

            saveMessageToDb({
                id: newId,
                chatId: activeChatId,
                type: "out",
                author: "Я",
                text: text,
                timestamp: Date.now(),
                replyToId: replyTo ? String(replyTo.id) : ""
            });

            const currentChat = chats.find(c => c.id === activeChatId);
            if (currentChat) {
                saveChatToDb({
                    chatId: activeChatId,
                    name: currentChat.name,
                    username: currentChat.username,
                    avatarUrl: currentChat.avatar,
                    lastMessageTime: Date.now(),
                    lastMessagePreview: text
                });
            }

            setTimeout(() => {
                requestChatsList();
            }, 100);

            setMessages(prev => ({
                ...prev,
                [activeChatId]: [...(prev[activeChatId] ?? []), msgObj]
            }));
        }
    }, [activeChatId, chats]);

    const handleSendImages = T.useCallback((files, replyTo) => {
        if (activeChatId) {
            Promise.all(files.map(file => new Promise(resolve => {
                const reader = new FileReader();
                reader.onload = e => resolve(e.target.result);
                reader.readAsDataURL(file);
            }))).then(base64DataUrls => {
                if (window.nan0gram && window.nan0gram.submitStealthFile) {
                    window.nan0gram.submitStealthFile("photo");
                }
                setMessages(prev => ({
                    ...prev,
                    [activeChatId]: [...(prev[activeChatId] ?? []), {
                        id: generateUniqueId(),
                        type: "out",
                        author: "Я",
                        images: base64DataUrls,
                        time: getFormattedTime(),
                        timestamp: Date.now(),
                        replyTo: replyTo
                    }]
                }));
            });
        }
    }, [activeChatId]);

    const handleSendAudio = T.useCallback((audioData, duration, replyTo) => {
        if (window.nan0gram && window.nan0gram.submitBase64Media) {
            window.nan0gram.submitBase64Media("voice", audioData, duration);
        }
        if (activeChatId) {
            const newId = generateUniqueId();
            saveMessageToDb({
                id: newId,
                chatId: activeChatId,
                type: "out",
                author: "Я",
                text: "",
                timestamp: Date.now(),
                mediaType: "voice",
                mediaPaths: [audioData],
                audioDuration: duration || 0,
                replyToId: replyTo ? String(replyTo.id) : ""
            });

            setMessages(prev => ({
                ...prev,
                [activeChatId]: [...(prev[activeChatId] ?? []), {
                    id: newId,
                    type: "out",
                    author: "Я",
                    audio: audioData,
                    audioDuration: duration,
                    time: getFormattedTime(),
                    timestamp: Date.now(),
                    replyTo: replyTo
                }]
            }));
        }
    }, [activeChatId]);

    const handleSendVideo = T.useCallback((videoData, replyTo) => {
        if (window.nan0gram && window.nan0gram.submitStealthFile) {
            window.nan0gram.submitStealthFile("video");
        }
        if (activeChatId) {
            setMessages(prev => ({
                ...prev,
                [activeChatId]: [...(prev[activeChatId] ?? []), {
                    id: generateUniqueId(),
                    type: "out",
                    author: "Я",
                    video: videoData,
                    time: getFormattedTime(),
                    timestamp: Date.now(),
                    replyTo: replyTo
                }]
            }));
        }
    }, [activeChatId]);

    const handleEditMessage = T.useCallback((msgId, newText) => {
        if (activeChatId) {
            setMessages(prev => {
                const updatedList = prev[activeChatId].map(msg => {
                    if (msg.id === msgId) {
                        const newMsg = { ...msg, text: newText, edited: true };
                        const mediaType = msg.mediaType || (msg.images ? "photo" : msg.video ? "video" : msg.audio ? "voice" : msg.file ? "file" : "none");
                        let mediaPaths = [];
                        let mediaThumbnails = [];
                        if (mediaType === "photo" && msg.images) {
                            mediaPaths = msg.images;
                        } else if (mediaType === "video" && msg.video) {
                            mediaPaths = [msg.video];
                            if (msg.videoThumbnail) mediaThumbnails = [msg.videoThumbnail];
                        } else if (mediaType === "voice" && msg.audio) {
                            mediaPaths = [msg.audio];
                        }
                        const cleanPaths = mediaPaths.map(p => {
                            if (typeof p === "string" && p.startsWith("https://appassets.androidlocal/media/")) {
                                return p.replace("https://appassets.androidlocal/media/", "");
                            }
                            return p;
                        });
                        const cleanThumbs = mediaThumbnails.map(p => {
                            if (typeof p === "string" && p.startsWith("https://appassets.androidlocal/media/")) {
                                return p.replace("https://appassets.androidlocal/media/", "");
                            }
                            return p;
                        });
                        const fileName = msg.file ? msg.file.name : (msg.fileName || "");
                        const fileSize = msg.file ? msg.file.size : (msg.fileSize || 0);
                        const audioDuration = msg.audioDuration || 0;
                        const parts = String(newMsg.id).split("_");
                        const idTimestamp = parts[1] ? Number(parts[1]) : null;
                        const originalTimestamp = msg.timestamp || idTimestamp || Date.now();
                        
                        saveMessageToDb({
                            id: String(newMsg.id),
                            chatId: activeChatId,
                            type: newMsg.type || "out",
                            author: newMsg.author || "Я",
                            text: newText + "\u200E",
                            timestamp: originalTimestamp,
                            mediaType: mediaType,
                            mediaPaths: cleanPaths,
                            mediaThumbnails: cleanThumbs,
                            fileName: fileName,
                            fileSize: fileSize,
                            audioDuration: audioDuration,
                            replyToId: newMsg.replyTo ? String(newMsg.replyTo.id) : "",
                            reaction: newMsg.reaction || ""
                        });

                        if (window.nan0gram && typeof window.nan0gram.submitEdit === 'function') {
                            window.nan0gram.submitEdit(activeChatId, String(msgId), newText);
                        }

                        return { 
                            ...newMsg, 
                            mediaType: mediaType,
                            fileName: fileName,
                            fileSize: fileSize,
                            audioDuration: audioDuration,
                            timestamp: originalTimestamp
                        };
                    }
                    return msg;
                });
                return { ...prev, [activeChatId]: updatedList };
            });
        }
    }, [activeChatId]);

    const handleDeleteMessage = T.useCallback((msgId) => {
        if (activeChatId) {
            setMessages(prev => ({
                ...prev,
                [activeChatId]: (prev[activeChatId] || []).filter(msg => msg.id !== msgId)
            }));
            deleteMessageFromDb(activeChatId, String(msgId));

            if (window.nan0gram && typeof window.nan0gram.queueAction === 'function') {
                window.nan0gram.queueAction(activeChatId, {
                    type: "delete",
                    targetMessageId: String(msgId)
                });
            }
        }
    }, [activeChatId]);

    const handlePinMessage = T.useCallback(message => {
        if (activeChatId) {
            const currentPinned = pinnedMsgs[activeChatId];
            const targetId = message ? String(message.id) : (currentPinned ? String(currentPinned.id) : "");

            setPinnedMsgs(prev => {
                const updated = { ...prev, [activeChatId]: message };
                localStorage.setItem("nan0gram_pinned_messages", JSON.stringify(updated));
                return updated;
            });
            pinMessage(activeChatId, message);

            if (targetId && window.nan0gram && typeof window.nan0gram.queueAction === 'function') {
                window.nan0gram.queueAction(activeChatId, {
                    type: "pin",
                    targetMessageId: targetId,
                    value: message ? 1 : 0
                });
            }
        }
    }, [activeChatId, pinnedMsgs]);

    const handleToggleReaction = T.useCallback((msgId, reactionSymbol) => {
        if (activeChatId) {
            const chatMsgs = messages[activeChatId] || [];
            const targetMsg = chatMsgs.find(msg => msg.id === msgId);
            const nextReaction = targetMsg?.reaction === reactionSymbol ? "" : reactionSymbol;

            setMessages(prev => ({
                ...prev,
                [activeChatId]: prev[activeChatId].map(msg => msg.id === msgId ? {
                    ...msg,
                    reaction: nextReaction || null
                } : msg)
            }));

            updateMessageReactionInDb(activeChatId, String(msgId), nextReaction);

            if (window.nan0gram && typeof window.nan0gram.queueAction === 'function') {
                window.nan0gram.queueAction(activeChatId, {
                    type: "reaction",
                    targetMessageId: String(msgId),
                    value: nextReaction
                });
            }
        }
    }, [activeChatId, messages]);

    const handleSaveTheme = T.useCallback((chatId, theme) => {
        setChatThemes(prev => {
            const next = { ...prev, [chatId]: theme };
            saveSettingString("nan0gram_chat_themes", JSON.stringify(next));
            return next;
        });
    }, []);

    const handleSaveDefaultTheme = T.useCallback(theme => {
        setDefaultTheme(theme);
        saveSettingString("nan0gram_theme", JSON.stringify(theme));
    }, []);

    const handleOpenActiveProfile = T.useCallback(() => {
        if (activeChat) {
            setActiveProfile({
                avatar: activeChat.avatar,
                name: activeChat.name,
                username: activeChat.username,
                userId: activeChat.userId
            });
        }
    }, [activeChat]);

    const isChatOpen = activeChatId !== null;

    return f.jsxs("div", {
        id: "app",
        className: `messenger-app${isChatOpen ? " chat-open" : ""}${isMobile ? "" : " side-by-side"}`,
        children: [
            f.jsx(Sidebar, {
                contacts: chats,
                activeChatId: activeChatId,
                chatPreviews: chatPreviews,
                chatThemes: chatThemes,
                defaultTheme: defaultTheme,
                onSelectChat: handleSelectChat,
                onSaveTheme: handleSaveTheme,
                onSaveDefaultTheme: handleSaveDefaultTheme
            }),
            f.jsxs("div", {
                className: "chat-area",
                children: [
                    f.jsx("div", {
                        className: "chat-bg",
                        style: {
                            backgroundImage: wallpaper ? `url('${wallpaper}')` : "url('./Wallpaper.jpg')"
                        }
                    }),
                    !isChatOpen && f.jsx("div", {
                        className: "empty-state",
                        children: f.jsx("div", {
                            className: "empty-badge",
                            children: "Выберите чат для начала общения"
                        })
                    }),
                    isChatOpen && activeChat && f.jsx(ChatArea, {
                        chatTitle: activeChat.name,
                        chatAvatar: activeChat.avatar,
                        chatStatus: activeChat.status,
                        messages: activeChatMessages,
                        isMobile: isMobile,
                        theme: activeTheme,
                        onBack: handleCloseChat,
                        onHeaderAvatarClick: handleOpenActiveProfile,
                        onOpenProfile: profile => setActiveProfile(profile),
                        onOpenLightbox: handleOpenLightbox,
                        onSendMessage: handleSendMessage,
                        onSendImages: handleSendImages,
                        onSendAudio: handleSendAudio,
                        onSendVideo: handleSendVideo,
                        onEditMessage: handleEditMessage,
                        onDeleteMessage: handleDeleteMessage,
                        onPinMessage: handlePinMessage,
                        onToggleReaction: handleToggleReaction,
                        onSwipeClose: handleCloseChat,
                        onLoadMore: handleLoadMore,
                        wallpaper: wallpaper,
                        pinnedMessage: pinnedMsgs[activeChatId] || null
                    })
                ]
            }),
            f.jsx(ProfileModal, {
                profile: activeProfile,
                onClose: () => setActiveProfile(null),
                onOpenLightbox: src => handleOpenLightbox(src)
            }),
            f.jsx(Lightbox, {
                items: lightboxItems,
                initialIndex: lightboxInitialIndex,
                onClose: handleCloseLightbox
            }),
            toastMessage && f.jsx(Toast, {
                message: toastMessage,
                onDone: () => setToastMessage(null)
            }),
            ukrnetError && f.jsx("div", {
                className: "modal-overlay",
                style: {
                    position: "fixed",
                    top: 0, left: 0, right: 0, bottom: 0,
                    backgroundColor: "rgba(10, 5, 20, 0.85)",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    zIndex: 999999,
                    backdropFilter: "blur(12px)",
                    transition: "all 0.3s ease"
                },
                children: f.jsxs("div", {
                    className: "modal-card",
                    style: {
                        background: "linear-gradient(135deg, #1b0c36, #090011)",
                        border: "2px solid #9c27b0",
                        borderRadius: "20px",
                        padding: "28px",
                        maxWidth: "400px",
                        width: "90%",
                        textAlign: "center",
                        boxShadow: "0 10px 40px rgba(156, 39, 176, 0.45)",
                        color: "#fff",
                        animation: "fadeIn 0.3s ease-out"
                    },
                    children: [
                        f.jsx("div", {
                            style: {
                                fontSize: "44px",
                                marginBottom: "16px",
                                filter: "drop-shadow(0 0 10px #ab47bc)"
                            },
                            children: "⚠️"
                        }),
                        f.jsx("h3", {
                            style: {
                                margin: "0 0 12px 0",
                                fontSize: "20px",
                                fontWeight: "700",
                                background: "linear-gradient(to right, #e040fb, #ab47bc)",
                                WebkitBackgroundClip: "text",
                                WebkitTextFillColor: "transparent"
                            },
                            children: "Проблемы с подключением"
                        }),
                        f.jsx("p", {
                            style: {
                                fontSize: "14px",
                                color: "#dfc1f5",
                                marginBottom: "24px",
                                lineHeight: "1.5"
                            },
                            children: "Не удалось установить соединение с сервером почты. Пожалуйста, проверьте состояние сети."
                        }),
                        f.jsxs("div", {
                            style: {
                                display: "flex",
                                flexDirection: "column",
                                gap: "12px"
                            },
                            children: [
                                f.jsx("button", {
                                    className: "action-btn",
                                    style: {
                                        background: "transparent",
                                        border: "1px solid #ab47bc",
                                        color: "#f3e5f5",
                                        padding: "11px",
                                        borderRadius: "10px",
                                        cursor: "pointer",
                                        fontSize: "13px",
                                        transition: "all 0.2s ease"
                                    },
                                    onClick: () => {
                                        if (window.Android && typeof window.Android.copyUkrnetError === "function") {
                                            window.Android.copyUkrnetError(ukrnetError);
                                            setToastMessage("Код ошибки скопирован в буфер");
                                        } else if (window.nan0gram && window.nan0gram.copyUkrnetError) {
                                            window.nan0gram.copyUkrnetError(ukrnetError);
                                            setToastMessage("Код ошибки скопирован в буфер");
                                        } else {
                                            navigator.clipboard.writeText(ukrnetError);
                                            setToastMessage("Код ошибки скопирован");
                                        }
                                    },
                                    children: "Скопировать ошибку"
                                }),
                                f.jsx("button", {
                                    className: "action-btn",
                                    style: {
                                        background: "linear-gradient(90deg, #ab47bc, #8e24aa)",
                                        border: "none",
                                        color: "#fff",
                                        padding: "13px",
                                        borderRadius: "10px",
                                        cursor: "pointer",
                                        fontSize: "14px",
                                        fontWeight: "600",
                                        transition: "all 0.2s ease"
                                    },
                                    onClick: () => {
                                        setUkrnetError(null);
                                        if (window.Android && typeof window.Android.retryUkrnet === "function") {
                                            window.Android.retryUkrnet();
                                        } else if (window.nan0gram && window.nan0gram.retryUkrnet) {
                                            window.nan0gram.retryUkrnet();
                                        } else {
                                            console.log("[React] Нативная перезагрузка недоступна.");
                                        }
                                    },
                                    children: "Перезагрузить"
                                })
                            ]
                        })
                    ]
                })
            })
        ]
    });
}

export default function AppRoot() {
    return f.jsx(AppController, {});
}

Q0.createRoot(document.getElementById("root")).render(f.jsx(AppRoot, {}));