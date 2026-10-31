# Jimi v2 — গবেষণা, পরিকল্পনা ও বাস্তবায়ন (বাংলা)

তারিখ: ৩১ অক্টোবর ২০২৬ · বিল্ড লাইন: `v1.0.0-buildNN` · প্যাকেজ: `com.saathi.ai` (নাম: **Jimi**)

এই দলিলে তিনটি জিনিস আছে — (১) কী কী গবেষণা করা হয়েছে, (২) সেই অনুযায়ী কী পরিকল্পনা
নেওয়া হয়েছিল, (৩) কোডে ঠিক কী বসেছে এবং কী এখনো বাকি/সীমিত। শেষের অংশটা ইচ্ছে করেই
সৎ রাখা হয়েছে: যেটা হয়নি সেটা "হয়েছে" লেখা নেই।

---

## ১. গবেষণা (যা এই আপডেটের ভিত্তি)

| বিষয় | যা জানা গেছে | কোথায় ব্যবহার হলো |
|---|---|---|
| **Termux RUN_COMMAND** | action `com.termux.RUN_COMMAND`, component `com.termux/.app.RunCommandService`; extras `RUN_COMMAND_PATH`, `RUN_COMMAND_ARGUMENTS` (String[]), `RUN_COMMAND_WORKDIR`, `RUN_COMMAND_BACKGROUND`, `RUN_COMMAND_PENDING_INTENT`; ফলাফল আসে `RUN_COMMAND_RESULT{STDOUT,STDERR,EXIT_CODE,ERRMSG}`-এ। কলারকে `com.termux.permission.RUN_COMMAND` ঘোষণা করতে হয় এবং ব্যবহারকারীকে একবার `~/.termux/termux.properties`-এ `allow-external-apps=true` দিতে হয়। | `access/TerminalTools.kt`, Manifest, `termux_run`/`termux_result`/`termux_enable_external` টুল |
| **AccessibilityService.takeScreenshot** | API 30+; config XML-এ `android:canTakeScreenshot="true"` লাগে; HardwareBuffer → software bitmap; MediaProjection-এর মতো কোনো প্রম্পট/ইন্ডিকেটর নেই। `FLAG_SECURE` অ্যাপ (ব্যাংক/DRM) ছবি দেয় না, কিন্তু অ্যাক্সেসিবিলিটি ট্রি পড়া যায়। | `access/ScreenVision.kt`, `res/xml/accessibility_service_config.xml`, `screen_look`/`screen_look_again` |
| **Gemini ইমেজ REST (Nano Banana)** | `POST /v1beta/models/<model>:generateContent`, হেডার `x-goog-api-key`, বডি `contents[].parts[].text`; ছবি ফেরে `candidates[0].content.parts[].inlineData.data` (base64 PNG); `generationConfig.responseModalities:["TEXT","IMAGE"]` + `imageConfig.aspectRatio`; এডিটের জন্য ইনপুট ছবিও একই parts-এ `inlineData` হিসেবে পাঠাতে হয়। মডেল নাম: `gemini-2.5-flash-image`, `gemini-3.1-flash-image`, `gemini-2.0-flash-exp-image-generation`। | `create/ImageGen.kt`, `generate_image`/`edit_image_ai` |
| **Jarvis-ধাঁচের অ্যাপগুলোর ক্ষমতা** | সবসময় শোনা + ওয়েক ওয়ার্ড; প্রোঅ্যাক্টিভ স্ক্রিন মনিটর (৫–৩০ সে.) ও OCR; লক্ষ্য/OKR ধরে রাখা ও দীর্ঘ এজেন্ট লুপ (২০০ ইটারেশন পর্যন্ত, "কাজ শেষ না হওয়া পর্যন্ত চলে"); OS অটোমেশন ও ADB ব্রিজ; সিস্টেম হেলথ মনিটরিং + অটো-ফিক্স; সেলফ-হিলিং কোড স্যান্ডবক্স; ওয়ার্কফ্লো ট্রিগার (cron/webhook/clipboard/screen); প্রেজেন্টেশন তৈরি; সংবেদনশীল কাজে authority gating ও অডিট ট্রেইল। | নিচের প্যারিটি ম্যাট্রিক্স + `task/`, `access/Solver.kt`, `access/HealthInsights.kt` |

---

## ২. তোমার ১৭টি দাবি → কী করা হলো

