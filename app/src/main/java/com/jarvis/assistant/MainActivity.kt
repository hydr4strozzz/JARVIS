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
import com.jarvis.assistant.service.JarvisForegroundService
import com.jarvis.assistant.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private var isRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

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
                b.status.text = "Listening... say 'Hey Jarvis'"
                isRunning = true
                startPulse()
            } else {
                i.action = JarvisForegroundService.ACTION_STOP
                startService(i)
                b.status.text = "Tap orb to start"
                isRunning = false
                b.orb.animate().cancel()
                b.orb.scaleX = 1f
                b.orb.scaleY = 1f
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
