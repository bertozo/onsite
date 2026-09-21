package com.xbertz.onsite.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.xbertz.onsite.data.AppDatabase
import com.xbertz.onsite.data.PlannedJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Local reminders derived from the planning calendar - one alarm at each planned job's start
 * ("clock in") and one at its end ("clock out"; a job with no end time is reminded 8 hours
 * after its start). Everything is on-device: the plan lives in Room, so there is no server
 * push involved.
 *
 * Alarms are not a queue that mutates with the plan: [reschedule] wipes whatever was set and
 * re-arms from the current rows, and is called wherever the plan or the toggles can change
 * (planning edits, sync pulls, settings, app start, boot). The request codes of the alarms
 * currently armed are remembered in prefs so that wipe can find them. Only the next
 * [HORIZON_DAYS] are armed - AlarmManager caps an app at 500 alarms, and anything further
 * out is re-armed on a later reschedule anyway.
 *
 * Exact alarms need SCHEDULE_EXACT_ALARM on API 31+, which the user grants from a system
 * screen (see [exactAlarmsAllowed]); without it the alarm is windowed to 10 minutes.
 */
object ReminderScheduler {
    const val PREFS = "settings"
    const val KEY_REMIND_START = "remind_start"
    const val KEY_REMIND_END = "remind_end"
    private const val KEY_ARMED_CODES = "reminder_request_codes"

    const val EXTRA_JOB_ID = "jobId"
    const val EXTRA_KIND = "kind"
    const val KIND_START = 0
    const val KIND_END = 1

    private const val HORIZON_DAYS = 14L
    private val WINDOW = Duration.ofMinutes(10)
    private val DEFAULT_END_AFTER = Duration.ofHours(8)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun isStartEnabled(context: Context) = prefs(context).getBoolean(KEY_REMIND_START, true)
    fun isEndEnabled(context: Context) = prefs(context).getBoolean(KEY_REMIND_END, true)

    fun setStartEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_REMIND_START, enabled).apply()
        rescheduleAsync(context)
    }

    fun setEndEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_REMIND_END, enabled).apply()
        rescheduleAsync(context)
    }

    fun exactAlarmsAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager(context).canScheduleExactAlarms()

    /** Fire-and-forget variant for callers without a coroutine (Activity, receivers, prefs setters). */
    fun rescheduleAsync(context: Context) {
        val app = context.applicationContext
        scope.launch { runCatching { reschedule(app) } }
    }

    suspend fun reschedule(context: Context) {
        val app = context.applicationContext
        val alarms = alarmManager(app)
        val prefs = prefs(app)

        prefs.getStringSet(KEY_ARMED_CODES, emptySet()).orEmpty().forEach { code ->
            code.toIntOrNull()?.let { alarms.cancel(pendingIntent(app, it, null)) }
        }

        val remindStart = isStartEnabled(app)
        val remindEnd = isEndEnabled(app)
        if (!remindStart && !remindEnd) {
            prefs.edit().remove(KEY_ARMED_CODES).apply()
            return
        }

        val now = System.currentTimeMillis()
        val horizon = now + Duration.ofDays(HORIZON_DAYS).toMillis()
        val jobs = AppDatabase.getInstance(app).plannedJobDao().getAll().first()
        val armed = mutableSetOf<String>()

        for (job in jobs) {
            val startAt = job.startInstantMillis()
            if (remindStart && startAt in (now + 1)..horizon) {
                arm(app, alarms, job, KIND_START, startAt)
                armed += requestCode(job.id, KIND_START).toString()
            }
            val endAt = job.endInstantMillis()
            if (remindEnd && endAt in (now + 1)..horizon) {
                arm(app, alarms, job, KIND_END, endAt)
                armed += requestCode(job.id, KIND_END).toString()
            }
        }
        prefs.edit().putStringSet(KEY_ARMED_CODES, armed).apply()
    }

    private fun arm(context: Context, alarms: AlarmManager, job: PlannedJob, kind: Int, atMillis: Long) {
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(EXTRA_JOB_ID, job.id)
            .putExtra(EXTRA_KIND, kind)
        val pending = pendingIntent(context, requestCode(job.id, kind), intent)
        if (exactAlarmsAllowed(context)) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        } else {
            alarms.setWindow(AlarmManager.RTC_WAKEUP, atMillis, WINDOW.toMillis(), pending)
        }
    }

    private fun pendingIntent(context: Context, requestCode: Int, intent: Intent?): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            intent ?: Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Two codes per job; the id is an autoincrement so it fits comfortably after the shift. */
    private fun requestCode(jobId: Long, kind: Int): Int = ((jobId % 1_000_000_000L).toInt() shl 1) or kind

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun alarmManager(context: Context) = context.getSystemService(AlarmManager::class.java)

    fun PlannedJob.startInstantMillis(): Long = atLocalMinute(dateEpochDay, startMinute)

    fun PlannedJob.endInstantMillis(): Long =
        endMinute?.let { atLocalMinute(dateEpochDay, it) } ?: (startInstantMillis() + DEFAULT_END_AFTER.toMillis())

    private fun atLocalMinute(epochDay: Long, minuteOfDay: Int): Long =
        LocalDate.ofEpochDay(epochDay)
            .atTime(LocalTime.ofSecondOfDay(minuteOfDay * 60L))
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
}
