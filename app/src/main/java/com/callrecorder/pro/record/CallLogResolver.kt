package com.callrecorder.pro.record

import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat

/** Resolves direction + contact name for a number using the call log. */
object CallLogResolver {

    data class Info(val direction: String, val contactName: String?)

    fun resolve(context: Context, number: String, aroundTimeMs: Long): Info {
        var direction = "unknown"
        var name: String? = null
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED
        ) return Info(direction, null)

        runCatching {
            val projection = arrayOf(
                CallLog.Calls.TYPE,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.DATE
            )
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI, projection, null, null,
                CallLog.Calls.DATE + " DESC LIMIT 20"
            )?.use { c ->
                val iType = c.getColumnIndex(CallLog.Calls.TYPE)
                val iName = c.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val iDate = c.getColumnIndex(CallLog.Calls.DATE)
                while (c.moveToNext()) {
                    val date = c.getLong(iDate)
                    if (kotlin.math.abs(date - aroundTimeMs) > 60_000) continue
                    direction = when (c.getInt(iType)) {
                        CallLog.Calls.INCOMING_TYPE -> "incoming"
                        CallLog.Calls.OUTGOING_TYPE -> "outgoing"
                        CallLog.Calls.MISSED_TYPE -> "incoming"
                        else -> "unknown"
                    }
                    name = c.getString(iName)
                    break
                }
            }
        }
        return Info(direction, name)
    }

    fun contactName(context: Context, number: String): String? {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) return null
        var name: String? = null
        runCatching {
            val uri = android.net.Uri.withAppendedPath(
                android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(number)
            )
            context.contentResolver.query(
                uri,
                arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) name = c.getString(0)
            }
        }
        return name
    }
}
