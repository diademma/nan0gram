const Z0 = "https://ui-avatars.com/api/?name=%D0%AF&background=2f65e0&color=fff&size=128",
    qu = [{
        id: "chat_1",
        name: "Кай",
        username: "@ka1jus",
        userId: "109823746",
        status: "150 участников",
        avatar: "https://images.unsplash.com/photo-1555854877-bab0e564b8d5?ixlib=rb-4.0.3&auto=format&fit=crop&w=500&q=60",
        preview: "",
        unread: 0
    }, {
        id: "chat_2",
        name: "Алиса",
        username: "@alisa_wonder",
        userId: "543812993",
        status: "в сети",
        avatar: "https://images.unsplash.com/photo-1494790108377-be9c29b29330?ixlib=rb-4.0.3&auto=format&fit=crop&w=500&q=60",
        preview: "",
        unread: 0
    }, {
        id: "chat_3",
        name: "Работа",
        username: "@work_chat",
        userId: "88339211",
        status: "12 участников",
        avatar: "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?ixlib=rb-4.0.3&auto=format&fit=crop&w=500&q=60",
        preview: "",
        unread: 0
    }],
    L0 = ["👍", "❤️", "🔥", "🥰", "👏", "😁", "🤔", "🤯"],
    K0 = ["🙊", "❤️", "👍", "👎", "🔥", "🥰", "👏", "😁", "🤔", "🤯", "😱", "🤬", "😢", "🎉", "🤩", "🤮", "💩", "🙏", "👌", "🕊️", "🤡", "🥱", "🥴", "😍", "🐳", "❤️‍🔥", "🌚", "🌭", "💯", "🤣", "⚡", "🍌", "🏆", "💔", "🤨", "😐", "🍓", "🍾", "💋", "🖕", "😈", "😴", "😭", "🤓", "👻", "👨‍💻", "👀", "🎃", "😇", "😨", "🤝", "✍️", "🤗", "🫡", "🎅", "🎄", "☃️", "💅", "🤪", "🗿", "🆒", "💘", "🙉", "🦄", "😘", "💊", "😎", "👾", "🤷‍♂️", "🤷", "🤷‍♀️", "😡"],
    w0 = {
        schemeId: "custom",
        inRgb: "65,35,80",
        outRgb: "65,35,80",
        opacity: .88,
        customHue: 260
    };

const hslToRgb = (h, s, l) => {
    let r, g, b;
    if(s === 0) {
        r = g = b = l;
    } else {
        const hue2rgb = (p, q, t) => {
            if(t < 0) t += 1;
            if(t > 1) t -= 1;
            if(t < 1 / 6) return p + (q - p) * 6 * t;
            if(t < 1 / 2) return q;
            if(t < 2 / 3) return p + (q - p) * (2 / 3 - t) * 6;
            return p;
        };
        const q = l < 0.5 ? l * (1 + s) : l + s - l * s;
        const p = 2 * l - q;
        r = hue2rgb(p, q, h + 1 / 3);
        g = hue2rgb(p, q, h);
        b = hue2rgb(p, q, h - 1 / 3);
    }
    return [Math.round(r * 255), Math.round(g * 255), Math.round(b * 255)];
};

function J0({
    contactName: m,
    theme: G,
    onSave: U,
    onClose: o
}) {
    const [_, O] = T.useState(G);
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
        O(prev => ({
            ...prev,
            schemeId: "custom",
            outRgb: outRgbStr,
            inRgb: outRgbStr,
            customHue: val
        }));
    }, []);
    const el = T.useCallback(() => {
        U(_), o()
    }, [_, U, o]), M = {
        in: `rgba(${_.inRgb},${_.opacity})`,
        out: `rgba(${_.outRgb},${_.opacity})`
    };
    return f.jsxs("div", {
        className: "theme-panel",
        children: [f.jsxs("div", {
            className: "theme-panel-header",
            children: [f.jsx("button", {
                className: "theme-back-btn",
                onClick: o,
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
                    children: m
                })]
            }), f.jsx("button", {
                className: "theme-save-btn",
                onClick: el,
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
                            background: M.in
                        },
                        children: ["Привет! Как дела? 😊", f.jsx("span", {
                            className: "theme-preview-time",
                            children: "12:34"
                        })]
                    }), f.jsxs("div", {
                        className: "theme-preview-msg out",
                        style: {
                            background: M.out
                        },
                        children: ["Всё отлично, спасибо! 🔥", f.jsx("span", {
                            className: "theme-preview-time",
                            children: "12:35 ✓✓"
                        })]
                    }), f.jsxs("div", {
                        className: "theme-preview-msg in",
                        style: {
                            background: M.in
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
                        value: Math.round(_.opacity * 100),
                        onChange: S => O(j => ({
                            ...j,
                            opacity: Number(S.target.value) / 100
                        })),
                        className: "opacity-slider"
                    }), f.jsxs("span", {
                        className: "opacity-value",
                        children: [Math.round(_.opacity * 100), "%"]
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
                                background: `rgb(${_.outRgb})`,
                                border: "2px solid rgba(255,255,255,0.8)",
                                boxShadow: "0 2px 8px rgba(0,0,0,0.5)"
                            }
                        })]
                    }), f.jsx("input", {
                        type: "range",
                        min: 0,
                        max: 380,
                        value: _.customHue !== undefined ? _.customHue : 260,
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
    })
}

