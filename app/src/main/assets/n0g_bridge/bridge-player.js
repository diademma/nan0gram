(function(W) {
    "use strict";
    try {

        function setupCustomVoicePlayer(container) {
            if (container.querySelector('.tg-voice-player')) return;

            const audio = container.querySelector('audio');
            if (!audio) return;

            audio.style.display = 'none';
            const defaultWave = container.querySelector('.voice-wave');
            if (defaultWave) defaultWave.style.display = 'none';
            const defaultDuration = container.querySelector('.voice-duration');
            if (defaultDuration) defaultDuration.style.display = 'none';

            const player = document.createElement('div');
            player.className = 'tg-voice-player';

            // Изолируем ГС от кликов контекстного меню (блокируем только клики и мышь на уровне всего плеера)
            const mainStopEvents = ['click', 'pointerdown', 'pointerup', 'mousedown', 'mouseup', 'contextmenu'];
            mainStopEvents.forEach(function(evt) {
                player.addEventListener(evt, function(e) {
                    e.stopPropagation();
                });
            });

            const playBtn = document.createElement('button');
            playBtn.className = 'tg-play-btn';
            playBtn.innerHTML = `
                <svg class="play-svg" viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>
                <svg class="pause-svg" viewBox="0 0 24 24" fill="currentColor" style="display:none"><path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/></svg>
            `;
            player.appendChild(playBtn);

            const waveContainer = document.createElement('div');
            waveContainer.className = 'tg-waveform-container';

            const barHeights = [10, 16, 8, 22, 12, 18, 6, 26, 14, 20, 10, 24, 16, 12, 22, 8, 18, 14, 28, 10, 22, 14, 18, 10, 14, 8, 16, 12, 24, 10];
            const barCount = 28;

            const bgWave = document.createElement('div');
            bgWave.className = 'tg-waveform-bg';
            const activeContainer = document.createElement('div');
            activeContainer.className = 'tg-waveform-active-container';
            const activeWave = document.createElement('div');
            activeWave.className = 'tg-waveform-active';

            for (let i = 0; i < barCount; i++) {
                const h = barHeights[i % barHeights.length];
                const bgBar = document.createElement('span');
                bgBar.className = 'tg-wave-bar';
                bgBar.style.height = h + 'px';
                bgWave.appendChild(bgBar);

                const activeBar = document.createElement('span');
                activeBar.className = 'tg-wave-bar active';
                activeBar.style.height = h + 'px';
                activeWave.appendChild(activeBar);
            }

            activeContainer.appendChild(activeWave);
            waveContainer.appendChild(bgWave);
            waveContainer.appendChild(activeContainer);
            player.appendChild(waveContainer);

            const durationDiv = document.createElement('div');
            durationDiv.className = 'tg-voice-meta';

            // Мгновенно отображаем исходную длительность (Замечание 2)
            const initialDurationText = defaultDuration ? defaultDuration.textContent.trim() : '0:00';
            durationDiv.textContent = initialDurationText;
            player.appendChild(durationDiv);

            container.appendChild(player);

            function formatTime(secs) {
                if (isNaN(secs) || secs === Infinity) return '0:00';
                const m = Math.floor(secs / 60);
                const s = Math.floor(secs % 60).toString().padStart(2, '0');
                return m + ':' + s;
            }

            const playIcon = playBtn.querySelector('.play-svg');
            const pauseIcon = playBtn.querySelector('.pause-svg');

            function updatePlayState() {
                if (audio.paused) {
                    playIcon.style.display = 'block';
                    pauseIcon.style.display = 'none';
                } else {
                    playIcon.style.display = 'none';
                    pauseIcon.style.display = 'block';
                }
            }

            playBtn.addEventListener('click', function(e) {
                e.stopPropagation();
                if (audio.paused) {
                    document.querySelectorAll('audio').forEach(function(other) {
                        if (other !== audio) {
                            other.pause();
                            const otherContainer = other.closest('.voice-msg');
                            if (otherContainer) {
                                const btn = otherContainer.querySelector('.tg-play-btn');
                                if (btn) {
                                    btn.querySelector('.play-svg').style.display = 'block';
                                    btn.querySelector('.pause-svg').style.display = 'none';
                                }
                            }
                        }
                    });
                    if (W.Android && typeof W.Android.requestTransientFocus === 'function') {
                        W.Android.requestTransientFocus();
                    }
                    audio.play().then(updatePlayState).catch(function(){});
                } else {
                    audio.pause();
                    updatePlayState();
                }
            });

            audio.addEventListener('play', updatePlayState);
            audio.addEventListener('pause', function() {
                updatePlayState();
                if (W.Android && typeof W.Android.abandonFocus === 'function') {
                    W.Android.abandonFocus();
                }
            });
            audio.addEventListener('ended', function() {
                updatePlayState();
                if (W.Android && typeof W.Android.abandonFocus === 'function') {
                    W.Android.abandonFocus();
                }
            });

            let isDraggingWave = false;
            let dragPct = 0;
            let lastTouchEnd = 0;

            let parsedDuration = 0;
            const timeParts = initialDurationText.split(':').map(Number);
            if (timeParts.length === 2) parsedDuration = timeParts[0] * 60 + timeParts[1];
            else if (timeParts.length === 3) parsedDuration = timeParts[0] * 3600 + timeParts[1] * 60 + timeParts[2];

            function getAudioTotal() {
                const d = audio.duration;
                return (d && d > 0 && d !== Infinity && !isNaN(d)) ? d : parsedDuration;
            }

            audio.addEventListener('timeupdate', function() {
                if (isDraggingWave) return;
                const current = audio.currentTime;
                const total = getAudioTotal();
                const pct = total > 0 ? (current / total) * 100 : 0;
                activeContainer.style.width = pct + '%';
                durationDiv.textContent = formatTime(current);
            });

            audio.addEventListener('loadedmetadata', function() {
                const total = getAudioTotal();
                if (total > 0) durationDiv.textContent = formatTime(total);
            });

            function updateWaveVisuals(e) {
                const rect = waveContainer.getBoundingClientRect();
                let clientX = rect.left;
                if (e.touches && e.touches.length > 0) {
                    clientX = e.touches[0].clientX;
                } else if (e.changedTouches && e.changedTouches.length > 0) {
                    clientX = e.changedTouches[0].clientX;
                } else if (typeof e.clientX === 'number') {
                    clientX = e.clientX;
                }
                const x = clientX - rect.left;
                dragPct = Math.max(0, Math.min(1, x / rect.width));
                const total = getAudioTotal();
                if (total > 0) {
                    activeContainer.style.width = (dragPct * 100) + '%';
                    durationDiv.textContent = formatTime(dragPct * total);
                }
            }

            waveContainer.addEventListener('touchstart', function(e) {
                e.stopPropagation();
                isDraggingWave = true;
                updateWaveVisuals(e);
            }, {passive: true});

            waveContainer.addEventListener('touchmove', function(e) {
                e.stopPropagation();
                if (isDraggingWave) updateWaveVisuals(e);
            }, {passive: true});

            waveContainer.addEventListener('touchend', function(e) {
                e.stopPropagation();
                lastTouchEnd = Date.now();
                if (isDraggingWave) {
                    isDraggingWave = false;
                    const total = getAudioTotal();
                    if (total > 0) {
                        audio.currentTime = dragPct * total;
                    }
                }
            });

            waveContainer.addEventListener('click', function(e) {
                e.stopPropagation();
                if (Date.now() - lastTouchEnd < 500) return;
                if (!isDraggingWave) {
                    updateWaveVisuals(e);
                    const total = getAudioTotal();
                    if (total > 0) {
                        audio.currentTime = dragPct * total;
                    }
                }
            });
        }

        document.addEventListener("DOMContentLoaded", function() {
            try {
                const observer = new MutationObserver(function(mutations) {
                    document.querySelectorAll('.voice-msg').forEach(setupCustomVoicePlayer);
                });
                observer.observe(document.body, { childList: true, subtree: true });
                document.querySelectorAll('.voice-msg').forEach(setupCustomVoicePlayer);
            } catch (err) {
                console.error('[nan0gram:player] Ошибка инициализации MutationObserver:', err.message);
            }
        });

    } catch (e) {
        console.error('[nan0gram:player] Критическая ошибка инициализации:', e.message);
    }
})(window);