| # | দাবি | বাস্তবায়ন | ফাইল / টুল |
|---|---|---|---|
| 1 | Termux-এ সরাসরি কমান্ড ও লেখা পাঠানো | RUN_COMMAND ইন্টেন্ট + ফলাফল রিসিভার; না চললে টার্মিনালে নিজে টাইপ করে এন্টার চাপে | `TerminalTools.kt`; `termux_run`, `termux_result`, `termux_type`, `termux_open` |
| 2 | রেস্ট্রিক্টেড ইনপুট ফিল্ড | ৪ ধাপ: ফোকাস → অ্যাক্সেসিবিলিটি টাইপ → রুট `input text` → ক্লিপবোর্ড+পেস্ট মেনু; কোন পথে হলো সেটা রিপোর্টে বলে | `ToolExecutor.fillField/pasteInto`; `fill_field`, `paste_into` |
| 3 | সব আলো/মানের ছবি বোঝা | ফ্রেম ডাউনস্কেল + JPEG পাঠানো, সাথে অ্যাক্সেসিবিলিটি ট্রি/`uiautomator dump` টেক্সট — দুই সূত্র মিলিয়ে বিচার | `ScreenVision.kt`, `MediaUnderstanding.kt`; `describe_image`, `screen_look` |
| 4 | টাকা/সংবেদনশীল কাজে মানুষের অনুমতি | গাইডে কঠোর নিয়ম: স্ক্রিন খোলা ও তথ্য ভরা পর্যন্ত Jimi করবে, চূড়ান্ত কনফার্ম ট্যাপ কখনো নয় | `ToolRegistryGuide.TEXT`, persona নিয়ম ৯/১৮ |
| 5 | গভীর সিস্টেম সেটিংস | রুট দিয়ে `settings put/get`, `pm`, `am`, `input`, `svc`; ডিপ লিংক দিয়ে সেটিংসের ভিতরের পেজ | `RootBridge.kt`; `system_setting_put/get`, `root_exec`, `app_deeplink` |
| 6 | লুকানো/নাম বদলানো অ্যাপ ও উইজেট | লঞ্চার-বিহীন অ্যাপ, সিস্টেম অ্যাপ, সন্দেহজনক লেবেল, সব উইজেট প্রভাইডার ও বসানো উইজেট; ৪ রকম ফলব্যাক দিয়ে লঞ্চ | `AppDirectory.kt`; `app_hidden`, `app_renamed`, `app_widgets`, `app_widget_instances`, `app_launch_any` |
| 7 | প্রোটেক্টেড ফিল্ডে কপি/পেস্ট | রুট ক্লিপবোর্ড (`service call clipboard`) + long-press + Paste মেনু | `RootBridge.setClipboard`, `paste_into` |
| 8 | রুট ব্রিজ, মোড নিজে হালনাগাদ | প্রথম কলে `su` প্রোব, ফলাফল ক্যাশ, `root_status` চাইলেই রি-চেক | `RootBridge.kt`, `root_status` |
| 9 | Jarvis-এর সমকক্ষ হওয়া | নিচের প্যারিটি ম্যাট্রিক্স দেখো | সব মডিউল |
| 10 | স্ক্রিন শেয়ার ছাড়াই দেখা | স্ক্রিনশট → মডেলের কাছে ফ্রেম → অস্থায়ী ফাইল মুছে ফেলা → নতুন স্ক্রিনশট; গ্রিড/কোঅর্ডিনেট ম্যাপিং | `ScreenVision.kt`; `screen_look`, `screen_look_again`, `screen_grid`, `screen_tap_cell`, `screen_locate`, `screen_clickables`, `screen_delete_shots` |
| 11 | বড় কাজ → টু-ডু লিস্ট → ব্যাকগ্রাউন্ড | JSON-এ সংরক্ষিত বোর্ড, StateFlow দিয়ে UI/বাবল লাইভ, ফোরগ্রাউন্ড সার্ভিস ওয়ার্কার, প্রতিটা কাজে যাচাই + টিক | `task/TaskBoard.kt`, `task/TaskRunnerService.kt`, `ui/TaskScreen.kt`; `task_plan`, `task_add`, `task_start`, `task_update`, `task_progress` |
| 12 | বাবল নতুন রূপ | পুরো রিরাইট: কোডে আঁকা OrbView (গ্রেডিয়েন্ট, সুইপ, মাইক লেভেল আর্ক, প্রগ্রেস আর্ক), ভিতরেই লেখা নিচ→উপরে ওঠে (বাবলের বাইরে যায় না), টেনে যেকোনো জায়গায় রাখা যায় ও অবস্থান মনে রাখে, অ্যাপ খুললে নিজে লুকায়/ব্যাকগ্রাউন্ডে ফেরে | `bubble/JimiBubble.kt`, `bubble/BubbleService.kt`, `MainActivity` |
| 13 | বর্তমান কাজ বাবলে ও অ্যাপের উপরের বারে | একই `SessionHub.state` থেকে দুই জায়গায় রেন্ডার: বাবলের ক্যাপশন + `NowBar` | `live/SessionHub.kt` (`taskText`,`taskProgress`), `ui/Components.kt` |
| 14 | পারফরম্যান্স/বাগ | হাবের PackageManager/ডিস্ক কল UI থ্রেড থেকে সরিয়ে IO-তে; ফাইল ম্যানেজার নিজেরাই খোলে (ACTION_VIEW + FileProvider + MIME); Play Store "not found"-এর জন্য `app_install`; ব্রাউজার ও গ্যালারির জন্য আলাদা প্লেবুক | `ui/HubScreen.kt`, `ui/FilesScreen.kt`, `access/FileTools.open()` |
| 15 | ছবি আঁকা ও এডিট | অফলাইন কোড-আঁকা ইঞ্জিন (২৫+ দৃশ্য: সূর্যাস্ত, রাত, পাহাড়, নদী, ঘর, গাছ, ফুল, মুখ, বিড়াল, মসজিদ, মান্ডালা, শহর, বৃষ্টি, নৌকা, হৃদয়, পতাকা, প্যাটার্ন…) + API জেনারেশন + পিক্সেল এডিট (rotate/crop/bright/contrast/sat/gray/invert/vintage/cool/warm/vivid/blur/circle/border/text/resize/flip) | `create/ArtEngine.kt`, `create/ImageGen.kt`, `media/ImageEditor.kt`; `draw_image`, `draw_poster`, `generate_image`, `edit_image_ai`, `edit_image`, `collage`, `set_wallpaper` |
| 16 | টার্মিনালে নিজে কাজ চালানো | কমান্ড → আউটপুট পড়া → পরের কমান্ড ঠিক করা, কয়েক রাউন্ড পর্যন্ত নিজে; ব্যাকগ্রাউন্ড জব ও আউটপুট টেইল | `TerminalTools.selfDrive/background/jobOutput`; `shell`, `shell_drive`, `shell_background`, `shell_job_output` |
| 17 | স্মার্ট, মাল্টিটাস্ক, কখনো "পারব না" নয় | এসকেলেশন সলভার (অটোপাইলট → অফলাইন প্যাক → অ্যাপ রিলঞ্চ → ভিশন → শেল/রুট), প্রতি ধাপে যাচাই, বিকল্প পথ; হেলথ চেক + নিজে সারানো | `access/Solver.kt`, `access/HealthInsights.kt`; `solve`, `solve_alternative`, `health_check`, `health_fix` |
| 18 | চুপ থাকলে ডিসকানেক্ট, বললেই অটো রিকানেক্ট | নির্দিষ্ট idle সময় পরে সেশন "পার্ক" (মাইক এনার্জি গেট চলতে থাকে, অডিও আপলোড হয় না), গলা শুনলেই রিকানেক্ট; সেটিংসে অন/অফ + সময় বাছাই | `live/VoiceGate.kt`, `capture/LiveSessionService.kt`, `SecurePrefs.autoReconnectVoice/idleParkSeconds` |
| 19 | ৪–৫ আঙুলের একসাথে ট্যাপ/ঘোরানো/বাঁকানো | মাল্টি-ফিঙ্গার স্ট্রোক, দুই আঙুলে ঘোরানো, পিঞ্চ, বাঁকা ড্র্যাগ | `GestureEngine`; `gesture_multi_finger`, `gesture_rotate`, `gesture_pinch`, `gesture_drag_arc` |
| 20 | টাইমার ধরে কাজ | `task_timer(goal, minutes)` → ওয়ার্কার ওই সময় ধরে চেষ্টা-যাচাই-বিকল্প পথ চালায় | `TaskRunnerService.workFor` |
| 21 | লুডু/দাবা ফোনের আসল অ্যাপে | ইনস্টল করা গেম খোঁজে, ছক্কায় ট্যাপ করে, স্ক্রিনশট দেখে গুটি বাছে; দাবায় বোর্ড মেপে a1–h8 → পিক্সেল ড্র্যাগ; ক্যারমে আসল স্ট্রাইক | `access/GameApps.kt`; `game_open`, `game_play`, `game_status` |
| 22 | ডিজাইনে কোনো ইমোজি নয় | পুরো রিপো থেকে ইমোজি শূন্য (যাচাই করা), ৬১টি হাতে-আঁকা ভেক্টর আইকন, থিমের ইমোজির বদলে কোডে আঁকা `ThemeMark`, সাইড ইন্ডিকেটর রেল (`SideRail`) | `ui/SaathiIcons.kt`, `ui/Components.kt`, সব স্ক্রিন |

