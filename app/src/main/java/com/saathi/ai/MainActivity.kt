package com.saathi.ai

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.saathi.ai.access.JimiAccessibilityService
import com.saathi.ai.bubble.BubbleService
import com.saathi.ai.capture.CameraController
import com.saathi.ai.capture.LiveSessionService
import com.saathi.ai.data.ChatStore
import com.saathi.ai.data.MemoryStore
import com.saathi.ai.data.SecurePrefs
import com.saathi.ai.hotword.HotwordService
import com.saathi.ai.live.Phase
import com.saathi.ai.live.SessionHub
import com.saathi.ai.ui.FilesScreen
import com.saathi.ai.ui.GamesScreen
import com.saathi.ai.ui.HomeScreen
import com.saathi.ai.ui.HubScreen
import com.saathi.ai.ui.MemoryScreen

import com.saathi.ai.ui.PermissionsScreen
import com.saathi.ai.ui.SettingsScreen
import com.saathi.ai.ui.SplashScreen
import com.saathi.ai.ui.ThemePickerScreen
import com.saathi.ai.ui.theme.JimiThemes
import com.saathi.ai.ui.theme.SaathiTheme
import com.saathi.ai.ui.theme.resolveTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private lateinit var prefs: SecurePrefs
    private var cameraController: CameraController? = null
    private var previewView: PreviewView? = null

    private var accessibilityOn = false
    private var bubbleOn = false
    private var hotwordOn = false

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            LiveSessionService.startScreen(this, result.resultCode, result.data!!)
            SessionHub.update { it.copy(screenOn = true) }
        } else {
            SessionHub.update { it.copy(screenOn = false) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = SecurePrefs(this)
        refreshSpecial()

        if (shouldWake(intent)) {
            wakeUp()
        }
        requestedScreen = intent?.getStringExtra("screen")

        setContent {
            var themeSpec by remember { mutableStateOf(resolveTheme(prefs.themeId, prefs.accentOverride)) }
            SaathiTheme(spec = themeSpec) {
                JimiApp(
                    initialScreen = requestedScreen.also { requestedScreen = null },
                    prefs = prefs,
                    accessibilityOn = accessibilityOn,
                    bubbleOn = bubbleOn,
                    hotwordOn = hotwordOn,
                    onThemeChanged = { themeSpec = resolveTheme(it, prefs.accentOverride) },
                    onAccentChanged = { themeSpec = resolveTheme(prefs.themeId, it) },
                    onStartSession = { startSession() },
                    onEndSession = {
                        stopCamera()
                        LiveSessionService.stop(this)
                        SessionHub.update {
                            it.copy(phase = Phase.ENDED, cameraOn = false, screenOn = false)
                        }
                    },
                    onSetMic = { enabled ->
                        LiveSessionService.setMic(this, enabled)
                        SessionHub.update { it.copy(micOn = enabled) }
                    },
                    onSetCamera = { enabled -> if (enabled) startCamera() else stopCamera() },
                    onSwitchCamera = { startCamera(front = false) },
                    onSetScreen = { enabled ->
                        if (enabled) requestScreenCapture() else {
                            LiveSessionService.stopScreen(this)
                            SessionHub.update { it.copy(screenOn = false) }
                        }
                    },
                    onToggleBubble = {
                        if (Settings.canDrawOverlays(this)) {
                            if (bubbleOn) BubbleService.hide(this) else BubbleService.show(this)
                            bubbleOn = !bubbleOn
                            prefs.bubbleEnabled = bubbleOn
                        } else {
                            openOverlaySettings()
                        }
                    },
                    onToggleHotword = {
                        if (hotwordOn) HotwordService.stop(this) else HotwordService.start(this)
                        hotwordOn = !hotwordOn
                        prefs.hotwordEnabled = hotwordOn
                    },
                    onMinimize = { minimizeToBubble() },
                    onSendText = { text -> LiveSessionService.sendText(this, text) },
                    getPreview = { previewView },
                    onRefreshSpecial = { refreshSpecial() }
                )
            }
        }
    }

    /** Invoked for the "Hey Jimi" notification tap / assist gesture / QS tile. */
    /**
     * One entry point for every "wake Jimi" path: hotword notification, digital
     * assistant gesture, Quick Settings tile, and the in-app button.
     * It starts the live session immediately (auto-connect) when the mic exists.
     */
    private fun wakeUp() {
        SessionHub.update { it.copy(phase = Phase.CONNECTING, error = null) }
        if (hasMic()) {
            LiveSessionService.start(this)
        } else {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 900)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (shouldWake(intent)) {
            wakeUp()
        }
    }

    /**
     * Every way the user can summon Jimi, in one place.
     *
     * The bug this fixes: the digital-assistant session used to send an extra named
     * "wake" while this activity only looked for the hotword extra, so holding the
     * home button opened Jimi *without starting it*. Now the hotword, the assistant
     * session, the ASSIST / VOICE_COMMAND intents and the Quick Settings tile all
     * funnel through here and auto-connect.
     */
    /** Set from the task-runner notification so the app opens straight on that page. */
    private var requestedScreen: String? = null

    private fun shouldWake(intent: Intent?): Boolean {
        if (intent == null) return false
        if (intent.getBooleanExtra(HotwordService.EXTRA_WAKE, false)) return true
        if (intent.getBooleanExtra("wake", false)) return true
        if (intent.getBooleanExtra("forceStart", false)) return true
        return when (intent.action) {
            Intent.ACTION_ASSIST,
            Intent.ACTION_VOICE_COMMAND,
            "android.intent.action.VOICE_ASSIST" -> true
            else -> false
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSpecial()
        // The app is in front of the user, so the floating bubble steps aside.
        if (bubbleOn) com.saathi.ai.bubble.BubbleService.autoHide(this)
        if (prefs.hotwordEnabled && !hotwordOn && hasMic()) {
            HotwordService.start(this)
            hotwordOn = true
        }
    }

    private fun refreshSpecial() {
        accessibilityOn = JimiAccessibilityService.isEnabled()
        bubbleOn = Settings.canDrawOverlays(this) && prefs.bubbleEnabled
        hotwordOn = prefs.hotwordEnabled && hasMic()
    }

    private fun startSession() {
        if (!hasMic()) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 900)
            return
        }
        SessionHub.update { it.copy(phase = Phase.CONNECTING, model = prefs.model, error = null) }
        LiveSessionService.start(this)
    }

    private fun hasMic(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun minimizeToBubble() {
        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings()
            return
        }
        prefs.bubbleEnabled = true
        BubbleService.show(this)
        bubbleOn = true
        // a little delay so the bubble appears before the window animates away
        window.decorView.postDelayed({ moveTaskToBack(true) }, 120)
    }

    private fun openOverlaySettings() {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun startCamera(front: Boolean = true) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 901)
            return
        }
        stopCamera()

        val pv = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        previewView = pv

        val controller = CameraController(this, this)
        controller.onFrame = { jpeg -> SessionHub.sendCameraFrame(jpeg) }
        cameraController = controller
        controller.start(front = front, onError = {
            SessionHub.update { st -> st.copy(cameraOn = false) }
        })
        SessionHub.update { it.copy(cameraOn = true) }
    }

    private fun stopCamera() {
        cameraController?.stop()
        cameraController = null
        previewView = null
        SessionHub.update { it.copy(cameraOn = false) }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.isNotEmpty() &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        when (requestCode) {
            900 -> if (granted) startSession()
            901 -> if (granted) startCamera()
        }
    }

    private fun requestScreenCapture() {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val base = manager.createScreenCaptureIntent()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val custom = runCatching {
                manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForUserChoice())
            }.getOrNull()
            screenCaptureLauncher.launch(custom ?: base)
            return
        }
        screenCaptureLauncher.launch(base)
    }

    override fun onPause() {
        super.onPause()
        // Leaving the app: the bubble comes back so background work stays visible.
        if (bubbleOn && prefs.bubbleEnabled) {
            com.saathi.ai.bubble.BubbleService.autoShow(this)
        }
    }

    override fun onDestroy() {
        stopCamera()
        super.onDestroy()
    }
}

