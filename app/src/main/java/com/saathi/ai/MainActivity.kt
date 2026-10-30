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
import com.saathi.ai.ui.HomeScreen
import com.saathi.ai.ui.MemoryScreen

import com.saathi.ai.ui.PermissionsScreen
import com.saathi.ai.ui.SettingsScreen
import com.saathi.ai.ui.SplashScreen
import com.saathi.ai.ui.ThemePickerScreen
import com.saathi.ai.ui.theme.JimiThemes
import com.saathi.ai.ui.theme.SaathiTheme
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

        if (intent?.getBooleanExtra(HotwordService.EXTRA_WAKE, false) == true) {
            wakeUp()
        }

        setContent {
            var themeSpec by remember { mutableStateOf(JimiThemes.get(prefs.themeId)) }
            SaathiTheme(spec = themeSpec) {
                JimiApp(
                    prefs = prefs,
                    accessibilityOn = accessibilityOn,
                    bubbleOn = bubbleOn,
                    hotwordOn = hotwordOn,
                    onThemeChanged = { themeSpec = JimiThemes.get(it) },
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
        if (intent.getBooleanExtra(HotwordService.EXTRA_WAKE, false)) {
            wakeUp()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSpecial()
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
    onThemeChanged: (String) -> Unit,
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

    var screen by remember { mutableStateOf("splash") }
    var configured by remember { mutableStateOf(prefs.isConfigured) }
    val state by SessionHub.state.collectAsState()

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
                onBack = { screen = "home" },
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

            "memory" -> MemoryScreen(
                memory = com.saathi.ai.data.MemoryStore(context),
                chats = com.saathi.ai.data.ChatStore(context),
                onBack = { screen = "settings" }
            )

            "themes" -> ThemePickerScreen(
                current = prefs.themeId,
                onPick = {
                    prefs.themeId = it
                    onThemeChanged(it)
                },
                onBack = { screen = "settings" }
            )

            "settings" -> SettingsScreen(
                prefs = prefs,
                onBack = {
                    configured = prefs.isConfigured
                    screen = "home"
                },
                onSaved = { configured = prefs.isConfigured },
                onOpenThemes = { screen = "themes" }
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
                onOpenMemory = { screen = "memory" },
                onOpenDraw = { screen = "draw" },
                onMinimize = onMinimize
            )
        }
    }
}
