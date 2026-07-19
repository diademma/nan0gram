import { 
    DEFAULT_AVATAR_URL, 
    QUICK_REACTIONS, 
    ALL_REACTIONS, 
    getAlbumLayout, 
    formatDuration 
} from './n0g_msg_utils_101.js';
import { 
    saveMediaToDownloads, 
    setWallpaperPending 
} from './n0g_msg_bridge_102.js';

const T = window.T || window.React;
const f = window.f || {
    jsx: (type, props, key) => T.createElement(type, { ...props, key }),
    jsxs: (type, props, key) => T.createElement(type, { ...props, key })
};

function calculateMenuPosition(x, y) {
    const ca = document.querySelector('.chat-area');
    const cr = ca ? ca.getBoundingClientRect() : {
        left: 0,
        top: 0,
        width: window.innerWidth,
        height: window.innerHeight
    };
    const width = 270;
    const maxHeight = 320;
    const padding = 8;
    let left = (x - cr.left) - width / 2;
    left = Math.max(padding, Math.min(left, cr.width - width - padding));
    let top = (y - cr.top) + 16;
    if (top + maxHeight > cr.height - padding) {
        top = (y - cr.top) - maxHeight - 16;
    }
    top = Math.max(padding, Math.min(top, cr.height - maxHeight - padding));
    return { left, top };
}

