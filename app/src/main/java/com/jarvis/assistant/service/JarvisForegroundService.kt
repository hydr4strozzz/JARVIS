package com.jarvis.assistant.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.R
import com.jarvis.assistant.actions.ActionExecutor
import com.jarvis.assistant.ai.LlmRouter
import com.jarvis.assistant.util.JarvisLogger
import com.jarvis.assistant.voice.SpeechRecognizerManager
import com.jarvis.assistant.voice.TtsManager
import kotlinx.coroutines.*

class JarvisForegroundService : Service() {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var speech: SpeechRecognizerManager
    private lateinit var tts: TtsManager
    private lateinit var router: LlmRouter
    private lateinit var executor: ActionExecutor
    private var wakeLock: PowerManager.WakeLock? = null
    private var armed = false
    private var busy = false

    override fun onCreate() {
        super.onCreate()
        tts = TtsManager(this)
        router = LlmRouter(this)
        executor = ActionExecutor(this)
        
        speech = SpeechRecognizerManager(this,
            onResult = ::onSpeechResult,
            onError = { err -> 
                if (err == android.speech.SpeechRecognizer.ERROR_NO_MATCH || err == android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    if (!busy && !armed) restartListening(300)
                } else {
                    if (!busy && !armed) restartListening(1000)
                }
            })
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            else -> startForegroundInternal()
        }
        return START_STICKY
    }

    private fun startForegroundInternal() {
        updateNotification("Listening... say 'Hey Jarvis'")
        JarvisLogger.log("JARVIS Active. Say 'Hey Jarvis'")
        speech.start()
    }

    private fun updateNotification(text: String) {
        val ni = NotificationCompat.Builder(this, JarvisApp.CHANNEL)
            .setContentTitle("JARVIS").setContentText(text).setSmallIcon(R.drawable.ic_mic).setOngoing(true).build()
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(1, ni); startForeground(1, ni)
    }

    private fun onSpeechResult(text: String) {
        val clean = text.trim().lowercase()
        if (busy || clean.isBlank()) return
        
        JarvisLogger.log("Heard: $text")

        if (!armed) {
            if (clean.contains("jarvis") || clean.contains("jervis")) {
                armed = true
                speech.stop()
                tts.speak("Ah huh")
                scope.launch { 
                    delay(1200) // Wait for TTS to finish saying 'Ah huh'
                    busy = false
                    armed = true // Now ready for command
                    JarvisLogger.log("Awaiting command...")
                    speech.start() 
                }
            } else {
                restartListening(300)
            }
            return
        }

        busy = true
        armed = false
        speech.stop()
        JarvisLogger.log("Thinking...")
        
        scope.launch {
            try {
                val reply = withContext(Dispatchers.IO) { router.handle(text) }
                reply.action?.let { executor.execute(it, reply.params) }
                if (reply.speak.isNotBlank()) {
                    tts.speak(reply.speak)
                    JarvisLogger.log("Reply: ${reply.speak}")
                    delay(2000) // Wait for long reply TTS to finish
                }
            } catch (e: Exception) {
                tts.speak("Error.")
                JarvisLogger.log("ERROR: ${e.message}")
                delay(1000)
            } finally {
                busy = false
                speech.start()
            }
        }
    }

    private fun restartListening(afterMs: Long) {
        scope.launch {
            delay(afterMs)
            if (!busy && !armed) speech.start()
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "JARVIS::wake")
        wakeLock?.acquire(10 * 60 * 1000L)
    }

    override fun onDestroy() {
        scope.cancel(); speech.destroy(); tts.shutdown()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object { const val ACTION_START = "jarvis.start"; const val ACTION_STOP = "jarvis.stop" }
}
