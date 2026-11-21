package com.huanchengfly.tieba.post.workers

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import androidx.work.workDataOf
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.api.retrofit.exception.TiebaMSignException
import com.huanchengfly.tieba.post.api.retrofit.exception.getErrorCode
import com.huanchengfly.tieba.post.api.retrofit.exception.getErrorMessage
import com.huanchengfly.tieba.post.api.retrofit.interceptors.ConnectivityInterceptor
import com.huanchengfly.tieba.post.repository.user.OKSignRepository
import com.huanchengfly.tieba.post.services.OKSignTileService
import com.huanchengfly.tieba.post.utils.DateTimeUtils
import com.huanchengfly.tieba.post.utils.HmTime
import com.huanchengfly.tieba.post.utils.NotificationUtils
import com.huanchengfly.tieba.post.utils.NotificationUtils.notificationManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

@HiltWorker
class OKSignWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val okSignRepository: OKSignRepository,
): CoroutineWorker(context, params) {

    private val notificationUpdater by lazy {
        OKSignNotificationUpdater(
            context = applicationContext,
            cancelIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id),
        ) { progress, total ->
            OKSignTileService.requestListening(applicationContext)
            setProgressAsync(workDataOf(KEY_PROGRESS to progress, KEY_TOTAL to total))
        }
    }

    override suspend fun doWork(): Result {
        if (DAILY_WORK_NAME in tags) {
            return if (okSignRepository.dispatchDailyRun(id)) Result.success() else Result.retry()
        }
        return sign()
    }

    private suspend fun sign(): Result = try {
        OKSignTileService.requestListening(applicationContext)
        notificationUpdater.setupNotification()
        try {
            setForeground(getForegroundInfo())
        } catch (e: IllegalStateException) { // Android S: ForegroundServiceStartNotAllowedException
            Log.e(TAG, "onDoWork: Set foreground failed: ${e.message}")
        }

        okSignRepository.sign(listener = notificationUpdater)
        Result.success()
    } catch (e: CancellationException) {
        Log.e(TAG, "onDoWork: $id is canceled: ${e.message}.")
        throw e
    } catch (e: Throwable) {
        Log.e(TAG, "onDoWork: ${e.getErrorMessage()}.", e)
        notificationUpdater.onError(ConnectivityInterceptor.wrapException(e))
        Result.failure(
            workDataOf(KEY_ERROR_MESSAGE to e.getErrorMessage())
        )
    } finally {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val title = applicationContext.getString(R.string.title_loading_data)
        return ForegroundInfo(
            NOTIFICATION_ID,
            notificationUpdater.buildProgressNotification(title).build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
        )
    }

    companion object {
        const val TAG = "OKSignWorker"
        const val TAG_EXPEDITED = "OKSignWorker:Expedited"
        internal const val DAILY_WORK_NAME = "OKSignWorker:Daily"
        internal const val DAILY_RUN_NAME = "OKSignWorker:DailyRun"

        const val KEY_PROGRESS = "sign_progress"
        const val KEY_TOTAL = "sign_total"

        const val KEY_ERROR_MESSAGE = "sign_error_msg"

        /**
         * Channel ID of signing notifications.
         *
         * @since 3.8.1 α
         * */
        private const val NOTIFICATION_CHANNEL_ID = "1"

        /**
         * ID of signing progress notification.
         *
         * @since 3.8.1 α
         * */
        private const val NOTIFICATION_ID = 1

        /**
         * ID of signing result notification.
         *
         * @since 4.0.0
         *
         * @see OKSignNotificationUpdater.onFinish
         * */
        private const val NOTIFICATION_ID_SIGN_RESULT = 15

        /**
         * ID of official signing failed notification.
         *
         * @since 4.0.0
         *
         * @see OKSignNotificationUpdater.onMSignFailed
         * */
        private const val NOTIFICATION_ID_M_SIGN_FAILED = 16

        internal fun dailyTimeTag(time: HmTime) = "$DAILY_WORK_NAME:${time.value}"

        internal suspend fun scheduleDaily(workManager: WorkManager, time: HmTime) {
            val request = PeriodicWorkRequestBuilder<OKSignWorker>(1, TimeUnit.DAYS)
                .addTag(TAG)
                .addTag(DAILY_WORK_NAME)
                .addTag(dailyTimeTag(time))
                .setNextScheduleTimeOverride(DateTimeUtils.nextDailyTimeMillis(time.hourOfDay, time.minute))
                .build()
            workManager.enqueueUniquePeriodicWork(
                DAILY_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            ).await()
        }

        internal suspend fun enqueueDailyRun(workManager: WorkManager) {
            val request = OneTimeWorkRequestBuilder<OKSignWorker>().addTag(TAG).build()
            workManager.enqueueUniqueWork(DAILY_RUN_NAME, ExistingWorkPolicy.KEEP, request).await()
        }

        fun startExpedited(workManager: WorkManager) {
            val request = OneTimeWorkRequestBuilder<OKSignWorker>()
                .addTag(TAG_EXPEDITED)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            workManager.enqueueUniqueWork(TAG_EXPEDITED, ExistingWorkPolicy.REPLACE, request)
        }

        private fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            NotificationChannelCompat.Builder(NOTIFICATION_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.title_oksign))
                .setLightsEnabled(false)
                .setShowBadge(false)
                .build()
                .let { channel -> notificationManager.createNotificationChannel(channel) }
        }

        /**
         * Helper class that updates the signing progress notification.
         *
         * @param context application context
         * @param cancelIntent [PendingIntent] can be used to cancel the worker
         * @param onProgress simple progress listener
         * */
        private class OKSignNotificationUpdater(
            private val context: Context,
            private val cancelIntent: PendingIntent,
            private val onProgress: (progress: Int, total: Int) -> Unit
        ): OKSignRepository.ProgressListener {

            private val notificationManager = NotificationUtils.notificationManager

            private var total = 0
            private var name: String  = ""

            fun buildProgressNotification(title: String, content: String? = null): NotificationCompat.Builder {
                return NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                    .setContentTitle(title)
                    .setTicker(title)
                    .setContentText(content)
                    .setSubText(context.getString(R.string.title_oksign))
                    .setSmallIcon(R.drawable.ic_oksign)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .addAction(0, context.getString(android.R.string.cancel), cancelIntent)
                    .setStyle(NotificationCompat.BigTextStyle())
            }

            fun setupNotification() {
                createNotificationChannel(context)
                notificationManager.cancel(NOTIFICATION_ID_SIGN_RESULT)
                notificationManager.cancel(NOTIFICATION_ID_M_SIGN_FAILED)
            }

            @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
            private fun updateProgressNotification(progress: Int, content: String?) {
                val title = context.getString(R.string.title_signing_progress, name, progress, total)
                buildProgressNotification(title, content)
                    .build()
                    .let { notificationManager.notify(NOTIFICATION_ID, it) }
            }

            override fun onInit(total: Int, userName: String) {
                this.total = total
                this.name = userName
                if (NotificationUtils.checkPermission(context)) {
                    buildProgressNotification(context.getString(R.string.title_start_sign))
                        .build()
                        .let { notificationManager.notify(NOTIFICATION_ID, it) }
                }
                onProgress(0, total)
            }

            override fun onSigned(progress: Int, forum: String, signBonusPoint: Int?) {
                if (NotificationUtils.checkPermission(context)) {
                    updateProgressNotification(
                        progress = progress + 1,
                        content = if (signBonusPoint == null) {
                            context.getString(R.string.text_singing_progress, forum)
                        } else {
                            context.getString(R.string.text_singing_progress_exp, forum, signBonusPoint)
                        }
                    )
                }
                onProgress(progress + 1, total)
            }

            override fun onFailed(progress: Int, forum: String, error: String) {
                if (NotificationUtils.checkPermission(context)) {
                    val content = context.getString(R.string.text_singing_progress_fail, forum, error)
                    updateProgressNotification(progress = progress + 1, content = content)
                }
                onProgress(progress + 1, total)
            }

            override fun onMSignFailed(e: Throwable) {
                if (NotificationUtils.checkPermission(context)) {
                    val content = if (e is TiebaMSignException) {
                        e.signNotice // Use message from server
                    } else {
                        context.getString(R.string.text_oksign_fallback)
                    }
                    NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                        .setContentTitle(context.getString(R.string.text_oksign_failed, e.getErrorCode(), e.getErrorMessage()))
                        .setContentText(content)
                        .setSmallIcon(R.drawable.ic_round_warning)
                        .build()
                        .let { notificationManager.notify(NOTIFICATION_ID_M_SIGN_FAILED, it) }
                }
            }

            override fun onFinish(succeed: Int) {
                if (NotificationUtils.checkPermission(context)) {
                    notificationManager.cancel(NOTIFICATION_ID)
                    val title = context.getString(R.string.title_oksign_finish)
                    val content = if (total > 0) {
                        context.getString(R.string.text_oksign_done, succeed)
                    } else {
                        context.getString(R.string.text_oksign_no_signable)
                    }
                    buildProgressNotification(title, content)
                        .clearActions()
                        .setOngoing(false)
                        .build()
                        .let { notificationManager.notify(NOTIFICATION_ID_SIGN_RESULT, it) }
                }
                onProgress(succeed, total)
            }

            fun onError(e: Throwable) {
                if (NotificationUtils.checkPermission(context)) {
                    notificationManager.cancel(NOTIFICATION_ID)
                    val title = context.getString(R.string.title_oksign_fail)
                    buildProgressNotification(title, e.getErrorMessage())
                        .clearActions()
                        .setOngoing(false)
                        .build()
                        .let { notificationManager.notify(NOTIFICATION_ID_SIGN_RESULT, it) }
                }
                onProgress(total, total)
            }
        }
    }
}
