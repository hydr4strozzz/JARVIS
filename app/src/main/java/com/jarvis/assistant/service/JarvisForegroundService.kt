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
            onError = { restartLater() })
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
        if (!armed) updateNotification("Listening... say 'Hey Jarvis'")
        speech.start()
    }

    private fun onSpeechResult(text: String) {
        val clean = text.trim().lowercase()
        if (busy) return

        if (!armed) {
            // "hey jarvis" or "jarvis" wake word
            if (clean.contains("jarvis")) {
                armed = true
                tts.speak("Yes?")
                updateNotification("Listening for command...")
                restartListening(afterMs = 500)
            } else {
                restartListening(afterMs = 300)
            }
            return
        }

        busy = true
        armed = false
        updateNotification("Thinking...")
        scope.launch {
            try {
                val reply = withContext(Dispatchers.IO) {
                    router.handle(text)
                }
                if (reply.speak.isNotBlank()) {
                    updateNotification("Speaking: ${reply.speak}")
                    tts.speak(reply.speak)
                }
                reply.action?.let { executor.execute(it, reply.params) }
            } catch (e: Exception) {
                tts.speak("Something went wrong.")
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

    private fun restartLater() {
        scope.launch { delay(1000); if (!busy) speech.start() }
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
