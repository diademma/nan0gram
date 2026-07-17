import { saveMediaToDownloads } from './n0g_msg_bridge_102.js';
import { ThemePanel, SettingsPanel } from './n0g_msg_settings_view_103.js';

const T = window.React;
const f = {
    jsx: (type, props, key) => T.createElement(type, { ...props, key }),
    jsxs: (type, props, key) => T.createElement(type, { ...props, key })
};

export function Sidebar({
    contacts,
    activeChatId,
    chatPreviews,
    chatThemes,
    defaultTheme,
    onSelectChat,
    onSaveTheme,
    onSaveDefaultTheme
}) {
    const [activeThemePanelChatId, setActiveThemePanelChatId] = T.useState(null);
    const [isSettingsOpen, setIsSettingsOpen] = T.useState(!1);
    const touchStartX = T.useRef(0);
    const touchStartY = T.useRef(0);
    const currentSwipeChatId = T.useRef(null);
    const isSwiping = T.useRef(!1);

    const handleTouchStart = T.useCallback(k => {
        const X = k.touches[0];
        touchStartX.current = X.clientX;
        touchStartY.current = X.clientY;
        isSwiping.current = !1;
        const vl = document.elementFromPoint(X.clientX, X.clientY)?.closest("[data-chat-id]");
        currentSwipeChatId.current = vl?.dataset.chatId ?? null;
    }, []);

    const handleTouchMove = T.useCallback(k => {
        if (Math.abs(k.touches[0].clientX - touchStartX.current) > 8) {
            isSwiping.current = !0;
        }
    }, []);

    const handleTouchEnd = T.useCallback(k => {
        if (k.target.tagName === "INPUT") return;
        const X = k.changedTouches[0];
        const dX = X.clientX - touchStartX.current;
        const dY = Math.abs(X.clientY - touchStartY.current);

        if (isSettingsOpen && dX < -60 && dY < Math.abs(dX) * 0.8) {
            setIsSettingsOpen(!1);
            return;
        }

        if (touchStartY.current >= 60 && dX > 60 && dX > dY * 1.2) {
            if (currentSwipeChatId.current) {
                setActiveThemePanelChatId(currentSwipeChatId.current);
            } else {
                setIsSettingsOpen(!0);
            }
            return;
        }

        if (!isSwiping.current || Math.abs(dX) < 10) {
            const al = document.elementFromPoint(X.clientX, X.clientY)?.closest("[data-chat-id]");
            if (al && al.dataset.chatId) {
                onSelectChat(al.dataset.chatId);
            }
        }
    }, [onSelectChat, isSettingsOpen]);

    const activeThemeChat = activeThemePanelChatId ? contacts.find(k => k.id === activeThemePanelChatId) : null;

    return f.jsxs("div", {
        className: "sidebar",
        style: {
            overflow: "hidden",
            position: "relative",
            userSelect: "none"
        },
        onTouchStart: handleTouchStart,
        onTouchMove: handleTouchMove,
        onTouchEnd: handleTouchEnd,
        children: [
            f.jsxs("div", {
                className: "sidebar-header",
                children: [
                    f.jsx("div", {
                        className: "app-title",
                        children: "nan0gram"
                    }),
                    f.jsx("button", {
                        className: "sidebar-gear-btn",
                        onPointerDown: k => k.stopPropagation(),
                        onClick: k => {
                            k.stopPropagation();
                            setIsSettingsOpen(!0);
                        },
                        children: f.jsxs("svg", {
                            width: 18,
                            height: 18,
                            viewBox: "0 0 24 24",
                            fill: "none",
                            stroke: "currentColor",
                            strokeWidth: 2,
                            strokeLinecap: "round",
                            strokeLinejoin: "round",
                            children: [
                                f.jsx("circle", { cx: 12, cy: 12, r: 3 }),
                                f.jsx("path", { d: "M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" })
                            ]
                        })
                    })
                ]
            }),
            f.jsx("div", {
                className: "chat-list",
                style: { flex: 1 },
                children: contacts.map(k => f.jsxs("div", {
                    "data-chat-id": k.id,
                    className: `chat-item${activeChatId === k.id ? " active" : ""}`,
                    children: [
                        f.jsx("div", {
                            className: "avatar",
                            style: { backgroundImage: `url('${k.avatar}')` }
                        }),
                        f.jsxs("div", {
                            className: "chat-info",
                            children: [
                                f.jsx("div", { className: "chat-name", children: k.name }),
                                f.jsx("div", { className: "chat-preview", children: chatPreviews[k.id] ?? k.preview })
                            ]
                        }),
                        k.unread > 0 && f.jsx("div", {
                            style: {
                                background: "var(--accent-blue)",
                                color: "#fff",
                                borderRadius: "50%",
                                width: 20,
                                height: 20,
                                fontSize: 12,
                                display: "flex",
                                alignItems: "center",
                                justifyContent: "center",
                                fontWeight: 600,
                                flexShrink: 0
                            },
                            children: k.unread
                        })
                    ]
                }, k.id))
            }),
            f.jsx("div", {
                className: "theme-panel-overlay",
                style: {
                    transform: isSettingsOpen ? "translateX(0)" : "translateX(-100%)",
                    pointerEvents: isSettingsOpen ? "auto" : "none"
                },
                children: f.jsx(SettingsPanel, {
                    defaultTheme: defaultTheme,
                    onSave: onSaveDefaultTheme,
                    onClose: () => setIsSettingsOpen(!1)
                })
            }),
            f.jsx("div", {
                className: "theme-panel-overlay",
                style: {
                    transform: activeThemePanelChatId ? "translateX(0)" : "translateX(-100%)",
                    pointerEvents: activeThemePanelChatId ? "auto" : "none"
                },
                children: activeThemeChat && activeThemePanelChatId && f.jsx(ThemePanel, {
                    contactName: activeThemeChat.name,
                    theme: chatThemes[activeThemePanelChatId] ?? defaultTheme,
                    onSave: k => onSaveTheme(activeThemePanelChatId, k),
                    onClose: () => setActiveThemePanelChatId(null)
                })
            })
        ]
    });
}

