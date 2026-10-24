package com.jarvis.assistant

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.jarvis.assistant.ai.LlmRouter
import com.jarvis.assistant.actions.ActionExecutor
import com.jarvis.assistant.databinding.ActivityMainBinding
import com.jarvis.assistant.service.JarvisForegroundService
import com.jarvis.assistant.util.JarvisLogger
import com.jarvis.assistant.util.Prefs
import com.jarvis.assistant.voice.TtsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private var isRunning = false
    private lateinit var router: LlmRouter
    private val executor by lazy { ActionExecutor(this) }
    private val tts by lazy { TtsManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        router = LlmRouter(this)

        lifecycleScope.launch {
            JarvisLogger.statusFlow.collect { status -> b.statusText.text = status }
        }

        b.btnSettings.setOnClickListener { showSettingsDialog() }
        b.btnPerms.setOnClickListener { requestPerms() }
        b.btnAccessibility.setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        
        b.btnBattery.setOnClickListener {
            // Changed from Battery to Overlay to prevent crash
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
            } else {
                JarvisLogger.log("Overlay already granted!")
            }
        }

        b.orb.setOnClickListener {
            val i = Intent(this, JarvisForegroundService::class.java)
            if (!isRunning) {
                if (!Settings.canDrawOverlays(this)) {
                    JarvisLogger.log("Grant Overlay permission first!")
                    return@setOnClickListener
                }
                i.action = JarvisForegroundService.ACTION_START
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i) else startService(i)
                isRunning = true
                startPulse()
            } else {
                i.action = JarvisForegroundService.ACTION_STOP
                startService(i)
                isRunning = false
                b.orb.animate().cancel()
                b.orb.scaleX = 1f; b.orb.scaleY = 1f
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

    private fun showSettingsDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(50, 30, 50, 10) }
        val etGroq = EditText(this).apply { hint = "Groq API Key (gsk_...)"; inputType = InputType.TYPE_CLASS_TEXT; setText(Prefs.getGroqKey(this@MainActivity)); setSingleLine() }
        layout.addView(etGroq)
        val etGemini = EditText(this).apply { hint = "Gemini API Key (AIzaSy...)"; inputType = InputType.TYPE_CLASS_TEXT; setText(Prefs.getGeminiKey(this@MainActivity)); setSingleLine(); setPadding(0, 30, 0, 0) }
        layout.addView(etGemini)

        AlertDialog.Builder(this)
            .setTitle("JARVIS Settings")
            .setMessage("Paste your keys.")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                Prefs.setGroqKey(this, etGroq.text.toString().trim())
                Prefs.setGeminiKey(this, etGemini.text.toString().trim())
                JarvisLogger.log("API Keys saved!")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startPulse() {
        val pulse = ObjectAnimator.ofPropertyValuesHolder(b.orb,
            PropertyValuesHolder.ofFloat("scaleX", 1.05f, 0.95f),
            PropertyValuesHolder.ofFloat("scaleY", 1.05f, 0.95f))
        pulse.duration = 1000; pulse.repeatCount = ObjectAnimator.INFINITE; pulse.repeatMode = ObjectAnimator.REVERSE; pulse.start()
    }

    private fun requestPerms() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE, Manifest.permission.SEND_SMS, Manifest.permission.READ_CONTACTS, Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) perms += Manifest.permission.POST_NOTIFICATIONS
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), 100)
    }
}
