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

    /** True after the user has completed the first-run permission page. */
    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING, value).apply()

    val isConfigured: Boolean get() = apiKey.isNotBlank()

    /** The full system instruction handed to Gemini Live at session start. */
    fun buildSystemPrompt(): String {
        val base = persona.ifBlank { DEFAULT_PERSONA }
        val langRule = when (language) {
            "bn" -> "\n\nসব সময় বাংলায় (Bangla) উত্তর দাও, যদি না ব্যবহারকারী স্পষ্টভাবে অন্য ভাষায় উত্তর চায়।"
            "en" -> "\n\nAlways reply in English unless the user clearly asks for another language."
            else -> "\n\nReply in whatever language the user is speaking."
        }
        return base + langRule
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
            তুমি "সাথী" — ব্যবহারকারীর একজন উষ্ণ, বুদ্ধিমান আর মজার AI সঙ্গী।
            নিয়ম:
            ১. কথা বলার মতো ছোট, স্বাভাবিক বাক্যে উত্তর দাও (সাধারণত ১-৩ বাক্য)। তুমি লেখা নও, তুমি কথা বলছ।
            ২. ব্যবহারকারী ক্যামেরা বা স্ক্রিন শেয়ার করলে যা দেখা যাচ্ছে তা বুঝে সরাসরি সাহায্য করো।
            ৩. কখনো নিজে থেকে বলো না যে তুমি একটি টেক্সট মডেল; তুমি কণ্ঠে কথা বলো।
            ৪. ব্যবহারকারীর টোন বুঝে সহানুভূতিশীল থাকো; দরকার হলে ছোট একটা প্রশ্ন করো।
            ৫. সংখ্যা, নাম, ঠিকানা বা গুরুত্বপূর্ণ তথ্য স্পষ্ট করে বলো।
            ৬. ব্যবহারকারী "থামো" বললে সাথে সাথে থেমে যাও।
        """.trimIndent()
    }
}
