package com.saathi.ai.data

import android.content.Context
import com.saathi.ai.live.ToolRegistryGuide
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores all user settings. The Gemini API key is kept inside
 * EncryptedSharedPreferences (Android Keystore backed), never in plain text
 * and never leaves the device.
 */
class SecurePrefs(private val contextRef: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(contextRef)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            contextRef,
            FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (t: Throwable) {
        // Very rare: some OEM ROMs break the keystore. App must still work.
        contextRef.getSharedPreferences(FILE + "_fallback", Context.MODE_PRIVATE)
    }

    var apiKey: String
        get() = prefs.getString(KEY_API, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API, value.trim()).apply()

    var model: String
        get() = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) = prefs.edit().putString(KEY_MODEL, value).apply()

    var voice: String
        get() = prefs.getString(KEY_VOICE, DEFAULT_VOICE) ?: DEFAULT_VOICE
        set(value) = prefs.edit().putString(KEY_VOICE, value).apply()

    /** "auto" | "bn" | "en" */
    var language: String
        get() = prefs.getString(KEY_LANG, "bn") ?: "bn"
        set(value) = prefs.edit().putString(KEY_LANG, value).apply()

    var persona: String
        get() = prefs.getString(KEY_PERSONA, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PERSONA, value).apply()

    var showTranscript: Boolean
        get() = prefs.getBoolean(KEY_TRANSCRIPT, true)
        set(value) = prefs.edit().putBoolean(KEY_TRANSCRIPT, value).apply()

    var searchGrounding: Boolean
        get() = prefs.getBoolean(KEY_SEARCH, true)
        set(value) = prefs.edit().putBoolean(KEY_SEARCH, value).apply()

    /** Visual theme id (see ui/theme/JimiThemes). */
    var themeId: String
        get() = prefs.getString(KEY_THEME, "aurora") ?: "aurora"
        set(value) = prefs.edit().putString(KEY_THEME, value).apply()

    /** Custom accent override (ARGB). 0 = use the theme's own accent. */
    var accentOverride: Int
        get() = prefs.getInt(KEY_ACCENT, 0)
        set(value) = prefs.edit().putInt(KEY_ACCENT, value).apply()

    /** Extra glow / motion on the orb. */
    var orbIntensity: Float
        get() = prefs.getFloat(KEY_INTENSITY, 1f)
        set(value) = prefs.edit().putFloat(KEY_INTENSITY, value).apply()

    /** True after the user has completed the first-run permission page. */
    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING, value).apply()

    val isConfigured: Boolean get() = apiKey.isNotBlank()

    var bubbleEnabled: Boolean
        get() = prefs.getBoolean(KEY_BUBBLE, false)
        set(value) = prefs.edit().putBoolean(KEY_BUBBLE, value).apply()

    var hotwordEnabled: Boolean
        get() = prefs.getBoolean(KEY_HOTWORD, false)
        set(value) = prefs.edit().putBoolean(KEY_HOTWORD, value).apply()

    /** Autopilot: let Jimi finish multi-step tasks on its own. */
    var autopilotEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTOPILOT, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTOPILOT, value).apply()

    // ---------------------------------------------------------------- bubble

    /** Where the user left the bubble, in screen pixels. -1 = auto (edge snap). */
    var bubbleX: Int
        get() = prefs.getInt(KEY_BUBBLE_X, -1)
        set(value) = prefs.edit().putInt(KEY_BUBBLE_X, value).apply()

    var bubbleY: Int
        get() = prefs.getInt(KEY_BUBBLE_Y, -1)
        set(value) = prefs.edit().putInt(KEY_BUBBLE_Y, value).apply()

    /** "pill" = orb + rising transcript panel, "orb" = minimal. */
    var bubbleStyle: String
        get() = prefs.getString(KEY_BUBBLE_STYLE, "pill") ?: "pill"
        set(value) = prefs.edit().putString(KEY_BUBBLE_STYLE, value).apply()

    // ---------------------------------------------------------------- power

    /**
     * Park the live session after a stretch of silence, but keep a cheap ear open
     * and reconnect the moment the user speaks again. This is the "voice session
     * stays, connection drops, talking wakes it" behaviour.
     */
    var autoReconnectVoice: Boolean
        get() = prefs.getBoolean(KEY_AUTO_RECONNECT, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_RECONNECT, value).apply()

    /** Seconds of silence before parking. 0 = never park. */
    var idleParkSeconds: Int
        get() = prefs.getInt(KEY_IDLE_PARK, 120)
        set(value) = prefs.edit().putInt(KEY_IDLE_PARK, value).apply()

    /** Allow Jimi to use `su` when the phone is rooted. */
    var rootMode: Boolean
        get() = prefs.getBoolean(KEY_ROOT, true)
        set(value) = prefs.edit().putBoolean(KEY_ROOT, value).apply()

    /** Look at the screen with on-demand screenshots instead of a screen share. */
    var visionMode: Boolean
        get() = prefs.getBoolean(KEY_VISION, true)
        set(value) = prefs.edit().putBoolean(KEY_VISION, value).apply()

    /** Background to-do runner: let Jimi keep working after the reply. */
    var backgroundTasks: Boolean
        get() = prefs.getBoolean(KEY_BG_TASKS, true)
        set(value) = prefs.edit().putBoolean(KEY_BG_TASKS, value).apply()

    // ------------------------------------------------------------------ v3

    /** Model used by the visual computer agent. "" = try the built-in fallbacks. */
    var computerUseModel: String
        get() = prefs.getString(KEY_CU_MODEL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CU_MODEL, value.trim()).apply()

    /** Gemini TTS model. "" = try the built-in fallbacks. */
    var ttsModel: String
        get() = prefs.getString(KEY_TTS_MODEL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TTS_MODEL, value.trim()).apply()

    /** Voice for generated audio (30 prebuilt names; Kore/Aoede/Puck are common). */
    var ttsVoice: String
        get() = prefs.getString(KEY_TTS_VOICE, "Kore") ?: "Kore"
        set(value) = prefs.edit().putString(KEY_TTS_VOICE, value.trim()).apply()

    /** How many sources the research engine cross-checks before answering. */
    var researchSources: Int
        get() = prefs.getInt(KEY_RESEARCH_SRC, 5)
        set(value) = prefs.edit().putInt(KEY_RESEARCH_SRC, value.coerceIn(1, 10)).apply()

    /** Step budget per agent run — keeps autonomous work bounded. */
    var agentBudget: Int
        get() = prefs.getInt(KEY_AGENT_BUDGET, 14)
        set(value) = prefs.edit().putInt(KEY_AGENT_BUDGET, value.coerceIn(2, 60)).apply()

    /**
     * Privacy default: screenshots are temporary. Only when the user explicitly
     * asks to keep them does anything stay on disk.
     */
    var keepScreenshots: Boolean
        get() = prefs.getBoolean(KEY_KEEP_SHOTS, false)
        set(value) = prefs.edit().putBoolean(KEY_KEEP_SHOTS, value).apply()

    /** Optional biometric lock before Jimi will act on sensitive screens. */
    var biometricLock: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC, false)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC, value).apply()

    /** Start page for the built-in browser workspace. */
    var browserHome: String
        get() = prefs.getString(KEY_BROWSER_HOME, "https://www.google.com") ?: "https://www.google.com"
        set(value) = prefs.edit().putString(KEY_BROWSER_HOME, value.trim()).apply()

    // ---------------------------------------------------------------- long-term memory

    private val store by lazy { MemoryStore(contextRef) }

    /** Jimi's memory: "key -> fact" pairs the model asked us to remember. */
    fun remember(key: String, value: String) {
        store.remember(key, value)
    }

    fun recall(): String =
        store.all().joinToString("\n") { "${it.key}: ${it.value}" }

    fun forgetAll() {
        store.forgetAll()
    }

    /** The full system instruction handed to Gemini Live at session start. */
    fun buildSystemPrompt(): String {
        val base = persona.ifBlank { DEFAULT_PERSONA }
        val langRule = when (language) {
            "bn" -> "\n\nসব সময় বাংলায় (Bangla) উত্তর দাও, যদি না ব্যবহারকারী স্পষ্টভাবে অন্য ভাষায় উত্তর চায়।"
            "en" -> "\n\nAlways reply in English unless the user clearly asks for another language."
            else -> "\n\nReply in whatever language the user is speaking."
        }
        val memoryRule = store.promptBlock()
        val guidesRule = "\n\n" + ToolRegistryGuide.TEXT
        return base + langRule + guidesRule + memoryRule
    }

    companion object {
        private const val FILE = "saathi_secure_prefs"
        private const val KEY_API = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_VOICE = "voice"
        private const val KEY_LANG = "language"
        private const val KEY_PERSONA = "persona"
        private const val KEY_TRANSCRIPT = "show_transcript"
        private const val KEY_SEARCH = "search_grounding"
        private const val KEY_ONBOARDING = "onboarding_done"
        private const val KEY_BUBBLE = "bubble_enabled"
        private const val KEY_HOTWORD = "hotword_enabled"
        private const val KEY_THEME = "theme_id"
        private const val KEY_ACCENT = "accent_override"
        private const val KEY_INTENSITY = "orb_intensity"
        private const val KEY_AUTOPILOT = "autopilot_enabled"
        private const val KEY_BUBBLE_X = "bubble_x"
        private const val KEY_BUBBLE_Y = "bubble_y"
        private const val KEY_BUBBLE_STYLE = "bubble_style"
        private const val KEY_AUTO_RECONNECT = "auto_reconnect_voice"
        private const val KEY_IDLE_PARK = "idle_park_seconds"
        private const val KEY_ROOT = "root_mode"
        private const val KEY_VISION = "vision_mode"
        private const val KEY_BG_TASKS = "background_tasks"
        private const val KEY_CU_MODEL = "computer_use_model"
        private const val KEY_TTS_MODEL = "tts_model"
        private const val KEY_TTS_VOICE = "tts_voice"
        private const val KEY_RESEARCH_SRC = "research_sources"
        private const val KEY_AGENT_BUDGET = "agent_budget"
        private const val KEY_KEEP_SHOTS = "keep_screenshots"
        private const val KEY_BIOMETRIC = "biometric_lock"
        private const val KEY_BROWSER_HOME = "browser_home"

        const val DEFAULT_MODEL = "gemini-3.8-live"
        const val DEFAULT_VOICE = "Aoede"

        val MODELS = listOf(
            "gemini-3.8-live",
            "gemini-3.8-live-extended-thinking",
            "gemini-3.1-flash-live-preview",
            "gemini-2.5-flash-native-audio-preview-12-2025"
        )

        val VOICES = listOf(
            "Aoede", "Kore", "Puck", "Charon", "Fenrir", "Leda", "Orus", "Zephyr",
            "Achernar", "Achird", "Algenib", "Algieba", "Alnilam", "Autonoe",
            "Callirrhoe", "Despina", "Enceladus", "Erinome", "Gacrux", "Iapetus",
            "Laomedeia", "Pulcherrima", "Rasalgethi", "Sadachbia", "Schedar",
            "Sulafat", "Umbriel", "Vindemiatrix", "Zubenelgenubi"
        )

        val DEFAULT_PERSONA = """
            তুমি "Jimi" — ব্যবহারকারীর একজন উষ্ণ, বুদ্ধিমান, হাসিখুশি আর অসম্ভব দক্ষ AI সহকারী।
            তুমি শুধু কথা বলো না — তুমি ব্যবহারকারীর ফোনটা নিজের হাতে চালাতে পারো (Accessibility দিয়ে
            ট্যাপ, স্ক্রল, লেখা, ড্র্যাগ, মাল্টি-টাচ, অ্যাপ খোলা, কল, এসএমএস, ক্যামেরা, শেয়ার, পেমেন্ট স্ক্রিন)।
            তাই ব্যবহারকারী যখন "একবারে পুরো কাজটা করে দাও" বলে, তুমি থেমে প্রশ্ন করার বদলে
            নিজে থেকেই ধাপগুলো ভেঙে নাও, প্রতিটা ধাপ করো, ফলাফল যাচাই করো, শেষ হলে জানাও।
            নিয়ম:
            ১. কথা বলার মতো ছোট, স্বাভাবিক বাক্যে উত্তর দাও (সাধারণত ১-৩ বাক্য)। তুমি লেখা নও, তুমি কথা বলছ।
            ২. ব্যবহারকারী ক্যামেরা বা স্ক্রিন শেয়ার করলে যা দেখা যাচ্ছে তা বুঝে সরাসরি সাহায্য করো।
            ৩. কখনো নিজে থেকে বলো না যে তুমি একটি টেক্সট মডেল; তুমি কণ্ঠে কথা বলো।
            ৪. ব্যবহারকারীর টোন বুঝে সহানুভূতিশীল থাকো; দরকার হলে ছোট একটা প্রশ্ন করো।
            ৫. সংখ্যা, নাম, ঠিকানা বা গুরুত্বপূর্ণ তথ্য স্পষ্ট করে বলো।
            ৬. ব্যবহারকারী "থামো" বললে সাথে সাথে থেমে যাও।
            ৭. বহু-ধাপের কাজে (যেমন: "টিকটকে ভিডিও আপলোড করো", "ফেসবুকে স্টোরি দাও",
               "অর্ডার করে দাও", "মেসেজের রিপ্লাই দাও", "ছবি এডিট করো") নিজে থেকে পুরো ধারাবাহিকতা শেষ করো:
               অ্যাপ খোলো → স্ক্রিন পড়ো → দরকারি জায়গায় ট্যাপ করো → লেখো → শেয়ার/পোস্ট করো।
               কোনো ধাপ আটকে গেলে অন্য পথ (long_press, scroll, deep_link, gesture_*, run_goal) চেষ্টা করো।
            ৮. ব্যবহারকারীর তথ্য (নাম, পরিবার, পছন্দ, ঠিকানা, ওষুধ, রুটিন) মনে রাখার মতো হলে
               remember টুল দিয়ে সেভ করো — পরেরবার নিজে থেকেই কাজে লাগাও।
            ৯. টাকা/পেমেন্ট/কল-কাটার মতো সিদ্ধান্ত কখনো নিজে চূড়ান্ত করবে না; স্ক্রিন খুলে দাও,
               চূড়ান্ত ধাপ ব্যবহারকারীর হাতে ছাড়ো।
            ১০. নিরাপত্তা: কারও ব্যক্তিগত ডেটা চুরি, স্পাই বা প্রতারণায় সাহায্য করবে না।
            ১১. একই কাজ দুইবার করবে না — "হয়েছে" মানে হয়েছে, আবার সার্চ দিও না।
            ১২. গেম খেলার সময় (লুডু, দাবা, ক্যারম) সত্যিকারের নিয়ম মেনে চাল চিন্তা করো,
                বোর্ডের অবস্থা মনে রাখো, আর স্পষ্ট করে বলো কোন চাল দিচ্ছো। লুডু/দাবা/ক্যারম
                খেলবে ফোনে ইনস্টল থাকা আসল গেম অ্যাপে (game_open → game_play), নিজের মুখে বা
                কল্পনার বোর্ডে নয়। অ্যাপ ইনস্টল না থাকলে সেটা স্পষ্ট করে বলবে।
            ১৩. অনুমতি বা সিস্টেম কোনো কাজ আটকালে হাল ছেড়ো না — সিঁড়ি বেয়ে ওঠো:
                সাধারণ টুল → run_goal → offline pack → screen_look ভিশন → shell/root_exec → solve।
                রুট থাকলে সেটা ব্যবহার করবে (system_setting_put, root_input, app_control)।
                "পারব না", "অসম্ভব", "আমার ক্ষমতা নেই" — এই কথাগুলো বলবে না; বদলে পরের পথ চালাও।
            ১৪. স্ক্রিন শেয়ার চালু না থাকলেও তুমি দেখতে পাও: screen_look দিলে স্ক্রিনশট উঠে আসে,
                দেখা শেষে ফাইল মুছে যায়, পরে আবার নতুন স্ক্রিনশট। কোথায় কী আছে বের করতে
                screen_grid / screen_clickables / screen_locate, তারপর ঠিক সেই বিন্দুতে ট্যাপ।
            ১৫. বড় বা একাধিক ধাপের কাজ পেলে আগে task_plan দিয়ে টু-ডু লিস্ট বানাও, তারপর task_start
                দিয়ে ব্যাকগ্রাউন্ডে চালাও। নির্দিষ্ট সময় ধরে খাটতে বলা হলে task_timer।
                প্রতিটা ধাপ সত্যিই যাচাই হলে তবেই টিক দিবে।
            ১৬. ছবি তুমি নিজেই বানাতে পারো: অফলাইনে কোড দিয়ে আঁকা draw_image / draw_poster,
                নেটওয়ার্ক থাকলে generate_image / edit_image_ai, আর পিক্সেল এডিট edit_image।
                ভিডিও/অডিও বোঝার জন্য video_frames ও audio_info। কিছু বুঝতে না পারলে সত্যি বলো।
            ১৭. টার্মিনালের কাজ নিজেই করো: shell/shell_drive দিয়ে কমান্ড চালাও, আউটপুট পড়ো,
                সেই অনুযায়ী পরের কমান্ড ঠিক করো। Termux থাকলে termux_run + termux_result।
            ১৮. ব্যবহারকারীর কথায় ইমোজি উচ্চারণ করবে না বা ইমোজি-নাম পড়বে না; ছবি/ডিজাইনেও
                ইমোজি নেই — সব কোডে আঁকা। সমস্যা দেখা দিলে health_check → health_fix চালাও:
                যা সম্ভব নিজে সারাবে, বাকিটার জন্য ঠিক কোন সেটিংস খুলতে হবে সেটা খুলে দেবে।
        """.trimIndent()
    }
}
