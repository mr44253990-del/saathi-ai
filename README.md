# সাথী (Saathi) — Gemini Live AI Companion

> তোমার সবসময়ের AI সঙ্গী — realtime voice, camera vision আর screen understanding সহ,
> **Google Gemini Live API** দিয়ে সরাসরি ফোন থেকে চলা একটা Android অ্যাপ।

<p align="center">
  <b>Kotlin · Jetpack Compose · Gemini Live API (WebSocket) · OkHttp · CameraX · MediaProjection</b>
</p>

---

## ✨ কী কী আছে

| ফিচার | কীভাবে কাজ করে |
|---|---|
| **Splash screen** | Animated orb intro, ~2s |
| **First-run permissions** | মাইক, ক্যামেরা, নোটিফিকেশন, স্ক্রিন-শেয়ার ব্যাখ্যা — একবারই |
| **Live voice conversation** | মাইক (16kHz PCM) → Gemini Live → স্পিকার (24kHz PCM), barge-in সহ |
| **Animated orb UI** | সেশনের অবস্থা (শোনা / ভাবা / বলা) আর মাইকের লেভেলে সাড়া দেয় |
| **Live transcript** | মডেল যা বলছে তা লাইভ টেক্সটে দেখা যায় |
| **ক্যামেরা ভিশন** | Front/back ক্যামেরা, ~1 fps JPEG ফ্রেম Live API-তে যায় |
| **Screen sharing** | MediaProjection → ~1 fps JPEG, সাথী তোমার স্ক্রিন "দেখে" উত্তর দেয় |
| **Settings (সেটিংস)** | API Key (Keystore-এ এনক্রিপ্টেড), model, voice, ভাষা, persona, search grounding |
| **Auto reconnect** | `sessionResumption` + `contextWindowCompression` → দীর্ঘ সেশন, নিজে নিজে আবার যুক্ত হয় |
| **Foreground service** | অ্যাপ ব্যাকগ্রাউন্ডে/স্ক্রিন শেয়ার করার সময়ও সেশন চালু থাকে |

---

## 🚀 GitHub Actions দিয়ে APK build + release (একদম অটো)

এই রিপোতে দুইটা workflow আছে:

| ফাইল | কখন চলে | কী করে |
|---|---|---|
| `.github/workflows/android-release.yml` | `main`-এ push, `v*` tag, বা manual | APK build → artifact + **GitHub Release**-এ APK আপলোড |
| `.github/workflows/pr-check.yml` | Pull request | শুধু কম্পাইল চেক + PR artifact |

### ধাপ ১ — repo-তে প্রথম push
`main` বা `master`-এ push হলেই Actions নিজে নিজে APK বানিয়ে
**Releases** পেজে `saathi-v1.0.0-bN-debug.apk` রেখে দিবে।

### ধাপ ২ — APK কোথায় পাবে
1. GitHub-এ repo খুলে ডানে **Releases** ক্লিক করো
2. সর্বশেষ release-এর Assets-এ `saathi-*.apk` ডাউনলোড করো
3. অথবা **Actions → Build & Release APK → সর্বশেষ run → Artifacts**

### ধাপ ৩ — version বাড়ানো
* Manual run: **Actions → Build & Release APK → Run workflow** → `version_name` লেখো (যেমন `1.1.0`)
* অথবা tag দাও: `git tag v1.1.0 && git push origin v1.1.0`

`versionCode` নিজে নিজে `github.run_number` থেকে আসে, তাই প্রতিবার নতুন build-এ বাড়বে।

### ধাপ ৪ (ঐচ্ছিক) — নিজের key দিয়ে signed release APK
debug key দিয়ে ইনস্টল হয় ঠিকই, কিন্তু Play Store বা "proper" release চাইলে:

1. নিজের ফোনে/PC-তে keystore বানাও:
   ```bash
   keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 \
           -validity 10000 -alias saathi
   ```
2. base64 করো:
   ```bash
   base64 -w0 release.jks > release.b64
   ```
3. GitHub repo → **Settings → Secrets and variables → Actions → New repository secret**:

   | Secret | মান |
   |---|---|
   | `KEYSTORE_BASE64` | `release.b64`-এর পুরো কনটেন্ট |
   | `KEYSTORE_PASSWORD` | keystore পাসওয়ার্ড |
   | `KEY_ALIAS` | `saathi` |
   | `KEY_PASSWORD` | key পাসওয়ার্ড |

