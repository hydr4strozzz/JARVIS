package com.jarvis.assistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JarvisAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun allNodes(): List<AccessibilityNodeInfo> {
        val roots = rootInActiveWindow ?: return emptyList()
        val out = mutableListOf<AccessibilityNodeInfo>()
        fun walk(n: AccessibilityNodeInfo?) {
            if (n == null) return
            out += n
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }
        walk(roots)
        return out
    }

    fun clickByText(text: String): Boolean {
        val target = text.lowercase()
        for (n in allNodes()) {
            val t = n.text?.toString()?.lowercase() ?: continue
            val d = n.contentDescription?.toString()?.lowercase() ?: ""
            if (t.contains(target) || d.contains(target)) {
                var cur: AccessibilityNodeInfo? = n
                while (cur != null) {
                    if (cur.isClickable) return cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    cur = cur.parent
                }
            }
        }
        return false
    }

    fun typeText(text: String): Boolean {
        val focus = findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val args = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return focus.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun scroll(down: Boolean): Boolean {
        val list = allNodes().firstOrNull { it.isScrollable } ?: return false
        val action = if (down) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                     else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        return list.performAction(action)
    }

    fun readScreen(): String {
        val sb = StringBuilder()
        for (n in allNodes()) {
            n.text?.toString()?.let { if (it.isNotBlank()) sb.append(it).append('\n') }
            n.contentDescription?.toString()?.let { if (it.isNotBlank()) sb.append(it).append('\n') }
        }
        return sb.toString()
    }

    fun back(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun home(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun recents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)

    companion object {
        @Volatile var instance: JarvisAccessibilityService? = null
        fun get(): JarvisAccessibilityService? = instance
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
