package com.jarvis.assistant

import android.Manifest
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
        b.btnStart.setOnClickListener {
            val i = Intent(this, JarvisForegroundService::class.java)
                .setAction(JarvisForegroundService.ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                startForegroundService(i) else startService(i)
            b.status.text = "JARVIS running"
        }
        b.btnStop.setOnClickListener {
            startService(Intent(this, JarvisForegroundService::class.java)
                .setAction(JarvisForegroundService.ACTION_STOP))
            b.status.text = "Stopped"
        }
    }

    private fun requestPerms() {
        val perms = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            perms += Manifest.permission.POST_NOTIFICATIONS
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), 100)
    }
}
