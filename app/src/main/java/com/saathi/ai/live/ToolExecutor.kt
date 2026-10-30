package com.saathi.ai.live

import android.content.Context
import com.saathi.ai.access.JimiAccessibilityService
import com.saathi.ai.access.PhoneTools
import com.saathi.ai.data.SecurePrefs
import org.json.JSONObject

/**
 * Runs one tool call coming from Gemini and returns the JSON response body that
 * is sent back in the `toolResponse` message.
 */
class ToolExecutor(private val context: Context) {

    private val tools = PhoneTools(context)
    private val prefs = SecurePrefs(context)

    fun execute(name: String, args: JSONObject): String {
        val a = args
        return try {
            when (name) {
                "open_app" -> tools.openApp(a.str("name"))
                "open_url" -> tools.openUrl(a.str("url"))
                "search_youtube" -> tools.searchYouTube(a.str("query"))
                "play_on_youtube" -> tools.playOnYouTube(a.str("query"))

                "read_screen" -> tools.readScreen()
                "screen_tap" -> tools.screenTap(a.str("text"))
                "screen_tap_at" -> tools.screenTapAt(a.num("x"), a.num("y"))
                "screen_scroll" -> tools.screenScroll(a.bool("down", true))
                "screen_type" -> tools.screenType(a.str("text"))
                "screen_zoom" -> tools.screenZoom(a.bool("zoom_in", true))
                "screen_press" -> tools.screenPress(a.str("key"))
                "current_app" -> tools.currentApp()

                "call_number" -> tools.callNumber(a.str("number"))
                "call_contact" -> tools.callContact(a.str("name"))
                "send_sms" -> tools.sendSms(a.str("number"), a.str("message"))
                "read_recent_sms" -> tools.readRecentSms(a.int("limit", 5))
                "read_call_log" -> tools.readCallLog(a.int("limit", 5))
                "read_contacts" -> tools.readContacts(a.int("limit", 8))

                "toggle_flashlight" -> tools.toggleFlashlight(a.bool("on", true))
                "set_volume" -> tools.setVolume(a.int("percent", 50))
                "vibrate" -> tools.vibrate(a.int("milliseconds", 400).toLong())
                "set_alarm" -> tools.setAlarm(a.str("text"))
                "set_timer" -> tools.setTimer(a.int("seconds", 60))
                "device_status" -> tools.deviceStatus()
                "open_settings" -> tools.openSettings(a.str("page"))

                "open_camera" -> tools.openCamera()
                "take_photo" -> tools.takePhoto()
                "latest_photos" -> tools.latestPhotos(a.int("limit", 3))

                "remember" -> {
                    prefs.remember(a.str("key"), a.str("value"))
                    "Remembered ${a.str("key")}"
                }
                "recall" -> prefs.recall().ifEmpty { "Nothing remembered yet" }

                "open_youtube_and_search_play" -> {
                    val q = a.str("query")
                    val opened = tools.playOnYouTube(q)
                    val service = JimiAccessibilityService.instance
                    if (service == null) {
                        "$opened (Accessibility is off, so I could not tap the first video)"
                    } else {
                        // Give the results page a moment to render, then tap the first result.
                        Thread.sleep(2600)
                        val screen = service.dumpScreen()
                        val tapped = service.tapByText(q) ||
                            service.tapByText("Play") ||
                            service.tapByText("শুরু করুন")
                        if (tapped) "$opened and started the first result" 
                        else "$opened — first result not found, screen was: ${screen.take(200)}"
                    }
                }

                else -> "Unknown tool: $name"
            }
        } catch (t: Throwable) {
            "Error: ${t.message ?: t.javaClass.simpleName}"
        }
    }

    private fun JSONObject.str(key: String): String = optString(key, "")
    private fun JSONObject.num(key: String): Int = optDouble(key, 0.0).toInt()
    private fun JSONObject.bool(key: String, fallback: Boolean): Boolean =
        if (has(key)) optBoolean(key, fallback) else fallback
    private fun JSONObject.int(key: String, fallback: Int): Int =
        if (has(key)) optDouble(key, fallback.toDouble()).toInt() else fallback
}
