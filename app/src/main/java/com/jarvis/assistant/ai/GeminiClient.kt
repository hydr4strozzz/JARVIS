package com.jarvis.assistant.ai

import com.jarvis.assistant.BuildConfig
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val SYSTEM_PROMPT = """
You are JARVIS, a personal voice assistant running on the user's Android phone.
Detect the language of the user's input and ALWAYS reply in that exact same language inside "speak".
Respond ONLY with a single JSON object, no markdown, no extra text.

Schema:
{"speak":"<what you say to user>","action":"<action or null>","params":{...}}

Available actions and their params:
- "launch_app"     {"app_name":"whatsapp"}   (or "package":"com.whatsapp")
- "open_url"       {"url":"https://..."}
- "set_volume"     {"percent":0-100}
- "media"          {"command":"play"|"pause"|"next"|"prev"}
- "call"           {"contact":"name"} or {"number":"+91..."}
- "sms"            {"to":"number","body":"text"}
- "click_text"     {"text":"visible button text"}
- "type_text"      {"text":"..."}
- "scroll"         {"direction":"up"|"down"}
- "back"           {}
- "home"           {}
- "recents"        {}
- "read_screen"    {}
- "flashlight"     {"on":true|false}
- "open_camera"  {}
- "toggle_data"  {}
- "toggle_hotspot" {}
- "none"           null

Rules:
- If user asks a question → action "none", just answer in "speak".
- If user asks to do something on the phone → pick the correct action.
- For "data on/off" use "toggle_data". For "hotspot on/off" use "toggle_hotspot".
- Keep "speak" short and natural in the user's language.
"""

    fun ask(userText: String): String {
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
                put("temperature", 0.4)
                put("maxOutputTokens", 300)
                put("responseMimeType", "application/json")
            })
        }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${BuildConfig.GEMINI_API_KEY}"
        val req = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            val str = resp.body?.string() ?: return ""
            val obj = JSONObject(str)
            val parts = obj.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
            return parts.getJSONObject(0).getString("text")
        }
    }
}
