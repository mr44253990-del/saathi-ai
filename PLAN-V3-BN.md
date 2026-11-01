# Jimi v3 — গভীর গবেষণা, অডিট ও আর্কিটেকচার পরিকল্পনা

> **অবস্থা:** Phase 1 (গবেষণা + অডিট) সম্পন্ন · **ব্যাচ A (ভিত্তি) কোড লেখা শেষ** — `ShizukuBridge`, `ErrorCenter`, `KillSwitch`, `ApprovalGate`, `PermissionCenter` + Gradle/Manifest আপডেট। ব্যাচ B–H বাকি, সব শেষে **একটাই** CI বিল্ড।
> **ভিত্তি:** বিল্ড ৩২ (`4e0fd3a`, CI `41207213579` success) — ৬০টি Kotlin ফাইল, ২০,০৭২ লাইন, ১৬৩টি টুল।
> **নীতি:** কাজ করা কোনো ফিচার ভাঙা যাবে না; দাবি ও বাস্তবতা আলাদা করে লিখতে হবে; ইমোজি শূন্য ডিজাইন; শেষে **একটাই** CI বিল্ড।

---

## ১. বর্তমান সিস্টেম অডিট (যা আসল কোড পড়ে বের করা)

### ১.১ যা এখন আছে ও কাজ করছে (নষ্ট করা যাবে না)

| অংশ | ফাইল | অবস্থা |
|---|---|---|
| লাইভ ভয়েস সেশন (WebSocket) | `live/LiveClient.kt` (312), `capture/LiveSessionService.kt` (630), `live/SessionHub.kt` | 24 kHz PCM আউট, টুল-কল লুপ, রিজাম্পশন টোকেন, ক্যামেরা ফ্রেম |
| টুল সিস্টেম | `live/ToolRegistry.kt` (1117), `live/ToolExecutor.kt` (678), `ToolRegistryGuide.kt` (185) | ১৬৩ টুল, ০ ডুপ |
| অ্যাক্সেসিবিলিটি অটোমেশন | `access/JimiAccessibilityService.kt` (478), `Autopilot.kt` (251), `GestureEngine.kt` (393) | ডাম্প, ট্যাপ, টাইপ, মানুষের মতো জেসচার, ক্যারম স্ট্রাইক |
| রুট ব্রিজ | `access/RootBridge.kt` (283) | su কমান্ড, settings/input/svc/pm/am, ফাইল রিড-রাইট |
| টার্মিনাল + Termux | `access/TerminalTools.kt` (348) | shell, ব্যাকগ্রাউন্ড জব, self-drive লুপ, RUN_COMMAND |
| স্ক্রিনশট ভিশন | `access/ScreenVision.kt` (306) | a11y takeScreenshot → JPEG → মডেল, গ্রিড, ডাম্প-কোঅর্ডিনেট |
| টাস্ক | `task/TaskBoard.kt` (209), `task/TaskRunnerService.kt` (277) | JSON স্টোরেজ, StateFlow, FGS, নোটিফিকেশন |
| সমস্যা সমাধান | `access/Solver.kt` (230) | এসকেলেশন সিঁড়ি, verdict টোকেন |
| ছবি | `create/ArtEngine.kt` (788), `create/ImageGen.kt` (198), `media/ImageEditor.kt` (312), `media/MediaUnderstanding.kt` (215) | অফলাইন আঁকা, API জেনারেশন/এডিট, পিক্সেল এডিট, ভিডিও ফ্রেম |
| অ্যাপ ডিরেক্টরি | `access/AppDirectory.kt` (433) | ইনস্টলড/লুকানো/সিস্টেম অ্যাপ, deep link, উইজেট |
| স্বাস্থ্য | `access/HealthInsights.kt` (355) | রিপোর্ট + সেলফ-ফিক্স |
| বাবল | `bubble/JimiBubble.kt` (537), `bubble/BubbleService.kt` (206) | কোডে আঁকা OrbView, ক্লিপ করা স্ক্রলিং টেক্সট, ড্র্যাগ, অটো শো/হাইড |
| ভয়েস গেট | `live/VoiceGate.kt` (119) | মাইক এনার্জি দিয়ে অটো রিকানেক্ট |
| মেমরি/চ্যাট | `data/MemoryStore.kt` (160), `data/ChatStore.kt` (114), `data/SecurePrefs.kt` (262) | EncryptedSharedPreferences (Keystore AES256-GCM) |
| UI | ১২টি স্ক্রিন + `ui/SaathiIcons.kt` (731, ৬১ ভেক্টর আইকন) + `ui/Components.kt` (411) | সাইড রেল, গ্লাস কার্ড, থিম ১২টি |
| অ্যাসিস্ট্যান্ট/হটওয়ার্ড | `assist/AssistantGateway.kt` (425), `hotword/HotwordService.kt` (333), `JimiTileService.kt` | হোম-হোল্ড, QS টাইল |

