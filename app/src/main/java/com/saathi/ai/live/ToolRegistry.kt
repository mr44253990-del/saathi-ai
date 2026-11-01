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
            val prop = JSONObject()
                .put("type", typeAndDesc.first)
                .put("description", typeAndDesc.second)
            if (typeAndDesc.first == "ARRAY") {
                prop.put("items", JSONObject().put("type", "STRING"))
            }
            properties.put(key, prop)
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


        // ---- app knowledge
        list += fn(
            "launch_app",
            "Open any installed app by the name the user said (works for Bangla names, brand names, typos). Also tells you what the app is for.",
            mapOf("name" to ("STRING" to "App name")),
            listOf("name")
        )
        list += fn(
            "list_apps",
            "List installed apps with a one-line purpose each — use it when the user asks 'আমার ফোনে কী কী অ্যাপ আছে'.",
            mapOf("limit" to ("NUMBER" to "How many"))
        )
        list += fn(
            "app_purpose",
            "Explain what an app is for, from its package name.",
            mapOf("package" to ("STRING" to "Package id")),
            listOf("package")
        )
        list += fn(
            "remember_playbook",
            "Save the working step-by-step recipe for a goal inside an app, so next time is faster.",
            mapOf(
                "package" to ("STRING" to "Package id"),
                "goal" to ("STRING" to "What the user wanted"),
                "steps" to ("ARRAY" to "Ordered list of labels/actions that worked")
            ),
            listOf("package", "goal")
        )




        list += fn(
            "save_text",
            "Write text or code Jimi produced into a real file (Downloads/Jimi) and optionally open the share sheet. Use this whenever the user asks for code, a list, a letter, or notes — do not only speak it.",
            mapOf(
                "fileName" to ("STRING" to "File name, e.g. game.py"),
                "content" to ("STRING" to "Full text/code to write"),
                "share" to ("BOOLEAN" to "true = open share sheet after saving")
            ),
            listOf("fileName", "content")
        )

        // ---- offline packs
        list += fn(
            "offline_packs",
            "List the pre-compiled offline action packs (YouTube play, WhatsApp message, Telegram voice note, FB story, TikTok upload, gallery edit, recharge, call contact, real carrom, file copy, settings, downloads)."
        )
        list += fn(
            "run_pack",
            "Run a whole offline pack on-device in ONE call — much faster than stepping tool by tool. Use for the everyday flows. Returns an honest report with proof from the screen.",
            mapOf(
                "pack" to ("STRING" to "Pack id, e.g. youtube_play"),
                "arg" to ("STRING" to "Argument; use name|text for two-part flows")
            ),
            listOf("pack")
        )

        // ---- media understanding
        list += fn(
            "describe_image",
            "Look at an image on the phone (path or content:// URI) and describe it: size, orientation, lighting, dominant colours. The frame is also sent to the live model so it can answer questions about the content.",
            mapOf("path" to ("STRING" to "Image path or URI")),
            listOf("path")
        )
        list += fn(
            "video_info",
            "Read a video's duration, resolution and bitrate.",
            mapOf("path" to ("STRING" to "Video path")),
            listOf("path")
        )
        list += fn(
            "video_frames",
            "Extract still frames from a video and show them to the model, so Jimi can actually watch the clip.",
            mapOf("path" to ("STRING" to "Video path"), "frames" to ("NUMBER" to "1-6")),
            listOf("path")
        )
        list += fn(
            "audio_info",
            "Read an audio file's title, artist, length and format.",
            mapOf("path" to ("STRING" to "Audio path")),
            listOf("path")
        )

        // ---- files
        list += fn(
            "list_files",
            "List a folder on the phone (downloads, dcim, pictures, movies, music, documents or any visible folder).",
            mapOf("path" to ("STRING" to "Folder name or full path"), "limit" to ("NUMBER" to "How many entries")),
            listOf("path")
        )
        list += fn(
            "find_file",
            "Search the phone for a file by name.",
            mapOf("query" to ("STRING" to "File name fragment")),
            listOf("query")
        )
        list += fn("file_info", "Size, path and modification time of a file.", mapOf("path" to ("STRING" to "File path")), listOf("path"))
        list += fn(
            "copy_file",
            "Copy a file into a folder (works inside Jimi-readable folders; otherwise use copy_file_via_ui).",
            mapOf("path" to ("STRING" to "Source file"), "to" to ("STRING" to "Destination folder")),
            listOf("path", "to")
        )
        list += fn(
            "move_file",
            "Move a file into a folder.",
            mapOf("path" to ("STRING" to "Source file"), "to" to ("STRING" to "Destination folder")),
            listOf("path", "to")
        )
        list += fn("delete_file", "Delete a file.", mapOf("path" to ("STRING" to "File path")), listOf("path"))
        list += fn(
            "new_folder",
            "Create a folder.",
            mapOf("in" to ("STRING" to "Parent folder"), "name" to ("STRING" to "New folder name")),
            listOf("name")
        )
        list += fn("share_file", "Share a file through the Android share sheet.", mapOf("path" to ("STRING" to "File path")), listOf("path"))
        list += fn("open_files_app", "Open the system Files app.", mapOf("folder" to ("STRING" to "Optional folder")))
        list += fn(
            "copy_file_via_ui",
            "Copy a file using the Files app UI (long-press → copy → open folder → paste). Use this for folders Android blocks direct access to.",
            mapOf("file" to ("STRING" to "Visible file name"), "folder" to ("STRING" to "Destination folder name")),
            listOf("file", "folder")
        )



        // ---- human-realism + verification
        list += fn(
            "human_tap",
            "Tap with human jitter and a natural press length. Use for checkboxes, 'I am not a robot' style widgets, small buttons.",
            mapOf("x" to ("NUMBER" to "X"), "y" to ("NUMBER" to "Y")),
            listOf("x", "y")
        )
        list += fn(
            "human_scroll",
            "Scroll like a thumb: short strokes with pauses, in bursts.",
            mapOf("down" to ("BOOLEAN" to "true = down"), "bursts" to ("NUMBER" to "1-6 strokes"))
        )
        list += fn(
            "human_slider",
            "Move a slider/seek bar to a percentage, with human acceleration.",
            mapOf("text" to ("STRING" to "Slider label"), "percent" to ("NUMBER" to "0-100")),
            listOf("text", "percent")
        )
        list += fn(
            "deliberate_tap",
            "Approach the target, pause, then press — for captchas and confirmation toggles.",
            mapOf("x" to ("NUMBER" to "X"), "y" to ("NUMBER" to "Y")),
            listOf("x", "y")
        )
        list += fn("wander", "Small exploratory nudges left/right/up/down, like a person looking around.")
        list += fn(
            "verify_task",
            "Check the screen for the expected result before telling the user the task is done. ALWAYS call this before saying 'শেষ'.",
            mapOf("keywords" to ("ARRAY" to "Words that should now appear on screen")),
            listOf("keywords")
        )
        list += fn(
            "open_carrom",
            "Open a real carrom board — an installed carrom game, Play Store, or a web board."
        )
        list += fn(
            "strike_carrom",
            "Play ONE real strike on the on-screen carrom board: Jimi reads the board, computes the angle to a pocket, and flicks the striker. Call it repeatedly to play a full game."
        )

        // ---- games
        list += fn(
            "game_start",
            "Start a board game you play with the user: ludo (লুডু), chess (দাবা) or carrom (ক্যারম).",
            mapOf("game" to ("STRING" to "ludo | chess | carrom")),
            listOf("game")
        )
        list += fn(
            "game_move",
            "Make one move. Ludo: pass roll/token. Chess: pass a move like e2e4. Carrom: pass dx/aim/power.",
            mapOf(
                "game" to ("STRING" to "ludo | chess | carrom"),
                "roll" to ("NUMBER" to "Ludo dice 1-6, optional"),
                "token" to ("NUMBER" to "Ludo token 0-3"),
                "move" to ("STRING" to "Chess move, e.g. e2e4"),
                "dx" to ("NUMBER" to "Carrom striker shift %"),
                "aim" to ("NUMBER" to "Carrom aim degrees"),
                "power" to ("NUMBER" to "Carrom power %")
            ),
            listOf("game")
        )
        list += fn(
            "game_board",
            "Show the current board / state of the game being played.",
            mapOf("game" to ("STRING" to "ludo | chess | carrom")),
            listOf("game")
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


        // ================================================================ v2
        // ---- root / system level (the escalation path when a permission blocks work)
        list += fn(
            "root_status",
            "Check whether root (su) is available and what Jimi can therefore do. Call this before assuming something is impossible because of permissions."
        )
        list += fn(
            "root_exec",
            "Run a shell command as root (su). Use it when normal permissions block the work: pm/settings/am/input commands, reading protected files, changing system config. Always report the real output.",
            mapOf(
                "cmd" to ("STRING" to "Command line, e.g. 'settings put system screen_off_timeout 600000'"),
                "timeout" to ("NUMBER" to "Timeout in ms (default 25000)"),
                "root" to ("BOOLEAN" to "true to use su (default), false for a plain shell")
            ),
            listOf("cmd")
        )
        list += fn(
            "root_grant_self",
            "Grant Jimi its own dangerous permissions through root (pm grant), so the user does not have to visit Settings one by one."
        )
        list += fn(
            "root_enable_accessibility",
            "Enable Jimi's accessibility service through root (settings put secure) when the user cannot or will not toggle it manually."
        )
        list += fn(
            "root_default_assistant",
            "Set Jimi as the default digital assistant through root."
        )
        list += fn(
            "system_setting_put",
            "Change a deep system setting through root: 'settings put <namespace> <key> <value>'. Examples: screen_off_timeout, stay_on_while_plugged_in, show_touches, animation scales, default IME.",
            mapOf(
                "namespace" to ("STRING" to "system | secure | global"),
                "key" to ("STRING" to "Setting key"),
                "value" to ("STRING" to "New value")
            ),
            listOf("key", "value")
        )
        list += fn(
            "system_setting_get",
            "Read a deep system setting through root ('settings get').",
            mapOf(
                "namespace" to ("STRING" to "system | secure | global"),
                "key" to ("STRING" to "Setting key")
            ),
            listOf("key")
        )
        list += fn(
            "root_input",
            "Inject touch/keyboard input through root ('input tap|swipe|text|keyevent'). Works even in apps and fields where accessibility injection is blocked.",
            mapOf(
                "action" to ("STRING" to "tap | swipe | text | key"),
                "x" to ("NUMBER" to "x for tap"),
                "y" to ("NUMBER" to "y for tap"),
                "x1" to ("NUMBER" to "swipe start x"), "y1" to ("NUMBER" to "swipe start y"),
                "x2" to ("NUMBER" to "swipe end x"), "y2" to ("NUMBER" to "swipe end y"),
                "ms" to ("NUMBER" to "swipe duration ms"),
                "text" to ("STRING" to "text to type"),
                "key" to ("STRING" to "key name, e.g. BACK, HOME, ENTER, POWER")
            ),
            listOf("action")
        )
        list += fn(
            "root_clipboard",
            "Write to the clipboard through root — reaches protected/third-party fields where the normal clipboard API is refused.",
            mapOf("text" to ("STRING" to "Text to put on the clipboard")),
            listOf("text")
        )
        list += fn(
            "root_radio",
            "Turn wifi / mobile data / airplane mode / torch on or off through root.",
            mapOf(
                "what" to ("STRING" to "wifi | data | airplane | torch"),
                "on" to ("BOOLEAN" to "true = on, false = off")
            ),
            listOf("what")
        )
        list += fn(
            "app_control",
            "Uninstall, freeze/disable, unfreeze/enable, clear data or force-stop an app through root. Ask before anything destructive.",
            mapOf(
                "action" to ("STRING" to "uninstall | freeze | unfreeze | clear | stop"),
                "name" to ("STRING" to "Spoken app name (resolved automatically)"),
                "package" to ("STRING" to "Exact package name if you already know it")
            ),
            listOf("action")
        )
        list += fn(
            "install_apk",
            "Install an APK file silently through root ('pm install -r -g').",
            mapOf("path" to ("STRING" to "Absolute path of the APK")),
            listOf("path")
        )
        list += fn(
            "system_info",
            "Read deep device state through root: build props, disk usage, running processes, or logcat.",
            mapOf(
                "kind" to ("STRING" to "props | storage | processes | logcat"),
                "lines" to ("NUMBER" to "logcat lines (default 60)"),
                "filter" to ("STRING" to "optional logcat grep filter")
            )
        )
        list += fn(
            "root_file",
            "Read or write any file through root, including paths normal apps cannot touch.",
            mapOf(
                "action" to ("STRING" to "read | write"),
                "path" to ("STRING" to "Absolute path"),
                "content" to ("STRING" to "Text to write"),
                "limit" to ("NUMBER" to "Max chars to read (default 4000)")
            ),
            listOf("action", "path")
        )

        // ---- real terminal work (shell + Termux), including reading results back
        list += fn(
            "shell",
            "Run a command in a real shell (root when available) and get stdout/stderr back, so you can read the result and keep going.",
            mapOf(
                "cmd" to ("STRING" to "Command line"),
                "timeout" to ("NUMBER" to "Timeout ms (default 25000)"),
                "root" to ("BOOLEAN" to "Prefer su (default true)")
            ),
            listOf("cmd")
        )
        list += fn(
            "shell_background",
            "Start a long-running command in the background and get a job id; read its output later with shell_job_output.",
            mapOf(
                "cmd" to ("STRING" to "Command line"),
                "tag" to ("STRING" to "Short label for the job")
            ),
            listOf("cmd")
        )
        list += fn(
            "shell_job_output",
            "Read the tail of a background shell job's output.",
            mapOf(
                "id" to ("NUMBER" to "Job id from shell_background"),
                "tail" to ("NUMBER" to "Lines to return (default 40)")
            ),
            listOf("id")
        )
        list += fn("shell_jobs", "List the background shell jobs Jimi started and whether they are still running.")
        list += fn(
            "shell_drive",
            "Drive a shell conversation yourself: run a command, read the output, decide the next command, repeat — up to N rounds. Use for interactive tools (top, sqlite3, python, ffmpeg probes).",
            mapOf(
                "cmd" to ("STRING" to "First command"),
                "rounds" to ("NUMBER" to "Max follow-up rounds (default 4)")
            ),
            listOf("cmd")
        )
        list += fn(
            "termux_run",
            "Run a command inside Termux (com.termux.RUN_COMMAND). Use when the work needs Termux's real environment: python, ffmpeg, git, node, pip packages.",
            mapOf(
                "cmd" to ("STRING" to "Executable path or command, e.g. /data/data/com.termux/files/usr/bin/python"),
                "workdir" to ("STRING" to "Working directory (optional)"),
                "background" to ("BOOLEAN" to "true = background session (default)")
            ),
            listOf("cmd")
        )
        list += fn(
            "termux_result",
            "Read the last result Termux sent back (stdout, stderr, exit code). Call this after termux_run to actually see what happened."
        )
        list += fn(
            "termux_enable_external",
            "Make Termux accept commands from Jimi: sets allow-external-apps=true in ~/.termux/termux.properties (via root, or by typing it in Termux)."
        )
        list += fn("termux_open", "Open the Termux app itself (when the user wants to see or type in it).")
        list += fn(
            "termux_type",
            "Type a command directly into the visible Termux window through accessibility and press Enter — the fallback when RUN_COMMAND is not allowed.",
            mapOf(
                "cmd" to ("STRING" to "Command to type"),
                "enter" to ("BOOLEAN" to "Press Enter afterwards (default true)")
            ),
            listOf("cmd")
        )
        list += fn("terminal_diagnostics", "Diagnose why terminal work is failing: is Termux installed, is RUN_COMMAND allowed, is root there.")

        // ---- vision without screen sharing
        list += fn(
            "screen_look",
            "Take a screenshot, send that frame to yourself so you can SEE the screen, read the text layer, then delete the temp file. Use this instead of asking for screen share: screenshot -> look -> delete -> fresh screenshot.",
            mapOf(
                "keep" to ("BOOLEAN" to "true to also save the JPEG (default false = delete)"),
                "note" to ("STRING" to "What you are looking for, so the next step is focused")
            )
        )
        list += fn("screen_look_again", "Take a FRESH screenshot and look again (previous temp file already deleted). Call after every action to see its real effect.")
        list += fn(
            "screen_grid",
            "Map the screen into labelled cells (A1..) with coordinates, so you know exactly where each option is before tapping.",
            mapOf("cols" to ("NUMBER" to "Columns (default 4)"), "rows" to ("NUMBER" to "Rows (default 6)"))
        )
        list += fn(
            "screen_tap_cell",
            "Tap the centre of a grid cell from screen_grid — precise clicking without guessing pixels.",
            mapOf(
                "cell" to ("STRING" to "Cell label, e.g. B3"),
                "cols" to ("NUMBER" to "Same cols as the grid you used"),
                "rows" to ("NUMBER" to "Same rows as the grid you used")
            ),
            listOf("cell")
        )
        list += fn(
            "screen_locate",
            "Find an element by its label and return exact x,y coordinates (accessibility tree first, uiautomator dump when the tree is empty).",
            mapOf("label" to ("STRING" to "Visible text or content description")),
            listOf("label")
        )
        list += fn(
            "screen_clickables",
            "List every clickable element on screen with bounds and centre coordinates — the map you need on canvas/game screens that expose no text.",
            mapOf("limit" to ("NUMBER" to "Max elements (default 40)"))
        )
        list += fn("screen_delete_shots", "Delete every temporary screenshot Jimi saved, so nothing private stays on disk.")

        // ---- app directory: hidden / renamed apps, widgets, deep links
        list += fn(
            "app_resolve",
            "Resolve any spoken app name (Bangla or English, including renamed or hidden ones) to its package name.",
            mapOf("name" to ("STRING" to "Spoken app name")),
            listOf("name")
        )
        list += fn(
            "app_launch_any",
            "Launch an app by spoken name with four fallbacks: launcher intent, package intent, monkey (root), then store search. Use when open_app says the app is unknown.",
            mapOf("name" to ("STRING" to "Spoken app name")),
            listOf("name")
        )
        list += fn("app_list_all", "Full installed-app summary including system apps, with enabled/disabled state.")
        list += fn("app_hidden", "List apps that are hidden from the launcher (no launcher icon) — the ones users cannot find.")
        list += fn("app_system_list", "List system apps and their packages.")
        list += fn("app_renamed", "Find apps whose label looks renamed/disguised (generic names, odd characters) — useful when something was hidden on purpose.")
        list += fn("app_widgets", "List every home-screen widget provider available on the phone.")
        list += fn("app_widget_instances", "List the widgets currently placed on home screens.")
        list += fn(
            "app_deeplink",
            "Open a deep link / URI inside an app instead of navigating its UI: kind can be url, tel, sms, mailto, geo, market, upi, share or settings.",
            mapOf(
                "kind" to ("STRING" to "url | tel | sms | mailto | geo | market | upi | share | settings"),
                "payload" to ("STRING" to "The link or value, e.g. https://... or +8801...")
            ),
            listOf("kind", "payload")
        )
        list += fn(
            "app_install",
            "Open the Play Store page (or a local APK path) to install an app. Fix for 'Play Store shows not found'.",
            mapOf("name" to ("STRING" to "App name or APK path")),
            listOf("name")
        )

        // ---- to-do board + background worker + timer
        list += fn(
            "task_list",
            "Show the to-do board with a tick per item, so the user can see progress.",
            mapOf("limit" to ("NUMBER" to "Max items (default 24)"))
        )
        list += fn(
            "task_add",
            "Add one item to the to-do board. Use this as soon as a request has more than one step.",
            mapOf(
                "title" to ("STRING" to "Short task title"),
                "detail" to ("STRING" to "Optional detail")
            ),
            listOf("title")
        )
        list += fn(
            "task_plan",
            "Write a whole plan at once: one item per step of a big task. Jimi then works through it in the background and ticks items off.",
            mapOf("steps" to ("ARRAY" to "Ordered step titles")),
            listOf("steps")
        )
        list += fn(
            "task_update",
            "Mark a task done / running / failed / blocked with a note. Only mark DONE when it was verified.",
            mapOf(
                "id" to ("STRING" to "Task id from task_list"),
                "status" to ("STRING" to "done | running | failed | blocked | pending"),
                "note" to ("STRING" to "What happened")
            ),
            listOf("id", "status")
        )
        list += fn(
            "task_start",
            "Start the background worker: it takes the next pending task, tries to finish it (with escalation), ticks it, and moves on. Works while the app is closed.",
            mapOf("id" to ("STRING" to "Optional specific task id"))
        )
        list += fn(
            "task_timer",
            "Keep working on a goal for a set duration — 'set a timer and grind'. The worker retries and escalates until the time is up.",
            mapOf(
                "goal" to ("STRING" to "What to achieve"),
                "minutes" to ("NUMBER" to "Duration in minutes (1-60)")
            ),
            listOf("goal")
        )
        list += fn("task_stop", "Stop the background worker immediately.")
        list += fn(
            "task_clear",
            "Remove finished tasks, or the whole board.",
            mapOf("what" to ("STRING" to "finished (default) | all"))
        )
        list += fn("task_progress", "One-line progress summary of the board (percent + counts).")

        // ---- the never-give-up solver
        list += fn(
            "solve",
            "Hand a whole goal to the escalation solver: autopilot -> offline pack -> relaunch app -> screenshot vision -> shell/root, verifying after every strategy and reporting honestly. Use it when a task keeps failing or when the user says 'যেভাবেই হোক করে দাও'.",
            mapOf("goal" to ("STRING" to "The goal in the user's own words")),
            listOf("goal")
        )
        list += fn(
            "solve_alternative",
            "Try a deliberately different route after a failed solve, skipping the strategies that already failed.",
            mapOf(
                "goal" to ("STRING" to "The goal"),
                "previous_report" to ("STRING" to "The earlier report text")
            ),
            listOf("goal")
        )

        // ---- drawing, generating, editing
        list += fn(
            "draw_image",
            "Jimi draws a picture ITSELF on the device (offline, code-drawn Canvas art: sunset, night, mountains, river, house, tree, flower, cat, bird, mosque, mandala, city, rain, boat, heart, flag, patterns...). No network, no emoji, no clip art.",
            mapOf(
                "prompt" to ("STRING" to "What to draw, Bangla or English"),
                "size" to ("NUMBER" to "Square size in px (default 1080)")
            ),
            listOf("prompt")
        )
        list += fn(
            "draw_poster",
            "Draw a picture and put text on it (greeting card, banner, poster).",
            mapOf(
                "prompt" to ("STRING" to "What to draw"),
                "text" to ("STRING" to "Text to write on it (\\n for a new line)"),
                "size" to ("NUMBER" to "Square size in px (default 1080)")
            ),
            listOf("prompt", "text")
        )
        list += fn(
            "generate_image",
            "Generate a photorealistic image with the Gemini image model (needs network + API key). Use draw_image when offline.",
            mapOf(
                "prompt" to ("STRING" to "Detailed description"),
                "aspect" to ("STRING" to "1:1 | 16:9 | 9:16 | 4:3 | 3:4")
            ),
            listOf("prompt")
        )
        list += fn(
            "edit_image_ai",
            "Edit an existing image with the image model (add/remove/change something described in words).",
            mapOf(
                "path" to ("STRING" to "Path of the source image"),
                "instruction" to ("STRING" to "What to change")
            ),
            listOf("path", "instruction")
        )
        list += fn(
            "edit_image",
            "Edit an image on-device with real pixel operations (no app, no network). Ops is a comma-separated list: rotate=90, flip=h|v, crop=1:1|16:9, resize=1080, bright=1.15, contrast=1.2, sat=1.3, gray, invert, vintage, cool, warm, vivid, blur, circle, border=24, text=শুভ নববর্ষ. Returns the new file path.",
            mapOf(
                "path" to ("STRING" to "Source image path"),
                "ops" to ("STRING" to "Comma-separated operations")
            ),
            listOf("path", "ops")
        )
        list += fn(
            "collage",
            "Join two or more images into one collage.",
            mapOf(
                "paths" to ("ARRAY" to "Image paths, in order"),
                "vertical" to ("BOOLEAN" to "true = stack vertically, false = side by side")
            ),
            listOf("paths")
        )
        list += fn(
            "set_wallpaper",
            "Set an image (drawn, generated or edited) as the home or lock wallpaper.",
            mapOf(
                "path" to ("STRING" to "Image path"),
                "which" to ("STRING" to "home (default) | lock")
            ),
            listOf("path")
        )
        list += fn(
            "share_image_file",
            "Open the share sheet for an image/file Jimi created, so the user can send it anywhere.",
            mapOf("path" to ("STRING" to "File path (file:// accepted)")),
            listOf("path")
        )

        // ---- health + self repair
        list += fn(
            "health_check",
            "Measure battery, temperature, storage, RAM, network, permissions, cache, API reachability and stuck tasks, then give concrete suggestions.",
            mapOf("api" to ("BOOLEAN" to "Also ping the Gemini API to prove it works (default true)"))
        )
        list += fn(
            "health_fix",
            "Self-diagnose and repair: clears cache, frees space, restarts a dead session, retries failed tasks, and opens the exact settings page when one tap from the user is unavoidable. Reports fixed / needs-you / cannot-fix honestly."
        )
        list += fn("health_headline", "One-line health summary for a quick answer.")

        // ---- games on the phone's own apps
        list += fn(
            "game_open",
            "Open the REAL installed game app (Ludo / chess / carrom). If it is not installed, say so and open the store search — never pretend to play.",
            mapOf("name" to ("STRING" to "ludo | chess | carrom")),
            listOf("name")
        )
        list += fn(
            "game_play",
            "Make one real move inside the installed game: 'roll' throws the Ludo dice then screenshots the board, 'token 2' moves that token, 'e2e4' drags that chess move on the real board, 'strike' flicks the carrom striker.",
            mapOf(
                "name" to ("STRING" to "ludo | chess | carrom"),
                "action" to ("STRING" to "roll | token N | e2e4 | strike")
            ),
            listOf("name")
        )
        list += fn("game_status", "Report which game apps are actually installed and whether accessibility/root are ready to drive them.")

        // ---- restricted input fields
        list += fn(
            "fill_field",
            "Write into an input field that resists normal automation: focuses it, tries accessibility typing, then root input, then clipboard + Paste menu. This is the answer for 'restricted input fields'.",
            mapOf(
                "label" to ("STRING" to "The field's visible label or hint (empty = currently focused field)"),
                "text" to ("STRING" to "Text to enter")
            ),
            listOf("text")
        )
        list += fn(
            "paste_into",
            "Paste the clipboard into a named field using long-press + the Paste menu (works in protected third-party fields).",
            mapOf("label" to ("STRING" to "Field label or hint (empty = current focus)"))
        )

        list += fn(
            "gesture_tap",
            "Tap exact pixel coordinates (use after screen_locate / screen_grid / screen_clickables gave you the numbers).",
            mapOf("x" to ("NUMBER" to "x in px"), "y" to ("NUMBER" to "y in px")),
            listOf("x", "y")
        )
        list += fn(
            "gesture_long_press",
            "Long-press exact pixel coordinates (context menus, selection handles, drag sources).",
            mapOf(
                "x" to ("NUMBER" to "x in px"), "y" to ("NUMBER" to "y in px"),
                "ms" to ("NUMBER" to "Hold duration ms (default 620)")
            ),
            listOf("x", "y")
        )


        // ==================== v3: agents, engine, browser, media, docs ====================
        list += fn("engine_start", "Queue a goal on the autonomous task engine: it plans, runs in the background (foreground service + WorkManager), self-corrects on errors and reports. Up to 5 run at once.", mapOf("goal" to ("STRING" to "What to achieve"), "priority" to ("NUMBER" to "1 low .. 5 urgent, default 3"), "deadline_minutes" to ("NUMBER" to "Optional hard deadline"), "budget" to ("NUMBER" to "Max steps, default from settings")), listOf("goal"))
        list += fn("engine_start_many", "Queue several goals at once; they run in parallel (max 5), each with its own progress and report.", mapOf("goals" to ("STRING" to "Goals separated by ' | '")), listOf("goals"))
        list += fn("engine_timer", "Timer-based execution: start a task after a delay.", mapOf("goal" to ("STRING" to "What to do"), "delay_minutes" to ("NUMBER" to "Minutes to wait"), "priority" to ("NUMBER" to "1..5")), listOf("goal", "delay_minutes"))
        list += fn("engine_pause", "Pause one queued or running task.", mapOf("id" to ("STRING" to "Task id")), listOf("id"))
        list += fn("engine_resume", "Resume a paused task.", mapOf("id" to ("STRING" to "Task id")), listOf("id"))
        list += fn("engine_cancel", "Cancel one task completely and free its resources.", mapOf("id" to ("STRING" to "Task id")), listOf("id"))
        list += fn("engine_cancel_all", "Emergency stop for background work: cancel every queued and running task.")
        list += fn("engine_retry", "Retry a failed task with the same goal.", mapOf("id" to ("STRING" to "Task id")), listOf("id"))
        list += fn("engine_status", "Live queue: state, priority, progress, steps done and remaining, errors for every task.")
        list += fn("engine_report", "Final report of one task: steps, timing, verdict, artifacts.", mapOf("id" to ("STRING" to "Task id")), listOf("id"))
        list += fn("engine_headline", "One line about what is running now.")
        list += fn("engine_clear", "Remove finished tasks from the stored queue.")
        list += fn("engine_recover", "Reload the persisted queue after a crash or reboot and resume interrupted work.")
        list += fn("agent_plan", "Plan a goal into ordered steps and tick them on the board.", mapOf("goal" to ("STRING" to "Goal"), "budget" to ("NUMBER" to "Max steps, default 22")), listOf("goal"))
        list += fn("agent_run", "Run one goal through the orchestrator: best two agents, approval when sensitive, merged result.", mapOf("goal" to ("STRING" to "Goal"), "detail" to ("STRING" to "Extra context"), "budget" to ("NUMBER" to "Max steps")), listOf("goal"))
        list += fn("agent_parallel", "Run several goals simultaneously through the orchestrator and merge the reports.", mapOf("goals" to ("STRING" to "Goals separated by ' | '")), listOf("goals"))
        list += fn("agent_status", "Resource locks and which agent holds what right now.")
        list += fn("screen_agent", "Do a task by looking at the screen: capture, vision model picks the button, authorized click, re-capture to verify, temp screenshots deleted.", mapOf("goal" to ("STRING" to "On-screen goal"), "budget" to ("NUMBER" to "Max vision rounds, default 10")), listOf("goal"))
        list += fn("vision_fill", "Fill an on-screen field found by vision or accessibility; password and OTP fields are refused.", mapOf("label" to ("STRING" to "Field label"), "text" to ("STRING" to "Value")), listOf("label", "text"))
        list += fn("vision_status", "Vision loop health: accessibility, screenshot source, computer-use model, sensitive-screen guard.")
        list += fn("browser_open", "Load a URL in the built-in off-screen WebView (JavaScript renders, modern sites work).", mapOf("url" to ("STRING" to "Full URL")), listOf("url"))
        list += fn("browser_search", "Search the web inside the browser and extract the results.", mapOf("query" to ("STRING" to "Search text")), listOf("query"))
        list += fn("browser_text", "Extract readable text of the current page.", mapOf("max_chars" to ("NUMBER" to "Limit, default 12000")))
        list += fn("browser_links", "List links on the current page with labels.", mapOf("limit" to ("NUMBER" to "default 20")))
        list += fn("browser_fields", "List input fields on the page (form assistance).", mapOf("limit" to ("NUMBER" to "default 20")))
        list += fn("browser_form_fill", "Type into a page field by label, placeholder or name.", mapOf("label" to ("STRING" to "Field label"), "value" to ("STRING" to "Value")), listOf("label", "value"))
        list += fn("browser_click_text", "Click a link or button by its visible text.", mapOf("text" to ("STRING" to "Visible text")), listOf("text"))
        list += fn("browser_shot", "Screenshot the current page to PNG for analysis.", mapOf("keep" to ("BOOLEAN" to "true keeps the file")))
        list += fn("browser_back", "Go back one page.")
        list += fn("browser_forward", "Go forward one page.")
        list += fn("browser_reload", "Reload the current page.")
        list += fn("browser_new_tab", "Open a new tab (multi-tab supported).", mapOf("url" to ("STRING" to "Optional URL")))
        list += fn("browser_tabs", "List open tabs with ids, titles and URLs.")
        list += fn("browser_switch_tab", "Switch to a tab by id.", mapOf("id" to ("NUMBER" to "Tab id")), listOf("id"))
        list += fn("browser_close_tab", "Close a tab by id.", mapOf("id" to ("NUMBER" to "Tab id")), listOf("id"))
        list += fn("browser_close_all", "Close every tab and free WebView memory.")
        list += fn("browser_download", "Queue a URL in Android DownloadManager.", mapOf("url" to ("STRING" to "File URL"), "mime" to ("STRING" to "Optional mime type")), listOf("url"))
        list += fn("browser_downloads", "Recent downloads and their state.")
        list += fn("browser_status", "Current page, tab count, last error, kill-switch state.")
        list += fn("browser_clear", "Clear page content and stop loading.")
        list += fn("browser_home", "Set the browser start page preference.", mapOf("site" to ("STRING" to "google | duckduckgo | bing | youtube")), listOf("site"))
        list += fn("research_run", "Multi-source research: parallel grounded search, cross-check between sources, old vs new separated, links saved, then a report. Cached 24h.", mapOf("query" to ("STRING" to "Question"), "sources" to ("NUMBER" to "How many, default from settings")), listOf("query"))
        list += fn("research_summary", "Summarize any text into key points.", mapOf("text" to ("STRING" to "Text"), "ask" to ("STRING" to "Optional instruction")), listOf("text"))
        list += fn("research_cache", "How many cached research results exist and how fresh they are.")
        list += fn("research_clear_cache", "Delete the research cache.")
        list += fn("audio_speak", "Generate speech with a Gemini TTS voice, save a WAV, and optionally play it in the background.", mapOf("text" to ("STRING" to "What to say"), "voice" to ("STRING" to "Voice name, default from settings"), "style" to ("STRING" to "Optional delivery hint"), "play" to ("BOOLEAN" to "true = play in background after generating")), listOf("text"))
        list += fn("audio_dialogue", "Generate a multi-speaker dialogue as one WAV file.", mapOf("script" to ("STRING" to "Lines like 'A: hello' / 'B: hi'")), listOf("script"))
        list += fn("audio_ambient", "Create offline ambience (rain, waves, focus, night, brown noise).", mapOf("kind" to ("STRING" to "rain|waves|focus|night|brown"), "seconds" to ("NUMBER" to "Length, default 120"), "play" to ("BOOLEAN" to "true = play in background")), listOf("kind"))
        list += fn("audio_voices", "List available TTS voices.")
        list += fn("audio_library", "List generated audio files with size and duration.")
        list += fn("audio_play", "Play an audio file in the background through the media foreground service.", mapOf("path" to ("STRING" to "File path"), "loop" to ("BOOLEAN" to "Loop for ambience")), listOf("path"))
        list += fn("audio_pause", "Pause background playback.")
        list += fn("audio_resume", "Resume background playback.")
        list += fn("audio_stop", "Stop background playback and remove the notification.")
        list += fn("audio_play_status", "Is playback running, paused or stopped?")
        list += fn("audio_denoise", "Offline noise reduction for a WAV (gate + smoothing); other formats get the ffmpeg command.", mapOf("path" to ("STRING" to "WAV path"), "strength" to ("NUMBER" to "0.3..1.0")), listOf("path"))
        list += fn("audio_trim", "Cut a WAV between two timestamps (offline, sample accurate).", mapOf("path" to ("STRING" to "WAV path"), "start_seconds" to ("NUMBER" to "Start"), "end_seconds" to ("NUMBER" to "End")), listOf("path", "start_seconds", "end_seconds"))
        list += fn("audio_transcribe", "Speech to text for an audio file.", mapOf("path" to ("STRING" to "Audio path")), listOf("path"))
        list += fn("audio_summarize", "Transcribe an audio file then summarize it.", mapOf("path" to ("STRING" to "Audio path")), listOf("path"))
        list += fn("audio_speakers", "Transcribe with speaker labels (model inference, not voiceprint diarisation).", mapOf("path" to ("STRING" to "Audio path")), listOf("path"))
        list += fn("video_summary", "Understand and summarize a video (whole file if small, sampled frames if large).", mapOf("path" to ("STRING" to "Video path")), listOf("path"))
        list += fn("video_scenes", "Detect scene changes with timestamps (on-device histogram comparison).", mapOf("path" to ("STRING" to "Video path"), "threshold" to ("NUMBER" to "0.1..0.5, default 0.24")), listOf("path"))
        list += fn("video_subtitles", "Extract visible captions from a video into an .srt file.", mapOf("path" to ("STRING" to "Video path")), listOf("path"))
        list += fn("video_frame", "Save one frame of a video as JPEG.", mapOf("path" to ("STRING" to "Video path"), "at_seconds" to ("NUMBER" to "Timestamp")), listOf("path"))
        list += fn("video_edit_hint", "Exact ffmpeg command for trimming or resizing, because on-device re-encoding is unavailable.", mapOf("path" to ("STRING" to "Video path"), "goal" to ("STRING" to "What to change")), listOf("path"))
        list += fn("image_ocr", "Read all text in an image exactly, in reading order.", mapOf("path" to ("STRING" to "Image path")), listOf("path"))
        list += fn("image_detect", "Detect objects with position and rough size, flagging uncertain guesses.", mapOf("path" to ("STRING" to "Image path")), listOf("path"))
        list += fn("image_compare", "Compare two images and describe differences.", mapOf("path_a" to ("STRING" to "First image"), "path_b" to ("STRING" to "Second image"), "question" to ("STRING" to "Optional focus")), listOf("path_a", "path_b"))
        list += fn("image_enhance", "Offline enhancement: upscale, contrast stretch, sharpen.", mapOf("path" to ("STRING" to "Image path"), "target_side" to ("NUMBER" to "Longest side px, default 1600")), listOf("path"))
        list += fn("image_remove_bg", "Remove a plain background offline and save a transparent PNG.", mapOf("path" to ("STRING" to "Image path"), "tolerance" to ("NUMBER" to "10..90, default 42")), listOf("path"))
        list += fn("image_facts", "Offline image facts: size, brightness, dominant colours.", mapOf("path" to ("STRING" to "Image path")), listOf("path"))
        list += fn("image_caption", "Draw text onto an image (offline).", mapOf("path" to ("STRING" to "Image path"), "text" to ("STRING" to "Caption"), "bottom" to ("BOOLEAN" to "true = bottom")), listOf("path", "text"))
        list += fn("doc_create", "Create a real document file: pdf, docx, pptx, html, md, csv or txt.", mapOf("kind" to ("STRING" to "pdf|docx|pptx|html|md|csv|txt"), "title" to ("STRING" to "Title / file base"), "content" to ("STRING" to "Body; blank lines split slides or sections")), listOf("kind", "content"))
        list += fn("doc_pdf", "Create an A4 PDF with wrapped text and page numbers.", mapOf("title" to ("STRING" to "Title"), "content" to ("STRING" to "Body")), listOf("content"))
        list += fn("doc_docx", "Create a Word .docx; lines starting with # become headings.", mapOf("title" to ("STRING" to "Title"), "content" to ("STRING" to "Body")), listOf("content"))
        list += fn("doc_pptx", "Create a PowerPoint .pptx; each blank-line block is a slide, first line is its title.", mapOf("title" to ("STRING" to "Presentation title"), "content" to ("STRING" to "Slide blocks")), listOf("content"))
        list += fn("doc_html", "Create a styled dark HTML page.", mapOf("title" to ("STRING" to "Title"), "content" to ("STRING" to "Body")), listOf("content"))
        list += fn("doc_csv", "Create a CSV file from rows (comma, pipe or tab separated).", mapOf("title" to ("STRING" to "File base"), "content" to ("STRING" to "Rows")), listOf("content"))
        list += fn("doc_list", "List documents the studio created.")
        list += fn("doc_capabilities", "What the document studio can produce.")
        list += fn("memory_search", "Search everything remembered: facts, chats, task history and files, ranked with sources.", mapOf("query" to ("STRING" to "Search text"), "limit" to ("NUMBER" to "Max hits, default 10")), listOf("query"))
        list += fn("memory_overview", "Dashboard of stored memory: counts per store plus task statistics.")
        list += fn("memory_forget", "Delete one stored item (user controlled).", mapOf("kind" to ("STRING" to "memory|task|chat|project"), "id" to ("STRING" to "Item id")), listOf("kind", "id"))
        list += fn("memory_forget_all", "Delete a whole store.", mapOf("what" to ("STRING" to "memory|tasks|chats|all")), listOf("what"))
        list += fn("history_recent", "Recent task history with verdicts.", mapOf("limit" to ("NUMBER" to "default 8")))
        list += fn("history_search", "Search task history.", mapOf("query" to ("STRING" to "Text"), "limit" to ("NUMBER" to "default 10")), listOf("query"))
        list += fn("history_stats", "Task statistics and repeated failure patterns.")
        list += fn("context_brief", "Compressed working context for a goal: memory, similar tasks, project, recent chat.", mapOf("goal" to ("STRING" to "Goal")), listOf("goal"))
        list += fn("project_create", "Create and select an AI project workspace.", mapOf("name" to ("STRING" to "Project name"), "note" to ("STRING" to "Optional first note")), listOf("name"))
        list += fn("project_open", "Select an existing project so its notes join the context.", mapOf("name" to ("STRING" to "Name or id")), listOf("name"))
        list += fn("project_note", "Add a note to the current project.", mapOf("text" to ("STRING" to "Note")), listOf("text"))
        list += fn("project_add_file", "Attach a file to the current project.", mapOf("path" to ("STRING" to "File path")), listOf("path"))
        list += fn("project_list", "List projects with file and task counts.")
        list += fn("project_detail", "Detail of the current or named project.", mapOf("name" to ("STRING" to "Optional name")))
        list += fn("project_remove", "Delete a project entry; files on disk stay.", mapOf("name" to ("STRING" to "Name or id")), listOf("name"))
        list += fn("auto_list", "List user automation templates.")
        list += fn("auto_save", "Save a reusable automation template from steps.", mapOf("name" to ("STRING" to "Template name"), "goal" to ("STRING" to "What it achieves"), "steps" to ("STRING" to "Steps separated by ' | '")), listOf("name", "steps"))
        list += fn("auto_run", "Run a saved template through the task engine (approval still applies).", mapOf("name" to ("STRING" to "Name or id"), "priority" to ("NUMBER" to "1..5")), listOf("name"))
        list += fn("auto_detail", "Show every step of a template.", mapOf("name" to ("STRING" to "Name or id")), listOf("name"))
        list += fn("auto_delete", "Delete a template.", mapOf("name" to ("STRING" to "Name or id")), listOf("name"))
        list += fn("auto_export", "Export templates to a text file the user can keep or edit.", mapOf("name" to ("STRING" to "Optional single template")))
        list += fn("auto_import", "Import templates from exported text.", mapOf("text" to ("STRING" to "Exported content")), listOf("text"))
        list += fn("auto_from_task", "Turn a finished task into a template using the steps that really ran.", mapOf("run_id" to ("STRING" to "Task history id"), "name" to ("STRING" to "Optional name")), listOf("run_id"))
        list += fn("record_start", "Start recording the actions the automation path really executes.")
        list += fn("record_stop", "Stop recording and report how many steps were captured.")
        list += fn("record_preview", "Show recorded steps.")
        list += fn("record_save", "Save the recording as an automation template.", mapOf("name" to ("STRING" to "Template name")), listOf("name"))
        list += fn("record_from_text", "Build a template from a spoken or written description (offline step splitter).", mapOf("name" to ("STRING" to "Template name"), "description" to ("STRING" to "Natural language steps")), listOf("name", "description"))
        list += fn("daily_report", "Today's brief: device health, reminders, activity data, recent tasks, suggestions. Never diagnoses illness.", mapOf("check_api" to ("BOOLEAN" to "Also test model reachability")))
        list += fn("daily_activity", "Which activity data is readable right now (honest about missing Health Connect).")
        list += fn("daily_sleep", "Read or save the user's own sleep window.", mapOf("text" to ("STRING" to "e.g. '11pm to 6am'; empty = read")))
        list += fn("daily_medicine", "Store a medication reminder; never dosage advice.", mapOf("medicine" to ("STRING" to "Medicine name"), "time" to ("STRING" to "When")), listOf("medicine", "time"))
        list += fn("daily_appointment", "Prepare an appointment checklist from the user's notes.", mapOf("title" to ("STRING" to "Appointment"), "notes" to ("STRING" to "Symptoms or questions")), listOf("title"))
        list += fn("shizuku_status", "Shizuku state: not installed / not running / no permission / ready adb / ready root / connection lost.")
        list += fn("shizuku_diagnostics", "Deep Shizuku check: uid, binder ping, permissions, what failed.")
        list += fn("shizuku_exec", "Run one privileged shell command through Shizuku; risky commands need approval.", mapOf("command" to ("STRING" to "Shell command")), listOf("command"))
        list += fn("shizuku_grant", "Grant a permission to an app through Shizuku.", mapOf("package" to ("STRING" to "Target package"), "permission" to ("STRING" to "Permission name")), listOf("package", "permission"))
        list += fn("shizuku_open", "Open the Shizuku app so the user can start it.")
        list += fn("permission_dashboard", "Permission audit: granted / not granted / restricted / required / connection lost, with how to fix each.")
        list += fn("permission_fix", "Automatic fixes that need no user action (open the right settings page, re-request).")
        list += fn("permission_guide", "Step-by-step instructions for one permission.", mapOf("id" to ("STRING" to "Permission id")), listOf("id"))
        list += fn("kill_stop", "EMERGENCY STOP: stop every registered background activity (browser, audio, tasks, capture).")
        list += fn("error_report", "Centralised error log: counts per module, last errors, what was tried.", mapOf("module" to ("STRING" to "Optional module filter")))
        list += fn("error_clear", "Clear the stored error log.")
        list += fn("approval_status", "Pending approval requests and recent decision history.")
        list += fn("approval_decide", "Approve or deny the pending sensitive action.", mapOf("approved" to ("BOOLEAN" to "true = allow")), listOf("approved"))
        list += fn("model_router", "Which model each kind of work uses, latency samples and demoted models.")
        list += fn("model_free", "Release caches of unused modules (browser tabs, screenshots) to free memory.")

        return JSONArray(list)
    }

    /** Tool descriptions the narrator hands to the model as a quick reference. */
    const val TOOL_GUIDE = """
        তুমি ফোনের ভেতরে থাকো এবং নিচের টুলগুলো দিয়ে সত্যিকারের কাজ করতে পারো:
        রুট/সিস্টেম: root_status, root_exec, root_input, root_clipboard, root_radio, app_control,
          system_setting_put/get, system_info, root_file, install_apk, root_grant_self,
          root_enable_accessibility, root_default_assistant।
        টার্মিনাল: shell, shell_background, shell_job_output, shell_jobs, shell_drive, termux_run,
          termux_result, termux_open, termux_type, termux_enable_external, terminal_diagnostics।
        দেখা: screen_look, screen_look_again, screen_grid, screen_tap_cell, screen_locate,
          screen_clickables, screen_delete_shots।
        অ্যাপ: app_resolve, app_launch_any, app_list_all, app_hidden, app_system_list, app_renamed,
          app_widgets, app_widget_instances, app_deeplink, app_install।
        কাজের তালিকা: task_plan, task_add, task_list, task_update, task_start, task_timer, task_stop,
          task_clear, task_progress, solve, solve_alternative।
        বানানো/এডিট: draw_image, draw_poster, generate_image, edit_image_ai, edit_image, collage,
          set_wallpaper, share_image_file।
        স্বাস্থ্য: health_check, health_fix, health_headline।
        গেম: game_open, game_play, game_status।
        লেখা বসানো: fill_field, paste_into।
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
        • ফাইল নিয়ে কাজ (কপি/মুভ/খোঁজা) হলে list_files/find_file দিয়ে শুরু করো;
          অ্যান্ড্রয়েড ব্লক করলে copy_file_via_ui দিয়ে Files অ্যাপের ভেতর থেকেই করো।
        • নতুন অ্যাপ হলে launch_app দিয়ে খোলো (নাম বাংলায় বললেও চিনবে), ওটা কী কাজের সেটাও বলবে।
        • ক্যারম **সত্যিকারের বোর্ডে** খেলবে: open_carrom → strike_carrom (বারবার),
          প্রতিটা স্ট্রাইকের পর read_screen দিয়ে বোর্ড দেখে পরের শট ঠিক করো।
          মনে মনে বা মুখে মুখে খেলবে না — আসল স্ট্রাইক করবে, তারপর স্কোর বলবে।
        • কোড/লেখা/তালিকা চাইলে পুরোটা লেখো, তারপর save_text দিয়ে ফাইলে সেভ করো
          (দরকার হলে share=true) — শুধু মুখে বললে চলবে না।
        • দৈনন্দিন কাজ (ইউটিউব, হোয়াটসঅ্যাপ, টেলিগ্রাম ভয়েস, স্টোরি, টিকটক, ছবি এডিট,
          রিচার্জ, কল, ক্যারম, ফাইল কপি) হলে আগে offline_packs দেখে run_pack চালাও —
          এক কলেই পুরো কাজ, ধাপে ধাপে টুল ডাকার চেয়ে অনেক দ্রুত। ব্যর্থ হলে তবেই হাতে ধাপ চালাও।
        • ছবি/ভিডিও/অডিও নিয়ে প্রশ্ন এলে describe_image / video_frames / audio_info ব্যবহার করো —
          ফাইলনাম দেখে অনুমান করবে না।
        • কাজ শেষ বলার আগে অবশ্যই verify_task দিয়ে স্ক্রিন যাচাই করো।
          যাচাই ছাড়া "হয়ে গেছে" বলা কঠোরভাবে নিষিদ্ধ।
        • কেউ খেলতে চাইলে game_start → game_move ধারাবাহিকভাবে চালাও, প্রতিবার game_board দিয়ে
          অবস্থা দেখো। নিয়ম কখনো ভাঙবে না, এবং চাল দেওয়ার আগে ছোট করে বলো কী দিচ্ছ।
    """
}
