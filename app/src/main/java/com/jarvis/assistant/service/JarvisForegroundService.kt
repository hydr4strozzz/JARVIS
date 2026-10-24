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
import com.jarvis.assistant.voice.AudioRecorderManager
import com.jarvis.assistant.voice.TtsManager
import kotlinx.coroutines.*

class JarvisForegroundService : Service() {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var recorder: AudioRecorderManager
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
        recorder = AudioRecorderManager(this) { text -> onSpeechResult(text) }
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
        JarvisLogger.log("Started. Say 'Hey Jarvis'")
        recorder.start()
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
                recorder.muteMic()
                tts.speak("Ah huh")
                scope.launch { delay(1200); recorder.unmuteMic(); JarvisLogger.log("Awaiting command...") }
            }
            return
        }

        busy = true
        armed = false
        recorder.muteMic()
        JarvisLogger.log("Thinking...")
        scope.launch {
            try {
                val reply = withContext(Dispatchers.IO) { router.handle(text) }
                reply.action?.let { executor.execute(it, reply.params) }
                if (reply.speak.isNotBlank()) {
                    tts.speak(reply.speak)
                    JarvisLogger.log("Reply: ${reply.speak}")
                    delay(1500) // Wait for TTS to finish
                }
            } catch (e: Exception) {
                tts.speak("Error.")
                JarvisLogger.log("ERROR: ${e.message}")
            } finally {
                busy = false
                recorder.unmuteMic()
            }
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "JARVIS::wake")
        wakeLock?.acquire(10 * 60 * 1000L)
    }

    override fun onDestroy() {
        scope.cancel(); recorder.stop(); tts.shutdown()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object { const val ACTION_START = "jarvis.start"; const val ACTION_STOP = "jarvis.stop" }
}