### ১.২ অডিটে পাওয়া ঘাটতি (v3-এর কাজ এখান থেকেই ঠিক হবে)

| # | সমস্যা | প্রমাণ | v3 সমাধান |
|---|---|---|---|
| A1 | **ব্রাউজার নেই** — কোনো WebView-ই কোডে নেই (মাত্র ৩টি কমেন্টে উল্লেখ) | `grep WebView` → শুধু কমেন্ট | Phase 7: নিজস্ব BrowserWorkspace (WebView, ট্যাব, JS, কনটেন্ট এক্সট্র্যাকশন, ডাউনলোড) |
| A2 | **একসাথে একটাই টাস্ক** — `TaskRunnerService` সিঙ্গল চ্যানেল, priority/queue নেই, pause/resume নেই | `TaskRunnerService.kt` | Phase 4/16: TaskEngine (৪–৫ কনকারেন্ট, queue, priority, pause/resume/cancel, history, WorkManager, রিকভারি) |
| A3 | **Shizuku নেই** — শুধু root ও su; রুট ছাড়া গভীর কন্ট্রোলের পথ নেই | `grep Shizuku` → ০ | Phase 2: ShizukuBridge (binder + newProcess + WRITE_SECURE_SETTINGS grant) |
| A4 | **কম্পিউটার-ইউজ এজেন্ট লুপ নেই** — গ্রিড/ডাম্প হ্যাক; Gemini-র আসল `computer_use` টুল ব্যবহার হয় না | `ScreenVision.grid()` | Phase 3: VisualAgent (ENVIRONMENT_MOBILE, 0–999 কোঅর্ডিনেট, closed loop, intent) |
| A5 | **মাল্টি-এজেন্ট নেই** — একটাই এক্সিকিউটর, ১৬৩ ব্রাঞ্চের switch | `ToolExecutor.kt` | Phase 17: Orchestrator + ১১টি এজেন্ট (parallel, unified result) |
| A6 | **কেন্দ্রীয় এরর সিস্টেম নেই** — error স্ট্রিং ছড়িয়ে আছে, retry limit কোথাও কোথাও নেই | `SessionState.error` | Phase 15: ErrorCenter (লগ, root cause, সমাধান, retry বাজেট, রিপোর্ট) |
| A7 | **অডিও জেনারেশন নেই** — শুধু লাইভ সেশনের PCM; TTS/মিউজিক ফাইল বানানো যায় না, ব্যাকগ্রাউন্ড প্লেয়ার নেই | `audio/AudioIo.kt` | Phase 20: Gemini TTS (`responseModalities:["AUDIO"]`) → WAV → FGS প্লেয়ার + নোটিফিকেশন কন্ট্রোল |
| A8 | **ডকুমেন্ট স্টুডিও নেই** — শুধু টেক্সট ফাইল; PDF/DOCX তৈরি হয় না | `FileTools.writeText` | Phase 18: DocumentStudio (PDF canvas, DOCX zip+XML, MD, HTML, CSV) |
| A9 | **অটোমেশন টেমপ্লেট নেই** — অফলাইন প্যাক হার্ডকোড করা ১২টি | `OfflinePacks.kt` | Phase 18: TemplateStore (ব্যবহারকারীর নিজের workflow সেভ/রান) + রেকর্ড-ম্যাক্রো |
| A10 | **OCR/অবজেক্ট ডিটেকশন/ব্যাকগ্রাউন্ড রিমুভাল/এনহ্যান্স নেই** | `MediaUnderstanding` শুধু বর্ণনা | Phase 9: VisionLab (OCR, enhance, bg-remove, upscale-lite, সব অন-ডিভাইস + API) |
| A11 | **ভিডিও সামারি/সাবটাইটেল নেই** — শুধু ফ্রেম তোলা | `videoFrames` | Phase 9: VideoLab (ফ্রেম স্যাম্পল → সিন ডিটেকশন → সামারি → SRT) |
| A12 | **স্পিকার-অ্যাওয়ার ট্রান্সক্রিপশন/নয়েজ রিডাকশন নেই** | `AudioIo` | Phase 9: AudioLab (গেট + স্পেকট্রাল সাবট্র্যাকশন, ডায়েরি-স্টাইল স্পিকার ট্যাগ) |
| A13 | **ডেইলি/হেলথ ট্র্যাকিং অর্ধেক** — রিমাইন্ডার আছে, স্টেপ/স্লিপ/অ্যাক্টিভিটি লগ নেই | `MemoryStore.addReminder` | Phase 12: DailyAssistant (স্টেপ কাউন্টার, স্লিপ উইন্ডো, ওষুধ রিমাইন্ডার অ্যালার্ম, রিপোর্ট) |
| A14 | **মডেল রাউটার নেই** — এক মডেলে সব; টেক্সট কাজেও লাইভ মডেল | `prefs.model` | Phase 18: ModelRouter (কাজ অনুযায়ী live/flash/computer-use/tts/image মডেল) |
| A15 | **Gemini Hub ধীর** — মডেল তালিকা/ক্যাশ/টাইমআউট ম্যানেজমেন্ট নেই | `HubScreen.kt` | Phase 14: API latency মেপে ক্যাশ + স্ট্রিমিং + টাইমআউট নীতি |
| A16 | **cron/webhook ট্রিগার নেই** (Jarvis-এ আছে) | — | Phase 4: AlarmManager + WorkManager periodic ট্রিগার |
| A17 | **ইমার্জেন্সি স্টপ নেই** — এক ট্যাপে সব থামানোর বাটন নেই | — | Phase 19: KillSwitch (সব সার্ভিস/টাস্ক/মাইক/স্ক্রিন একসাথে বন্ধ) |
| A18 | **Task Recovery নেই** — অ্যাপ মারা গেলে অসম্পূর্ণ টাস্ক হারায় | `TaskBoard` ফাইলে থাকে কিন্তু রিজিউম লজিক নেই | Phase 4: বুট/রিস্টার্টে রিজিউম + progress snapshot |
| A19 | **পারমিশন ড্যাশবোর্ড অসম্পূর্ণ** — অনুমতি তালিকা আছে, কিন্তু Granted/Restricted/Required/Connection-Lost স্টেট ও গাইডেন্স নেই | `PermissionsScreen.kt` (445) | Phase 2: PermissionCenter (স্টেট মেশিন + ধাপে ধাপে নির্দেশনা) |
| A20 | **স্ক্রিন কনটেক্সট সার্চ/প্রজেক্ট ওয়ার্কস্পেস নেই** | — | Phase 18: ScreenContextSearch + ProjectWorkspace |

