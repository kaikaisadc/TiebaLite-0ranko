package com.huanchengfly.tieba.post.repository

import com.huanchengfly.tieba.post.api.TiebaApi
import com.huanchengfly.tieba.post.api.models.AddThreadBean
import com.huanchengfly.tieba.post.api.models.protos.addPost.AddPostResponse
import com.huanchengfly.tieba.post.arch.GlobalEvent
import com.huanchengfly.tieba.post.arch.emitGlobalEventSuspend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

object AddPostRepository {
    fun addThread(
        content: String,
        forumId: Long,
        forumName: String,
        title: String? = "",
        isHide: Int? = 1,
        isTitle: Int? = 1
    ): Flow<AddThreadBean> =
        TiebaApi.getInstance()
            .addThreadFlow(
                content,
                forumName,
                forumId.toString(),
                title.orEmpty(),
                requireNotNull(isHide),
                requireNotNull(isTitle)
            ).onEach {
                emitGlobalEventSuspend(
                    GlobalEvent.AddThreadSuccess(
                        it.tid?.toLongOrNull(),
                        it.pid?.toLongOrNull(),
                        it.errorMsg,
                    )
                )
            }

    fun addPost(
        content: String,
        forumId: Long,
        forumName: String,
        threadId: Long,
        tbs: String? = null,
        nameShow: String? = null,
        postId: Long? = null,
        subPostId: Long? = null,
        replyUserId: Long? = null,
    ): Flow<AddPostResponse> =
        TiebaApi.getInstance()
            .addPostFlow(
                content,
                forumId.toString(),
                forumName,
                threadId.toString(),
                tbs,
                nameShow,
                postId?.toString(),
                subPostId?.toString(),
                replyUserId?.toString()
            )
            .onEach {
                val newPostId = it.data_?.pid?.toLongOrNull()
                val anchor = postId ?: newPostId ?: return@onEach
                emitGlobalEventSuspend(
                    GlobalEvent.ReplySuccess(
                        threadId = threadId,
                        newPostId = anchor,
                        postId = postId,
                        subPostId = subPostId,
                        newSubPostId = newPostId.takeIf { postId != null },
                    )
                )
            }
}