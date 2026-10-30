package com.callrecorder.pro.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "recordings", indices = [Index("startTime"), Index("number")])
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    val contactName: String?,
    val direction: String,              // "incoming" | "outgoing" | "unknown"
    val startTime: Long,                // epoch millis
    val durationMs: Long,
    val filePath: String,
    val source: String,                 // audio source key, e.g. VOICE_RECOGNITION
    val format: String,                 // "aac" | "wav"
    val fileSize: Long,
    val sha256: String,
    val favorite: Boolean = false,
    val notes: String = "",
    val transcript: String = "",
    val tags: String = "",              // comma separated
    val importedFromSamsung: Boolean = false
)

@Entity(tableName = "bookmarks", primaryKeys = ["recordingId", "positionMs"])
data class BookmarkEntity(
    val recordingId: Long,
    val positionMs: Long,
    val note: String = ""
)