### ১.৩ যা ইতিমধ্যে ঠিক আছে (আবার করার দরকার নেই)
ইমোজি-মুক্ত ভেক্টর আইকন সিস্টেম, ফাইল ম্যানেজার, রুট ব্রিজ, টার্মিনাল/Termux, স্ক্রিনশট ভিশনের ভিত্তি, টাস্ক বোর্ডের স্টোরেজ, এসকেলেশন সলভার, অফলাইন আর্ট, অন-ডিভাইস ইমেজ এডিট, ভয়েস গেট, বাবলের অটো শো/হাইড, এনক্রিপ্টেড API key স্টোরেজ, হাবের স্পিড ফিক্স।

---

## ২. প্রতিযোগী ও প্রযুক্তি গবেষণা

| প্রযুক্তি | কীভাবে কাজ করে | Jimi v3-এ কী নেওয়া হবে |
|---|---|---|
| **Gemini `computer_use` টুল** (ENVIRONMENT_MOBILE) | ক্লায়েন্ট স্ক্রিনশট + লক্ষ্য + আগের অ্যাকশন হিস্ট্রি পাঠায় → মডেল function call ফেরত দেয় (`click`, `type`, `scroll`, `long_press`, `drag_and_drop`, `wait`, `press_key`, `take_screenshot`, `open_app`, `go_back`) → ক্লায়েন্ট চালায় → নতুন স্ক্রিনশট → লুপ। কোঅর্ডিনেট **0–999 নরমালাইজড**, ক্লায়েন্টে স্ক্রিন সাইজ দিয়ে গুণ করতে হয়। প্রতিটি অ্যাকশনে `intent` ফিল্ড (কেন এটা করছে)। কাস্টম ফাংশন যোগ ও বিপজ্জনক অ্যাকশন বাদ দেওয়া যায়। thinking level কমানো যায় (দ্রুততার জন্য)। | **VisualAgent**-এর মেরুদণ্ড। মোবাইল অ্যাকশন সেট + কাস্টম `open_app`/`go_home`/`read_screen_text`; payment/password অ্যাকশন **excluded** + human-in-the-loop গেট |
| **OpenAI Operator / Computer Use** | একই closed-loop প্যাটার্ন; ব্রাউজারে focus + coordinate click; "watch mode" (সংবেদনশীল ফর্মে থেমে যায়) | সংবেদনশীল ফর্মে **auto-pause + ব্যবহারকারীর হাতে শেষ ট্যাপ** নীতি |
| **Claude Computer Use** | screenshot → tool_use (computer/bash/text_editor) → result → loop; bash ও ফাইল এডিট একসাথে | এজেন্ট লুপে **টার্মিনাল ও ভিশন একই অর্কেস্ট্রেটরের** অধীনে; টুল ফলাফল টেক্সট হিসেবে ফেরত |
| **Shizuku** | `app_process` দিয়ে shell(2000)/root(0) UID-এ সার্ভিস চলে; অ্যাপ `dev.rikka.shizuku:api` + `provider` দিয়ে binder পায়; `Shizuku.pingBinder()`, `checkSelfPermission()`, `requestPermission()`, `addBinderReceivedListenerSticky`, `ShizukuBinderWrapper(SystemServiceHelper.getSystemService("package"))` দিয়ে সিস্টেম API, `Shizuku.newProcess()` দিয়ে কমান্ড। Android 9+ এ hidden API এর জন্য `AndroidHiddenApiBypass` লাগে। `getUid()`=0 হলে root, 2000 হলে ADB | **ShizukuBridge**: রুট ছাড়াই `pm/am/settings/input/svc/dumpsys`, নিজেকে `WRITE_SECURE_SETTINGS` grant, অ্যাক্সেসিবিলিটি নিজে চালু, Connection-Lost স্টেট হ্যান্ডলিং |
| **ADB** | shell UID-এর পারমিশন সেট (অন্য অ্যাপের sandbox পড়া যায় না) | Shizuku-র মাধ্যমেই ADB-সমতুল্য কাজ; আলাদা ADB ক্লায়েন্ট নয় (সম্ভবও নয়) |
| **Tasker / AutoInput** | প্রোফাইল (trigger) + টাস্ক (action ধাপ); AutoInput অ্যাক্সেসিবিলিটি দিয়ে UI কন্ট্রোল | **AutomationTemplate**: trigger (সময়/অ্যাপ/নোটিফিকেশন/ম্যানুয়াল) + ধাপ তালিকা + শর্ত; রেকর্ড করে টেমপ্লেট বানানো |
| **Termux** | RUN_COMMAND intent + broadcast ফলাফল; পূর্ণ প্যাকেজ ম্যানেজার | আগে থেকেই আছে; v3-এ ffmpeg/python পাইপলাইন টেমপ্লেট |
| **Open Interpreter** | কোড লেখে → চালায় → আউটপুট পড়ে → সংশোধন করে (self-correcting loop) | `selfDrive` উন্নত করে **CodeAgent**: লেখা → যাচাই → চালানো → error পড়ে ঠিক করা, ধ্বংসাত্মক কমান্ডে অনুমোদন |
| **Browser automation (Playwright)** | DOM এক্সট্র্যাকশন, ফর্ম ফিল, নেভিগেশন, স্ক্রিনশট | WebView + `evaluateJavascript` দিয়ে DOM টেক্সট/লিংক/ফর্ম এক্সট্র্যাকশন, রিসার্চ ইঞ্জিন |
| **Multimodal agents** | ভিশন + অডিও + টেক্সট এক লুপে | Orchestrator প্রতিটি কাজের জন্য সঠিক মডেল/এজেন্ট বেছে নেয় |
| **Jarvis (অনলাইন)** | cron/webhook ট্রিগার, PPTX, বায়োমেট্রিক লক | AlarmManager+WorkManager ট্রিগার, DocumentStudio (PDF/DOCX), বায়োমেট্রিক লক (BiometricPrompt) |

