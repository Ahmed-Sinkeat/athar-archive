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
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
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
    override suspend fun doWork(): Result {
        val entityId = inputData.getString(EntityIdKey) ?: return Result.failure()
        val content = EntryPoints.get(
            applicationContext,
            ContentWorkerEntryPoint::class.java,
        ).contentAccess()
        if (!content.isPinned(entityId)) return Result.success()

        return try {
            var lastProgressPercent = -1
            content.runDownload(entityId) { downloaded, total ->
                val progressPercent = if (total <= 0) 0 else (downloaded * 100 / total).toInt()
                if (progressPercent == lastProgressPercent) return@runDownload
                lastProgressPercent = progressPercent
                setProgressAsync(
                    workDataOf(
                        DownloadedBytesKey to downloaded,
                        TotalBytesKey to total,
                    ),
                )
            }
            ContentDownloadNotifications.completed(
                applicationContext,
                entityId,
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
    fun enqueue(entityId: String, collection: String) {
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
        workManager.enqueueUniqueWork(identity, ExistingWorkPolicy.KEEP, request)
    }

    fun cancel(entityId: String, collection: String) {
        workManager.cancelUniqueWork("content:$collection:$entityId")
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
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
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
