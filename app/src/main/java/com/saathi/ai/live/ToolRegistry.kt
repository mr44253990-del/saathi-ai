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
