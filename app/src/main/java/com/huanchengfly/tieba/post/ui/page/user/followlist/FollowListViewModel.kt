package com.huanchengfly.tieba.post.ui.page.user.followlist

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.huanchengfly.tieba.post.api.models.FollowListBean.FollowUserBean
import com.huanchengfly.tieba.post.api.retrofit.exception.getErrorMessage
import com.huanchengfly.tieba.post.arch.BaseStateViewModel
import com.huanchengfly.tieba.post.arch.CommonUiEvent
import com.huanchengfly.tieba.post.arch.UiEvent
import com.huanchengfly.tieba.post.repository.UserProfileRepository
import com.huanchengfly.tieba.post.ui.models.user.ConcernType
import com.huanchengfly.tieba.post.ui.models.user.FollowUser
import com.huanchengfly.tieba.post.ui.page.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface FollowListUiEvent : UiEvent {
    data class FollowFailed(val message: String) : FollowListUiEvent

    data class UnfollowFailed(val message: String) : FollowListUiEvent
}

@HiltViewModel
class FollowListViewModel @Inject constructor(
    private val userProfileRepo: UserProfileRepository,
    savedStateHandle: SavedStateHandle
) : BaseStateViewModel<FollowListUiState>() {

    override val errorHandler = CoroutineExceptionHandler { _, e ->
        Log.e(TAG, "onError: ", e)
        _uiState.update { it.copy(isRefreshing = false, isLoadingMore = false, error = e) }
    }

    private val param = savedStateHandle.toRoute<Destination.UserFollowList>()
    val uid: Long = param.uid

    private var pageJob: Job? = null

    override fun createInitialState() = FollowListUiState()

    init {
        onRefresh()
    }

    fun onRefresh() {
        if (currentState.isRefreshing) return
        pageJob?.cancel()
        loadPage(refresh = true)
    }

    fun onLoadMore() {
        val state = currentState
        if (state.isLoadingMore || state.isRefreshing || !state.hasMore) return
        loadPage(refresh = false)
    }

    private fun loadPage(refresh: Boolean) {
        val page = if (refresh) 1 else currentState.currentPage + 1
        _uiState.update { it.copy(isRefreshing = refresh, isLoadingMore = !refresh, error = null) }
        pageJob = launchJobInVM {
            if (refresh) {
                // Let the server apply pending follow changes before reading the first page.
                uiState.first { state -> state.users.none { it.concernType == ConcernType.UPDATING } }
            }
            try {
                val result = userProfileRepo.loadUserFollowList(uid, page)
                val rows = withContext(Dispatchers.Default) { result.followList.map(FollowUserBean::toFollowUser) }
                _uiState.update { it.applyPage(result, rows, refresh) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isRefreshing = false, isLoadingMore = false, error = e.takeIf { refresh }) }
                if (!refresh) sendUiEvent(CommonUiEvent.ToastError(e))
            }
        }
    }

    fun onFilterChanged(filter: FollowListFilter) = _uiState.update { it.copy(filter = filter) }

    fun onFollowClicked(user: FollowUser) = changeFollow(user, follow = true)

    fun onUnfollowClicked(user: FollowUser) = changeFollow(user, follow = false)

    private fun changeFollow(selected: FollowUser, follow: Boolean) = launchInVM {
        val state = currentState
        val user = state.users.firstOrNull { it.uid == selected.uid } ?: return@launchInVM
        if (state.isRefreshing || !user.canChangeFollow) return@launchInVM
        val next = when (user.concernType) {
            ConcernType.NONE -> if (follow) ConcernType.FOLLOWING else return@launchInVM
            ConcernType.FANS -> if (follow) ConcernType.MUTUAL else return@launchInVM
            ConcernType.FOLLOWING -> if (!follow) ConcernType.NONE else return@launchInVM
            ConcernType.MUTUAL -> if (!follow) ConcernType.FANS else return@launchInVM
            else -> return@launchInVM
        }

        _uiState.update { it.updateConcern(user.uid, ConcernType.UPDATING) }
        try {
            if (follow) userProfileRepo.requestFollowUser(user.uid, user.portrait)
            else userProfileRepo.requestUnfollowUser(user.uid, user.portrait)
            _uiState.update { it.updateConcern(user.uid, next, countChange = if (follow) 1 else -1) }
        } catch (e: CancellationException) {
            _uiState.update { it.updateConcern(user.uid, user.concernType) }
            throw e
        } catch (e: Exception) {
            _uiState.update { it.updateConcern(user.uid, user.concernType) }
            val message = e.getErrorMessage()
            sendUiEvent(if (follow) FollowListUiEvent.FollowFailed(message) else FollowListUiEvent.UnfollowFailed(message))
        }
    }

    private companion object {
        const val TAG = "FollowListViewModel"
    }
}