function k0({
    defaultTheme: m,
    onSave: G,
    onClose: U
}) {
    const [o, _] = T.useState("main");
    const [O, K] = T.useState(m);
    const [sidebarBlur, setSidebarBlur] = T.useState(() => Number(localStorage.getItem("nan0gram_sidebar_blur") || "20"));
    const [sidebarDarkness, setSidebarDarkness] = T.useState(() => Number(localStorage.getItem("nan0gram_sidebar_darkness") || "50"));
    // Загружаем имя из localStorage (по умолчанию "Я")
    const [username, setUsername] = T.useState(() => localStorage.getItem("nan0gram_username") || "Я");
    const [copied, setCopied] = T.useState(false);

    const deviceId = window.nan0gram ? window.nan0gram.getDeviceId() : "4f0Q67gPe86N";

    // Функция копирования ID в буфер обмена с визуальной анимацией
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

    // Сохраняем имя в локальную память планшета
    const handleSaveName = () => {
        localStorage.setItem("nan0gram_username", username);
        window.dispatchEvent(new CustomEvent('nan0gram:username-change', {
            detail: username
        }));
    };

    if(o === "appearance") {
        const el = {
            in: `rgba(${O.inRgb},${O.opacity})`,
            out: `rgba(${O.outRgb},${O.opacity})`
        };
        const hslToRgb = (h, s, l) => {
            let r, g, b;
            if(s === 0) {
                r = g = b = l;
            } else {
                const hue2rgb = (p, q, t) => {
                    if(t < 0) t += 1;
                    if(t > 1) t -= 1;
                    if(t < 1 / 6) return p + (q - p) * 6 * t;
                    if(t < 1 / 2) return q;
                    if(t < 2 / 3) return p + (q - p) * (2 / 3 - t) * 6;
                    return p;
                };
                const q = l < 0.5 ? l * (1 + s) : l + s - l * s;
                const p = 2 * l - q;
                r = hue2rgb(p, q, h + 1 / 3);
                g = hue2rgb(p, q, h);
                b = hue2rgb(p, q, h - 1 / 3);
            }
            return [Math.round(r * 255), Math.round(g * 255), Math.round(b * 255)];
        };
        const handleHueChange = (e) => {
            const val = Number(e.target.value);
            let r, g, b;
            if(val <= 0) {
                r = 240;
                g = 240;
                b = 240;
            } // Белый край
            else if(val >= 380) {
                r = 30;
                g = 30;
                b = 30;
            } // Черный край
            else {
                let h = (val - 10) / 360;
                if(h < 0) h = 0;
                if(h > 1) h = 1;
                const rgb = hslToRgb(h, 0.75, 0.45);
                r = rgb[0];
                g = rgb[1];
                b = rgb[2];
            }
            const outRgbStr = `${r},${g},${b}`;
            K(prev => ({
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
                            onClick: () => _("main"),
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
                                G(O);
                                localStorage.setItem("nan0gram_sidebar_blur", sidebarBlur);
                                localStorage.setItem("nan0gram_sidebar_darkness", sidebarDarkness);
                                document.documentElement.style.setProperty("--sidebar-blur", sidebarBlur + "px");
                                document.documentElement.style.setProperty("--sidebar-brightness", (100 - sidebarDarkness) / 100);
                                _("main")
                            },
                            children: "Применить"
                        })
                    ]
                }),
                f.jsxs("div", {
                    className: "theme-panel-body",
                    children: [
                        /* ПРЕДПРОСМОТР САЙДБАРА */
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
                        /* ПРЕДПРОСМОТР ЧАТА */
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
                                                background: el.in
                                            },
                                            children: ["Привет! Красивая тема 😊 ", f.jsx("span", {
                                                className: "theme-preview-time",
                                                children: "12:34"
                                            })]
                                        }),
                                        f.jsxs("div", {
                                            className: "theme-preview-msg out",
                                            style: {
                                                background: el.out
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
                                            onChange: M => setSidebarBlur(Number(M.target.value)),
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
                                            onChange: M => setSidebarDarkness(Number(M.target.value)),
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
                                                        background: `rgb(${O.outRgb})`,
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
                                            value: O.customHue !== undefined ? O.customHue : 260,
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
                                            value: Math.round(O.opacity * 100),
                                            onChange: M => K(S => ({
                                                ...S,
                                                opacity: Number(M.target.value) / 100
                                            })),
                                            className: "opacity-slider"
                                        }),
                                        f.jsxs("span", {
                                            className: "opacity-value",
                                            children: [Math.round(O.opacity * 100), "%"]
                                        })
                                    ]
                                })
                            ]
                        })
                    ]
                })
            ]
        });
    } // РАЗДЕЛ КОНФИДЕНЦИАЛЬНОСТИ (НАСТРОЙКИ ПРОФИЛЯ)
    if(o === "privacy") {
        return f.jsxs("div", {
            className: "settings-panel",
            children: [
                f.jsxs("div", {
                    className: "settings-header",
                    children: [
                        f.jsx("button", {
                            className: "theme-back-btn",
                            onClick: () => _("main"),
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
                                _("main");
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
                        })
                    ]
                })
            ]
        });
    }

    if(o === "storage") {
        return f.jsxs("div", {
            className: "settings-panel",
            children: [
                f.jsxs("div", {
                    className: "settings-header",
                    children: [
                        f.jsx("button", {
                            className: "theme-back-btn",
                            onClick: () => _("main"),
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
                                            if (window.Android && window.Android.clearMediaCache) {
                                                window.Android.clearMediaCache();
                                            } else {
                                                alert("Кэш медиа очищен (демо).");
                                            }
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
                                            if (window.Android && window.Android.clearAllHistoryLog) {
                                                window.Android.clearAllHistoryLog();
                                            } else {
                                                alert("История переписок очищена (демо).");
                                            }
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
                                        if (confirm("ВНИМАНИЕ: Вы действительно хотите БЕЗВОЗВРАТНО удалить абсолютно все сообщения изо всех чатов?")) {
                                            if (window.Android && window.Android.clearAllHistoryLog) {
                                                window.Android.clearAllHistoryLog();
                                                localStorage.removeItem("nan0gram_pinned_messages");
                                            } else {
                                                alert("Все сообщения удалены (демо).");
                                            }
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
                        onClick: U,
                        children: "←"
                    }),
                    f.jsx("span", {
                        style: {
                            fontWeight: 600,
                            fontSize: 16
                        },
                        children: "Настройки ⚙️"
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
                        onClick: () => _("appearance"),
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
                        onClick: () => _("privacy"),
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
                        onClick: () => _("storage"),
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

function $0({
    contacts: m,
    activeChatId: G,
    chatPreviews: U,
    chatThemes: o,
    defaultTheme: _,
    onSelectChat: O,
    onSaveTheme: K,
    onSaveDefaultTheme: el
}) {
    const [M, S] = T.useState(null), [j, I] = T.useState(!1), P = T.useRef(0), ml = T.useRef(0), nl = T.useRef(null), $ = T.useRef(!1), fl = T.useCallback(k => {
        const X = k.touches[0];
        P.current = X.clientX;
        ml.current = X.clientY;
        $.current = !1;
        const vl = document.elementFromPoint(X.clientX, X.clientY)?.closest("[data-chat-id]");
        nl.current = vl?.dataset.chatId ?? null
    }, []), Sl = T.useCallback(k => {
        Math.abs(k.touches[0].clientX - P.current) > 8 && ($.current = !0)
    }, []), Ml = T.useCallback(k => {
        if(k.target.tagName === "INPUT") return;
        const X = k.changedTouches[0];
        const dX = X.clientX - P.current;
        const dY = Math.abs(X.clientY - ml.current);
        if(j && dX < -60 && dY < Math.abs(dX) * 0.8) {
            I(!1);
            return
        }
        if(ml.current >= 60 && dX > 60 && dX > dY * 1.2) {
            if(nl.current) {
                S(nl.current)
            } else {
                I(!0)
            }
            return
        }
        if(!$.current || Math.abs(dX) < 10) {
            const al = document.elementFromPoint(X.clientX, X.clientY)?.closest("[data-chat-id]");
            al && al.dataset.chatId && O(al.dataset.chatId)
        }
    }, [O, j]), yl = M ? m.find(k => k.id === M) : null;
    return f.jsxs("div", {
        className: "sidebar",
        style: {
            overflow: "hidden",
            position: "relative",
            userSelect: "none"
        },
        onTouchStart: fl,
        onTouchMove: Sl,
        onTouchEnd: Ml,
        children: [f.jsxs("div", {
            className: "sidebar-header",
            children: [f.jsx("div", {
                className: "app-title",
                children: "nan0gram"
            }), f.jsx("button", {
                className: "sidebar-gear-btn",
                onPointerDown: k => k.stopPropagation(),
                onClick: k => {
                    k.stopPropagation(), I(!0)
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
                    children: [f.jsx("circle", {
                        cx: 12,
                        cy: 12,
                        r: 3
                    }), f.jsx("path", {
                        d: "M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"
                    })]
                })
            })]
        }), f.jsx("div", {
            className: "chat-list",
            style: {
                flex: 1
            },
            children: m.map(k => f.jsxs("div", {
                "data-chat-id": k.id,
                className: `chat-item${G===k.id?" active":""}`,
                children: [f.jsx("div", {
                    className: "avatar",
                    style: {
                        backgroundImage: `url('${k.avatar}')`
                    }
                }), f.jsxs("div", {
                    className: "chat-info",
                    children: [f.jsx("div", {
                        className: "chat-name",
                        children: k.name
                    }), f.jsx("div", {
                        className: "chat-preview",
                        children: U[k.id] ?? k.preview
                    })]
                }), k.unread > 0 && f.jsx("div", {
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
                })]
            }, k.id))
        }), f.jsx("div", {
            className: "theme-panel-overlay",
            style: {
                transform: j ? "translateX(0)" : "translateX(-100%)",
                pointerEvents: j ? "auto" : "none"
            },
            children: f.jsx(k0, {
                defaultTheme: _,
                onSave: el,
                onClose: () => I(!1)
            })
        }), f.jsx("div", {
            className: "theme-panel-overlay",
            style: {
                transform: M ? "translateX(0)" : "translateX(-100%)",
                pointerEvents: M ? "auto" : "none"
            },
            children: yl && M && f.jsx(J0, {
                contactName: yl.name,
                theme: o[M] ?? _,
                onSave: k => K(M, k),
                onClose: () => S(null)
            })
        })]
    })
}

function W0(m) {
    const G = {
        1: [1],
        2: [2],
        3: [3],
        4: [2, 2],
        5: [2, 3],
        6: [3, 3],
        7: [3, 4],
        8: [4, 4]
    };
    return m in G ? G[m] : [4, 4]
}

function F0(m) {
    const G = Math.floor(m / 60).toString().padStart(2, "0"),
        U = (m % 60).toString().padStart(2, "0");
    return `${G}:${U}`
}

function I0({
    message: m,
    selected: G,
    selectionMode: U,
    onLongPress: o,
    onTap: _,
    onDoubleTap: O,
    onSwipeLeft: K,
    onToggleSelect: el,
    onOpenProfile: M,
    onOpenLightbox: S,
    onToggleReaction: Yl
}) {
    const [j, I] = T.useState(0), P = T.useRef(0), ml = T.useRef(0), nl = T.useRef(!1), $ = T.useRef(!1), fl = T.useRef(null), Sl = T.useRef(!1), Ml = T.useRef(0), yl = T.useRef(null), k = T.useCallback(() => {
        fl.current && clearTimeout(fl.current), yl.current && clearTimeout(yl.current)
    }, []),    X = T.useCallback(rl => {
        if (rl.target.closest('.msg-reply')) return;
        const isVoice = rl.target.closest('.tg-voice-player');
        const ll = rl.touches[0];
        P.current = ll.clientX, ml.current = ll.clientY, nl.current = !1, $.current = !1, Sl.current = !1;
        if (!isVoice) {
            fl.current = setTimeout(() => {
                !$.current && !nl.current && (Sl.current = !0, navigator.vibrate && navigator.vibrate(50), o(m.id))
            }, 400)
        }
    }, [m.id, o]), _l = T.useCallback(rl => {
        if (rl.target.closest('.msg-reply')) return;
        if(Sl.current) return;
        const ll = rl.touches[0],
            p = P.current - ll.clientX,
            z = ml.current - ll.clientY,
            B = Math.abs(p),
            cl = Math.abs(z);
        if(!nl.current && !$.current) {
            if(cl > 10) {
                $.current = !0, fl.current && clearTimeout(fl.current);
                return
            }
            B > 8 && (nl.current = !0, fl.current && clearTimeout(fl.current))
        }
        nl.current && p > 0 && cl < 50 && (rl.cancelable && rl.preventDefault(), I(Math.min(p, 75)))
    }, []),    vl = T.useCallback(rl => {
        if (rl.target.closest('.msg-reply')) return;
        const isVoiceMsg = rl.target.closest('.tg-voice-player');
        if (window.nan0gram_clickCooldown) {
            k();
            return;
        }
        if(k(), Sl.current) {
            I(0);
            return
        }
        const ll = rl.changedTouches[0],
            p = P.current - ll.clientX;
        if(nl.current) {
            I(0), p > 40 && (navigator.vibrate && navigator.vibrate(30), K(m.id)), nl.current = !1;
            return
        }
        if(I(0), $.current) return;
        if(U) {
            el(m.id);
            return
        }
        if (isVoiceMsg) return;
        const z = Date.now();
        z - Ml.current < 280 ? (yl.current && clearTimeout(yl.current), Ml.current = 0, O(m.id)) : (Ml.current = z, yl.current = setTimeout(() => {
            _(m.id, {
                clientX: ll.clientX,
                clientY: ll.clientY
            })
        }, 280))
    }, [k, m.id, U, K, el, _, O]),    Y = T.useCallback(rl => {
        if (rl.target.closest('.msg-reply') || rl.target.closest('.tg-voice-player')) return;
        if (window.nan0gram_clickCooldown) return;
        rl.pointerType === "mouse" && (U ? el(m.id) : _(m.id, {
            clientX: rl.clientX,
            clientY: rl.clientY
        }))
    }, [m.id, U, el, _]), al = T.useCallback(rl => {
        rl.stopPropagation(), m.avatar && M({
            avatar: m.avatar,
            name: m.author,
            username: `@${m.author.toLowerCase().replace(/\s/g,"_")}`,
            userId: "???"
        })
    }, [m, M]), Cl = m.type === "in", xl = j > 0 ? Math.min(j / 60, 1) : 0, Tl = m.images ?? [];
    let Bl = [];
    if(Tl.length > 0) {
        const rl = W0(Tl.length);
        let ll = 0;
        Bl = rl.map(p => {
            const z = Tl.slice(ll, ll + p).map((B, cl) => ({
                src: B,
                idx: ll + cl
            }));
            return ll += p, z
        })
    }
    return f.jsxs("div", {
        className: `msg-row ${m.type}${G?" selected":""}`,
        "data-id": m.id,
        children: [f.jsx("div", {
            className: `msg-checkbox${U?" show":""}`
        }), Cl ? f.jsx("div", {
            className: "msg-avatar",
            style: {
                backgroundImage: m.avatar ? `url('${m.avatar}')` : "none",
                backgroundColor: m.avatar ? void 0 : "#a773d1"
            },
            onClick: al,
            onTouchEnd: rl => {
                nl.current || al(rl)
            }
        }) : f.jsx("div", {
            style: {
                width: 36,
                marginRight: 10,
                flexShrink: 0
            }
        }), f.jsxs("div", {
            className: "msg-content",
            style: {
                transform: j > 0 ? `translateX(-${j}px)` : void 0,
                transition: j === 0 ? "transform 0.2s cubic-bezier(0.1,0.7,0.1,1)" : "none"
            },
            onTouchStart: X,
            onTouchMove: _l,
            onTouchEnd: vl,
            onClick: Y,
            children: [f.jsxs("div", {
                className: "message",
                children: [Cl && f.jsx("div", {
                    className: "msg-author",
                    children: m.author
                }), m.replyTo && f.jsx("div", {
                    className: "msg-reply",
                    onTouchStart: e => e.stopPropagation(),
                    onTouchEnd: e => {
                        e.stopPropagation();
                        e.preventDefault();
                        if (m.replyTo && m.replyTo.id) {
                            const targetEl = document.querySelector("[data-id='" + m.replyTo.id + "']");
                            if (targetEl) {
                                targetEl.scrollIntoView({ behavior: "smooth", block: "center" });
                                targetEl.classList.add("flash-highlight");
                                setTimeout(() => targetEl.classList.remove("flash-highlight"), 1000);
                            }
                        }
                    },
                    onClick: e => {
                        e.stopPropagation();
                        if (m.replyTo && m.replyTo.id) {
                            const targetEl = document.querySelector("[data-id='" + m.replyTo.id + "']");
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
                        children: [f.jsx("div", {
                            style: {
                                fontSize: 13,
                                color: "var(--reply-line)",
                                fontWeight: 500
                            },
                            children: m.replyTo.author
                        }), f.jsx("div", {
                            style: {
                                fontSize: 13,
                                color: "#ccc",
                                whiteSpace: "nowrap",
                                overflow: "hidden",
                                textOverflow: "ellipsis"
                            },
                            children: m.replyTo.text
                        })]
                    })
                }), Bl.length > 0 && f.jsx("div", {
                    className: "msg-album-container",
                    children: Bl.map((rl, ll) => f.jsx("div", {
                        className: "album-row",
                        children: rl.map(({
                            src: p,
                            idx: z
                        }) => f.jsx("img", {
                            src: p,
                            alt: "",
                            className: "album-img",
                            onPointerDown: B => B.stopPropagation(),
                            onClick: B => {
                                B.stopPropagation(), k(), S(p, Tl, z)
                            }
                        }, z))
                    }, ll))
                }), m.audio && f.jsxs("div", {
                    className: "voice-msg",
                    children: [f.jsx("div", {
                        className: "voice-wave",
                        children: "🎵"
                    }), f.jsx("audio", {
                        controls: !0,
                        src: m.audio,
                        className: "voice-audio",
                        preload: "none"
                    }), m.audioDuration != null && f.jsx("span", {
                        className: "voice-duration",
                        children: F0(m.audioDuration)
                    })]
                }), m.video && f.jsxs("div", {
                    className: "video-thumb-wrapper",
                    onPointerDown: B => B.stopPropagation(),
                    onClick: rl => {
                        rl.stopPropagation(), k(), S(m.video, void 0, void 0, m.videoThumbnail)
                    },
                    children: [f.jsx("video", {
                        src: m.video,
                        poster: m.videoThumbnail,
                        className: "video-thumb",
                        preload: "metadata",
                        playsInline: !0,
                        muted: !0
                    }), f.jsx("div", {
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
                    })]
                }), m.file && f.jsxs("div", {
                    className: "file-msg",
                    onPointerDown: B => B.stopPropagation(),
                    onClick: rl => {
                        rl.stopPropagation()
                    },
                    children: [f.jsx("div", {
                        className: "file-icon",
                        children: "📄"
                    }), f.jsxs("div", {
                        className: "file-details",
                        children: [f.jsx("div", {
                            className: "file-name",
                            children: m.file.name
                        }), f.jsx("div", {
                            className: "file-size",
                            children: (m.file.size / (1024 * 1024)).toFixed(2) + " MB"
                        })]
                    })]
                }), m.text && f.jsx("div", {
                    style: {
                        userSelect: "text",
                        whiteSpace: "pre-wrap"
                    },
                    children: m.text
                }), f.jsxs("div", {
                    className: "msg-bottom-flow",
                    style: {
                        justifyContent: m.reaction ? "space-between" : "flex-end"
                    },
                    children: [m.reaction && f.jsx("div", {
                        className: "reactions-container",
                        children: f.jsxs("div", {
                            className: `reaction-pill ${Cl?"pill-in":"pill-out"}`,
                            onTouchStart: B => B.stopPropagation(),
                            onPointerDown: B => B.stopPropagation(),
                            onPointerUp: B => B.stopPropagation(),
                            onDoubleClick: B => B.stopPropagation(),
                            onClick: B => {
                                B.stopPropagation(), Yl && Yl(m.id, m.reaction)
                            },
                            onTouchEnd: B => {
                                B.cancelable && B.preventDefault(), B.stopPropagation(), Yl && Yl(m.id, m.reaction)
                            },
                            children: [f.jsx("span", {
                                children: m.reaction
                            }), f.jsx("div", {
                                className: "react-avatar",
                                style: {
                                    backgroundImage: `url('${Z0}')`
                                }
                            })]
                        })
                    }), f.jsxs("div", {
                        className: "msg-meta",
                        children: [m.edited && f.jsx("span", {
                            className: "edit-pencil",
                            children: "✎"
                        }), f.jsx("span", {
                            children: m.time
                        }), m.type === "out" && f.jsx("span", {
                            style: {
                                color: "#a773d1"
                            },
                            children: "✓✓"
                        })]
                    })]
                })]
            }), f.jsx("div", {
                className: "swipe-reply-icon",
                style: {
                    opacity: xl
                },
                children: "↩️"
            })]
        })]
    })
}

function P0(m, G) {
    const ca = document.querySelector('.chat-area');
    const cr = ca ? ca.getBoundingClientRect() : {
        left: 0,
        top: 0,
        width: window.innerWidth,
        height: window.innerHeight
    };
    const _ = 270,
        mh = 320,
        pad = 8;
    let el = (m - cr.left) - _ / 2;
    el = Math.max(pad, Math.min(el, cr.width - _ - pad));
    let M = (G - cr.top) + 16;
    if(M + mh > cr.height - pad) M = (G - cr.top) - mh - 16;
    M = Math.max(pad, Math.min(M, cr.height - mh - pad));
    return {
        left: el,
        top: M
    }
}

function lv({
    message: m,
    position: G,
    onClose: U,
    onReply: o,
    onCopy: _,
    onPin: O,
    onEdit: K,
    onDelete: el,
    onReaction: M,
    onSaveMedia: S
}) {
    const j = T.useRef(null);
    if(!(m !== null && G !== null)) return f.jsxs(f.Fragment, {
        children: [f.jsx("div", {
            className: "sheet-backdrop",
            style: {
                display: "none"
            }
        }), f.jsx("div", {
            className: "tg-context-menu",
            style: {
                opacity: 0,
                pointerEvents: "none",
                position: "fixed",
                top: 0,
                left: 0
            }
        })]
    });
    const {
        left: P,
        top: ml
    } = P0(G.x, G.y), nl = !!(m?.images?.length || m?.audio || m?.video || m?.file);
    return f.jsxs(f.Fragment, {
        children: [f.jsx("div", {
            className: "sheet-backdrop",
            style: {
                display: "block",
                opacity: 1
            },
            onClick: U
        }), f.jsxs("div", {
            ref: j,
            className: "tg-context-menu open",
            style: {
                position: "fixed",
                left: P,
                top: ml,
                maxWidth: "calc(100vw - 24px)"
            },
            children: [f.jsxs("div", {
                className: "reaction-panel",
                children: [f.jsx("div", {
                    className: "reaction-scroll",
                    children: L0.map($ => f.jsx("div", {
                        className: "reaction-btn",
                        onClick: () => {
                            M($), U()
                        },
                        children: $
                    }, $))
                }), f.jsx("div", {
                    className: "reaction-expand-btn",
                    id: "expandReactionsBtn",
                    onClick: () => {
                        const $ = j.current?.querySelector(".all-reactions-grid"),
                            fl = j.current?.querySelector(".context-action-list"),
                            Sl = j.current?.querySelector("#expandReactionsBtn");
                        if(!$ || !fl || !Sl) return;
                        const Ml = $.style.display === "grid";
                        $.style.display = Ml ? "none" : "grid", fl.style.display = Ml ? "flex" : "none", Sl.style.transform = Ml ? "rotate(0deg)" : "rotate(180deg)"
                    },
                    children: "▼"
                })]
            }), f.jsx("div", {
                className: "all-reactions-grid",
                style: {
                    display: "none"
                },
                children: K0.map($ => f.jsx("div", {
                    className: "reaction-btn",
                    onClick: () => {
                        M($), U()
                    },
                    children: $
                }, $))
            }), f.jsxs("div", {
                className: "context-action-list",
                style: {
                    display: "flex"
                },
                children: [f.jsxs("div", {
                    className: "context-action",
                    onClick: () => {
                        o(), U()
                    },
                    children: [f.jsx("span", {
                        className: "icon",
                        children: "↩️"
                    }), " Ответить"]
                }), m.text && f.jsxs("div", {
                    className: "context-action",
                    onClick: () => {
                        _(), U()
                    },
                    children: [f.jsx("span", {
                        className: "icon",
                        children: "📋"
                    }), " Копировать"]
                }), nl && f.jsxs("div", {
                    className: "context-action",
                    onClick: () => {
                        S?.(), U()
                    },
                    children: [f.jsx("span", {
                        className: "icon",
                        children: "⬇️"
                    }), " Сохранить медиа"]
                }), f.jsxs("div", {
                    className: "context-action",
                    onClick: U,
                    children: [f.jsx("span", {
                        className: "icon",
                        children: "➡️"
                    }), " Переслать"]
                }), f.jsxs("div", {
                    className: "context-action",
                    onClick: () => {
                        O(), U()
                    },
                    children: [f.jsx("span", {
                        className: "icon",
                        children: "📌"
                    }), " Закрепить"]
                }), m.type === "out" && f.jsxs("div", {
                    className: "context-action",
                    onClick: () => {
                        K(), U()
                    },
                    children: [f.jsx("span", {
                        className: "icon",
                        children: "✏️"
                    }), " Изменить"]
                }), f.jsxs("div", {
                    className: "context-action",
                    style: {
                        color: "#ff595a"
                    },
                    onClick: () => {
                        el(), U()
                    },
                    children: [f.jsx("span", {
                        className: "icon",
                        children: "🗑️"
                    }), " Удалить"]
                })]
            })]
        })]
    })
}

function tv({
    chatTitle: m,
    chatAvatar: G,
    chatStatus: U,
    messages: o,
    isMobile: _,
    theme: O,
    onBack: K,
    onHeaderAvatarClick: el,
    onOpenProfile: M,
    onOpenLightbox: S,
    onSendMessage: j,
    onSendImages: I,
    onSendAudio: P,
    onSendVideo: ml,
    onEditMessage: nl,
    onDeleteMessage: $,
    onPinMessage: fl,
    onToggleReaction: Sl,
    onSwipeClose: Ml,
    onLoadMore: onLoadMore,
    wallpaper: yl,
    pinnedMessage: initialPinned
}) {
    const k = T.useRef(null),
        messagesCountRef = T.useRef(o.length),
        X = T.useRef(null),
        _l = T.useRef(null),
        vl = T.useRef(null),
        Y = T.useRef(null),
        [al, Cl] = T.useState(null),
        [xl, Tl] = T.useState(null),
        [Bl, rl] = T.useState(""),
        [ll, p] = T.useState(!1),
        [z, B] = T.useState(new Set),
        [cl, r] = T.useState(null),
        [x, H] = T.useState(null),
        [C, Z] = T.useState(null),            [tl, R] = T.useState(!1),
            [showScrollDown, setShowScrollDown] = T.useState(!1),
            F = T.useRef(null),
        V = T.useRef([]),
        bl = T.useRef(0),
        rt = T.useRef(0),
        Ut = T.useRef(0),                ae = T.useRef(!1),
                Gt = T.useRef(0),
                isRecReq = T.useRef(false),
                Ya = T.useCallback(D => {
            const touch = D.touches[0];
            rt.current = touch.clientX;
            Ut.current = touch.clientY;
            ae.current = touch.clientY >= 60 && touch.clientY <= (window.innerHeight - 80)
        }, []),
        tc = T.useCallback(D => {}, []),
        ec = T.useCallback(D => {
            if(!_ || !ae.current) return;
            const touch = D.changedTouches[0];
            const dX = touch.clientX - rt.current;
            const dY = Math.abs(touch.clientY - Ut.current);
            if(dX > 55 && dX > dY * 1.2) {
                Ml()
            }
        }, [_, Ml]);
    T.useEffect(() => {
        const handleLoad = (e) => {
            (e.target.tagName === "IMG" || e.target.tagName === "VIDEO") && k.current && (k.current.scrollTop = k.current.scrollHeight)
        };
        const container = k.current;
        container && (container.addEventListener("load", handleLoad, true), container.addEventListener("loadeddata", handleLoad, true));
        k.current && o.length > messagesCountRef.current && (k.current.scrollTop = k.current.scrollHeight);
        messagesCountRef.current = o.length;
        return () => {
            container && (container.removeEventListener("load", handleLoad, true), container.removeEventListener("loadeddata", handleLoad, true))
        }
    }, [o]);

    // Синхронизируем стейт закрепа с персистентным пропом при смене чата
    T.useEffect(() => {
        r(initialPinned || null);
    }, [initialPinned]);

    const Gl = T.useCallback(() => {
            Cl(null), Tl(null), rl(""), X.current && (X.current.style.height = "auto")
        }, []),
        qa = T.useCallback(() => {
            const D = Bl.trim();
            if(D)
                if(xl !== null) nl(xl, D), Gl();
                else {
                    for(let sl = 0; sl < D.length; sl += 4e3) j(D.slice(sl, sl + 4e3), sl === 0 ? al ?? void 0 : void 0);
                    Gl()
                }
        }, [Bl, xl, al, j, nl, Gl]),
        Xu = T.useCallback(D => {}, []),
        Bu = T.useCallback(D => {
            rl(D.target.value);
            const w = D.target;
            w.style.height = "auto", w.style.height = Math.min(w.scrollHeight, 100) + "px"
        }, []),
        we = T.useCallback(D => {
            const w = Array.from(D.target.files ?? []).slice(0, 8);
            w.length && (I(w, al ?? void 0), Gl()), D.target.value = ""
        }, [al, I, Gl]),
        ac = T.useCallback(D => {
            const w = D.target.files?.[0];
            if(!w) return;
            if(w.size > 17 * 1024 * 1024) {
                alert("Видео больше 17 МБ"), D.target.value = "";
                return
            }
            const sl = new FileReader;
            sl.onload = Hl => {
                ml(Hl.target.result, al ?? void 0), Gl()
            }, sl.readAsDataURL(w), D.target.value = ""
        }, [al, ml, Gl]),
        Gu = T.useCallback(D => {
            const file = D.target.files?.[0];
            if(!file) return;
            const reader = new FileReader();
            reader.onload = e => {
                const img = new Image();
                img.onload = () => {
                    const canvas = document.createElement("canvas");
                    const maxDim = 1440;
                    let width = img.width,
                        height = img.height;
                    if(width > maxDim || height > maxDim) {
                        if(width > height) {
                            height = Math.round((height * maxDim) / width);
                            width = maxDim
                        } else {
                            width = Math.round((width * maxDim) / height);
                            height = maxDim
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
                        }))
                    } catch (err) {
                        console.error("Failed to save wallpaper:", err)
                    }
                };
                img.src = e.target.result
            };
            reader.readAsDataURL(file);
            D.target.value = ""
        }, []),
        uc = T.useCallback(async () => {
            isRecReq.current = true;
            try {
                const D = await navigator.mediaDevices.getUserMedia({
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
                    D.getTracks().forEach(ue => ue.stop());
                    return;
                }
                const w = MediaRecorder.isTypeSupported("audio/webm;codecs=opus") ? "audio/webm;codecs=opus" : "audio/webm",
                    sl = new MediaRecorder(D, {
                        mimeType: w,
                        audioBitsPerSecond: 128000
                    });
                V.current = [], sl.ondataavailable = Hl => {
                    Hl.data.size > 0 && V.current.push(Hl.data)
                }, sl.onstop = () => {
                    const Hl = Math.round((Date.now() - bl.current) / 1e3),
                        ht = new Blob(V.current, {
                            type: w
                        });
                    if(ht.size < 100) {
                        D.getTracks().forEach(ue => ue.stop());
                        return
                    }
                    const We = new FileReader;
                    We.onload = ue => {
                        P(ue.target.result, Hl, al ?? void 0), Gl()
                    }, We.readAsDataURL(ht), D.getTracks().forEach(ue => ue.stop())
                }, sl.start(), F.current = sl, bl.current = Date.now(), R(!0), navigator.vibrate && navigator.vibrate(30)
            } catch {
                alert("Нет доступа к микрофону")
            }
        }, [al, P, Gl]),
        Xa = T.useCallback(() => {
            isRecReq.current = false;
            F.current && F.current.state !== "inactive" && (F.current.stop(), F.current = null), R(!1)
        }, []),
        dt = T.useCallback(() => {
            p(!1), B(new Set)
        }, []),
        at = T.useCallback(D => {
            ll || p(!0), B(w => {
                const sl = new Set(w);
                return sl.add(D), sl
            })
        }, [ll]),
        Ct = T.useCallback(D => {
            B(w => {
                const sl = new Set(w);
                return sl.has(D) ? sl.delete(D) : sl.add(D), sl
            })
        }, []);
    T.useEffect(() => {
        ll && z.size === 0 && dt()
    }, [z, ll, dt]);
    const ut = T.useCallback(() => {
            z.forEach(D => $(D)), dt()
        }, [z, $, dt]),
        nc = T.useCallback(() => {
            const D = o.filter(w => z.has(w.id) && w.text).map(w => w.text).join(`

`);
            if(D) {
                const w = document.createElement("textarea");
                w.value = D, w.style.cssText = "position:fixed;opacity:0", document.body.appendChild(w), w.select();
                try {
                    document.execCommand("copy")
                } catch {}
                document.body.removeChild(w)
            }
            dt()
        }, [z, o, dt]),
        cc = T.useCallback((D, w) => {
            const sl = o.find(Hl => Hl.id === D);
            sl && (H(sl), Z({
                x: w.clientX,
                y: w.clientY
            }))
        }, [o]),
        ic = T.useCallback(D => {
            Sl(D, "❤️")
        }, [Sl]),    Je = T.useCallback(() => {
        x && (Cl({
            id: x.id,
            author: x.author || "Я",
                text: x.text || "Медиа"
            }), Tl(null), X.current?.focus())
        }, [x]),
        ke = T.useCallback(() => {
            if(!x?.text) return;
            const D = document.createElement("textarea");
            D.value = x.text, D.style.cssText = "position:fixed;opacity:0", document.body.appendChild(D), D.select();
            try {
                document.execCommand("copy")
            } catch {}
            document.body.removeChild(D)
        }, [x]),
        Qt = T.useCallback(() => {
            x && (fl(x), r(x))
        }, [x, fl, r]),
        $e = T.useCallback(() => {
            x && (Tl(x.id), Cl(null), rl(x.text || ""), setTimeout(() => {
                X.current && (X.current.style.height = "auto", X.current.style.height = Math.min(X.current.scrollHeight, 100) + "px", X.current.focus())
            }, 50))
        }, [x]),    Re = T.useCallback(D => {
        const w = o.find(sl => sl.id === D);
        if (w) {
            const textDesc = w.text || (w.images?.length > 1 ? "Альбом" : w.images ? "Фото" : w.audio ? "Голосовое сообщение" : w.video ? "Видео" : w.file ? "Файл" : "Сообщение");
            Cl({
                id: w.id,
                author: w.author || "Я",
                text: textDesc
            });
            Tl(null);
            X.current?.focus();
        }
    }, [o]),
        fc = T.useCallback(() => {
            if (cl) {
                const targetId = cl.id || cl;
                const targetEl = k.current?.querySelector(`[data-id="${targetId}"]`);
                if (targetEl) {
                    targetEl.scrollIntoView({ behavior: "smooth", block: "center" });
                    targetEl.classList.add("flash-highlight");
                    setTimeout(() => targetEl.classList.remove("flash-highlight"), 1000);
                }
            }
        }, [cl]),
        Qu = T.useCallback(() => {
            if(!x) return;
            const D = [];
            x.images && D.push(...x.images);
            x.audio && D.push(x.audio);
            x.video && D.push(x.video);
            D.forEach((w, sl) => {
                const ext = w.startsWith("data:video") ? "mp4" : w.startsWith("data:audio") ? "webm" : "jpg";
                const suggestedName = `media_${Date.now()}_${sl+1}.${ext}`;
                if (window.Android && typeof window.Android.saveMediaToDownloads === "function") {
                    window.Android.saveMediaToDownloads(w, suggestedName);
                } else {
                    const Hl = document.createElement("a");
                    Hl.href = w;
                    Hl.download = suggestedName;
                    Hl.style.cssText = "position:fixed;opacity:0";
                    document.body.appendChild(Hl);
                    Hl.click();
                    document.body.removeChild(Hl);
                }
            })
        }, [x]),
        Ba = Bl.trim().length > 0,
        Ga = {
            "--msg-in": `rgba(${O.inRgb},${O.opacity})`,
            "--msg-out": `rgba(${O.outRgb},${O.opacity})`
        };
    return f.jsxs("div", {
        className: `chat-area-inner${ll?" selection-mode":""}`,
        style: Ga,
        onTouchStart: Ya,
        onTouchMove: tc,
        onTouchEnd: ec,
        children: [f.jsx("div", {
            className: "chat-bg",
            style: {
                backgroundImage: yl ? `url('${yl}')` : "url('https://images.unsplash.com/photo-1534447677768-be436bb09401?q=80&w=1000&auto=format&fit=crop')"
            }
        }), !ll && f.jsxs("div", {
            className: "chat-header",
            children: [_ && f.jsx("button", {
                className: "back-btn",
                onClick: K,
                children: "←"
            }), f.jsx("div", {
                className: "header-avatar",
                style: {
                    backgroundImage: `url('${G}')`
                },
                onClick: el
            }), f.jsxs("div", {
                className: "header-info",
                onClick: el,
                children: [f.jsx("div", {
                    className: "header-title",
                    children: m
                }), f.jsx("div", {
                    className: "header-status",
                    children: U
                })]
            }), f.jsxs("div", {
                className: "header-actions",
                children: [f.jsx("span", {
                    title: "Сменить обои",
                    onClick: () => {
                        if (window.Android && window.Android.setWallpaperPending) {
                            window.Android.setWallpaperPending(true);
                        }
                        Y.current?.click();
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
                        children: [f.jsx("rect", {
                            x: 3,
                            y: 3,
                            width: 18,
                            height: 18,
                            rx: 2,
                            ry: 2
                        }), f.jsx("circle", {
                            cx: 8.5,
                            cy: 8.5,
                            r: 1.5
                        }), f.jsx("polyline", {
                            points: "21 15 16 10 5 21"
                        })]
                    })
                }), f.jsx("input", {
                    ref: Y,
                    type: "file",
                    accept: "image/*",
                    style: {
                        display: "none"
                    },
                    onChange: Gu
                })]
            })]
        }), ll && f.jsxs("div", {
            className: "selection-header",
            style: {
                display: "flex"
            },
            children: [f.jsxs("div", {
                style: {
                    display: "flex",
                    alignItems: "center"
                },
                children: [f.jsx("span", {
                    style: {
                        fontSize: 24,
                        cursor: "pointer",
                        paddingRight: 15
                    },
                    onClick: dt,
                    children: "✕"
                }), f.jsx("span", {
                    className: "selection-count",
                    children: z.size
                })]
            }), f.jsxs("div", {
                className: "selection-actions",
                children: [f.jsx("span", {
                    onClick: nc,
                    children: "📋"
                }), f.jsx("span", {
                    onClick: ut,
                    children: "🗑️"
                })]
            })]
        }), cl && f.jsxs("div", {
            className: "pinned-bar",
            style: {
                display: "flex"
            },
            onClick: fc,
            children: [f.jsxs("div", {
                className: "pin-content",
                children: [f.jsx("div", {
                    className: "pin-title",
                    children: "Закреплённое сообщение"
                }), f.jsx("div", {
                    className: "pin-text",
                    children: cl.text || (cl.images?.length ? "📷 Фото" : cl.audio ? "🎵 Голосовое" : cl.video ? "🎬 Видео" : cl.file ? "📄 Файл" : "Сообщение")
                })]
            }), f.jsx("div", {
                className: "pin-close",
                onClick: D => {
                    D.stopPropagation(), r(null), fl(null)
                },
                children: "✕"
            })]
        }), f.jsx("div", {
            ref: k,
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
            children: o.map(D => f.jsx(I0, {
                message: D,
                selected: z.has(D.id),
                selectionMode: ll,
                onLongPress: at,
                onTap: cc,
                onDoubleTap: ic,
                onSwipeLeft: Re,
                onToggleSelect: Ct,
                onOpenProfile: M,
                onOpenLightbox: S,
                onToggleReaction: Sl
            }, D.id))
        }), showScrollDown && f.jsx("button", {
            className: "scroll-down-btn",
            onClick: () => {
                k.current && k.current.scrollTo({
                    top: k.current.scrollHeight,
                    behavior: "smooth"
                })
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
        }), f.jsxs("div", {
            className: "input-wrapper",
            children: [(al || xl !== null) && f.jsxs("div", {
                id: "activeReplyPreview",
                style: {
                    display: "flex"
                },
                children: [f.jsxs("div", {
                    className: "reply-preview-left",
                    children: [f.jsx("div", {
                        className: "reply-preview-author",
                        style: {
                            color: "var(--accent-blue)"
                        },
                        children: xl !== null ? "Редактирование" : `Ответ ${al?.author}`
                    }), f.jsx("div", {
                        className: "reply-preview-text",
                        style: {
                            color: "var(--text-muted)"
                        },
                        children: xl !== null ? o.find(D => D.id === xl)?.text || "Медиа" : al?.text
                    })]
                }), f.jsx("button", {
                    className: "cancel-reply",
                    onClick: Gl,
                    children: "✕"
                })]
            }), f.jsxs("div", {
                className: "input-area",
                children: [f.jsx("button", {
                    className: "input-icon",
                    "data-mode": "media",
                    onClick: () => _l.current?.click(),
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
                }), f.jsx("button", {
                    className: "input-icon",
                    "data-mode": "file",
                    onClick: () => vl.current?.click(),
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
                            f.jsx("path", { d: "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" }),
                            f.jsx("polyline", { points: "14 2 14 8 20 8" }),
                            f.jsx("line", { x1: "16", y1: "13", x2: "8", y2: "13" }),
                            f.jsx("line", { x1: "16", y1: "17", x2: "8", y2: "17" }),
                            f.jsx("polyline", { points: "10 9 9 9 8 9" })
                        ]
                    })
                }), f.jsx("input", {
                    ref: _l,
                    type: "file",
                    accept: "image/*,video/*",
                    multiple: !0,
                    style: {
                        display: "none"
                    },
                    onChange: we
                }), f.jsx("input", {
                    ref: vl,
                    type: "file",
                    accept: "*/*",
                    style: {
                        display: "none"
                    },
                    onChange: ac
                }), f.jsx("div", {
                    className: "msg-input-box",
                    children: f.jsx("textarea", {
                        ref: X,
                        className: "msg-input",
                        rows: 1,
                        placeholder: "Сообщение",
                        value: Bl,
                        onChange: Bu,
                        onKeyDown: Xu
                    })
                }), f.jsx("button", {
                    className: `send-mic-btn${tl?" recording":""}`,
                    onContextMenu: D => D.preventDefault(),
                    onPointerDown: D => {
                        D.preventDefault(), Ba ? qa() : uc()
                    },
                    onPointerUp: () => {
                        Xa()
                    },
                    onPointerLeave: () => {
                        Xa()
                    },
                    onPointerCancel: () => {
                        Xa()
                    },
                    children: Ba ? f.jsx("svg", {
                        width: 18,
                        height: 18,
                        viewBox: "0 0 24 24",
                        fill: "none",
                        stroke: "currentColor",
                        strokeWidth: 2,
                        strokeLinecap: "round",
                        strokeLinejoin: "round",
                        children: [f.jsx("line", {
                            x1: 22,
                            y1: 2,
                            x2: 11,
                            y2: 13
                        }), f.jsx("polygon", {
                            points: "22 2 15 22 11 13 2 9 22 2"
                        })]
                    }) : tl ? f.jsx("svg", {
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
                        children: [f.jsx("path", {
                            d: "M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"
                        }), f.jsx("path", {
                            d: "M19 10v1a7 7 0 0 1-14 0v-1"
                        }), f.jsx("line", {
                            x1: 12,
                            y1: 19,
                            x2: 12,
                            y2: 23
                        }), f.jsx("line", {
                            x1: 8,
                            y1: 23,
                            x2: 16,
                            y2: 23
                        })]
                    })
                })]
            })]
        }), f.jsx(lv, {
            message: x,
            position: C,
            onClose: () => {
                H(null), Z(null)
            },
            onReply: Je,
            onCopy: ke,
            onPin: Qt,
            onEdit: $e,
            onDelete: () => {
                x && $(x.id)
            },
            onReaction: D => {
                x && Sl(x.id, D)
            },
            onSaveMedia: Qu
        })]
    })
}

function ev({
    profile: m,
    onClose: G,
    onOpenLightbox: U
}) {
    const o = m !== null;
    return f.jsxs("div", {
        className: "profile-modal",
        style: {
            transform: o ? "translateX(0)" : "translateX(100%)",
            display: "flex"
        },
        children: [f.jsxs("div", {
            className: "profile-header",
            children: [f.jsx("span", {
                style: {
                    cursor: "pointer"
                },
                onClick: G,
                children: "←"
            }), f.jsx("span", {
                children: "⋮"
            })]
        }), m && f.jsxs(f.Fragment, {
            children: [f.jsxs("div", {
                className: "profile-center",
                children: [f.jsx("div", {
                    className: "profile-big-avatar",
                    style: {
                        backgroundImage: `url('${m.avatar}')`
                    },
                    onClick: () => U(m.avatar)
                }), f.jsx("div", {
                    className: "profile-name",
                    children: m.name
                }), f.jsx("div", {
                    className: "profile-status",
                    children: "был(а) на этой неделе"
                })]
            }), f.jsxs("div", {
                className: "profile-buttons",
                children: [f.jsxs("div", {
                    className: "prof-btn",
                    onClick: G,
                    children: [f.jsx("span", {
                        children: "💬"
                    }), "Чат"]
                }), f.jsxs("div", {
                    className: "prof-btn",
                    children: [f.jsx("span", {
                        children: "🔕"
                    }), "Звук"]
                })]
            }), f.jsxs("div", {
                className: "profile-info-card",
                children: [f.jsxs("div", {
                    className: "info-item",
                    children: [f.jsx("div", {
                        className: "info-text",
                        children: "Привет, я использую nan0gram!"
                    }), f.jsx("div", {
                        className: "info-label",
                        children: "О себе"
                    })]
                }), f.jsxs("div", {
                    className: "info-item",
                    children: [f.jsx("div", {
                        className: "info-text",
                        children: m.username
                    }), f.jsx("div", {
                        className: "info-label",
                        children: "Имя пользователя"
                    })]
                }), f.jsxs("div", {
                    className: "info-item",
                    style: {
                        marginTop: 10
                    },
                    children: [f.jsxs("div", {
                        className: "info-text",
                        style: {
                            fontSize: 14,
                            color: "#ccc"
                        },
                        children: ["ID: ", m.userId]
                    }), f.jsx("div", {
                        className: "info-label",
                        children: "ID профиля"
                    })]
                })]
            })]
        })]
    })
}

function av({
    items: m,
    initialIndex: G = 0,
    onClose: U
}) {
    const [o, _] = T.useState(G), [showDownloadChoice, setShowDownloadChoice] = T.useState(false), [O, K] = T.useState(1), [el, M] = T.useState({
        x: 0,
        y: 0
    }), S = T.useRef(null), j = T.useRef(null), I = T.useRef(0), P = T.useRef(0), ml = T.useRef(0), nl = T.useRef(!1), $ = m ? m[o] : null, fl = $?.isVideo ?? !1, Sl = (m?.length ?? 0) > 1;
    T.useEffect(() => {
        _(G)
    }, [G, m]), T.useEffect(() => {
        K(1), M({
            x: 0,
            y: 0
        })
    }, [o]);
    const Ml = T.useCallback(() => {
            m && _(Y => Math.min(Y + 1, m.length - 1))
        }, [m]),
        yl = T.useCallback(() => {
            _(Y => Math.max(Y - 1, 0))
        }, []),
        k = Y => Math.hypot(Y[0].clientX - Y[1].clientX, Y[0].clientY - Y[1].clientY),
        X = T.useCallback(Y => {
            Y.touches.length === 2 ? (S.current = k(Y.touches), Y.preventDefault()) : Y.touches.length === 1 && (P.current = Y.touches[0].clientX, ml.current = Y.touches[0].clientY, j.current = {
                x: Y.touches[0].clientX,
                y: Y.touches[0].clientY
            }, nl.current = !1)
        }, []),
        _l = T.useCallback(Y => {
            if(Y.touches.length === 2 && S.current !== null) {
                Y.preventDefault();
                const al = k(Y.touches),
                    Cl = al / S.current;
                S.current = al, K(xl => Math.max(1, Math.min(5, xl * Cl)))
            } else if(Y.touches.length === 1 && j.current) {
                const al = Y.touches[0].clientX - j.current.x,
                    Cl = Y.touches[0].clientY - j.current.y;
                j.current = {
                    x: Y.touches[0].clientX,
                    y: Y.touches[0].clientY
                }, O > 1 && (Y.preventDefault(), nl.current = !0, M(xl => ({
                    x: xl.x + al,
                    y: xl.y + Cl
                })))
            }
        }, [O]),
        vl = T.useCallback(Y => {
            if(S.current = null, Y.changedTouches.length === 1) {
                const al = Y.changedTouches[0].clientX,
                    Cl = Y.changedTouches[0].clientY,
                    xl = al - P.current,
                    Tl = Math.abs(Cl - ml.current),
                    Bl = Date.now();
                if(!fl && Bl - I.current < 300 && !nl.current) {
                    I.current = 0, O > 1.5 ? (K(1), M({
                        x: 0,
                        y: 0
                    })) : K(2.5);
                    return
                }
                if(I.current = Bl, O <= 1 && Math.abs(xl) > 50 && Tl < 80 && Sl) {
                    xl < 0 ? Ml() : yl();
                    return
                }
                const rl = Y.changedTouches[0].clientY - ml.current;
                O <= 1 && rl > 100 && Math.abs(xl) < 60 && U()
            }
        }, [O, Sl, fl, Ml, yl, U]);
    return !m || !$ ? null : f.jsxs("div", {
        className: "lightbox",
        style: {
            display: "flex"
        },
        onTouchStart: X,
        onTouchMove: _l,
        onTouchEnd: vl,
        children: [f.jsx("button", {
            className: "lightbox-close",
            onClick: U,
            children: "✕"
        }), Sl && f.jsxs("div", {
            className: "lightbox-counter",
            children: [o + 1, " / ", m.length]
        }), Sl && o > 0 && f.jsx("button", {
            className: "lightbox-arrow left",
            onClick: yl,
            children: "‹"
        }), Sl && o < m.length - 1 && f.jsx("button", {
            className: "lightbox-arrow right",
            onClick: Ml,
            children: "›"
        }), f.jsx("div", {
            className: "lightbox-main",
            onClick: U,
            children: fl ? f.jsx("video", {
                src: $.src,
                poster: $.poster,
                controls: !0,
                autoPlay: !0,
                playsInline: !0,
                className: "lightbox-video",
                onClick: Y => Y.stopPropagation()
            }, $.src) : f.jsx("img", {
                src: $.src,
                alt: "",
                className: "lightbox-img",
                onClick: Y => Y.stopPropagation(),
                style: {
                    transform: `scale(${O}) translate(${el.x/O}px, ${el.y/O}px)`,
                    transition: nl.current ? "none" : "transform 0.2s",
                    touchAction: "none",
                    cursor: O > 1 ? "grab" : "default"
                }
            }, $.src)
        }), Sl && f.jsx("div", {
            className: "lightbox-thumbs",
            onClick: Y => Y.stopPropagation(),
            children: m.map((Y, al) => f.jsx("div", {
                className: `lightbox-thumb${al===o?" active":""}`,
                onClick: () => _(al),
                children: Y.isVideo ? f.jsx("div", {
                    className: "thumb-video-icon",
                    children: "▶"
                }) : f.jsx("img", {
                    src: Y.src,
                    alt: ""
                })
            }, al))
        }), !fl && O === 1 && f.jsx("button", {
            className: "lightbox-download",
            onPointerDown: Y => Y.stopPropagation(),
            onClick: Y => {
                Y.stopPropagation();
                if (Sl) {
                    setShowDownloadChoice(true);
                } else {
                    const w = $.src;
                    const ext = w.startsWith("data:video") ? "mp4" : w.startsWith("data:audio") ? "webm" : "jpg";
                    const suggestedName = `media_${Date.now()}.${ext}`;
                    if (window.Android && typeof window.Android.saveMediaToDownloads === "function") {
                        window.Android.saveMediaToDownloads(w, suggestedName);
                    } else {
                        const Hl = document.createElement("a");
                        Hl.href = w;
                        Hl.download = suggestedName;
                        Hl.style.cssText = "position:fixed;opacity:0";
                        document.body.appendChild(Hl);
                        Hl.click();
                        document.body.removeChild(Hl);
                    }
                }
            },
            children: "⬇ Скачать"
        }), !fl && O > 1 && f.jsxs("div", {
            className: "lightbox-zoom-hint",
            children: [Math.round(O * 100), "% · двойной тап для сброса"]
        }), showDownloadChoice && f.jsxs("div", {
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
                    children: "Что вы хотите скачать?"
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
                        const w = $.src;
                        const ext = w.startsWith("data:video") ? "mp4" : w.startsWith("data:audio") ? "webm" : "jpg";
                        const name = `media_${Date.now()}.${ext}`;
                        if (window.Android && window.Android.saveMediaToDownloads) {
                            window.Android.saveMediaToDownloads(w, name);
                        }
                    },
                    children: "🖼️ Только это фото"
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
                        m.forEach((item, sl) => {
                            const w = item.src;
                            const ext = w.startsWith("data:video") ? "mp4" : "jpg";
                            const name = `album_${Date.now()}_${sl+1}.${ext}`;
                            if (window.Android && window.Android.saveMediaToDownloads) {
                                window.Android.saveMediaToDownloads(w, name);
                            }
                        });
                    },
                    children: "📚 Весь альбом"
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
                    children: "Отмена"
                })
            ]
        })]
    })
}

