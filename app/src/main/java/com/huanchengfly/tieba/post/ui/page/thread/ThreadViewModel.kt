package com.huanchengfly.tieba.post.ui.page.thread

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastMap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.api.Error
import com.huanchengfly.tieba.post.api.TiebaApi
import com.huanchengfly.tieba.post.api.booleanToString
import com.huanchengfly.tieba.post.api.retrofit.exception.getErrorCode
import com.huanchengfly.tieba.post.api.retrofit.exception.getErrorMessage
import com.huanchengfly.tieba.post.arch.BaseStateViewModel
import com.huanchengfly.tieba.post.arch.CommonUiEvent
import com.huanchengfly.tieba.post.arch.UiEvent
import com.huanchengfly.tieba.post.components.ClipBoardLinkDetector
import com.huanchengfly.tieba.post.models.PhotoViewData
import com.huanchengfly.tieba.post.models.database.ThreadHistory
import com.huanchengfly.tieba.post.repository.HistoryRepository
import com.huanchengfly.tieba.post.repository.PageData
import com.huanchengfly.tieba.post.repository.PbPageRepository
import com.huanchengfly.tieba.post.repository.PbPageUiResponse
import com.huanchengfly.tieba.post.repository.ThreadStoreRepository
import com.huanchengfly.tieba.post.repository.user.SettingsRepository
import com.huanchengfly.tieba.post.ui.models.PostData
import com.huanchengfly.tieba.post.ui.models.SubPostItemData
import com.huanchengfly.tieba.post.ui.page.Destination
import com.huanchengfly.tieba.post.ui.page.Destination.Companion.navTypeOf
import com.huanchengfly.tieba.post.ui.page.Destination.Reply
import com.huanchengfly.tieba.post.ui.page.Destination.SubPosts
import com.huanchengfly.tieba.post.ui.page.threadstore.ThreadStoreUiEvent
import com.huanchengfly.tieba.post.utils.TiebaUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.reflect.typeOf

