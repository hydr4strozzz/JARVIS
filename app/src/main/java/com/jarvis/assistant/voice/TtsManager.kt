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
                tts?.language = Locale.getDefault()
                // Make it faster, more natural
                tts?.setSpeechRate(1.1f) 
                tts?.setPitch(1.0f)
            }
        }
    }

    fun speak(text: String) {
        if (!ready) return
        // Auto-detect if the text contains Malayalam characters to set the right language
        val isMalayalam = text.any { it.code in 0x0D00..0x0D7F }
        if (isMalayalam) {
            tts?.language = Locale("ml", "IN")
        } else {
            tts?.language = Locale.getDefault()
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_${System.currentTimeMillis()}")
    }

    fun shutdown() { tts?.stop(); tts?.shutdown() }
}
