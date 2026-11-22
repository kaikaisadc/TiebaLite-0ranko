package com.huanchengfly.tieba.post.ui.page.search

import android.util.Log
import androidx.collection.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import com.huanchengfly.tieba.post.api.retrofit.exception.getErrorMessage
import com.huanchengfly.tieba.post.arch.BaseStateViewModel
import com.huanchengfly.tieba.post.arch.TbLiteExceptionHandler
import com.huanchengfly.tieba.post.arch.UiState
import com.huanchengfly.tieba.post.arch.stateInViewModel
import com.huanchengfly.tieba.post.repository.SearchRepository
import com.huanchengfly.tieba.post.ui.models.search.SearchSuggestion
import com.huanchengfly.tieba.post.ui.page.search.thread.SearchThreadSortType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import javax.inject.Inject

@Immutable
data class SearchUiState(
    val submittedKeyword: String = "",
    @SearchThreadSortType val sortType: Int = SearchThreadSortType.NEWEST,
    val suggestionKeyword: String = "",
    val suggestion: SearchSuggestion? = null,
) : UiState {

    val isKeywordNotEmpty: Boolean = submittedKeyword.isNotEmpty()
}

private const val TAG = "SearchViewModel"

@Stable
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepo: SearchRepository
): BaseStateViewModel<SearchUiState>() {

    /**
     * In-Memory cache of recent search suggestion
     * */
    private val cache: LruCache<String, SearchSuggestion> = LruCache(10)

    private var suggestionJob: Job? = null

    private val emptySuggestion = SearchSuggestion(null, emptyList())

    override val errorHandler = TbLiteExceptionHandler(TAG) { _, e, _ ->
        if (viewModelScope.isActive) {
            sendUiEvent(SearchUiEvent.Error(e))
        }
    }

    val searchHistories: StateFlow<List<String>> = searchRepo.getHistoryFlow()
        .catch { e -> errorHandler.handleException(currentCoroutineContext(), e) }
        .stateInViewModel(initialValue = emptyList())

    override fun createInitialState(): SearchUiState = SearchUiState()

    fun onClearHistory(): Unit = launchInVM {
        runCatching {
            require(searchHistories.value.isNotEmpty()) { "Empty History" }
            searchRepo.clearHistory()
        }
        .onFailure { e ->
            emitUiEvent(SearchUiEvent.ClearHistoryFailed(e))
        }
        .onSuccess { emitUiEvent(SearchUiEvent.ClearHistorySucceed) }
    }

    /**
     * Called on delete search history is clicked.
     * */
    fun onDeleteHistory(history: String): Unit = launchInVM {
        runCatching {
            searchRepo.deleteHistory(history)
        }
        .onFailure { e -> emitUiEvent(SearchUiEvent.DeleteHistoryFailed(e)) }
    }

    /**
     * Called when the input text in the search box has been changed.
     * */
    fun onKeywordInputChanged(keyword: String) {
        suggestionJob?.cancel()
        val showSuggestions = keyword.isNotBlank() && keyword != currentState.submittedKeyword
        val cached = if (showSuggestions) cache[keyword] else null
        _uiState.update { it.copy(suggestionKeyword = keyword, suggestion = cached) }
        if (!showSuggestions || cached != null) return

        suggestionJob = launchJobInVM {
            delay(200)
            val suggestion = try {
                searchRepo.searchSuggestions(keyword)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "onKeywordInputChanged: ${e.getErrorMessage()}")
                return@launchJobInVM
            }
            if (!isActive) return@launchJobInVM
            cacheSuggestion(keyword, suggestion)
            _uiState.update {
                if (it.suggestionKeyword == keyword) it.copy(suggestion = suggestion) else it
            }
        }
    }

    fun onSubmitKeyword(keyword: String) {
        suggestionJob?.cancel()
        val previous = currentState.submittedKeyword
        _uiState.update { it.copy(submittedKeyword = keyword, suggestionKeyword = "", suggestion = null) }
        if (keyword.isNotEmpty() && keyword != previous) launchInVM { searchRepo.addHistory(keyword) }
    }

    fun onSortTypeChanged(@SearchThreadSortType sortType: Int): Unit = launchInVM {
        _uiState.update { it.copy(sortType = sortType) }
    }

    private fun cacheSuggestion(keyword: String, suggestion: SearchSuggestion) {
        val isEmpty = suggestion.forum == null && suggestion.suggestions.isEmpty()
        // replace with empty obj if result is empty
        cache.put(keyword, if (isEmpty) emptySuggestion else suggestion)
    }

    override fun onCleared() {
        super.onCleared()
        suggestionJob?.cancel()
        cache.evictAll()
    }
}