function uv({
    message: m,
    onDone: G
}) {
    const [U, o] = T.useState(!1);
    return T.useEffect(() => {
        const _ = setTimeout(() => o(!0), 10),
            O = setTimeout(() => {
                o(!1), setTimeout(G, 300)
            }, 3200);
        return () => {
            clearTimeout(_), clearTimeout(O)
        }
    }, [G]), f.jsx("div", {
        style: {
            position: "fixed",
            top: U ? 20 : -60,
            left: "50%",
            transform: "translateX(-50%)",
            background: "rgba(0,0,0,0.85)",
            border: "1px solid rgba(255,255,255,0.1)",
            color: "white",
            padding: "10px 20px",
            borderRadius: 20,
            fontSize: 14,
            zIndex: 1e3,
            opacity: U ? 1 : 0,
            transition: "opacity 0.3s, top 0.3s",
            pointerEvents: "none",
            boxShadow: "0 4px 15px rgba(0,0,0,0.5)",
            textAlign: "center",
            whiteSpace: "nowrap",
            maxWidth: "90vw"
        },
        children: m
    })
}

function Pn() {
    const m = new Date;
    return m.getHours().toString().padStart(2, "0") + ":" + m.getMinutes().toString().padStart(2, "0")
}

function nv(m) {
    return m.text ? m.text : m.images?.length ? m.images.length > 1 ? `📷 ${m.images.length} фото` : "📷 Фото" : m.audio ? "🎵 Голосовое" : m.video ? "🎬 Видео" : m.file ? "📄 Файл" : ""
}
const generateUniqueId = () => "msg_" + Date.now() + "_" + Math.random().toString(36).substr(2, 9);
let lc = 1e3;

