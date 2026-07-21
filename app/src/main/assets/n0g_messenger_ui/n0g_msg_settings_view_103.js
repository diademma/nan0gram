import { hslToRgb } from './n0g_msg_utils_101.js';
import { 
    getDeviceId, 
    clearMediaCache, 
    clearAllHistoryLog 
} from './n0g_msg_bridge_102.js';

const T = window.T || window.React;
const f = window.f || {
    jsx: (type, props, key) => T.createElement(type, { ...props, key }),
    jsxs: (type, props, key) => T.createElement(type, { ...props, key })
};

export function ThemePanel({
    contactName,
    theme: initialTheme,
    onSave,
    onClose
}) {
    const [theme, setTheme] = T.useState(initialTheme);
    const handleHueChange = T.useCallback((e) => {
        const val = Number(e.target.value);
        let r, g, b;
        if(val <= 0) {
            r = 240; g = 240; b = 240;
        } else if(val >= 380) {
            r = 30; g = 30; b = 30;
        } else {
            let h = (val - 10) / 360;
            if(h < 0) h = 0;
            if(h > 1) h = 1;
            const rgb = hslToRgb(h, 0.75, 0.45);
            r = rgb[0]; g = rgb[1]; b = rgb[2];
        }
        const outRgbStr = `${r},${g},${b}`;
        setTheme(prev => ({
            ...prev,
            schemeId: "custom",
            outRgb: outRgbStr,
            inRgb: outRgbStr,
            customHue: val
        }));
    }, []);
    const handleSave = T.useCallback(() => {
        onSave(theme);
        onClose();
    }, [theme, onSave, onClose]);
    const previewColors = {
        in: `rgba(${theme.inRgb},${theme.opacity})`,
        out: `rgba(${theme.outRgb},${theme.opacity})`
    };
    return f.jsxs("div", {
        className: "theme-panel",
        children: [f.jsxs("div", {
            className: "theme-panel-header",
            children: [f.jsx("button", {
                className: "theme-back-btn",
                onClick: onClose,
                children: "←"
            }), f.jsxs("div", {
                className: "theme-panel-title",
                children: [f.jsx("div", {
                    style: {
                        fontSize: 16,
                        fontWeight: 600
                    },
                    children: "Тема чата"
                }), f.jsx("div", {
                    style: {
                        fontSize: 13,
                        color: "#8b7d98"
                    },
                    children: contactName
                })]
            }), f.jsx("button", {
                className: "theme-save-btn",
                onClick: handleSave,
                children: "Сохранить"
            })]
        }), f.jsxs("div", {
            className: "theme-panel-body",
            children: [f.jsxs("div", {
                className: "theme-preview-section",
                children: [f.jsx("div", {
                    className: "theme-preview-label",
                    children: "Предпросмотр"
                }), f.jsxs("div", {
                    className: "theme-preview-chat",
                    children: [f.jsxs("div", {
                        className: "theme-preview-msg in",
                        style: {
                            background: previewColors.in
                        },
                        children: ["Привет! Как дела? 😊", f.jsx("span", {
                            className: "theme-preview-time",
                            children: "12:34"
                        })]
                    }), f.jsxs("div", {
                        className: "theme-preview-msg out",
                        style: {
                            background: previewColors.out
                        },
                        children: ["Всё отлично, спасибо! 🔥", f.jsx("span", {
                            className: "theme-preview-time",
                            children: "12:35 ✓✓"
                        })]
                    }), f.jsxs("div", {
                        className: "theme-preview-msg in",
                        style: {
                            background: previewColors.in
                        },
                        children: ["Жду с нетерпением 🎉", f.jsx("span", {
                            className: "theme-preview-time",
                            children: "12:36"
                        })]
                    })]
                })]
            }), f.jsxs("div", {
                className: "theme-section",
                children: [f.jsx("div", {
                    className: "theme-section-title",
                    children: "Прозрачность пузырей"
                }), f.jsxs("div", {
                    className: "opacity-row",
                    children: [f.jsx("span", {
                        className: "opacity-label",
                        children: "Прозрачный"
                    }), f.jsx("input", {
                        type: "range",
                        min: 30,
                        max: 100,
                        value: Math.round(theme.opacity * 100),
                        onChange: e => setTheme(prev => ({
                            ...prev,
                            opacity: Number(e.target.value) / 100
                        })),
                        className: "opacity-slider"
                    }), f.jsxs("span", {
                        className: "opacity-value",
                        children: [Math.round(theme.opacity * 100), "%"]
                    })]
                })]
            }), f.jsxs("div", {
                className: "theme-section",
                children: [f.jsx("div", {
                    className: "theme-section-title",
                    children: "Цвет сообщений (Оттенок)"
                }), f.jsxs("div", {
                    style: {
                        display: "flex",
                        flexDirection: "column",
                        gap: "16px",
                        background: "#15101b",
                        border: "1px solid rgba(255,255,255,0.05)",
                        borderRadius: "16px",
                        padding: "16px"
                    },
                    children: [f.jsxs("div", {
                        style: {
                            display: "flex",
                            alignItems: "center",
                            justifyContent: "space-between"
                        },
                        children: [f.jsx("span", {
                            style: {
                                fontSize: "14px",
                                color: "#fff",
                                fontWeight: 500
                            },
                            children: "Оттенок"
                        }), f.jsx("div", {
                            style: {
                                width: "28px",
                                height: "28px",
                                borderRadius: "50%",
                                background: `rgb(${theme.outRgb})`,
                                border: "2px solid rgba(255,255,255,0.8)",
                                boxShadow: "0 2px 8px rgba(0,0,0,0.5)"
                            }
                        })]
                    }), f.jsx("input", {
                        type: "range",
                        min: 0,
                        max: 380,
                        value: theme.customHue !== undefined ? theme.customHue : 260,
                        onChange: handleHueChange,
                        className: "hue-slider",
                        style: {
                            width: "100%",
                            WebkitAppearance: "none",
                            height: "14px",
                            borderRadius: "8px",
                            background: "linear-gradient(to right, #f0f0f0 0%, #ff0000 5%, #ffff00 20%, #00ff00 35%, #00ffff 50%, #0000ff 65%, #ff00ff 80%, #ff0000 95%, #1e1e1e 100%)",
                            outline: "none",
                            margin: 0
                        }
                    })]
                })]
            })]
        })]
    });
}

