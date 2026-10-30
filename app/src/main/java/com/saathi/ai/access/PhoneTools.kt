package com.saathi.ai.access

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.provider.AlarmClock
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

/**
 * The real-world actions behind Jimi's function-calling tools.
 * Every function is defensive: it returns a short human readable result that is
 * sent straight back to the model as the tool response.
 */
class PhoneTools(private val context: Context) {

    private val YOUTUBE_PKG = "com.google.android.youtube"

    // ---------------------------------------------------------------- apps & web

    fun openApp(name: String): String {
        val pm = context.packageManager
        val query = name.lowercase().trim()
        val known = mapOf(
            "youtube" to "com.google.android.youtube",
            "ইউটিউব" to "com.google.android.youtube",
            "facebook" to "com.facebook.katana",
            "ফেসবুক" to "com.facebook.katana",
            "whatsapp" to "com.whatsapp",
            "হোয়াটসঅ্যাপ" to "com.whatsapp",
            "instagram" to "com.instagram.android",
            "chrome" to "com.android.chrome",
            "gmail" to "com.google.android.gm",
            "maps" to "com.google.android.apps.maps",
            "ম্যাপ" to "com.google.android.apps.maps",
            "play store" to "com.android.vending",
            "camera" to "com.android.camera",
            "ক্যামেরা" to "com.android.camera",
            "settings" to "com.android.settings",
            "সেটিংস" to "com.android.settings",
            "gallery" to "com.google.android.apps.photos"
        )

        known[query]?.let { pkg ->
            launchPackage(pkg)?.let { return it }
        }

        // Try to match an installed app by label.
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(intent, 0)
        apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(query) }?.let {
            launchPackage(it.activityInfo.packageName)?.let { r -> return r }
        }

