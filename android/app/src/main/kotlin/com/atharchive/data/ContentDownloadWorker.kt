package com.atharchive.data

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.atharchive.MainActivity
import com.atharchive.R
import com.atharchive.core.data.content.ContentIntegrityException
import com.atharchive.core.data.network.ContentTransportException
import com.atharchive.core.data.repository.InsufficientContentStorageException
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val EntityIdKey = "entity_id"
private const val CollectionKey = "collection"
private const val DownloadedBytesKey = "downloaded_bytes"
private const val TotalBytesKey = "total_bytes"
private const val ContentTag = "content"

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ContentWorkerEntryPoint {
    fun contentAccess(): ContentAccess
}

class ContentDownloadWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(-1)

    private fun foregroundInfo(percent: Int): ForegroundInfo {
        ContentDownloadNotifications.createChannel(applicationContext)
        val openApp = PendingIntent.getActivity(applicationContext, 0, Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(applicationContext, "content_downloads")
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("جارٍ تنزيل المحتوى")
            .setContentText("يمكنك إيقاف التنزيل أو إلغاؤه من صفحة التنزيلات")
            .setContentIntent(openApp).setOngoing(true).setOnlyAlertOnce(true)
            .setProgress(100, percent.coerceAtLeast(0), percent < 0).build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(id.hashCode(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(id.hashCode(), notification)
    }

    override suspend fun doWork(): Result {
        val entityId = inputData.getString(EntityIdKey) ?: return Result.failure()
        val content = EntryPoints.get(
            applicationContext,
            ContentWorkerEntryPoint::class.java,
        ).contentAccess()
        val isAudio = inputData.getString(CollectionKey) == "audio"
        val pauseKey = if (isAudio) "audio:$entityId" else entityId
        if (pauseKey in content.pausedDownloads.value) return Result.success()
        if (isAudio) {
            if (entityId !in content.requestedAudio.value) return Result.success()
        } else if (!content.isPinned(entityId)) return Result.success()

        return try {
            // The notification is a courtesy, never a gate: with the permission denied, or the
            // app briefly backgrounded by the permission dialog, promoting to foreground can
            // throw. The transfer must run regardless.
            // ponytail: unpromoted work may be stopped by the system in the background;
            // WorkManager retries it (backoff), so the download still completes.
            try { setForeground(getForegroundInfo()) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { }
            var lastProgressPercent = -1
            val progress: (Long, Long) -> Unit = { downloaded, total ->
                val percent = if (total <= 0) 0 else (downloaded * 100 / total).toInt()
                if (percent != lastProgressPercent) {
                    lastProgressPercent = percent
                    setProgressAsync(workDataOf(DownloadedBytesKey to downloaded, TotalBytesKey to total))
                    runCatching { setForegroundAsync(foregroundInfo(percent)) }
                }
            }
            if (isAudio) content.runAudioDownload(entityId, progress) else content.runDownload(entityId, progress)
            ContentDownloadNotifications.completed(
                applicationContext,
                if (isAudio) "audio:$entityId" else entityId,
                content.entityTitle(entityId),
            )
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: InsufficientContentStorageException) {
            Result.failure(
                workDataOf(
                    "error" to "low_storage",
                    "available_bytes" to error.availableBytes,
                    "required_bytes" to error.requiredBytes,
                ),
            )
        } catch (error: ContentTransportException) {
            if (runAttemptCount < 4) Result.retry() else Result.failure(errorData(error))
        } catch (error: ContentIntegrityException) {
            if (runAttemptCount < 4) Result.retry() else Result.failure(errorData(error))
        } catch (error: Throwable) {
            Result.failure(errorData(error))
        }
    }

    private fun errorData(error: Throwable) = workDataOf(
        "error" to (error.message ?: error::class.java.simpleName),
    )
}

@Singleton
class ContentDownloadScheduler @Inject constructor(
    private val workManager: WorkManager,
) {
    val workInfos = workManager.getWorkInfosByTagFlow(ContentTag)

    fun enqueue(entityId: String, collection: String, replace: Boolean = false) {
        val identity = "content:$collection:$entityId"
        val request = OneTimeWorkRequestBuilder<ContentDownloadWorker>()
            .setInputData(
                workDataOf(
                    EntityIdKey to entityId,
                    CollectionKey to collection,
                ),
            )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(ContentTag)
            .addTag(identity)
            .build()
        workManager.enqueueUniqueWork(identity, if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request)
    }

    suspend fun cancel(entityId: String, collection: String) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            workManager.cancelUniqueWork("content:$collection:$entityId").result.get()
        }
    }
}

object ContentDownloadNotifications {
    private const val ChannelId = "content_downloads"
    private const val GroupKey = "athar_content_downloads"
    private const val SummaryId = 0x415448

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                ChannelId,
                "تنزيلات المحتوى",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "اكتمال تنزيل الكتب والمقالات والقصائد"
            },
        )
    }

    fun completed(context: Context, entityId: String, title: String) {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        createChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data = android.net.Uri.parse("athar://download/${android.net.Uri.encode(entityId)}")
            putExtra("athar_download", entityId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            entityId.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val item = Notification.Builder(context, ChannelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("اكتمل التنزيل")
            .setContentText(title)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup(GroupKey)
            .build()
        val summary = Notification.Builder(context, ChannelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("تنزيلات آثار")
            .setContentText("اكتملت تنزيلات المحتوى")
            .setGroup(GroupKey)
            .setGroupSummary(true)
            .build()
        manager.notify(entityId.hashCode(), item)
        manager.notify(SummaryId, summary)
    }
}
