package com.jarvis.assistant.ai

import android.content.Context
import android.util.Log

class LlmRouter(private val ctx: Context) {
    fun handle(userText: String): LlmReply {
        val localReply = LocalCommandParser.parse(userText)
        if (localReply != null) return localReply

        val raw = try {
            GroqClient.ask(ctx, userText)
        } catch (e: Exception) {
            Log.w("Jarvis", "Groq failed: ${e.message}, trying Gemini")
            try { 
                GeminiClient.ask(ctx, userText) 
            } catch (e2: Exception) {
                return LlmReply("Error: ${e.message} | ${e2.message}", null, null)
            }
        }
        return ActionParser.parse(raw)
    }
}
