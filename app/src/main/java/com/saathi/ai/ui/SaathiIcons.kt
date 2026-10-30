package com.saathi.ai.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * A tiny, self-contained icon set built from polygons so the app does not need
 * the huge `material-icons-extended` artifact (which would slow CI builds down).
 */
object SaathiIcons {

    private fun icon(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.White),
                pathFillType = PathFillType.NonZero,
                pathBuilder = block
            )
        }.build()

    /** Screen / monitor frame + rising arrow. */
    val ScreenShare: ImageVector by lazy {
        icon("screen_share") {
            // monitor frame (4 bars)
            moveTo(2f, 3.6f); lineTo(22f, 3.6f); lineTo(22f, 5.6f); lineTo(2f, 5.6f); close()
            moveTo(2f, 15.4f); lineTo(22f, 15.4f); lineTo(22f, 17.4f); lineTo(2f, 17.4f); close()
            moveTo(2f, 5.6f); lineTo(4f, 5.6f); lineTo(4f, 15.4f); lineTo(2f, 15.4f); close()
            moveTo(20f, 5.6f); lineTo(22f, 5.6f); lineTo(22f, 15.4f); lineTo(20f, 15.4f); close()
            // stand
            moveTo(10.6f, 17.4f); lineTo(13.4f, 17.4f); lineTo(13.4f, 20.4f); lineTo(10.6f, 20.4f); close()
            moveTo(7.6f, 20.4f); lineTo(16.4f, 20.4f); lineTo(16.4f, 22.2f); lineTo(7.6f, 22.2f); close()
            // arrow up
            moveTo(12f, 6.4f)
            lineTo(15.6f, 10.2f); lineTo(13.2f, 10.2f); lineTo(13.2f, 14.4f)
            lineTo(10.8f, 14.4f); lineTo(10.8f, 10.2f); lineTo(8.4f, 10.2f)
            close()
        }
    }

    val Mic: ImageVector by lazy {
        icon("mic") {
            // capsule
            moveTo(9f, 6f)
            lineTo(10f, 4f); lineTo(12f, 3.2f); lineTo(14f, 4f); lineTo(15f, 6f)
            lineTo(15f, 10f); lineTo(14f, 12f); lineTo(12f, 13f); lineTo(10f, 12f); lineTo(9f, 10f)
            close()
            // stem + base
            moveTo(11.1f, 13.2f); lineTo(12.9f, 13.2f); lineTo(12.9f, 17.2f); lineTo(11.1f, 17.2f); close()
            moveTo(8.6f, 17.2f); lineTo(15.4f, 17.2f); lineTo(15.4f, 19.4f); lineTo(8.6f, 19.4f); close()
        }
    }

    val MicOff: ImageVector by lazy {
        icon("mic_off") {
            moveTo(9f, 5f)
            lineTo(10f, 3f); lineTo(12f, 2.2f); lineTo(14f, 3f); lineTo(15f, 5f)
            lineTo(15f, 9f); lineTo(14f, 11f); lineTo(12f, 12f); lineTo(10f, 11f); lineTo(9f, 9f)
            close()
            moveTo(11.1f, 12.2f); lineTo(12.9f, 12.2f); lineTo(12.9f, 16.4f); lineTo(11.1f, 16.4f); close()
            moveTo(8.6f, 16.4f); lineTo(15.4f, 16.4f); lineTo(15.4f, 18.4f); lineTo(8.6f, 18.4f); close()
            // slash
            moveTo(4.4f, 2.6f); lineTo(6.2f, 2.6f); lineTo(20.2f, 21.4f); lineTo(18.4f, 21.4f); close()
        }
    }

    val Videocam: ImageVector by lazy {
        icon("videocam") {
            moveTo(2.4f, 6f); lineTo(15f, 6f); lineTo(16.4f, 7.4f); lineTo(16.4f, 16.6f)
            lineTo(15f, 18f); lineTo(2.4f, 18f); lineTo(1.6f, 17f); lineTo(1.6f, 7f); close()
            moveTo(17.6f, 10.4f); lineTo(22.4f, 6.4f); lineTo(22.4f, 17.6f); lineTo(17.6f, 13.6f); close()
        }
    }

    val VideocamOff: ImageVector by lazy {
        icon("videocam_off") {
            moveTo(2.4f, 5f); lineTo(15f, 5f); lineTo(16.4f, 6.4f); lineTo(16.4f, 15.6f)
            lineTo(15f, 17f); lineTo(2.4f, 17f); lineTo(1.6f, 16f); lineTo(1.6f, 6f); close()
            moveTo(17.6f, 9.4f); lineTo(22.4f, 5.4f); lineTo(22.4f, 16.6f); lineTo(17.6f, 12.6f); close()
            moveTo(4.4f, 3.4f); lineTo(6.2f, 3.4f); lineTo(20.2f, 22.2f); lineTo(18.4f, 22.2f); close()
        }
    }

    val Cameraswitch: ImageVector by lazy {
        icon("cameraswitch") {
            // top arrow to the right
            moveTo(2.6f, 6.4f); lineTo(13.4f, 6.4f); lineTo(13.4f, 3.8f); lineTo(18.4f, 8f)
            lineTo(13.4f, 12.2f); lineTo(13.4f, 9.6f); lineTo(2.6f, 9.6f); close()
            // bottom arrow to the left
            moveTo(21.4f, 17.6f); lineTo(10.6f, 17.6f); lineTo(10.6f, 20.2f); lineTo(5.6f, 16f)
            lineTo(10.6f, 11.8f); lineTo(10.6f, 14.4f); lineTo(21.4f, 14.4f); close()
        }
    }

    val Keyboard: ImageVector by lazy {
        icon("keyboard") {
            moveTo(2f, 6f); lineTo(22f, 6f); lineTo(22f, 7.6f); lineTo(2f, 7.6f); close()
            moveTo(2f, 16.4f); lineTo(22f, 16.4f); lineTo(22f, 18f); lineTo(2f, 18f); close()
            moveTo(2f, 7.6f); lineTo(3.6f, 7.6f); lineTo(3.6f, 16.4f); lineTo(2f, 16.4f); close()
            moveTo(20.4f, 7.6f); lineTo(22f, 7.6f); lineTo(22f, 16.4f); lineTo(20.4f, 16.4f); close()
            moveTo(4.8f, 9.4f); lineTo(6.2f, 9.4f); lineTo(6.2f, 10.8f); lineTo(4.8f, 10.8f); close()
            moveTo(7.6f, 9.4f); lineTo(9f, 9.4f); lineTo(9f, 10.8f); lineTo(7.6f, 10.8f); close()
            moveTo(10.4f, 9.4f); lineTo(11.8f, 9.4f); lineTo(11.8f, 10.8f); lineTo(10.4f, 10.8f); close()
            moveTo(13.2f, 9.4f); lineTo(14.6f, 9.4f); lineTo(14.6f, 10.8f); lineTo(13.2f, 10.8f); close()
            moveTo(16f, 9.4f); lineTo(17.4f, 9.4f); lineTo(17.4f, 10.8f); lineTo(16f, 10.8f); close()
            moveTo(18.8f, 9.4f); lineTo(20.2f, 9.4f); lineTo(20.2f, 10.8f); lineTo(18.8f, 10.8f); close()
            moveTo(7.4f, 12.8f); lineTo(16.6f, 12.8f); lineTo(16.6f, 14.4f); lineTo(7.4f, 14.4f); close()
        }
    }

    val StopCircle: ImageVector by lazy {
        icon("stop_circle") {
            moveTo(8.4f, 3.4f); lineTo(15.6f, 3.4f); lineTo(20.6f, 8.4f); lineTo(20.6f, 15.6f)
            lineTo(15.6f, 20.6f); lineTo(8.4f, 20.6f); lineTo(3.4f, 15.6f); lineTo(3.4f, 8.4f)
            close()
            // inner square — same colour, reads as a stop chip
            moveTo(8.6f, 8.6f); lineTo(15.4f, 8.6f); lineTo(15.4f, 15.4f); lineTo(8.6f, 15.4f); close()
        }
    }

    val Send: ImageVector by lazy {
        icon("send") {
            moveTo(1.8f, 20.6f); lineTo(22.6f, 12f); lineTo(1.8f, 3.4f)
            lineTo(1.8f, 10.2f); lineTo(16.2f, 12f); lineTo(1.8f, 13.8f)
            close()
        }
    }

    val Settings: ImageVector by lazy {
        icon("settings") {
            // three sliders
            moveTo(2.6f, 5.2f); lineTo(21.4f, 5.2f); lineTo(21.4f, 7.2f); lineTo(2.6f, 7.2f); close()
            moveTo(8f, 3.2f); lineTo(11f, 3.2f); lineTo(11f, 9.2f); lineTo(8f, 9.2f); close()
            moveTo(2.6f, 11f); lineTo(21.4f, 11f); lineTo(21.4f, 13f); lineTo(2.6f, 13f); close()
            moveTo(14f, 9f); lineTo(17f, 9f); lineTo(17f, 15f); lineTo(14f, 15f); close()
            moveTo(2.6f, 16.8f); lineTo(21.4f, 16.8f); lineTo(21.4f, 18.8f); lineTo(2.6f, 18.8f); close()
            moveTo(5.6f, 14.8f); lineTo(8.6f, 14.8f); lineTo(8.6f, 20.8f); lineTo(5.6f, 20.8f); close()
        }
    }

    val ArrowBack: ImageVector by lazy {
        icon("arrow_back") {
            moveTo(15.8f, 3.4f); lineTo(18.2f, 5.8f); lineTo(11.8f, 12f); lineTo(18.2f, 18.2f)
            lineTo(15.8f, 20.6f); lineTo(7f, 12f); close()
            moveTo(2.6f, 10.9f); lineTo(11f, 10.9f); lineTo(11f, 13.1f); lineTo(2.6f, 13.1f); close()
        }
    }

    val Check: ImageVector by lazy {
        icon("check") {
            moveTo(9.2f, 18.4f); lineTo(2.8f, 12f); lineTo(5.2f, 9.6f); lineTo(9.2f, 13.6f)
            lineTo(18.8f, 4f); lineTo(21.2f, 6.4f); close()
        }
    }

    val Visibility: ImageVector by lazy {
        icon("visibility") {
            moveTo(1.4f, 12f)
            quadTo(12f, 2.6f, 22.6f, 12f)
            quadTo(12f, 21.4f, 1.4f, 12f)
            close()
            // pupil
            moveTo(12f, 8.4f); lineTo(14.6f, 9.5f); lineTo(15.6f, 12f); lineTo(14.6f, 14.5f)
            lineTo(12f, 15.6f); lineTo(9.4f, 14.5f); lineTo(8.4f, 12f); lineTo(9.4f, 9.5f)
            close()
        }
    }

    val VisibilityOff: ImageVector by lazy {
        icon("visibility_off") {
            moveTo(1.4f, 12f)
            quadTo(12f, 2.6f, 22.6f, 12f)
            quadTo(12f, 21.4f, 1.4f, 12f)
            close()
            moveTo(12f, 8.4f); lineTo(14.6f, 9.5f); lineTo(15.6f, 12f); lineTo(14.6f, 14.5f)
            lineTo(12f, 15.6f); lineTo(9.4f, 14.5f); lineTo(8.4f, 12f); lineTo(9.4f, 9.5f)
            close()
            moveTo(3.2f, 2.8f); lineTo(5.2f, 2.8f); lineTo(21.2f, 21.2f); lineTo(19.2f, 21.2f); close()
        }
    }

    val OpenInNew: ImageVector by lazy {
        icon("open_in_new") {
            // corner frame
            moveTo(3.2f, 3.2f); lineTo(10.4f, 3.2f); lineTo(10.4f, 5.8f); lineTo(5.8f, 5.8f)
            lineTo(5.8f, 18.2f); lineTo(18.2f, 18.2f); lineTo(18.2f, 13.6f); lineTo(20.8f, 13.6f)
            lineTo(20.8f, 20.8f); lineTo(3.2f, 20.8f); close()
            // arrow to the top-right
            moveTo(19.4f, 2.6f); lineTo(19.4f, 10.6f); lineTo(16.6f, 7.8f); lineTo(12.2f, 12.2f)
            lineTo(9.4f, 9.4f); lineTo(13.8f, 5f); lineTo(11f, 2.2f); close()
        }
    }

    val Notifications: ImageVector by lazy {
        icon("notifications") {
            moveTo(9.6f, 4.6f); lineTo(12f, 2.8f); lineTo(14.4f, 4.6f); lineTo(15.4f, 8f)
            lineTo(16.2f, 11.6f); lineTo(17.8f, 14f); lineTo(17.8f, 16f); lineTo(6.2f, 16f)
            lineTo(6.2f, 14f); lineTo(7.8f, 11.6f); lineTo(8.6f, 8f); close()
            moveTo(10.2f, 17.2f); lineTo(13.8f, 17.2f); lineTo(13.8f, 19.2f); lineTo(12.8f, 21f)
            lineTo(11.2f, 21f); lineTo(10.2f, 19.2f); close()
        }
    }

    val PhotoCamera: ImageVector by lazy {
        icon("photo_camera") {
            // frame
            moveTo(2f, 4.6f); lineTo(22f, 4.6f); lineTo(22f, 6.2f); lineTo(2f, 6.2f); close()
            moveTo(2f, 17.8f); lineTo(22f, 17.8f); lineTo(22f, 19.4f); lineTo(2f, 19.4f); close()
            moveTo(2f, 6.2f); lineTo(3.6f, 6.2f); lineTo(3.6f, 17.8f); lineTo(2f, 17.8f); close()
            moveTo(20.4f, 6.2f); lineTo(22f, 6.2f); lineTo(22f, 17.8f); lineTo(20.4f, 17.8f); close()
            // mountains
            moveTo(4.6f, 16.8f); lineTo(9.2f, 10f); lineTo(12.2f, 14.4f); lineTo(14f, 12f)
            lineTo(19.4f, 16.8f); close()
            // sun
            moveTo(14.6f, 7.4f); lineTo(17.4f, 7.4f); lineTo(17.4f, 10.2f); lineTo(14.6f, 10.2f); close()
        }
    }

    val Call: ImageVector by lazy {
        icon("call") {
            // receiver handset
            moveTo(4.2f, 3.2f); lineTo(8.6f, 3.2f); lineTo(9.6f, 7.4f); lineTo(7.4f, 8.8f)
            lineTo(15.2f, 16.6f); lineTo(16.6f, 14.4f); lineTo(20.8f, 15.4f); lineTo(20.8f, 19.8f)
            lineTo(19.6f, 20.8f); lineTo(18.2f, 20.8f)
            lineTo(3.2f, 5.8f); lineTo(3.2f, 4.4f); close()
        }
    }

    val Contacts: ImageVector by lazy {
        icon("contacts") {
            moveTo(4f, 2.4f); lineTo(19.5f, 2.4f); lineTo(19.5f, 4.4f); lineTo(4f, 4.4f); close()
            moveTo(4f, 19.6f); lineTo(19.5f, 19.6f); lineTo(19.5f, 21.6f); lineTo(4f, 21.6f); close()
            moveTo(4f, 4.4f); lineTo(5.6f, 4.4f); lineTo(5.6f, 19.6f); lineTo(4f, 19.6f); close()
            moveTo(17.6f, 4.4f); lineTo(19.5f, 4.4f); lineTo(19.5f, 19.6f); lineTo(17.6f, 19.6f); close()
            moveTo(9.2f, 8f); lineTo(12.6f, 8f); lineTo(13.4f, 10.4f); lineTo(12.6f, 12.6f)
            lineTo(9.2f, 12.6f); lineTo(8.4f, 10.4f); close()
            moveTo(7.4f, 17.2f); lineTo(14.4f, 17.2f); lineTo(14.4f, 18.6f); lineTo(7.4f, 18.6f); close()
            moveTo(7.6f, 14.4f); lineTo(8.8f, 13f); lineTo(13f, 13f); lineTo(14.2f, 14.4f)
            lineTo(14.2f, 16.6f); lineTo(7.6f, 16.6f); close()
        }
    }

    val Sms: ImageVector by lazy {
        icon("sms") {
            moveTo(3f, 4f); lineTo(21f, 4f); lineTo(21f, 15.4f); lineTo(8.4f, 15.4f)
            lineTo(4.6f, 19.4f); lineTo(5.4f, 15.4f); lineTo(3f, 15.4f); close()
            moveTo(6.6f, 8.6f); lineTo(17.4f, 8.6f); lineTo(17.4f, 10.4f); lineTo(6.6f, 10.4f); close()
            moveTo(6.6f, 12f); lineTo(13.4f, 12f); lineTo(13.4f, 13.6f); lineTo(6.6f, 13.6f); close()
        }
    }

    val Gallery: ImageVector by lazy {
        icon("gallery") {
            moveTo(3.2f, 4.6f); lineTo(20.8f, 4.6f); lineTo(20.8f, 6.2f); lineTo(3.2f, 6.2f); close()
            moveTo(3.2f, 17.8f); lineTo(20.8f, 17.8f); lineTo(20.8f, 19.4f); lineTo(3.2f, 19.4f); close()
            moveTo(3.2f, 6.2f); lineTo(4.8f, 6.2f); lineTo(4.8f, 17.8f); lineTo(3.2f, 17.8f); close()
            moveTo(19.2f, 6.2f); lineTo(20.8f, 6.2f); lineTo(20.8f, 17.8f); lineTo(19.2f, 17.8f); close()
            moveTo(5.4f, 16.6f); lineTo(10.2f, 9.4f); lineTo(13.2f, 14f); lineTo(15f, 11.6f)
            lineTo(18.6f, 16.6f); close()
            moveTo(14.4f, 7.4f); lineTo(17.2f, 7.4f); lineTo(17.2f, 10.2f); lineTo(14.4f, 10.2f); close()
        }
    }

    val Accessibility: ImageVector by lazy {
        icon("accessibility") {
            moveTo(10.4f, 2.4f); lineTo(13.6f, 2.4f); lineTo(13.6f, 5.6f); lineTo(10.4f, 5.6f); close()
            moveTo(4f, 6.4f); lineTo(20f, 6.4f); lineTo(20f, 8.4f); lineTo(13.4f, 8.4f)
            lineTo(13.4f, 11f); lineTo(17.4f, 18.4f); lineTo(15.6f, 19.4f); lineTo(12f, 12.8f)
            lineTo(8.4f, 19.4f); lineTo(6.6f, 18.4f); lineTo(10.6f, 11f); lineTo(10.6f, 8.4f)
            lineTo(4f, 8.4f); close()
        }
    }

    val KeyboardVoice: ImageVector by lazy {
        icon("keyboard_voice") {
            moveTo(9f, 5.4f); lineTo(10f, 3.4f); lineTo(12f, 2.6f); lineTo(14f, 3.4f); lineTo(15f, 5.4f)
            lineTo(15f, 9.4f); lineTo(14f, 11.4f); lineTo(12f, 12.2f); lineTo(10f, 11.4f); lineTo(9f, 9.4f)
            close()
            moveTo(11.1f, 12.4f); lineTo(12.9f, 12.4f); lineTo(12.9f, 16.4f); lineTo(11.1f, 16.4f); close()
            moveTo(8.6f, 16.4f); lineTo(15.4f, 16.4f); lineTo(15.4f, 18.4f); lineTo(8.6f, 18.4f); close()
        }
    }

    val Minimize: ImageVector by lazy {
        icon("minimize") {
            moveTo(4f, 13.4f); lineTo(10.6f, 13.4f); lineTo(10.6f, 20f); lineTo(8f, 20f)
            lineTo(8f, 16f); lineTo(4f, 16f); close()
            moveTo(13.4f, 4f); lineTo(16f, 4f); lineTo(16f, 8f); lineTo(20f, 8f)
            lineTo(20f, 10.6f); lineTo(13.4f, 10.6f); close()
        }
    }

    val Bubble: ImageVector by lazy {
        icon("bubble") {
            moveTo(12f, 3.2f)
            curveTo(17.4f, 3.2f, 21.4f, 6.6f, 21.4f, 10.9f)
            curveTo(21.4f, 15.2f, 17.4f, 18.6f, 12f, 18.6f)
            curveTo(10.9f, 18.6f, 9.85f, 18.45f, 8.9f, 18.2f)
            lineTo(4.4f, 20.8f); lineTo(5.5f, 16.6f)
            curveTo(3.85f, 15.3f, 2.6f, 13.25f, 2.6f, 10.9f)
            curveTo(2.6f, 6.6f, 6.6f, 3.2f, 12f, 3.2f)
            close()
        }
    }

    val Bolt: ImageVector by lazy {
        icon("bolt") {
            moveTo(13.2f, 2.2f); lineTo(5.4f, 13.4f); lineTo(10.4f, 13.4f)
            lineTo(9.2f, 21.8f); lineTo(18.2f, 10.2f); lineTo(12.6f, 10.2f); close()
        }
    }

    val Brain: ImageVector by lazy {
        icon("brain") {
            moveTo(9.2f, 3.2f)
            curveTo(6.6f, 3.2f, 4.6f, 5.2f, 4.6f, 7.7f)
            curveTo(3.2f, 8.4f, 2.4f, 9.8f, 2.4f, 11.4f)
            curveTo(2.4f, 12.9f, 3.1f, 14.2f, 4.3f, 15f)
            curveTo(4.2f, 15.4f, 4.2f, 15.8f, 4.2f, 16.2f)
            curveTo(4.2f, 18.6f, 6.2f, 20.6f, 8.6f, 20.6f)
            lineTo(9.2f, 20.6f); lineTo(9.2f, 3.2f); close()
            moveTo(14.8f, 3.2f); lineTo(14.8f, 20.6f); lineTo(15.4f, 20.6f)
            curveTo(17.8f, 20.6f, 19.8f, 18.6f, 19.8f, 16.2f)
            curveTo(19.8f, 15.8f, 19.8f, 15.4f, 19.7f, 15f)
            curveTo(20.9f, 14.2f, 21.6f, 12.9f, 21.6f, 11.4f)
            curveTo(21.6f, 9.8f, 20.8f, 8.4f, 19.4f, 7.7f)
            curveTo(19.4f, 5.2f, 17.4f, 3.2f, 14.8f, 3.2f)
            close()
        }
    }

    val Draw: ImageVector by lazy {
        icon("draw") {
            moveTo(3.4f, 20.6f); lineTo(7.8f, 19.4f); lineTo(19.4f, 7.8f)
            lineTo(16.2f, 4.6f); lineTo(4.6f, 16.2f); close()
            moveTo(17.6f, 3.2f); lineTo(19.0f, 1.8f); lineTo(22.2f, 5.0f); lineTo(20.8f, 6.4f); close()
        }
    }

    val Games: ImageVector by lazy {
        icon("games") {
            moveTo(7.0f, 6.4f); lineTo(17.0f, 6.4f)
            curveTo(19.4f, 6.4f, 21.0f, 8.4f, 21.4f, 10.8f)
            lineTo(22.2f, 16.0f)
            curveTo(22.5f, 17.6f, 21.3f, 19.0f, 19.7f, 19.0f)
            curveTo(18.9f, 19.0f, 18.2f, 18.6f, 17.7f, 18.0f)
            lineTo(16.2f, 16.0f); lineTo(7.8f, 16.0f); lineTo(6.3f, 18.0f)
            curveTo(5.8f, 18.6f, 5.1f, 19.0f, 4.3f, 19.0f)
            curveTo(2.7f, 19.0f, 1.5f, 17.6f, 1.8f, 16.0f)
            lineTo(2.6f, 10.8f)
            curveTo(3.0f, 8.4f, 4.6f, 6.4f, 7.0f, 6.4f)
            close()
            moveTo(8.0f, 9.2f); lineTo(8.0f, 11.2f); lineTo(6.0f, 11.2f); lineTo(6.0f, 12.8f)
            lineTo(8.0f, 12.8f); lineTo(8.0f, 14.8f); lineTo(9.6f, 14.8f); lineTo(9.6f, 12.8f)
            lineTo(11.6f, 12.8f); lineTo(11.6f, 11.2f); lineTo(9.6f, 11.2f); lineTo(9.6f, 9.2f); close()
            moveTo(16.4f, 10.0f)
            curveTo(17.1f, 10.0f, 17.6f, 10.5f, 17.6f, 11.2f)
            curveTo(17.6f, 11.9f, 17.1f, 12.4f, 16.4f, 12.4f)
            curveTo(15.7f, 12.4f, 15.2f, 11.9f, 15.2f, 11.2f)
            curveTo(15.2f, 10.5f, 15.7f, 10.0f, 16.4f, 10.0f)
            close()
        }
    }

    val Folder: ImageVector by lazy {
        icon("folder") {
            moveTo(2.6f, 6.2f)
            curveTo(2.6f, 5.0f, 3.6f, 4.0f, 4.8f, 4.0f)
            lineTo(9.0f, 4.0f); lineTo(11.0f, 6.4f); lineTo(19.2f, 6.4f)
            curveTo(20.4f, 6.4f, 21.4f, 7.4f, 21.4f, 8.6f)
            lineTo(21.4f, 17.8f)
            curveTo(21.4f, 19.0f, 20.4f, 20.0f, 19.2f, 20.0f)
            lineTo(4.8f, 20.0f)
            curveTo(3.6f, 20.0f, 2.6f, 19.0f, 2.6f, 17.8f)
            close()
        }
    }
}
