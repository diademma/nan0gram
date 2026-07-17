(function(W) {
    "use strict";
    try {
        // --- Mic Button Timer-Based Lock (Bypasses UI Conflicts) ---
        let isRecording = false;
        let isLocked = false;
        let lockTimeout = null;
        let recordingInterval = null;
        let elapsedSeconds = 0;
        let pressStartTime = 0;

        function getOrCreateCancelBtn() {
            let btn = document.querySelector('.tg-record-cancel-btn');
            if (!btn) {
                btn = document.createElement('div');
                btn.className = 'tg-record-cancel-btn';
                btn.innerHTML = `<svg viewBox="0 0 24 24" fill="currentColor"><path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/></svg>`;
                document.body.appendChild(btn);
            }
            return btn;
        }

        // Вспомогательная функция для форматирования таймера
        function formatTimer(sec) {
            const m = Math.floor(sec / 60).toString().padStart(2, "0");
            const s = (sec % 60).toString().padStart(2, "0");
            return `${m}:${s}`;
        }

        function showRecordingUI() {
            const inputBox = document.querySelector('.msg-input-box');
            const inputArea = document.querySelector('.input-area');
            if (!inputBox || !inputArea) return;

            inputBox.style.display = 'none';
            inputArea.querySelectorAll('.input-icon').forEach(el => el.style.display = 'none');

            let bar = document.querySelector('.tg-recording-overlay-bar');
            if (!bar) {
                bar = document.createElement('div');
                bar.className = 'tg-recording-overlay-bar';
                
                let waveHTML = `<div class="tg-rec-wave">`;
                for (let i = 0; i < 24; i++) {
                    waveHTML += `<span class="tg-rec-bar" style="animation-delay: ${0.05 * (i % 6)}s; animation-duration: ${0.5 + (i % 4) * 0.15}s"></span>`;
                }
                waveHTML += `</div>`;

                bar.innerHTML = `
                    <div class="tg-rec-dot"></div>
                    <span class="tg-rec-label">REC</span>
                    <span class="tg-rec-timer">00:00</span>
                    ${waveHTML}
                `;
                inputArea.insertBefore(bar, inputArea.firstChild);
            }
        }

        function hideRecordingUI() {
            const inputBox = document.querySelector('.msg-input-box');
            const inputArea = document.querySelector('.input-area');
            if (inputBox) inputBox.style.display = 'flex';
            if (inputArea) inputArea.querySelectorAll('.input-icon').forEach(el => el.style.display = 'block');

            const bar = document.querySelector('.tg-recording-overlay-bar');
            if (bar) bar.remove();
        }

        function dispatchRelease(btn) {
            btn.dispatchEvent(new PointerEvent('pointerup', { bubbles: true, cancelable: true }));
            try { btn.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, cancelable: true })); } catch(e){}
            try { btn.dispatchEvent(new TouchEvent('touchend', { bubbles: true, cancelable: true })); } catch(e){}
        }

        // --- БЕЗОПАСНЫЙ ПЕРЕХВАТ СОБЫТИЙ С ЦЕПОЧКОЙ ПОДАВЛЕНИЯ ---
        const blockDuringLock = function(e) {
            try {
                const btn = e.target && typeof e.target.closest === 'function' ? e.target.closest('.send-mic-btn') : null;
                if (btn && isLocked) {
                    const input = document.querySelector('.msg-input');
                    const hasText = input && input.value.trim().length > 0;
                    if (!hasText) {
                        e.stopPropagation();
                        e.preventDefault();
                    }
                }
            } catch (err) {
                console.error('[nan0gram:recorder] blockDuringLock error:', err.message);
            }
        };

        const releaseEvents = ['pointerup', 'touchend', 'pointerleave', 'pointerout', 'mouseleave', 'pointercancel', 'touchcancel'];
        releaseEvents.forEach(evt => {
            window.addEventListener(evt, blockDuringLock, { capture: true });
        });

        window.addEventListener('pointerdown', function(e) {
            try {
                if (window.nan0gram_clickCooldown && e.target && typeof e.target.closest === 'function' && e.target.closest('.send-mic-btn')) {
                    e.stopPropagation();
                    e.preventDefault();
                    return;
                }

                const btn = e.target && typeof e.target.closest === 'function' ? e.target.closest('.send-mic-btn') : null;
                if (btn) {
                    const input = document.querySelector('.msg-input');
                    const hasText = input && input.value.trim().length > 0;
                    if (hasText) return;

                    if (isLocked) {
                        isLocked = false;
                        isRecording = false;
                        btn.classList.remove('tg-send-mode');
                        document.body.classList.remove('tg-locked-active');
                        
                        const cancelBtn = document.querySelector('.tg-record-cancel-btn');
                        if (cancelBtn) cancelBtn.style.display = 'none';

                        hideRecordingUI();
                        window.nan0gram_clickCooldown = true;
                        setTimeout(() => { window.nan0gram_clickCooldown = false; }, 400);

                        dispatchRelease(btn);
                        e.stopPropagation();
                        e.preventDefault();
                        return;
                    }

                    isRecording = true;
                    isLocked = false;
                    elapsedSeconds = 0;
                    pressStartTime = Date.now();
                    document.body.classList.remove('tg-locked-active');
                    showRecordingUI();

                    clearInterval(recordingInterval);
                    recordingInterval = setInterval(() => {
                        elapsedSeconds++;
                        const timerEl = document.querySelector('.tg-rec-timer');
                        if (timerEl) timerEl.textContent = formatTimer(elapsedSeconds);
                    }, 1000);

                    clearTimeout(lockTimeout);
                    lockTimeout = setTimeout(() => {
                        if (isRecording && !isLocked) {
                            isLocked = true;
                            if (navigator.vibrate) navigator.vibrate(50);

                            btn.classList.add('tg-send-mode');
                            document.body.classList.add('tg-locked-active');
                            const cancelBtn = getOrCreateCancelBtn();
                            const btnRect = btn.getBoundingClientRect();
                            cancelBtn.style.left = (btnRect.left - 54) + 'px';
                            cancelBtn.style.top = (btnRect.top + (btnRect.height - 44) / 2) + 'px';
                            cancelBtn.style.display = 'flex';
                        }
                    }, 1000);
                } 
            } catch (err) {
                console.error('[nan0gram:recorder] pointerdown error:', err.message);
            }
        }, { capture: true });

        window.addEventListener('pointerup', function(e) {
            try {
                const btn = e.target && typeof e.target.closest === 'function' ? e.target.closest('.send-mic-btn') : null;
                if (btn) {
                    const input = document.querySelector('.msg-input');
                    const hasText = input && input.value.trim().length > 0;
                    if (hasText) return;

                    if (isLocked) {
                        e.stopPropagation();
                        e.preventDefault();
                        return;
                    }

                    if (isRecording) {
                        const pressDuration = Date.now() - pressStartTime;
                        if (pressDuration < 400) {
                            window.nan0gram_cancelVoice = true;
                            isRecording = false;
                            clearTimeout(lockTimeout);
                            clearInterval(recordingInterval);
                            hideRecordingUI();
                            dispatchRelease(btn);
                            return;
                        }
                        isRecording = false;
                        clearTimeout(lockTimeout);
                        clearInterval(recordingInterval);
                        document.body.classList.remove('tg-locked-active');
                        hideRecordingUI();
                    }
                } 
            } catch (err) {
                console.error('[nan0gram:recorder] pointerup error:', err.message);
            }
        }, { capture: true });

        window.addEventListener('pointercancel', function(e) {
            try {
                if (isRecording && !isLocked) {
                    isRecording = false;
                    clearTimeout(lockTimeout);
                    clearInterval(recordingInterval);
                    document.body.classList.remove('tg-locked-active');
                    hideRecordingUI();
                }
            } catch (err) {
                console.error('[nan0gram:recorder] pointercancel error:', err.message);
            }
        }, { capture: true });

        document.addEventListener('pointerdown', function(e) {
            try {
                const cancelBtn = e.target && typeof e.target.closest === 'function' ? e.target.closest('.tg-record-cancel-btn') : null;
                if (cancelBtn) {
                    e.stopPropagation();
                    e.preventDefault();

                    window.nan0gram_cancelVoice = true;

                    isLocked = false;
                    isRecording = false;
                    clearTimeout(lockTimeout);
                    clearInterval(recordingInterval);
                    document.body.classList.remove('tg-locked-active');
                    
                    cancelBtn.style.display = 'none';
                    hideRecordingUI();

                    const micBtn = document.querySelector('.send-mic-btn');
                    if (micBtn) {
                        micBtn.classList.remove('tg-send-mode');
                        window.nan0gram_clickCooldown = true;
                        setTimeout(() => { window.nan0gram_clickCooldown = false; }, 400);
                        dispatchRelease(micBtn);
                    }
                }
            } catch (err) {
                console.error('[nan0gram:recorder] cancelBtn pointerdown error:', err.message);
            }
        }, { capture: true });

        // --- Управление аудиофокусом Android при записи ГС ---
        if (W.navigator && W.navigator.mediaDevices && W.navigator.mediaDevices.getUserMedia) {
            const originalGetUserMedia = W.navigator.mediaDevices.getUserMedia.bind(W.navigator.mediaDevices);
            W.navigator.mediaDevices.getUserMedia = function(constraints) {
                if (constraints && constraints.audio) {
                    if (W.Android && typeof W.Android.requestTransientFocus === 'function') {
                        W.Android.requestTransientFocus();
                    }
                }
                return originalGetUserMedia(constraints).then(function(stream) {
                    const audioTrack = stream.getAudioTracks()[0];
                    if (audioTrack) {
                        const originalStop = audioTrack.stop.bind(audioTrack);
                        audioTrack.stop = function() {
                            originalStop();
                            if (W.Android && typeof W.Android.abandonFocus === 'function') {
                                W.Android.abandonFocus();
                            }
                        };
                    }
                    return stream;
                }).catch(function(err) {
                    if (W.Android && typeof W.Android.abandonFocus === 'function') {
                        W.Android.abandonFocus();
                    }
                    throw err;
                });
            };
        }
    } catch (e) {
        console.error('[nan0gram:recorder] Критическая ошибка инициализации:', e.message);
    }
})(window);