export function ProfileModal({ profile, onClose, onOpenLightbox }) {
    const isOpen = profile !== null;
    return f.jsxs("div", {
        className: "profile-modal",
        style: {
            transform: isOpen ? "translateX(0)" : "translateX(100%)",
            display: "flex"
        },
        children: [
            f.jsxs("div", {
                className: "profile-header",
                children: [
                    f.jsx("span", {
                        style: { cursor: "pointer" },
                        onClick: onClose,
                        children: "←"
                    }),
                    f.jsx("span", { children: "⋮" })
                ]
            }),
            profile && f.jsxs(f.Fragment, {
                children: [
                    f.jsxs("div", {
                        className: "profile-center",
                        children: [
                            f.jsx("div", {
                                className: "profile-big-avatar",
                                style: { backgroundImage: `url('${profile.avatar}')` },
                                onClick: () => onOpenLightbox(profile.avatar)
                            }),
                            f.jsx("div", { className: "profile-name", children: profile.name }),
                            f.jsx("div", { className: "profile-status", children: "был(а) на этой неделе" })
                        ]
                    }),
                    f.jsxs("div", {
                        className: "profile-buttons",
                        children: [
                            f.jsxs("div", {
                                className: "prof-btn",
                                onClick: onClose,
                                children: [f.jsx("span", { children: "💬" }), "Чат"]
                            }),
                            f.jsxs("div", {
                                className: "prof-btn",
                                children: [f.jsx("span", { children: "🔕" }), "Звук"]
                            })
                        ]
                    }),
                    f.jsxs("div", {
                        className: "profile-info-card",
                        children: [
                            f.jsxs("div", {
                                className: "info-item",
                                children: [
                                    f.jsx("div", { className: "info-text", children: "Привет, я использую nan0gram!" }),
                                    f.jsx("div", { className: "info-label", children: "О себе" })
                                ]
                            }),
                            f.jsxs("div", {
                                className: "info-item",
                                children: [
                                    f.jsx("div", { className: "info-text", children: profile.username }),
                                    f.jsx("div", { className: "info-label", children: "Имя пользователя" })
                                ]
                            }),
                            f.jsxs("div", {
                                className: "info-item",
                                style: { marginTop: 10 },
                                children: [
                                    f.jsxs("div", {
                                        className: "info-text",
                                        style: { fontSize: 14, color: "#ccc" },
                                        children: ["ID: ", profile.userId]
                                    }),
                                    f.jsx("div", { className: "info-label", children: "ID профиля" })
                                ]
                            })
                        ]
                    })
                ]
            })
        ]
    });
}

