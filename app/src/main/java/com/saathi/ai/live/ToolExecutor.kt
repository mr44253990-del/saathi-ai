package com.saathi.ai.live

import android.content.Context
import com.saathi.ai.access.JimiAccessibilityService
import com.saathi.ai.access.PhoneTools
import com.saathi.ai.access.SmartTools
import com.saathi.ai.access.AppKnowledge
import com.saathi.ai.access.FileTools
import com.saathi.ai.media.MediaUnderstanding
import com.saathi.ai.access.OfflinePacks
import com.saathi.ai.data.SecurePrefs
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Runs one tool call coming from Gemini and returns the JSON response body that
 * is sent back in the `toolResponse` message.
 */
class ToolExecutor(private val context: Context) {

    private val tools = PhoneTools(context)
    private val smart = SmartTools(context)
    private val apps = AppKnowledge(context)
    private val files = FileTools(context)
    private val media = MediaUnderstanding(context)
    private val prefs = SecurePrefs(context)

    // v2 helpers. Lazy because most sessions never touch root, games or the editor,
    // and constructing them eagerly would slow down every tool call.
    private val term by lazy { com.saathi.ai.access.TerminalTools(context) }
    private val games by lazy { com.saathi.ai.access.GameApps(context) }
    private val health by lazy { com.saathi.ai.access.HealthInsights(context) }
    private val editor by lazy { com.saathi.ai.media.ImageEditor(context) }
    private val imageGen by lazy { com.saathi.ai.create.ImageGen(context) }
    private val board by lazy { com.saathi.ai.task.TaskBoard(context) }

    // v3 engines. Same rule as above: constructed only when a v3 tool is used.
    private val research by lazy { com.saathi.ai.web.ResearchEngine(context) }
    private val audioGen by lazy { com.saathi.ai.media.AudioGen(context) }
    private val audioLab by lazy { com.saathi.ai.media.AudioLab(context) }
    private val videoLab by lazy { com.saathi.ai.media.VideoLab(context) }
    private val visionLab by lazy { com.saathi.ai.media.VisionLab(context) }
    private val docs by lazy { com.saathi.ai.create.DocumentStudio(context) }
    private val history by lazy { com.saathi.ai.data.TaskHistory(context) }
    private val memSearch by lazy { com.saathi.ai.data.MemorySearch(context) }
    private val projects by lazy { com.saathi.ai.data.ProjectWorkspace(context) }
    private val summarizer by lazy { com.saathi.ai.data.ContextSummarizer(context) }
    private val daily by lazy { com.saathi.ai.daily.DailyAssistant(context) }
    private val templates by lazy { com.saathi.ai.auto.TemplateStore(context) }
    private val engine get() = com.saathi.ai.task.TaskEngine
    private val orch get() = com.saathi.ai.agent.Orchestrator.get(context)
    private val web get() = com.saathi.ai.web.WebBrowser
    private val shiz get() = com.saathi.ai.access.ShizukuBridge
    private val perms get() = com.saathi.ai.access.PermissionCenter
    private val errs get() = com.saathi.ai.core.ErrorCenter
    private val gate get() = com.saathi.ai.core.ApprovalGate
    private val kill get() = com.saathi.ai.core.KillSwitch
    private val playback get() = com.saathi.ai.media.AudioPlaybackService
    private val recorder get() = com.saathi.ai.auto.MacroRecorder
    private val router get() = ModelRouter
    private val visual get() = com.saathi.ai.vision.VisualCore

    /**
     * Repeated-call guard.
     *
     * The model used to re-issue "search YouTube" three times in a row because each
     * call looked successful. Now an identical (name, args) pair inside
     * [DUPLICATE_WINDOW_MS] returns a note instead of firing the action again, so the
     * conversation moves on instead of looping.
     */
    private val lastCalls = ConcurrentHashMap<String, Long>()

    private fun isDuplicate(name: String, args: JSONObject): Boolean {
        val key = "$name|${args.toString()}"
        val now = System.currentTimeMillis()
        val prev = lastCalls[key]
        if (prev != null && now - prev < DUPLICATE_WINDOW_MS) return true
        lastCalls[key] = now
        if (lastCalls.size > 64) {
            lastCalls.entries.removeIf { now - it.value > DUPLICATE_WINDOW_MS * 4 }
        }
        return false
    }