@Stable
@HiltViewModel
class ThreadViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    private val historyRepo: HistoryRepository,
    private val storeRepo: ThreadStoreRepository,
    private val threadRepo: PbPageRepository,
    settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle
) : BaseStateViewModel<ThreadUiState>() {

    private val params = savedStateHandle.toRoute<Destination.Thread>(
        typeMap = mapOf(typeOf<ThreadFrom?>() to navTypeOf<ThreadFrom?>(isNullableAllowed = true))
    )

    private val threadId: Long = params.threadId
    private val postId: Long = params.postId
    private val historyTimeStamp = System.currentTimeMillis()

    private var from: String = params.from?.tag ?: ""

    /**
     * Post or Thread(FirstPost) marked for deletion.
     *
     * @see onDeletePost
     * @see onDeleteThread
     * */
    private val _deletePost: MutableStateFlow<PostData?> = MutableStateFlow(null)
    val deletePost: StateFlow<PostData?> = _deletePost.asStateFlow()

    var isImmersiveMode by mutableStateOf(false)
        private set

    var hideReply by mutableStateOf(false)
        private set

    suspend fun loadSubPostPhotoData(
        post: PostData,
        subPost: SubPostItemData,
        photoIndex: Int,
    ): PhotoViewData? {
        val thread = currentState.thread ?: return null
        val photos = threadRepo.getSubPostPhotos(
            threadId = thread.id,
            postId = post.id,
            forumId = thread.simpleForum.first,
            subPostId = subPost.id,
        ) ?: return null

        return photos.getOrNull(photoIndex)?.photoViewData
    }

    /**
     * Job of Add/Update/Remove thread collections, cancelable.
     *
     * @see updateCollections
     * @see removeFromCollections
     * */
    private var collectionsJob: Job? = null

    private var pageLoadJob: Job? = null
    private var pendingReplyPostId: Long? = null

    private val reloadState: ThreadLoadState.Running
        get() = if (currentState.thread == null) {
            ThreadLoadState.Running.InitialLoading
        } else {
            ThreadLoadState.Running.Refreshing
        }

    private fun startPageLoad(
        operation: ThreadLoadState.Running,
        replace: Boolean = false,
        load: suspend (ThreadUiState) -> Unit,
    ) {
        val snapshot = currentState
        val previous = snapshot.loadState
        if (previous is ThreadLoadState.Running) {
            if (!replace) return
            if (previous is ThreadLoadState.Running.LoadingReply && pendingReplyPostId == null) {
                pendingReplyPostId = previous.postId
            }
            pageLoadJob?.cancel()
        }
        _uiState.update { it.copy(loadState = operation) }

        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                load(snapshot)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ensureActive()
                onPageLoadFailed(operation, e)
            } finally {
                if (isActive) loadPendingReply()
            }
        }
        pageLoadJob = job
        job.start()
    }

    private suspend fun finishPageLoad(
        event: ThreadUiEvent? = null,
        update: (ThreadUiState) -> ThreadUiState,
    ) {
        currentCoroutineContext().ensureActive()
        _uiState.update { update(it).copy(loadState = ThreadLoadState.Idle) }
        event?.let(::sendUiEvent)
    }

    private fun onPageLoadFailed(operation: ThreadLoadState.Running, error: Exception) {
        val incremental = operation == ThreadLoadState.Running.LoadingMore ||
                operation is ThreadLoadState.Running.LoadingReply
        val noMore = incremental && error.getErrorCode() == Error.ERROR_POST_NOMORE
        val showOnPage = !noMore && (!incremental || currentState.thread == null)
        _uiState.update { it.copy(loadState = ThreadLoadState.Failed(error, showOnPage)) }
        if (noMore) {
            sendUiEvent(CommonUiEvent.Toast(context.getString(R.string.no_more)))
        } else {
            Log.w(TAG, "Unable to load thread", error)
            if (!showOnPage) sendUiEvent(CommonUiEvent.ToastError(error))
        }
    }

    private val firstPostId: Long
        get() = currentState.firstPost?.id ?: 0L

    private val forumId: Long?
        get() = params.forumId ?: currentState.forum?.first

    private val forumName: String?
        get() = currentState.forum?.second

    override fun createInitialState(): ThreadUiState {
        return ThreadUiState(seeLz = params.seeLz, sortType = params.sortType)
    }

    init {
        requestLoad(page = 0, postId = postId, scrollToReply = params.scrollToReply)
        viewModelScope.launch {
            hideReply = settingsRepository.habitSettings.snapshot().hideReply
        }
    }

    fun requestLoad(page: Int = 1, postId: Long, scrollToReply: Boolean = true) {
        startPageLoad(reloadState, replace = true) { oldState ->
            val sortType = oldState.sortType
            val fromType = from.takeIf { it == FROM_STORE }.orEmpty()
            val response = threadRepo
                .pbPage(threadId, page, postId, forumId, oldState.seeLz, sortType, from = fromType)
            val pageData = response.page.let {
                it.toThreadPageData(
                    sortType = sortType,
                    previous = it.current_page,
                    nextPagePostId = response.nextPagePostId,
                    hasPrevious = if (sortType != ThreadSortType.BY_DESC) {
                        it.has_prev != 0 // Bug: Server returns wrong has_prev when FROM_STORE with seeLz enabled
                    } else {
                        // Check has previous manually if sort by DESC
                        it.total_page > 1 && it.current_page < it.total_page
                    }
                )
            }
            val event = if (scrollToReply) ThreadUiEvent.LoadSuccess(page, postId) else null
            finishPageLoad(event) { it.updateStateFrom(response).copy(pageData = pageData) }
        }
    }

    fun requestLoadFirstPage() {
        startPageLoad(reloadState, replace = true) { oldState ->
            val sortType = oldState.sortType
            val isAscSorting = sortType == ThreadSortType.BY_ASC
            val response = threadRepo.pbPage(threadId, 0, 0, forumId, oldState.seeLz, sortType)
            val pageData = response.page.run {
                toThreadPageData(
                    sortType = sortType,
                    previous = total_page,
                    current = if (isAscSorting) current_page else total_page,
                    nextPagePostId = if (isAscSorting) {
                        response.nextPagePostId
                    } else {
                        response.posts.lastOrNull()?.id ?: 0
                    }
                )
            }
            val event = if (isAscSorting) {
                ThreadUiEvent.ScrollToFirstReply
            } else {
                ThreadUiEvent.ScrollToLatestReply
            }
            finishPageLoad(event) {
                it.updateStateFrom(response).copy(firstPost = it.firstPost, pageData = pageData)
            }
        }
    }

    fun requestLoad(page: Int) {
        val state = currentState
        // Check target page is first page
        if ((page <= 1 && state.sortType == ThreadSortType.BY_ASC) ||
            (page == state.pageData.total && state.sortType == ThreadSortType.BY_DESC)
        ) {
            requestLoadFirstPage()
        } else {
            requestLoad(page, postId = 0)
        }
    }

    /**
     * Load previous page.
     *
     * @param offset offset of the first visible post. Will be used as backward scroll offset
     *   in [ThreadUiEvent.LoadPreviousSuccess]. This is a workaround for broken scroll
     *   position preservation caused by LoadPreviousButton.
     *
     * @see [PageData.hasPrevious]
     * */
    fun requestLoadPrevious(offset: Int) {
        val firstPost = currentState.data.firstOrNull() ?: return
        startPageLoad(ThreadLoadState.Running.LoadingMore) { state ->
            val sortType = state.sortType
            val page = state.pageData.previousPage(sortType)
            val postId = firstPost.id
            val response = threadRepo
                .pbPage(threadId, page, postId, forumId, state.seeLz, sortType, back = true)
            val newData = mergeThreadPosts(old = state.data, new = response.posts, prepend = true)
            val pageData = response.page.toThreadPageData(
                sortType = sortType,
                previous = response.page.current_page,
                current = state.pageData.current,
                hasMore = state.pageData.hasMore
            )

            // Scroll to previous floor
            val previousIndex = withContext(Dispatchers.Default) {
                newData.indexOfFirst { p -> p.floor == firstPost.floor }
            }
            val event = if (offset > 0 && previousIndex > 0) {
                ThreadUiEvent.LoadPreviousSuccess(previousIndex, -offset)
            } else null
            finishPageLoad(event) {
                it.copy(thread = response.thread, data = newData, pageData = pageData)
            }
        }
    }

    fun requestLoadMore() {
        startPageLoad(ThreadLoadState.Running.LoadingMore) { state ->
            val sortType = state.sortType
            val nextPage = state.pageData.nextPage(sortType)
            val response = threadRepo
                .pbPage(threadId, nextPage, state.pageData.nextPagePostId, forumId, state.seeLz, sortType)
            val newData = mergeThreadPosts(old = state.data, new = response.posts)
            val pageData = response.page.toThreadPageData(
                sortType = sortType,
                previous = state.pageData.previous,
                nextPagePostId = response.nextPagePostId,
                hasPrevious = state.pageData.hasPrevious
            )

            finishPageLoad { it.updateStateFrom(response).copy(data = newData, pageData = pageData) }
        }
    }

    /**
     * 加载当前贴子的最新回复
     */
    fun requestLoadLatestPosts() {
        val latestPostId = currentState.data.lastOrNull()?.id ?: return
        startPageLoad(ThreadLoadState.Running.LoadingMore) { state ->
            val response = threadRepo.pbPage(
                threadId = threadId,
                page = 0,
                postId = latestPostId,
                forumId = forumId,
                seeLz = state.seeLz,
                sortType = state.sortType,
                lastPostId = latestPostId,
            )
            val data = mergeThreadPosts(state.data, response.posts)
            val pageData = response.page.toThreadPageData(
                sortType = state.sortType,
                previous = state.pageData.previous,
                nextPagePostId = response.nextPagePostId,
                hasPrevious = state.pageData.hasPrevious,
            )
            finishPageLoad {
                it.copy(data = data, thread = response.thread, latestPosts = null, pageData = pageData)
            }
        }
    }

    /** 当前用户发送新的回复时，加载用户发送的回复。 */
    fun requestLoadMyLatestReply(newPostId: Long) {
        pendingReplyPostId = newPostId
        loadPendingReply()
    }

    private fun loadPendingReply() {
        val postId = pendingReplyPostId ?: return
        val state = currentState
        if (state.loadState is ThreadLoadState.Running || state.thread == null || state.error != null) return
        pendingReplyPostId = null
        startPageLoad(ThreadLoadState.Running.LoadingReply(postId)) { loaded ->
            val response = threadRepo.pbPage(threadId, page = 0, postId = postId, forumId = forumId)
            val replies = withContext(Dispatchers.Default) {
                loaded.mergeLatestReplies(response.posts, response.page.current_page)
            }
            val event = ThreadUiEvent.ScrollToLatestReply.takeIf { replies.hasNewPosts }
            finishPageLoad(event) {
                it.copy(tbs = response.tbs, data = replies.posts, latestPosts = replies.pendingPosts)
            }
        }
    }

    /**
     * 收藏/更新这个帖子到 [markedPost] 楼
     * */
    fun updateCollections(markedPost: PostData) {
        collectionsJob?.let { if (it.isActive) it.cancel() }
        // Launch in different CoroutineScope
        collectionsJob = MainScope().launch {
            storeRepo.add(threadId, postId = markedPost.id)
                .onFailure { e ->
                    emitUiEvent(ThreadStoreUiEvent.Add.Failure(message = e.getErrorMessage()))
                }
                .onSuccess {
                    _uiState.update {
                        it.copy(thread = it.thread!!.copy(collectMarkPid = markedPost.id))
                    }
                    emitUiEvent(ThreadStoreUiEvent.Add.Success(markedPost.floor))
                }
        }
    }

    /**
     * 取消收藏这个帖子
     * */
    fun removeFromCollections() {
        if (collectionsJob?.isActive == true) {
            sendUiEvent(ThreadStoreUiEvent.Loading)
            return
        }

        collectionsJob = launchJobInVM {
            val state = _uiState.first()
            runCatching {
                require(state.thread!!.collected)
                storeRepo.remove(threadId, forumId = forumId, tbs = state.tbs)
            }
            .onFailure { e ->
                emitUiEvent(ThreadStoreUiEvent.Delete.Failure(message = e.getErrorMessage()))
            }
            .onSuccess {
                _uiState.update { it.copy(thread = it.thread!!.copy(collectMarkPid = null)) }
                emitUiEvent(ThreadStoreUiEvent.Delete.Success)
            }
        }
    }

    fun onPostLikeClicked(post: PostData) {
        if (currentState.user == null) {
            sendUiEvent(ThreadLikeUiEvent.NotLoggedIn); return
        } else if (post.like.loading) {
            sendUiEvent(ThreadLikeUiEvent.Connecting); return
        }

        viewModelScope.launch {
            val start = System.currentTimeMillis()
            val liked = post.like.liked
            val opType = if (liked) 1 else 0 // 操作 0 = 点赞, 1 = 取消点赞

            TiebaApi.getInstance()
                .opAgreeFlow(threadId.toString(), post.id.toString(), opType, objType = 1)
                .onStart {
                    _uiState.update { it.updateLikedPost(post.id, !liked, loading = true) }
                }
                .catch { e ->
                    sendUiEvent(ThreadLikeUiEvent.Failed(e))
                    _uiState.update { it.updateLikedPost(post.id, liked, loading = false) }
                }
                .collect {
                    if (System.currentTimeMillis() - start < 400) { // Wait for button animation
                        delay(250)
                    }
                    _uiState.update { it.updateLikedPost(post.id, !liked, loading = false) }
                }
        }
    }

    fun onThreadLikeClicked(): Unit = launchInVM {
        val stateSnapshot = currentState
        val oldThread = stateSnapshot.thread ?: throw NullPointerException()
        val like = oldThread.like

        // check user logged in & requesting like status update
        if (stateSnapshot.user == null) {
            emitUiEvent(ThreadLikeUiEvent.NotLoggedIn); return@launchInVM
        } else if (like.loading) {
            emitUiEvent(ThreadLikeUiEvent.Connecting); return@launchInVM
        }

        _uiState.update { it.copy(thread = oldThread.updateLikeStatus(liked = !like.liked, loading = true)) }
        runCatching {
            threadRepo.requestLikeThread(oldThread)
        }
        .onFailure { e ->
            sendUiEvent(ThreadLikeUiEvent.Failed(e))
            _uiState.update {
                it.copy(thread = it.thread!!.updateLikeStatus(liked = like.liked, loading = false))
            }
        }
        .onSuccess { _ ->
            _uiState.update { // Update like loading status
                it.copy(thread = it.thread!!.updateLikeStatus(liked = !like.liked, loading = false))
            }
        }
    }

    fun onDeleteConfirmed(): Job = launchJobInVM {
        val post = _deletePost.getAndUpdate { null } ?: throw NullPointerException()
        if (post.id == currentState.firstPost!!.id) {
            requestDeleteThread()
        } else {
            requestDeletePost(post)
        }
    }

    /**
     * Mark my post for deletion
     *
     * @see onDeleteConfirmed
     * */
    fun onDeletePost(post: PostData) = _deletePost.update { post }

    /**
     * Mark my thread for deletion
     *
     * @see onDeleteConfirmed
     * */
    fun onDeleteThread() = _deletePost.update { currentState.firstPost }

    fun onDeleteCancelled() = _deletePost.update { null }

    private suspend fun requestDeletePost(post: PostData) {
        val state = _uiState.first()
        val delMyPost = post.author.id == state.user?.id
        runCatching {
            threadRepo.deletePost(post.id, state.thread!!, state.tbs, delMyPost)
        }
        .onFailure { e -> sendUiEvent(ThreadUiEvent.DeletePostFailed(message = e.getErrorMessage())) }
        .onSuccess {
            // Remove this post from data list
            _uiState.update { it.copy(data = it.data.fastFilter { p -> p.id != post.id }) }
            sendUiEvent(ThreadUiEvent.DeletePostSuccess)
        }
    }

    private suspend fun requestDeleteThread() {
        val state = currentState
        val delMyThread = state.lz!!.id == state.user?.id
        runCatching {
            threadRepo.deleteThread(state.thread!!, state.tbs, delMyThread)
        }
        .onFailure { e -> sendUiEvent(ThreadUiEvent.DeletePostFailed(message = e.getErrorMessage())) }
        .onSuccess {
            sendUiEvent(CommonUiEvent.NavigateUp)
        }
    }

    fun requestPollPost(options: List<Int>) = launchInVM {
        val thread = currentState.thread!!
        if (thread.pollInfo!!.isLoading) return@launchInVM

        _uiState.update { it.copy(thread = thread.updatePollStatus(loading = true)) }
        runCatching {
            threadRepo.requestPollPost(forumId, threadId, options)
        }
        .onFailure { e ->
            sendUiEvent(CommonUiEvent.ToastError(e))
            _uiState.update { it.copy(thread = thread.updatePollStatus(loading = false)) }
        }
        .onSuccess { pollInfo ->
            _uiState.update { it.copy(thread = thread.copy(pollInfo = pollInfo)) }
        }
    }

    fun onSeeLzChanged() {
        val newState = _uiState.updateAndGet { it.copy(seeLz = !it.seeLz) }
        val collectMarkPid = newState.thread?.collectMarkPid
        // Jump to collectMarkPid when seeLz switched off
        if (!newState.seeLz && collectMarkPid != null && collectMarkPid != newState.firstPost?.id) {
            requestLoad(0, postId = newState.thread.collectMarkPid)
        } else {
            requestLoadFirstPage()
        }
    }

    fun onSortChanged(@ThreadSortType sortType: Int) {
        _uiState.update { it.copy(sortType = sortType) }
        requestLoadFirstPage()
    }

    fun onSaveHistory(lastVisiblePost: PostData?) = launchInVM {
        val state = currentState
        val author = state.lz ?: return@launchInVM
        val title = state.thread?.title ?: return@launchInVM

        val history = ThreadHistory(
            id = threadId,
            avatar = author.avatarUrl,
            name = author.nameShow,
            forum = forumName,
            title = title,
            isSeeLz = state.seeLz,
            pid = lastVisiblePost?.takeIf { it.id > 0 && it.floor > 5 }?.id ?: 0, // 大于 5 楼
            timestamp = historyTimeStamp,
        )
        historyRepo.saveHistory(history)
    }

    fun onShareThread() = TiebaUtil.shareThread(context, currentState.thread?.title?: "", threadId)

    fun onCopyThreadLink() {
        val seeLz = currentState.seeLz
        val link = "https://tieba.baidu.com/p/$threadId?see_lz=${seeLz.booleanToString()}"
        TiebaUtil.copyText(context = context, text = link)
        ClipBoardLinkDetector.onCopyTiebaLink(link)
    }

    fun onImmersiveModeChanged() {
        if (!isImmersiveMode && !currentState.seeLz) {
            onSeeLzChanged()
        }
        isImmersiveMode = !isImmersiveMode
    }

    fun onReplyThread() = sendUiEvent(
        event = ThreadUiEvent.ToReplyDestination(
            Reply(forumId = forumId ?: 0, forumName = forumName ?: "", threadId = threadId)
        )
    )

    fun onReplyPost(post: PostData) = sendUiEvent(
        event = ThreadUiEvent.ToReplyDestination(
            Reply(
                forumId = forumId ?: 0,
                forumName = forumName.orEmpty(),
                threadId = threadId,
                postId = post.id,
                replyUserId = post.author.id,
                replyUserName = post.author.nameShow.takeIf { name -> name.isNotEmpty() } ?: post.author.name,
                replyUserPortrait = post.author.portrait
            )
        )
    )

    fun onReplyClicked(post: PostData) {
        if (post.id == firstPostId) {
            onReplyThread()
        } else {
            onReplyPost(post)
        }
    }

    fun onReplySubPost(post: PostData, subPost: SubPostItemData) = sendUiEvent(
        ThreadUiEvent.ToReplyDestination(
            Reply(
                forumId = forumId ?: 0,
                forumName = forumName.orEmpty(),
                threadId = threadId,
                postId = post.id,
                subPostId = subPost.id,
                replyUserId = subPost.author.id,
                replyUserName = subPost.author.nameShow.takeIf { name -> name.isNotEmpty() }
                    ?: subPost.author.name,
                replyUserPortrait = subPost.author.portrait,
            )
        )
    )

    fun onOpenSubPost(post: PostData, subPostId: Long) {
        val forumId = forumId ?: return
        sendUiEvent(
            ThreadUiEvent.ToSubPostsDestination(SubPosts(threadId, forumId, post.id, subPostId))
        )
    }

    private fun ThreadUiState.updateStateFrom(response: PbPageUiResponse): ThreadUiState {
        if (response.user == null) {
            hideReply = true
        }

        val firstPost = this.firstPost ?: response.firstPost // use old firstPost if possible
        return this.copy(
            user = response.user,
            data = response.posts,
            firstPost = firstPost,
            tbs = response.tbs,
            thread = response.thread.copy(firstPostId = firstPost?.id ?: firstPostId),
            latestPosts = null,
        )
    }

    companion object {

        private const val TAG = "ThreadViewModel"

        private fun ThreadUiState.updateLikedPost(postId: Long, liked: Boolean, loading: Boolean) = copy(
            data = this.data.fastMap { post ->
                if (post.id == postId) post.updateLikesCount(liked, loading) else post
            }
        )
    }
}

sealed interface ThreadUiEvent : UiEvent {
    class DeletePostFailed(val message: String) : ThreadUiEvent

    object DeletePostSuccess : ThreadUiEvent

    object ScrollToFirstReply : ThreadUiEvent

    object ScrollToLatestReply : ThreadUiEvent

    data class LoadPreviousSuccess(val previousIndex: Int, val offset: Int) : ThreadUiEvent

    data class LoadSuccess(val page: Int, val postId: Long) : ThreadUiEvent

    data class ToReplyDestination(val direction: Reply): ThreadUiEvent

    data class ToSubPostsDestination(val direction: SubPosts): ThreadUiEvent
}
