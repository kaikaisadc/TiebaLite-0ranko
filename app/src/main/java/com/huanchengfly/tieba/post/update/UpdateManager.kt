package com.huanchengfly.tieba.post.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.huanchengfly.tieba.post.BuildConfig
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.utils.DownloadUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

object UpdateManager {

    private const val REPO = "kaikaisadc/TiebaLite-0ranko"
    private const val GITHUB_LATEST_RELEASE_URL =
        "https://api.github.com/repos/$REPO/releases/latest"
    internal const val APK_MIME_TYPE = "application/vnd.android.package-archive"

    private val downloadLock = Mutex()
    private val invalidFilenameChars = Regex("[^a-zA-Z0-9._-]")

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val changelog: String?,
        val apkUrl: String,
        val apkSize: Long,
    )

    sealed interface DownloadResult {
        data class Success(val file: File) : DownloadResult
        data class Failure(val message: String) : DownloadResult
    }

    /**
     * 检查 GitHub Releases 最新版本。
     * Release tag 需使用 versionCode 命名（如 v391034）；
     * 无 Release、无 APK 附件或版本不高于当前安装版本时返回 null。
     */
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(GITHUB_LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "TiebaLite")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext null
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")

            val json = response.body.string()
            val release = JSONObject(json)
            val versionCode = release.optString("tag_name")
                .trimStart('v', 'V')
                .toIntOrNull()
                ?: return@withContext null
            if (versionCode <= BuildConfig.VERSION_CODE) return@withContext null

            val assets = release.optJSONArray("assets") ?: JSONArray()
            var apkUrl: String? = null
            var apkSize = 0L
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url").takeIf { it.isNotBlank() }
                    apkSize = asset.optLong("size", 0L)
                    break
                }
            }
            val url = apkUrl ?: return@withContext null

            UpdateInfo(
                versionCode = versionCode,
                versionName = release.optString("name").ifBlank { release.optString("tag_name") },
                changelog = release.optString("body").takeIf { it.isNotBlank() },
                apkUrl = url,
                apkSize = apkSize,
            )
        }
    }

    suspend fun downloadAndTrack(
        context: Context,
        info: UpdateInfo,
        onProgress: ((Float) -> Unit)? = null,
    ): DownloadResult = withContext(Dispatchers.IO) {
        downloadLock.withLock {
            val target = updateApkFile(context, info)
                ?: return@withLock DownloadResult.Failure("下载目录不可用")
            if (isCompleteApk(target, info.apkSize)) return@withLock DownloadResult.Success(target)

            val staging = File(target.parentFile, "${target.name}.${UUID.randomUUID()}.part")
            var downloadId: Long? = null
            try {
                staging.parentFile?.mkdirs()
                val id = DownloadUtil.enqueueNewDownloadOrThrow(
                    uri = Uri.parse(info.apkUrl),
                    title = context.getString(R.string.update_download_title, info.versionName),
                    destination = Uri.fromFile(staging),
                ) {
                    setMimeType(APK_MIME_TYPE)
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    setAllowedOverMetered(true)
                }
                downloadId = id
                val result = trackDownload(id, staging, info.apkSize, onProgress)
                currentCoroutineContext().ensureActive()
                when {
                    result is DownloadResult.Failure -> result
                    staging.renameTo(target) -> DownloadResult.Success(target)
                    else -> DownloadResult.Failure("无法保存更新文件")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DownloadResult.Failure(e.message ?: "无法下载更新")
            } finally {
                downloadId?.let { runCatching { DownloadUtil.downloadManager.remove(it) } }
                staging.delete()
            }
        }
    }

    private suspend fun trackDownload(
        downloadId: Long,
        targetFile: File,
        expectedSize: Long,
        onProgress: ((Float) -> Unit)?,
    ): DownloadResult {
        while (true) {
            currentCoroutineContext().ensureActive()
            val progress = queryDownload(downloadId)
            if (progress == null) return DownloadResult.Failure("无法获取下载状态")
            when (progress.status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    // 校验下载文件完整性，避免损坏的 APK 导致安装时解析失败
                    if (!isCompleteApk(targetFile, expectedSize)) {
                        return DownloadResult.Failure("下载文件校验失败，请重试")
                    }
                    return DownloadResult.Success(targetFile)
                }

                DownloadManager.STATUS_FAILED ->
                    return DownloadResult.Failure("下载失败（错误码 ${progress.reason}）")

                else -> {
                    if (onProgress != null && progress.total > 0) {
                        val fraction =
                            (progress.downloaded.toFloat() / progress.total).coerceIn(0f, 1f)
                        withContext(Dispatchers.Main.immediate) { onProgress(fraction) }
                    }
                    delay(500)
                }
            }
        }
    }

    fun installApk(context: Context, file: File): Boolean {
        if (!file.exists()) return false
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.share.FileProvider",
                file,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, APK_MIME_TYPE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun findDownloadedApk(context: Context, info: UpdateInfo): File? = withContext(Dispatchers.IO) {
        updateApkFile(context, info)?.takeIf { isCompleteApk(it, info.apkSize) }
    }

    private fun updateApkFile(context: Context, info: UpdateInfo): File? {
        val downloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
        val name = info.versionName.replace(invalidFilenameChars, "_").take(100)
        return File(File(downloads, "apk"), "TiebaLite-$name.apk")
    }

    private fun isCompleteApk(file: File, expectedSize: Long): Boolean {
        if (!file.isFile || file.length() == 0L) return false
        if (expectedSize > 0 && file.length() != expectedSize) return false
        return try {
            ZipFile(file).use { it.getEntry("AndroidManifest.xml") != null }
        } catch (e: IOException) {
            false
        }
    }

    private data class DownloadProgress(
        val status: Int,
        val downloaded: Long,
        val total: Long,
        val reason: Int,
    )

    private fun queryDownload(downloadId: Long): DownloadProgress? {
        return try {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = DownloadUtil.downloadManager.query(query)
            cursor?.use {
                if (it.moveToFirst()) {
                    DownloadProgress(
                        status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                        downloaded = it.getLong(
                            it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        ),
                        total = it.getLong(
                            it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        ),
                        reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
                    )
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
