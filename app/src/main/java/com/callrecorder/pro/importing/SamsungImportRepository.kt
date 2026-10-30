package com.callrecorder.pro.importing

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.callrecorder.pro.data.db.RecordingDao
import com.callrecorder.pro.data.db.RecordingEntity
import com.callrecorder.pro.util.hashed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Imports recordings from a user-granted SAF folder (typically Samsung's
 * internal "Call" recordings dir). Deduplicates via SHA-256 of contents.
 */
class SamsungImportRepository(
    private val context: Context,
    private val dao: RecordingDao
) {
    suspend fun scanOnce(treeUri: Uri? = null): Int = withContext(Dispatchers.IO) {
        val tree = treeUri ?: return@withContext 0
        val root = DocumentFile.fromTreeUri(context, tree) ?: return@withContext 0
        val known = dao.allHashes().toHashSet()
        var imported = 0

        val audioExts = setOf("m4a", "aac", "amr", "wav", "3gp", "mp3", "ogg")
        root.listFiles().filter { it.isFile && it.name?.substringAfterLast('.', "")?.lowercase() in audioExts }
            .forEach { doc ->
                runCatching {
                    val tmp = File(context.cacheDir, "import_${System.currentTimeMillis()}")
                    context.contentResolver.openInputStream(doc.uri)?.use { input ->
                        FileOutputStream(tmp).use { input.copyTo(it) }
                    } ?: return@forEach
                    val hash = tmp.hashed()
                    if (hash in known) { tmp.delete(); return@forEach }

                    val destDir = File(context.filesDir, "recordings").apply { mkdirs() }
                    val dest = File(destDir, "samsung_${doc.name ?: hash.take(8)}")
                    tmp.renameTo(dest)

                    val name = doc.name ?: ""
                    val meta = parseSamsungName(name)
                    dao.insert(
                        RecordingEntity(
                            number = meta.first,
                            contactName = null,
                            direction = meta.second,
                            startTime = doc.lastModified(),
                            durationMs = 0,
                            filePath = dest.absolutePath,
                            source = "SAMSUNG_IMPORT",
                            format = name.substringAfterLast('.', "m4a").lowercase(),
                            fileSize = dest.length(),
                            sha256 = hash,
                            importedFromSamsung = true
                        )
                    )
                    imported++
                }
            }
        imported
    }

    /** Samsung names look like "YYYYMMDD_HHMMSS_Number [direction].m4a". */
    private fun parseSamsungName(name: String): Pair<String, String> {
        var number = ""
        var direction = "unknown"
        runCatching {
            val base = name.substringBeforeLast('.')
            val bracket = base.substringAfterLast('[', "").substringBefore(']', "")
            direction = when (bracket.lowercase()) {
                "incoming" -> "incoming"
                "outgoing" -> "outgoing"
                else -> "unknown"
            }
            val before = base.substringBeforeLast('[').trim()
            number = before.split('_').lastOrNull { it.any { ch -> ch.isDigit() } } ?: ""
        }
        return number to direction
    }
}
