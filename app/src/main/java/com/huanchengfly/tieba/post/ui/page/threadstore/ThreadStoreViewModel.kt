package com.huanchengfly.tieba.post.ui.page.threadstore

import androidx.compose.runtime.Immutable
import com.huanchengfly.tieba.post.api.retrofit.exception.getErrorMessage
import com.huanchengfly.tieba.post.arch.BaseStateViewModel
import com.huanchengfly.tieba.post.arch.CommonUiEvent
import com.huanchengfly.tieba.post.arch.TbLiteExceptionHandler
import com.huanchengfly.tieba.post.arch.UiState
import com.huanchengfly.tieba.post.repository.ThreadStoreRepository
import com.huanchengfly.tieba.post.ui.models.ThreadStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@Immutable
data class ThreadStoreUiState(
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    /** 下一页页码, 0 基([ThreadStoreRepository.load] 的 offset = pageSize * page), 首屏加载 page 0 */
    val currentPage: Int = 0,
    val data: List<ThreadStore> = emptyList(),
    val error: Throwable? = null
) : UiState {

    val isEmpty: Boolean
        get() = data.isEmpty()
}

@HiltViewModel
class ThreadStoreViewModel @Inject constructor(
    private val threadStoreRepo: ThreadStoreRepository
) : BaseStateViewModel<ThreadStoreUiState>() {

    override val errorHandler = TbLiteExceptionHandler(TAG) { context, e, suppressed ->
        if (suppressed && !currentState.isEmpty) {
            _uiState.update { it.copy(isRefreshing = false, isLoadingMore = false, error = null) }
            sendUiEvent(CommonUiEvent.ToastError(e))
        } else {
            _uiState.update { it.copy(isRefreshing = false, isLoadingMore = false, error = e) }
        }
    }

    private val requests = Mutex()
    private var loadJob: Job? = null

    init {
        onRefresh()
    }

    override fun createInitialState(): ThreadStoreUiState = ThreadStoreUiState()

    /**
     * 收藏列表滚动位置, 用于从帖子页返回时恢复 (不进 UiState, 仅页面重组恢复用)。
     * 刷新重置数据时归零。
     */
    var listRestoreIndex: Int = 0
        private set

    var listRestoreOffset: Int = 0
        private set

    fun saveListPosition(index: Int, offset: Int) {
        listRestoreIndex = index
        listRestoreOffset = offset
    }

    fun onRefresh() {
        if (currentState.isRefreshing) return
        loadJob?.cancel()
        _uiState.update { it.copy(isRefreshing = true, isLoadingMore = false, error = null) }
        loadJob = launchJobInVM {
            requests.withLock {
                val data = threadStoreRepo.load()
                saveListPosition(0, 0)
                _uiState.update { ThreadStoreUiState(data = data, hasMore = data.hasMore) }
            }
        }
    }

    fun onLoadMore() {
        val state = currentState
        if (state.isRefreshing || state.isLoadingMore || !state.hasMore) return
        _uiState.update { it.copy(isLoadingMore = true, error = null) }
        loadJob = launchJobInVM {
            requests.withLock {
                val nextPage = currentState.currentPage + 1
                val data = threadStoreRepo.load(page = nextPage)
                _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        currentPage = nextPage,
                        data = (it.data + data).distinctBy(ThreadStore::id),
                        hasMore = data.hasMore,
                    )
                }
            }
        }
    }

    fun onDelete(thread: ThreadStore) = launchInVM {
        val result = requests.withLock {
            if (currentState.data.none { it.id == thread.id }) return@launchInVM
            threadStoreRepo.remove(thread).onSuccess {
                applyThreadResult(thread.id, null)
            }
        }
        result.fold(
            onSuccess = { emitUiEvent(ThreadStoreUiEvent.Delete.Success) },
            onFailure = { emitUiEvent(ThreadStoreUiEvent.Delete.Failure(it.getErrorMessage())) },
        )
    }

    fun onThreadResult(threadId: Long, markedPostId: Long?) = launchInVM {
        requests.withLock { applyThreadResult(threadId, markedPostId) }
    }

    private fun applyThreadResult(threadId: Long, markedPostId: Long?) {
        _uiState.update { state ->
            state.copy(data = state.data.mapNotNull { thread ->
                if (thread.id == threadId) {
                    markedPostId?.let { thread.copy(markPid = it) }
                } else {
                    thread
                }
            })
        }
    }

    companion object {

        private const val TAG = "ThreadStoreViewModel"

        private val List<ThreadStore>.hasMore: Boolean
            get() = this.size == ThreadStoreRepository.LOAD_LIMIT // Result reached limit
    }
}
