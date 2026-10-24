package com.jarvis.assistant.util

import android.content.Context

object Prefs {
    private const val PREF_NAME = "jarvis_prefs"
    private const val KEY_GROQ = "user_groq_key"
    private const val KEY_GEMINI = "user_gemini_key"

    fun getGroqKey(ctx: Context): String =
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString(KEY_GROQ, "") ?: ""

    fun setGroqKey(ctx: Context, key: String) {
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putString(KEY_GROQ, key).apply()
    }

    fun getGeminiKey(ctx: Context): String =
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString(KEY_GEMINI, "") ?: ""

    fun setGeminiKey(ctx: Context, key: String) {
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putString(KEY_GEMINI, key).apply()
    }
}