export function MessageRow({
    message,
    selected,
    selectionMode,
    onLongPress,
    onTap,
    onDoubleTap,
    onSwipeLeft,
    onToggleSelect,
    onOpenProfile,
    onOpenLightbox,
    onToggleReaction,
    onMediaLoad
}) {
    const [swipeOffset, setSwipeOffset] = T.useState(0);
    const startX = T.useRef(0);
    const startY = T.useRef(0);
    const isSwiping = T.useRef(false);
    const isScrolling = T.useRef(false);
    const pressTimer = T.useRef(null);
    const longPressed = T.useRef(false);
    const lastTapTime = T.useRef(0);
    const tapTimer = T.useRef(null);
    const replyStartX = T.useRef(0);
    const replyStartY = T.useRef(0);
    const msgContentRef = T.useRef(null);

    T.useEffect(() => {
        const el = msgContentRef.current;
        if (!el) return;
        const handler = (e) => {
            if (e.target.closest('.msg-reply')) return;
            if (longPressed.current) return;
            const touch = e.touches[0];
            const diffX = startX.current - touch.clientX;
            const diffY = Math.abs(startY.current - touch.clientY);
            const absX = Math.abs(diffX);
            if (!isSwiping.current && !isScrolling.current) {
                if (diffY > 10) { isScrolling.current = true; pressTimer.current && clearTimeout(pressTimer.current); return; }
                absX > 8 && (isSwiping.current = true, pressTimer.current && clearTimeout(pressTimer.current));
            }
            if (isSwiping.current && diffX > 0 && diffY < 50) { e.preventDefault(); setSwipeOffset(Math.min(diffX, 75)); }
        };
        el.addEventListener('touchmove', handler, { passive: false });
        return () => el.removeEventListener('touchmove', handler);
    }, []);

    const clearTimers = T.useCallback(() => {
        pressTimer.current && clearTimeout(pressTimer.current);
        tapTimer.current && clearTimeout(tapTimer.current);
    }, []);

    const handleTouchStart = T.useCallback(e => {
        if (e.target.closest('.msg-reply')) return;
        const isVoice = e.target.closest('.tg-voice-player');
        const touch = e.touches[0];
        startX.current = touch.clientX;
        startY.current = touch.clientY;
        isSwiping.current = false;
        isScrolling.current = false;
        longPressed.current = false;
        if (!isVoice) {
            pressTimer.current = setTimeout(() => {
                !isScrolling.current && !isSwiping.current && (longPressed.current = true, navigator.vibrate && navigator.vibrate(50), onLongPress(message.id));
            }, 400);
        }
    }, [message.id, onLongPress]);

    const handleTouchMove = T.useCallback(e => {
        if (e.target.closest('.msg-reply')) return;
        if (longPressed.current) return;
        const touch = e.touches[0];
        const diffX = startX.current - touch.clientX;
        const diffY = Math.abs(startY.current - touch.clientY);
        const absX = Math.abs(diffX);
        if (!isSwiping.current && !isScrolling.current) {
            if (diffY > 10) {
                isScrolling.current = true;
                pressTimer.current && clearTimeout(pressTimer.current);
                return;
            }
            absX > 8 && (isSwiping.current = true, pressTimer.current && clearTimeout(pressTimer.current));
        }
        isSwiping.current && diffX > 0 && diffY < 50 && (e.cancelable && e.preventDefault(), setSwipeOffset(Math.min(diffX, 75)));
    }, []);

    const handleTouchEnd = T.useCallback(e => {
        if (e.target.closest('.msg-reply')) return;
        const isVoiceMsg = e.target.closest('.tg-voice-player');
        if (window.nan0gram_clickCooldown) {
            clearTimers();
            return;
        }
        clearTimers();
        if (longPressed.current) {
            setSwipeOffset(0);
            return;
        }
        const touch = e.changedTouches[0];
        const diffX = startX.current - touch.clientX;
        if (isSwiping.current) {
            setSwipeOffset(0);
            diffX > 40 && (navigator.vibrate && navigator.vibrate(30), onSwipeLeft(message.id));
            isSwiping.current = false;
            return;
        }
        setSwipeOffset(0);
        if (isScrolling.current) return;
        if (selectionMode) {
            onToggleSelect(message.id);
            return;
        }
        if (isVoiceMsg) return;
        const now = Date.now();
        now - lastTapTime.current < 280 ? (tapTimer.current && clearTimeout(tapTimer.current), lastTapTime.current = 0, onDoubleTap(message.id)) : (lastTapTime.current = now, tapTimer.current = setTimeout(() => {
            onTap(message.id, {
                clientX: touch.clientX,
                clientY: touch.clientY
            });
        }, 280));
    }, [clearTimers, message.id, selectionMode, onSwipeLeft, onToggleSelect, onTap, onDoubleTap]);

    const handlePointerDown = T.useCallback(e => {
        if (e.target.closest('.msg-reply') || e.target.closest('.tg-voice-player')) return;
        if (window.nan0gram_clickCooldown) return;
        e.pointerType === "mouse" && (selectionMode ? onToggleSelect(message.id) : onTap(message.id, {
            clientX: e.clientX,
            clientY: e.clientY
        }));
    }, [message.id, selectionMode, onToggleSelect, onTap]);

    const handleAvatarClick = T.useCallback(e => {
        e.stopPropagation();
        message.avatar && onOpenProfile({
            avatar: message.avatar,
            name: message.author,
            username: `@${message.author.toLowerCase().replace(/\s/g, "_")}`,
            userId: "???"
        });
    }, [message, onOpenProfile]);

    const isIncoming = message.type === "in";
    const replyIconOpacity = swipeOffset > 0 ? Math.min(swipeOffset / 60, 1) : 0;
    const images = message.images ?? [];
    let albumRows = [];
    if (images.length > 0) {
        const layout = getAlbumLayout(images.length);
        let imgIdx = 0;
        albumRows = layout.map(p => {
            const rowImages = images.slice(imgIdx, imgIdx + p).map((imgUrl, colIdx) => ({
                src: imgUrl,
                idx: imgIdx + colIdx
            }));
            imgIdx += p;
            return rowImages;
        });
    }

    return f.jsxs("div", {
        className: `msg-row ${message.type}${selected ? " selected" : ""}`,
        "data-id": message.id,
        children: [
            f.jsx("div", {
                className: `msg-checkbox${selectionMode ? " show" : ""}`
            }),
            isIncoming ? f.jsx("div", {
                className: "msg-avatar",
                style: {
                    backgroundImage: message.avatar ? `url('${message.avatar}')` : "none",
                    backgroundColor: message.avatar ? void 0 : "#a773d1"
                },
                onClick: handleAvatarClick,
                onTouchEnd: e => {
                    isSwiping.current || handleAvatarClick(e);
                }
            }) : f.jsx("div", {
                style: {
                    width: 36,
                    marginRight: 10,
                    flexShrink: 0
                }
            }),
            f.jsxs("div", {
                ref: msgContentRef,
                className: "msg-content",
                style: {
                    transform: swipeOffset > 0 ? `translateX(-${swipeOffset}px)` : void 0,
                    transition: swipeOffset === 0 ? "transform 0.2s cubic-bezier(0.1,0.7,0.1,1)" : "none"
                },
                onTouchStart: handleTouchStart,
                onTouchEnd: handleTouchEnd,
                onClick: handlePointerDown,
                children: [
                    f.jsxs("div", {
                        className: "message",
                        children: [
                            isIncoming && f.jsx("div", {
                                className: "msg-author",
                                children: message.author
                            }),
                            message.replyTo && f.jsx("div", {
                                className: "msg-reply",
                                onTouchStart: e => {
                                    e.stopPropagation();
                                    const touch = e.touches[0];
                                    if (touch) {
                                        replyStartX.current = touch.clientX;
                                        replyStartY.current = touch.clientY;
                                    }
                                },
                                onTouchEnd: e => {
                                    e.stopPropagation();
                                    const touch = e.changedTouches[0];
                                    if (touch) {
                                        const diffX = Math.abs(replyStartX.current - touch.clientX);
                                        const diffY = Math.abs(replyStartY.current - touch.clientY);
                                        if (diffX > 8 || diffY > 8) {
                                            // Была прокрутка, блокируем действие
                                            return;
                                        }
                                    }
                                    e.preventDefault();
                                    if (message.replyTo && message.replyTo.id) {
                                        const targetEl = document.querySelector("[data-id='" + message.replyTo.id + "']");
                                        if (targetEl) {
                                            targetEl.scrollIntoView({ behavior: "smooth", block: "center" });
                                            targetEl.classList.add("flash-highlight");
                                            setTimeout(() => targetEl.classList.remove("flash-highlight"), 1000);
                                        }
                                    }
                                },
                                onClick: e => {
                                    e.stopPropagation();
                                    if (message.replyTo && message.replyTo.id) {
                                        const targetEl = document.querySelector("[data-id='" + message.replyTo.id + "']");
                                        if (targetEl) {
                                            targetEl.scrollIntoView({ behavior: "smooth", block: "center" });
                                            targetEl.classList.add("flash-highlight");
                                            setTimeout(() => targetEl.classList.remove("flash-highlight"), 1000);
                                        }
                                    }
                                },
                                children: f.jsxs("div", {
                                    style: {
                                        display: "flex",
                                        flexDirection: "column"
                                    },
                                    children: [
                                        f.jsx("div", {
                                            style: {
                                                fontSize: 13,
                                                color: "var(--reply-line)",
                                                fontWeight: 500
                                            },
                                            children: message.replyTo.author
                                        }),
                                        f.jsx("div", {
                                            style: {
                                                fontSize: 13,
                                                color: "#ccc",
                                                whiteSpace: "nowrap",
                                                overflow: "hidden",
                                                textOverflow: "ellipsis"
                                            },
                                            children: message.replyTo.text
                                        })
                                    ]
                                })
                            }),
                            albumRows.length > 0 && f.jsx("div", {
                                className: "msg-album-container",
                                children: albumRows.map((row, rowIdx) => f.jsx("div", {
                                    className: "album-row",
                                    children: row.map(({ src, idx }) => f.jsx("img", {
                                        src: src,
                                        alt: "",
                                        className: "album-img",
                                        onLoad: () => onMediaLoad?.(),
                                        onPointerDown: e => e.stopPropagation(),
                                        onClick: e => {
                                            e.stopPropagation();
                                            clearTimers();
                                            onOpenLightbox(src, images, idx);
                                        }
                                    }, idx))
                                }, rowIdx))
                            }),
                            message.audio && f.jsxs("div", {
                                className: "voice-msg",
                                children: [
                                    f.jsx("div", {
                                        className: "voice-wave",
                                        children: "🎵"
                                    }),
                                    f.jsx("audio", {
                                        controls: true,
                                        src: message.audio,
                                        className: "voice-audio",
                                        preload: "none"
                                    }),
                                    message.audioDuration != null && f.jsx("span", {
                                        className: "voice-duration",
                                        children: formatDuration(message.audioDuration)
                                    })
                                ]
                            }),
                            message.video && f.jsxs("div", {
                                className: "video-thumb-wrapper",
                                onPointerDown: e => e.stopPropagation(),
                                onClick: e => {
                                    e.stopPropagation();
                                    clearTimers();
                                    onOpenLightbox(message.video, void 0, void 0, message.videoThumbnail);
                                },
                                children: [
                                    f.jsx("video", {
                                        src: message.video,
                                        poster: message.videoThumbnail,
                                        className: "video-thumb",
                                        preload: "metadata",
                                        playsInline: true,
                                        muted: true,
                                        onLoadedMetadata: () => onMediaLoad?.()
                                    }),
                                    f.jsx("div", {
                                        className: "video-play-overlay",
                                        children: f.jsx("svg", {
                                            width: 24,
                                            height: 24,
                                            viewBox: "0 0 24 24",
                                            fill: "currentColor",
                                            children: f.jsx("polygon", {
                                                points: "5 3 19 12 5 21"
                                            })
                                        })
                                    })
                                ]
                            }),
                            message.file && f.jsxs("div", {
                                className: "file-msg",
                                onPointerDown: e => e.stopPropagation(),
                                onClick: e => {
                                    e.stopPropagation();
                                },
                                children: [
                                    f.jsx("div", {
                                        className: "file-icon",
                                        children: "📄"
                                    }),
                                    f.jsxs("div", {
                                        className: "file-details",
                                        children: [
                                            f.jsx("div", {
                                                className: "file-name",
                                                children: message.file.name
                                            }),
                                            f.jsx("div", {
                                                className: "file-size",
                                                children: (message.file.size / (1024 * 1024)).toFixed(2) + " MB"
                                            })
                                        ]
                                    })
                                ]
                            }),
                            message.text && f.jsx("div", {
                                style: {
                                    userSelect: "text",
                                    whiteSpace: "pre-wrap"
                                },
                                children: message.text
                            }),
                            f.jsxs("div", {
                                className: "msg-bottom-flow",
                                style: {
                                    justifyContent: message.reaction ? "space-between" : "flex-end"
                                },
                                children: [
                                    message.reaction && f.jsx("div", {
                                        className: "reactions-container",
                                        children: f.jsxs("div", {
                                            className: `reaction-pill ${isIncoming ? "pill-in" : "pill-out"}`,
                                            onTouchStart: e => e.stopPropagation(),
                                            onPointerDown: e => e.stopPropagation(),
                                            onPointerUp: e => e.stopPropagation(),
                                            onDoubleClick: e => e.stopPropagation(),
                                            onClick: e => {
                                                e.stopPropagation();
                                                onToggleReaction && onToggleReaction(message.id, message.reaction);
                                            },
                                            onTouchEnd: e => {
                                                e.cancelable && e.preventDefault();
                                                e.stopPropagation();
                                                onToggleReaction && onToggleReaction(message.id, message.reaction);
                                            },
                                            children: [
                                                f.jsx("span", {
                                                    children: message.reaction
                                                }),
                                                f.jsx("div", {
                                                    className: "react-avatar",
                                                    style: {
                                                        backgroundImage: `url('${DEFAULT_AVATAR_URL}')`
                                                    }
                                                })
                                            ]
                                        })
                                    }),
                                    f.jsxs("div", {
                                        className: "msg-meta",
                                        children: [
                                            message.edited && f.jsx("span", {
                                                className: "edit-pencil",
                                                children: "✎"
                                            }),
                                            f.jsx("span", {
                                                children: message.time
                                            }),
                                            message.type === "out" && f.jsx("span", {
                                                style: {
                                                    color: "#a773d1"
                                                },
                                                children: "✓✓"
                                            })
                                        ]
                                    })
                                ]
                            })
                        ]
                    }),
                    f.jsx("div", {
                        className: "swipe-reply-icon",
                        style: {
                            opacity: replyIconOpacity
                        },
                        children: "↩️"
                    })
                ]
            })
        ]
    });
}

