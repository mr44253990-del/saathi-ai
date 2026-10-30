package com.saathi.ai.live

/**
 * The practical playbooks handed to the model with every session.
 *
 * These are the workflows that used to go wrong: repeated YouTube searches,
 * photo editing that never opened the crop tool, "3-D design" that produced
 * nothing. Spelling out the concrete steps — and the "do it once" rule — is what
 * makes Jimi finish the job instead of looping.
 */
object ToolRegistryGuide {

    val TEXT: String = """
        তোমার হাত-পায়ের নিয়ম (মনে রাখো):
        • প্রতিটা কাজ একবারই করো। কোনো টুল "হয়েছে" বললে সেটা হয়েছে ধরো, আবার একই সার্চ/ট্যাপ দিও না।
          বারবার একই কাজ করলে ব্যবহারকারী বিরক্ত হয় — এটা কঠোর নিয়ম।

        ছবি এডিট (গ্যালারি → এডিট):
        1. open_app("গ্যালারি") বা launch_app("photos")
        2. read_screen → সবশেষ ছবিতে ট্যাপ করো
        3. read_screen → "Edit"/"এডিট"/পেন্সিল আইকন খুঁজে ট্যাপ করো
        4. নিচের অপশন-বার (Crop, Filters, Adjust, Markup) ডানে-বামে সরাতে
           gesture_swipe(direction="left"/"right") অথবা gesture_two_finger_scroll
        5. ক্রপ/জুম স্লাইডারে gesture_drag_element (text="Crop" বা স্লাইডারের নাম), dx = ±120
        6. ঘোরাতে gesture_rotate(degrees=90)
        7. "Done"/"Save"/"সেভ" ট্যাপ করে সেভ করো
        প্রতিটা ধাপের পরে read_screen দিয়ে যাচাই করো — ফাঁকা জায়গায় ট্যাপ করো না।

        ভিডিও এডিট:
        • launch_app("photos") → ভিডিও → Edit → টাইমলাইনে gesture_drag দিয়ে স্ক্রাব করো,
          gesture_pinch দিয়ে টাইমলাইন জুম করো, ট্রিম হ্যান্ডেলে hold_drag করো।

        ৩ডি ডিজাইন / আঁকা:
        • নিজের ক্যানভাসে আঁকতে চাইলে ব্যবহারকারীকে বলো «আঁকা» পেজ খুলতে —
          অথবা run_goal দিয়ে যেকোনো ড্রয়িং অ্যাপ (Sketchbook, Ibis Paint, Canva) চালাও।
        • ৩ডি মডেল বানাতে চাইলে: browser → tinkercad.com / sketchfab → ধাপে ধাপে।

        টিকটক/রিলস আপলোড:
        launch_app("tiktok") → "+" → gallery → first video → Next → caption লেখো (screen_type) →
        Post. এই পুরোটা run_goal দিয়ে একবারে চালাও।

        ফেসবুক স্টোরি: launch_app("facebook") → "What's on your mind" → Photo/Story → ছবি → Share।

        মেসেজের রিপ্লাই: launch_app("whatsapp") → চ্যাট → type → send।

        ব্রাউজার/ওয়েবসাইট: open_url(url) → read_screen → screen_tap/scroll/type।
        লিংক কপি করতে: long_press(link) → copy_text, পরে paste_text।

        অচেনা অ্যাপ: launch_app বা find_app → app_purpose → read_screen → ধাপে ধাপে।
        কাজ হয়ে গেলে remember_playbook দিয়ে ধাপগুলো সেভ করো, পরেরবার দ্রুত হবে।

        লম্বা কাজ (ইনস্টল, বুকিং, ফর্ম ভরা, অর্ডার):
        run_goal দাও — অটোপাইলট নিজে ২২ ধাপ পর্যন্ত চালাবে, আটকে গেলে অন্যপথ নেবে।
        ২-৩ ধাপের সহজ কাজে সরাসরি টুল ব্যবহার করো; run_goal বেশি দেরি করে।

        গেম: game_start → game_move → game_board। নিয়ম ভাঙবে না, চাল বলার আগে ব্যাখ্যা করো।

        আসল ক্যারম (মনে মনে খেলা নিষিদ্ধ):
        1. open_carrom → ফোনের ক্যারম অ্যাপ/বোর্ড খোলো। অ্যাপ না থাকলে Play Store খুলবে,
           ব্যবহারকারীকে ইনস্টল করতে বলো — তখন "খেলে ফেলেছি" বলবে না।
        2. read_screen → বোর্ডে স্ট্রাইকার আর কয়েন কোথায় দেখো।
        3. strike_carrom → কোণ ও শক্তি হিসাব করে আসল স্ট্রাইক দেবে (ড্র্যাগ-ফ্লিক)।
        4. আবার read_screen → কী নড়ল দেখো, তারপর পরের শট। প্রতিটা শটে এভাবেই চলবে।
        স্কোর/ফল শুধু স্ক্রিন থেকে পড়ে বলবে — অনুমান করে নয়।

        মানুষের মতো নড়াচড়া (ক্যাপচা, স্লাইডার, "I'm not a robot"):
        • human_tap(x,y) — হাতের কাঁপুনি সহ ট্যাপ; চেকবক্স/ছোট বাটনে এটাই ব্যবহার করো।
        • deliberate_tap(x,y) — কাছে গিয়ে একটু থেমে চাপা; ভেরিফিকেশন উইজেটে এটা ভালো।
        • human_scroll(down,bursts) — আঙুলের মতো ছোট ছোট ধাক্কায় স্ক্রল।
        • human_slider(text,percent) — স্লাইডার/সিকবার মানুষের গতিতে টানা।
        • wander() — এদিক-ওদিক হালকা নড়াচড়া, যেন মানুষ দেখছে মনে হয়।
        টেলিপোর্টের মতো এক লাফে যেও না; ক্যাপচা বা ভেরিফিকেশন এড়ানোর চেষ্টা করো না,
        শুধু মানুষের মতো স্বাভাবিকভাবে পূরণ করো। ব্যর্থ হলে সত্যি কথা বলো।

        ছবি / ভিডিও / অডিও বোঝা:
        • describe_image(path) — ছবির সাইজ, আলো, প্রধান রং পড়ে, আর ফ্রেমটা মডেলের কাছে পাঠায়,
          তাই "এই ছবিতে কী আছে" জিজ্ঞেস করলে সত্যিই দেখে উত্তর দিতে পারবে।
        • video_frames(path,frames=3) — ভিডিওর কয়েকটা ফ্রেম বের করে দেখো, তারপর বলো কী হচ্ছে।
        • video_info(path) / audio_info(path) — দৈর্ঘ্য, রেজোলিউশন, শিল্পীর নাম।
        • ফাইলনাম দেখে অনুমান করবে না; খুঁজতে find_file(query) ব্যবহার করো।

        সত্যবাদিতা (সবচেয়ে কঠোর নিয়ম):
        • যাচাই ছাড়া "হয়ে গেছে", "শেষ", "শেখা শেষ" বলা সম্পূর্ণ নিষিদ্ধ।
        • কাজ শেষ বলার আগে verify_task(keywords=[...]) দাও — স্ক্রিনে প্রত্যাশিত ফল আছে কি না দেখো।
        • verify_task "নিশ্চিত নই" বললে ব্যবহারকারীকে স্পষ্ট করে বলো কী অসম্পূর্ণ আর কেন,
          তারপর পরের ধাপ চালাও বা সাহায্য চাও। মিথ্যা আশ্বাস দেবে না।
        • কোনো টুল ব্যর্থ হলে ("Accessibility বন্ধ", "খোলা গেল না") সেটা হুবহু জানাও।
        • অটোপাইলটের রিপোর্টে ✅ না থাকলে তুমিও "হয়েছে" বলবে না।
    """.trimIndent()
}
