package com.jarvis.assistant.actions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.SmsManager
import com.google.gson.JsonObject
import com.jarvis.assistant.service.JarvisAccessibilityService
import com.jarvis.assistant.util.JarvisLogger

class ActionExecutor(private val ctx: Context) {
    fun execute(action: String, params: JsonObject?) {
        try {
            val p = params ?: JsonObject()
            when (action) {
                "launch_app"   -> launchApp(p)
                "open_url"     -> openUrl(str(p, "url"))
                "set_volume"   -> setVolume(p.get("percent")?.asInt ?: 50)
                "media"        -> media(str(p, "command"))
                "call"         -> call(p)
                "sms"          -> sms(p)
                "open_camera"  -> openCamera()
                "click_text"   -> JarvisAccessibilityService.get()?.clickByText(str(p, "text"))
                "type_text"    -> JarvisAccessibilityService.get()?.typeText(str(p, "text"))
                "scroll"       -> JarvisAccessibilityService.get()?.scroll(str(p, "direction") == "down")
                "back"         -> JarvisAccessibilityService.get()?.back()
                "home"         -> JarvisAccessibilityService.get()?.home()
                "recents"      -> JarvisAccessibilityService.get()?.recents()
                "flashlight"   -> flashlight(p.get("on")?.asBoolean ?: true)
                "toggle_data"  -> toggleData()
                "toggle_hotspot" -> toggleHotspot()
            }
        } catch (e: Exception) {
            JarvisLogger.log("Action Error: ${e.message}")
        }
    }

    private fun str(o: JsonObject, k: String): String = if (o.has(k) && !o.get(k).isJsonNull) o.get(k).asString else ""

    private fun launchApp(p: JsonObject) {
        val name = str(p, "app_name").lowercase()
        val explicit = str(p, "package")
        val pm = ctx.packageManager

        if (explicit.isNotBlank()) {
            pm.getLaunchIntentForPackage(explicit)?.let { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(it); return }
        }
        if (name.isNotBlank()) {
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val match = apps.firstOrNull { 
                pm.getApplicationLabel(it).toString().lowercase().contains(name) || 
                it.packageName.lowercase().contains(name)
            }
            match?.let { pm.getLaunchIntentForPackage(it.packageName)?.let { i -> i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(i); return } }
        }
    }

    private fun openUrl(url: String) { if (url.isNotBlank()) ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    private fun openCamera() { val i = Intent(MediaStore.ACTION_IMAGE_CAPTURE); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(i) }
    private fun setVolume(percent: Int) { val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager; am.setStreamVolume(AudioManager.STREAM_MUSIC, (percent * am.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 100), 0) }
    
    private fun media(cmd: String) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val key = when (cmd) { "play" -> android.view.KeyEvent.KEYCODE_MEDIA_PLAY; "pause" -> android.view.KeyEvent.KEYCODE_MEDIA_PAUSE; "next" -> android.view.KeyEvent.KEYCODE_MEDIA_NEXT; "prev" -> android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS; else -> return }
        am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, key)); am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, key))
    }
    
    private fun call(p: JsonObject) { 
        val num = str(p, "number"); val cont = str(p, "contact"); 
        val target = if (num.isNotBlank()) num else resolveContact(cont) ?: return; 
        ctx.startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$target")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) 
    }
    
    private fun resolveContact(name: String): String? {
        if (name.isBlank()) return null
        val cursor = ctx.contentResolver.query(android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER, android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME), null, null, null) ?: return null
        cursor.use { while (it.moveToNext()) { if (it.getString(1).lowercase().contains(name.lowercase())) return it.getString(0) } }
        return null
    }
    
    private fun sms(p: JsonObject) {
        val to = str(p, "to"); val body = str(p, "body"); if (to.isBlank() || body.isBlank()) return
        val mgr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ctx.getSystemService(SmsManager::class.java) else @Suppress("DEPRECATION") SmsManager.getDefault()
        mgr.sendTextMessage(to, null, body, null, null)
    }
    
    private fun flashlight(on: Boolean) { val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager; cm.cameraIdList.firstOrNull()?.let { cm.setTorchMode(it, on) } }
    
    private fun toggleData() {
        val i = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY) else Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(i)
    }
    
    private fun toggleHotspot() { val i = Intent(Settings.ACTION_WIRELESS_SETTINGS); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(i) }
}