export function ContextMenu({
    message,
    position,
    onClose,
    onReply,
    onCopy,
    onPin,
    onEdit,
    onDelete,
    onReaction,
    onSaveMedia
}) {
    const menuRef = T.useRef(null);
    if (!(message !== null && position !== null)) {
        return f.jsxs(f.Fragment, {
            children: [
                f.jsx("div", {
                    className: "sheet-backdrop",
                    style: {
                        display: "none"
                    }
                }),
                f.jsx("div", {
                    className: "tg-context-menu",
                    style: {
                        opacity: 0,
                        pointerEvents: "none",
                        position: "fixed",
                        top: 0,
                        left: 0
                    }
                })
            ]
        });
    }

    const { left, top } = calculateMenuPosition(position.x, position.y);
    const isMedia = !!(message?.images?.length || message?.audio || message?.video || message?.file);

    return f.jsxs(f.Fragment, {
        children: [
            f.jsx("div", {
                className: "sheet-backdrop",
                style: {
                    display: "block",
                    opacity: 1
                },
                onClick: onClose
            }),
            f.jsxs("div", {
                ref: menuRef,
                className: "tg-context-menu open",
                style: {
                    position: "fixed",
                    left: left,
                    top: top,
                    maxWidth: "calc(100vw - 24px)"
                },
                children: [
                    f.jsxs("div", {
                        className: "reaction-panel",
                        children: [
                            f.jsx("div", {
                                className: "reaction-scroll",
                                children: QUICK_REACTIONS.map(emoji => f.jsx("div", {
                                    className: "reaction-btn",
                                    onClick: () => {
                                        onReaction(emoji);
                                        onClose();
                                    },
                                    children: emoji
                                }, emoji))
                            }),
                            f.jsx("div", {
                                className: "reaction-expand-btn",
                                id: "expandReactionsBtn",
                                onClick: () => {
                                    const allReactionsGrid = menuRef.current?.querySelector(".all-reactions-grid");
                                    const actionList = menuRef.current?.querySelector(".context-action-list");
                                    const expandBtn = menuRef.current?.querySelector("#expandReactionsBtn");
                                    if (!allReactionsGrid || !actionList || !expandBtn) return;
                                    const isExpanded = allReactionsGrid.style.display === "grid";
                                    allReactionsGrid.style.display = isExpanded ? "none" : "grid";
                                    actionList.style.display = isExpanded ? "flex" : "none";
                                    expandBtn.style.transform = isExpanded ? "rotate(0deg)" : "rotate(180deg)";
                                },
                                children: "▼"
                            })
                        ]
                    }),
                    f.jsx("div", {
                        className: "all-reactions-grid",
                        style: {
                            display: "none"
                        },
                        children: ALL_REACTIONS.map(emoji => f.jsx("div", {
                            className: "reaction-btn",
                            onClick: () => {
                                onReaction(emoji);
                                onClose();
                            },
                            children: emoji
                        }, emoji))
                    }),
                    f.jsxs("div", {
                        className: "context-action-list",
                        style: {
                            display: "flex"
                        },
                        children: [
                            f.jsxs("div", {
                                className: "context-action",
                                onClick: () => {
                                    onReply();
                                    onClose();
                                },
                                children: [
                                    f.jsx("span", {
                                        className: "icon",
                                        children: "↩️"
                                    }),
                                    " Ответить"
                                ]
                            }),
                            message.text && f.jsxs("div", {
                                className: "context-action",
                                onClick: () => {
                                    onCopy();
                                    onClose();
                                },
                                children: [
                                    f.jsx("span", {
                                        className: "icon",
                                        children: "📋"
                                    }),
                                    " Копировать"
                                ]
                            }),
                            isMedia && f.jsxs("div", {
                                className: "context-action",
                                onClick: () => {
                                    onSaveMedia?.();
                                    onClose();
                                },
                                children: [
                                    f.jsx("span", {
                                        className: "icon",
                                        children: "⬇️"
                                    }),
                                    " Сохранить медиа"
                                ]
                            }),
                            f.jsxs("div", {
                                className: "context-action",
                                onClick: onClose,
                                children: [
                                    f.jsx("span", {
                                        className: "icon",
                                        children: "➡️"
                                    }),
                                    " Переслать"
                                ]
                            }),
                            f.jsxs("div", {
                                className: "context-action",
                                onClick: () => {
                                    onPin();
                                    onClose();
                                },
                                children: [
                                    f.jsx("span", {
                                        className: "icon",
                                        children: "📌"
                                    }),
                                    " Закрепить"
                                ]
                            }),
                            message.type === "out" && f.jsxs("div", {
                                className: "context-action",
                                onClick: () => {
                                    onEdit();
                                    onClose();
                                },
                                children: [
                                    f.jsx("span", {
                                        className: "icon",
                                        children: "✏️"
                                    }),
                                    " Изменить"
                                ]
                            }),
                            f.jsxs("div", {
                                className: "context-action",
                                style: {
                                    color: "#ff595a"
                                },
                                onClick: () => {
                                    onDelete();
                                    onClose();
                                },
                                children: [
                                    f.jsx("span", {
                                        className: "icon",
                                        children: "🗑️"
                                    }),
                                    " Удалить"
                                ]
                            })
                        ]
                    })
                ]
            })
        ]
    });
}