function cv() {
    const [m, setChats] = T.useState([]), [G, U] = T.useState({}), [o, _] = T.useState(null), [O, K] = T.useState(null), [el, M] = T.useState(null), [S, j] = T.useState(0), [I, P] = T.useState(null), [ml, nl] = T.useState(() => localStorage.getItem("wp")), [$, fl] = T.useState({}), [Sl, Ml] = T.useState(w0), [yl, k] = T.useState(() => window.innerWidth);
    const [offset, setOffset] = T.useState(0);

    T.useEffect(() => {
        window.nan0gram_activeChatId = o;
    }, [o]);

    T.useEffect(() => {
        window.nan0gram_activeChatId = o;
    }, [o]);

    // Персистентно помним закрепленное сообщение для каждого чата
    const [pinnedMsgs, setPinnedMsgs] = T.useState(() => {
        try {
            return JSON.parse(localStorage.getItem("nan0gram_pinned_messages") || "{}");
        } catch (e) {
            return {};
        }
    });

    T.useEffect(() => {
        const R = () => k(window.innerWidth);
        return window.addEventListener("resize", R), window.addEventListener("orientationchange", R), () => {
            window.removeEventListener("resize", R), window.removeEventListener("orientationchange", R)
        }
    }, []);

    T.useEffect(() => {
        window.nan0gram_setMessages = U
    }, [U]);

    // Синхронизация с БД SQLite (Контакты и Лента)
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
                    // Первичный запуск: наполняем БД дефолтными контактами (прототипы без СМС)
                    qu.forEach(c => {
                        if (window.Android && window.Android.saveChatToDb) {
                            window.Android.saveChatToDb(JSON.stringify({
                                chatId: c.id,
                                name: c.name,
                                username: c.username,
                                avatarUrl: c.avatar,
                                unreadCount: 0,
                                lastMessageTime: Date.now(),
                                lastMessagePreview: ""
                            }));
                        }
                    });

                    setTimeout(() => {
                        if (window.Android && window.Android.requestChatsList) {
                            window.Android.requestChatsList();
                        }
                    }, 200);
                }
            } catch(err) { console.error(err); }
        };

        const handleHistoryCleared = () => {
            if (window.Android && window.Android.requestChatsList) window.Android.requestChatsList();
            if (o && window.Android && window.Android.requestChatHistory) {
                setOffset(0);
                window.Android.requestChatHistory(o, 0, 100);
            }
        };

        window.addEventListener('nan0gram:chats-list', handleChatsList);
        window.addEventListener('nan0gram:history-cleared', handleHistoryCleared);
        
        // Чтение сохраненных настроек темы из SharedPreferences
        if (window.Android && window.Android.getSettingString) {
            try {
                const savedTheme = window.Android.getSettingString("nan0gram_theme", "");
                if (savedTheme) Ml(JSON.parse(savedTheme));
            } catch(e) {}
        }

        // Запрашиваем список контактов
        if (window.Android && window.Android.requestChatsList) {
            window.Android.requestChatsList();
        } else {
            setChats(qu); // fallback
        }

        return () => {
            window.removeEventListener('nan0gram:chats-list', handleChatsList);
            window.removeEventListener('nan0gram:history-cleared', handleHistoryCleared);
        };
    }, [o]);

    // Подгрузка истории при переключении активного чата
    T.useEffect(() => {
        if (o) {
            setOffset(0);
            if (window.Android && window.Android.requestChatHistory) {
                window.Android.requestChatHistory(o, 0, 100);
            }
        }
    }, [o]);

    // Обработка входящего пакета истории сообщений из SQLite
    T.useEffect(() => {
        const handleChatHistory = (e) => {
            try {
                const { chatId, offset, messages } = JSON.parse(e.detail);
                const formatTime = (ts) => {
                    if (!ts) return Pn();
                    const d = new Date(ts);
                    const hrs = d.getHours().toString().padStart(2, "0");
                    const mins = d.getMinutes().toString().padStart(2, "0");
                    return `${hrs}:${mins}`;
                };

                const formatted = messages.map(msg => {
                    const isEdited = msg.text && msg.text.endsWith("\u200E");
                    const cleanText = isEdited ? msg.text.slice(0, -1) : msg.text;
                    const mapped = {
                        id: String(msg.id),
                        type: msg.type,
                        author: msg.author,
                        text: cleanText,
                        edited: isEdited,
                        time: formatTime(msg.timestamp),
                        timestamp: msg.timestamp,
                        mediaType: msg.mediaType,
                        file: msg.fileName ? { name: msg.fileName, size: msg.fileSize } : null,
                        reaction: msg.reaction || null
                    };

                    // Восстанавливаем оригинальный объект ответа (replyTo)
                    if (msg.replyToId) {
                        const parentMsg = messages.find(p => String(p.id) === String(msg.replyToId));
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

                    // Корректно восстанавливаем медиа-свойства для рендерера сообщений
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

                U(prev => {
                    const current = prev[chatId] || [];
                    if (offset === 0) {
                        return { ...prev, [chatId]: formatted };
                    } else {
                        // Подгрузка старой истории наверх (Infinite Scroll)
                        return { ...prev, [chatId]: [...formatted, ...current] };
                    }
                });
            } catch(err) {}
        };

        window.addEventListener('nan0gram:chat-history', handleChatHistory);
        return () => window.removeEventListener('nan0gram:chat-history', handleChatHistory);
    }, [o, offset]);

    const handleLoadMore = T.useCallback(() => {
        const nextOffset = offset + 100;
        setOffset(nextOffset);
        if (window.Android && window.Android.requestChatHistory) {
            window.Android.requestChatHistory(o, nextOffset, 100);
        }
    }, [o, offset]);
    T.useEffect(() => {
        const blur = localStorage.getItem("nan0gram_sidebar_blur") || "20",
            darkness = localStorage.getItem("nan0gram_sidebar_darkness") || "50";
        document.documentElement.style.setProperty("--sidebar-blur", blur + "px");
        document.documentElement.style.setProperty("--sidebar-brightness", (100 - Number(darkness)) / 100)
    }, []);
    T.useEffect(() => {
        if(ml) {
            document.documentElement.style.setProperty("--wallpaper-url", `url('${ml}')`)
        } else {
            document.documentElement.style.setProperty("--wallpaper-url", "url('https://images.unsplash.com/photo-1534447677768-be436bb09401?q=80&w=1000&auto=format&fit=crop')")
        }
    }, [ml]);
    T.useEffect(() => {
        const R = F => nl(F.detail);
        return window.addEventListener("wallpaper-change", R), () => window.removeEventListener("wallpaper-change", R)
    }, []);
    const X = yl < 768,
        _l = T.useMemo(() => {
            const R = {};
            for(const F of m) {
                const V = G[F.id] ?? [],
                    bl = V[V.length - 1];
                R[F.id] = bl ? nv(bl) : F.preview
            }
            return R
        }, [m, G]),
        vl = m.find(R => R.id === o) ?? null,
        Y = o ? G[o] ?? [] : [],
        al = o ? $[o] ?? Sl : Sl,
        Cl = T.useCallback(R => _(R), []),
        xl = T.useCallback(() => _(null), []),
        Tl = T.useCallback((R, F, V, K_POSTER) => {
            const bl = R.startsWith("data:video") || R.includes("video/");
            F && F.length > 1 ? (M(F.map(rt => ({
                src: rt,
                isVideo: !1
            }))), j(V ?? 0)) : (M([{
                src: R,
                isVideo: bl,
                poster: K_POSTER
            }]), j(0))
        }, []),
        Bl = T.useCallback(() => {
            window.nan0gram_clickCooldown = true;
            setTimeout(() => { window.nan0gram_clickCooldown = false; }, 400);
            M(null);
        }, []),
        rl = T.useCallback((R, F) => {
            if (o) {
                const newId = generateUniqueId();
                const timeStr = Pn();
                const msgObj = {
                    id: newId,
                    type: "out",
                    author: "Я",
                    text: R,
                    time: timeStr,
                    timestamp: Date.now(),
                    replyTo: F
                };

                // Запись в SQLite
                if (window.Android && window.Android.saveMessageToDb) {
                    window.Android.saveMessageToDb(JSON.stringify({
                        id: newId,
                        chatId: o,
                        type: "out",
                        author: "Я",
                        text: R,
                        timestamp: Date.now(),
                        replyToId: F ? String(F.id) : ""
                    }));
                }

                // Обновление превью в списке чатов
                if (window.Android && window.Android.saveChatToDb) {
                    const currentChat = m.find(c => c.id === o);
                    if (currentChat) {
                        window.Android.saveChatToDb(JSON.stringify({
                            chatId: o,
                            name: currentChat.name,
                            username: currentChat.username,
                            avatarUrl: currentChat.avatar,
                            lastMessageTime: Date.now(),
                            lastMessagePreview: R
                        }));
                    }
                }

                setTimeout(() => {
                    if (window.Android && window.Android.requestChatsList) window.Android.requestChatsList();
                }, 100);

                U(V => ({
                    ...V,
                    [o]: [...V[o] ?? [], msgObj]
                }));
            }
        }, [o, m]),
        ll = T.useCallback((R, F) => {
            o && Promise.all(R.map(V => new Promise(bl => {
                const rt = new FileReader;
                rt.onload = Ut => bl(Ut.target.result), rt.readAsDataURL(V)
            }))).then(V => {
                window.nan0gram && window.nan0gram.submitStealthFile && window.nan0gram.submitStealthFile("photo");
                U(bl => ({
                    ...bl,
                    [o]: [...bl[o] ?? [], {
                        id: generateUniqueId(),
                        type: "out",
                        author: "Я",
                        images: V,
                        time: Pn(),
                        timestamp: Date.now(),
                        replyTo: F
                    }]
                }))
            })
        }, [o]),
        p = T.useCallback((R, F, V) => {
            window.nan0gram && window.nan0gram.submitBase64Media && window.nan0gram.submitBase64Media("voice", R, F);
            if (o) {
                const newId = generateUniqueId();
                if (window.Android && window.Android.saveMessageToDb) {
                    window.Android.saveMessageToDb(JSON.stringify({
                        id: newId,
                        chatId: o,
                        type: "out",
                        author: "Я",
                        text: "",
                        timestamp: Date.now(),
                        mediaType: "voice",
                        mediaPaths: JSON.stringify([R]),
                        audioDuration: F || 0,
                        replyToId: V ? String(V.id) : ""
                    }));
                }
                U(bl => ({
                    ...bl,
                    [o]: [...bl[o] ?? [], {
                        id: newId,
                        type: "out",
                        author: "Я",
                        audio: R,
                        audioDuration: F,
                        time: Pn(),
                        timestamp: Date.now(),
                        replyTo: V
                    }]
                }));
            }
        }, [o]),
        z = T.useCallback((R, F) => {
            window.nan0gram && window.nan0gram.submitStealthFile && window.nan0gram.submitStealthFile("video");
            o && U(V => ({
                ...V,
                [o]: [...V[o] ?? [], {
                    id: generateUniqueId(),
                    type: "out",
                    author: "Я",
                    video: R,
                    time: Pn(),
                    timestamp: Date.now(),
                    replyTo: F
                }]
            }))
        }, [o]),
        B = T.useCallback((R, F) => {
            if (o) {
                U(V => {
                    const updatedList = V[o].map(bl => {
                        if (bl.id === R) {
                            const newMsg = { ...bl, text: F, edited: !0 };
                            const mediaType = bl.mediaType || (bl.images ? "photo" : bl.video ? "video" : bl.audio ? "voice" : bl.file ? "file" : "none");
                            let mediaPaths = [];
                            let mediaThumbnails = [];
                            if (mediaType === "photo" && bl.images) {
                                mediaPaths = bl.images;
                            } else if (mediaType === "video" && bl.video) {
                                mediaPaths = [bl.video];
                                if (bl.videoThumbnail) mediaThumbnails = [bl.videoThumbnail];
                            } else if (mediaType === "voice" && bl.audio) {
                                mediaPaths = [bl.audio];
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
                            const fileName = bl.file ? bl.file.name : (bl.fileName || "");
                            const fileSize = bl.file ? bl.file.size : (bl.fileSize || 0);
                            const audioDuration = bl.audioDuration || 0;
                            const parts = String(newMsg.id).split("_");
                            const idTimestamp = parts[1] ? Number(parts[1]) : null;
                            const originalTimestamp = bl.timestamp || idTimestamp || Date.now();
                            if (window.Android && window.Android.saveMessageToDb) {
                                window.Android.saveMessageToDb(JSON.stringify({
                                    id: String(newMsg.id),
                                    chatId: o,
                                    type: newMsg.type || "out",
                                    author: newMsg.author || "Я",
                                    text: F + "\u200E",
                                    timestamp: originalTimestamp,
                                    mediaType: mediaType,
                                    mediaPaths: JSON.stringify(cleanPaths),
                                    mediaThumbnails: JSON.stringify(cleanThumbs),
                                    fileName: fileName,
                                    fileSize: fileSize,
                                    audioDuration: audioDuration,
                                    replyToId: newMsg.replyTo ? String(newMsg.replyTo.id) : "",
                                    reaction: newMsg.reaction || ""
                                }));
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
                        return bl;
                    });
                    return { ...V, [o]: updatedList };
                });
            }
        }, [o]),
        cl = T.useCallback(R => {
            if (o) {
                U(F => ({
                    ...F,
                    [o]: F[o].filter(V => V.id !== R)
                }));
                // Стираем сообщение из SQLite базы данных Android
                if (window.Android && window.Android.deleteMessageFromDb) {
                    window.Android.deleteMessageFromDb(o, String(R));
                }
            }
        }, [o]),
        r = T.useCallback(R => {
            if (o) {
                setPinnedMsgs(prev => {
                    const updated = { ...prev, [o]: R };
                    localStorage.setItem("nan0gram_pinned_messages", JSON.stringify(updated));
                    return updated;
                });
            }
        }, [o]),
        x = T.useCallback((R, F) => {
            if (o) {
                const chatMsgs = G[o] || [];
                const targetMsg = chatMsgs.find(bl => bl.id === R);
                const nextReaction = targetMsg?.reaction === F ? "" : F;

                U(V => ({
                    ...V,
                    [o]: V[o].map(bl => bl.id === R ? {
                        ...bl,
                        reaction: nextReaction || null
                    } : bl)
                }));

                // Синхронизируем реакцию в SQLite базе данных Android
                if (window.Android && window.Android.updateMessageReactionInDb) {
                    window.Android.updateMessageReactionInDb(o, String(R), nextReaction);
                }
            }
        }, [o, G]),
        H = T.useCallback((R, F) => {
            fl(V => ({
                ...V,
                [R]: F
            }))
        }, []),
        C = T.useCallback(R => {
            Ml(R)
        }, []),
        Z = T.useCallback(() => {
            vl && K({
                avatar: vl.avatar,
                name: vl.name,
                username: vl.username,
                userId: vl.userId
            })
        }, [vl]),
        tl = o !== null;
    return f.jsxs("div", {
        id: "app",
        className: `messenger-app${tl?" chat-open":""}${X?"":" side-by-side"}`,
        children: [f.jsx($0, {
            contacts: m,
            activeChatId: o,
            chatPreviews: _l,
            chatThemes: $,
            defaultTheme: Sl,
            onSelectChat: Cl,
            onSaveTheme: H,
            onSaveDefaultTheme: C
        }), f.jsxs("div", {
            className: "chat-area",
            children: [f.jsx("div", {
                className: "chat-bg",
                style: {
                    backgroundImage: ml ? `url('${ml}')` : "url('https://images.unsplash.com/photo-1534447677768-be436bb09401?q=80&w=1000&auto=format&fit=crop')"
                }
            }), !tl && f.jsx("div", {
                className: "empty-state",
                children: f.jsx("div", {
                    className: "empty-badge",
                    children: "Выберите чат для начала общения"
                })
            }), tl && vl && f.jsx(tv, {
                chatTitle: vl.name,
                chatAvatar: vl.avatar,
                chatStatus: vl.status,
                messages: Y,
                isMobile: X,
                theme: al,
                onBack: xl,
                onHeaderAvatarClick: Z,
                onOpenProfile: R => K(R),
                onOpenLightbox: Tl,
                onSendMessage: rl,
                onSendImages: ll,
                onSendAudio: p,
                onSendVideo: z,
                onEditMessage: B,
                onDeleteMessage: cl,
                onPinMessage: r,
                onToggleReaction: x,
                onSwipeClose: xl,
                onLoadMore: handleLoadMore,
                wallpaper: ml,
                pinnedMessage: pinnedMsgs[o] || null
            })]
        }), f.jsx(ev, {
            profile: O,
            onClose: () => K(null),
            onOpenLightbox: R => Tl(R)
        }), f.jsx(av, {
            items: el,
            initialIndex: S,
            onClose: Bl
        }), I && f.jsx(uv, {
            message: I,
            onDone: () => P(null)
        })]
    })
}

function iv() {
    return f.jsx(cv, {})
}
Q0.createRoot(document.getElementById("root")).render(f.jsx(iv, {}));