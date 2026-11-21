package com.huanchengfly.tieba.post.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.utils.NotificationUtils
import com.huanchengfly.tieba.post.utils.NotificationUtils.notificationManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

@HiltWorker
class UpdateDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val versionName = inputData.getString(KEY_VERSION_NAME) ?: return Result.failure()
        val versionCode = inputData.getInt(KEY_VERSION_CODE, 0)
        val expectedSize = inputData.getLong(KEY_APK_SIZE, 0L)

        val context = applicationContext
        val info = UpdateManager.UpdateInfo(versionCode, versionName, null, url, expectedSize)
        return when (val result = UpdateManager.downloadAndTrack(context, info)) {
            is UpdateManager.DownloadResult.Success -> {
                postNotification(
                    title = context.getString(R.string.update_notify_ready_title, versionName),
                    text = context.getString(R.string.update_notify_ready_text),
                    contentIntent = installPendingIntent(context, result.file, versionCode),
                )
                Result.success()
            }

            is UpdateManager.DownloadResult.Failure -> {
                postNotification(
                    title = context.getString(R.string.update_notify_failed_title, versionName),
                    text = context.getString(R.string.update_notify_failed_text),
                    contentIntent = null,
                )
                Result.failure()
            }
        }
    }

    private fun postNotification(
        title: String,
        text: String,
        contentIntent: PendingIntent?,
    ) {
        if (!NotificationUtils.checkPermission(applicationContext)) return
        createChannel()
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentIntent)
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationUtils.createChannel(
                channelId = CHANNEL_ID,
                name = applicationContext.getString(R.string.update_channel_download),
            )
        }
    }

    private fun installPendingIntent(context: Context, file: File, versionCode: Int): PendingIntent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.share.FileProvider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, UpdateManager.APK_MIME_TYPE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return PendingIntent.getActivity(
            context,
            versionCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val WORK_NAME = "update_download"
        private const val KEY_URL = "url"
        private const val KEY_VERSION_NAME = "version_name"
        private const val KEY_VERSION_CODE = "version_code"
        private const val KEY_APK_SIZE = "apk_size"

        private const val CHANNEL_ID = "update_download"
        private const val NOTIFICATION_ID = 0x5A17

        fun enqueue(context: Context, info: UpdateManager.UpdateInfo) {
            val request = OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
                .setInputData(
                    workDataOf(
                        KEY_URL to info.apkUrl,
                        KEY_VERSION_NAME to info.versionName,
                        KEY_VERSION_CODE to info.versionCode,
                        KEY_APK_SIZE to info.apkSize,
                    )
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("$WORK_NAME:${info.versionCode}", ExistingWorkPolicy.KEEP, request)
        }
    }
}
