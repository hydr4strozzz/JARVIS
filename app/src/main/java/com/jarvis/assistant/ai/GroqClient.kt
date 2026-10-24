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

object GroqClient {
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()

    private const val SYSTEM_PROMPT = """
You are JARVIS, a highly capable Android AI assistant with full phone control.
Detect language and reply in that SAME language inside "speak".
Respond ONLY with valid JSON.

Schema: {"speak":"<reply>","action":"<action>","params":{...}}

CRITICAL ACTIONS FOR FULL ACCESS:
- "launch_app"    {"app_name":"<name>"}  (Use this for 'open youtube', 'whatsapp thurakku', etc)
- "click_text"    {"text":"<text>"}      (Click any visible button or text on screen)
- "type_text"     {"text":"<text>"}      (Type text into focused field)
- "scroll"        {"direction":"down"}   (Scroll up or down)
- "back"          {}                     (Go back)
- "home"          {}                     (Go to home screen)
- "recents"       {}                     (Open recent apps)

OTHER ACTIONS:
- "open_url"      {"url":"..."}
- "set_volume"    {"percent":0-100}
- "media"         {"command":"play"|"pause"|"next"|"prev"}
- "call"          {"contact":"name"} or {"number":"..."}
- "sms"           {"to":"...","body":"..."}
- "read_screen"   {}                     (Reads what is currently on screen)
- "flashlight"    {"on":true|false}
- "open_camera"   {}
- "toggle_data"   {}
- "toggle_hotspot"{}
- "none"          null

Examples:
User: "Open WhatsApp" -> {"speak":"Opening WhatsApp","action":"launch_app","params":{"app_name":"whatsapp"}}
User: "Type hello" -> {"speak":"Typing","action":"type_text","params":{"text":"hello"}}
User: "Click send" -> {"speak":"Clicking send","action":"click_text","params":{"text":"send"}}
"""

    fun ask(ctx: Context, userText: String): String {
        var apiKey = Prefs.getGroqKey(ctx)
        if (apiKey.isBlank()) apiKey = BuildConfig.GROQ_API_KEY
        if (apiKey.isBlank()) throw Exception("Groq key missing")

        val body = JSONObject().apply {
            put("model", "llama-3.1-8b-instant")
            put("temperature", 0.1)
            put("max_tokens", 250)
            put("response_format", JSONObject().put("type", "json_object"))
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                put(JSONObject().put("role", "user").put("content", userText))
            })
        }
        val req = Request.Builder().url("https://api.groq.com/openai/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()

        client.newCall(req).execute().use { resp ->
            val str = resp.body?.string() ?: return ""
            if (!resp.isSuccessful) throw Exception("Groq HTTP ${resp.code}")
            return JSONObject(str).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        }
    }
}
