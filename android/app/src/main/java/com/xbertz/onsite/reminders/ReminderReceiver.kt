package com.xbertz.onsite.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.xbertz.onsite.LocaleManager
import com.xbertz.onsite.MainActivity
import com.xbertz.onsite.R
import com.xbertz.onsite.data.AppDatabase
import com.xbertz.onsite.data.PlannedJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Fires when one of [ReminderScheduler]'s alarms goes off. The job is re-read from Room at
 * that moment rather than carried in the intent: if it was deleted or its toggle switched off
 * since the alarm was armed, nothing is shown. A start reminder is also skipped when a session
 * is already running (they already clocked in), and the end reminder's wording depends on
 * whether there is a session to stop.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val jobId = intent.getLongExtra(ReminderScheduler.EXTRA_JOB_ID, -1L)
        val kind = intent.getIntExtra(ReminderScheduler.EXTRA_KIND, -1)
        if (jobId < 0 || kind < 0) return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                show(app, jobId, kind)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun show(context: Context, jobId: Long, kind: Int) {
        val enabled = if (kind == ReminderScheduler.KIND_START) ReminderScheduler.isStartEnabled(context) else ReminderScheduler.isEndEnabled(context)
        if (!enabled) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val db = AppDatabase.getInstance(context)
        val job = db.plannedJobDao().getById(jobId) ?: return
        val running = db.trackingSessionDao().getActiveSession() != null
        if (kind == ReminderScheduler.KIND_START && running) return

        val res = LocaleManager.resources(context)
        ensureChannel(context, res.getString(R.string.reminder_channel))
        val label = job.label() ?: res.getString(R.string.app_name)
        val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(res.configuration.locales[0])
        val (title, text) = if (kind == ReminderScheduler.KIND_START) {
            res.getString(R.string.reminder_start_title) to
                res.getString(R.string.reminder_start_text, label, job.startTime.format(time))
        } else {
            res.getString(R.string.reminder_end_title) to if (running) {
                res.getString(R.string.reminder_end_text_running, label)
            } else {
                res.getString(R.string.reminder_end_text_idle, label, (job.endTime ?: LocalTime.now()).format(time))
            }
        }

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_session)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()
        try {
            manager.notify(notificationId(jobId, kind), notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }

    private fun PlannedJob.label(): String? =
        listOfNotNull(clientName, siteLabel, jobTypeLabel).firstOrNull { it.isNotBlank() }

    private fun notificationId(jobId: Long, kind: Int): Int = NOTIFICATION_BASE + ((jobId % 100_000L).toInt() shl 1) + kind

    private fun ensureChannel(context: Context, name: String) {
        val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_HIGH)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "reminders"
        const val NOTIFICATION_BASE = 2000
    }
}
