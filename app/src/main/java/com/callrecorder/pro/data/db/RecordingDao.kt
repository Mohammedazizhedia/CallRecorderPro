package com.callrecorder.pro.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {

    @Query(
        """
        SELECT * FROM recordings
        WHERE (:query IS NULL OR number LIKE '%' || :query || '%'
               OR contactName LIKE '%' || :query || '%'
               OR notes LIKE '%' || :query || '%'
               OR transcript LIKE '%' || :query || '%'
               OR tags LIKE '%' || :query || '%')
          AND (:direction IS NULL OR direction = :direction)
          AND (:favoritesOnly = 0 OR favorite = 1)
        ORDER BY startTime DESC
        """
    )
    fun observeRecordings(
        query: String?,
        direction: String?,
        favoritesOnly: Boolean
    ): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE id = :id")
    fun observeById(id: Long): Flow<RecordingEntity?>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getById(id: Long): RecordingEntity?

    @Query("SELECT sha256 FROM recordings")
    suspend fun allHashes(): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(recording: RecordingEntity): Long

    @Update
    suspend fun update(recording: RecordingEntity)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COALESCE(SUM(fileSize),0) FROM recordings")
    fun observeTotalSize(): Flow<Long>

    @Query("SELECT * FROM recordings WHERE favorite = 0 AND startTime < :cutoff")
    suspend fun olderThan(cutoff: Long): List<RecordingEntity>

    @Query("SELECT COALESCE(SUM(fileSize),0) FROM recordings")
    suspend fun totalSizeBytes(): Long
}

@Dao
interface BookmarkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(bookmark: BookmarkEntity)

    @Query("SELECT * FROM bookmarks WHERE recordingId = :recordingId ORDER BY positionMs")
    fun observeFor(recordingId: Long): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE recordingId = :recordingId ORDER BY positionMs")
    suspend fun listFor(recordingId: Long): List<BookmarkEntity>

    @Query("DELETE FROM bookmarks WHERE recordingId = :recordingId AND positionMs = :positionMs")
    suspend fun delete(recordingId: Long, positionMs: Long)

    @Query("DELETE FROM bookmarks WHERE recordingId = :recordingId")
    suspend fun deleteAllFor(recordingId: Long)
}
