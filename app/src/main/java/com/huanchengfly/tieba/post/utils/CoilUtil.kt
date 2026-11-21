package com.huanchengfly.tieba.post.utils

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.WorkerThread
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.unit.IntSize
import coil3.decode.BlackholeDecoder
import coil3.decode.Decoder
import coil3.ImageLoader
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.huanchengfly.tieba.post.arch.unsafeLazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

object CoilUtil {

    val DarkFilter: ColorFilter by unsafeLazy {
        ColorFilter.colorMatrix(ColorMatrix().apply {
            setToScale(0.7f, 0.7f, 0.7f, 1.0f)
        })
    }

    val BlackholeDecoderFactory: Decoder.Factory by unsafeLazy {
        BlackholeDecoder.Factory()
    }

    fun ImageRequest.Builder.downloadOnly() = apply {
        decoderFactory(BlackholeDecoderFactory).memoryCachePolicy(CachePolicy.DISABLED)
    }

    suspend fun <T> withDownloadedImage(
        context: Context,
        url: String,
        imageLoader: ImageLoader = context.applicationContext.imageLoader,
        consume: suspend (File) -> T,
    ): T = withContext(Dispatchers.IO) {
        val diskCache = imageLoader.diskCache ?: throw IOException("Image disk cache is disabled")
        val snapshot = diskCache.openSnapshot(url) ?: run {
            val request = ImageRequest.Builder(context.applicationContext)
                .data(url)
                .downloadOnly()
                .build()
            when (val result = imageLoader.execute(request)) {
                is ErrorResult -> throw IOException("Unable to download image", result.throwable)
                is SuccessResult -> diskCache.openSnapshot(result.diskCacheKey ?: url)
                    ?: throw IOException("Downloaded image is unavailable in the cache")
            }
        }
        snapshot.use { consume(it.data.toFile()) }
    }

    /**
     * Decodes the raw dimensions without allocating memory for the entire image
     * */
    @WorkerThread
    fun decodeRawDimensions(context: Context, resource: Uri): Result<IntSize> {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        try {
            val input = context.contentResolver.openInputStream(resource) ?: throw IOException("Unable to open $resource")
            input.use { BitmapFactory.decodeStream(it, null, options) }

            if (options.outWidth == -1 || options.outHeight == -1) {
                throw IOException("Failed to decode dimensions of $resource")
            }
            return Result.success(
                IntSize(width = options.outWidth, height = options.outHeight)
            )
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}