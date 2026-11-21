package com.huanchengfly.tieba.post.ui.page.photoview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.iielse.imageviewer.adapter.ItemType
import com.github.iielse.imageviewer.core.DataProvider
import com.github.iielse.imageviewer.core.Photo
import com.huanchengfly.tieba.post.api.TiebaApi
import com.huanchengfly.tieba.post.api.models.PicPageBean
import com.huanchengfly.tieba.post.api.models.bestQualitySrc
import com.huanchengfly.tieba.post.api.models.isGif
import com.huanchengfly.tieba.post.api.models.isLongPic
import com.huanchengfly.tieba.post.api.retrofit.exception.TiebaApiException
import com.huanchengfly.tieba.post.api.retrofit.exception.TiebaException
import com.huanchengfly.tieba.post.arch.TbLiteExceptionHandler
import com.huanchengfly.tieba.post.arch.firstOrThrow
import com.huanchengfly.tieba.post.models.LoadPicPageData
import com.huanchengfly.tieba.post.models.PhotoViewData
import com.huanchengfly.tieba.post.models.PicItem
import com.huanchengfly.tieba.post.utils.JobQueue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PhotoViewViewModel : ViewModel(), DataProvider {

    private val _state = MutableStateFlow(PhotoViewUiState())
    val state: StateFlow<PhotoViewUiState> = _state.asStateFlow()

    private val handler = TbLiteExceptionHandler(TAG) { _, e, _ ->
        _state.update { it.copy(error = e) }
    }

    private val queue = JobQueue()

    private var initialized = false
    private var album: LoadPicPageData? = null

    private fun List<PicPageBean.PicBean>.toUniquePhotoViewItems(old: List<PhotoViewItem>): List<PhotoViewItem> {
        val oldDataIds = old.mapTo(HashSet()) { it.picId }
        return this
            .filterNot { oldDataIds.contains(it.img.original.id) }
            .map { it.toPhotoItem() }
    }

    fun initData(viewData: PhotoViewData) {
        if (initialized) return
        initialized = true

        val localState = PhotoViewUiState(
            data = viewData.picItems.mapIndexed { index, item ->
                PhotoViewItem(item = item, overallIndex = index + 1)
            },
            totalAmount = viewData.picItems.size,
            initialIndex = viewData.index.coerceIn(0, viewData.picItems.lastIndex.coerceAtLeast(0)),
        )
        val pageData = viewData.data
        album = pageData
        if (pageData == null) {
            _state.value = localState
            return
        }

        viewModelScope.launch(Dispatchers.Default + handler) {
            val newState = try {
                loadInitialPage(pageData, localState)
            } catch (e: Exception) {
                if (e is CancellationException || localState.data.isEmpty()) throw e
                localState
            }
            ensureActive()
            _state.value = newState
        }
    }

    private suspend fun loadInitialPage(
        pageData: LoadPicPageData,
        localState: PhotoViewUiState,
    ): PhotoViewUiState {
        val picPage = pageData.toPageFlow(pageData.picId, pageData.picIndex, prev = false)
            .firstOrThrow()
        val totalAmount = picPage.picAmount ?: throw TiebaException("加载列表失败, 远古坟贴?")
        val fetchedItems = picPage.picList.orEmpty().toUniquePhotoViewItems(old = emptyList())
        val selectedIndex = fetchedItems.indexOfFirst { it.picId == pageData.picId }
        if (selectedIndex < 0) throw TiebaException("加载图片列表失败")

        val firstItemIndex = fetchedItems.first().overallIndex
        val fetchedIds = fetchedItems.mapTo(HashSet()) { it.picId }
        val precedingItems = localState.data.take(localState.initialIndex)
            .filterNot { it.picId in fetchedIds }
            .takeLast((firstItemIndex - 1).coerceAtLeast(0))
        val localItems = precedingItems.mapIndexed { index, item ->
            item.copy(overallIndex = firstItemIndex - precedingItems.size + index)
        }
        val items = localItems + fetchedItems
        return PhotoViewUiState(
            data = items.toImmutableList(),
            hasNext = items.last().overallIndex < totalAmount,
            hasPrev = items.first().overallIndex > 1,
            totalAmount = totalAmount,
            initialIndex = localItems.size + selectedIndex,
        )
    }

    override fun loadInitial(): List<Photo> {
        val state = _state.value
        require(state.data.isNotEmpty()) { "Call initData before loading photos" }
        return state.data.drop(state.initialIndex)
    }

    override fun loadBefore(key: Long, callback: (List<Photo>) -> Unit) =
        loadAdjacentPage(key, previous = true, callback = callback)

    override fun loadAfter(key: Long, callback: (List<Photo>) -> Unit) =
        loadAdjacentPage(key, previous = false, callback = callback)

    private fun loadAdjacentPage(key: Long, previous: Boolean, callback: (List<Photo>) -> Unit) {
        queue.submit(Dispatchers.Default + handler) {
            val state = _state.value
            val items = state.data
            val index = items.indexOfFirst { it.id() == key }
            if (previous && index > 0) {
                callback(items.take(index)) // Images omitted by loadInitial().
                return@submit
            }

            val pageData = album
            val anchor = items.getOrNull(index)
            val hasMore = if (previous) state.hasPrev else state.hasNext
            if (pageData == null || anchor == null || !hasMore) {
                callback(emptyList())
                return@submit
            }

            val page = pageData.toPageFlow(anchor.picId, anchor.overallIndex, prev = previous)
                .retryWhen { cause, attempt -> cause !is TiebaApiException && attempt < 3 }
                .firstOrThrow()
            val pageItems = page.picList.orEmpty()
            if (pageItems.isEmpty()) {
                if (!previous) throw TiebaException("加载图片列表失败")
                callback(emptyList())
                return@submit
            }

            val uniqueItems = pageItems.toUniquePhotoViewItems(old = items)
            val updatedState = if (previous) {
                state.copy(
                    data = (uniqueItems + items).toImmutableList(),
                    hasPrev = pageItems.first().overAllIndex.toInt() > 1,
                )
            } else {
                val amount = page.picAmount ?: throw TiebaException("加载列表失败, 远古坟贴?")
                state.copy(
                    data = (items + uniqueItems).toImmutableList(),
                    hasNext = pageItems.last().overAllIndex.toInt() < amount,
                )
            }

            withContext(Dispatchers.Main.immediate) {
                _state.value = updatedState
                callback(uniqueItems)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        queue.cancel()
    }

    companion object {
        private const val TAG = "PhotoViewViewModel"

        private fun LoadPicPageData.toPageFlow(picId: String, picIndex: Int, prev: Boolean): Flow<PicPageBean> {
            return TiebaApi.getInstance().picPageFlow(
                forumId = forumId.toString(),
                forumName = forumName,
                threadId = threadId.toString(),
                seeLz = seeLz,
                picId = picId,
                picIndex = picIndex.toString(),
                objType = objType,
                prev = prev
            )
        }

        private fun PicPageBean.PicBean.toPhotoItem(): PhotoViewItem {
            val originSize = img.original.size.toIntOrNull() ?: 0 // Bytes

            return PhotoViewItem(
                picId = img.original.id,
                originUrl = img.bestQualitySrc,
                overallIndex = overAllIndex.toInt(),
                postId = postId?.toLongOrNull(),
                type = when {
                    img.isGif -> ItemType.PHOTO

                    img.original.isLongPic() || originSize >= 1024 * 1024 * 2 -> ItemType.SUBSAMPLING

                    else -> ItemType.PHOTO
                }
            )
        }
    }
}

data class PhotoViewUiState(
    val data: List<PhotoViewItem> = persistentListOf(),
    val totalAmount: Int = 0,
    val hasNext: Boolean = false,
    val hasPrev: Boolean = false,
    val initialIndex: Int = 0,
    val error: Throwable? = null,
)

data class PhotoViewItem(
    val picId: String,
    val originUrl: String,
    val overallIndex: Int,
    val postId: Long? = null,
    val type: Int
): Photo {

    constructor(item: PicItem, overallIndex: Int): this(
        picId = item.picId,
        originUrl = item.originUrl,
        overallIndex = overallIndex,
        postId = item.postId,
        type = ItemType.PHOTO
    )

    override fun id(): Long = picId.hashCode().toLong()

    override fun itemType(): Int = type
}
