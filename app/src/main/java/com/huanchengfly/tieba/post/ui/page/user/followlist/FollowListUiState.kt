package com.huanchengfly.tieba.post.ui.page.user.followlist

import androidx.compose.runtime.Immutable
import com.huanchengfly.tieba.post.api.models.FollowListBean
import com.huanchengfly.tieba.post.api.models.FollowListBean.FollowUserBean
import com.huanchengfly.tieba.post.arch.UiState
import com.huanchengfly.tieba.post.ui.models.user.ConcernType
import com.huanchengfly.tieba.post.ui.models.user.FollowUser
import com.huanchengfly.tieba.post.utils.StringUtil

enum class FollowListFilter { All, Mutual }

@Immutable
data class FollowListUiState(
    val filter: FollowListFilter = FollowListFilter.All,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: Throwable? = null,
    val currentPage: Int = 1,
    val hasMore: Boolean = false,
    val totalFollowNum: Int = 0,
    val tipsText: String? = null,
    val users: List<FollowUser> = emptyList(),
) : UiState {
    fun applyPage(result: FollowListBean, rows: List<FollowUser>, refresh: Boolean) = copy(
        isRefreshing = false,
        isLoadingMore = false,
        error = null,
        currentPage = result.pageNum,
        hasMore = result.hasMore == 1,
        totalFollowNum = if (refresh) result.totalFollowNum else totalFollowNum,
        tipsText = if (refresh) result.tipsText else tipsText,
        users = (if (refresh) rows else users + rows).distinctBy { it.uid },
    )

    fun updateConcern(uid: Long, @ConcernType type: Int, countChange: Int = 0) = copy(
        users = users.map { if (it.uid == uid) it.copy(concernType = type) else it },
        totalFollowNum = totalFollowNum + countChange,
    )
}

internal fun FollowUserBean.toFollowUser(): FollowUser {
    val portraitId = portrait.orEmpty().trim()
    return FollowUser(
        uid = id,
        avatar = StringUtil.getAvatarUrl(portraitId),
        displayName = StringUtil.getUserNameString(true, name.orEmpty(), nameShow?.takeIf { it.isNotBlank() }),
        portrait = portraitId,
        intro = intro?.takeIf { it.isNotBlank() },
        concernType = hasConcerned,
    )
}