---

## ৩. Jarvis প্যারিটি ম্যাট্রিক্স

| ক্ষমতা (Jarvis-ধাঁচের অ্যাপে যা দেখা গেছে) | Jimi v2-এ আছে? | কীভাবে |
|---|---|---|
| সবসময় শোনা + ওয়েক ওয়ার্ড | হ্যাঁ (আগে থেকেই) | `HotwordService`, Quick Settings টাইল, ডিজিটাল অ্যাসিস্ট্যান্ট |
| চুপ থাকলে পার্ক, গলা শুনলে অটো রিকানেক্ট | হ্যাঁ (নতুন) | `VoiceGate` + `idleParkSeconds` |
| স্ক্রিন দেখে প্রোঅ্যাক্টিভ পরামর্শ | হ্যাঁ (প্রায় পূর্ণ) | `screen_look` (স্ক্রিন শেয়ার ছাড়া), হাবের `NowBar`, হেলথ চেক |
| লক্ষ্য ধরে রেখে দীর্ঘ এজেন্ট লুপ | হ্যাঁ (নতুন) | `Solver.runWithEscalation` (৫ কৌশল × যাচাই), `TaskRunnerService` গার্ড ৬০ ধাপ, `task_timer` |
| টু-ডু/OKR ধাঁচের কাজের তালিকা | হ্যাঁ (নতুন) | `TaskBoard` (স্থায়ী JSON + লাইভ flow) |
| OS অটোমেশন (উইন্ডো/কমান্ড) | হ্যাঁ (নতুন) | `RootBridge` (su/pm/am/input/settings), `TerminalTools` (শেল + Termux) |
| সেলফ-হিলিং / সমস্যা নিজে সারানো | হ্যাঁ (নতুন) | `HealthInsights.selfFix` (ক্যাশ, সেশন রিস্টার্ট, ব্যর্থ টাস্ক রিট্রাই, সঠিক সেটিংস পেজ খোলা) |
| সিস্টেম হেলথ মনিটরিং | হ্যাঁ (নতুন) | ব্যাটারি, তাপ, স্টোরেজ, র‍্যাম, নেটওয়ার্ক, API পিং, ক্যাশ |
| ছবি তৈরি/এডিট | হ্যাঁ (নতুন) | `ImageGen` (API) + `ImageEditor` (অন-ডিভাইস পিক্সেল) + `ArtEngine` (অফলাইন আঁকা) |
| ফাইল/প্রসেস ব্যবস্থাপনা | হ্যাঁ (আছে + নতুন) | `FileTools` (১০ টুল) + `FileTools.open`, `root_file`, `system_info(processes)` |
| ডেইলি ব্রিফিং/সার্চ | হ্যাঁ | `web_search`, `search grounding`, সময়/আবহাওয়া টুল |
| ওয়ার্কফ্লো ট্রিগার (clipboard/screen/time) | আংশিক | টাইমার + রিমাইন্ডার + idle-gate আছে; cron/webhook ধাঁচের ট্রিগার এখনো নেই |
| প্রেজেন্টেশন তৈরি | আংশিক | পোস্টার/ছবি + টেক্সট ফাইল বানায়; PPTX ফরম্যাটে নয় |
| সংবেদনশীল কাজে authority gating | হ্যাঁ (নতুন, কঠোর) | গাইড + persona নিয়ম: চূড়ান্ত কনফার্ম ট্যাপ কখনোই Jimi দেবে না |
| বায়োমেট্রিক লক | না | অ্যান্ড্রয়েড BiometricPrompt যোগ করা লাগবে (পরের ধাপ) |

