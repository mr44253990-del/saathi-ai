package com.saathi.ai.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saathi.ai.data.SecurePrefs
import com.saathi.ai.ui.theme.Aqua
import com.saathi.ai.ui.theme.TextMuted
import com.saathi.ai.ui.theme.TextPrimary
import com.saathi.ai.ui.theme.Violet

@Composable
fun SettingsScreen(
    prefs: SecurePrefs,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current

    var apiKey by remember { mutableStateOf(prefs.apiKey) }
    var model by remember { mutableStateOf(prefs.model) }
    var voice by remember { mutableStateOf(prefs.voice) }
    var language by remember { mutableStateOf(prefs.language) }
    var persona by remember { mutableStateOf(prefs.persona.ifBlank { SecurePrefs.DEFAULT_PERSONA }) }
    var showTranscript by remember { mutableStateOf(prefs.showTranscript) }
    var searchGrounding by remember { mutableStateOf(prefs.searchGrounding) }
    var revealKey by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    SaathiBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(SaathiIcons.ArrowBack, "ফিরে যাও", tint = TextPrimary, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text("সেটিংস", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }

            Spacer(Modifier.height(12.dp))

            // ---------- API key
            SectionCard(title = "Gemini API Key", icon = "🔑") {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it.trim() },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("AIza…", color = TextMuted, fontSize = 13.sp) },
                    visualTransformation = if (revealKey) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        Icon(
                            if (revealKey) SaathiIcons.VisibilityOff else SaathiIcons.Visibility,
                            contentDescription = "key",
                            tint = TextMuted,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { revealKey = !revealKey }
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = fieldColors()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "এটা তোমার ফোনেই এনক্রিপ্ট হয়ে সেভ থাকবে, কোথাও পাঠানো হয় না।",
                    color = TextMuted,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Violet.copy(alpha = 0.16f))
                        .clickable {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))
                            )
                        }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ফ্রি API Key নাও", color = Violet, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(6.dp))
                    Icon(SaathiIcons.OpenInNew, null, tint = Violet, modifier = Modifier.size(13.dp))
                }
            }

            Spacer(Modifier.height(14.dp))

            // ---------- Model
            SectionCard(title = "মডেল", icon = "🧠") {
                ChipRow(
                    options = SecurePrefs.MODELS,
                    selected = model,
                    onSelect = { model = it },
                    labelMapper = { m ->
                        when (m) {
                            "gemini-3.8-live" -> "3.8 Live"
                            "gemini-3.8-live-extended-thinking" -> "3.8 Live Thinking"
                            "gemini-3.1-flash-live-preview" -> "3.1 Flash Live"
                            else -> "2.5 Native Audio"
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))
                Text(text = model, color = TextMuted, fontSize = 10.5.sp)
            }

            Spacer(Modifier.height(14.dp))

            // ---------- Voice
            SectionCard(title = "কণ্ঠস্বর", icon = "🎙️") {
                ChipRow(
                    options = SecurePrefs.VOICES,
                    selected = voice,
                    onSelect = { voice = it }
                )
            }

            Spacer(Modifier.height(14.dp))

            // ---------- Language
            SectionCard(title = "ভাষা", icon = "🌐") {
                ChipRow(
                    options = listOf("bn", "en", "auto"),
                    selected = language,
                    onSelect = { language = it },
                    labelMapper = { l ->
                        when (l) {
                            "bn" -> "বাংলা"
                            "en" -> "English"
                            else -> "অটো"
                        }
                    }
                )
            }

            Spacer(Modifier.height(14.dp))

            // ---------- Persona
            SectionCard(title = "সাথীর ব্যক্তিত্ব", icon = "💜") {
                OutlinedTextField(
                    value = persona,
                    onValueChange = { persona = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    placeholder = { Text("সাথী কেমন হবে, কীভাবে কথা বলবে…", color = TextMuted, fontSize = 13.sp) },
                    shape = RoundedCornerShape(16.dp),
                    colors = fieldColors()
                )
                Spacer(Modifier.height(9.dp))
                Text(
                    "রিসেট করো",
                    color = Aqua,
                    fontSize = 11.5.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Aqua.copy(alpha = 0.12f))
                        .clickable { persona = SecurePrefs.DEFAULT_PERSONA }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }

            Spacer(Modifier.height(14.dp))

            // ---------- Toggles
            SectionCard(title = "অ্যাডভান্সড", icon = "⚙️") {
                ToggleRow(
                    title = "কথার লিখিত রূপ দেখাও",
                    subtitle = "তুমি আর সাথী কী বলছে, স্ক্রিনে লেখা আকারে দেখাবে",
                    checked = showTranscript,
                    onChange = { showTranscript = it }
                )
                Spacer(Modifier.height(10.dp))
                ToggleRow(
                    title = "Google Search গ্রাউন্ডিং",
                    subtitle = "সাথী দরকারমতো লাইভ তথ্য খুঁজে উত্তর দিতে পারবে",
                    checked = searchGrounding,
                    onChange = { searchGrounding = it }
                )
            }

            Spacer(Modifier.height(22.dp))

            GradientButton(
                text = if (saved) "সেভ হয়েছে ✓" else "সেভ করো",
                modifier = Modifier.fillMaxWidth(),
                icon = SaathiIcons.Check
            ) {
                prefs.apiKey = apiKey
                prefs.model = model
                prefs.voice = voice
                prefs.language = language
                prefs.persona = if (persona.trim() == SecurePrefs.DEFAULT_PERSONA.trim()) "" else persona
                prefs.showTranscript = showTranscript
                prefs.searchGrounding = searchGrounding
                saved = true
                onSaved()
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "নতুন সেটিং চলতি সেশনে নয়, পরের সেশনে প্রযোজ্য হবে।",
                color = TextMuted.copy(alpha = 0.7f),
                fontSize = 10.5.sp
            )
            Spacer(Modifier.height(26.dp))
        }
    }
}

@Composable
private fun SectionCard(title: String, icon: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.055f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun ChipRow(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    labelMapper: (String) -> String = { it }
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (isSelected) {
                            Brush.linearGradient(listOf(Violet, Aqua))
                        } else {
                            Brush.linearGradient(
                                listOf(Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.04f))
                            )
                        }
                    )
                    .clickable { onSelect(option) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = labelMapper(option),
                    color = if (isSelected) Color.White else TextMuted,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = TextMuted, fontSize = 10.5.sp, lineHeight = 14.sp)
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF04211F),
                checkedTrackColor = Aqua,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = Color.White.copy(alpha = 0.10f)
            )
        )
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Aqua.copy(alpha = 0.6f),
    unfocusedBorderColor = Color.White.copy(alpha = 0.10f),
    cursorColor = Aqua,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedContainerColor = Color.White.copy(alpha = 0.04f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.03f)
)
