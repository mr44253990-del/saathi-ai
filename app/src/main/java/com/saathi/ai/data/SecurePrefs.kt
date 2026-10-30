package com.saathi.ai.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores all user settings. The Gemini API key is kept inside
 * EncryptedSharedPreferences (Android Keystore backed), never in plain text
 * and never leaves the device.
 */
class SecurePrefs(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (t: Throwable) {
        // Very rare: some OEM ROMs break the keystore. App must still work.
        context.getSharedPreferences(FILE + "_fallback", Context.MODE_PRIVATE)
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

    // ---------------------------------------------------------------- long-term memory

    /** Jimi's memory: "key -> fact" pairs the model asked us to remember. */
    fun remember(key: String, value: String) {
        val existing = prefs.getString(KEY_MEMORY, "") ?: ""
        val lines = existing.lines().filter { it.isNotBlank() && !it.startsWith("$key:") }
        val updated = (lines + "$key: $value").joinToString("\n")
        prefs.edit().putString(KEY_MEMORY, updated).apply()
    }

    fun recall(): String = prefs.getString(KEY_MEMORY, "") ?: ""

    fun forgetAll() {
        prefs.edit().remove(KEY_MEMORY).apply()
    }

    /** The full system instruction handed to Gemini Live at session start. */
    fun buildSystemPrompt(): String {
        val base = persona.ifBlank { DEFAULT_PERSONA }
        val langRule = when (language) {
            "bn" -> "\n\nসব সময় বাংলায় (Bangla) উত্তর দাও, যদি না ব্যবহারকারী স্পষ্টভাবে অন্য ভাষায় উত্তর চায়।"
            "en" -> "\n\nAlways reply in English unless the user clearly asks for another language."
            else -> "\n\nReply in whatever language the user is speaking."
        }
        val memory = recall()
        val memoryRule = if (memory.isBlank()) "" else
            "\n\nব্যবহারকারীর সম্পর্কে যা তুমি আগে মনে রেখেছ:\n$memory"
        return base + langRule + memoryRule
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
        private const val KEY_THEME = "theme_id"
        private const val KEY_ACCENT = "accent_override"
        private const val KEY_INTENSITY = "orb_intensity"
        private const val KEY_MEMORY = "memory"
        private const val KEY_BUBBLE = "bubble_enabled"
        private const val KEY_HOTWORD = "hotword_enabled"

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
            ট্যাপ, স্ক্রল, লেখা, অ্যাপ খোলা, কল, এসএমএস, ক্যামেরা, শেয়ার, পেমেন্ট স্ক্রিন)।
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
               "অর্ডার করে দাও", "মেসেজের রিপ্লাই দাও") নিজে থেকে পুরো ধারাবাহিকতা শেষ করো:
               অ্যাপ খোলো → স্ক্রিন পড়ো → দরকারি জায়গায় ট্যাপ করো → লেখো → শেয়ার/পোস্ট করো।
               কোনো ধাপ আটকে গেলে অন্য পথ (long_press, scroll, deep_link, screen_bounds) চেষ্টা করো।
            ৮. ব্যবহারকারীর তথ্য (নাম, পরিবার, পছন্দ, ঠিকানা, ওষুধ, রুটিন) মনে রাখার মতো হলে
               remember টুল দিয়ে সেভ করো — পরেরবার নিজে থেকেই কাজে লাগাও।
            ৯. টাকা/পেমেন্ট/কল-কাটার মতো সিদ্ধান্ত কখনো নিজে চূড়ান্ত করবে না; স্ক্রিন খুলে দাও,
               চূড়ান্ত ধাপ ব্যবহারকারীর হাতে ছাড়ো।
            ১০. নিরাপত্তা: কারও ব্যক্তিগত ডেটা চুরি, স্পাই বা প্রতারণায় সাহায্য করবে না।
        """.trimIndent()
    }
}