export function Lightbox({ items, initialIndex = 0, onClose }) {
    const [activeIndex, setActiveIndex] = T.useState(initialIndex);
    const [showDownloadChoice, setShowDownloadChoice] = T.useState(false);
    const [scale, setScale] = T.useState(1);
    const [translate, setTranslate] = T.useState({ x: 0, y: 0 });
    
    const touchDistance = T.useRef(null);
    const lastTouch = T.useRef(null);
    const lastTapTime = T.useRef(0);
    const touchStartX = T.useRef(0);
    const touchStartY = T.useRef(0);
    const isDragging = T.useRef(!1);

    const activeItem = items ? items[activeIndex] : null;
    const isVideo = activeItem?.isVideo ?? !1;
    const hasMultiple = (items?.length ?? 0) > 1;

    T.useEffect(() => { setActiveIndex(initialIndex); }, [initialIndex, items]);
    T.useEffect(() => {
        setScale(1);
        setTranslate({ x: 0, y: 0 });
    }, [activeIndex]);

    const goNext = T.useCallback(() => { items && setActiveIndex(Y => Math.min(Y + 1, items.length - 1)) }, [items]);
    const goPrev = T.useCallback(() => { setActiveIndex(Y => Math.max(Y - 1, 0)) }, []);
    
    const getDistance = Y => Math.hypot(Y[0].clientX - Y[1].clientX, Y[0].clientY - Y[1].clientY);

    const handleTouchStart = T.useCallback(Y => {
        if (Y.touches.length === 2) {
            touchDistance.current = getDistance(Y.touches);
            Y.preventDefault();
        } else if (Y.touches.length === 1) {
            touchStartX.current = Y.touches[0].clientX;
            touchStartY.current = Y.touches[0].clientY;
            lastTouch.current = { x: Y.touches[0].clientX, y: Y.touches[0].clientY };
            isDragging.current = !1;
        }
    }, []);

    const handleTouchMove = T.useCallback(Y => {
        if (Y.touches.length === 2 && touchDistance.current !== null) {
            Y.preventDefault();
            const al = getDistance(Y.touches);
            const Cl = al / touchDistance.current;
            touchDistance.current = al;
            setScale(xl => Math.max(1, Math.min(5, xl * Cl)));
        } else if (Y.touches.length === 1 && lastTouch.current) {
            const al = Y.touches[0].clientX - lastTouch.current.x;
            const Cl = Y.touches[0].clientY - lastTouch.current.y;
            lastTouch.current = { x: Y.touches[0].clientX, y: Y.touches[0].clientY };
            if (scale > 1) {
                Y.preventDefault();
                isDragging.current = !0;
                setTranslate(xl => ({ x: xl.x + al, y: xl.y + Cl }));
            }
        }
    }, [scale]);

    const handleTouchEnd = T.useCallback(Y => {
        touchDistance.current = null;
        if (Y.changedTouches.length === 1) {
            const al = Y.changedTouches[0].clientX;
            const Cl = Y.changedTouches[0].clientY;
            const xl = al - touchStartX.current;
            const Tl = Math.abs(Cl - touchStartY.current);
            const Bl = Date.now();
            
            if (!isVideo && Bl - lastTapTime.current < 300 && !isDragging.current) {
                lastTapTime.current = 0;
                if (scale > 1.5) {
                    setScale(1);
                    setTranslate({ x: 0, y: 0 });
                } else {
                    setScale(2.5);
                }
                return;
            }
            
            lastTapTime.current = Bl;
            
            if (scale <= 1 && Math.abs(xl) > 50 && Tl < 80 && hasMultiple) {
                xl < 0 ? goNext() : goPrev();
                return;
            }
            
            const rl = Y.changedTouches[0].clientY - touchStartY.current;
            if (scale <= 1 && rl > 100 && Math.abs(xl) < 60) {
                onClose();
            }
        }
    }, [scale, hasMultiple, isVideo, goNext, goPrev, onClose]);

    if (!items || !activeItem) return null;

    return f.jsxs("div", {
        className: "lightbox",
        style: { display: "flex" },
        onTouchStart: handleTouchStart,
        onTouchMove: handleTouchMove,
        onTouchEnd: handleTouchEnd,
        children: [
            f.jsx("button", { className: "lightbox-close", onClick: onClose, children: "✕" }),
            hasMultiple && f.jsxs("div", {
                className: "lightbox-counter",
                children: [activeIndex + 1, " / ", items.length]
            }),
            hasMultiple && activeIndex > 0 && f.jsx("button", {
                className: "lightbox-arrow left",
                onClick: goPrev,
                children: "‹"
            }),
            hasMultiple && activeIndex < items.length - 1 && f.jsx("button", {
                className: "lightbox-arrow right",
                onClick: goNext,
                children: "›"
            }),
            f.jsx("div", {
                className: "lightbox-main",
                onClick: onClose,
                children: isVideo ? f.jsx("video", {
                    src: activeItem.src,
                    poster: activeItem.poster,
                    controls: !0,
                    autoPlay: !0,
                    playsInline: !0,
                    className: "lightbox-video",
                    onClick: Y => Y.stopPropagation()
                }, activeItem.src) : f.jsx("img", {
                    src: activeItem.src,
                    alt: "",
                    className: "lightbox-img",
                    onClick: Y => Y.stopPropagation(),
                    style: {
                        transform: `scale(${scale}) translate(${translate.x/scale}px, ${translate.y/scale}px)`,
                        transition: isDragging.current ? "none" : "transform 0.2s",
                        touchAction: "none",
                        cursor: scale > 1 ? "grab" : "default"
                    }
                }, activeItem.src)
            }),
            hasMultiple && f.jsx("div", {
                className: "lightbox-thumbs",
                onClick: Y => Y.stopPropagation(),
                children: items.map((Y, al) => f.jsx("div", {
                    className: `lightbox-thumb${al === activeIndex ? " active" : ""}`,
                    onClick: () => setActiveIndex(al),
                    children: Y.isVideo ? f.jsx("div", { className: "thumb-video-icon", children: "▶" }) : f.jsx("img", { src: Y.src, alt: "" })
                }, al))
            }),
            !isVideo && scale === 1 && f.jsx("button", {
                className: "lightbox-download",
                onPointerDown: Y => Y.stopPropagation(),
                onClick: Y => {
                    Y.stopPropagation();
                    if (hasMultiple) {
                        setShowDownloadChoice(true);
                    } else {
                        const w = activeItem.src;
                        const ext = w.startsWith("data:video") ? "mp4" : w.startsWith("data:audio") ? "webm" : "jpg";
                        const suggestedName = `media_${Date.now()}.${ext}`;
                        saveMediaToDownloads(w, suggestedName);
                    }
                },
                children: "⬇ Скачать"
            }),
            !isVideo && scale > 1 && f.jsxs("div", {
                className: "lightbox-zoom-hint",
                children: [Math.round(scale * 100), "% · двойной тап для сброса"]
            }),
            showDownloadChoice && f.jsxs("div", {
                style: {
                    position: "fixed",
                    inset: 0,
                    background: "rgba(0,0,0,0.85)",
                    display: "flex",
                    flexDirection: "column",
                    justifyContent: "center",
                    alignItems: "center",
                    zIndex: 400,
                    gap: "14px",
                    backdropFilter: "blur(10px)",
                    WebkitBackdropFilter: "blur(10px)"
                },
                onPointerDown: e => e.stopPropagation(),
                onClick: e => {
                    e.stopPropagation();
                    setShowDownloadChoice(false);
                },
                children: [
                    f.jsx("div", {
                        style: {
                            color: "#fff",
                            fontSize: "16px",
                            fontWeight: "600",
                            marginBottom: "6px"
                        },
                        children: "What do you want to download?"
                    }),
                    f.jsx("button", {
                        style: {
                            background: "var(--accent-blue)",
                            border: "none",
                            color: "#fff",
                            padding: "12px 24px",
                            borderRadius: "16px",
                            fontSize: "14px",
                            fontWeight: "600",
                            cursor: "pointer",
                            width: "200px"
                        },
                        onClick: e => {
                            e.stopPropagation();
                            setShowDownloadChoice(false);
                            const w = activeItem.src;
                            const ext = w.startsWith("data:video") ? "mp4" : w.startsWith("data:audio") ? "webm" : "jpg";
                            const name = `media_${Date.now()}.${ext}`;
                            saveMediaToDownloads(w, name);
                        },
                        children: "🖼️ Only this photo"
                    }),
                    f.jsx("button", {
                        style: {
                            background: "#382b46",
                            border: "1px solid rgba(167, 115, 209, 0.4)",
                            color: "#fff",
                            padding: "12px 24px",
                            borderRadius: "16px",
                            fontSize: "14px",
                            fontWeight: "600",
                            cursor: "pointer",
                            width: "200px"
                        },
                        onClick: e => {
                            e.stopPropagation();
                            setShowDownloadChoice(false);
                            items.forEach((item, sl) => {
                                const w = item.src;
                                const ext = w.startsWith("data:video") ? "mp4" : "jpg";
                                const name = `album_${Date.now()}_${sl+1}.${ext}`;
                                saveMediaToDownloads(w, name);
                            });
                        },
                        children: "📚 Entire album"
                    }),
                    f.jsx("button", {
                        style: {
                            background: "transparent",
                            border: "none",
                            color: "var(--text-muted)",
                            fontSize: "14px",
                            cursor: "pointer",
                            marginTop: "6px"
                        },
                        onClick: e => {
                            e.stopPropagation();
                            setShowDownloadChoice(false);
                        },
                        children: "Cancel"
                    })
                ]
            })
        ]
    });
}

export function Toast({ message, onDone }) {
    const [isVisible, setIsVisible] = T.useState(!1);
    
    T.useEffect(() => {
        const _ = setTimeout(() => setIsVisible(!0), 10);
        const O = setTimeout(() => {
            setIsVisible(!1);
            setTimeout(onDone, 300);
        }, 3200);
        return () => {
            clearTimeout(_);
            clearTimeout(O);
        };
    }, [onDone]);
    
    return f.jsx("div", {
        style: {
            position: "fixed",
            top: isVisible ? 20 : -60,
            left: "50%",
            transform: "translateX(-50%)",
            background: "rgba(0,0,0,0.85)",
            border: "1px solid rgba(255,255,255,0.1)",
            color: "white",
            padding: "10px 20px",
            borderRadius: 20,
            fontSize: 14,
            zIndex: 1e3,
            opacity: isVisible ? 1 : 0,
            transition: "opacity 0.3s, top 0.3s",
            pointerEvents: "none",
            boxShadow: "0 4px 15px rgba(0,0,0,0.5)",
            textAlign: "center",
            whiteSpace: "nowrap",
            maxWidth: "90vw"
        },
        children: message
    });
}