        // Fall back to a web search.
        return openUrl("https://www.google.com/search?q=" + Uri.encode(name))
    }

    private fun launchPackage(pkg: String): String? {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            ?: return null
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            "Opened $pkg"
        } catch (t: Throwable) {
            null
        }
    }

    fun openUrl(url: String): String {
        val fixed = if (url.startsWith("http")) url else "https://$url"
        return try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(fixed)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            "Opened $fixed"
        } catch (t: Throwable) {
            "Could not open the link"
        }
    }

    /**
     * Opens the YouTube **app** (not the browser) on a search. The old version used
     * an https:// intent, which on many phones hands off to Chrome — the user saw
     * "it keeps going to YouTube and coming back". Now: app first, browser as a
     * last resort, and only ever one launch per call.
     */
    fun searchYouTube(query: String): String = launchYouTubeSearch(query, filterVideos = false)

    fun playOnYouTube(query: String): String = launchYouTubeSearch(query, filterVideos = true)

    private fun launchYouTubeSearch(query: String, filterVideos: Boolean): String {
        val q = query.trim()
        if (q.isEmpty()) return "কী খুঁজতে হবে বলো।"
        val encoded = Uri.encode(q)
        val filter = if (filterVideos) "&sp=EgIQAQ%253D%253D" else ""

        val attempts = listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube://results?search_query=$encoded$filter"))
                .setPackage(YOUTUBE_PKG)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$encoded$filter"))
                .setPackage(YOUTUBE_PKG)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$encoded$filter"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        for (intent in attempts) {
            if (runCatching { context.startActivity(intent) }.isSuccess) {
                return if (filterVideos) "ইউটিউবে «$q» খুঁজছি — প্রথম ভিডিও চালানোর চেষ্টা করব"
                else "ইউটিউবে «$q» খুঁজছি"
            }
        }
        return "ইউটিউব খোলা গেল না।"
    }

    fun openBrowser(url: String): String = openUrl(url)

    // ---------------------------------------------------------------- calls & sms

    fun callNumber(number: String, direct: Boolean = true): String {
        val clean = number.replace(Regex("[^0-9+]"), "")
        if (clean.length < 3) return "Invalid number"
        val uri = Uri.parse("tel:$clean")
        val intent = if (direct && hasPermission(Manifest.permission.CALL_PHONE)) {
            Intent(Intent.ACTION_CALL, uri)
        } else {
            Intent(Intent.ACTION_DIAL, uri)
        }
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            if (direct) "Calling $clean" else "Dialing $clean"
        } catch (t: Throwable) {
            "Could not start the call"
        }
    }

    /** Looks a contact up by name and calls the first matching number. */
    fun callContact(name: String): String {
        val number = findContactNumber(name) ?: return "No contact found named $name"
        return callNumber(number)
    }

    fun findContactNumber(name: String): String? {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) return null
        val resolver: ContentResolver = context.contentResolver
        val uri = Uri.withAppendedPath(
            ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(name)
        )
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        resolver.query(uri, projection, null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        return null
    }

    fun sendSms(number: String, message: String): String {
        if (!hasPermission(Manifest.permission.SEND_SMS)) return "SMS permission is not granted"
        return try {
            val mgr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            mgr.sendTextMessage(number, null, message, null, null)
            "SMS sent to $number"
        } catch (t: Throwable) {
            "Could not send SMS: ${t.message}"
        }
    }

    fun readRecentSms(limit: Int = 5): String {
        if (!hasPermission(Manifest.permission.READ_SMS)) return "SMS read permission is not granted"
        val uri = Uri.parse("content://sms/inbox")
        val projection = arrayOf("address", "body", "date")
        val sb = StringBuilder()
        context.contentResolver.query(
            uri, projection, null, null, "date DESC"
        )?.use { c ->
            var i = 0
            while (c.moveToNext() && i < limit) {
                val addr = c.getString(0) ?: "unknown"
                val body = (c.getString(1) ?: "").take(120)
                sb.append("$addr: $body\n")
                i++
            }
        }
        return sb.toString().ifEmpty { "No messages" }
    }

    fun readCallLog(limit: Int = 5): String {
        if (!hasPermission(Manifest.permission.READ_CALL_LOG)) return "Call log permission is not granted"
        val uri = Uri.parse("content://call_log/calls")
        val projection = arrayOf("name", "number", "type", "date")
        val sb = StringBuilder()
        context.contentResolver.query(
            uri, projection, null, null, "date DESC"
        )?.use { c ->
            var i = 0
            while (c.moveToNext() && i < limit) {
                val name = c.getString(0) ?: c.getString(1) ?: "unknown"
                val type = when (c.getInt(2)) {
                    1 -> "incoming"
                    2 -> "outgoing"
                    3 -> "missed"
                    else -> "call"
                }
                sb.append("$name ($type)\n")
                i++
            }
        }
        return sb.toString().ifEmpty { "Call log is empty" }
    }

    fun readContacts(limit: Int = 8): String {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) return "Contacts permission is not granted"
        val uri = ContactsContract.Contacts.CONTENT_URI
        val sb = StringBuilder()
        context.contentResolver.query(uri, arrayOf(ContactsContract.Contacts.DISPLAY_NAME), null, null, null)
            ?.use { c ->
                var i = 0
                while (c.moveToNext() && i < limit) {
                    c.getString(0)?.let { sb.append(it).append('\n') }
                    i++
                }
            }
        return sb.toString().ifEmpty { "No contacts" }
    }

    // ---------------------------------------------------------------- device

    fun toggleFlashlight(on: Boolean): String {
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull { camId ->
                cm.getCameraCharacteristics(camId)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return "No flashlight on this phone"
            cm.setTorchMode(id, on)
            if (on) "Flashlight on" else "Flashlight off"
        } catch (t: Throwable) {
            "Could not switch the flashlight"
        }
    }

    fun setVolume(percent: Int): String {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        val target = (max * (percent.coerceIn(0, 100) / 100f)).toInt()
        am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, target, 0)
        return "Volume set to $percent%"
    }

    fun vibrate(ms: Long = 400): String {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                as android.os.VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        }
        v.vibrate(ms)
        return "Vibrating"
    }

    fun setAlarm(text: String): String {
        val intent = try {
            Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_MESSAGE, text)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } catch (t: Throwable) {
            return "Could not set the alarm"
        }
        return try {
            context.startActivity(intent)
            "Alarm set"
        } catch (t: Throwable) {
            "Could not set the alarm"
        }
    }

    fun setTimer(seconds: Int): String {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds.coerceAtLeast(1))
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            "Timer set for $seconds seconds"
        } catch (t: Throwable) {
            "Could not set the timer"
        }
    }

    fun deviceStatus(): String {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val bat = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = bat?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val charging = bat?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ==
            android.os.BatteryManager.BATTERY_STATUS_CHARGING
        return buildString {
            append("Battery: ")
            append(if (level >= 0) "$level%" else "unknown")
            if (charging) append(" (charging)")
            append(", Network: ")
            append(tm.networkOperatorName.ifBlank { "unknown" })
            append(", Android ")
            append(Build.VERSION.RELEASE)
            append(", Device ")
            append("${Build.MANUFACTURER} ${Build.MODEL}")
        }
    }

    // ---------------------------------------------------------------- camera & gallery

    fun openCamera(): String = openApp("camera")

    /** Opens the system camera in photo mode and asks it to take one shot. */
    fun takePhoto(): String {
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            "Camera opened — Bhai, your turn to say 'cheese'. Tap the shutter or ask me to tap it."
        } catch (t: Throwable) {
            "Could not open the camera"
        }
    }

    fun latestPhotos(limit: Int = 3): String {
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.DATE_ADDED)
        val sb = StringBuilder()
        context.contentResolver.query(uri, projection, null, null, "date_added DESC")?.use { c ->
            var i = 0
            while (c.moveToNext() && i < limit) {
                sb.append(c.getString(0) ?: "photo").append('\n')
                i++
            }
        }
        return sb.toString().ifEmpty { "No photos found" }
    }

    fun shareFile(path: String): String {
        return try {
            val file = File(path)
            if (!file.exists()) return "File not found"
            val uri: Uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                file
            )
            context.startActivity(
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            "Sharing ${file.name}"
        } catch (t: Throwable) {
            "Could not share the file"
        }
    }

    // ---------------------------------------------------------------- system settings shortcuts

    fun openSettings(which: String): String {
        val action = when (which.lowercase()) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound", "volume" -> Settings.ACTION_SOUND_SETTINGS
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return try {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "Opened settings"
        } catch (t: Throwable) {
            "Could not open settings"
        }
    }

    // ---------------------------------------------------------------- screen control (needs accessibility)

    fun screenTap(text: String): String {
        val service = JimiAccessibilityService.instance
            ?: return "Accessibility is off — please enable Jimi in Settings → Accessibility"
        return if (service.tapByText(text)) "Tapped \"$text\""
        else "Could not find \"$text\" on screen"
    }

    fun screenTapAt(x: Int, y: Int): String {
        val service = JimiAccessibilityService.instance
            ?: return "Accessibility is off — please enable Jimi in Settings → Accessibility"
        return if (service.tap(x.toFloat(), y.toFloat())) "Tapped at $x,$y" else "Tap failed"
    }

    fun screenScroll(down: Boolean): String {
        val service = JimiAccessibilityService.instance
            ?: return "Accessibility is off — please enable Jimi in Settings → Accessibility"
        return if (service.scroll(down)) {
            if (down) "Scrolled down" else "Scrolled up"
        } else "Could not scroll"
    }

    fun screenType(text: String): String {
        val service = JimiAccessibilityService.instance
            ?: return "Accessibility is off"
        return if (service.typeText(text)) "Typed \"$text\"" else "No text field found"
    }

    fun screenZoom(zoomIn: Boolean): String {
        val service = JimiAccessibilityService.instance
            ?: return "Accessibility is off"
        return if (service.pinch(zoomIn)) {
            if (zoomIn) "Zoomed in" else "Zoomed out"
        } else "Could not zoom"
    }

    fun screenPress(key: String): String {
        val service = JimiAccessibilityService.instance
            ?: return "Accessibility is off"
        val action = when (key.lowercase()) {
            "back" -> AccessibilityService.GLOBAL_ACTION_BACK
            "home" -> AccessibilityService.GLOBAL_ACTION_HOME
            "recents" -> AccessibilityService.GLOBAL_ACTION_RECENTS
            "notifications" -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings" -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
            "power" -> AccessibilityService.GLOBAL_ACTION_POWER_DIALOG
            else -> return "Unknown key $key"
        }
        return if (service.globalAction(action)) "Pressed $key" else "Could not press $key"
    }


    fun screenLongPress(text: String): String {
        val service = JimiAccessibilityService.instance ?: return "Accessibility বন্ধ"
        val bounds = service.boundsOf(text) ?: return "\"$text\" স্ক্রিনে পাওয়া যায়নি"
        return if (service.longPress(bounds.centerX().toFloat(), bounds.centerY().toFloat()))
            "\"$text\" চেপে ধরেছি" else "লং প্রেস করা গেল না"
    }

    fun screenBounds(text: String): String {
        val service = JimiAccessibilityService.instance ?: return "Accessibility বন্ধ"
        val b = service.boundsOf(text) ?: return "পাওয়া যায়নি"
        return "${b.centerX()}, ${b.centerY()}"
    }

    fun readScreen(): String {
        val service = JimiAccessibilityService.instance
            ?: return "Accessibility is off — please enable Jimi in Settings → Accessibility"
        val dump = service.dumpScreen()
        return dump.ifEmpty { "The screen looks empty" }
    }

    fun currentApp(): String {
        val pkg = JimiAccessibilityService.lastPackage
        return if (pkg.isBlank()) "unknown" else pkg
    }

    fun takeScreenshot(): Bitmap? = null // kept for future API 34+ screenshot support

    private fun hasPermission(p: String): Boolean =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
}
