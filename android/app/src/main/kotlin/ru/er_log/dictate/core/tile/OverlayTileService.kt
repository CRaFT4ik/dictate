package ru.er_log.dictate.core.tile

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import ru.er_log.dictate.core.overlay.FloatingButtonService

/**
 * Quick Settings tile that toggles [FloatingButtonService] on or off.
 *
 * Tile state is kept in sync with the service's live [FloatingButtonService.isRunning] flag so
 * that adding / removing the tile after the service is already running reflects the correct state.
 */
public class OverlayTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val tile = qsTile ?: return
        if (tile.state == Tile.STATE_INACTIVE) {
            ContextCompat.startForegroundService(
                this,
                Intent(this, FloatingButtonService::class.java)
            )
            tile.state = Tile.STATE_ACTIVE
        } else {
            stopService(Intent(this, FloatingButtonService::class.java))
            tile.state = Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }

    // ---- helpers ----

    private fun updateTileState() {
        val tile = qsTile ?: return
        tile.state = if (FloatingButtonService.isRunning.get()) {
            Tile.STATE_ACTIVE
        } else {
            Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }
}
