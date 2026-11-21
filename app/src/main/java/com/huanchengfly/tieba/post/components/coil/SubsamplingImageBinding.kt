package com.huanchengfly.tieba.post.components.coil

import android.net.Uri
import android.util.Log
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import coil3.ImageLoader
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.utils.CoilUtil
import com.huanchengfly.tieba.post.utils.ImageUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class SubsamplingImageBinding(
    private val view: SubsamplingScaleImageView,
    private val lifecycle: Lifecycle,
    private val loader: ImageLoader,
    private val url: String,
) : View.OnAttachStateChangeListener {
    private var loadJob: Job? = null

    fun bind() {
        (view.getTag(R.id.image_load_tag) as? SubsamplingImageBinding)?.unbind()
        view.setTag(R.id.image_load_tag, this)
        view.maxScale = 10f
        view.isZoomEnabled = true
        view.addOnAttachStateChangeListener(this)
        load()
    }

    override fun onViewAttachedToWindow(view: View) = load()

    override fun onViewDetachedFromWindow(view: View) = clear()

    private fun unbind() {
        view.removeOnAttachStateChangeListener(this)
        clear()
    }

    private fun clear() {
        view.recycle()
        loadJob?.cancel()
        loadJob = null
    }

    private fun load() {
        if (loadJob != null) return
        loadJob = lifecycle.coroutineScope.launch {
            try {
                CoilUtil.withDownloadedImage(view.context, url, loader) { file ->
                    val uri = Uri.fromFile(file)
                    val size = CoilUtil.decodeRawDimensions(view.context, uri).getOrThrow()
                    withContext(Dispatchers.Main.immediate) {
                        view.setMinimumScaleType(
                            if (ImageUtil.isLongImg(size.width, size.height)) {
                                SubsamplingScaleImageView.SCALE_TYPE_START
                            } else {
                                SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
                            }
                        )
                        view.setImage(ImageSource.uri(uri))
                    }
                    // Region decoding continues after setImage returns.
                    awaitCancellation()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("SubsamplingImage", "Unable to open image", e)
                view.recycle()
                AppCompatResources.getDrawable(view.context, R.drawable.ic_error)?.let {
                    view.setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE)
                    view.setImage(ImageSource.bitmap(it.toBitmap()))
                }
            }
        }
    }
}
