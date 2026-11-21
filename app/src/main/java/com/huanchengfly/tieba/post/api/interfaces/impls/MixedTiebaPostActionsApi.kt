package com.huanchengfly.tieba.post.api.interfaces.impls

import android.os.Build
import com.huanchengfly.tieba.post.App
import com.huanchengfly.tieba.post.api.ClientVersion
import com.huanchengfly.tieba.post.api.buildCommonRequest
import com.huanchengfly.tieba.post.api.buildProtobufRequestBody
import com.huanchengfly.tieba.post.api.interfaces.TiebaPostActionsApi
import com.huanchengfly.tieba.post.api.models.AddThreadBean
import com.huanchengfly.tieba.post.api.models.CheckReportBean
import com.huanchengfly.tieba.post.api.models.CommonResponse
import com.huanchengfly.tieba.post.api.models.WebReplyResultBean
import com.huanchengfly.tieba.post.api.models.WebUploadPicBean
import com.huanchengfly.tieba.post.api.models.protos.addPollPost.AddPollPostReponse
import com.huanchengfly.tieba.post.api.models.protos.addPollPost.AddPollPostRequest
import com.huanchengfly.tieba.post.api.models.protos.addPollPost.AddPollPostRequestDate
import com.huanchengfly.tieba.post.api.models.protos.addPost.AddPostRequest
import com.huanchengfly.tieba.post.api.models.protos.addPost.AddPostRequestData
import com.huanchengfly.tieba.post.api.models.protos.addPost.AddPostResponse
import com.huanchengfly.tieba.post.api.retrofit.ApiResult
import com.huanchengfly.tieba.post.api.retrofit.RetrofitTiebaApi
import com.huanchengfly.tieba.post.models.PhotoInfoBean
import com.huanchengfly.tieba.post.utils.AccountUtil
import com.huanchengfly.tieba.post.utils.ImageUtil
import java.io.FileInputStream
import java.io.IOException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import retrofit2.Call

internal object MixedTiebaPostActionsApi : TiebaPostActionsApi {
    override fun delThread(
        forumId: String,
        forumName: String,
        threadId: String,
        tbs: String
    ): Call<CommonResponse> =
        RetrofitTiebaApi.MINI_TIEBA_API.delThread(forumId, forumName, threadId, tbs)

    override fun delThreadFlow(
        forumId: Long,
        forumName: String,
        threadId: Long,
        tbs: String?,
        delMyThread: Boolean,
        isHide: Boolean,
    ): Flow<CommonResponse> =
        RetrofitTiebaApi.OFFICIAL_TIEBA_API
            .delThreadFlow(
                forumId,
                forumName,
                threadId,
                tbs,
                deleteMyThread = if (delMyThread) 1 else 0,
                isFrsMask = if (isHide) 1 else 0
            )

    override fun delPost(
        forumId: String,
        forumName: String,
        threadId: String,
        postId: String,
        tbs: String,
        isFloor: Boolean,
        delMyPost: Boolean
    ): Call<CommonResponse> =
        RetrofitTiebaApi.MINI_TIEBA_API.delPost(
            forumId,
            forumName,
            threadId,
            postId,
            tbs,
            is_floor = if (isFloor) 1 else 0,
            src = if (isFloor) 3 else 1,
            is_vip_del = if (delMyPost) 0 else 1,
            delete_my_post = if (delMyPost) 1 else 0
        )

    override fun delPostFlow(
        forumId: Long,
        forumName: String,
        threadId: Long,
        postId: Long,
        tbs: String?,
        isFloor: Boolean,
        delMyPost: Boolean
    ): Flow<CommonResponse> =
        RetrofitTiebaApi.OFFICIAL_TIEBA_API
            .delPostFlow(
                forumId,
                forumName,
                threadId,
                postId,
                isFloor = if (isFloor) 1 else 0,
                src = if (isFloor) 3 else 1,
                isVipDel = if (delMyPost) 0 else 1,
                deleteMyPost = if (delMyPost) 1 else 0,
                tbs = tbs
            )

