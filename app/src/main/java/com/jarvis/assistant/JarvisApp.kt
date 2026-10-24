package com.jarvis.assistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.jarvis.assistant.util.JarvisLogger

class JarvisApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL, "JARVIS", NotificationManager.IMPORTANCE_LOW)
            )
        }
        
        // Catch crashes and show them instead of closing the app
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            JarvisLogger.log("CRASH: ${throwable.localizedMessage}")
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
    companion object { const val CHANNEL = "jarvis_channel" }
}