export function SettingsPanel({
    defaultTheme: initialDefaultTheme,
    onSave,
    onClose
}) {
    const [activeTab, setActiveTab] = T.useState("main");
    const [localTheme, setLocalTheme] = T.useState(initialDefaultTheme);
    const [sidebarBlur, setSidebarBlur] = T.useState(() => Number(localStorage.getItem("nan0gram_sidebar_blur") || "20"));
    const [sidebarDarkness, setSidebarDarkness] = T.useState(() => Number(localStorage.getItem("nan0gram_sidebar_darkness") || "50"));
    const [username, setUsername] = T.useState(() => localStorage.getItem("nan0gram_username") || "Я");
    const [copied, setCopied] = T.useState(false);
    const [encryptMessages, setEncryptMessages] = T.useState(() => localStorage.getItem("nan0gram_encrypt_messages") === "true");

    const deviceId = getDeviceId();

    const handleCopy = () => {
        const temp = document.createElement("textarea");
        temp.value = deviceId;
        temp.style.position = "fixed";
        temp.style.opacity = "0";
        document.body.appendChild(temp);
        temp.select();
        try {
            document.execCommand("copy");
            setCopied(true);
            setTimeout(() => setCopied(false), 2000);
        } catch (e) {}
        document.body.removeChild(temp);
    };

    const handleSaveName = () => {
        localStorage.setItem("nan0gram_username", username);
        window.dispatchEvent(new CustomEvent('nan0gram:username-change', {
            detail: username
        }));
    };

    if(activeTab === "appearance") {
        const previewColors = {
            in: `rgba(${localTheme.inRgb},${localTheme.opacity})`,
            out: `rgba(${localTheme.outRgb},${localTheme.opacity})`
        };
        const handleHueChange = (e) => {
            const val = Number(e.target.value);
            let r, g, b;
            if(val <= 0) {
                r = 240;
                g = 240;
                b = 240;
            } else if(val >= 380) {
                r = 30;
                g = 30;
                b = 30;
            } else {
                let h = (val - 10) / 360;
                if(h < 0) h = 0;
                if(h > 1) h = 1;
                const rgb = hslToRgb(h, 0.75, 0.45);
                r = rgb[0];
                g = rgb[1];
                b = rgb[2];
            }
            const outRgbStr = `${r},${g},${b}`;
            setLocalTheme(prev => ({
                ...prev,
                schemeId: "custom",
                outRgb: outRgbStr,
                inRgb: outRgbStr,
                customHue: val
            }));
        };
        return f.jsxs("div", {
            className: "settings-panel",
            children: [
                f.jsx("style", {
                    dangerouslySetInnerHTML: {
                        __html: `
                    .hue-slider::-webkit-slider-thumb {
                        -webkit-appearance: none; width: 24px; height: 24px;
                        border-radius: 50%; background: #ffffff;
                        border: 2px solid #ccc; box-shadow: 0 2px 10px rgba(0,0,0,0.4);
                        cursor: pointer;
                    }
                `
                    }
                }),
                f.jsxs("div", {
                    className: "settings-header",
                    children: [
                        f.jsx("button", {
                            className: "theme-back-btn",
                            onClick: () => setActiveTab("main"),
                            children: "←"
                        }),
                        f.jsx("span", {
                            style: {
                                fontWeight: 600,
                                fontSize: 16
                            },
                            children: "Оформление"
                        }),
                        f.jsx("button", {
                            className: "theme-save-btn",
                            onClick: () => {
                                onSave(localTheme);
                                localStorage.setItem("nan0gram_sidebar_blur", sidebarBlur);
                                localStorage.setItem("nan0gram_sidebar_darkness", sidebarDarkness);
                                document.documentElement.style.setProperty("--sidebar-blur", sidebarBlur + "px");
                                document.documentElement.style.setProperty("--sidebar-brightness", (100 - sidebarDarkness) / 100);
                                setActiveTab("main")
                            },
                            children: "Применить"
                        })
                    ]
                }),
                f.jsxs("div", {
                    className: "theme-panel-body",
                    children: [
                        f.jsxs("div", {
                            className: "theme-preview-section",
                            children: [
                                f.jsx("div", {
                                    className: "theme-preview-label",
                                    children: "Сайдбар"
                                }),
                                f.jsxs("div", {
                                    style: {
                                        position: "relative",
                                        height: "140px",
                                        borderRadius: "14px",
                                        overflow: "hidden",
                                        border: "1px solid rgba(255,255,255,0.1)"
                                    },
                                    children: [
                                        f.jsx("div", {
                                            style: {
                                                position: "absolute",
                                                inset: 0,
                                                backgroundImage: "var(--wallpaper-url)",
                                                backgroundSize: "cover",
                                                backgroundPosition: "left center",
                                                filter: `blur(${sidebarBlur}px) brightness(${(100-sidebarDarkness)/100})`,
                                                zIndex: 0
                                            }
                                        }),
                                        f.jsxs("div", {
                                            style: {
                                                position: "relative",
                                                zIndex: 1,
                                                padding: "10px",
                                                display: "flex",
                                                flexDirection: "column",
                                                gap: "8px"
                                            },
                                            children: [
                                                f.jsxs("div", {
                                                    style: {
                                                        display: "flex",
                                                        alignItems: "center",
                                                        gap: "12px",
                                                        background: "rgba(255,255,255,0.03)",
                                                        padding: "10px 14px",
                                                        borderRadius: "14px",
                                                        border: "1px solid rgba(255,255,255,0.04)"
                                                    },
                                                    children: [
                                                        f.jsx("div", {
                                                            style: {
                                                                width: "36px",
                                                                height: "36px",
                                                                borderRadius: "50%",
                                                                background: "#a773d1"
                                                            }
                                                        }),
                                                        f.jsxs("div", {
                                                            style: {
                                                                display: "flex",
                                                                flexDirection: "column"
                                                            },
                                                            children: [
                                                                f.jsx("div", {
                                                                    style: {
                                                                        fontSize: "14px",
                                                                        color: "#fff",
                                                                        fontWeight: 500
                                                                    },
                                                                    children: "Алиса"
                                                                }),
                                                                f.jsx("div", {
                                                                    style: {
                                                                        fontSize: "12px",
                                                                        color: "#8b7d98"
                                                                    },
                                                                    children: "Привет! Как дела?"
                                                                })
                                                            ]
                                                        })
                                                    ]
                                                }),
                                                f.jsxs("div", {
                                                    style: {
                                                        display: "flex",
                                                        alignItems: "center",
                                                        gap: "12px",
                                                        background: "rgba(167,115,209,0.12)",
                                                        padding: "10px 14px",
                                                        borderRadius: "14px",
                                                        border: "1px solid rgba(167,115,209,0.25)",
                                                        boxShadow: "0 4px 15px rgba(167,115,209,0.08)"
                                                    },
                                                    children: [
                                                        f.jsx("div", {
                                                            style: {
                                                                width: "36px",
                                                                height: "36px",
                                                                borderRadius: "50%",
                                                                background: "#382b46"
                                                            }
                                                        }),
                                                        f.jsxs("div", {
                                                            style: {
                                                                display: "flex",
                                                                flexDirection: "column"
                                                            },
                                                            children: [
                                                                f.jsx("div", {
                                                                    style: {
                                                                        fontSize: "14px",
                                                                        color: "#fff",
                                                                        fontWeight: 500
                                                                    },
                                                                    children: "Работа"
                                                                }),
                                                                f.jsx("div", {
                                                                    style: {
                                                                        fontSize: "12px",
                                                                        color: "#8b7d98"
                                                                    },
                                                                    children: "Отчет готов?"
                                                                })
                                                            ]
                                                        })
                                                    ]
                                                })
                                            ]
                                        })
                                    ]
                                })
                            ]
                        }),
                        f.jsxs("div", {
                            className: "theme-preview-section",
                            children: [
                                f.jsx("div", {
                                    className: "theme-preview-label",
                                    children: "Сообщения"
                                }),
                                f.jsxs("div", {
                                    className: "theme-preview-chat",
                                    style: {
                                        backgroundImage: "var(--wallpaper-url)"
                                    },
                                    children: [
                                        f.jsxs("div", {
                                            className: "theme-preview-msg in",
                                            style: {
                                                background: previewColors.in
                                            },
                                            children: ["Привет! Красивая тема 😊 ", f.jsx("span", {
                                                className: "theme-preview-time",
                                                children: "12:34"
                                            })]
                                        }),
                                        f.jsxs("div", {
                                            className: "theme-preview-msg out",
                                            style: {
                                                background: previewColors.out
                                            },
                                            children: ["Согласен, выглядит! ✨ ", f.jsx("span", {
                                                className: "theme-preview-time",
                                                children: "12:35 ✓✓"
                                            })]
                                        })
                                    ]
                                })
                            ]
                        }),
                        f.jsxs("div", {
                            className: "theme-section",
                            children: [
                                f.jsx("div", {
                                    className: "theme-section-title",
                                    children: "Размытие сайдбара"
                                }),
                                f.jsxs("div", {
                                    className: "opacity-row",
                                    children: [
                                        f.jsx("span", {
                                            className: "opacity-label",
                                            children: "Размытие"
                                        }),
                                        f.jsx("input", {
                                            type: "range",
                                            min: 0,
                                            max: 40,
                                            value: sidebarBlur,
                                            onChange: e => setSidebarBlur(Number(e.target.value)),
                                            className: "opacity-slider"
                                        }),
                                        f.jsxs("span", {
                                            className: "opacity-value",
                                            children: [sidebarBlur, "px"]
                                        })
                                    ]
                                })
                            ]
                        }),
                        f.jsxs("div", {
                            className: "theme-section",
                            children: [
                                f.jsx("div", {
                                    className: "theme-section-title",
                                    children: "Затемнение сайдбара"
                                }),
                                f.jsxs("div", {
                                    className: "opacity-row",
                                    children: [
                                        f.jsx("span", {
                                            className: "opacity-label",
                                            children: "Темнота"
                                        }),
                                        f.jsx("input", {
                                            type: "range",
                                            min: 0,
                                            max: 90,
                                            value: sidebarDarkness,
                                            onChange: e => setSidebarDarkness(Number(e.target.value)),
                                            className: "opacity-slider"
                                        }),
                                        f.jsxs("span", {
                                            className: "opacity-value",
                                            children: [sidebarDarkness, "%"]
                                        })
                                    ]
                                })
                            ]
                        }),
                        f.jsxs("div", {
                            className: "theme-section",
                            children: [
                                f.jsx("div", {
                                    className: "theme-section-title",
                                    children: "Цвет Бабла (сообщения)"
                                }),
                                f.jsxs("div", {
                                    style: {
                                        display: "flex",
                                        flexDirection: "column",
                                        gap: "16px",
                                        background: "#15101b",
                                        border: "1px solid rgba(255,255,255,0.05)",
                                        borderRadius: "16px",
                                        padding: "16px"
                                    },
                                    children: [
                                        f.jsxs("div", {
                                            style: {
                                                display: "flex",
                                                alignItems: "center",
                                                justifyContent: "space-between"
                                            },
                                            children: [
                                                f.jsx("span", {
                                                    style: {
                                                        fontSize: "14px",
                                                        color: "#fff",
                                                        fontWeight: 500
                                                    },
                                                    children: "Оттенок"
                                                }),
                                                f.jsx("div", {
                                                    style: {
                                                        width: "28px",
                                                        height: "28px",
                                                        borderRadius: "50%",
                                                        background: `rgb(${localTheme.outRgb})`,
                                                        border: "2px solid rgba(255,255,255,0.8)",
                                                        boxShadow: "0 2px 8px rgba(0,0,0,0.5)"
                                                    }
                                                })
                                            ]
                                        }),
                                        f.jsx("input", {
                                            type: "range",
                                            min: 0,
                                            max: 380,
                                            value: localTheme.customHue !== undefined ? localTheme.customHue : 260,
                                            onChange: handleHueChange,
                                            className: "hue-slider",
                                            style: {
                                                width: "100%",
                                                WebkitAppearance: "none",
                                                height: "14px",
                                                borderRadius: "8px",
                                                background: "linear-gradient(to right, #f0f0f0 0%, #ff0000 5%, #ffff00 20%, #00ff00 35%, #00ffff 50%, #0000ff 65%, #ff00ff 80%, #ff0000 95%, #1e1e1e 100%)",
                                                outline: "none",
                                                margin: 0
                                            }
                                        })
                                    ]
                                })
                            ]
                        }),
                        f.jsxs("div", {
                            className: "theme-section",
                            style: {
                                marginTop: 10
                            },
                            children: [
                                f.jsx("div", {
                                    className: "theme-section-title",
                                    children: "Прозрачность бабла"
                                }),
                                f.jsxs("div", {
                                    className: "opacity-row",
                                    children: [
                                        f.jsx("span", {
                                            className: "opacity-label",
                                            children: "Прозрач."
                                        }),
                                        f.jsx("input", {
                                            type: "range",
                                            min: 30,
                                            max: 100,
                                            value: Math.round(localTheme.opacity * 100),
                                            onChange: e => setLocalTheme(prev => ({
                                                ...prev,
                                                opacity: Number(e.target.value) / 100
                                            })),
                                            className: "opacity-slider"
                                        }),
                                        f.jsxs("span", {
                                            className: "opacity-value",
                                            children: [Math.round(localTheme.opacity * 100), "%"]
                                        })
                                    ]
                                })
                            ]
                        })
                    ]
                })
            ]
        });
    }

    if(activeTab === "privacy") {
        return f.jsxs("div", {
            className: "settings-panel",
            children: [
                f.jsxs("div", {
                    className: "settings-header",
                    children: [
                        f.jsx("button", {
                            className: "theme-back-btn",
                            onClick: () => setActiveTab("main"),
                            children: "←"
                        }),
                        f.jsx("span", {
                            style: {
                                fontWeight: 600,
                                fontSize: 16
                            },
                            children: "Конфиденциальность"
                        }),
                        f.jsx("button", {
                            className: "theme-save-btn",
                            onClick: () => {
                                handleSaveName();
                                setActiveTab("main");
                            },
                            children: "Сохранить"
                        })
                    ]
                }),
                f.jsxs("div", {
                    className: "theme-panel-body",
                    children: [
                        f.jsxs("div", {
                            className: "theme-section",
                            children: [
                                f.jsx("div", {
                                    className: "theme-section-title",
                                    children: "Ваше имя в чате"
                                }),
                                f.jsx("input", {
                                    type: "text",
                                    value: username,
                                    onChange: e => setUsername(e.target.value),
                                    placeholder: "Введите никнейм...",
                                    style: {
                                        width: "100%",
                                        background: "#15101b",
                                        border: "1px solid rgba(255,255,255,0.1)",
                                        borderRadius: "12px",
                                        padding: "12px 16px",
                                        color: "#fff",
                                        fontSize: "15px",
                                        outline: "none"
                                    }
                                })
                            ]
                        }),
                        f.jsxs("div", {
                            className: "theme-section",
                            style: {
                                marginTop: 10
                            },
                            children: [
                                f.jsx("div", {
                                    className: "theme-section-title",
                                    children: "Уникальный ID устройства"
                                }),
                                f.jsxs("div", {
                                    style: {
                                        display: "flex",
                                        alignItems: "center",
                                        background: "#15101b",
                                        borderRadius: "12px",
                                        padding: "12px 16px",
                                        border: "1px solid rgba(255,255,255,0.1)",
                                        justifyContent: "space-between"
                                    },
                                    children: [
                                        f.jsx("span", {
                                            style: {
                                                fontFamily: "monospace",
                                                fontSize: "16px",
                                                color: "#a773d1",
                                                fontWeight: "bold",
                                                letterSpacing: "1px"
                                            },
                                            children: deviceId
                                        }),
                                        f.jsx("button", {
                                            onClick: handleCopy,
                                            style: {
                                                background: copied ? "#2563eb" : "#382b46",
                                                border: "none",
                                                color: "#fff",
                                                borderRadius: "8px",
                                                padding: "6px 12px",
                                                fontSize: "12px",
                                                cursor: "pointer",
                                                transition: "background 0.2s"
                                            },
                                            children: copied ? "Скопировано!" : "Копировать"
                                        })
                                    ]
                                }),
                                f.jsx("div", {
                                    style: {
                                        fontSize: "11px",
                                        color: "var(--text-muted)",
                                        marginTop: "6px",
                                        lineHeight: "1.3"
                                    },
                                    children: "Этот идентификатор жестко привязан к вашему устройству. Передайте его контакту в Telegram, чтобы он мог отправить вам запрос на добавление."
                                })
                            ]
                        }),
                        f.jsxs("div", {
                            className: "theme-section",
                            style: { marginTop: 10 },
                            children: [
                                f.jsx("div", {
                                    className: "theme-section-title",
                                    children: "Шифрование сообщений"
                                }),
                                f.jsxs("div", {
                                    style: {
                                        display: "flex",
                                        alignItems: "center",
                                        justifyContent: "space-between",
                                        background: "#15101b",
                                        borderRadius: "12px",
                                        padding: "12px 16px",
                                        border: "1px solid rgba(255,255,255,0.1)"
                                    },
                                    children: [
                                        f.jsxs("div", {
                                            children: [
                                                f.jsx("div", {
                                                    style: { fontSize: "15px", color: "#fff", fontWeight: 500 },
                                                    children: encryptMessages ? "AES-GCM + RSA" : "Стандартный текст"
                                                }),
                                                f.jsx("div", {
                                                    style: { fontSize: "12px", color: "var(--text-muted)", marginTop: "2px" },
                                                    children: encryptMessages ? "Сообщения зашифрованы" : "Шифрование отключено"
                                                })
                                            ]
                                        }),
                                        f.jsx("div", {
                                            onClick: () => {
                                                const next = !encryptMessages;
                                                setEncryptMessages(next);
                                                localStorage.setItem("nan0gram_encrypt_messages", String(next));
                                            },
                                            style: {
                                                flexShrink: 0,
                                                width: "48px",
                                                height: "28px",
                                                borderRadius: "14px",
                                                background: encryptMessages ? "#a773d1" : "rgba(255,255,255,0.15)",
                                                position: "relative",
                                                cursor: "pointer",
                                                transition: "background 0.25s"
                                            },
                                            children: f.jsx("div", {
                                                style: {
                                                    position: "absolute",
                                                    top: "3px",
                                                    left: encryptMessages ? "23px" : "3px",
                                                    width: "22px",
                                                    height: "22px",
                                                    borderRadius: "50%",
                                                    background: "#fff",
                                                    transition: "left 0.25s",
                                                    boxShadow: "0 1px 4px rgba(0,0,0,0.35)"
                                                }
                                            })
                                        })
                                    ]
                                })
                            ]
                        })
                    ]
                })
            ]
        });
    }

    if(activeTab === "storage") {
        return f.jsxs("div", {
            className: "settings-panel",
            children: [
                f.jsxs("div", {
                    className: "settings-header",
                    children: [
                        f.jsx("button", {
                            className: "theme-back-btn",
                            onClick: () => setActiveTab("main"),
                            children: "←"
                        }),
                        f.jsx("span", {
                            style: {
                                fontWeight: 600,
                                fontSize: 16
                            },
                            children: "Память и данные"
                        }),
                        f.jsx("div", {
                            style: {
                                width: 72
                            }
                        })
                    ]
                }),
                f.jsxs("div", {
                    className: "theme-panel-body",
                    children: [
                        f.jsxs("div", {
                            className: "theme-section",
                            children: [
                                f.jsx("div", {
                                    className: "theme-section-title",
                                    children: "Управление кэшем"
                                }),
                                f.jsxs("button", {
                                    className: "settings-item",
                                    onClick: () => {
                                        if (confirm("Вы действительно хотите удалить все загруженные картинки и видео из кэша устройства?")) {
                                            clearMediaCache();
                                        }
                                    },
                                    children: [
                                        f.jsx("div", {
                                            className: "settings-icon",
                                            children: "🗑️"
                                        }),
                                        f.jsxs("div", {
                                            className: "settings-item-content",
                                            children: [
                                                f.jsx("div", {
                                                    className: "settings-item-title",
                                                    children: "Очистить кэш медиа"
                                                }),
                                                f.jsx("div", {
                                                    className: "settings-item-desc",
                                                    children: "Удалит все загруженные изображения, видео и голосовые сообщения."
                                                })
                                            ]
                                        })
                                    ]
                                }),
                                f.jsxs("button", {
                                    className: "settings-item",
                                    onClick: () => {
                                        if (confirm("ВНИМАНИЕ: Это действие безвозвратно удалит историю всех переписок, оставив только последние 100 сообщений в каждом чате! Продолжить?")) {
                                            clearAllHistoryLog();
                                        }
                                    },
                                    children: [
                                        f.jsx("div", {
                                            className: "settings-icon",
                                            children: "💬"
                                        }),
                                        f.jsxs("div", {
                                            className: "settings-item-content",
                                            children: [
                                                f.jsx("div", {
                                                    className: "settings-item-title",
                                                    children: "Очистить кэш переписок"
                                                }),
                                                f.jsx("div", {
                                                    className: "settings-item-desc",
                                                    children: "Удалит старую историю сообщений, сохранив до 100 СМС в каждом чате."
                                                })
                                            ]
                                        })
                                    ]
                                }),
                                f.jsxs("button", {
                                    className: "settings-item",
                                    onClick: () => {
                                        if (confirm("ВНИЯНИЕ: Вы действительно хотите БЕЗВОЗВРАТНО удалить абсолютно все сообщения изо всех чатов?")) {
                                            clearAllHistoryLog();
                                            localStorage.removeItem("nan0gram_pinned_messages");
                                        }
                                    },
                                    children: [
                                        f.jsx("div", {
                                            className: "settings-icon",
                                            children: "🗑️"
                                        }),
                                        f.jsxs("div", {
                                            className: "settings-item-content",
                                            children: [
                                                f.jsx("div", {
                                                    className: "settings-item-title",
                                                    children: "Полная очистка сообщений"
                                                }),
                                                f.jsx("div", {
                                                    className: "settings-item-desc",
                                                    children: "Безвозвратно сотрет всю историю переписки на устройстве."
                                                })
                                            ]
                                        })
                                    ]
                                })
                            ]
                        })
                    ]
                })
            ]
        });
    }

    return f.jsxs("div", {
        className: "settings-panel",
        children: [
            f.jsxs("div", {
                className: "settings-header",
                children: [
                    f.jsx("button", {
                        className: "theme-back-btn",
                        onClick: onClose,
                        children: "←"
                    }),
                    f.jsx("span", {
                        style: {
                            fontWeight: 600,
                            fontSize: 16
                        },
                        children: "⚙️ Настройки ⚙️"
                    }),
                    f.jsx("div", {
                        style: {
                            width: 72
                        }
                    })
                ]
            }),
            f.jsxs("div", {
                className: "settings-list",
                children: [
                    f.jsxs("button", {
                        className: "settings-item",
                        onClick: () => setActiveTab("appearance"),
                        children: [
                            f.jsx("div", {
                                className: "settings-icon",
                                children: "🎨"
                            }),
                            f.jsxs("div", {
                                className: "settings-item-content",
                                children: [
                                    f.jsx("div", {
                                        className: "settings-item-title",
                                        children: "Оформление"
                                    }),
                                    f.jsx("div", {
                                        className: "settings-item-desc",
                                        children: "Цвет и прозрачность пузырей"
                                    })
                                ]
                            }),
                            f.jsx("div", {
                                className: "settings-arrow",
                                children: "›"
                            })
                        ]
                    }),
                    f.jsxs("button", {
                        className: "settings-item settings-item--disabled",
                        children: [
                            f.jsx("div", {
                                className: "settings-icon",
                                children: "🔔"
                            }),
                            f.jsxs("div", {
                                className: "settings-item-content",
                                children: [
                                    f.jsx("div", {
                                        className: "settings-item-title",
                                        children: "Уведомления"
                                    }),
                                    f.jsx("div", {
                                        className: "settings-item-desc",
                                        children: "Скоро..."
                                    })
                                ]
                            }),
                            f.jsx("div", {
                                className: "settings-arrow",
                                children: "›"
                            })
                        ]
                    }),
                    f.jsxs("button", {
                        className: "settings-item",
                        onClick: () => setActiveTab("privacy"),
                        children: [
                            f.jsx("div", {
                                className: "settings-icon",
                                children: "🔒"
                            }),
                            f.jsxs("div", {
                                className: "settings-item-content",
                                children: [
                                    f.jsx("div", {
                                        className: "settings-item-title",
                                        children: "Конфиденциальность"
                                    }),
                                    f.jsx("div", {
                                        className: "settings-item-desc",
                                        children: "Управление профилем, никнеймом и вашим ID"
                                    })
                                ]
                            }),
                            f.jsx("div", {
                                className: "settings-arrow",
                                children: "›"
                            })
                        ]
                    }),
                    f.jsxs("button", {
                        className: "settings-item",
                        onClick: () => setActiveTab("storage"),
                        children: [
                            f.jsx("div", {
                                className: "settings-icon",
                                children: "💾"
                            }),
                            f.jsxs("div", {
                                className: "settings-item-content",
                                children: [
                                    f.jsx("div", {
                                        className: "settings-item-title",
                                        children: "Память и данные"
                                    }),
                                    f.jsx("div", {
                                        className: "settings-item-desc",
                                        children: "Очистка медиафайлов и базы данных переписок"
                                    })
                                ]
                            }),
                            f.jsx("div", {
                                className: "settings-arrow",
                                children: "›"
                            })
                        ]
                    })
                ]
            })
        ]
    });
}