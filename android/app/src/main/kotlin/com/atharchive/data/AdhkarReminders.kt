package com.atharchive.data

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.atharchive.MainActivity
import com.atharchive.R
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * One daily reminder per adhkar list, at a time of day the reader picks (minutes after midnight).
 * ponytail: WorkManager periodic work, so Doze can make it a few minutes late; move to
 * AlarmManager.setAndAllowWhileIdle if people need it on the minute.
 */
object AdhkarReminders {
    private const val Prefs = "adhkar_reminders"
    private const val ChannelId = "adhkar_reminders"

    fun all(context: Context): Map<String, Int> =
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).all.mapNotNull { (k, v) -> (v as? Int)?.let { k to it } }.toMap()

    /** [minutes] null turns the list's reminder off. */
    fun set(context: Context, listId: String, label: String, minutes: Int?) {
        val prefs = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).edit()
        val work = WorkManager.getInstance(context)
        if (minutes == null) {
            prefs.remove(listId).apply()
            work.cancelUniqueWork(name(listId))
            return
        }
        prefs.putInt(listId, minutes).apply()
        val request = PeriodicWorkRequestBuilder<AdhkarReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayUntil(LocalDateTime.now(), minutes).toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("list" to listId, "label" to label))
            .build()
        work.enqueueUniquePeriodicWork(name(listId), ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request)
    }

    /** Time until the next [minutes]-after-midnight; a time already past today means tomorrow. */
    internal fun delayUntil(now: LocalDateTime, minutes: Int): Duration {
        val today = now.toLocalDate().atStartOfDay().plusMinutes(minutes.toLong())
        return Duration.between(now, if (today.isAfter(now)) today else today.plusDays(1))
    }

    private fun name(listId: String) = "adhkar_reminder:$listId"

    internal fun notify(context: Context, listId: String, label: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(ChannelId, "تذكير الأذكار", NotificationManager.IMPORTANCE_DEFAULT))
        // Opens the app on this list (MainActivity reads the same extra the download notices use).
        val open = PendingIntent.getActivity(
            context, listId.hashCode(),
            Intent(context, MainActivity::class.java).putExtra("athar_download", "adhkar:$listId")
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.notify(
            name(listId).hashCode(),
            Notification.Builder(context, ChannelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("أذكار $label")
                .setContentText("حان وقت أذكار $label")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }
}

class AdhkarReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val list = inputData.getString("list") ?: return Result.success()
        AdhkarReminders.notify(applicationContext, list, inputData.getString("label").orEmpty())
        return Result.success()
    }
}