### ২.১ Gemini অডিও জেনারেশন (Phase 20-এর দাবি) — গবেষণা ফল
- REST: `POST https://generativelanguage.googleapis.com/v1beta/models/<tts-model>:generateContent?key=…`
- বডি: `{"contents":[{"parts":[{"text":"…"}]}],"generationConfig":{"responseModalities":["AUDIO"],"speechConfig":{"voiceConfig":{"prebuiltVoiceConfig":{"voiceName":"Kore"}}}}}`
- রেসপন্স: `candidates[0].content.parts[0].inlineData.data` = **base64 PCM, 24 kHz, 16-bit, mono** → WAV হেডার বসিয়ে ফাইল।
- মডেল: `gemini-2.5-flash-preview-tts`, `gemini-2.5-pro-preview-tts` (preview; নাম বদলাতে পারে → সেটিংসে editable + ফলব্যাক তালিকা)।
- **৩০টি ভয়েস** (Zephyr, Puck, Charon, Kore, Fenrir, Leda, Orus, Aoede, Callirrhoe, Autonoe, Enceladus, Iapetus, Umbriel, Algieba, Despina, Erinome, Algenib, Rasalgethi, Laomedeia, Achernar, Alnilam, Schedar, Gacrux, Pulcherrima, Achird, Zubenelgenubi, Vindemiatrix, Sadachbia, Sadaltager, Sulafat)।
- **মাল্টি-স্পিকার**: `multiSpeakerVoiceConfig.speakerVoiceConfigs[]` — গল্প/সংলাপ দুই কণ্ঠে।
- টোন/স্টাইল প্রম্পটেই লিখতে হয় ("উৎসবের আমেজে পড়ো")।
- ব্যাকগ্রাউন্ড প্লেব্যাক: `MediaPlayer`/`AudioTrack` + **FOREGROUND_SERVICE_MEDIA_PLAYBACK** সার্ভিস + নোটিফিকেশনে play/pause/stop।