export function ChatArea({
    chatTitle,
    chatAvatar,
    chatStatus,
    messages,
    isMobile,
    theme,
    onBack,
    onHeaderAvatarClick,
    onOpenProfile,
    onOpenLightbox,
    onSendMessage,
    onSendImages,
    onSendAudio,
    onSendVideo,
    onEditMessage,
    onDeleteMessage,
    onPinMessage,
    onToggleReaction,
    onSwipeClose,
    onLoadMore,
    wallpaper,
    pinnedMessage
}) {
    const scrollContainerRef = T.useRef(null);
    const messagesCountRef = T.useRef(messages.length);
    const bottomRef = T.useRef(null);

    const scrollToAnchor = T.useCallback(() => {
        bottomRef.current?.scrollIntoView();
    }, []);
    const inputRef = T.useRef(null);
    const mediaInputRef = T.useRef(null);
    const fileInputRef = T.useRef(null);
    const wallpaperInputRef = T.useRef(null);

    const [replyMessage, setReplyMessage] = T.useState(null);
    const [editingMessageId, setEditingMessageId] = T.useState(null);
    const [inputText, setInputText] = T.useState("");
    const [isSelectionMode, setIsSelectionMode] = T.useState(false);
    const [selectedMessageIds, setSelectedMessageIds] = T.useState(new Set());
    const [pinnedMessageState, setPinnedMessageState] = T.useState(null);
    const [activeMessage, setActiveMessage] = T.useState(null);
    const [contextMenuPos, setContextMenuPos] = T.useState(null);
    const [isRecording, setIsRecording] = T.useState(false);
    const [showScrollDown, setShowScrollDown] = T.useState(false);

    const mediaChunks = T.useRef([]);
    const mediaRecorder = T.useRef(null);
    const recordStartTime = T.useRef(0);

    const touchStartX = T.useRef(0);
    const touchStartY = T.useRef(0);
    const isSwipeValid = T.useRef(false);
    const isRecReq = T.useRef(false);

    const handleTouchStartSwipe = T.useCallback(e => {
        const touch = e.touches[0];
        touchStartX.current = touch.clientX;
        touchStartY.current = touch.clientY;
        isSwipeValid.current = touch.clientY >= 60 && touch.clientY <= (window.innerHeight - 80);
    }, []);

    const handleTouchMoveSwipe = T.useCallback(e => {}, []);

    const handleTouchEndSwipe = T.useCallback(e => {
        if (!isMobile || !isSwipeValid.current) return;
        const touch = e.changedTouches[0];
        const diffX = touch.clientX - touchStartX.current;
        const diffY = Math.abs(touch.clientY - touchStartY.current);
        if (diffX > 55 && diffX > diffY * 1.2) {
            onSwipeClose();
        }
    }, [isMobile, onSwipeClose]);

    T.useEffect(() => {
        if (messages.length > messagesCountRef.current) {
            bottomRef.current?.scrollIntoView();
        }
        messagesCountRef.current = messages.length;
    }, [messages]);

    T.useEffect(() => {
        setPinnedMessageState(pinnedMessage || null);
    }, [pinnedMessage]);

    const resetInputState = T.useCallback(() => {
        setReplyMessage(null);
        setEditingMessageId(null);
        setInputText("");
        inputRef.current && (inputRef.current.style.height = "auto");
    }, []);

    const handleSendMessageClick = T.useCallback(() => {
        const trimmedText = inputText.trim();
        if (trimmedText) {
            if (editingMessageId !== null) {
                onEditMessage(editingMessageId, trimmedText);
                resetInputState();
            } else {
                for (let start = 0; start < trimmedText.length; start += 4000) {
                    onSendMessage(trimmedText.slice(start, start + 4000), start === 0 ? replyMessage ?? void 0 : void 0);
                }
                resetInputState();
            }
        }
    }, [inputText, editingMessageId, replyMessage, onSendMessage, onEditMessage, resetInputState]);

    const handleInputKeyDown = T.useCallback(e => {}, []);

    const handleInputChange = T.useCallback(e => {
        setInputText(e.target.value);
        const target = e.target;
        target.style.height = "auto";
        target.style.height = Math.min(target.scrollHeight, 100) + "px";
    }, []);

    const handleMediaChange = T.useCallback(e => {
        const files = Array.from(e.target.files ?? []).slice(0, 8);
        files.length && (onSendImages(files, replyMessage ?? void 0), resetInputState());
        e.target.value = "";
    }, [replyMessage, onSendImages, resetInputState]);

    const handleVideoChange = T.useCallback(e => {
        const file = e.target.files?.[0];
        if (!file) return;
        if (file.size > 17 * 1024 * 1024) {
            alert("Видео больше 17 МБ");
            e.target.value = "";
            return;
        }
        const reader = new FileReader();
        reader.onload = event => {
            onSendVideo(event.target.result, replyMessage ?? void 0);
            resetInputState();
            // Скролл не нужен здесь — useEffect слушает loadeddata на video-элементе
            // и делает мгновенный scrollTop = scrollHeight когда thumbnail загрузился.
        };
        reader.readAsDataURL(file);
        e.target.value = "";
    }, [replyMessage, onSendVideo, resetInputState]);

    const handleWallpaperChange = T.useCallback(e => {
        const file = e.target.files?.[0];
        if (!file) return;
        const reader = new FileReader();
        reader.onload = event => {
            const img = new Image();
            img.onload = () => {
                const canvas = document.createElement("canvas");
                const maxDim = 1440;
                let width = img.width, height = img.height;
                if (width > maxDim || height > maxDim) {
                    if (width > height) {
                        height = Math.round((height * maxDim) / width);
                        width = maxDim;
                    } else {
                        width = Math.round((width * maxDim) / height);
                        height = maxDim;
                    }
                }
                canvas.width = width;
                canvas.height = height;
                const ctx = canvas.getContext("2d");
                ctx.drawImage(img, 0, 0, width, height);
                const compressedUrl = canvas.toDataURL("image/jpeg", 0.85);
                try {
                    localStorage.setItem("wp", compressedUrl);
                    window.dispatchEvent(new CustomEvent("wallpaper-change", {
                        detail: compressedUrl
                    }));
                } catch (err) {
                    console.error("Failed to save wallpaper:", err);
                }
            };
            img.src = event.target.result;
        };
        reader.readAsDataURL(file);
        e.target.value = "";
    }, []);

    const startAudioRecording = T.useCallback(async () => {
        isRecReq.current = true;
        try {
            const stream = await navigator.mediaDevices.getUserMedia({
                audio: {
                    channelCount: 1,
                    sampleRate: 48000,
                    sampleSize: 16,
                    echoCancellation: true,
                    noiseSuppression: true,
                    autoGainControl: true
                }
            });
            if (!isRecReq.current) {
                stream.getTracks().forEach(track => track.stop());
                return;
            }
            const mime = MediaRecorder.isTypeSupported("audio/webm;codecs=opus") ? "audio/webm;codecs=opus" : "audio/webm";
            const recorder = new MediaRecorder(stream, {
                mimeType: mime,
                audioBitsPerSecond: 128000
            });
            mediaChunks.current = [];
            recorder.ondataavailable = e => {
                e.data.size > 0 && mediaChunks.current.push(e.data);
            };
            recorder.onstop = () => {
                const duration = Math.round((Date.now() - recordStartTime.current) / 1000);
                const audioBlob = new Blob(mediaChunks.current, {
                    type: mime
                });
                if (audioBlob.size < 100) {
                    stream.getTracks().forEach(track => track.stop());
                    return;
                }
                const reader = new FileReader();
                reader.onload = event => {
                    onSendAudio(event.target.result, duration, replyMessage ?? void 0);
                    resetInputState();
                };
                reader.readAsDataURL(audioBlob);
                stream.getTracks().forEach(track => track.stop());
            };
            recorder.start();
            mediaRecorder.current = recorder;
            recordStartTime.current = Date.now();
            setIsRecording(true);
            navigator.vibrate && navigator.vibrate(30);
        } catch {
            alert("Нет доступа к микрофону");
        }
    }, [replyMessage, onSendAudio, resetInputState]);

    const stopAudioRecording = T.useCallback(() => {
        isRecReq.current = false;
        mediaRecorder.current && mediaRecorder.current.state !== "inactive" && (mediaRecorder.current.stop(), mediaRecorder.current = null);
        setIsRecording(false);
    }, []);

    const clearSelectionMode = T.useCallback(() => {
        setIsSelectionMode(false);
        setSelectedMessageIds(new Set());
    }, []);

    const startSelectionMode = T.useCallback(msgId => {
        isSelectionMode || setIsSelectionMode(true);
        setSelectedMessageIds(prev => {
            const copy = new Set(prev);
            return copy.add(msgId), copy;
        });
    }, [isSelectionMode]);

    const toggleMessageSelect = T.useCallback(msgId => {
        setSelectedMessageIds(prev => {
            const copy = new Set(prev);
            return copy.has(msgId) ? copy.delete(msgId) : copy.add(msgId), copy;
        });
    }, []);

    T.useEffect(() => {
        isSelectionMode && selectedMessageIds.size === 0 && clearSelectionMode();
    }, [selectedMessageIds, isSelectionMode, clearSelectionMode]);

    const handleDeleteSelected = T.useCallback(() => {
        selectedMessageIds.forEach(msgId => onDeleteMessage(msgId));
        clearSelectionMode();
    }, [selectedMessageIds, onDeleteMessage, clearSelectionMode]);

    const handleCopySelected = T.useCallback(() => {
        const combinedText = messages
            .filter(msg => selectedMessageIds.has(msg.id) && msg.text)
            .map(msg => msg.text)
            .join("\n\n");
        if (combinedText) {
            const temp = document.createElement("textarea");
            temp.value = combinedText;
            temp.style.cssText = "position:fixed;opacity:0";
            document.body.appendChild(temp);
            temp.select();
            try {
                document.execCommand("copy");
            } catch {}
            document.body.removeChild(temp);
        }
        clearSelectionMode();
    }, [selectedMessageIds, messages, clearSelectionMode]);

    const handleMessageTap = T.useCallback((msgId, clientPos) => {
        const msg = messages.find(m => m.id === msgId);
        msg && (setActiveMessage(msg), setContextMenuPos({
            x: clientPos.clientX,
            y: clientPos.clientY
        }));
    }, [messages]);

    const handleMessageDoubleTap = T.useCallback(msgId => {
        onToggleReaction(msgId, "❤️");
    }, [onToggleReaction]);

    const handleReplyAction = T.useCallback(() => {
        activeMessage && (setReplyMessage({
            id: activeMessage.id,
            author: activeMessage.author || "Я",
            text: activeMessage.text || "Медиа"
        }), setEditingMessageId(null), inputRef.current?.focus());
    }, [activeMessage]);

    const handleCopyAction = T.useCallback(() => {
        if (!activeMessage?.text) return;
        const temp = document.createElement("textarea");
        temp.value = activeMessage.text;
        temp.style.cssText = "position:fixed;opacity:0";
        document.body.appendChild(temp);
        temp.select();
        try {
            document.execCommand("copy");
        } catch {}
        document.body.removeChild(temp);
    }, [activeMessage]);

    const handlePinAction = T.useCallback(() => {
        activeMessage && (onPinMessage(activeMessage), setPinnedMessageState(activeMessage));
    }, [activeMessage, onPinMessage]);

    const handleEditAction = T.useCallback(() => {
        activeMessage && (setEditingMessageId(activeMessage.id), setReplyMessage(null), setInputText(activeMessage.text || ""), setTimeout(() => {
            if (inputRef.current) {
                inputRef.current.style.height = "auto";
                inputRef.current.style.height = Math.min(inputRef.current.scrollHeight, 100) + "px";
                inputRef.current.focus();
            }
        }, 50));
    }, [activeMessage]);

    const handleSwipeToReply = T.useCallback(msgId => {
        const msg = messages.find(m => m.id === msgId);
        if (msg) {
            const textDesc = msg.text || (msg.images?.length > 1 ? "Альбом" : msg.images ? "Фото" : msg.audio ? "Голосовое сообщение" : msg.video ? "Видео" : msg.file ? "Файл" : "Сообщение");
            setReplyMessage({
                id: msg.id,
                author: msg.author || "Я",
                text: textDesc
            });
            setEditingMessageId(null);
            inputRef.current?.focus();
        }
    }, [messages]);

    const handleGoToPinned = T.useCallback(() => {
        if (pinnedMessageState) {
            const targetId = pinnedMessageState.id || pinnedMessageState;
            const targetEl = scrollContainerRef.current?.querySelector(`[data-id="${targetId}"]`);
            if (targetEl) {
                targetEl.scrollIntoView({ behavior: "smooth", block: "center" });
                targetEl.classList.add("flash-highlight");
                setTimeout(() => targetEl.classList.remove("flash-highlight"), 1000);
            }
        }
    }, [pinnedMessageState]);

    const handleSaveMedia = T.useCallback(() => {
        if (!activeMessage) return;
        const mediaUrls = [];
        if (activeMessage.images) mediaUrls.push(...activeMessage.images);
        if (activeMessage.audio) mediaUrls.push(activeMessage.audio);
        if (activeMessage.video) mediaUrls.push(activeMessage.video);

        mediaUrls.forEach((url, idx) => {
            const ext = url.startsWith("data:video") ? "mp4" : url.startsWith("data:audio") ? "webm" : "jpg";
            const suggestedName = `media_${Date.now()}_${idx + 1}.${ext}`;
            saveMediaToDownloads(url, suggestedName);
        });
    }, [activeMessage]);

    const hasTextToSend = inputText.trim().length > 0;
    const bubbleThemes = {
        "--msg-in": `rgba(${theme.inRgb},${theme.opacity})`,
        "--msg-out": `rgba(${theme.outRgb},${theme.opacity})`
    };

    return f.jsxs("div", {
        className: `chat-area-inner${isSelectionMode ? " selection-mode" : ""}`,
        style: bubbleThemes,
        onTouchStart: handleTouchStartSwipe,
        onTouchMove: handleTouchMoveSwipe,
        onTouchEnd: handleTouchEndSwipe,
        children: [
            f.jsx("div", {
                className: "chat-bg",
                style: {
                    backgroundImage: wallpaper ? `url('${wallpaper}')` : "url('https://images.unsplash.com/photo-1534447677768-be436bb09401?q=80&w=1000&auto=format&fit=crop')"
                }
            }),
            !isSelectionMode && f.jsxs("div", {
                className: "chat-header",
                children: [
                    isMobile && f.jsx("button", {
                        className: "back-btn",
                        onClick: onBack,
                        children: "←"
                    }),
                    f.jsx("div", {
                        className: "header-avatar",
                        style: {
                            backgroundImage: `url('${chatAvatar}')`
                        },
                        onClick: onHeaderAvatarClick
                    }),
                    f.jsxs("div", {
                        className: "header-info",
                        onClick: onHeaderAvatarClick,
                        children: [
                            f.jsx("div", {
                                className: "header-title",
                                children: chatTitle
                            }),
                            f.jsx("div", {
                                className: "header-status",
                                children: chatStatus
                            })
                        ]
                    }),
                    f.jsxs("div", {
                        className: "header-actions",
                        children: [
                            f.jsx("span", {
                                title: "Сменить обои",
                                onClick: () => {
                                    setWallpaperPending(true);
                                    wallpaperInputRef.current?.click();
                                },
                                style: {
                                    cursor: "pointer"
                                },
                                children: f.jsx("svg", {
                                    width: 18,
                                    height: 18,
                                    viewBox: "0 0 24 24",
                                    fill: "none",
                                    stroke: "currentColor",
                                    strokeWidth: 2,
                                    strokeLinecap: "round",
                                    strokeLinejoin: "round",
                                    children: [
                                        f.jsx("rect", {
                                            x: 3,
                                            y: 3,
                                            width: 18,
                                            height: 18,
                                            rx: 2,
                                            ry: 2
                                        }),
                                        f.jsx("circle", {
                                            cx: 8.5,
                                            cy: 8.5,
                                            r: 1.5
                                        }),
                                        f.jsx("polyline", {
                                            points: "21 15 16 10 5 21"
                                        })
                                    ]
                                })
                            }),
                            f.jsx("input", {
                                ref: wallpaperInputRef,
                                type: "file",
                                accept: "image/*",
                                style: {
                                    display: "none"
                                },
                                onChange: handleWallpaperChange
                            })
                        ]
                    })
                ]
            }),
            isSelectionMode && f.jsxs("div", {
                className: "selection-header",
                style: {
                    display: "flex"
                },
                children: [
                    f.jsxs("div", {
                        style: {
                            display: "flex",
                            alignItems: "center"
                        },
                        children: [
                            f.jsx("span", {
                                style: {
                                    fontSize: 24,
                                    cursor: "pointer",
                                    paddingRight: 15
                                },
                                onClick: clearSelectionMode,
                                children: "✕"
                            }),
                            f.jsx("span", {
                                className: "selection-count",
                                children: selectedMessageIds.size
                            })
                        ]
                    }),
                    f.jsxs("div", {
                        className: "selection-actions",
                        children: [
                            f.jsx("span", {
                                onClick: handleCopySelected,
                                children: "📋"
                            }),
                            f.jsx("span", {
                                onClick: handleDeleteSelected,
                                children: "🗑️"
                            })
                        ]
                    })
                ]
            }),
            pinnedMessageState && f.jsxs("div", {
                className: "pinned-bar",
                style: {
                    display: "flex"
                },
                onClick: handleGoToPinned,
                children: [
                    f.jsxs("div", {
                        className: "pin-content",
                        children: [
                            f.jsx("div", {
                                className: "pin-title",
                                children: "Закреплённое сообщение"
                            }),
                            f.jsx("div", {
                                className: "pin-text",
                                children: pinnedMessageState.text || (pinnedMessageState.images?.length ? "📷 Фото" : pinnedMessageState.audio ? "🎵 Голосовое" : pinnedMessageState.video ? "🎬 Видео" : pinnedMessageState.file ? "📄 Файл" : "Сообщение")
                            })
                        ]
                    }),
                    f.jsx("div", {
                        className: "pin-close",
                        onClick: e => {
                            e.stopPropagation();
                            setPinnedMessageState(null);
                            onPinMessage(null);
                        },
                        children: "✕"
                    })
                ]
            }),
            f.jsx("div", {
                ref: scrollContainerRef,
                className: "messages-container",
                onScroll: e => {
                    const threshold = 150;
                    const { scrollTop, scrollHeight, clientHeight } = e.target;
                    const isNearBottom = (scrollHeight - scrollTop - clientHeight) < threshold;
                    setShowScrollDown(!isNearBottom);
                    if (scrollTop === 0 && onLoadMore) {
                        onLoadMore();
                    }
                },
                children: [
                    ...messages.map(msg => f.jsx(MessageRow, {
                        message: msg,
                        selected: selectedMessageIds.has(msg.id),
                        selectionMode: isSelectionMode,
                        onLongPress: startSelectionMode,
                        onTap: handleMessageTap,
                        onDoubleTap: handleMessageDoubleTap,
                        onSwipeLeft: handleSwipeToReply,
                        onToggleSelect: toggleMessageSelect,
                        onOpenProfile: onOpenProfile,
                        onOpenLightbox: onOpenLightbox,
                        onToggleReaction: onToggleReaction,
                        onMediaLoad: scrollToAnchor
                    }, msg.id)),
                    f.jsx("div", { ref: bottomRef, style: { height: 0, flexShrink: 0 } })
                ]
            }),
            showScrollDown && f.jsx("button", {
                className: "scroll-down-btn",
                onClick: () => {
                    scrollContainerRef.current && scrollContainerRef.current.scrollTo({
                        top: scrollContainerRef.current.scrollHeight,
                        behavior: "smooth"
                    });
                },
                children: f.jsx("svg", {
                    width: "16",
                    height: "16",
                    viewBox: "0 0 24 24",
                    fill: "none",
                    stroke: "currentColor",
                    strokeWidth: "2.5",
                    strokeLinecap: "round",
                    strokeLinejoin: "round",
                    children: f.jsx("polyline", {
                        points: "6 9 12 15 18 9"
                    })
                })
            }),
            f.jsxs("div", {
                className: "input-wrapper",
                children: [
                    (replyMessage || editingMessageId !== null) && f.jsxs("div", {
                        id: "activeReplyPreview",
                        style: {
                            display: "flex"
                        },
                        children: [
                            f.jsxs("div", {
                                className: "reply-preview-left",
                                children: [
                                    f.jsx("div", {
                                        className: "reply-preview-author",
                                        style: {
                                            color: "var(--accent-blue)"
                                        },
                                        children: editingMessageId !== null ? "Редактирование" : `Ответ ${replyMessage?.author}`
                                    }),
                                    f.jsx("div", {
                                        className: "reply-preview-text",
                                        style: {
                                            color: "var(--text-muted)"
                                        },
                                        children: editingMessageId !== null ? messages.find(m => m.id === editingMessageId)?.text || "Медиа" : replyMessage?.text
                                    })
                                ]
                            }),
                            f.jsx("button", {
                                className: "cancel-reply",
                                onClick: resetInputState,
                                children: "✕"
                            })
                        ]
                    }),
                    f.jsxs("div", {
                        className: "input-area",
                        children: [
                            f.jsx("button", {
                                className: "input-icon",
                                "data-mode": "media",
                                onClick: () => mediaInputRef.current?.click(),
                                children: f.jsx("svg", {
                                    width: 18,
                                    height: 18,
                                    viewBox: "0 0 24 24",
                                    fill: "none",
                                    stroke: "currentColor",
                                    strokeWidth: 2,
                                    strokeLinecap: "round",
                                    strokeLinejoin: "round",
                                    children: f.jsx("path", {
                                        d: "M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48"
                                    })
                                })
                            }),
                            f.jsx("button", {
                                className: "input-icon",
                                "data-mode": "file",
                                onClick: () => fileInputRef.current?.click(),
                                children: f.jsx("svg", {
                                    width: 18,
                                    height: 18,
                                    viewBox: "0 0 24 24",
                                    fill: "none",
                                    stroke: "currentColor",
                                    strokeWidth: 2,
                                    strokeLinecap: "round",
                                    strokeLinejoin: "round",
                                    children: [
                                        f.jsx("path", {
                                            d: "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"
                                        }),
                                        f.jsx("polyline", {
                                            points: "14 2 14 8 20 8"
                                        }),
                                        f.jsx("line", {
                                            x1: "16",
                                            y1: "13",
                                            x2: "8",
                                            y2: "13"
                                        }),
                                        f.jsx("line", {
                                            x1: "16",
                                            y1: "17",
                                            x2: "8",
                                            y2: "17"
                                        }),
                                        f.jsx("polyline", {
                                            points: "10 9 9 9 8 9"
                                        })
                                    ]
                                })
                            }),
                            f.jsx("input", {
                                ref: mediaInputRef,
                                type: "file",
                                accept: "image/*,video/*",
                                multiple: true,
                                style: {
                                    display: "none"
                                },
                                onChange: handleMediaChange
                            }),
                            f.jsx("input", {
                                ref: fileInputRef,
                                type: "file",
                                accept: "*/*",
                                style: {
                                    display: "none"
                                },
                                onChange: handleVideoChange
                            }),
                            f.jsx("div", {
                                className: "msg-input-box",
                                children: f.jsx("textarea", {
                                    ref: inputRef,
                                    className: "msg-input",
                                    rows: 1,
                                    placeholder: "Сообщение",
                                    value: inputText,
                                    onChange: handleInputChange,
                                    onKeyDown: handleInputKeyDown
                                })
                            }),
                            f.jsx("button", {
                                className: `send-mic-btn${isRecording ? " recording" : ""}`,
                                onContextMenu: e => e.preventDefault(),
                                onPointerDown: e => {
                                    e.preventDefault();
                                    hasTextToSend ? handleSendMessageClick() : startAudioRecording();
                                },
                                onPointerUp: () => {
                                    stopAudioRecording();
                                },
                                onPointerLeave: () => {
                                    stopAudioRecording();
                                },
                                onPointerCancel: () => {
                                    stopAudioRecording();
                                },
                                children: hasTextToSend ? f.jsx("svg", {
                                    width: 18,
                                    height: 18,
                                    viewBox: "0 0 24 24",
                                    fill: "none",
                                    stroke: "currentColor",
                                    strokeWidth: 2,
                                    strokeLinecap: "round",
                                    strokeLinejoin: "round",
                                    children: [
                                        f.jsx("line", {
                                            x1: 22,
                                            y1: 2,
                                            x2: 11,
                                            y2: 13
                                        }),
                                        f.jsx("polygon", {
                                            points: "22 2 15 22 11 13 2 9 22 2"
                                        })
                                    ]
                                }) : isRecording ? f.jsx("svg", {
                                    width: 18,
                                    height: 18,
                                    viewBox: "0 0 24 24",
                                    fill: "none",
                                    stroke: "currentColor",
                                    strokeWidth: 2,
                                    strokeLinecap: "round",
                                    strokeLinejoin: "round",
                                    children: f.jsx("rect", {
                                        x: 4,
                                        y: 4,
                                        width: 16,
                                        height: 16,
                                        rx: 2
                                    })
                                }) : f.jsx("svg", {
                                    width: 18,
                                    height: 18,
                                    viewBox: "0 0 24 24",
                                    fill: "none",
                                    stroke: "currentColor",
                                    strokeWidth: 2,
                                    strokeLinecap: "round",
                                    strokeLinejoin: "round",
                                    children: [
                                        f.jsx("path", {
                                            d: "M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"
                                        }),
                                        f.jsx("path", {
                                            d: "M19 10v1a7 7 0 0 1-14 0v-1"
                                        }),
                                        f.jsx("line", {
                                            x1: 12,
                                            y1: 19,
                                            x2: 12,
                                            y2: 23
                                        }),
                                        f.jsx("line", {
                                            x1: 8,
                                            y1: 23,
                                            x2: 16,
                                            y2: 23
                                        })
                                    ]
                                })
                            })
                        ]
                    })
                ]
            }),
            f.jsx(ContextMenu, {
                message: activeMessage,
                position: contextMenuPos,
                onClose: () => {
                    setActiveMessage(null);
                    setContextMenuPos(null);
                },
                onReply: handleReplyAction,
                onCopy: handleCopyAction,
                onPin: handlePinAction,
                onEdit: handleEditAction,
                onDelete: () => {
                    activeMessage && onDeleteMessage(activeMessage.id);
                },
                onReaction: emoji => {
                    activeMessage && onToggleReaction(activeMessage.id, emoji);
                },
                onSaveMedia: handleSaveMedia
            })
        ]
    });
}