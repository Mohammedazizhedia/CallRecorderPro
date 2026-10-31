package com.callrecorder.pro.record

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** One-tap manual recording from Quick Settings (competitor feature). */
class QuickRecordTileService : TileService() {

    override fun onStartListening() {
        updateTile()
    }

    override fun onClick() {
        runCatching {
            if (RecordingService.isRecordingNow) {
                RecordingService.requestStop(this)
            } else {
                RecordingService.startRecording(this, null, manual = true)
            }
        }
        updateTile()
    }

    private fun updateTile() {
        qsTile?.let { t ->
            t.state = if (RecordingService.isRecordingNow) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            t.updateTile()
        }
    }
}