---

## ৩. v3 আর্কিটেকচার

```
                         ┌───────────────────────────────┐
   ভয়েস / টেক্সট / বাবল → │        Orchestrator            │ ← ট্রিগার (সময়, নোটিফিকেশন, টেমপ্লেট)
                         │  (context, plan, route, merge) │
                         └──────┬────────────────────────┘
        ┌──────────┬──────────┬─┴────────┬──────────┬──────────┐
   ResearchAgent BrowserAgent VisualAgent FileAgent TermAgent CodeAgent
   MediaAgent  AudioAgent  VideoAgent  PlannerAgent RecoveryAgent
        └──────────┴──────────┴─┬────────┴──────────┴──────────┘
                         ┌──────┴───────┐     ┌────────────────┐
                         │  TaskEngine  │────▶│  ErrorCenter    │
                         │ queue/priority│     │ log/cause/retry │
                         └──────┬───────┘     └────────────────┘
                                │
        DeviceControl (ShizukuBridge → RootBridge → TerminalTools → A11y/Gesture)
        Perception (ScreenVision → computer_use loop → VisionLab OCR)
        Creation (ArtEngine/ImageGen/ImageEditor/DocumentStudio/AudioGen)
        Memory (MemoryStore/ChatStore/TaskHistory/ProjectWorkspace)
```

**নীতি:** প্রতিটি এজেন্টের ইনপুট = `{goal, context, budget(steps/time), tools[]}`; আউটপুট = `{status, report, artifacts[], errors[]}`। Orchestrator কখনো অসীম retry করবে না — বাজেট শেষ হলে RecoveryAgent-এর কাছে যাবে, তারপর সৎ রিপোর্ট।

