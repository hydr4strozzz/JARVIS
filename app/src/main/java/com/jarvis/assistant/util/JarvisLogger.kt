package com.jarvis.assistant.util

import kotlinx.coroutines.flow.MutableStateFlow

object JarvisLogger {
    val statusFlow = MutableStateFlow("Idle")
    fun log(msg: String) {
        statusFlow.value = msg
    }
}
