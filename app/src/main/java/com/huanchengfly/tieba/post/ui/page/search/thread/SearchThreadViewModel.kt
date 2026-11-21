package com.huanchengfly.tieba.post.ui.page.search.thread

import androidx.annotation.IntDef
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.util.fastDistinctBy
import com.huanchengfly.tieba.post.arch.CommonUiEvent
import com.huanchengfly.tieba.post.arch.TbLiteExceptionHandler
import com.huanchengfly.tieba.post.arch.UiState
import com.huanchengfly.tieba.post.repository.SearchRepository
import com.huanchengfly.tieba.post.ui.models.search.SearchThreadInfo
import com.huanchengfly.tieba.post.ui.page.search.SearchResultsViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
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
) : SearchResultsViewModel<SearchThreadUiState>() {

    override val errorHandler = TbLiteExceptionHandler(TAG) { _, e, suppressed ->
        if (suppressed && !currentState.isEmpty) {
            _uiState.update { it.copy(isRefreshing = false, isLoadingMore = false, error = null) }
            sendUiEvent(CommonUiEvent.ToastError(e))
        } else {
            _uiState.update { it.copy(isRefreshing = false, isLoadingMore = false, error = e) }
        }
    }

    override fun createInitialState(): SearchThreadUiState = SearchThreadUiState()

    private fun searchThreadInternal(
        keyword: String,
        @SearchThreadSortType sortType: Int = currentState.sortType,
    ) {
        val state = SearchThreadUiState(
            keyword = keyword,
            sortType = sortType,
            isRefreshing = keyword.isNotBlank(),
        )
        _uiState.value = state
        if (state.isRefreshing) {
            loadPage(state, page = 1)
        } else {
            cancelSearch()
        }
    }

    private fun loadPage(request: SearchThreadUiState, page: Int) {
        launchSearch(
            load = {
                val result = searchRepo.searchThread(request.keyword, page, request.sortType)
                if (page == 1) result else withContext(Dispatchers.Default) {
                    result.copy(items = (request.data + result.items).fastDistinctBy { it.lazyListKey })
                }
            },
        ) { (hasMore, items) ->
            _uiState.update { state ->
                if (page > 1 && state !== request) state else state.copy(
                    isRefreshing = false,
                    isLoadingMore = false,
                    currentPage = page,
                    hasMore = hasMore,
                    data = items,
                )
            }
        }
    }

    fun onLoadMore() {
        val state = currentState
        if (state.isRefreshing || state.isLoadingMore || !state.hasMore) return

        val request = _uiState.updateAndGet { it.copy(isLoadingMore = true) }
        loadPage(request, page = request.currentPage + 1)
    }

    fun onKeywordChanged(keyword: String) {
        if (currentState.keyword != keyword) searchThreadInternal(keyword)
    }

    fun onRefresh() {
        val state = currentState
        if (!state.isRefreshing) searchThreadInternal(state.keyword)
    }

    fun onSortTypeChanged(@SearchThreadSortType sortType: Int) {
        val state = currentState
        if (state.sortType != sortType) searchThreadInternal(state.keyword, sortType)
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