---

## ৪. বাস্তবায়ন ব্যাচ (একটাই CI বিল্ড, শেষে)

### ব্যাচ A — ভিত্তি (Phase 2, 15, 19)
| ফাইল | কী হবে |
|---|---|
| `app/build.gradle.kts` | + `dev.rikka.shizuku:api:13.1.5`, `dev.rikka.shizuku:provider:13.1.5`, `org.lsposed.hiddenapibypass:hiddenapibypass:4.3`, `androidx.work:work-runtime-ktx:2.9.1`, `androidx.biometric:biometric:1.1.0` |
| `AndroidManifest.xml` | + `moe.shizuku.manager.permission.API_V23`, ShizukuProvider, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, BootReceiver, AudioService, media-playback service |
| `access/ShizukuBridge.kt` | ping/bind/permission, `exec()` (newProcess), `uid()` (0=root/2000=adb), `grantSelfWriteSecureSettings()`, `enableAccessibility()`, `setDefaultAssistant()`, status রিপোর্ট, Connection-Lost হ্যান্ডলিং |
| `access/PermissionCenter.kt` | সব পারমিশনের একক স্টেট মেশিন: GRANTED / NOT_GRANTED / RESTRICTED / REQUIRED / CONNECTION_LOST + ধাপে ধাপে বাংলা নির্দেশনা + এক ট্যাপে খোলার intent |
| `core/ErrorCenter.kt` | কেন্দ্রীয় এরর লগ (JSON, ঘূর্ণায়মান ৫০০ এন্ট্রি), module ট্যাগ, root-cause অনুমান, সম্ভাব্য সমাধান, retry বাজেট (ডিফল্ট ৩), Timeout নীতি, StatusFlow, রিপোর্ট টেক্সট |
| `core/KillSwitch.kt` | ইমার্জেন্সি স্টপ: সব সার্ভিস/টাস্ক/মাইক/স্ক্রিন/বাবল/অডিও একসাথে বন্ধ + লগ |
| `core/ApprovalGate.kt` | সংবেদনশীল অ্যাকশন (পেমেন্ট, পাসওয়ার্ড, OTP, ডিলিট, root-level পরিবর্তন) → ব্যবহারকারীর অনুমোদন (নোটিফিকেশন/বাবল/ডায়ালগ) → টাইমআউটে বাতিল |

### ব্যাচ B — টাস্ক ইঞ্জিন ও মাল্টি-এজেন্ট (Phase 4, 16, 17)
`agent/Orchestrator.kt`, `agent/Agent.kt` (interface + base), `agent/Agents.kt` (১১টি এজেন্ট), `task/TaskEngine.kt` (queue, priority, ৪–৫ কনকারেন্ট, conflict detection, resource lock), `task/TaskRecord.kt` (history, estimated duration, steps), `task/TaskRecovery.kt` (বুট/রিস্টার্টে রিজিউম), `task/SchedulerTriggers.kt` (AlarmManager + WorkManager periodic), `ui/TaskCenterScreen.kt` (ড্যাশবোর্ড: pause/resume/cancel/history)

### ব্যাচ C — ভিজ্যুয়াল কম্পিউটার এজেন্ট (Phase 3)
`vision/ComputerUseClient.kt` (REST: computer_use tool, ENVIRONMENT_MOBILE, thinking level, action parse), `vision/VisualAgent.kt` (closed loop: capture→act→verify→next, budget, auto-delete screenshot), `vision/ActionMapper.kt` (0–999 → আসল পিক্সেল; click/type/scroll/long_press/drag/press_key/open_app/go_back → GestureEngine/RootBridge/ShizukuBridge), `vision/SafetyFilter.kt` (payment/password/OTP ব্লক + ApprovalGate), `vision/ScreenState.kt` (কোন অ্যাপ/স্ক্রিন/ডায়ালগ খোলা, scroll detection, পরিবর্তন হয়েছে কি না)