@Composable
private fun JimiApp(
    prefs: SecurePrefs,
    accessibilityOn: Boolean,
    bubbleOn: Boolean,
    hotwordOn: Boolean,
    initialScreen: String? = null,
    onThemeChanged: (String) -> Unit,
    onAccentChanged: (Int) -> Unit = {},
    onStartSession: () -> Unit,
    onEndSession: () -> Unit,
    onSetMic: (Boolean) -> Unit,
    onSetCamera: (Boolean) -> Unit,
    onSwitchCamera: () -> Unit,
    onSetScreen: (Boolean) -> Unit,
    onToggleBubble: () -> Unit,
    onToggleHotword: () -> Unit,
    onMinimize: () -> Unit,
    onSendText: (String) -> Unit,
    getPreview: () -> PreviewView?,
    onRefreshSpecial: () -> Unit
) {
    val context = LocalContext.current

    // A deep link (task notification) can land straight on a page once onboarding
    // is done; otherwise always start at the splash so permissions get checked.
    var screen by remember {
        mutableStateOf(
            if (initialScreen != null && prefs.onboardingDone) initialScreen else "splash"
        )
    }
    // secondary pages (memory/themes/files/games) remember their origin, so the
    // back arrow always returns the user to where they actually came from
    var returnTo by remember { mutableStateOf("home") }
    var configured by remember { mutableStateOf(prefs.isConfigured) }
    val state by SessionHub.state.collectAsState()

    // v3 bring-up: these must exist before the user asks for anything, so a task
    // survives a restart, errors are on record and the browser can be killed.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { com.saathi.ai.access.ShizukuBridge.attach(context) }
            runCatching { com.saathi.ai.core.ErrorCenter.load(context) }
            runCatching { com.saathi.ai.task.TaskEngine.init(context) }
            runCatching { com.saathi.ai.web.WebBrowser.registerKillSwitch() }
        }
    }

    LaunchedEffect(Unit) {
        delay(1400)
        if (!SessionHub.isServiceRunning) {
            SessionHub.update {
                if (it.phase == Phase.CONNECTING || it.phase == Phase.LISTENING ||
                    it.phase == Phase.SPEAKING || it.phase == Phase.THINKING
                ) {
                    it.copy(phase = Phase.IDLE, micOn = false, cameraOn = false)
                } else it
            }
        }
    }

    AnimatedContent(
        targetState = screen,
        transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(220)) },
        label = "screen"
    ) { current ->
        when (current) {
            "splash" -> SplashScreen(onFinished = {
                screen = if (!prefs.onboardingDone) "permissions" else "home"
                onRefreshSpecial()
            })

            "permissions" -> PermissionsScreen(onDone = {
                prefs.onboardingDone = true
                onRefreshSpecial()
                screen = "home"
            })

            "draw" -> com.saathi.ai.create.DrawingScreen(
                onBack = { screen = returnTo },
                onShare = { bmp ->
                    runCatching {
                        val dir = java.io.File(context.cacheDir, "art")
                        dir.mkdirs()
                        val f = java.io.File(dir, "jimi-art-" + System.currentTimeMillis() + ".png")
                        f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 96, it) }
                        com.saathi.ai.access.SmartTools(context).shareImage("file://" + f.absolutePath)
                    }
                }
            )

            "hub" -> HubScreen(
                accessibilityHealthy = accessibilityOn,
                onOpenMemory = { returnTo = "hub"; screen = "memory" },
                onOpenThemes = { returnTo = "hub"; screen = "themes" },
                onOpenDraw = { returnTo = "hub"; screen = "draw" },
                onOpenFiles = { screen = "files" },
                onOpenGames = { screen = "games" },
                onOpenTasks = { returnTo = "hub"; screen = "tasks" },
                onOpenControl = { returnTo = "hub"; screen = "control" },
                onBack = { screen = "home" }
            )

            // The background worker's notification deep-links straight here.
            "tasks" -> com.saathi.ai.ui.TaskScreen(onBack = { screen = returnTo })

            "control" -> com.saathi.ai.ui.ControlCenterScreen(onBack = { screen = returnTo })

            "files" -> FilesScreen(onBack = { screen = "hub" })

            "games" -> GamesScreen(
                onBack = { screen = "hub" },
                onPlayCarrom = {
                    screen = "home"
                    onSendText("ক্যারম খেলব — আসল বোর্ড খুলে নিজে স্ট্রাইক করো, " +
                        "মুখে মুখে না। প্রথমে open_carrom, তারপর বোর্ড পড়ে strike_carrom।")
                },
                onStartGame = { game -> screen = "home"; onSendText("$game শুরু করো") }
            )

            "memory" -> MemoryScreen(
                memory = com.saathi.ai.data.MemoryStore(context),
                chats = com.saathi.ai.data.ChatStore(context),
                onBack = { screen = returnTo }
            )

            "themes" -> ThemePickerScreen(
                current = prefs.themeId,
                onPick = {
                    prefs.themeId = it
                    onThemeChanged(it)
                },
                onBack = { screen = returnTo }
            )

            "settings" -> SettingsScreen(
                prefs = prefs,
                onBack = {
                    configured = prefs.isConfigured
                    screen = "home"
                },
                onSaved = { configured = prefs.isConfigured },
                onOpenThemes = { returnTo = "settings"; screen = "themes" },
                onThemeChanged = onThemeChanged,
                onAccentChanged = onAccentChanged
            )

            else -> HomeScreen(
                state = state,
                configured = configured,
                hasMicPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED,
                accessibilityOn = accessibilityOn,
                bubbleOn = bubbleOn,
                hotwordOn = hotwordOn,
                cameraPreviewView = getPreview(),
                onStart = onStartSession,
                onEnd = onEndSession,
                onToggleMic = { onSetMic(!state.micOn) },
                onToggleCamera = { onSetCamera(!state.cameraOn) },
                onSwitchCamera = onSwitchCamera,
                onToggleScreen = { onSetScreen(!state.screenOn) },
                onToggleBubble = onToggleBubble,
                onToggleHotword = onToggleHotword,
                onSendText = onSendText,
                onOpenSettings = { screen = "settings" },
                onOpenMemory = { returnTo = "home"; screen = "memory" },
                onOpenDraw = { returnTo = "home"; screen = "draw" },
                onOpenHub = { screen = "hub" },
                onOpenTasks = { returnTo = "home"; screen = "tasks" },
                onNewChat = {
                    com.saathi.ai.data.ChatStore(context).newChat()
                    SessionHub.update { it.copy(userText = "", modelText = "", lastTool = "", bubbleMsg = "", error = null) }
                },
                onMinimize = onMinimize
            )
        }
    }
}
