package com.jarvis.assistant

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.jarvis.assistant.ai.LlmRouter
import com.jarvis.assistant.actions.ActionExecutor
import com.jarvis.assistant.databinding.ActivityMainBinding
import com.jarvis.assistant.service.JarvisForegroundService
import com.jarvis.assistant.util.JarvisLogger
import com.jarvis.assistant.voice.TtsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private var isRunning = false
    private val router = LlmRouter()
    private val executor by lazy { ActionExecutor(this) }
    private val tts by lazy { TtsManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        // Check API Keys
        val groqKey = BuildConfig.GROQ_API_KEY
        if (groqKey.isBlank() || groqKey.length < 10) {
            JarvisLogger.log("ERROR: GROQ API KEY IS MISSING IN BUILD.\nDid you add secrets and re-build?")
        }

        lifecycleScope.launch {
            JarvisLogger.statusFlow.collect { status ->
                b.statusText.text = status
            }
        }

        b.btnPerms.setOnClickListener { requestPerms() }
        b.btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        b.btnBattery.setOnClickListener {
            val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            i.data = Uri.parse("package:$packageName")
            startActivity(i)
        }

        b.orb.setOnClickListener {
            val i = Intent(this, JarvisForegroundService::class.java)
            if (!isRunning) {
                i.action = JarvisForegroundService.ACTION_START
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    startForegroundService(i) else startService(i)
                isRunning = true
                startPulse()
            } else {
                i.action = JarvisForegroundService.ACTION_STOP
                startService(i)
                isRunning = false
                b.orb.animate().cancel()
                b.orb.scaleX = 1f
                b.orb.scaleY = 1f
                JarvisLogger.log("Stopped")
            }
        }

        b.btnTest.setOnClickListener {
            val text = b.editCommand.text.toString().trim()
            if (text.isNotEmpty()) {
                b.editCommand.setText("")
                JarvisLogger.log("Testing: $text")
                lifecycleScope.launch {
                    try {
                        val reply = withContext(Dispatchers.IO) { router.handle(text) }
                        reply.action?.let { executor.execute(it, reply.params) }
                        if (reply.speak.isNotBlank()) {
                            tts.speak(reply.speak)
                            JarvisLogger.log("SUCCESS:\nAction: ${reply.action}\nSpeak: ${reply.speak}")
                        } else {
                            JarvisLogger.log("SUCCESS but no speak text.\nAction: ${reply.action}")
                        }
                    } catch (e: Exception) {
                        JarvisLogger.log("API ERROR: ${e.message}")
                    }
                }
            }
        }
    }

    private fun startPulse() {
        val pulse = ObjectAnimator.ofPropertyValuesHolder(
            b.orb,
            PropertyValuesHolder.ofFloat("scaleX", 1.05f, 0.95f),
            PropertyValuesHolder.ofFloat("scaleY", 1.05f, 0.95f)
        )
        pulse.duration = 1000
        pulse.repeatCount = ObjectAnimator.INFINITE
        pulse.repeatMode = ObjectAnimator.REVERSE
        pulse.start()
    }

    private fun requestPerms() {
        val perms = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            perms += Manifest.permission.POST_NOTIFICATIONS
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), 100)
    }
}