### ব্যাচ D — ব্রাউজার ও রিসার্চ (Phase 7)
`web/BrowserEngine.kt` (WebView সেটআপ, JS, ডাউনলোড ম্যানেজার, error detection), `web/TabSession.kt` (মাল্টি-ট্যাব), `web/PageExtract.kt` (evaluateJavascript: টেক্সট/লিংক/ফর্ম/টেবিল), `web/BrowserAgent.kt` (নেভিগেট/ফর্ম ফিল/স্ক্রিনশট বিশ্লেষণ), `web/ResearchEngine.kt` (parallel search, source cross-check, তারিখ অনুযায়ী পুরনো/নতুন আলাদা, ক্যাশ, সোর্স লিংক সংরক্ষণ, রিপোর্ট), `ui/BrowserScreen.kt`

### ব্যাচ E — মাল্টিমোডাল ও অডিও (Phase 9, 20)
`media/VisionLab.kt` (OCR, enhance, bg-remove, sharpen/denoise, upscale-lite, image-to-image), `media/VideoLab.kt` (সিন ডিটেকশন, সামারি, সাবটাইটেল/SRT, অডিও-ভিডিও সিঙ্ক তথ্য), `media/AudioLab.kt` (নয়েজ রিডাকশন, স্পিকার ট্যাগ, অডিও সামারি, ভয়েস এনার্জি), `media/AudioGen.kt` (Gemini TTS single+multi-speaker → WAV, অফলাইন অ্যাম্বিয়েন্ট সিন্থ: বৃষ্টি/ঢেউ/ফোকাস টোন), `media/AudioPlaybackService.kt` (ব্যাকগ্রাউন্ড প্লে + নোটিফিকেশন কন্ট্রোল), `ui/AudioScreen.kt`

### ব্যাচ F — মেমরি, হেলথ, টেমপ্লেট, ডকুমেন্ট, প্রজেক্ট (Phase 11, 12, 18)
`data/TaskHistory.kt`, `data/ProjectWorkspace.kt`, `data/ContextSummarizer.kt`, `data/MemorySearch.kt`, `daily/DailyAssistant.kt` (স্টেপ, স্লিপ উইন্ডো, ওষুধ অ্যালার্ম, অ্যাপয়েন্টমেন্ট প্রস্তুতি, রিপোর্ট; **কখনো ডায়াগনোসিস নয়**), `auto/AutomationTemplate.kt` + `auto/TemplateStore.kt` + `auto/MacroRecorder.kt`, `create/DocumentStudio.kt` (PDF/DOCX/MD/HTML/CSV), `live/ModelRouter.kt`, `ui/MemoryDashboardScreen.kt`, `ui/HealthScreen.kt`, `ui/AutomationScreen.kt`

### ব্যাচ G — UI/UX রিডিজাইন ও পারফরম্যান্স (Phase 13, 14, 5, 6, 8)
`ui/theme/*` (liquid glass টোকেন, deep navy/charcoal/violet/electric blue/cyan/teal), `ui/Glass.kt` (গ্লাসমরফিজম কার্ড, animated border, dynamic lighting), `ui/NavRail.kt` (dynamic sidebar + smooth page transition + progress indicator), নতুন/আপডেট স্ক্রিন: PremiumDashboard, ModelManager, PermissionDashboard, VoiceControlCenter, BrowserWorkspace, FileWorkspace, MemoryDashboard, TaskCenter; বাবল v2 (liquid glass, morphing, compact mini ↔ expanded control mode, live status text animation — overflow ছাড়া); Gemini Hub পারফরম্যান্স (ক্যাশ, টাইমআউট, স্ট্রিমিং, মডেল সুইচ, অব্যবহৃত মডেলের রিসোর্স বন্ধ); ফাইল ম্যানেজার আপগ্রেড (ZIP/PDF/কোড প্রিভিউ, recent, search)

### ব্যাচ H — টুল ওয়্যারিং, ডকস, বিল্ড
`ToolRegistry`/`ToolExecutor`-এ নতুন টুল (অনুমান ৭০–৯০টি), `ToolRegistryGuide` প্লেবুক, persona নিয়ম, লোকাল kotlinc যাচাই → **একটি commit → একটি CI বিল্ড** → dex যাচাই → গাইড v8 + ডেলিভারি নোট v8

---

## ৫. ঝুঁকি ও সীমাবদ্ধতা (আগেই স্পষ্ট)

