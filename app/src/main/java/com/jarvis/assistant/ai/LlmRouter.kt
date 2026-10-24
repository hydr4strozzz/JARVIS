package com.jarvis.assistant.ai

import android.util.Log

class LlmRouter {
    fun handle(userText: String): LlmReply {
        val raw = try {
            GeminiClient.ask(userText)
        } catch (e: Exception) {
            Log.w("Jarvis", "Gemini failed: \${e.message}")
            try { GroqClient.ask(userText) } catch (e2: Exception) {
                return LlmReply("I could not reach the server.", null, null)
            }
        }
        return ActionParser.parse(raw)
    }
}
