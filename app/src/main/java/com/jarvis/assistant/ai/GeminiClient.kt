package com.jarvis.assistant.ai

import android.content.Context
import com.jarvis.assistant.BuildConfig
import com.jarvis.assistant.util.Prefs
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiClient {
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

    private const val SYSTEM_PROMPT = """
You are JARVIS, a highly capable Android AI assistant with full physical phone control.
You are NOT a standard AI, you are JARVIS with a mic, camera, and screen.
NEVER say "As an AI", "I am a language model", or "I don't have ears". 
LANGUAGE RULE: You understand Malayalam, Manglish (Malayalam in English letters), and English. ALWAYS reply in the EXACT language the user used. If they speak Manglish, reply in Manglish. If Malayalam, reply in Malayalam.

Respond ONLY with valid JSON.

Schema: {"speak":"<reply>","action":"<action>","params":{...}}

ACTIONS YOU CAN DO:
- "launch_app"    {"app_name":"<name>"}
- "click_text"    {"text":"<text>"}
- "type_text"     {"text":"<text>"}
- "scroll"        {"direction":"down"}
- "back"          {}
- "home"          {}
- "recents"       {}
- "open_url"      {"url":"..."}
- "set_volume"    {"percent":0-100}
- "media"         {"command":"play"|"pause"|"next"|"prev"}
- "call"          {"contact":"name"} or {"number":"..."}
- "sms"           {"to":"...","body":"..."}
- "read_screen"   {}
- "flashlight"    {"on":true|false}
- "open_camera"   {}
- "toggle_data"   {}
- "toggle_hotspot"{}
- "none"          null
"""

    fun ask(ctx: Context, userText: String): String {
        var apiKey = Prefs.getGeminiKey(ctx)
        if (apiKey.isBlank()) apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) throw Exception("Gemini key missing")

        val body = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", userText)))
                })
            })
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT)))
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.2)
                put("maxOutputTokens", 300)
                put("responseMimeType", "application/json")
            })
        }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
        val req = Request.Builder().url(url).post(body.toString().toRequestBody("application/json".toMediaType())).build()

        client.newCall(req).execute().use { resp ->
            val str = resp.body?.string() ?: return ""
            return JSONObject(str).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
        }
    }
}