1. **নতুন Gradle ডিপেন্ডেন্সি** (Shizuku, hiddenapibypass, WorkManager, biometric) বিল্ড ফেল করাতে পারে — ব্যাচ A-তেই যোগ করে শেষে একবার বিল্ড; ফেল করলে লগ পড়ে ফিক্স।
2. **`gemini-2.5-computer-use` মডেল আইডি** কী-এর অ্যাক্সেসভেদে নাও চলতে পারে → মডেল আইডি সেটিংসে editable + ফলব্যাক: প্রধান মডেল → পুরনো গ্রিড/ডাম্প পথ। ব্যর্থ হলে সত্যি কথা বলবে।
3. **TTS মডেল preview** → নাম বদলালে 404; ফলব্যাক তালিকা + Android TextToSpeech লোকাল বিকল্প।
4. **Shizuku ইনস্টল না থাকলে** → স্ট্যাটাস ড্যাশবোর্ডে "ইনস্টল নেই / বন্ধ আছে / সংযোগ হারিয়েছে" + Play Store লিংক + পুনরায় সংযোগ বাটন; রুট থাকলে রুট পথই ব্যবহার হবে।
5. **ভিডিও এনকোডিং** এখনো নেই (এনকোডার দরকার) — ফ্রেম বিশ্লেষণ, এডিট অ্যাপ অটোমেশন, Termux-ffmpeg কমান্ড: এই তিন পথ।
6. **ডিভাইসে পরীক্ষা সম্ভব নয় এই পরিবেশে** — কম্পাইল + প্যাকেজিং + dex যাচাই হবে; রানটাইম আচরণ ফোনে পরীক্ষা করতে হবে। প্রতিটি দাবিতে এটা আলাদা করে লেখা থাকবে।
7. **ব্যাটারি**: ৪–৫ কনকারেন্ট টাস্ক + ভিশন লুপ ব্যাটারি খাবে → বাজেট (সময়/ধাপ), পার্ক মোড, WorkManager constraint, ব্যবহারকারীর নিয়ন্ত্রণে সীমা।
8. **নিরাপত্তা**: পাসওয়ার্ড/OTP/পেমেন্ট কখনো অটো-সাবমিট হবে না; FLAG_SECURE স্ক্রিনশট অসম্ভব; স্ক্রিনশট অস্থায়ী, ব্যবহারকারী না বললে সংরক্ষণ হবে না; ধ্বংসাত্মক শেল কমান্ডে অনুমোদন বাধ্যতামূলক।

---

## ৬. ডেলিভারি চেকলিস্ট (Phase 20) — ম্যাপিং

| চেকলিস্ট আইটেম | v3-এ কোথায় | পরীক্ষা কীভাবে সম্ভব |
|---|---|---|
| App Launch, Login/Auth (API key) | বিদ্যমান + PermissionCenter | ফোনে হাতে |
| AI Chat, Voice Interaction | বিদ্যমান + AudioGen/TTS | ফোনে হাতে |
| Floating Bubble | ব্যাচ G (liquid glass, mini/expanded) | ফোনে হাতে |
| Background Task, Task Recovery | ব্যাচ B | ফোনে হাতে + লগ |
| Multi-Agent Execution | ব্যাচ B | ফোনে হাতে + লগ |
| Shizuku Connection, Optional Root | ব্যাচ A | ফোনে হাতে (Shizuku ইনস্টল দরকার) |
| Accessibility Automation | বিদ্যমান + ব্যাচ C | ফোনে হাতে |
| Browser Automation | ব্যাচ D | ফোনে হাতে |
| File Manager | বিদ্যমান + ব্যাচ G (প্রিভিউ/recent/search) | ফোনে হাতে |
| Terminal Integration | বিদ্যমান + ব্যাচ A (Shizuku exec) | ফোনে হাতে |
| Image / Video / Audio Processing | ব্যাচ E | ফোনে হাতে |
| Release APK Generation | ব্যাচ H — CI রিলিজ | স্বয়ংক্রিয় (এখানেই যাচাই হয়) |
| কোড-পর্যায় যাচাই | প্রতিটি ব্যাচে লোকাল kotlinc + dex স্ক্যান | এখানেই করা হবে |
