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
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.atharchive.MainActivity
import com.atharchive.R
import com.atharchive.ui.components.arabicDigits
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private const val EntityIdKey = "entity_id"
private const val CollectionKey = "collection"
private const val BulkKey = "bulk"

/**
 * Transfers that run at once, across all workers. WorkManager starts up to 20 workers; all 20
 * downloading together fought over the network, the CPU and the database's single writer, and
 * the app stopped responding during «تنزيل الكل». The rest wait here, shown as «في الانتظار».
 */
private val transferSlots = Semaphore(3)
private const val ContentTag = "content"

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ContentWorkerEntryPoint {
    fun contentAccess(): ContentAccess
    fun downloadScheduler(): ContentDownloadScheduler
}

class ContentDownloadWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(-1)

    /** What this worker is fetching, for the notification; null until doWork has looked it up. */
    private var itemTitle: String? = null

    private val batches get() = EntryPoints.get(applicationContext, ContentWorkerEntryPoint::class.java).contentAccess().batches.value

    /**
     * Every worker shares one notification id, so parallel transfers show as one line. During a
     * bulk download it counts the type («الكتب · نصوص: ١٢ من ٣٤٠»), not each item's bytes.
     */
    private fun foregroundInfo(percent: Int, detail: String? = null): ForegroundInfo {
        ContentDownloadNotifications.createChannel(applicationContext)
        val openApp = PendingIntent.getActivity(applicationContext, 0, Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val batches = batches
        val done = batches.sumOf { it.done }
        val total = batches.sumOf { it.total }
        val builder = Notification.Builder(applicationContext, ContentDownloadNotifications.ProgressChannelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openApp).setOngoing(true).setOnlyAlertOnce(true)
        if (total > 0) {
            builder.setContentTitle("جارٍ تنزيل ${batches.joinToString("، ") { it.label }}")
                .setContentText("${arabicDigits(done)} من ${arabicDigits(total)}")
                .setProgress(total, done, false)
        } else {
            // One item: say which, how far, and what it is doing (fetching bytes, then preparing
            // the text for reading and search), not a bare «جارٍ تنزيل المحتوى».
            val title = itemTitle
            builder.setContentTitle(if (title != null) "جارٍ تنزيل «$title»" else "جارٍ تنزيل المحتوى")
                .setContentText(
                    listOfNotNull(
                        percent.takeIf { it >= 0 }?.let { "${arabicDigits(it)}٪" },
                        detail ?: "يمكنك إيقافه أو إلغاؤه من صفحة التنزيلات",
                    ).joinToString(" · "),
                )
                .setProgress(100, percent.coerceAtLeast(0), percent < 0)
        }
        val notification = builder.build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(ContentDownloadNotifications.OngoingId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(ContentDownloadNotifications.OngoingId, notification)
    }

    private suspend fun transfer(): Result {
        val entityId = inputData.getString(EntityIdKey) ?: return Result.failure()
        val content = EntryPoints.get(
            applicationContext,
            ContentWorkerEntryPoint::class.java,
        ).contentAccess()
        val scheduler = EntryPoints.get(applicationContext, ContentWorkerEntryPoint::class.java).downloadScheduler()
        val collection = inputData.getString(CollectionKey)
        val isAudio = collection == "audio"
        val identity = "content:$collection:$entityId"
        val pauseKey = if (isAudio) "audio:$entityId" else entityId
        content.awaitDownloadState()
        if (pauseKey in content.pausedDownloads.value) return Result.success()
        if (isAudio) {
            if (entityId !in content.requestedAudio.value) return Result.success()
        } else if (!content.isPinned(entityId)) return Result.success()
        // Audio ids are not catalog entities; their line keeps the generic title.
        if (!isAudio) itemTitle = runCatching { content.entityTitle(entityId) }.getOrNull()

        return try {
            // The notification is a courtesy, never a gate: with the permission denied, the app
            // briefly backgrounded by the permission dialog, or Android 15's daily data-sync
            // limit used up, promoting to foreground throws. The transfer runs regardless; if
            // the system stops it in the background, WorkManager runs it again.
            var lastProgressPercent = -1
            var lastNotified = 0L
            val progress: (Long, Long) -> Unit = { downloaded, total ->
                val percent = if (total <= 0) 0 else (downloaded * 100 / total).toInt()
                if (percent != lastProgressPercent) {
                    lastProgressPercent = percent
                    val now = android.os.SystemClock.elapsedRealtime()
                    scheduler.report(identity, TransferProgress(downloaded, total))
                    // A batch counts items, which only change when one finishes. A text package
                    // reports its bytes as the first half and its import as the second.
                    val half = total / 2
                    val detail = when {
                        isAudio || total <= 0 -> if (total > 0) "${byteSize(downloaded)} من ${byteSize(total)}" else null
                        downloaded < half -> "${byteSize(downloaded)} من ${byteSize(half)}"
                        else -> "تجهيز النص للقراءة والبحث"
                    }
                    // the notification at most once a second: a 650-byte item used to promote four times
                    if (batches.isEmpty() && now - lastNotified >= 1_000) {
                        lastNotified = now
                        runCatching { setForegroundAsync(foregroundInfo(percent, detail)) }
                    }
                }
            }
            scheduler.report(identity, TransferProgress(0, 0, queued = true))
            transferSlots.withPermit {
                // Promoted only once it has a slot: 200 queued workers each promoted before waiting.
                try { setForeground(getForegroundInfo()) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { }
                if (isAudio) content.runAudioDownload(entityId, progress) else content.runDownload(entityId, progress)
            }
            Analytics.hit("download", pauseKey)
            val batches = content.finishBatchItem(pauseKey)
            if (batches.isEmpty()) {
                ContentDownloadNotifications.completed(applicationContext, pauseKey, content.entityTitle(entityId))
            } else {
                // Part of a bulk download: one notice when its whole type is done, none per item.
                batches.filter { it.left.isEmpty() }.forEach {
                    ContentDownloadNotifications.batchCompleted(applicationContext, it.label, it.total)
                }
            }
            Result.success()
        } catch (error: CancellationException) {
            // Android 15+ allows data-sync work about six hours a day. WorkManager re-queues it;
            // the reader is told why it stopped and that opening the app resets the limit.
            if (stopReason == WorkInfo.STOP_REASON_FOREGROUND_SERVICE_TIMEOUT) {
                ContentDownloadNotifications.timeLimitReached(applicationContext)
            }
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

    /**
     * «تنزيل الكل»: one worker takes the pinned, unfinished items itself, three at a time, instead of
     * one WorkManager job per item. A 953-item run spent 60% of its CPU in WorkManager bookkeeping
     * (job rows, constraint checks, foreground promotion) rather than in the transfers (2026-11-10).
     */
    private suspend fun bulk(): Result {
        val entry = EntryPoints.get(applicationContext, ContentWorkerEntryPoint::class.java)
        val content = entry.contentAccess()
        val scheduler = entry.downloadScheduler()
        content.awaitDownloadState()
        try { setForeground(getForegroundInfo()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { }
        var transient = false
        var stop = false
        var lastNotified = 0L
        while (!stop) {
            val todo = content.pendingTextDownloads()
            if (todo.isEmpty()) break
            var progressed = false
            kotlinx.coroutines.coroutineScope {
                for (target in todo) launch {
                    transferSlots.withPermit {
                        val id = target.entityId
                        if (stop || id in content.pausedDownloads.value || !content.isPinned(id)) return@withPermit
                        val identity = "content:${target.collection}:$id"
                        try {
                            content.runDownload(id) { downloaded, total -> scheduler.report(identity, TransferProgress(downloaded, total)) }
                            progressed = true
                            val batches = content.finishBatchItem(id)
                            batches.filter { it.left.isEmpty() }.forEach {
                                ContentDownloadNotifications.batchCompleted(applicationContext, it.label, it.total)
                            }
                            val now = android.os.SystemClock.elapsedRealtime()
                            if (now - lastNotified >= 1_000) {
                                lastNotified = now
                                runCatching { setForegroundAsync(foregroundInfo(-1)) }
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: InsufficientContentStorageException) {
                            // no room for any item: stop the run, the rows show the failure
                            stop = true
                        } catch (_: ContentTransportException) {
                            transient = true
                        } catch (_: ContentIntegrityException) {
                            transient = true
                        } catch (_: Throwable) {
                            // this item stays failed in its row; the rest go on
                        } finally {
                            scheduler.report(identity, null)
                        }
                    }
                }
            }
            // ponytail: an item that fails every time is skipped for this run and shows as failed; nothing loops on it
            if (!progressed) break
        }
        return if (transient && runAttemptCount < 4) Result.retry() else Result.success()
    }

    override suspend fun doWork(): Result = if (inputData.getBoolean(BulkKey, false)) bulk() else try {
        transfer()
    } finally {
        val collection = inputData.getString(CollectionKey)
        EntryPoints.get(applicationContext, ContentWorkerEntryPoint::class.java).downloadScheduler()
            .report("content:$collection:${inputData.getString(EntityIdKey)}", null)
    }

    private fun errorData(error: Throwable) = workDataOf(
        "error" to (error.message ?: error::class.java.simpleName),
    )
}

@Singleton
class ContentDownloadScheduler @Inject constructor(
    private val workManager: WorkManager,
) {
    /** Unfinished and failed downloads only: finished ones pile up by the thousand after «تنزيل الكل». */
    val workInfos = workManager.getWorkInfosFlow(
        WorkQuery.Builder.fromTags(listOf(ContentTag))
            .addStates(listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED, WorkInfo.State.FAILED))
            .build(),
    )

    private val _progress = MutableStateFlow<Map<String, TransferProgress>>(emptyMap())

    /**
     * Live bytes per running download, by its unique work name, kept in memory. Through
     * WorkManager's progress every percent of every worker was a database write that re-read
     * all download jobs, finished ones included.
     */
    val progress: StateFlow<Map<String, TransferProgress>> = _progress

    fun report(identity: String, progress: TransferProgress?) =
        _progress.update { if (progress == null) it - identity else it + (identity to progress) }

    /**
     * [offline]: the package is already on the phone (sent from another one), so no network is awaited.
     * [wifiOnly]: «تنزيل الكل» waits for an unmetered network unless the reader chose mobile data.
     */
    fun enqueue(entityId: String, collection: String, replace: Boolean = false, offline: Boolean = false, wifiOnly: Boolean = false) {
        val identity = "content:$collection:$entityId"
        val request = OneTimeWorkRequestBuilder<ContentDownloadWorker>()
            .setInputData(
                workDataOf(
                    EntityIdKey to entityId,
                    CollectionKey to collection,
                ),
            )
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(
                    when {
                        offline -> NetworkType.NOT_REQUIRED
                        wifiOnly -> NetworkType.UNMETERED
                        else -> NetworkType.CONNECTED
                    },
                ).build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(ContentTag)
            .addTag(identity)
            .build()
        workManager.enqueueUniqueWork(identity, if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request)
    }

    /**
     * The one queue worker of «تنزيل الكل» (see [ContentDownloadWorker.bulk]): it reads the pins
     * itself, so this is a single enqueue whatever the count. Mobile data replaces a Wi-Fi-only
     * run so the new choice applies; a Wi-Fi-only request never tightens a running one.
     */
    fun enqueueBulk(wifiOnly: Boolean) {
        val request = OneTimeWorkRequestBuilder<ContentDownloadWorker>()
            .setInputData(workDataOf(BulkKey to true))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(ContentTag)
            .build()
        workManager.enqueueUniqueWork("content:bulk", if (wifiOnly) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE, request)
    }

    /** For bulk pause: fire the cancel without waiting on each one in turn. */
    fun cancelQuietly(entityId: String, collection: String) {
        workManager.cancelUniqueWork("content:$collection:$entityId")
    }

    suspend fun cancel(entityId: String, collection: String) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            workManager.cancelUniqueWork("content:$collection:$entityId").result.get()
        }
    }
}

data class TransferProgress(val downloaded: Long, val total: Long, val queued: Boolean = false)

object ContentDownloadNotifications {
    private const val ChannelId = "content_downloads"
    /** Silent: every worker re-posts the progress line, and on an alerting channel each one rang. */
    const val ProgressChannelId = "content_download_progress"
    private const val GroupKey = "athar_content_downloads"
    private const val SummaryId = 0x415448
    const val OngoingId = 0x415449
    private const val TimeLimitId = 0x41544A

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
        manager.createNotificationChannel(
            NotificationChannel(ProgressChannelId, "تقدّم التنزيل", NotificationManager.IMPORTANCE_LOW),
        )
    }

    fun batchCompleted(context: Context, label: String, count: Int) {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        createChannel(context)
        val openApp = PendingIntent.getActivity(context, label.hashCode(), Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        context.getSystemService(NotificationManager::class.java).notify(
            "batch:$label".hashCode(),
            Notification.Builder(context, ChannelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("اكتمل تنزيل $label")
                .setContentText("${arabicDigits(count)} عنصر")
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .build(),
        )
    }

    fun timeLimitReached(context: Context) {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        createChannel(context)
        val openApp = PendingIntent.getActivity(context, TimeLimitId, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        context.getSystemService(NotificationManager::class.java).notify(
            TimeLimitId,
            Notification.Builder(context, ChannelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("توقّف التنزيل مؤقتًا")
                .setContentText("بلغ حدّ أندرويد للتنزيل في الخلفية؛ افتح التطبيق ليكمل")
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .build(),
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
            .setGroupAlertBehavior(Notification.GROUP_ALERT_SUMMARY)
            .build()
        val summary = Notification.Builder(context, ChannelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("تنزيلات آثار")
            .setContentText("اكتملت تنزيلات المحتوى")
            .setGroup(GroupKey)
            .setGroupSummary(true)
            .setOnlyAlertOnce(true)
            .build()
        manager.notify(entityId.hashCode(), item)
        manager.notify(SummaryId, summary)
    }
}
