package com.huanchengfly.tieba.post.ui.page.thread

import androidx.compose.runtime.Immutable
import com.huanchengfly.tieba.post.arch.UiState
import com.huanchengfly.tieba.post.repository.PageData
import com.huanchengfly.tieba.post.ui.models.PostData
import com.huanchengfly.tieba.post.ui.models.SimpleForum
import com.huanchengfly.tieba.post.ui.models.ThreadInfoData
import com.huanchengfly.tieba.post.ui.models.UserData

@Immutable
sealed interface ThreadLoadState {
    data object Idle : ThreadLoadState

    sealed interface Running : ThreadLoadState {
        data object InitialLoading : Running
        data object Refreshing : Running
        data object LoadingMore : Running
        data class LoadingReply(val postId: Long) : Running
    }

    data class Failed(val cause: Throwable, val showOnPage: Boolean) : ThreadLoadState
}

@Immutable
data class ThreadUiState(
    val loadState: ThreadLoadState = ThreadLoadState.Idle,
    val seeLz: Boolean = false,
    @ThreadSortType val sortType: Int = ThreadSortType.DEFAULT,
    val user: UserData? = null,
    val firstPost: PostData? = null,
    val thread: ThreadInfoData? = null,
    val tbs: String? = null,
    val data: List<PostData> = emptyList(),
    val latestPosts: List<PostData>? = null,
    val pageData: PageData = PageData()
) : UiState {

    val isLoadingPage: Boolean
        get() = loadState == ThreadLoadState.Running.InitialLoading ||
                loadState == ThreadLoadState.Running.Refreshing

    val isLoadingMore: Boolean
        get() = loadState == ThreadLoadState.Running.LoadingMore

    val error: Throwable?
        get() = (loadState as? ThreadLoadState.Failed)?.takeIf { it.showOnPage }?.cause

    val lz: UserData?
        get() = firstPost?.author

    val forum: SimpleForum?
        get() = thread?.simpleForum
}
