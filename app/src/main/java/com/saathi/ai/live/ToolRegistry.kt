package com.saathi.ai.live

import org.json.JSONArray
import org.json.JSONObject

/**
 * Every capability the model can call, described in the format the Live API
 * expects inside `setup.tools[].functionDeclarations[]`.
 */
object ToolRegistry {

    private fun fn(
        name: String,
        description: String,
        props: Map<String, Pair<String, String>> = emptyMap(),
        required: List<String> = emptyList()
    ): JSONObject {
        val properties = JSONObject()
        props.forEach { (key, typeAndDesc) ->
            properties.put(
                key,
                JSONObject().put("type", typeAndDesc.first).put("description", typeAndDesc.second)
            )
        }
        val parameters = JSONObject()
            .put("type", "OBJECT")
            .put("properties", properties)
        if (required.isNotEmpty()) {
            parameters.put("required", JSONArray(required))
        }

        return JSONObject()
            .put("name", name)
            .put("description", description)
            .put("parameters", parameters)
            // Blocking keeps the conversation honest: Jimi answers only after the
            // action really happened (e.g. "YouTube খুলেছি").
            .put("behavior", "BLOCKING")
    }

    /** Declarations handed to Gemini on session start. */
    fun declarations(): JSONArray {
        val list = mutableListOf<JSONObject>()

        // ---- apps & web
        list += fn(
            "open_app",
            "Open an app installed on the phone by name (e.g. YouTube, Facebook, WhatsApp, Camera, Settings).",
            mapOf("name" to ("STRING" to "App name")),
            listOf("name")
        )
        list += fn(
            "open_url",
            "Open a website in the browser.",
            mapOf("url" to ("STRING" to "Full URL, e.g. https://google.com")),
            listOf("url")
        )
        list += fn(
            "search_youtube",
            "Search YouTube for a query and open the results.",
            mapOf("query" to ("STRING" to "What to search")),
            listOf("query")
        )
        list += fn(
            "play_on_youtube",
            "Open YouTube search filtered to videos for a song/video name.",
            mapOf("query" to ("STRING" to "Song or video name")),
            listOf("query")
        )

        // ---- screen control (accessibility)
        list += fn(
            "read_screen",
            "Read the text that is currently visible on screen. Use it to understand what the user is looking at (YouTube, browser, gallery, chat, etc.).",
        )
        list += fn(
            "screen_tap",
            "Tap a visible UI element by its text/label. Example: text='Shorts', 'Subscribe', 'Search'.",
            mapOf("text" to ("STRING" to "Visible label/text to tap")),
            listOf("text")
        )
        list += fn(
            "screen_tap_at",
            "Tap at exact screen coordinates (pixels). Use only after read_screen.",
            mapOf(
                "x" to ("NUMBER" to "x pixel"),
                "y" to ("NUMBER" to "y pixel")
            ),
            listOf("x", "y")
        )
        list += fn(
            "screen_scroll",
            "Scroll the current screen up or down (YouTube, browser, gallery, lists).",
            mapOf("down" to ("BOOLEAN" to "true = scroll down, false = up")),
            listOf("down")
        )
        list += fn(
            "screen_type",
            "Type text into the focused input field on screen (search boxes, chat, forms).",
            mapOf("text" to ("STRING" to "Text to type")),
            listOf("text")
        )
        list += fn(
            "screen_zoom",
            "Pinch to zoom in or out — works in the camera, maps and browsers.",
            mapOf("zoom_in" to ("BOOLEAN" to "true = zoom in, false = zoom out")),
            listOf("zoom_in")
        )
        list += fn(
            "screen_press",
            "Press a system key: back, home, recents, notifications, quick_settings, power.",
            mapOf("key" to ("STRING" to "back | home | recents | notifications | quick_settings | power")),
            listOf("key")
        )
        list += fn("current_app", "Report which app is currently in the foreground.")

        // ---- calls & messages
        list += fn(
            "call_number",
            "Call a phone number. Use contact_lookup first if the user gave a name.",
            mapOf("number" to ("STRING" to "Phone number")),
            listOf("number")
        )
        list += fn(
            "call_contact",
            "Call a saved contact by name.",
            mapOf("name" to ("STRING" to "Contact name")),
            listOf("name")
        )
        list += fn(
            "send_sms",
            "Send an SMS to a number.",
            mapOf(
                "number" to ("STRING" to "Phone number"),
                "message" to ("STRING" to "Message body")
            ),
            listOf("number", "message")
        )
        list += fn(
            "read_recent_sms",
            "Read the latest received SMS messages.",
            mapOf("limit" to ("NUMBER" to "How many messages"))
        )
        list += fn(
            "read_call_log",
            "Read the latest call history entries.",
            mapOf("limit" to ("NUMBER" to "How many entries"))
        )
        list += fn(
            "read_contacts",
            "List saved contacts.",
            mapOf("limit" to ("NUMBER" to "How many contacts"))
        )

        // ---- device
        list += fn(
            "toggle_flashlight",
            "Turn the torch/flashlight on or off.",
            mapOf("on" to ("BOOLEAN" to "true = on")),
            listOf("on")
        )
        list += fn(
            "set_volume",
            "Set the media volume.",
            mapOf("percent" to ("NUMBER" to "0-100")),
            listOf("percent")
        )
        list += fn(
            "vibrate",
            "Vibrate the phone briefly.",
            mapOf("milliseconds" to ("NUMBER" to "Duration in ms"))
        )
        list += fn(
            "set_alarm",
            "Create an alarm. Put the full time in text, e.g. '7:30 am'. Also speak it aloud.",
            mapOf("text" to ("STRING" to "Alarm description/time")),
            listOf("text")
        )
        list += fn(
            "set_timer",
            "Start a countdown timer.",
            mapOf("seconds" to ("NUMBER" to "Duration in seconds")),
            listOf("seconds")
        )
        list += fn("device_status", "Battery level, charging state, network and Android version.")
        list += fn(
            "open_settings",
            "Open a system settings page: wifi, bluetooth, battery, display, sound, apps, accessibility, location.",
            mapOf("page" to ("STRING" to "settings page")),
            listOf("page")
        )

        // ---- camera & photos
        list += fn("open_camera", "Open the camera app.")
        list += fn("take_photo", "Open the camera ready to take a photo. Then use screen_tap to press the shutter if the user asks.")
        list += fn(
            "latest_photos",
            "List the most recent photos in the gallery.",
            mapOf("limit" to ("NUMBER" to "How many photos"))
        )

        // ---- memory
        list += fn(
            "remember",
            "Save an important fact about the user so it is remembered in future sessions.",
            mapOf(
                "key" to ("STRING" to "Short label"),
                "value" to ("STRING" to "The fact")
            ),
            listOf("key", "value")
        )
        list += fn("recall", "Read back everything Jimi has remembered about the user.")

        // ---- misc
        list += fn(
            "open_youtube_and_search_play",
            "One-shot macro: open YouTube, search for a song, and tap the first video result. Use this when the user says 'ইউটিউবে এই গানটা চালাও'.",
            mapOf("query" to ("STRING" to "Song/video name")),
            listOf("query")
        )

        return JSONArray(list)
    }

    /** Tool descriptions the narrator hands to the model as a quick reference. */
    const val TOOL_GUIDE = """
        তুমি ফোনের ভেতরে থাকো এবং নিচের টুলগুলো দিয়ে সত্যিকারের কাজ করতে পারো:
        open_app, open_url, search_youtube, play_on_youtube, read_screen, screen_tap,
        screen_tap_at, screen_scroll, screen_type, screen_zoom, screen_press, current_app,
        call_number, call_contact, send_sms, read_recent_sms, read_call_log, read_contacts,
        toggle_flashlight, set_volume, vibrate, set_alarm, set_timer, device_status,
        open_settings, open_camera, take_photo, latest_photos, remember, recall.
        কাজ করার আগে দরকার হলে read_screen দিয়ে স্ক্রিন পড়ো, তারপর কাজ করো।
        কাজ শেষ হলে ছোট করে জানাও কী করেছ।
    """
}
