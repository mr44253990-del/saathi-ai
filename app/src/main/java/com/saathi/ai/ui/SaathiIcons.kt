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

    val Add: ImageVector by lazy {
        icon("add") {
            moveTo(11.0f, 3.0f); lineTo(13.0f, 3.0f); lineTo(13.0f, 11.0f); lineTo(21.0f, 11.0f)
            lineTo(21.0f, 13.0f); lineTo(13.0f, 13.0f); lineTo(13.0f, 21.0f); lineTo(11.0f, 21.0f)
            lineTo(11.0f, 13.0f); lineTo(3.0f, 13.0f); lineTo(3.0f, 11.0f); lineTo(11.0f, 11.0f); close()
        }
    }

    val Palette: ImageVector by lazy {
        icon("palette") {
            moveTo(12f, 2.6f)
            curveTo(6.6f, 2.6f, 2.4f, 6.6f, 2.4f, 11.8f)
            curveTo(2.4f, 16.9f, 6.6f, 21.2f, 12f, 21.2f)
            curveTo(12.9f, 21.2f, 13.6f, 20.5f, 13.6f, 19.6f)
            curveTo(13.6f, 19.2f, 13.4f, 18.8f, 13.2f, 18.5f)
            curveTo(12.9f, 18.1f, 12.8f, 17.7f, 12.8f, 17.3f)
            curveTo(12.8f, 16.5f, 13.5f, 15.8f, 14.3f, 15.8f)
            lineTo(16.4f, 15.8f)
            curveTo(19.2f, 15.8f, 21.6f, 13.4f, 21.6f, 10.6f)
            curveTo(21.6f, 6.2f, 17.3f, 2.6f, 12f, 2.6f)
            close()
        }
    }

    // ------------------------------------------------------------ v2 additions
    // Everything below is polygon-built too: no PNG, no font glyph, no emoji.

    val Key: ImageVector by lazy {
        icon("key") {
            // ring
            moveTo(7.2f, 4.6f); lineTo(11.4f, 4.6f); lineTo(11.4f, 6.6f); lineTo(7.2f, 6.6f); close()
            moveTo(7.2f, 10.6f); lineTo(11.4f, 10.6f); lineTo(11.4f, 12.6f); lineTo(7.2f, 12.6f); close()
            moveTo(5.2f, 6.6f); lineTo(7.2f, 6.6f); lineTo(7.2f, 10.6f); lineTo(5.2f, 10.6f); close()
            moveTo(11.4f, 6.6f); lineTo(13.4f, 6.6f); lineTo(13.4f, 10.6f); lineTo(11.4f, 10.6f); close()
            // shaft + teeth
            moveTo(8.3f, 12.6f); lineTo(10.3f, 12.6f); lineTo(10.3f, 21.4f); lineTo(8.3f, 21.4f); close()
            moveTo(10.3f, 15.4f); lineTo(13.4f, 15.4f); lineTo(13.4f, 17f); lineTo(10.3f, 17f); close()
            moveTo(10.3f, 18.2f); lineTo(12.6f, 18.2f); lineTo(12.6f, 19.8f); lineTo(10.3f, 19.8f); close()
        }
    }

    val Globe: ImageVector by lazy {
        icon("globe") {
            // outer ring (two arcs approximated by octagon shells)
            moveTo(12f, 2.4f); lineTo(17f, 4.4f); lineTo(20.6f, 8.4f); lineTo(21.6f, 12f)
            lineTo(20.6f, 15.6f); lineTo(17f, 19.6f); lineTo(12f, 21.6f); lineTo(7f, 19.6f)
            lineTo(3.4f, 15.6f); lineTo(2.4f, 12f); lineTo(3.4f, 8.4f); lineTo(7f, 4.4f); close()
            moveTo(12f, 4.4f); lineTo(8.2f, 5.9f); lineTo(5.4f, 9f); lineTo(4.6f, 12f)
            lineTo(5.4f, 15f); lineTo(8.2f, 18.1f); lineTo(12f, 19.6f); lineTo(15.8f, 18.1f)
            lineTo(18.6f, 15f); lineTo(19.4f, 12f); lineTo(18.6f, 9f); lineTo(15.8f, 5.9f); close()
            // equator + meridian bars
            moveTo(3.2f, 11.1f); lineTo(20.8f, 11.1f); lineTo(20.8f, 12.9f); lineTo(3.2f, 12.9f); close()
            moveTo(11.1f, 3.2f); lineTo(12.9f, 3.2f); lineTo(12.9f, 20.8f); lineTo(11.1f, 20.8f); close()
        }
    }

    val Heart: ImageVector by lazy {
        icon("heart") {
            moveTo(12f, 20.6f); lineTo(4.2f, 12.8f); lineTo(3.2f, 9.4f); lineTo(4.6f, 6.2f)
            lineTo(7.8f, 4.8f); lineTo(10.6f, 6.2f); lineTo(12f, 8f); lineTo(13.4f, 6.2f)
            lineTo(16.2f, 4.8f); lineTo(19.4f, 6.2f); lineTo(20.8f, 9.4f); lineTo(19.8f, 12.8f); close()
            moveTo(12f, 17.8f); lineTo(17.6f, 12.2f); lineTo(18.4f, 9.8f); lineTo(17.4f, 7.8f)
            lineTo(15.4f, 7.2f); lineTo(13.4f, 8.6f); lineTo(12f, 11f); lineTo(10.6f, 8.6f)
            lineTo(8.6f, 7.2f); lineTo(6.6f, 7.8f); lineTo(5.6f, 9.8f); lineTo(6.4f, 12.2f); close()
        }
    }

    val Pulse: ImageVector by lazy {
        icon("pulse") {
            moveTo(2.4f, 11.1f); lineTo(6.2f, 11.1f); lineTo(8.4f, 5.4f); lineTo(10.2f, 5.4f)
            lineTo(12.6f, 17.6f); lineTo(14.6f, 9.4f); lineTo(16.4f, 13.4f); lineTo(21.6f, 13.4f)
            lineTo(21.6f, 15.4f); lineTo(15.2f, 15.4f); lineTo(14.4f, 13.6f); lineTo(12.2f, 22.4f)
            lineTo(9.2f, 22.4f); lineTo(8.9f, 9.6f); lineTo(7.6f, 13.1f); lineTo(2.4f, 13.1f); close()
        }
    }

    val Terminal: ImageVector by lazy {
        icon("terminal") {
            moveTo(2.4f, 3.6f); lineTo(21.6f, 3.6f); lineTo(21.6f, 5.4f); lineTo(2.4f, 5.4f); close()
            moveTo(2.4f, 5.4f); lineTo(4.2f, 5.4f); lineTo(4.2f, 19.4f); lineTo(2.4f, 19.4f); close()
            moveTo(19.8f, 5.4f); lineTo(21.6f, 5.4f); lineTo(21.6f, 19.4f); lineTo(19.8f, 19.4f); close()
            moveTo(2.4f, 19.4f); lineTo(21.6f, 19.4f); lineTo(21.6f, 21.2f); lineTo(2.4f, 21.2f); close()
            // prompt chevron
            moveTo(6.4f, 8.6f); lineTo(7.8f, 7.4f); lineTo(11.6f, 11.6f); lineTo(7.8f, 15.8f)
            lineTo(6.4f, 14.6f); lineTo(9f, 11.6f); close()
            // underscore
            moveTo(12.4f, 14.6f); lineTo(17.6f, 14.6f); lineTo(17.6f, 16.2f); lineTo(12.4f, 16.2f); close()
        }
    }

    val Root: ImageVector by lazy {
        icon("root") {
            // shield outline
            moveTo(12f, 2.2f); lineTo(20.4f, 5.2f); lineTo(20.4f, 12.4f); lineTo(16.6f, 18.6f)
            lineTo(12f, 21.8f); lineTo(7.4f, 18.6f); lineTo(3.6f, 12.4f); lineTo(3.6f, 5.2f); close()
            moveTo(12f, 4.4f); lineTo(6f, 6.6f); lineTo(6f, 12f); lineTo(8.9f, 17f); lineTo(12f, 19.4f)
            lineTo(15.1f, 17f); lineTo(18f, 12f); lineTo(18f, 6.6f); close()
            // check inside
            moveTo(8.6f, 11.4f); lineTo(10.2f, 10f); lineTo(11.8f, 12f); lineTo(14.6f, 8.4f)
            lineTo(16.2f, 9.8f); lineTo(12.2f, 15f); close()
        }
    }

    val Task: ImageVector by lazy {
        icon("task") {
            // three tick boxes + lines
            moveTo(3f, 4f); lineTo(4.4f, 2.8f); lineTo(7.4f, 6.4f); lineTo(5.8f, 7.6f); close()
            moveTo(9.4f, 4.4f); lineTo(21f, 4.4f); lineTo(21f, 6.2f); lineTo(9.4f, 6.2f); close()
            moveTo(3f, 11f); lineTo(4.4f, 9.8f); lineTo(7.4f, 13.4f); lineTo(5.8f, 14.6f); close()
            moveTo(9.4f, 11.4f); lineTo(21f, 11.4f); lineTo(21f, 13.2f); lineTo(9.4f, 13.2f); close()
            moveTo(3.6f, 16.6f); lineTo(7.4f, 16.6f); lineTo(7.4f, 20.4f); lineTo(3.6f, 20.4f); close()
            moveTo(9.4f, 18.4f); lineTo(18f, 18.4f); lineTo(18f, 20.2f); lineTo(9.4f, 20.2f); close()
        }
    }

    val Timer: ImageVector by lazy {
        icon("timer") {
            moveTo(12f, 3.4f); lineTo(16.6f, 4.6f); lineTo(19.8f, 8.4f); lineTo(20.6f, 13f)
            lineTo(19f, 17.4f); lineTo(15.4f, 20.2f); lineTo(12f, 20.8f); lineTo(8.6f, 20.2f)
            lineTo(5f, 17.4f); lineTo(3.4f, 13f); lineTo(4.2f, 8.4f); lineTo(7.4f, 4.6f); close()
            moveTo(12f, 5.4f); lineTo(8.6f, 6.3f); lineTo(6.2f, 9.1f); lineTo(5.6f, 12.6f)
            lineTo(6.8f, 16f); lineTo(9.5f, 18.2f); lineTo(12f, 18.8f); lineTo(14.5f, 18.2f)
            lineTo(17.2f, 16f); lineTo(18.4f, 12.6f); lineTo(17.8f, 9.1f); lineTo(15.4f, 6.3f); close()
            // hands
            moveTo(11.2f, 8.4f); lineTo(12.8f, 8.4f); lineTo(12.8f, 12.4f); lineTo(11.2f, 12.4f); close()
            moveTo(12.4f, 11.6f); lineTo(15.6f, 13.4f); lineTo(14.8f, 14.8f); lineTo(11.6f, 13f); close()
            moveTo(9.4f, 1.6f); lineTo(14.6f, 1.6f); lineTo(14.6f, 3.2f); lineTo(9.4f, 3.2f); close()
        }
    }

    val Dice: ImageVector by lazy {
        icon("dice") {
            moveTo(3.6f, 3.6f); lineTo(20.4f, 3.6f); lineTo(20.4f, 20.4f); lineTo(3.6f, 20.4f); close()
            moveTo(5.6f, 5.6f); lineTo(5.6f, 18.4f); lineTo(18.4f, 18.4f); lineTo(18.4f, 5.6f); close()
            // pips
            moveTo(7.6f, 7.6f); lineTo(9.8f, 7.6f); lineTo(9.8f, 9.8f); lineTo(7.6f, 9.8f); close()
            moveTo(14.2f, 7.6f); lineTo(16.4f, 7.6f); lineTo(16.4f, 9.8f); lineTo(14.2f, 9.8f); close()
            moveTo(10.9f, 10.9f); lineTo(13.1f, 10.9f); lineTo(13.1f, 13.1f); lineTo(10.9f, 13.1f); close()
            moveTo(7.6f, 14.2f); lineTo(9.8f, 14.2f); lineTo(9.8f, 16.4f); lineTo(7.6f, 16.4f); close()
            moveTo(14.2f, 14.2f); lineTo(16.4f, 14.2f); lineTo(16.4f, 16.4f); lineTo(14.2f, 16.4f); close()
        }
    }

    val Chess: ImageVector by lazy {
        icon("chess") {
            // knight-ish pawn silhouette built from blocks
            moveTo(9f, 3f); lineTo(15f, 3f); lineTo(15f, 5f); lineTo(9f, 5f); close()
            moveTo(8f, 5f); lineTo(16f, 5f); lineTo(14.6f, 8f); lineTo(9.4f, 8f); close()
            moveTo(9.6f, 8f); lineTo(14.4f, 8f); lineTo(15.6f, 15f); lineTo(8.4f, 15f); close()
            moveTo(6.6f, 15f); lineTo(17.4f, 15f); lineTo(17.4f, 17.4f); lineTo(6.6f, 17.4f); close()
            moveTo(5.2f, 17.4f); lineTo(18.8f, 17.4f); lineTo(18.8f, 21f); lineTo(5.2f, 21f); close()
        }
    }

    val Board: ImageVector by lazy {
        icon("board") {
            // 4x4 checkerboard
            moveTo(3f, 3f); lineTo(7.5f, 3f); lineTo(7.5f, 7.5f); lineTo(3f, 7.5f); close()
            moveTo(12f, 3f); lineTo(16.5f, 3f); lineTo(16.5f, 7.5f); lineTo(12f, 7.5f); close()
            moveTo(7.5f, 7.5f); lineTo(12f, 7.5f); lineTo(12f, 12f); lineTo(7.5f, 12f); close()
            moveTo(16.5f, 7.5f); lineTo(21f, 7.5f); lineTo(21f, 12f); lineTo(16.5f, 12f); close()
            moveTo(3f, 12f); lineTo(7.5f, 12f); lineTo(7.5f, 16.5f); lineTo(3f, 16.5f); close()
            moveTo(12f, 12f); lineTo(16.5f, 12f); lineTo(16.5f, 16.5f); lineTo(12f, 16.5f); close()
            moveTo(7.5f, 16.5f); lineTo(12f, 16.5f); lineTo(12f, 21f); lineTo(7.5f, 21f); close()
            moveTo(16.5f, 16.5f); lineTo(21f, 16.5f); lineTo(21f, 21f); lineTo(16.5f, 21f); close()
        }
    }

    val Wand: ImageVector by lazy {
        icon("wand") {
            moveTo(3.2f, 19.4f); lineTo(14.6f, 8f); lineTo(16f, 9.4f); lineTo(4.6f, 20.8f); close()
            moveTo(15.6f, 4.6f); lineTo(17f, 3.2f); lineTo(19.4f, 5.6f); lineTo(18f, 7f); close()
            moveTo(18.4f, 8.4f); lineTo(21.4f, 8.4f); lineTo(21.4f, 10.2f); lineTo(18.4f, 10.2f); close()
            moveTo(13.6f, 2.6f); lineTo(15.4f, 2.6f); lineTo(15.4f, 5.6f); lineTo(13.6f, 5.6f); close()
        }
    }

    val Chat: ImageVector by lazy {
        icon("chat") {
            moveTo(2.6f, 3.4f); lineTo(21.4f, 3.4f); lineTo(21.4f, 16.2f); lineTo(11.4f, 16.2f)
            lineTo(6.6f, 20.8f); lineTo(6.6f, 16.2f); lineTo(2.6f, 16.2f); close()
            moveTo(4.6f, 5.4f); lineTo(4.6f, 14.2f); lineTo(8.6f, 14.2f); lineTo(8.6f, 16.6f)
            lineTo(10.8f, 14.2f); lineTo(19.4f, 14.2f); lineTo(19.4f, 5.4f); close()
        }
    }

    val Warning: ImageVector by lazy {
        icon("warning") {
            moveTo(12f, 2.6f); lineTo(22.4f, 20.6f); lineTo(1.6f, 20.6f); close()
            moveTo(12f, 6.8f); lineTo(5f, 18.6f); lineTo(19f, 18.6f); close()
            moveTo(11.1f, 9.4f); lineTo(12.9f, 9.4f); lineTo(12.9f, 14f); lineTo(11.1f, 14f); close()
            moveTo(11.1f, 15.2f); lineTo(12.9f, 15.2f); lineTo(12.9f, 17f); lineTo(11.1f, 17f); close()
        }
    }

    val Close: ImageVector by lazy {
        icon("close") {
            moveTo(4.4f, 3f); lineTo(12f, 10.6f); lineTo(19.6f, 3f); lineTo(21f, 4.4f)
            lineTo(13.4f, 12f); lineTo(21f, 19.6f); lineTo(19.6f, 21f); lineTo(12f, 13.4f)
            lineTo(4.4f, 21f); lineTo(3f, 19.6f); lineTo(10.6f, 12f); lineTo(3f, 4.4f); close()
        }
    }

    val ArrowRight: ImageVector by lazy {
        icon("arrow_right") {
            moveTo(3.4f, 11.1f); lineTo(15.4f, 11.1f); lineTo(15.4f, 6.6f); lineTo(22f, 12f)
            lineTo(15.4f, 17.4f); lineTo(15.4f, 12.9f); lineTo(3.4f, 12.9f); close()
        }
    }

    val Play: ImageVector by lazy {
        icon("play") {
            moveTo(5.4f, 3f); lineTo(20.4f, 12f); lineTo(5.4f, 21f); close()
        }
    }

    val Pause: ImageVector by lazy {
        icon("pause") {
            moveTo(5.6f, 3.6f); lineTo(10.2f, 3.6f); lineTo(10.2f, 20.4f); lineTo(5.6f, 20.4f); close()
            moveTo(13.8f, 3.6f); lineTo(18.4f, 3.6f); lineTo(18.4f, 20.4f); lineTo(13.8f, 20.4f); close()
        }
    }

    val Trash: ImageVector by lazy {
        icon("trash") {
            moveTo(9f, 2.4f); lineTo(15f, 2.4f); lineTo(15f, 4.4f); lineTo(19.8f, 4.4f)
            lineTo(19.8f, 6.4f); lineTo(4.2f, 6.4f); lineTo(4.2f, 4.4f); lineTo(9f, 4.4f); close()
            moveTo(5.6f, 7.6f); lineTo(18.4f, 7.6f); lineTo(17.2f, 21.4f); lineTo(6.8f, 21.4f); close()
            moveTo(7.6f, 9.6f); lineTo(8.6f, 19.4f); lineTo(10.2f, 19.4f); lineTo(9.4f, 9.6f); close()
            moveTo(11.5f, 9.6f); lineTo(12.5f, 9.6f); lineTo(12.5f, 19.4f); lineTo(11.5f, 19.4f); close()
            moveTo(14.6f, 9.6f); lineTo(15.4f, 19.4f); lineTo(17f, 19.4f); lineTo(16.4f, 9.6f); close()
        }
    }

    val Share: ImageVector by lazy {
        icon("share") {
            moveTo(16.4f, 2.4f); lineTo(21.6f, 6f); lineTo(16.4f, 9.6f); lineTo(16.4f, 7.4f)
            lineTo(9.6f, 11.6f); lineTo(9.6f, 8.6f); lineTo(16.4f, 5.4f); close()
            moveTo(16.4f, 14.4f); lineTo(21.6f, 18f); lineTo(16.4f, 21.6f); lineTo(16.4f, 18.6f)
            lineTo(9.6f, 15.4f); lineTo(9.6f, 12.4f); lineTo(16.4f, 16.6f); close()
            moveTo(3.2f, 9.4f); lineTo(8.4f, 9.4f); lineTo(8.4f, 14.6f); lineTo(3.2f, 14.6f); close()
        }
    }

    val Search: ImageVector by lazy {
        icon("search") {
            moveTo(10.6f, 2.8f); lineTo(15.2f, 4f); lineTo(18.4f, 7.6f); lineTo(19.2f, 12f)
            lineTo(17.4f, 16f); lineTo(13.8f, 18.4f); lineTo(9.6f, 18.2f); lineTo(6.2f, 15.6f)
            lineTo(4.4f, 11.6f); lineTo(5.2f, 7.2f); lineTo(8f, 4.2f); close()
            moveTo(10.6f, 5f); lineTo(7.8f, 6.2f); lineTo(6.4f, 8.8f); lineTo(6.6f, 11.8f)
            lineTo(8.2f, 14.4f); lineTo(11f, 15.8f); lineTo(14f, 15.4f); lineTo(16.2f, 13.2f)
            lineTo(16.8f, 10.2f); lineTo(15.6f, 7.4f); lineTo(13.2f, 5.6f); close()
            moveTo(16.4f, 16.4f); lineTo(21.8f, 21.8f); lineTo(20.2f, 23.2f); lineTo(14.8f, 17.8f); close()
        }
    }

    val Doc: ImageVector by lazy {
        icon("doc") {
            moveTo(4.4f, 2.4f); lineTo(14.6f, 2.4f); lineTo(19.6f, 7.4f); lineTo(19.6f, 21.6f)
            lineTo(4.4f, 21.6f); close()
            moveTo(6.4f, 4.4f); lineTo(6.4f, 19.6f); lineTo(17.6f, 19.6f); lineTo(17.6f, 8.4f)
            lineTo(13.6f, 8.4f); lineTo(13.6f, 4.4f); close()
            moveTo(8.6f, 11f); lineTo(15.4f, 11f); lineTo(15.4f, 12.6f); lineTo(8.6f, 12.6f); close()
            moveTo(8.6f, 14.4f); lineTo(15.4f, 14.4f); lineTo(15.4f, 16f); lineTo(8.6f, 16f); close()
        }
    }

    val FolderOpen: ImageVector by lazy {
        icon("folder_open") {
            moveTo(2.4f, 4.4f); lineTo(9.4f, 4.4f); lineTo(11.4f, 7f); lineTo(20.4f, 7f)
            lineTo(20.4f, 9f); lineTo(2.4f, 9f); close()
            moveTo(2.4f, 10f); lineTo(21.6f, 10f); lineTo(18.6f, 20f); lineTo(5.4f, 20f); close()
            moveTo(4.8f, 12f); lineTo(6.8f, 18f); lineTo(17.2f, 18f); lineTo(19.2f, 12f); close()
        }
    }

    val Refresh: ImageVector by lazy {
        icon("refresh") {
            moveTo(12f, 3.4f); lineTo(17.4f, 5f); lineTo(20.6f, 9.4f); lineTo(18.2f, 10.2f)
            lineTo(15.6f, 6.8f); lineTo(12f, 5.6f); lineTo(8.2f, 7f); lineTo(5.8f, 10f)
            lineTo(9.4f, 10f); lineTo(9.4f, 12.2f); lineTo(3.4f, 12.2f); lineTo(3.4f, 6.2f)
            lineTo(5.6f, 6.2f); lineTo(6.6f, 5.4f); lineTo(9.2f, 3.8f); close()
            moveTo(14.6f, 11.8f); lineTo(20.6f, 11.8f); lineTo(20.6f, 17.8f); lineTo(18.4f, 17.8f)
            lineTo(17.4f, 18.6f); lineTo(14.8f, 20.2f); lineTo(12f, 20.6f); lineTo(6.6f, 19f)
            lineTo(3.4f, 14.6f); lineTo(5.8f, 13.8f); lineTo(8.4f, 17.2f); lineTo(12f, 18.4f)
            lineTo(15.8f, 17f); lineTo(18.2f, 14f); lineTo(14.6f, 14f); close()
        }
    }

    val Link: ImageVector by lazy {
        icon("link") {
            moveTo(9.4f, 3.4f); lineTo(15.4f, 3.4f); lineTo(20.6f, 8.6f); lineTo(20.6f, 14.6f)
            lineTo(18.4f, 16.8f); lineTo(16.8f, 15.2f); lineTo(18.4f, 13.6f); lineTo(18.4f, 9.6f)
            lineTo(14.4f, 5.6f); lineTo(10.4f, 5.6f); lineTo(8.8f, 7.2f); lineTo(7.2f, 5.6f); close()
            moveTo(5.6f, 7.2f); lineTo(7.2f, 8.8f); lineTo(5.6f, 10.4f); lineTo(5.6f, 14.4f)
            lineTo(9.6f, 18.4f); lineTo(13.6f, 18.4f); lineTo(15.2f, 16.8f); lineTo(16.8f, 18.4f)
            lineTo(14.6f, 20.6f); lineTo(8.6f, 20.6f); lineTo(3.4f, 15.4f); lineTo(3.4f, 9.4f); close()
        }
    }

    val Save: ImageVector by lazy {
        icon("save") {
            moveTo(3f, 3f); lineTo(17.4f, 3f); lineTo(21f, 6.6f); lineTo(21f, 21f); lineTo(3f, 21f); close()
            moveTo(5f, 5f); lineTo(5f, 19f); lineTo(19f, 19f); lineTo(19f, 7.4f); lineTo(16.6f, 5f)
            lineTo(14.6f, 5f); lineTo(14.6f, 9.6f); lineTo(7.4f, 9.6f); lineTo(7.4f, 5f); close()
            moveTo(9f, 12f); lineTo(17f, 12f); lineTo(17f, 17f); lineTo(9f, 17f); close()
        }
    }

    val Sparkle: ImageVector by lazy {
        icon("sparkle") {
            moveTo(12f, 2.4f); lineTo(13.8f, 8.6f); lineTo(20f, 10.4f); lineTo(13.8f, 12.2f)
            lineTo(12f, 18.4f); lineTo(10.2f, 12.2f); lineTo(4f, 10.4f); lineTo(10.2f, 8.6f); close()
            moveTo(18.4f, 15.4f); lineTo(19.2f, 18f); lineTo(21.8f, 18.8f); lineTo(19.2f, 19.6f)
            lineTo(18.4f, 22.2f); lineTo(17.6f, 19.6f); lineTo(15f, 18.8f); lineTo(17.6f, 18f); close()
            moveTo(4.6f, 14.4f); lineTo(5.2f, 16.4f); lineTo(7.2f, 17f); lineTo(5.2f, 17.6f)
            lineTo(4.6f, 19.6f); lineTo(4f, 17.6f); lineTo(2f, 17f); lineTo(4f, 16.4f); close()
        }
    }

    val Grid: ImageVector by lazy {
        icon("grid") {
            moveTo(3f, 3f); lineTo(10.6f, 3f); lineTo(10.6f, 10.6f); lineTo(3f, 10.6f); close()
            moveTo(13.4f, 3f); lineTo(21f, 3f); lineTo(21f, 10.6f); lineTo(13.4f, 10.6f); close()
            moveTo(3f, 13.4f); lineTo(10.6f, 13.4f); lineTo(10.6f, 21f); lineTo(3f, 21f); close()
            moveTo(13.4f, 13.4f); lineTo(21f, 13.4f); lineTo(21f, 21f); lineTo(13.4f, 21f); close()
        }
    }

    val Robot: ImageVector by lazy {
        icon("robot") {
            moveTo(11.2f, 2f); lineTo(12.8f, 2f); lineTo(12.8f, 4.4f); lineTo(11.2f, 4.4f); close()
            moveTo(4f, 4.4f); lineTo(20f, 4.4f); lineTo(20f, 16f); lineTo(4f, 16f); close()
            moveTo(6f, 6.4f); lineTo(6f, 14f); lineTo(18f, 14f); lineTo(18f, 6.4f); close()
            moveTo(7.6f, 8.2f); lineTo(10.4f, 8.2f); lineTo(10.4f, 11f); lineTo(7.6f, 11f); close()
            moveTo(13.6f, 8.2f); lineTo(16.4f, 8.2f); lineTo(16.4f, 11f); lineTo(13.6f, 11f); close()
            moveTo(2f, 7.4f); lineTo(4f, 7.4f); lineTo(4f, 13f); lineTo(2f, 13f); close()
            moveTo(20f, 7.4f); lineTo(22f, 7.4f); lineTo(22f, 13f); lineTo(20f, 13f); close()
            moveTo(7f, 17.4f); lineTo(17f, 17.4f); lineTo(17f, 21.4f); lineTo(7f, 21.4f); close()
        }
    }

}
