package com.huanchengfly.tieba.post.ui.page.thread

import com.huanchengfly.tieba.post.api.models.protos.Page
import com.huanchengfly.tieba.post.repository.PageData
import com.huanchengfly.tieba.post.ui.models.PostData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun Page.toThreadPageData(
    @ThreadSortType sortType: Int,
    current: Int = current_page,
    previous: Int = 0,
    nextPagePostId: Long = 0L,
    hasMore: Boolean = new_total_page > 1 && if (sortType == ThreadSortType.BY_DESC) {
        current > 1
    } else {
        current < new_total_page
    },
    hasPrevious: Boolean = new_total_page > 1 && if (sortType == ThreadSortType.BY_DESC) {
        previous < new_total_page
    } else {
        previous > 1 && previous < new_total_page
    },
): PageData = PageData(
    current = current,
    previous = previous,
    total = new_total_page,
    postCount = total_count,
    nextPagePostId = nextPagePostId,
    hasMore = hasMore,
    hasPrevious = hasPrevious,
)

internal fun PageData.nextPage(@ThreadSortType sortType: Int): Int {
    val page = if (sortType == ThreadSortType.BY_DESC) current - 1 else current + 1
    return page.coerceIn(1, total.coerceAtLeast(1))
}

internal fun PageData.previousPage(@ThreadSortType sortType: Int): Int {
    val page = if (sortType == ThreadSortType.BY_DESC) previous + 1 else previous - 1
    return page.coerceIn(1, total.coerceAtLeast(1))
}

internal suspend fun mergeThreadPosts(
    old: List<PostData>,
    new: List<PostData>,
    prepend: Boolean = false,
): List<PostData> = withContext(Dispatchers.Default) {
    val ids = old.mapTo(HashSet(), PostData::id)
    val added = new.filter { ids.add(it.id) }
    if (prepend) added + old else old + added
}

internal data class ThreadReplyUpdate(
    val posts: List<PostData>,
    val pendingPosts: List<PostData>? = null,
    val hasNewPosts: Boolean = false,
)

internal fun ThreadUiState.mergeLatestReplies(received: List<PostData>, responsePage: Int): ThreadReplyUpdate {
    if (received.isEmpty()) return ThreadReplyUpdate(data)

    val receivedPosts = received.distinctBy(PostData::id)
    val byId = receivedPosts.associateBy(PostData::id)
    val existingIds = data.mapTo(HashSet(), PostData::id)
    val updated = data.map { byId[it.id] ?: it }
    val added = receivedPosts.filter { it.id !in existingIds }
    if (added.isEmpty()) return ThreadReplyUpdate(updated)

    val descending = sortType == ThreadSortType.BY_DESC
    val lastFloor = (if (descending) data.firstOrNull() else data.lastOrNull())?.floor ?: 1
    val contiguous = receivedPosts.first().floor == lastFloor + 1 || responsePage == pageData.current
    return if (contiguous) {
        ThreadReplyUpdate(
            posts = if (descending) added.asReversed() + updated else updated + added,
            hasNewPosts = true,
        )
    } else {
        ThreadReplyUpdate(posts = updated, pendingPosts = receivedPosts, hasNewPosts = true)
    }
}
