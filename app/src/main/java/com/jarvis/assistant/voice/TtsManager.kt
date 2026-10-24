package com.jarvis.assistant.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class TtsManager(ctx: Context) {
    private var ready = false
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(ctx) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                tts?.setSpeechRate(1.1f) 
            }
        }
    }

    fun speak(text: String) {
        if (!ready) return
        val isMalayalam = text.any { it.code in 0x0D00..0x0D7F }
        tts?.language = if (isMalayalam) Locale("ml", "IN") else Locale.getDefault()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_${System.currentTimeMillis()}")
    }

    fun shutdown() { tts?.stop(); tts?.shutdown() }
}