    override fun webUploadPic(photoInfoBean: PhotoInfoBean): Call<WebUploadPicBean> {
        var base64: String? = null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            base64 = ImageUtil.imageToBase64(photoInfoBean.file)
        } else {
            try {
                App.INSTANCE.contentResolver.openAssetFileDescriptor(
                    photoInfoBean.fileUri,
                    "r"
                )?.use { afd ->
                    base64 =
                        ImageUtil.imageToBase64(FileInputStream(afd.parcelFileDescriptor.fileDescriptor))
                }
            } catch (e: IOException) {
                e.printStackTrace()
                base64 = null
            }
        }
        return RetrofitTiebaApi.WEB_TIEBA_API.webUploadPic(base64)
    }

    override fun webReply(
        forumId: String,
        forumName: String,
        threadId: String,
        tbs: String,
        content: String,
        imgInfo: String?,
        nickName: String,
        pn: String,
        bsk: String
    ): Call<WebReplyResultBean> =
        RetrofitTiebaApi.WEB_TIEBA_API.webReply(
            content = content,
            imgInfo = imgInfo ?: "",
            forumId = forumId,
            forumName = forumName,
            tbs = tbs,
            threadId = threadId,
            nickName = nickName,
            bsk = bsk,
            referer = "https://tieba.baidu.com/p/$threadId?lp=5028&mo_device=1&is_jingpost=0&pn=$pn&"
        )

    override fun webReply(
        forumId: String,
        forumName: String,
        threadId: String,
        tbs: String,
        content: String,
        imgInfo: String?,
        nickName: String,
        postId: String,
        floor: String,
        pn: String,
        bsk: String
    ): Call<WebReplyResultBean> =
        RetrofitTiebaApi.WEB_TIEBA_API.webReply(
            content = content,
            imgInfo = imgInfo ?: "",
            forumId = forumId,
            forumName = forumName,
            tbs = tbs,
            threadId = threadId,
            nickName = nickName,
            postId = postId,
            floor = floor,
            bsk = bsk,
            referer = "https://tieba.baidu.com/p/$threadId?lp=5028&mo_device=1&is_jingpost=0&pn=$pn&"
        )

    override fun webReply(
        forumId: String,
        forumName: String,
        threadId: String,
        tbs: String,
        content: String,
        imgInfo: String?,
        nickName: String,
        postId: String,
        replyPostId: String,
        floor: String,
        pn: String,
        bsk: String
    ): Call<WebReplyResultBean> =
        RetrofitTiebaApi.WEB_TIEBA_API.webReply(
            content = content,
            imgInfo = imgInfo ?: "",
            forumId = forumId,
            forumName = forumName,
            tbs = tbs,
            threadId = threadId,
            nickName = nickName,
            postId = postId,
            replyPostId = replyPostId,
            floor = floor,
            bsk = bsk,
            referer = "https://tieba.baidu.com/p/$threadId?lp=5028&mo_device=1&is_jingpost=0&pn=$pn&"
        )

    override fun webReplyAsync(
        forumId: String,
        forumName: String,
        threadId: String,
        tbs: String,
        content: String,
        imgInfo: String?,
        nickName: String,
        pn: String,
        bsk: String
    ): Deferred<ApiResult<WebReplyResultBean>> =
        RetrofitTiebaApi.WEB_TIEBA_API.webReplyAsync(
            content = content,
            imgInfo = imgInfo ?: "",
            forumId = forumId,
            forumName = forumName,
            tbs = tbs,
            threadId = threadId,
            nickName = nickName,
            bsk = bsk,
            referer = "https://tieba.baidu.com/p/$threadId?lp=5028&mo_device=1&is_jingpost=0&pn=$pn&"
        )

    override fun webReplyAsync(
        forumId: String,
        forumName: String,
        threadId: String,
        tbs: String,
        content: String,
        imgInfo: String?,
        nickName: String,
        postId: String,
        floor: String,
        pn: String,
        bsk: String
    ): Deferred<ApiResult<WebReplyResultBean>> =
        RetrofitTiebaApi.WEB_TIEBA_API.webReplyAsync(
            content = content,
            imgInfo = imgInfo ?: "",
            forumId = forumId,
            forumName = forumName,
            tbs = tbs,
            threadId = threadId,
            nickName = nickName,
            postId = postId,
            floor = floor,
            bsk = bsk,
            referer = "https://tieba.baidu.com/p/$threadId?lp=5028&mo_device=1&is_jingpost=0&pn=$pn&"
        )

    override fun webReplyAsync(
        forumId: String,
        forumName: String,
        threadId: String,
        tbs: String,
        content: String,
        imgInfo: String?,
        nickName: String,
        postId: String,
        replyPostId: String,
        floor: String,
        pn: String,
        bsk: String
    ): Deferred<ApiResult<WebReplyResultBean>> =
        RetrofitTiebaApi.WEB_TIEBA_API.webReplyAsync(
            content = content,
            imgInfo = imgInfo ?: "",
            forumId = forumId,
            forumName = forumName,
            tbs = tbs,
            threadId = threadId,
            nickName = nickName,
            postId = postId,
            replyPostId = replyPostId,
            floor = floor,
            bsk = bsk,
            referer = "https://tieba.baidu.com/p/$threadId?lp=5028&mo_device=1&is_jingpost=0&pn=$pn&"
        )

    override fun checkReportPost(postId: String): Call<CheckReportBean> =
        RetrofitTiebaApi.OFFICIAL_TIEBA_API.checkReport(
            category = "1",
            reportParam = mapOf(
                "pid" to postId
            )
        )

    override fun checkReportPostAsync(postId: String): Deferred<ApiResult<CheckReportBean>> =
        RetrofitTiebaApi.OFFICIAL_TIEBA_API.checkReportAsync(
            category = "1",
            reportParam = mapOf(
                "pid" to postId
            )
        )

    override fun addPostFlow(
        content: String,
        forumId: String,
        forumName: String,
        threadId: String,
        tbs: String?,
        nameShow: String?,
        postId: String?,
        subPostId: String?,
        replyUserId: String?
    ): Flow<AddPostResponse> {
        return RetrofitTiebaApi.OFFICIAL_PROTOBUF_TIEBA_POST_API
            .addPostFlow(
                buildProtobufRequestBody(
                    AddPostRequest(
                        AddPostRequestData(
                            anonymous = "1",
                            barrage_time = "0".takeIf { postId.isNullOrEmpty() },
                            can_no_forum = "0",
                            common = buildCommonRequest(
                                clientVersion = ClientVersion.TIEBA_V12_POST,
                                tbs = tbs ?: AccountUtil.getAccountInfo { this.tbs }
                            ),
                            content = content,
                            entrance_type = "0",
                            fid = forumId,
                            floor_num = "0",
                            kw = forumName,
                            is_ad = "0",
                            is_addition = "0",
                            is_barrage = "0",
                            is_feedback = "0",
                            is_giftpost = "0",
                            is_pictxt = "0",
                            is_show_bless = 0,
                            is_twzhibo_thread = "0",
                            name_show = nameShow ?: AccountUtil.getAccountInfo { this.nickname }
                                .orEmpty(),
                            new_vcode = "1",
                            post_from = if (postId.isNullOrEmpty() && subPostId.isNullOrEmpty()) "13" else if (subPostId.isNullOrEmpty()) "0" else null,
                            quote_id = postId,
                            reply_uid = replyUserId.takeIf { !postId.isNullOrEmpty() },
                            repostid = postId,
                            sub_post_id = subPostId,
                            show_custom_figure = 0,
                            takephoto_num = "0",
                            tid = threadId,
                            v_fid = "".takeIf { postId.isNullOrEmpty() },
                            v_fname = "".takeIf { postId.isNullOrEmpty() },
                            vcode_tag = "12",
                        )
                    ),
                    clientVersion = ClientVersion.TIEBA_V12_POST
                )
            )
    }

    override fun addThreadFlow(
        threadContent: String,
        kw: String,
        fid: String,
        title: String,
        isHide: Int,
        isTitle: Int
    ): Flow<AddThreadBean> =
    RetrofitTiebaApi.MINI_TIEBA_API.addThreadFlow(threadContent, kw, fid, title, isHide, isTitle)

    override fun addPollPost(forumId: Long?, threadId: Long, option: String): Flow<CommonResponse> =
        RetrofitTiebaApi.HYBRID_TIEBA_API.addPollPost(
            forumId,
            threadId,
            option
        )

    override fun addPollPostProtobuf(
        forumId: Long?,
        threadId: Long,
        option: String
    ): Flow<AddPollPostReponse> =
        RetrofitTiebaApi.OFFICIAL_PROTOBUF_TIEBA_POST_API.addPollPostProtobuf(
            buildProtobufRequestBody(
                AddPollPostRequest(
                    AddPollPostRequestDate(
                        forum_id = forumId ?: 0L,
                        thread_id = threadId,
                        options = option,
                    )
                )
            )
        )
}
