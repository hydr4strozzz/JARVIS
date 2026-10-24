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
        router = LlmRouter()
        executor = ActionExecutor(this)
        speech = SpeechRecognizerManager(this,
            onResult = ::onSpeechResult,
            onError = { err -> 
                JarvisLogger.log("Mic Error: $err")
                if (err == 7 || err == 6) restartListening(500) // NO_MATCH or TIMEOUT
                else restartListening(2000) // BUSY or NETWORK
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
        updateNotification("Listening...")
        JarvisLogger.log("Started. Say 'Hey Jarvis'")
        startListeningLoop()
    }

    private fun updateNotification(text: String) {
        val ni = NotificationCompat.Builder(this, JarvisApp.CHANNEL)
            .setContentTitle("JARVIS")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_mic)
            .setOngoing(true)
            .build()
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(1, ni)
        startForeground(1, ni)
    }

    private fun startListeningLoop() {
        speech.start()
    }

    private fun onSpeechResult(text: String) {
        val clean = text.trim().lowercase()
        if (busy) return

        JarvisLogger.log("Heard: $text")

        if (!armed) {
            if (clean.contains("jarvis")) {
                armed = true
                tts.speak("Yes?")
                JarvisLogger.log("Awaiting command...")
                restartListening(afterMs = 1000)
            } else {
                restartListening(afterMs = 300)
            }
            return
        }

        busy = true
        armed = false
        JarvisLogger.log("Thinking...")
        scope.launch {
            try {
                val reply = withContext(Dispatchers.IO) {
                    router.handle(text)
                }
                
                reply.action?.let { executor.execute(it, reply.params) }
                
                if (reply.speak.isNotBlank()) {
                    tts.speak(reply.speak)
                    JarvisLogger.log("Reply: ${reply.speak}\nAction: ${reply.action}")
                } else {
                    JarvisLogger.log("Action done: ${reply.action} (No speak text)")
                }
            } catch (e: Exception) {
                tts.speak("Error.")
                JarvisLogger.log("ERROR: ${e.message}")
            } finally {
                busy = false
                restartListening(afterMs = 1000)
            }
        }
    }

    private fun restartListening(afterMs: Long = 400) {
        scope.launch {
            delay(afterMs)
            if (!busy) speech.start()
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "JARVIS::wake")
        wakeLock?.acquire(10 * 60 * 1000L)
    }

    override fun onDestroy() {
        scope.cancel()
        speech.destroy()
        tts.shutdown()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "jarvis.start"
        const val ACTION_STOP = "jarvis.stop"
    }
}
