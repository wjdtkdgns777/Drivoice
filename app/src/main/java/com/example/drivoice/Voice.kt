package com.example.drivoice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Locale
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInit
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode

class Voice(context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val tts = TextToSpeech(context, TtsMode.ON_DEVICE)
    private val stt = SpeechToText(context, SttMode.ON_DEVICE)
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { if (it < 0) tts.stop() }
        .build()
    private val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, TONE_VOLUME)

    private var ready = false
    private var afterSpeech: (() -> Unit)? = null
    private var onHeard: ((String) -> Unit)? = null

    var speaking = false
        private set

    private val events = object : EventListener {
        override fun onStart() = Unit
        override fun onReady() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() = finish(true)
        override fun onStop() = finish(false)
        override fun onError(error: TtsError) = finish(false)
    }

    private val results = object : ResultListener {
        override fun onUpdated(text: String, isFinal: Boolean) {
            if (isFinal) main.post { onHeard?.invoke(text); onHeard = null }
        }

        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onStartedRecognition() = Unit
        override fun onEndedRecognition() = Unit
        override fun onReady() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onError(error: SttError) {
            Log.w(TAG, "stt $error")
        }
    }

    init {
        tts.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                tts.setLocale(Locale.KOREA)
                tts.setAudioAttributes(attributes)
                ready = true
            }

            override fun onFailure(error: TtsError) {
                Log.w(TAG, "tts $error")
            }
        })
        tts.addEventListener(events)
        stt.initialize(object : SttInit {
            override fun onSuccess() = stt.setLocale(Locale.KOREA)
            override fun onFailure(error: SttError) {
                Log.w(TAG, "stt init $error")
            }
        })
        stt.addListener(results)
    }

    fun speak(text: String, then: (() -> Unit)? = null): Boolean {
        if (!ready || speaking) return false
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
        speaking = true
        afterSpeech = then
        tts.speak(text)
        return true
    }

    fun listen(onText: (String) -> Unit) {
        onHeard = onText
        stt.request()
        main.postDelayed({
            stt.stop()
            onHeard = null
        }, LISTEN_MS)
    }

    fun chime() {
        if (speaking || audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        tone.startTone(ToneGenerator.TONE_PROP_ACK, TONE_MS)
        main.postDelayed({ if (!speaking) audio.abandonAudioFocusRequest(focus) }, TONE_MS.toLong())
    }

    fun release() {
        tts.removeEventListener(events)
        stt.removeListener(results)
        tts.release()
        stt.release()
        tone.release()
    }

    private fun finish(done: Boolean) {
        main.post {
            audio.abandonAudioFocusRequest(focus)
            speaking = false
            val then = afterSpeech
            afterSpeech = null
            if (done) then?.invoke()
        }
    }

    private companion object {
        const val TAG = "Drivoice"
        const val LISTEN_MS = 8_000L
        const val TONE_VOLUME = 60
        const val TONE_MS = 160
    }
}
