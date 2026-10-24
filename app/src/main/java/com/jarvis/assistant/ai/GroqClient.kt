package com.jarvis.assistant.ai

import com.jarvis.assistant.BuildConfig
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GroqClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val SYSTEM_PROMPT = """
You are JARVIS, a personal voice assistant running on the user's Android phone.
Detect the language of the user's input and ALWAYS reply in that exact same language inside "speak".
Respond ONLY with a single JSON object, no markdown.

Schema:
{"speak":"<what you say to user>","action":"<action or null>","params":{...}}

Actions and params:
- "launch_app"  {"app_name":"whatsapp"} or {"package":"com.whatsapp"}
- "open_url"    {"url":"..."}
- "set_volume"  {"percent":0-100}
- "media"       {"command":"play"|"pause"|"next"|"prev"}
- "call"        {"contact":"name"} or {"number":"..."}
- "sms"         {"to":"...","body":"..."}
- "click_text"  {"text":"..."}
- "type_text"   {"text":"..."}
- "scroll"      {"direction":"up"|"down"}
- "back" {}
- "home" {}
- "recents" {}
- "read_screen" {}
- "flashlight" {"on":true|false}
- "none" null
"""

    fun ask(userText: String): String {
        val body = JSONObject().apply {
            put("model", "llama-3.3-70b-versatile")
            put("temperature", 0.4)
            put("max_tokens", 300)
            put("response_format", JSONObject().put("type", "json_object"))
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                put(JSONObject().put("role", "user").put("content", userText))
            })
        }
        val req = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .header("Authorization", "Bearer \${BuildConfig.GROQ_API_KEY}")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            val str = resp.body?.string() ?: return ""
            val obj = JSONObject(str)
            return obj.getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content")
        }
    }
}
