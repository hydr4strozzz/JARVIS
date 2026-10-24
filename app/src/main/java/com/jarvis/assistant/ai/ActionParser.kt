package com.jarvis.assistant.ai

import com.google.gson.Gson
import com.google.gson.JsonObject

data class LlmReply(val speak: String, val action: String?, val params: JsonObject?)

object ActionParser {
    private val gson = Gson()

    fun parse(raw: String): LlmReply {
        val json = extractJson(raw)
        return try {
            val obj = gson.fromJson(json, JsonObject::class.java)
            val speak = obj.get("speak")?.asString ?: ""
            val action = obj.get("action")?.asString?.takeIf { it != "null" && it.isNotBlank() }
            val params = obj.get("params")?.takeIf { it.isJsonObject }?.asJsonObject
            LlmReply(speak, action, params)
        } catch (e: Exception) {
            LlmReply(raw.take(300), null, null)
        }
    }

    private fun extractJson(raw: String): String {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        return if (start >= 0 && end > start) raw.substring(start, end + 1) else "{}"
    }
}
