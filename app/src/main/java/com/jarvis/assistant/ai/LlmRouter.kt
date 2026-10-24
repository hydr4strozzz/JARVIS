package com.jarvis.assistant.ai

import android.util.Log

class LlmRouter {
    fun handle(userText: String): LlmReply {
        // GROQ FIRST FOR INSTANT SPEED, GEMINI AS FALLBACK
        val raw = try {
            GroqClient.ask(userText)
        } catch (e: Exception) {
            Log.w("Jarvis", "Groq failed: ${e.message}, falling back to Gemini")
            try { GeminiClient.ask(userText) } catch (e2: Exception) {
                return LlmReply("Network error.", null, null)
            }
        }
        return ActionParser.parse(raw)
    }
}