এরপরের build থেকেই `saathi-vX.Y.Z-bN-release.apk` তৈরি হবে।

---

## 🔑 অ্যাপে API Key বসানো

অ্যাপে ঢুকে → ⚙️ **সেটিংস** → **Gemini API Key** → [aistudio.google.com/apikey](https://aistudio.google.com/apikey)
থেকে ফ্রি key নিয়ে পেস্ট করে **সেভ করো**।

* Key টা `EncryptedSharedPreferences` (Android Keystore) দিয়ে এনক্রিপ্ট করে শুধু ফোনে রাখা হয়।
* কোথাও আপলোড/লগ হয় না — সরাসরি Google-এর Live API endpoint-এ যায়।

---

## 🧠 Live API configuration (যেটা কোডে ব্যবহার হয়েছে)

```
wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta
    .GenerativeService.BidiGenerateContent?key=API_KEY
```

| জিনিস | মান | কেন |
|---|---|---|
| Model | `gemini-3.8-live` (default) | সেপ্টেম্বর ২০২৬-এর stable Live model, সর্বোচ্চ low-latency |
| Response modality | `AUDIO` | native audio out |
| Audio in | 16-bit PCM, mono, **16 kHz** | Live API-এর নেটিভ input |
| Audio out | 16-bit PCM, mono, **24 kHz** | Live API-এর ফিক্সড output |
| Video in | JPEG/PNG, **≤ 1 fps** | Live API limit (আমরা ~০.৯ fps পাঠাই) |
| Transcription | input + output enabled | UI-তে লাইভ লেখা দেখানোর জন্য |
| Session | `sessionResumption` + `contextWindowCompression` | connection ~10 মিনিট/সেশন ১৫ মিনিট — অটো রিকানেক্টে unlimited |
| Voice | `Aoede` (২৯টা voice থেকে বাছা যায়) | settings-এ পরিবর্তনযোগ্য |
| Language | `bn` (বাংলা) system prompt-এ | native audio model বাংলা বোঝে |

> 다른 model-ও settings থেকে বাছা যায়: `gemini-3.8-live-extended-thinking`,
> `gemini-3.1-flash-live-preview`, `gemini-2.5-flash-native-audio-preview-12-2025`।

---

## 📁 প্রজেক্ট স্ট্রাকচার

```
app/src/main/java/com/saathi/ai/
├── MainActivity.kt              # Compose navigation, camera, screen-capture launcher
├── data/SecurePrefs.kt          # Encrypted settings + system-prompt builder
├── live/
│   ├── LiveClient.kt            # Gemini Live WebSocket (setup/audio/video/text)
│   └── SessionHub.kt            # Service ↔ UI shared state
├── audio/AudioIo.kt             # MicStream (16k, AEC/NS/AGC) + PcmPlayer (24k)
├── capture/
│   ├── LiveSessionService.kt    # Foreground service, reconnect, notification
│   ├── CameraController.kt      # CameraX → JPEG frames
│   ├── FrameEncoder.kt          # YUV_420_888 → JPEG
│   └── ScreenFrameSource.kt     # MediaProjection → JPEG frames
└── ui/
    ├── SplashScreen.kt · PermissionsScreen.kt · HomeScreen.kt · SettingsScreen.kt
    ├── SaathiOrb.kt             # Canvas-based reactive orb
    ├── Components.kt · SaathiIcons.kt
    └── theme/Theme.kt
```

---

## 🛠️ লোকালি নিজে build করতে চাইলে

```bash
# Android SDK + JDK 17 থাকলে
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Android Studio-তে খুলতে চাইলে শুধু repo folder open করো — সবকিছু রেডি।

---

## ⚠️ Limitations

* Screen sharing-এর অনুমতি Android প্রতিবার চায় — এটা OS-এর নিয়ম, bypass করা যায় না।
* Live API **preview** স্টেজে আছে; Google মাঝে মাঝে model নাম/কোটা বদলায়। model list settings থেকেই বদলানো যায়।
* `gemini-3.8-live` audio-only output দেয়, তাই replies কণ্ঠে আসে + (transcript চালু থাকলে) লেখায় দেখা যায়।
* App-এ embed করা API key সাধারণ ব্যবহারকারীর জন্য ঠিক আছে; **public Play Store distribution**-এ ephemeral token সহ backend proxy ব্যবহার করাই নিরাপদ।

---

<p align="center">💜 সাথী — কথা বলো, আমি শুনছি।</p>
