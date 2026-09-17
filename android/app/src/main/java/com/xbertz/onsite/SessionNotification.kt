package com.xbertz.onsite

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.xbertz.onsite.data.TrackingSession

/**
 * Ongoing notification shown while a session is being tracked. It uses the system chronometer,
 * so the elapsed time keeps ticking without any service or periodic update from the app;
 * it is posted when a session starts and cancelled when it stops.
 */
object SessionNotification {
    private const val CHANNEL_ID = "session"
    private const val NOTIFICATION_ID = 1001

    fun show(context: Context, session: TrackingSession) {
        val manager = NotificationManagerCompat.from(context)
        // On API 33+ this is false until the user grants POST_NOTIFICATIONS; notify() would then be a no-op anyway.
        if (!manager.areNotificationsEnabled()) return
        val res = LocaleManager.resources(context)
        ensureChannel(context, res.getString(R.string.notification_channel_session))

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val details = listOfNotNull(
            session.companyName?.takeIf { it.isNotBlank() },
            session.jobTypeLabel?.takeIf { it.isNotBlank() }
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_session)
            .setContentTitle(res.getString(R.string.notification_session_title, session.siteLabel.orEmpty()))
            .setContentText(details.joinToString(" · "))
            .setWhen(session.startTimestampMillis)
            .setUsesChronometer(true)
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openApp)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call; the notification is a convenience only.
        }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel(context: Context, name: String) {
        val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW).apply {
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