---

## ৪. নতুন/বদলানো কোডের হিসাব

**নতুন ফাইল (১৩):** `access/RootBridge.kt`, `access/TerminalTools.kt`, `access/ScreenVision.kt`,
`access/AppDirectory.kt`, `access/Solver.kt`, `access/GameApps.kt`, `access/HealthInsights.kt`,
`task/TaskBoard.kt`, `task/TaskRunnerService.kt`, `create/ArtEngine.kt`, `create/ImageGen.kt`,
`media/ImageEditor.kt`, `live/VoiceGate.kt`, `ui/TaskScreen.kt`

**রিরাইট:** `bubble/JimiBubble.kt` (XML/বিটম্যাপ ছাড়া, সম্পূর্ণ কোডে আঁকা), `ui/FilesScreen.kt`,
`ui/GamesScreen.kt`, `ui/HubScreen.kt`

**বড় প্যাচ:** `ToolExecutor.kt` (+৭১ ব্রাঞ্চ), `ToolRegistry.kt` (১৬৩ ডিক্লারেশন),
`ToolRegistryGuide.kt` (নতুন প্লেবুক), `SecurePrefs.kt` (৮টি নতুন সেটিং + persona নিয়ম ১২–১৮),
`LiveSessionService.kt` (পার্ক/রিকানেক্ট + `instance`), `MainActivity.kt` (ডিপ লিংক, বাবল অটো show/hide,
`tasks` রুট), `FileTools.kt` (`open()` + পূর্ণ MIME টেবিল), সব UI স্ক্রিন (ইমোজি → ভেক্টর আইকন)

