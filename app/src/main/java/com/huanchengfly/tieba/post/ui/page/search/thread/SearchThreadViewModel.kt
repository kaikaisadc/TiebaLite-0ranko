package com.huanchengfly.tieba.post.ui.page.search.thread

import androidx.annotation.IntDef
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.util.fastDistinctBy
import com.huanchengfly.tieba.post.arch.BaseStateViewModel
import com.huanchengfly.tieba.post.arch.CommonUiEvent
import com.huanchengfly.tieba.post.arch.TbLiteExceptionHandler
import com.huanchengfly.tieba.post.arch.UiState
import com.huanchengfly.tieba.post.repository.SearchRepository
import com.huanchengfly.tieba.post.ui.models.search.SearchThreadInfo
import com.huanchengfly.tieba.post.utils.extension.set
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.withContext
import javax.inject.Inject

@Immutable
data class SearchThreadUiState(
    val isRefreshing: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: Throwable? = null,
    val currentPage: Int = 1,
    val hasMore: Boolean = false,
    val keyword: String = "",
    val data: List<SearchThreadInfo> = emptyList(),
    @SearchThreadSortType val sortType: Int = SearchThreadSortType.NEWEST,
) : UiState {

    val isEmpty: Boolean
        get() = data.isEmpty()
}

private const val TAG = "SearchThreadViewModel"

@Stable
@HiltViewModel
class SearchThreadViewModel @Inject constructor(
    private val searchRepo: SearchRepository
) : BaseStateViewModel<SearchThreadUiState>() {

    private var searchJob: Job? = null

    override val errorHandler = TbLiteExceptionHandler(TAG) { _, e, suppressed ->
        if (suppressed && !currentState.isEmpty) {
            _uiState.update { it.copy(isRefreshing = false, isLoadingMore = false, error = null) }
            sendUiEvent(CommonUiEvent.ToastError(e))
        } else {
            _uiState.update { it.copy(isRefreshing = false, isLoadingMore = false, error = e) }
        }
    }

    override fun createInitialState(): SearchThreadUiState = SearchThreadUiState()

    private fun searchThreadInternal(keyword: String) {
        searchJob?.cancel()
        if (keyword.isBlank()) {
            _uiState.set { SearchThreadUiState(isRefreshing = false, keyword = keyword, sortType = sortType) }
            return // on clear
        }

        val uiStateSnapshot = _uiState.updateAndGet { SearchThreadUiState(keyword = keyword, sortType = it.sortType) }
        searchJob = launchJobInVM {
            val result = runCatching { searchRepo.searchThread(keyword, page = 1, uiStateSnapshot.sortType) }
            ensureActive()
            val (hasMore, threads) = result.getOrThrow()
            _uiState.update {
                it.copy(isRefreshing = false, hasMore = hasMore, data = threads)
            }
        }
    }

    fun onLoadMore() {
        val state = currentState
        if (state.isRefreshing || state.isLoadingMore || !state.hasMore) return

        val uiStateSnapshot = _uiState.updateAndGet { it.copy(isLoadingMore = true) }
        searchJob = launchJobInVM {
            val page = uiStateSnapshot.currentPage + 1
            val result = runCatching {
                searchRepo.searchThread(uiStateSnapshot.keyword, page, uiStateSnapshot.sortType)
            }
            ensureActive()
            val (hasMore, threads) = result.getOrThrow()
            // Old threads + new threads
            val newData = withContext(Dispatchers.Default) {
                (uiStateSnapshot.data + threads).fastDistinctBy { it.lazyListKey }
            }
            _uiState.update {
                if (it === uiStateSnapshot) {
                    it.copy(isLoadingMore = false, currentPage = page, hasMore = hasMore, data = newData)
                } else {
                    it // state changed during loading, skip update
                }
            }
        }
    }

    fun onKeywordChanged(keyword: String) {
        if (currentState.keyword != keyword) searchThreadInternal(keyword) else return
    }

    fun onRefresh() {
        val uiStateSnapshot = currentState
        if (uiStateSnapshot.isRefreshing) return else searchThreadInternal(uiStateSnapshot.keyword)
    }

    fun onSortTypeChanged(@SearchThreadSortType sortType: Int) {
        if (currentState.sortType != sortType) {
            val oldState = _uiState.updateAndGet { it.copy(sortType = sortType) }
            searchThreadInternal(oldState.keyword)
        }
    }
}

@IntDef(SearchThreadSortType.NEWEST, SearchThreadSortType.OLDEST, SearchThreadSortType.RELATIVE)
@Retention(AnnotationRetention.SOURCE)
annotation class SearchThreadSortType {
    companion object {
        const val NEWEST = 5
        const val OLDEST = 0
        const val RELATIVE = 2
    }
}