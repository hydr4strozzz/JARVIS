package com.jarvis.assistant.util

import android.content.Context

object Prefs {
    fun lastUtterance(ctx: Context): String =
        ctx.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
            .getString("last_utterance", "") ?: ""

    fun setLastUtterance(ctx: Context, s: String) {
        ctx.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
            .edit().putString("last_utterance", s).apply()
    }
}
