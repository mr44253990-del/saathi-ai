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



        // ---- human-like gestures (multi-touch, drag, hold)
        list += fn(
            "gesture_drag",
            "Drag from one point to another with a realistic finger arc. Use for sliders, scrub bars, panning maps, moving a photo in an editor.",
            mapOf(
                "from_x" to ("NUMBER" to "Start X"), "from_y" to ("NUMBER" to "Start Y"),
                "to_x" to ("NUMBER" to "End X"), "to_y" to ("NUMBER" to "End Y"),
                "ms" to ("NUMBER" to "Duration in ms, default 340")
            ),
            listOf("from_x", "from_y", "to_x", "to_y")
        )
        list += fn(
            "gesture_hold_drag",
            "Press and hold, then drag — reorder lists, move a widget, drag a slider that needs a long press first.",
            mapOf(
                "from_x" to ("NUMBER" to "Start X"), "from_y" to ("NUMBER" to "Start Y"),
                "to_x" to ("NUMBER" to "End X"), "to_y" to ("NUMBER" to "End Y")
            ),
            listOf("from_x", "from_y", "to_x", "to_y")
        )
        list += fn(
            "gesture_drag_element",
            "Drag the element that shows this text by dx/dy pixels. Perfect for the crop/zoom slider in a photo editor.",
            mapOf(
                "text" to ("STRING" to "Visible label of the element"),
                "dx" to ("NUMBER" to "Horizontal pixels"),
                "dy" to ("NUMBER" to "Vertical pixels")
            ),
            listOf("text")
        )
        list += fn(
            "gesture_pinch",
            "Two-finger pinch to zoom in or out (photos, maps, camera).",
            mapOf("zoom_in" to ("BOOLEAN" to "true = zoom in"))
        )
        list += fn(
            "gesture_swipe",
            "One-finger swipe in a direction.",
            mapOf("direction" to ("STRING" to "up | down | left | right"))
        )
        list += fn(
            "gesture_two_finger_scroll",
            "Two-finger scroll — needed inside map/photo editors where a one-finger drag moves content instead of scrolling.",
            mapOf("down" to ("BOOLEAN" to "true = scroll down"))
        )
        list += fn(
            "gesture_rotate",
            "Two-finger rotate (photo rotation, 3-D viewers).",
            mapOf("degrees" to ("NUMBER" to "Rotate by this many degrees"))
        )
        list += fn(
            "gesture_multi_finger",
            "Simultaneous multi-finger swipe/tap — 3-finger screenshot, 4-finger gestures, OEM shortcuts.",
            mapOf(
                "fingers" to ("NUMBER" to "2-10 fingers"),
                "dx" to ("NUMBER" to "Horizontal travel"),
                "dy" to ("NUMBER" to "Vertical travel"),
                "ms" to ("NUMBER" to "Duration")
            ),
            listOf("fingers")
        )
        list += fn("open_notifications", "Pull down the notification shade.")
        list += fn("back", "Press the system back button.")

        // ---- agentic
        list += fn(
            "run_goal",
            "Hand a multi-step goal to Jimi's on-device Autopilot. It then loops on its own: read the screen, find the best element, tap/type/drag, verify, and continue up to 22 steps. Use this for long tasks such as 'প্লে স্টোরে গিয়ে এই অ্যাপ ইনস্টল করো', 'গ্যালারির ছবি এডিট করে সেভ করো', 'ফেসবুকে স্টোরি দাও'.",
            mapOf("goal" to ("STRING" to "Plain-language goal in Bangla or English")),
            listOf("goal")
        )

        // ---- smart / everyday
        list += fn(
            "web_search",
            "Search the web in the browser for any question, price, news, recipe or fact.",
            mapOf("query" to ("STRING" to "Search text")),
            listOf("query")
        )
        list += fn(
            "open_weather",
            "Show today/this week's weather for a place by opening the weather search page.",
            mapOf("location" to ("STRING" to "City or area; empty for current location"))
        )
        list += fn(
            "map_search",
            "Search a place, shop, hospital, restaurant or address on Google Maps.",
            mapOf("query" to ("STRING" to "Place to find")),
            listOf("query")
        )
        list += fn(
            "play_store_search",
            "Search the Play Store for an app.",
            mapOf("query" to ("STRING" to "App name")),
            listOf("query")
        )
        list += fn(
            "upi_pay",
            "Open the UPI payment sheet with payee, amount and note pre-filled. NEVER confirms the payment; the user must approve it.",
            mapOf(
                "vpa" to ("STRING" to "UPI ID like name@okaxis"),
                "payee" to ("STRING" to "Payee display name"),
                "amount" to ("STRING" to "Amount in rupees"),
                "note" to ("STRING" to "Payment note")
            ),
            listOf("vpa")
        )
        list += fn("upi_scan", "Open the UPI scanner so the user can pay by QR code.")
        list += fn(
            "copy_text",
            "Copy text to the clipboard so the user can paste it anywhere.",
            mapOf("text" to ("STRING" to "Text to copy")),
            listOf("text")
        )
        list += fn("paste_text", "Read whatever is currently on the clipboard.")
        list += fn(
            "share_text",
            "Open the Android share sheet with this text (WhatsApp, Telegram, SMS, anything).",
            mapOf("text" to ("STRING" to "Text to share")),
            listOf("text")
        )
        list += fn(
            "medicine_reminder",
            "Set a daily medicine reminder alarm in the clock app.",
            mapOf(
                "medicine" to ("STRING" to "Medicine name and dose"),
                "time" to ("STRING" to "Time like 8:30 am or সকাল ৮টা")
            ),
            listOf("medicine", "time")
        )
        list += fn("battery_status", "Battery percentage and charging state.")
        list += fn("time_now", "Current date and time in the user's locale.")
        list += fn(
            "find_app",
            "Work out which installed app matches a name the user said (works for Bangla, English, brand names, even misspelled).",
            mapOf("name" to ("STRING" to "App name as the user said it")),
            listOf("name")
        )
        list += fn(
            "deep_link",
            "Open a specific screen inside a known app (whatsapp, instagram, facebook, tiktok, youtube, telegram, gmail, phonepe, gpay, paytm).",
            mapOf(
                "app" to ("STRING" to "App key"),
                "value" to ("STRING" to "Phone number / username / search text, optional")
            ),
            listOf("app")
        )
        list += fn(
            "long_press",
            "Press and hold an element with this exact on-screen text (for menus, delete, copy options).",
            mapOf("text" to ("STRING" to "Visible text")),
            listOf("text")
        )
        list += fn(
            "screen_bounds",
            "Get the screen coordinates of an element so you can tap it precisely.",
            mapOf("text" to ("STRING" to "Visible text")),
            listOf("text")
        )
        list += fn(
            "read_clipboard",
            "Read the clipboard (what the user last copied)."
        )
        list += fn(
            "write_clipboard",
            "Put text on the clipboard.",
            mapOf("text" to ("STRING" to "Text")),
            listOf("text")
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
        open_settings, open_camera, take_photo, latest_photos, remember, recall,
        web_search, open_weather, map_search, play_store_search, upi_pay, upi_scan,
        copy_text, paste_text, share_text, medicine_reminder, battery_status, time_now,
        find_app, deep_link, long_press, screen_bounds, read_clipboard, write_clipboard.

        কীভাবে কাজ করবে:
        • ব্যবহারকারী "একবারে পুরো কাজটা করে দাও" বললে নিজে থেকে ধাপে ধাপে সব ধাপ শেষ করো —
          প্রতিটা ধাপের পর read_screen দিয়ে দেখো কাজটা হয়েছে কি না, না হলে অন্যভাবে চেষ্টা করো।
        • নতুন/অচেনা অ্যাপ হলে: find_app → open_app → read_screen → দরকার হলে scroll/tap/type।
          মনে রাখো কোথায় কী বোতাম ছিল, পরেরবার দ্রুত করো।
        • "কপি করা টেক্সট পেস্ট করো" হলে read_clipboard → target অ্যাপে type।
        • টাকা/পেমেন্ট কখনো নিজে confirm করবে না — শুধু স্ক্রিন খুলে দেবে।
        • কাজ শেষ হলে এক-দুই বাক্যে বলো কী করেছ।
        • লম্বা/জটিল কাজে (এডিটিং, ইনস্টল, পোস্ট, ফাইল কপি) run_goal দিয়ে অটোপাইলট চালাও —
          সে নিজে ধাপে ধাপে শেষ করবে। ছোট/স্পষ্ট কাজে সরাসরি gesture_* ব্যবহার করো।
        • স্লাইডার/ক্রপ/জুম সরাতে gesture_drag বা gesture_drag_element, ছবি ঘোরাতে gesture_rotate,
          ম্যাপে gesture_two_finger_scroll — এক আঙুলে হবে না এমন জায়গায় দুই-তিন আঙুল ব্যবহার করো।
        • একই কাজ দুইবার করবে না। টুল "হয়েছে" বললে ধরে নাও কাজ হয়েছে, আবার সার্চ দিও না।
    """
}