**টুল সংখ্যা:** ৯২ → **১৬৩** (প্রতিটি ডিক্লারেশনের বিপরীতে হ্যান্ডলার আছে, কোনো ডুপ্লিকেট নেই)

**Manifest:** `com.termux.permission.RUN_COMMAND`, `ACTIVITY_RECOGNITION`, `QUERY_ALL_PACKAGES`;
`TaskRunnerService` (FGS specialUse + subtype property), `TermuxResultReceiver`;
`canTakeScreenshot="true"`; FileProvider path-এ `external-files-path`/`external-cache-path`

---

## ৫. যা সীমিত থাকছে (সৎ স্বীকারোক্তি)

1. **ভিডিও রি-এনকোডিং নয়** — কাট/জোড়া দেওয়ার জন্য এনকোডার লাগে। Jimi ফ্রেম দেখে, টাইমলাইনে
   ড্র্যাগ/ট্রিম করে, আর Termux-এ ffmpeg থাকলে কমান্ড দিয়ে করে; না পারলে স্পষ্ট বলে।
2. **FLAG_SECURE স্ক্রিন** (ব্যাংক/Netflix/DRM) স্ক্রিনশট দেয় না — তখন অ্যাক্সেসিবিলিটি ট্রি পড়ে কাজ চলে,
   না পারলে বলে দেয়।
3. **রুট না থাকলে** গভীর সিস্টেম সেটিংস ও `pm/am` কমান্ড সীমিত — তখন শেল + অ্যাক্সেসিবিলিটি পথ চলে।
4. **Termux RUN_COMMAND** একবার ব্যবহারকারীর `allow-external-apps=true` চায়; রুট থাকলে Jimi নিজেই
   ফাইলটা ঠিক করে দেয়, না থাকলে খুলে দিয়ে বলে।
5. **API কী ছাড়া** `generate_image`/`edit_image_ai` চলবে না — তখন `draw_image` (অফলাইন কোড-আঁকা)
   ব্যবহার হয়।
6. **cron/webhook ট্রিগার** এখনো নেই (রিমাইন্ডার + টাইমার + idle-gate আছে)।
7. **বায়োমেট্রিক লক** নেই।

---

## ৬. বিল্ড ও ডেলিভারি

* লোকাল gradle চালানো হয়নি — সব বিল্ড GitHub Actions-এ (`push → main`)।
* একটিমাত্র কমিট, একটিমাত্র বিল্ড, তারপর APK ডাউনলোড + dex-এ প্রতিটি নতুন মডিউলের উপস্থিতি যাচাই।
* APK ডিবাগ বিল্ড — Play Protect সতর্কতা দেখাবে (অ্যাক্সেসিবিলিটি + SMS + ওভারলে পারমিশনের কারণে)।
