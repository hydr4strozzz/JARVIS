package com.jarvis.assistant.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class TtsManager(ctx: Context) {
    private var ready = false
    private val tts = TextToSpeech(ctx) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ready = true
            tts.language = Locale.getDefault()
        }
    }

    fun speak(text: String) {
        if (!ready) return
        tts.language = Locale.getDefault()
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_\${System.currentTimeMillis()}")
    }

    fun shutdown() { tts.stop(); tts.shutdown() }
}
