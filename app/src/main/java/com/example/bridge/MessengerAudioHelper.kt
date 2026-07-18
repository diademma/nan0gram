package com.example

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.WebView

internal class MessengerAudioHelper(
    private val getUkrnetWebView: () -> WebView?
) {
    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { }
    private var focusRequest: Any? = null
    private val ui = Handler(Looper.getMainLooper())

    fun requestTransientFocus() {
        ui.post {
            val context = getUkrnetWebView()?.context ?: return@post
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrib = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(attrib)
                    .setOnAudioFocusChangeListener { }
                    .build()
                focusRequest = request
                am.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(focusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            }
        }
    }

    fun abandonFocus() {
        ui.post {
            val context = getUkrnetWebView()?.context ?: return@post
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val request = focusRequest as? AudioFocusRequest
                if (request != null) {
                    am.abandonAudioFocusRequest(request)
                }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(focusChangeListener)
            }
        }
    }
}