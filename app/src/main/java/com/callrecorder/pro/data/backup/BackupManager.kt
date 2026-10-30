package com.callrecorder.pro.data.backup

import android.content.Context
import android.net.Uri
import com.callrecorder.pro.data.db.BookmarkDao
import com.callrecorder.pro.data.db.BookmarkEntity
import com.callrecorder.pro.data.db.RecordingDao
import com.callrecorder.pro.data.db.RecordingEntity
import com.callrecorder.pro.util.hashed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * ZIP export/import: recordings + a JSON metadata catalog.
 * Cloud backup is user-driven via the system share sheet (ACTION_SEND) so
 * no Drive SDK is bundled — keeps the APK small (documented in README).
 */
class BackupManager(
    private val context: Context,
    private val dao: RecordingDao,
    private val bookmarkDao: BookmarkDao? = null
) {
    suspend fun exportTo(zipUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val recs = dao.observeRecordings(null, null, false).first()
            val marks = bookmarkDao?.let { b ->
                recs.flatMap { b.listFor(it.id) }
            }.orEmpty()

            context.contentResolver.openOutputStream(zipUri, "wt")?.use { raw ->
                ZipOutputStream(raw.buffered()).use { zip ->
                    zip.putNextEntry(ZipEntry("catalog.json"))
                    zip.write(toJson(recs, marks).toByteArray())
                    zip.closeEntry()
                    for (rec in recs) {
                        val f = File(rec.filePath)
                        if (!f.exists()) continue
                        zip.putNextEntry(ZipEntry("audio/${f.name}"))
                        f.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            } ?: error("cannot open output stream")
        }
    }

    suspend fun importFrom(zipUri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val destDir = File(context.filesDir, "recordings").apply { mkdirs() }
            val known = dao.allHashes().toHashSet()
            var imported = 0

            context.contentResolver.openInputStream(zipUri)?.use { raw ->
                ZipInputStream(raw.buffered()).use { zip ->
                    var catalog: JSONObject? = null
                    var entry = zip.nextEntry
                    while (entry != null) {
                        when {
                            entry.name == "catalog.json" ->
                                catalog = JSONObject(zip.readBytes().decodeToString())
                            entry.name.startsWith("audio/") -> {
                                val name = entry.name.substringAfter('/')
                                File(destDir, name).outputStream().use { zip.copyTo(it) }
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }

                    val arr = catalog?.getJSONArray("recordings") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val rec = fromJson(o)
                        val f = File(destDir, File(rec.filePath).name)
                        if (!f.exists()) continue
                        val hash = f.hashed()
                        if (hash in known) { f.delete(); continue }
                        dao.insert(rec.copy(filePath = f.absolutePath, sha256 = hash))
                        imported++
                    }
                }
            } ?: error("cannot open input stream")
            imported
        }
    }

    private fun toJson(recs: List<RecordingEntity>, marks: List<BookmarkEntity>): String {
        val root = JSONObject()
        root.put("recordings", JSONArray().apply {
            recs.forEach { put(recordingToJson(it)) }
        })
        root.put("bookmarks", JSONArray().apply {
            marks.forEach { m ->
                put(JSONObject().apply {
                    put("recordingId", m.recordingId)
                    put("positionMs", m.positionMs)
                    put("note", m.note)
                })
            }
        })
        return root.toString()
    }

    private fun recordingToJson(r: RecordingEntity) = JSONObject().apply {
        put("number", r.number)
        put("contactName", r.contactName ?: JSONObject.NULL)
        put("direction", r.direction)
        put("startTime", r.startTime)
        put("durationMs", r.durationMs)
        put("fileName", File(r.filePath).name)
        put("source", r.source)
        put("format", r.format)
        put("fileSize", r.fileSize)
        put("favorite", r.favorite)
        put("notes", r.notes)
        put("transcript", r.transcript)
        put("tags", r.tags)
    }

    private fun fromJson(o: JSONObject): RecordingEntity = RecordingEntity(
        number = o.optString("number"),
        contactName = if (o.isNull("contactName")) null else o.optString("contactName"),
        direction = o.optString("direction", "unknown"),
        startTime = o.optLong("startTime"),
        durationMs = o.optLong("durationMs"),
        filePath = o.optString("fileName"),
        source = o.optString("source"),
        format = o.optString("format"),
        fileSize = o.optLong("fileSize"),
        sha256 = "",
        favorite = o.optBoolean("favorite"),
        notes = o.optString("notes"),
        transcript = o.optString("transcript"),
        tags = o.optString("tags")
    )
}
