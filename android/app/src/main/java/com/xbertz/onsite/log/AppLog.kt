package com.xbertz.onsite.log

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException

/**
 * Logcat, plus a small rotating copy kept on the device.
 *
 * The on-device copy is the point of this class. The people using this app are on a roof
 * somewhere, not attached to adb, so "it didn't sync yesterday" arrives long after any
 * logcat is gone; Settings -> diagnostics shares [collectForSharing]'s file back to us.
 *
 * Everything here is best effort and swallows its own I/O errors: a logger that can crash
 * the app it exists to explain is worse than no logger. Two files of [MAX_FILE_BYTES] are
 * kept, so the worst case on disk is small enough to attach to an email.
 */
object AppLog {
    private const val FILE_NAME = "onsite.log"
    private const val PREVIOUS_FILE_NAME = "onsite.log.1"
    private const val MAX_FILE_BYTES = 192L * 1024

    /** Serializes file writes and keeps them off whatever thread is logging. */
    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "AppLog").apply { isDaemon = true }
    }
    private val timestamp: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

    /**
     * Access tokens travel in headers, never in a log call - but an exception message can
     * still quote a URL or a response body, and this file gets shared by email. Cheap
     * insurance against carrying a live session around.
     */
    private val JWT_SHAPED = Regex("""eyJ[A-Za-z0-9_=-]{8,}\.[A-Za-z0-9_=-]+(\.[A-Za-z0-9_=-]+)?""")

    @Volatile private var logDir: File? = null
    @Volatile private var cacheDir: File? = null
    @Volatile private var debuggable = false
    @Volatile private var appVersion = "?"

    /** Called once from MainActivity; until then logging still reaches logcat. */
    fun init(context: Context) {
        val app = context.applicationContext
        debuggable = (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        appVersion = runCatching {
            app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "?"
        }.getOrDefault("?")
        logDir = File(app.filesDir, "logs").also { it.mkdirs() }
        cacheDir = app.cacheDir
        i(
            "App",
            "app start version=$appVersion device=${Build.MANUFACTURER} ${Build.MODEL} " +
                "android=${Build.VERSION.RELEASE} debuggable=$debuggable",
        )
    }

    /** Detail useful while developing; kept out of release builds' logcat and file. */
    fun d(tag: String, message: String) {
        if (!debuggable) return
        Log.d(tag, message)
        append("D", tag, message, null)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        append("I", tag, message, null)
    }

    /** Something failed but the app carried on - the level most of this app's failures are. */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(tag, message, throwable)
        append("W", tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        append("E", tag, message, throwable)
    }

    private fun append(level: String, tag: String, message: String, throwable: Throwable?) {
        val dir = logDir ?: return
        val now = Instant.now()
        writer.execute {
            runCatching {
                val line = buildString {
                    append(timestamp.format(now)).append(' ').append(level).append('/').append(tag)
                    append(": ").append(message)
                    throwable?.let {
                        append(" | ").append(it.javaClass.simpleName)
                        it.message?.let { m -> append(": ").append(m) }
                    }
                    append('\n')
                }
                val file = File(dir, FILE_NAME)
                if (file.length() > MAX_FILE_BYTES) {
                    File(dir, PREVIOUS_FILE_NAME).delete()
                    file.renameTo(File(dir, PREVIOUS_FILE_NAME))
                }
                file.appendText(JWT_SHAPED.replace(line, "<token>"))
            }
        }
    }

    /**
     * Flattens what is on disk (oldest first) into one file under cacheDir for the share
     * sheet, headed by the device details that a bug report is useless without. Returns
     * null when there is nothing logged yet.
     */
    fun collectForSharing(): File? {
        val dir = logDir ?: return null
        val cache = cacheDir ?: return null
        drain()
        val parts = listOf(File(dir, PREVIOUS_FILE_NAME), File(dir, FILE_NAME)).filter { it.exists() }
        if (parts.isEmpty()) return null
        return runCatching {
            val target = File(cache, "logs").also { it.mkdirs() }.let { File(it, "onsite-log.txt") }
            target.writeText(
                "onsite $appVersion on ${Build.MANUFACTURER} ${Build.MODEL}, " +
                    "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n\n",
            )
            parts.forEach { target.appendText(it.readText()) }
            target
        }.getOrNull()
    }

    /** Waits for queued lines to reach disk, so a share includes the failure just logged. */
    private fun drain() {
        runCatching { writer.submit { }.get() }
    }
}

/**
 * The house style for a call whose failure must not stop the caller: keep the
 * `runCatching { ... }.getOrDefault(...)` shape, but leave a trace behind. A swallowed
 * exception is the difference between a five-minute diagnosis and a guess.
 */
fun <T> Result<T>.logFailure(tag: String, action: String): Result<T> =
    onFailure {
        // runCatching catches cancellation too; a coroutine going away is not a failure
        // and logging it as one would train everyone to ignore these lines.
        if (it !is CancellationException) AppLog.w(tag, "$action failed", it)
    }
