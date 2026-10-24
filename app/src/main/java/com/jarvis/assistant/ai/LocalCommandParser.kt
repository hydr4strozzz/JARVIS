package com.jarvis.assistant.ai

object LocalCommandParser {
    fun parse(text: String): LlmReply? {
        val clean = text.lowercase()
        return when {
            clean.contains("data on") || clean.contains("turn on data") || clean.contains("mobile data on") ||
            clean.contains("ഡാറ്റ ഓൺ") || clean.contains("ഡാറ്റ വേണം") || clean.contains("ഡാറ്റ ഓൺ ചെയ്യ്") ||
            clean.contains("മൊബൈൽ ഡാറ്റ ഓൺ") || clean.contains("ഡാറ്റ ഇട്") -> 
                LlmReply("Opening data settings", "toggle_data", null)

            clean.contains("data off") || clean.contains("turn off data") || clean.contains("mobile data off") ||
            clean.contains("ഡാറ്റ ഓഫ്") || clean.contains("ഡാറ്റ ഓഫ് ചെയ്യ്") || clean.contains("ഡാറ്റ ഓഫ് ആക്ക്") ||
            clean.contains("മൊബൈൽ ഡാറ്റ ഓഫ്") -> 
                LlmReply("Opening data settings", "toggle_data", null)

            clean.contains("hotspot on") || clean.contains("turn on hotspot") || clean.contains("hotspot venam") ||
            clean.contains("ഹോട്ട്സ്പോട്ട് ഓൺ") || clean.contains("ഹോട്ട്സ്പോട്ട് വേണം") || clean.contains("ഹോട്ട്സ്പോട്ട് ഓൺ ചെയ്യ്") -> 
                LlmReply("Opening hotspot settings", "toggle_hotspot", null)

            clean.contains("hotspot off") || clean.contains("turn off hotspot") ||
            clean.contains("ഹോട്ട്സ്പോട്ട് ഓഫ്") || clean.contains("ഹോട്ട്സ്പോട്ട് ഓഫ് ചെയ്യ്") || clean.contains("ഹോട്ട്സ്പോട്ട് ഓഫ് ആക്ക്") -> 
                LlmReply("Opening hotspot settings", "toggle_hotspot", null)

            else -> null
        }
    }
}
