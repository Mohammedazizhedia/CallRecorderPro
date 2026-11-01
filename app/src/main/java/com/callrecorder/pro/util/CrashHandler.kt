
package com.callrecorder.pro.util

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

/** BFB §10 — global crash trap: writes crash.log + DebugLog, then defers to system. */
class CrashHandler(context: Context) : Thread.UncaughtExceptionHandler {

    private val appContext = context.applicationContext
    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(t: Thread, e: Throwable) {
        runCatching {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            val msg = "${t.name}: ${e.javaClass.simpleName}: ${e.message}\n${sw}"
            DebugLog.log("CRASH: ${e.javaClass.simpleName}: ${e.message}")
            File(appContext.filesDir, "crash.log").writeText(msg)
        }
        defaultHandler?.uncaughtException(t, e) ?: exitProcess(1)
    }
}
