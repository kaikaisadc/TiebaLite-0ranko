package com.huanchengfly.tieba.post.ui.page.thread

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.util.fastFirstOrNull
import com.huanchengfly.tieba.post.ui.models.PostData

internal fun LazyListState.middleVisiblePost(uiState: ThreadUiState): PostData? = layoutInfo.run {
    val middle = visibleItemsInfo.getOrNull(visibleItemsInfo.size / 2)
    val postItem = middle?.takeIf { it.contentType === Type.Post }
        ?: visibleItemsInfo.lastOrNull { it.contentType === Type.Post }
    return uiState.resolvePost(postItem)
}

/**
 * 该楼层顶部相对视口起始线的位移: >0 表示楼头已滚出视口, <0 表示楼头仍在视口内.
 * 该楼层不在可见范围内时返回 null.
 */
internal fun LazyListState.scrolledPastOffsetInItem(itemIndex: Int): Int? = layoutInfo.run {
    val item = visibleItemsInfo.firstOrNull { it.index == itemIndex } ?: return null
    if (itemIndex == firstVisibleItemIndex) {
        firstVisibleItemScrollOffset
    } else {
        viewportStartOffset - item.offset
    }
}

/**
 * 评论导航的锚点: 取视口顶部第一个可见楼层, 使「上一楼/下一楼」按可视顺序逐层跳转.
 *
 * 与 [middleVisiblePost](取中间楼, 用于收藏楼层/返回键) 区分, 避免锚点漂移导致方向感知错乱.
 */
internal fun LazyListState.navigationAnchorPost(uiState: ThreadUiState): PostData? = layoutInfo.run {
    // 楼主帖(FirstPost)也视为可锚定帖子: 进入详情后首个可见帖是楼主帖, 这样第一按 ▼ 落到 2 楼
    // 优先取「顶边已进入视口」的首个帖子; 跳转让位后, 上一楼顶部在视口外, 不会被误选为锚点
    val viewportStart = viewportStartOffset
    val postItem = visibleItemsInfo.firstOrNull {
        (it.contentType === Type.Post || it.contentType === Type.FirstPost) &&
                (it.offset - viewportStart) >= 0
    } ?: visibleItemsInfo.firstOrNull {
        it.contentType === Type.Post || it.contentType === Type.FirstPost
    }
    return uiState.resolvePost(postItem)
}

private fun ThreadUiState.resolvePost(item: LazyListItemInfo?): PostData? {
    if (item == null || item.contentType === Type.FirstPost) return firstPost
    val postId = item.key as Long
    return data.fastFirstOrNull { it.id == postId } ?: firstPost
}
