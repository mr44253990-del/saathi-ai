package com.saathi.ai.hotword

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.saathi.ai.MainActivity
import com.saathi.ai.R
import com.saathi.ai.live.Phase
import com.saathi.ai.live.SessionHub

/**
 * Quick Settings tile — the reliable, one-tap "Hey Jimi" on Android 12+.
 *
 * Pull down the shade, tap Jimi, and it opens the assistant **and** starts the
 * session. Unlike an activity launch from a background service (which the OS
 * blocks), a user-initiated tile tap is always allowed.
 */
@RequiresApi(Build.VERSION_CODES.N)
class JimiTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.let { tile ->
            tile.state = if (SessionHub.state.value.phase == Phase.LISTENING) {
                Tile.STATE_ACTIVE
            } else {
                Tile.STATE_INACTIVE
            }
            tile.label = "Jimi"
            runCatching {
                tile.icon = Icon.createWithResource(this, R.drawable.ic_notification)
            }
            tile.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        SessionHub.update { it.copy(phase = Phase.CONNECTING) }

        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(HotwordService.EXTRA_WAKE, true)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(
                this, 21, open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(open)
        }
    }
}
