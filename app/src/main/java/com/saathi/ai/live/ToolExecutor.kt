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
                    .joinToString("\n") { (if (it.isDir) "📁 " else "📄 ") + it.name + (if (it.isDir) "" else "  " + files.humanSize(it.size)) }
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

                else -> "Unknown tool: $name"
            }
        } catch (t: Throwable) {
            "Error: ${t.message ?: t.javaClass.simpleName}"
        }
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

    companion object {
        private const val DUPLICATE_WINDOW_MS = 12_000L
    }
}
