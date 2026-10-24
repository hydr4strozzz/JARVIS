package com.jarvis.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.jarvis.assistant.util.JarvisLogger

class SpeechRecognizerManager(
    private val ctx: Context,
    private val onResult: (String) -> Unit,
    private val onError: (Int) -> Unit
) {
    private var recognizer: SpeechRecognizer? = null
    private var isListening = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { isListening = true }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { isListening = false }
        
        override fun onError(error: Int) {
            isListening = false
            onError(error)
        }
        
        override fun onResults(results: Bundle?) {
            isListening = false
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
            if (text.isNotBlank()) onResult(text) else onError(SpeechRecognizer.ERROR_NO_MATCH)
        }
        
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun start() {
        mainHandler.post {
            if (isListening) return@post
            
            // Always destroy old one to prevent native crashes
            try {
                recognizer?.destroy()
            } catch (e: Exception) {}
            
            recognizer = SpeechRecognizer.createSpeechRecognizer(ctx).apply {
                setRecognitionListener(listener)
            }
            
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
            }
            
            try {
                recognizer?.startListening(intent)
                isListening = true
            } catch (e: Exception) {
                isListening = false
                onError(-1)
            }
        }
    }

    fun stop() {
        mainHandler.post {
            if (!isListening) return@post
            isListening = false
            try { recognizer?.stopListening() } catch (e: Exception) {}
        }
    }

    fun destroy() {
        mainHandler.post {
            isListening = false
            try { recognizer?.destroy() } catch (e: Exception) {}
            recognizer = null
        }
    }
}