    fun execute(name: String, args: JSONObject): String {
        val a = args
        return try {
            if (isDuplicate(name, a) && !name.startsWith("read_") && name != "recall") {
                return "একই কাজ এইমাত্র করেছি ($name) — আবার করছি না। স্ক্রিনে যা হয়েছে সেটা দেখে পরের ধাপে যাও।"
            }
            when (name) {
                "open_app" -> tools.openApp(a.str("name"))
                "open_url" -> tools.openUrl(a.str("url"))
                "web_search" -> if (smart.webSearch(a.str("query"))) "খুঁজে দেখাচ্ছি" else "সার্চ করা গেল না"
                "open_weather" -> smart.openWeather(a.str("location"))
                "map_search" -> if (smart.mapSearch(a.str("query"))) "ম্যাপে খুঁজছি" else "ম্যাপ খোলা গেল না"
                "play_store_search" -> if (smart.playStoreSearch(a.str("query"))) "Play Store-এ খুঁজছি" else "Play Store খোলা গেল না"
                "upi_pay" -> smart.upiPay(a.str("vpa"), a.str("payee"), a.str("amount"), a.str("note"))
                "upi_scan" -> smart.upiScan()
                "copy_text" -> smart.copy(a.str("text"))
                "paste_text" -> smart.paste()
                "share_text" -> smart.shareText(a.str("text"))
                "medicine_reminder" -> {
                    com.saathi.ai.data.MemoryStore(context)
                        .addReminder(a.str("medicine"), a.str("time"))
                    smart.medicineReminder(a.str("medicine"), a.str("time"))
                }
                "battery_status" -> smart.batteryReport()
                "time_now" -> smart.timeNow()
                "find_app" -> {
                    val app = apps.resolve(a.str("name"))
                    if (app == null) "কোনো অ্যাপ পাওয়া যায়নি"
                    else "${app.label} (${app.pkg}) — ${app.purpose}"
                }
                "launch_app" -> {
                    val app = apps.resolve(a.str("name"))
                    when {
                        app == null -> "«${a.str("name")}» নামে কোনো অ্যাপ ইনস্টল নেই"
                        apps.launch(app) -> "${app.label} খুলেছি — ${app.purpose}"
                        else -> "${app.label} খোলা গেল না"
                    }
                }
                "list_apps" -> apps.installedApps()
                    .take(a.int("limit", 25))
                    .joinToString("\n") { "${it.label} — ${it.purpose}" }
                "app_purpose" -> apps.purposeOf(a.str("package"))
                // ---- files
                "list_files" -> files.list(a.str("path"), a.int("limit", 25))
                    .joinToString("\n") { (if (it.isDir) "ফোল্ডার · " else "ফাইল · ") + it.name + (if (it.isDir) "" else "  " + files.humanSize(it.size)) }
                    .ifBlank { "ফোল্ডার খালি বা পড়া যাচ্ছে না" }
                "find_file" -> files.find(a.str("query"))
                    .joinToString("\n") { it.path }
                    .ifBlank { "কিছু পাওয়া যায়নি" }
                "file_info" -> files.info(a.str("path"))
                "copy_file" -> files.copy(a.str("path"), a.str("to"))
                "move_file" -> files.move(a.str("path"), a.str("to"))
                "delete_file" -> files.delete(a.str("path"))
                "new_folder" -> files.newFolder(a.str("in"), a.str("name"))
                "share_file" -> files.share(a.str("path"))
                "open_files_app" -> files.openInFilesApp(a.str("folder"))
                "copy_file_via_ui" -> files.copyViaUi(a.str("file"), a.str("folder"))

                // ---- games
                "game_start" -> {
                    when (a.str("game").lowercase()) {
                        "ludo", "লুডু" -> {
                            com.saathi.ai.create.GameEngine.ludo = com.saathi.ai.create.GameEngine.LudoState.new()
                            "লুডু শুরু! তুমি সবুজ, আমি লাল। চাইলে «ছক্কা» বলো।"
                        }
                        "chess", "দাবা" -> {
                            com.saathi.ai.create.GameEngine.chess = com.saathi.ai.create.GameEngine.ChessState()
                            com.saathi.ai.create.GameEngine.chessBoard(com.saathi.ai.create.GameEngine.chess!!)
                        }
                        "carrom", "ক্যারম" -> {
                            com.saathi.ai.create.GameEngine.carrom = com.saathi.ai.create.GameEngine.CarromState()
                            "ক্যারম বোর্ড তৈরি। স্ট্রাইকার ঠিক করে মারো।"
                        }
                        else -> "লুডু, দাবা বা ক্যারম — কোনটা?"
                    }
                }
                "game_move" -> {
                    val engine = com.saathi.ai.create.GameEngine
                    when (a.str("game").lowercase()) {
                        "ludo", "লুডু" -> {
                            val st = engine.ludo
                            if (st == null) {
                                "আগে game_start দিয়ে লুডু শুরু করো"
                            } else {
                            val roll = a.int("roll", 0).takeIf { it in 1..6 } ?: engine.rollDice()
                            val legal = engine.ludoLegalMoves(st, roll)
                            if (legal.isEmpty()) {
                                engine.nextTurn(st)
                                "ছক্কা $roll — কোনো চাল সম্ভব না, পালা বদলাল।\n" + engine.ludoBoard(st)
                            } else {
                                val token = a.int("token", legal.first()).coerceIn(0, 3)
                                val move = if (token !in legal) {
                                    "টোকেন ${token + 1} এখন চালানো যাবে না"
                                } else {
                                    engine.ludoMove(st, token, roll)
                                }
                                engine.nextTurn(st)
                                "$move (ছক্কা $roll)\n" + engine.ludoBoard(st)
                            }
                            }
                        }
                        "chess", "দাবা" -> {
                            val st = engine.chess
                            if (st == null) {
                                "আগে game_start দিয়ে দাবা শুরু করো"
                            } else {
                                val move = a.str("move")
                                if (move.length != 4) engine.chessBoard(st)
                                else engine.chessApply(st, move) + "\n" + engine.chessBoard(st)
                            }
                        }
                        "carrom", "ক্যারম" -> {
                            val st = engine.carrom
                            if (st == null) {
                                "আগে game_start দিয়ে ক্যারম শুরু করো"
                            } else {
                                engine.adjust(
                                    st,
                                    a.num("dx").toFloat() / 100f,
                                    a.num("aim").toFloat(),
                                    a.num("power").toFloat() / 100f
                                )
                                engine.carromHint(st)
                            }
                        }
                        else -> "কোন গেম?"
                    }
                }
                "game_board" -> {
                    val engine = com.saathi.ai.create.GameEngine
                    when (a.str("game").lowercase()) {
                        "ludo", "লুডু" -> engine.ludo?.let { engine.ludoBoard(it) } ?: "লুডু চলছে না"
                        "chess", "দাবা" -> engine.chess?.let { engine.chessBoard(it) } ?: "দাবা চলছে না"
                        "carrom", "ক্যারম" -> engine.carrom?.let { engine.carromHint(it) } ?: "ক্যারম চলছে না"
                        else -> engine.describe(a.str("game"))
                    }
                }

                "save_text" -> files.writeText(
                    a.str("fileName"),
                    a.str("content"),
                    a.bool("share", false)
                )

                // ---- offline packs (no model round trips)
                "offline_packs" -> OfflinePacks.catalogue()
                "run_pack" -> {
                    val name = a.str("pack")
                    val arg = a.str("arg")
                    OfflinePacks.run(context, name, arg.ifBlank { null }) { step ->
                        SessionHub.update { it.copy(lastTool = step) }
                    }
                }

                // ---- media understanding
                "describe_image" -> {
                    val facts = media.describeImage(a.str("path"))
                    if (facts == null) "ছবিটা পড়া গেল না"
                    else {
                        // push the frame into the live session so the model can see it
                        SessionHub.sendCameraFrame(facts.jpeg)
                        media.describe(facts)
                    }
                }
                "video_info" -> media.videoInfo(a.str("path"))
                "video_frames" -> {
                    val frames = media.videoFrames(a.str("path"), a.int("frames", 3))
                    frames.forEach { SessionHub.sendCameraFrame(it) }
                    if (frames.isEmpty()) "ভিডিওর ফ্রেম বের করা গেল না"
                    else "ভিডিওর ${frames.size}টি ফ্রেম দেখছি — এখন বলো কী দেখতে পাচ্ছি"
                }
                "audio_info" -> media.audioInfo(a.str("path"))

                "remember_playbook" -> {
                    val steps = a.optJSONArray("steps")
                    val list = (0 until (steps?.length() ?: 0)).map { steps!!.optString(it) }
                    apps.rememberPlaybook(a.str("package"), a.str("goal"), list)
                    "মুখস্থ করেছি — পরেরবার দ্রুত হবে"
                }
                "deep_link" -> if (smart.deepLink(a.str("app"), a.str("value"))) "খুলে দিয়েছি" else "এটি খোলা গেল না"
                "long_press" -> tools.screenLongPress(a.str("text"))
                "screen_bounds" -> tools.screenBounds(a.str("text"))
                "read_clipboard" -> smart.paste()
                "write_clipboard" -> smart.copy(a.str("text"))
                "search_youtube" -> tools.searchYouTube(a.str("query"))
                "play_on_youtube" -> tools.playOnYouTube(a.str("query"))

                // ---- human-like gestures
                "gesture_tap" -> gesture { it.tap(a.num("x").toFloat(), a.num("y").toFloat()) }
                "gesture_long_press" -> gesture { it.longPress(a.num("x").toFloat(), a.num("y").toFloat(), a.int("ms", 620).toLong()) }
                "gesture_drag" -> gesture {
                    it.dragArc(a.num("from_x").toFloat(), a.num("from_y").toFloat(), a.num("to_x").toFloat(), a.num("to_y").toFloat(), a.int("ms", 340).toLong())
                }
                "gesture_hold_drag" -> gesture {
                    it.holdAndDrag(a.num("from_x").toFloat(), a.num("from_y").toFloat(), a.num("to_x").toFloat(), a.num("to_y").toFloat())
                }
                "gesture_drag_element" -> gesture {
                    it.dragElement(a.str("text"), a.num("dx").toFloat(), a.num("dy").toFloat())
                }
                "gesture_pinch" -> gesture {
                    it.pinch(a.bool("zoom_in", true), null, null)
                }
                "gesture_swipe" -> gesture {
                    when (a.str("direction")) {
                        "up" -> it.swipeVertical(down = false)
                        "down" -> it.swipeVertical(down = true)
                        "left" -> it.swipeHorizontal(right = false)
                        else -> it.swipeHorizontal(right = true)
                    }
                }
                "gesture_two_finger_scroll" -> gesture { it.twoFingerScroll(a.bool("down", true)) }
                "gesture_rotate" -> gesture { it.rotate(a.num("degrees").toFloat().takeIf { d -> d != 0f } ?: 45f) }
                "human_tap" -> gesture { it.humanTap(a.num("x").toFloat(), a.num("y").toFloat()) }
                "human_scroll" -> gesture { it.humanScroll(a.bool("down", true), a.int("bursts", 1)) }
                "human_slider" -> gesture {
                    it.humanSlider(a.str("text"), a.num("percent").toFloat() / 100f)
                }
                "deliberate_tap" -> gesture {
                    it.deliberateTap(a.num("x").toFloat(), a.num("y").toFloat())
                }
                "wander" -> gesture { it.wander() }
                "verify_task" -> {
                    val arr = a.optJSONArray("keywords")
                    val list = (0 until (arr?.length() ?: 0)).map { arr!!.optString(it) }
                    tools.verifyTaskDone(list)
                }
                "open_carrom" -> tools.openCarromBoard()
                "strike_carrom" -> tools.strikeCarrom()
                "gesture_multi_finger" -> gesture {
                    it.multiFinger(
                        a.int("fingers", 3),
                        a.num("dx").toFloat(),
                        a.num("dy").toFloat(),
                        a.int("ms", 280).toLong()
                    )
                }
                "open_notifications" -> gesture { it.openNotifications() }
                "back" -> gesture { it.gestureBack() }

                // ---- agentic
                "run_goal" -> {
                    val goal = a.str("goal")
                    val service = JimiAccessibilityService.instance
                    if (service == null) "Accessibility বন্ধ — কাজটা করতে পারব না"
                    else com.saathi.ai.access.JimiAccessibilityService.autopilot(goal) { step ->
                        SessionHub.update { st -> st.copy(bubbleMsg = step) }
                    }
                }

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


                // ================================================================ v2
                // ---- root bridge: the escalation path when a permission blocks work
                "root_status" -> com.saathi.ai.access.RootBridge.status(context)
                "root_exec" -> com.saathi.ai.access.RootBridge
                    .exec(a.str("cmd"), a.int("timeout", 25000).toLong(), a.bool("root", true))
                    .report()
                "root_grant_self" -> com.saathi.ai.access.RootBridge.grantSelf(context)
                "root_enable_accessibility" -> com.saathi.ai.access.RootBridge.enableAccessibility(context)
                "root_default_assistant" -> com.saathi.ai.access.RootBridge.setDefaultAssistant(context)
                "system_setting_put" -> com.saathi.ai.access.RootBridge
                    .putSetting(a.str("namespace").ifBlank { "system" }, a.str("key"), a.str("value")).report()
                "system_setting_get" -> com.saathi.ai.access.RootBridge
                    .getSetting(a.str("namespace").ifBlank { "system" }, a.str("key"))
                "root_input" -> {
                    val rb = com.saathi.ai.access.RootBridge
                    when (a.str("action")) {
                        "tap" -> rb.inputTap(a.int("x", 0), a.int("y", 0)).report()
                        "swipe" -> rb.inputSwipe(
                            a.int("x1", 0), a.int("y1", 0), a.int("x2", 0), a.int("y2", 0), a.int("ms", 300)
                        ).report()
                        "text" -> rb.inputText(a.str("text")).report()
                        "key" -> rb.inputKey(a.str("key")).report()
                        else -> "action বলো: tap / swipe / text / key (রুট থাকলে অ্যাক্সেসিবিলিটি ছাড়াও ইনপুট দেওয়া যায়)"
                    }
                }
                "root_clipboard" -> com.saathi.ai.access.RootBridge.setClipboard(a.str("text")).report()
                "root_radio" -> {
                    val rb = com.saathi.ai.access.RootBridge
                    val on = a.bool("on", true)
                    when (a.str("what")) {
                        "wifi" -> rb.wifi(on).report()
                        "data" -> rb.data(on).report()
                        "airplane" -> rb.airplane(on).report()
                        "torch" -> rb.torch(on).report()
                        else -> "what বলো: wifi / data / airplane / torch"
                    }
                }
                "app_control" -> {
                    val rb = com.saathi.ai.access.RootBridge
                    val pkg = a.str("package").ifBlank {
                        com.saathi.ai.access.AppDirectory.resolve(context, a.str("name")) ?: ""
                    }
                    if (pkg.isBlank()) "অ্যাপের প্যাকেজ নাম পাওয়া যায়নি"
                    else when (a.str("action")) {
                        "uninstall" -> rb.uninstall(pkg).report()
                        "freeze", "disable" -> rb.freeze(pkg).report()
                        "unfreeze", "enable" -> rb.unfreeze(pkg).report()
                        "clear" -> rb.clearData(pkg).report()
                        "stop" -> rb.forceStop(pkg).report()
                        else -> "action বলো: uninstall / freeze / unfreeze / clear / stop"
                    }
                }
                "install_apk" -> com.saathi.ai.access.RootBridge.installApk(a.str("path")).report()
                "system_info" -> {
                    val rb = com.saathi.ai.access.RootBridge
                    when (a.str("kind")) {
                        "storage", "disk" -> rb.storage()
                        "processes", "top" -> rb.topProcesses()
                        "logcat", "logs" -> rb.logcat(a.int("lines", 60), a.str("filter").ifBlank { null })
                        "props", "device" -> rb.props()
                        else -> rb.props() + "\n" + rb.storage()
                    }
                }
                "root_file" -> {
                    val rb = com.saathi.ai.access.RootBridge
                    if (a.str("action") == "write") rb.writeFile(a.str("path"), a.str("content")).report()
                    else rb.readFile(a.str("path"), a.int("limit", 4000))
                }

                // ---- real terminal work (shell + Termux), read back and continue
                "shell" -> term.shell(a.str("cmd"), a.int("timeout", 25000).toLong(), a.bool("root", true))
                "shell_background" -> term.background(a.str("cmd"), a.str("tag").ifBlank { "job" })
                "shell_job_output" -> term.jobOutput(a.int("id", -1), a.int("tail", 40))
                "shell_jobs" -> term.listJobs()
                "shell_drive" -> term.selfDrive(a.str("cmd"), a.int("rounds", 4))
                "termux_run" -> term.runInTermux(
                    a.str("cmd"), a.str("workdir").ifBlank { null }, a.bool("background", true)
                )
                "termux_result" -> term.termuxResult()
                "termux_enable_external" -> term.enableTermuxExternalApps()
                "termux_open" -> term.openTermux()
                "termux_type" -> term.typeIntoTermux(a.str("cmd"), a.bool("enter", true))
                "terminal_diagnostics" -> term.diagnostics()

                // ---- vision without screen-share: shoot, look, delete, shoot again
                "screen_look" -> com.saathi.ai.access.ScreenVision.look(
                    context, a.bool("keep", false), a.str("note").ifBlank { null }
                )
                "screen_look_again" -> com.saathi.ai.access.ScreenVision.lookAgain(context)
                "screen_grid" -> com.saathi.ai.access.ScreenVision.grid(context, a.int("cols", 4), a.int("rows", 6))
                "screen_tap_cell" -> com.saathi.ai.access.ScreenVision.tapCell(
                    context, a.str("cell"), a.int("cols", 4), a.int("rows", 6)
                )
                "screen_locate" -> {
                    val p = com.saathi.ai.access.ScreenVision.locateByDump(a.str("label"))
                    if (p == null) "«${a.str("label")}» স্ক্রিনে পাওয়া যায়নি — screen_clickables দিয়ে তালিকা দেখো"
                    else "«${a.str("label")}» আছে x=${p.first}, y=${p.second} — gesture_tap বা human_tap দিয়ে চাপো"
                }
                "screen_clickables" -> com.saathi.ai.access.ScreenVision.clickableMap(a.int("limit", 40))
                "screen_delete_shots" -> com.saathi.ai.access.ScreenVision.deleteAllShots(context)

                // ---- app directory: hidden/renamed apps, widgets, deep links
                "app_resolve" -> {
                    val pkg = com.saathi.ai.access.AppDirectory.resolve(context, a.str("name"))
                    if (pkg == null) "«${a.str("name")}» নামে কিছু ইনস্টল নেই — app_hidden/app_renamed দেখো, " +
                        "নয়তো app_install দিয়ে Play Store খোলো"
                    else "প্যাকেজ: $pkg"
                }
                "app_launch_any" -> com.saathi.ai.access.AppDirectory.launch(context, a.str("name"))
                "app_list_all" -> com.saathi.ai.access.AppDirectory.summary(context)
                "app_hidden" -> com.saathi.ai.access.AppDirectory.hidden(context)
                "app_system_list" -> com.saathi.ai.access.AppDirectory.systemApps(context)
                "app_renamed" -> com.saathi.ai.access.AppDirectory.suspiciousLabels(context)
                "app_widgets" -> com.saathi.ai.access.AppDirectory.widgets(context)
                "app_widget_instances" -> com.saathi.ai.access.AppDirectory.widgetInstances(context)
                "app_deeplink" -> com.saathi.ai.access.AppDirectory.openDeepLink(context, a.str("kind"), a.str("payload"))
                "app_install" -> com.saathi.ai.access.AppDirectory.install(context, a.str("name"))

                // ---- the to-do board: plan it, run it in background, tick it
                "task_list" -> board.boardText(a.int("limit", 24))
                "task_add" -> board.add(a.str("title"), a.str("detail")).let { "তালিকায় যোগ করেছি: ${it.title} (id ${it.id})" }
                "task_plan" -> {
                    val arr = a.optJSONArray("steps")
                    val steps = if (arr == null) emptyList() else (0 until arr.length()).map { arr.optString(it) }
                    board.addPlan(steps.filter { it.isNotBlank() })
                }
                "task_update" -> {
                    val st = when (a.str("status").lowercase()) {
                        "done", "finished", "সম্পন্ন", "শেষ" -> com.saathi.ai.task.TaskBoard.Status.DONE
                        "running", "চলছে" -> com.saathi.ai.task.TaskBoard.Status.RUNNING
                        "failed", "ব্যর্থ" -> com.saathi.ai.task.TaskBoard.Status.FAILED
                        "blocked", "আটকে" -> com.saathi.ai.task.TaskBoard.Status.BLOCKED
                        else -> com.saathi.ai.task.TaskBoard.Status.PENDING
                    }
                    val t = board.update(a.str("id"), st, a.str("note"))
                    if (t == null) "ওই আইডির কাজ খুঁজে পাইনি — task_list দেখো"
                    else "টিক দিয়েছি: ${t.title} → ${t.status}"
                }
                "task_start" -> {
                    if (!prefs.backgroundTasks) "ব্যাকগ্রাউন্ড টাস্ক সেটিংসে বন্ধ আছে — চালু করলে চালাব"
                    else {
                        com.saathi.ai.task.TaskRunnerService.start(context, a.str("id").ifBlank { null })
                        "ব্যাকগ্রাউন্ড ওয়ার্কার চালু করেছি। নোটিফিকেশনে অগ্রগতি দেখাবে, কাজ শেষ হলে টিক পড়বে।"
                    }
                }
                "task_timer" -> {
                    val mins = a.int("minutes", 10).coerceIn(1, 60)
                    com.saathi.ai.task.TaskRunnerService.workFor(context, a.str("goal"), mins * 60)
                    "$mins মিনিট ধরে «${a.str("goal")}» নিয়ে কাজ করব — থামালে task_stop বলো।"
                }
                "task_stop" -> {
                    com.saathi.ai.task.TaskRunnerService.stop(context)
                    "ব্যাকগ্রাউন্ড ওয়ার্কার থামিয়েছি"
                }
                "task_clear" -> if (a.str("what") == "all") board.clearAll() else board.clearFinished()
                "task_progress" -> board.progressText().ifBlank { "তালিকা খালি" }

                // ---- the never-give-up solver
                "solve" -> {
                    val svc = JimiAccessibilityService.instance
                    if (svc == null) "Accessibility বন্ধ — solve চলবে না, আগে চালু করো"
                    else {
                        val goal = a.str("goal")
                        SessionHub.update { it.copy(taskText = goal) }
                        val report = com.saathi.ai.access.Solver.runWithEscalation(context, svc, goal) { step ->
                            SessionHub.update { it.copy(lastTool = step) }
                        }
                        val verdict = com.saathi.ai.access.Solver.classify(report)
                        SessionHub.update {
                            it.copy(taskText = "", taskProgress = if (verdict == com.saathi.ai.access.Solver.Verdict.DONE) 100 else it.taskProgress)
                        }
                        com.saathi.ai.access.Solver.oneLine(report) + "\n" + report.take(1500) +
                            "\n" + com.saathi.ai.access.Solver.advice(report)
                    }
                }
                "solve_alternative" -> {
                    val svc = JimiAccessibilityService.instance
                    if (svc == null) "Accessibility বন্ধ"
                    else com.saathi.ai.access.Solver
                        .runAlternative(context, svc, a.str("goal"), a.str("previous_report"))
                        .take(1600)
                }

                // ---- drawing and editing: Jimi makes the picture itself
                "draw_image" -> com.saathi.ai.create.ArtEngine.drawAndSave(
                    context, a.str("prompt"), a.int("size", 1080)
                )
                "draw_poster" -> {
                    val bmp = com.saathi.ai.create.ArtEngine.draw(a.str("prompt"), a.int("size", 1080))
                    com.saathi.ai.create.ArtEngine.withText(bmp, a.str("text"))
                    val dir = java.io.File(context.getExternalFilesDir(null) ?: context.filesDir, "art").apply { mkdirs() }
                    val f = java.io.File(dir, "jimi-poster-${System.currentTimeMillis()}.png")
                    val ok = runCatching {
                        f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 95, it) }
                    }.isSuccess
                    bmp.recycle()
                    if (ok) "পোস্টার বানিয়েছি: ${f.absolutePath}" else "এঁকেছি কিন্তু সেভ করা যায়নি"
                }
                "generate_image" -> imageGen.generate(a.str("prompt"), a.str("aspect").ifBlank { "1:1" })
                "edit_image_ai" -> imageGen.edit(a.str("path"), a.str("instruction"))
                "edit_image" -> editor.edit(a.str("path"), a.str("ops"))
                "collage" -> {
                    val arr = a.optJSONArray("paths")
                    val paths = if (arr == null) listOf(a.str("path1"), a.str("path2"))
                    else (0 until arr.length()).map { arr.optString(it) }
                    editor.collage(paths.filter { it.isNotBlank() }, a.bool("vertical", false))
                }
                "set_wallpaper" -> {
                    val bmp = media.loadBitmap(a.str("path"))
                    if (bmp == null) "ছবি পড়া যায়নি: ${a.str("path")}"
                    else runCatching {
                        val wm = context.getSystemService(Context.WALLPAPER_SERVICE) as android.app.WallpaperManager
                        if (a.str("which") == "lock") {
                            wm.setBitmap(bmp, null, true, android.app.WallpaperManager.FLAG_LOCK)
                        } else {
                            wm.setBitmap(bmp)
                        }
                        bmp.recycle()
                        "ওয়ালপেপার সেট করেছি"
                    }.getOrElse { "ওয়ালপেপার সেট করা যায়নি: ${it.message}" }
                }
                "share_image_file" -> smart.shareImage(a.str("path"))

                // ---- health check and self-repair
                "health_check" -> health.report(a.bool("api", true))
                "health_fix" -> health.selfFix(true)
                "health_headline" -> health.headline()

                // ---- games on the phone's own apps
                "game_open" -> games.open(a.str("name"))
                "game_play" -> games.play(a.str("name"), a.str("action").ifBlank { null })
                "game_status" -> games.status()

                // ---- restricted input fields: type, and if the field blocks IME,
                //      clipboard + long-press + Paste (root clipboard when available)
                "fill_field" -> fillField(a.str("label"), a.str("text"))
                "paste_into" -> pasteInto(a.str("label"))

                else -> v3Tool(name, a) ?: "Unknown tool: $name"
            }
        } catch (t: Throwable) {
            "Error: ${t.message ?: t.javaClass.simpleName}"
        }
    }

    /**
     * Types into a field that may block normal IME injection (banking apps, some
     * WebView forms, secure fields). Order: focus → accessibility type → root input →
     * clipboard + Paste menu. It always reports which route worked.
     */
    private fun fillField(label: String, text: String): String {
        val svc = JimiAccessibilityService.instance
            ?: return "Accessibility বন্ধ — ফিল্ডে লেখা যাবে না"
        if (text.isBlank()) return "কী লিখবে সেটা বলো"

        val bounds = if (label.isBlank()) null else svc.boundsOf(label)
        if (label.isNotBlank() && bounds == null) {
            return "«$label» নামের ফিল্ড স্ক্রিনে পাওয়া যায়নি। screen_look বা read_screen দিয়ে দেখে " +
                "ঠিক লেবেল বলো।"
        }
        if (bounds != null) {
            svc.gestures.humanTap(bounds.centerX().toFloat(), bounds.centerY().toFloat())
            Thread.sleep(500)
        }

        if (svc.typeText(text)) return "ফিল্ডে লিখেছি${if (label.isBlank()) "" else " («$label»)"}"

        val rootTyped = if (com.saathi.ai.access.RootBridge.available()) {
            com.saathi.ai.access.RootBridge.inputText(text).ok
        } else false
        if (rootTyped) return "ফিল্ডে লিখেছি (রুট ইনপুট দিয়ে) — সাধারণ টাইপিং ব্লক ছিল"

        // last resort: clipboard + the field's own Paste menu
        val clipped = smart.copy(text)
        if (bounds != null) {
            svc.gestures.longPress(bounds.centerX().toFloat(), bounds.centerY().toFloat(), 720)
        } else {
            svc.gestures.longPress(svc.screenWidth() / 2f, svc.screenHeight() / 2f, 720)
        }
        Thread.sleep(700)
        val pasted = svc.tapByText("Paste") || svc.tapByText("পেস্ট") ||
            svc.tapByText("Paste as plain text")
        return if (pasted) "পেস্ট মেনু থেকে বসিয়েছি ($clipped)"
        else "এই ফিল্ড টাইপও নেয় না, পেস্ট মেনুও খোলেনি। টেক্সট ক্লিপবোর্ডে রেখেছি ($clipped) — " +
            "তুমি একবার চেপে ধরে Paste চাপো, অথবা রুট থাকলে root_input(text) চেষ্টা করব।"
    }

    /** Pastes whatever is on the clipboard into a named field. */
    private fun pasteInto(label: String): String {
        val svc = JimiAccessibilityService.instance ?: return "Accessibility বন্ধ"
        val bounds = if (label.isBlank()) null else svc.boundsOf(label)
        if (label.isNotBlank() && bounds == null) return "«$label» ফিল্ড পাওয়া যায়নি"
        val x = bounds?.centerX()?.toFloat() ?: svc.screenWidth() / 2f
        val y = bounds?.centerY()?.toFloat() ?: svc.screenHeight() / 2f
        svc.gestures.humanTap(x, y)
        Thread.sleep(400)
        svc.gestures.longPress(x, y, 720)
        Thread.sleep(700)
        val pasted = svc.tapByText("Paste") || svc.tapByText("পেস্ট") || svc.tapByText("Paste as plain text")
        return if (pasted) "পেস্ট করেছি" else "পেস্ট মেনু খোলেনি — ক্লিপবোর্ডের লেখা: ${smart.paste()}"
    }

    private inline fun gesture(block: (com.saathi.ai.access.GestureEngine) -> Boolean): String {
        val svc = JimiAccessibilityService.instance ?: return "Accessibility বন্ধ — চালু করো"
        return if (block(svc.gestures)) "হয়েছে" else "হয়নি"
    }

    private fun JSONObject.str(key: String): String = optString(key, "")
    private fun JSONObject.num(key: String): Int = optDouble(key, 0.0).toInt()
    private fun JSONObject.bool(key: String, fallback: Boolean): Boolean =
        if (has(key)) optBoolean(key, fallback) else fallback
    private fun JSONObject.int(key: String, fallback: Int): Int =
        if (has(key)) optDouble(key, fallback.toDouble()).toInt() else fallback


    // =================================================================== v3 tools

    /**
     * Second-generation tool set (Phase 3-18 of the v3 upgrade): task engine,
     * multi-agent orchestration, visual computer use, browser, research, audio,
     * video, image lab, document studio, memory, automations, daily assistant and
     * the system-control surface (Shizuku, permissions, kill switch, error centre).
     *
     * Returns null for names it does not own, so the main `when` still reports
     * "Unknown tool" for anything genuinely missing.
     */
    private fun v3Tool(name: String, a: JSONObject): String? {
        fun res(r: com.saathi.ai.agent.AgentResult): String =
            "${r.verdictLine()}\n${r.report}".trim() +
                if (r.artifacts.isNotEmpty()) "\nফাইল/আউটপুট:\n" + r.artifacts.joinToString("\n") { "• $it" } else ""
        fun goals(raw: String): List<String> =
            raw.split("|").map { it.trim() }.filter { it.isNotBlank() }

        return when (name) {
            // ---- autonomous task engine
            "engine_start" -> engine.start(
                context, a.str("goal"), a.int("priority", 3),
                a.num("deadline_minutes") * 60, a.int("budget", prefs.agentBudget)
            )
            "engine_start_many" -> engine.startMany(context, goals(a.str("goals")), a.int("priority", 3))
            "engine_timer" -> engine.startAfter(
                context, a.str("goal"), a.num("delay_minutes") * 60, a.int("priority", 3)
            )
            "engine_pause" -> engine.pause(a.str("id"))
            "engine_resume" -> engine.resume(context, a.str("id"))
            "engine_cancel" -> engine.cancel(a.str("id"))
            "engine_cancel_all" -> engine.cancelAll()
            "engine_retry" -> engine.retry(context, a.str("id"))
            "engine_status" -> engine.statusText()
            "engine_report" -> engine.report(a.str("id"))
            "engine_headline" -> engine.headline()
            "engine_clear" -> engine.clearFinished()
            "engine_recover" -> engine.recover(context)

            // ---- multi-agent orchestration
            "agent_plan" -> res(orch.runPlan(a.str("goal"), budgetSteps = a.int("budget", 22)))
            "agent_run" -> res(
                orch.run(
                    a.str("goal"), budgetSteps = a.int("budget", prefs.agentBudget),
                    detail = a.str("detail")
                )
            )
            "agent_parallel" -> {
                val g = goals(a.str("goals"))
                if (g.isEmpty()) "ব্যর্থ: কোনো লক্ষ্য দেওয়া হয়নি — ' | ' দিয়ে আলাদা করে লেখো"
                else orch.parallel(g, List(g.size) { "par-$it" }).joinToString("\n\n") { (id, r) ->
                    "[$id] ${r.verdictLine()}\n${r.report.take(700)}"
                }
            }
            "agent_status" -> com.saathi.ai.agent.ResourceLocks.status() +
                "\nএজেন্ট: planner, research, browser, visual, file, terminal, code, media, audio, video, recovery"

            // ---- visual computer use
            "screen_agent" -> res(
                orch.run(
                    a.str("goal"),
                    budgetSteps = a.int("budget", 10),
                    detail = "স্ক্রিন দেখে করতে হবে — ভিজ্যুয়াল কম্পিউটার এজেন্ট ব্যবহার করো"
                )
            )
            "vision_fill" -> visual.fill(context, a.str("label"), a.str("text"))
            "vision_status" -> visual.status(context)

            // ---- browser workspace
            "browser_open" -> web.open(context, a.str("url"))
            "browser_search" -> web.search(context, a.str("query"))
            "browser_text" -> web.text(context, a.int("max_chars", 12_000))
            "browser_links" -> web.links(context, a.int("limit", 20))
            "browser_fields" -> web.fields(context, a.int("limit", 20))
            "browser_form_fill" -> web.fill(context, a.str("label"), a.str("value"))
            "browser_click_text" -> web.clickText(context, a.str("text"))
            "browser_shot" -> web.shot(context, a.bool("keep", false))
            "browser_back" -> web.back(context)
            "browser_forward" -> web.forward(context)
            "browser_reload" -> web.reload(context)
            "browser_new_tab" -> web.newTab(context, a.str("url"))
            "browser_tabs" -> web.tabs()
            "browser_switch_tab" -> web.switchTab(a.num("id"))
            "browser_close_tab" -> web.closeTab(context, a.num("id"))
            "browser_close_all" -> web.closeAll(context)
            "browser_download" -> web.download(context, a.str("url"), null, a.str("mime").ifBlank { null }, null)
            "browser_downloads" -> web.downloads(context)
            "browser_status" -> web.status(context)
            "browser_clear" -> web.clear(context)
            "browser_home" -> {
                val site = a.str("site").lowercase()
                val url = when (site) {
                    "google" -> "https://www.google.com"
                    "duckduckgo", "ddg" -> "https://duckduckgo.com"
                    "bing" -> "https://www.bing.com"
                    "youtube" -> "https://www.youtube.com"
                    else -> site
                }
                prefs.browserHome = url
                "সফল: ব্রাউজারের শুরু পেজ সেট হয়েছে — $url"
            }

            // ---- research engine
            "research_run" -> research.research(a.str("query"), a.int("sources", prefs.researchSources))
            "research_summary" -> research.summarize(a.str("text"), a.str("ask"))
            "research_cache" -> research.cacheStats()
            "research_clear_cache" -> research.clearCache()

            // ---- audio: generation, playback, processing
            "audio_speak" -> {
                val out = audioGen.speak(
                    a.str("text"),
                    a.str("voice").ifBlank { prefs.ttsVoice },
                    a.str("style")
                )
                if (a.bool("play", false)) {
                    val path = Regex("/[^\\s:]+\\.wav").find(out)?.value
                    if (path != null) out + "\n" + playback.play(context, path, java.io.File(path).name)
                    else out + "\n(ব্যাকগ্রাউন্ডে চালানো যায়নি — ফাইল পাথ পাওয়া যায়নি)"
                } else out
            }
            "audio_dialogue" -> audioGen.dialogue(a.str("script"))
            "audio_ambient" -> {
                val out = audioGen.ambient(a.str("kind"), a.int("seconds", 120))
                if (a.bool("play", false)) {
                    val path = Regex("/[^\\s:]+\\.wav").find(out)?.value
                    if (path != null) out + "\n" + playback.play(context, path, java.io.File(path).name, true)
                    else out + "\n(ব্যাকগ্রাউন্ডে চালানো যায়নি — ফাইল পাথ পাওয়া যায়নি)"
                } else out
            }
            "audio_voices" -> audioGen.voices()
            "audio_library" -> audioGen.library()
            "audio_play" -> playback.play(
                context, a.str("path"),
                java.io.File(a.str("path")).name, a.bool("loop", false)
            )
            "audio_pause" -> playback.pause(context)
            "audio_resume" -> playback.resume(context)
            "audio_stop" -> playback.stop(context)
            "audio_play_status" -> playback.status()
            "audio_denoise" -> audioLab.denoise(
                a.str("path"),
                if (a.has("strength")) a.optDouble("strength", 0.8).toFloat() else 0.8f
            )
            "audio_trim" -> audioLab.trim(a.str("path"), a.num("start_seconds"), a.num("end_seconds"))
            "audio_transcribe" -> audioLab.transcribe(a.str("path"))
            "audio_summarize" -> audioLab.summarize(a.str("path"))
            "audio_speakers" -> audioLab.speakerTags(a.str("path"))

            // ---- video
            "video_summary" -> videoLab.summarize(a.str("path"))
            "video_scenes" -> videoLab.scenes(
                a.str("path"),
                if (a.has("threshold")) a.optDouble("threshold", 0.24) else 0.24
            )
            "video_subtitles" -> videoLab.subtitles(a.str("path"))
            "video_frame" -> videoLab.posterFrame(a.str("path"), a.num("at_seconds") * 1000L)
            "video_edit_hint" -> videoLab.trimHint(a.str("path"), a.str("goal"))

            // ---- image lab
            "image_ocr" -> visionLab.ocr(a.str("path"))
            "image_detect" -> visionLab.detect(a.str("path"))
            "image_compare" -> visionLab.compare(a.str("path_a"), a.str("path_b"), a.str("question"))
            "image_enhance" -> visionLab.enhance(a.str("path"), a.int("target_side", 1600))
            "image_remove_bg" -> visionLab.removeBackground(a.str("path"), a.int("tolerance", 42))
            "image_facts" -> visionLab.facts(a.str("path"))
            "image_caption" -> visionLab.caption(a.str("path"), a.str("text"), a.bool("bottom", true))

            // ---- document studio
            "doc_create" -> docs.create(a.str("kind"), a.str("content"), a.str("title"))
            "doc_pdf" -> docs.pdf(a.str("title"), a.str("content"))
            "doc_docx" -> docs.docx(a.str("title"), a.str("content").lines())
            "doc_pptx" -> docs.pptx(a.str("title"), docs.slidesFrom(a.str("content")))
            "doc_html" -> docs.html(a.str("title"), a.str("content"))
            "doc_csv" -> docs.csv(a.str("title"), docs.parseCsv(a.str("content")))
            "doc_list" -> docs.library()
            "doc_capabilities" -> docs.capabilities()

            // ---- memory, history, projects
            "memory_search" -> memSearch.report(a.str("query"), a.int("limit", 10))
            "memory_overview" -> memSearch.overview()
            "memory_forget" -> memSearch.forget(a.str("kind"), a.str("id"))
            "memory_forget_all" -> memSearch.forgetAll(a.str("what"))
            "history_recent" -> history.report(a.int("limit", 8))
            "history_search" -> {
                val hits = history.search(a.str("query"), a.int("limit", 10))
                if (hits.isEmpty()) "কোনো ফল নেই — «${a.str("query")}» টাস্ক ইতিহাসে পাওয়া যায়নি"
                else hits.joinToString("\n") { "[${it.status}] ${it.goal} — ${it.report.lines().firstOrNull()?.take(120)}" }
            }
            "history_stats" -> history.stats()
            "context_brief" -> summarizer.workingContext(a.str("goal"))
            "project_create" -> projects.create(a.str("name"), a.str("note"))
            "project_open" -> projects.open(a.str("name"))
            "project_note" -> projects.addNote(a.str("text"))
            "project_add_file" -> projects.addFile(a.str("path"))
            "project_list" -> projects.list()
            "project_detail" -> projects.detail(a.str("name"))
            "project_remove" -> projects.remove(a.str("name"))

            // ---- automations and macro recording
            "auto_list" -> templates.list()
            "auto_save" -> templates.save(a.str("name"), a.str("goal"), goals(a.str("steps")))
            "auto_run" -> templates.run(a.str("name"), a.int("priority", 3))
            "auto_detail" -> templates.detail(a.str("name"))
            "auto_delete" -> templates.delete(a.str("name"))
            "auto_export" -> templates.export(a.str("name"))
            "auto_import" -> templates.import(a.str("text"))
            "auto_from_task" -> templates.fromHistory(a.str("run_id"), a.str("name"))
            "record_start" -> recorder.start()
            "record_stop" -> recorder.stop()
            "record_preview" -> recorder.preview()
            "record_save" -> recorder.saveAs(context, a.str("name"))
            "record_from_text" -> recorder.fromText(context, a.str("name"), a.str("description"))

            // ---- daily assistant and health
            "daily_report" -> daily.report(a.bool("check_api", false))
            "daily_activity" -> daily.activityBlock()
            "daily_sleep" -> daily.sleepWindow(a.str("text"))
            "daily_medicine" -> daily.medReminder(a.str("medicine"), a.str("time"))
            "daily_appointment" -> daily.appointmentPrep(a.str("title"), a.str("notes"))

            // ---- Shizuku and privileged access
            "shizuku_status" -> shiz.status(context)
            "shizuku_diagnostics" -> shiz.diagnostics(context)
            "shizuku_exec" -> {
                val cmd = a.str("command")
                if (cmd.isBlank()) "ব্যর্থ: কমান্ড খালি"
                else {
                    val risk = gate.classify("shell", cmd)
                    if (risk >= com.saathi.ai.core.ApprovalGate.Risk.HIGH) {
                        val decision = gate.askBlocking(context, "Shizuku কমান্ড", cmd)
                        if (decision.decision != com.saathi.ai.core.ApprovalGate.Decision.APPROVED) {
                            "আটকে: কমান্ডটি অনুমতি ছাড়া চালানো হবে না (${decision.decision}) — $cmd"
                        } else shiz.exec(cmd).report(1200)
                    } else shiz.exec(cmd).report(1200)
                }
            }
            "shizuku_grant" -> shiz.grantPermission(context, a.str("package"), a.str("permission"))
            "shizuku_open" -> shiz.openShizuku(context)

            // ---- permissions, safety, errors, routing
            "permission_dashboard" -> perms.dashboard(context)
            "permission_fix" -> perms.autoFix(context)
            "permission_guide" -> perms.guide(context, a.str("id"))
            "kill_stop" -> kill.stopAll(context)
            "error_report" -> if (a.str("module").isNotBlank()) errs.forModule(a.str("module"))
                else errs.report(a.int("limit", 18))
            "error_clear" -> { errs.clear(); "সফল: এরর লগ মুছে ফেলা হয়েছে" }
            "approval_status" -> gate.status() + "\n" + gate.historyText(8)
            "approval_decide" -> {
                gate.decideCurrent(a.bool("approved", false))
                if (a.bool("approved", false)) "অনুমতি দেওয়া হয়েছে — কাজ চলবে" else "অনুমতি দেওয়া হয়নি — কাজ আটকানো হয়েছে"
            }
            "model_router" -> router.status(context)
            "model_free" -> router.freeMemory(context)

            else -> null
        }
    }

    companion object {
        private const val DUPLICATE_WINDOW_MS = 12_000L
    }
}
