package com.huanchengfly.tieba.post.ui.page.forum.searchpost

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.huanchengfly.tieba.post.arch.TbLiteExceptionHandler
import com.huanchengfly.tieba.post.arch.UiState
import com.huanchengfly.tieba.post.arch.stateInViewModel
import com.huanchengfly.tieba.post.repository.SearchRepository
import com.huanchengfly.tieba.post.ui.models.search.SearchThreadInfo
import com.huanchengfly.tieba.post.ui.page.Destination
import com.huanchengfly.tieba.post.ui.page.search.SearchResultsViewModel
import com.huanchengfly.tieba.post.ui.page.search.SearchUiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import javax.inject.Inject

/**
 * UiState for the ForumSearchPostPage
 * */
data class ForumSearchPostUiState(
    val isRefreshing: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: Throwable? = null,
    val currentPage: Int = 1,
    val hasMore: Boolean = false,
    val keyword: String = "",
    val data: List<SearchThreadInfo> = emptyList(),
    val sortType: Int = ForumSearchPostSortType.NEWEST,
    val filterType: Int = ForumSearchPostFilterType.ALL,
) : UiState {

    val isKeywordNotEmpty: Boolean = keyword.isNotBlank()
}

@HiltViewModel
class ForumSearchPostViewModel @Inject constructor(
    private val searchRepo: SearchRepository,
    savedStateHandle: SavedStateHandle
) : SearchResultsViewModel<ForumSearchPostUiState>() {

    private val params = savedStateHandle.toRoute<Destination.ForumSearchPost>()

    val forumName: String = params.forumName
    val forumId: Long = params.forumId

    override val errorHandler = TbLiteExceptionHandler(TAG) { _, e, _ ->
        _uiState.update { it.copy(isRefreshing = false, isLoadingMore = false, error = e) }
    }

    val searchHistories: StateFlow<List<String>> = searchRepo.getPostHistoryFlow(forumId)
        .catch { e ->
            errorHandler.handleException(currentCoroutineContext(), e)
        }
        .stateInViewModel(initialValue = emptyList())

    override fun createInitialState(): ForumSearchPostUiState = ForumSearchPostUiState()

    fun onClearHistory(): Unit = launchInVM {
        runCatching {
            require(searchHistories.value.isNotEmpty()) { "Empty History" }
            searchRepo.clearPostHistory(forumId)
        }
        .onFailure { e ->
            emitUiEvent(SearchUiEvent.ClearHistoryFailed(e))
        }
        .onSuccess { emitUiEvent(SearchUiEvent.ClearHistorySucceed) }
    }

    /**
     * Called when delete search history is clicked
     *
     * @param history search history
     * */
    fun onDeleteHistory(history: String): Unit = launchInVM {
        runCatching {
            searchRepo.deletePostHistory(forumId, history)
        }
        .onFailure { e -> emitUiEvent(SearchUiEvent.DeleteHistoryFailed(e)) }
    }

    private fun searchPostInternal(
        keyword: String,
        sortType: Int = currentState.sortType,
        filterType: Int = currentState.filterType,
    ) {
        val state = ForumSearchPostUiState(
            keyword = keyword,
            sortType = sortType,
            filterType = filterType,
            isRefreshing = keyword.isNotBlank(),
        )
        _uiState.value = state
        if (state.isRefreshing) {
            loadPage(state, page = 1)
        } else {
            cancelSearch()
        }
    }

    private fun loadPage(request: ForumSearchPostUiState, page: Int) {
        launchSearch(
            load = {
                searchRepo.searchPost(
                    keyword = request.keyword,
                    forumName = forumName,
                    forumId = forumId,
                    sortType = request.sortType,
                    filterType = request.filterType,
                    page = page,
                )
            },
        ) { (hasMore, items) ->
            _uiState.update { state ->
                if (page > 1 && state !== request) state else state.copy(
                    isRefreshing = false,
                    isLoadingMore = false,
                    currentPage = page,
                    hasMore = hasMore,
                    data = if (page == 1) items else request.data + items,
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

    fun onRefresh() {
        val state = currentState
        if (!state.isRefreshing) searchPostInternal(state.keyword)
    }

    fun onSubmitKeyword(keyword: String) {
        if (keyword == currentState.keyword) return
        if (keyword.isNotBlank()) {
            launchInVM {
                searchRepo.addPostHistory(forumId, keyword)
            }
        }
        searchPostInternal(keyword)
    }

    fun onFilterTypeChanged(filterType: Int) {
        val state = currentState
        if (state.filterType != filterType) {
            searchPostInternal(keyword = state.keyword, filterType = filterType)
        }
    }

    fun onSortTypeChanged(sortType: Int) {
        val state = currentState
        if (state.sortType != sortType) {
            searchPostInternal(keyword = state.keyword, sortType = sortType)
        }
    }
}

private const val TAG = "ForumSearchPostViewModel"

object ForumSearchPostSortType {
    const val NEWEST = 1
    const val RELATIVE = 2
}

object ForumSearchPostFilterType {
    const val ONLY_THREAD = 1
    const val ALL = 2
}
