package com.huanchengfly.tieba.post.ui.page.search

import androidx.compose.runtime.Immutable
import com.huanchengfly.tieba.post.arch.CommonUiEvent
import com.huanchengfly.tieba.post.arch.TbLiteExceptionHandler
import com.huanchengfly.tieba.post.arch.UiState
import com.huanchengfly.tieba.post.repository.SearchResult
import kotlinx.coroutines.flow.update

@Immutable
data class BaseSearchUiState<T>(
    val keyword: String = "",
    val exactMatch: T? = null,
    val fuzzyMatch: List<T> = emptyList(),
    val isRefreshing: Boolean = true,
    val error: Throwable? = null,
) : UiState {

    val isEmpty: Boolean
        get() = exactMatch == null && fuzzyMatch.isEmpty()
}

abstract class SearchBaseViewModel<T> : SearchResultsViewModel<BaseSearchUiState<T>>() {

    override val errorHandler = TbLiteExceptionHandler(this::class.java.simpleName) { _, e, suppressed ->
        if (suppressed && !currentState.isEmpty) {
            _uiState.update { it.copy(isRefreshing = false, error = null) }
            sendUiEvent(CommonUiEvent.ToastError(e))
        } else {
            _uiState.update { it.copy(isRefreshing = false, error = e) }
        }
    }

    override fun createInitialState(): BaseSearchUiState<T> = BaseSearchUiState()

    protected abstract suspend fun search(keyword: String): SearchResult<T>

    private fun searchInternal(keyword: String) {
        val isRefreshing = keyword.isNotBlank()
        _uiState.value = BaseSearchUiState(keyword, isRefreshing = isRefreshing)
        if (!isRefreshing) {
            cancelSearch()
            return
        }

        launchSearch(load = { search(keyword) }) { (exactMatch, fuzzyMatch) ->
            _uiState.update {
                it.copy(exactMatch = exactMatch, fuzzyMatch = fuzzyMatch, isRefreshing = false)
            }
        }
    }

    fun onKeywordChanged(keyword: String) {
        if (currentState.keyword != keyword) searchInternal(keyword)
    }

    fun onRefresh() {
        val state = currentState
        if (!state.isRefreshing) searchInternal(state.keyword)
    }
}
