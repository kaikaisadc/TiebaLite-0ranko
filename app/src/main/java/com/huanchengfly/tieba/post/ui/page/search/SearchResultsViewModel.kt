package com.huanchengfly.tieba.post.ui.page.search

import androidx.annotation.MainThread
import androidx.lifecycle.viewModelScope
import com.huanchengfly.tieba.post.arch.BaseStateViewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

abstract class SearchResultsViewModel<State> : BaseStateViewModel<State>() {

    private var searchJob: Job? = null

    @MainThread
    protected fun cancelSearch() {
        searchJob?.cancel()
        searchJob = null
    }

    @MainThread
    protected fun <T> launchSearch(
        load: suspend () -> T,
        onSuccess: (T) -> Unit,
    ) {
        cancelSearch()
        val job = viewModelScope.launch(errorHandler, start = CoroutineStart.LAZY) {
            val result = runCatching { load() }
            // An outdated request must deliver neither results nor errors.
            ensureActive()
            onSuccess(result.getOrThrow())
        }
        searchJob = job
        job.start()
    }
}
