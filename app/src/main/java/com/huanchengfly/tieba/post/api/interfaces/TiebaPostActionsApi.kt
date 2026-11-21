package com.huanchengfly.tieba.post.api.interfaces

import com.huanchengfly.tieba.post.api.models.*
import com.huanchengfly.tieba.post.api.models.protos.addPollPost.AddPollPostReponse
import com.huanchengfly.tieba.post.api.models.protos.addPost.AddPostResponse
import com.huanchengfly.tieba.post.api.retrofit.ApiResult
import com.huanchengfly.tieba.post.models.PhotoInfoBean
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import retrofit2.Call

interface TiebaPostActionsApi {
    /**
     * 删除自己的贴子
     *
     * **需登录**
     *
     * @param forumId 吧 ID
     * @param forumName 吧名
     * @param threadId 贴子 ID
     */
    fun delThread(
        forumId: String,
        forumName: String,
        threadId: String,
        tbs: String
    ): Call<CommonResponse>

    /**
     * 删除/隐藏贴子
     *
     * **需登录**
     *
     * @param forumId 吧 ID
     * @param forumName 吧名
     * @param threadId 贴子 ID
     * @param tbs tbs
     * @param delMyThread 是否为自己的贴子
     * @param isHide 是否为隐藏贴子
     */
    fun delThreadFlow(
        forumId: Long,
        forumName: String,
        threadId: Long,
        tbs: String?,
        delMyThread: Boolean,
        isHide: Boolean
    ): Flow<CommonResponse>

    /**
     * 删除贴子中的回复
     *
     * **需登录**
     *
     * @param forumId 吧 ID
     * @param forumName 吧名
     * @param threadId 贴子 ID
     * @param postId 回复 ID
     * @param isFloor 是否为楼中楼回复
     * @param delMyPost 是否为当前登录用户回复
     */
    fun delPost(
        forumId: String,
        forumName: String,
        threadId: String,
        postId: String,
        tbs: String,
        isFloor: Boolean,
        delMyPost: Boolean
    ): Call<CommonResponse>

    /**
     * 删除贴子中的回复
     *
     * **需登录**
     *
     * @param forumId 吧 ID
     * @param forumName 吧名
     * @param threadId 贴子 ID
     * @param postId 回复 ID
     * @param isFloor 是否为楼中楼回复
     * @param delMyPost 是否为当前登录用户回复
     */
    fun delPostFlow(
        forumId: Long,
        forumName: String,
        threadId: Long,
        postId: Long,
        tbs: String?,
        isFloor: Boolean = false,
        delMyPost: Boolean = true
    ): Flow<CommonResponse>

    /**
     * 上传图片（web 接口）
     *
     * **需登录**
     */
    fun webUploadPic(
        photoInfoBean: PhotoInfoBean,
    ): Call<WebUploadPicBean>

    /**
     * 回贴 - 回复主题贴（web 接口）
     *
     * **需登录**
     *
     * @param forumId 吧 ID
     * @param forumName 吧名
     * @param threadId 贴子 ID
     * @param tbs tbs
     * @param content 回复内容
     * @param imgInfo 图片
     * @param nickName 昵称
     * @param pn 页码
     * @param bsk BSK
     */
    fun webReply(
        forumId: String,
        forumName: String,
        threadId: String,
        tbs: String,
        content: String,
        imgInfo: String?,
        nickName: String,
        pn: String,
        bsk: String
    ): Call<WebReplyResultBean>

    /**
     * 回贴 - 回复别人的回复（web 接口）
     *
     * **需登录**
     *
     * @param forumId 吧 ID
     * @param forumName 吧名
     * @param threadId 贴子 ID
     * @param tbs tbs
     * @param content 回复内容
     * @param imgInfo 图片
     * @param nickName 昵称
     * @param postId 回复 ID
     * @param floor 回复楼层
     * @param pn 页码
     * @param bsk BSK
     */
    fun webReply(
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
    ): Call<WebReplyResultBean>

    /**
     * 回贴 - 回复楼中楼（web 接口）
     *
     * **需登录**
     *
     * @param forumId 吧 ID
     * @param forumName 吧名
     * @param threadId 贴子 ID
     * @param tbs tbs
     * @param content 回复内容
     * @param imgInfo 图片
     * @param nickName 昵称
     * @param postId 回复 ID
     * @param replyPostId 楼中楼 ID
     * @param floor 回复楼层
     * @param pn 页码
     * @param bsk BSK
     */
    fun webReply(
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
    ): Call<WebReplyResultBean>

    /**
     * 回贴 - 回复主题贴（web 接口）（异步）
     *
     * **需登录**
     *
     * @param forumId 吧 ID
     * @param forumName 吧名
     * @param threadId 贴子 ID
     * @param tbs tbs
     * @param content 回复内容
     * @param imgInfo 图片
     * @param nickName 昵称
     * @param pn 页码
     * @param bsk BSK
     */
    fun webReplyAsync(
        forumId: String,
        forumName: String,
        threadId: String,
        tbs: String,
        content: String,
        imgInfo: String?,
        nickName: String,
        pn: String,
        bsk: String
    ): Deferred<ApiResult<WebReplyResultBean>>

    /**
     * 回贴 - 回复别人的回复（web 接口）（异步）
     *
     * **需登录**
     *
     * @param forumId 吧 ID
     * @param forumName 吧名
     * @param threadId 贴子 ID
     * @param tbs tbs
     * @param content 回复内容
     * @param imgInfo 图片
     * @param nickName 昵称
     * @param postId 回复 ID
     * @param floor 回复楼层
     * @param pn 页码
     * @param bsk BSK
     */
    fun webReplyAsync(
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
    ): Deferred<ApiResult<WebReplyResultBean>>

    /**
     * 回贴 - 回复楼中楼（web 接口）（异步）
     *
     * **需登录**
     *
     * @param forumId 吧 ID
     * @param forumName 吧名
     * @param threadId 贴子 ID
     * @param tbs tbs
     * @param content 回复内容
     * @param imgInfo 图片
     * @param nickName 昵称
     * @param postId 回复 ID
     * @param replyPostId 楼中楼 ID
     * @param floor 回复楼层
     * @param pn 页码
     * @param bsk BSK
     */
    fun webReplyAsync(
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
    ): Deferred<ApiResult<WebReplyResultBean>>

    /**
     * 获取举报贴子/回贴页面 URL
     *
     * @param postId PID
     */
    fun checkReportPost(
        postId: String,
    ): Call<CheckReportBean>

    /**
     * 获取举报贴子/回贴页面 URL
     *
     * @param postId PID
     */
    fun checkReportPostAsync(
        postId: String,
    ): Deferred<ApiResult<CheckReportBean>>

    /**
     * 回贴（App 接口）
     *
     * **需登录**
     *
     * @param forumId 吧 ID（不可为null，0）
     * @param forumName 吧名
     * @param threadId 贴子 ID
     * @param tbs tbs
     * @param content 回复内容
     * @param postId 回复楼 ID，为空则回复贴子
     * @param replyUserId 楼中楼回复用户 ID
     */
    fun addPostFlow(
        content: String,
        forumId: String,
        forumName: String,
        threadId: String,
        tbs: String? = null,
        nameShow: String? = null,
        postId: String? = null,
        subPostId: String? = null,
        replyUserId: String? = null
    ): Flow<AddPostResponse>

    /**
     * 发帖
     * @param threadContent 帖子内容
     * @param kw 吧名
     * @param fid 吧id
     * @param title 标题(无标题是留空)
     * @param isHide 个人主页显示(0,显示 1,仅自己可见)
     * @param isTitle 是否有标题(0,有标题 1,无标题)
     */
    fun addThreadFlow(
        threadContent: String,
        kw: String,
        fid: String,
        title: String,
        isHide: Int,
        isTitle: Int,
    ): Flow<AddThreadBean>

    /**
     * 投票
     * @param forumId 吧ID
     * @param threadId 贴ID
     * @param option 选项
     */
    fun addPollPost(
        forumId: Long?,
        threadId: Long,
        option: String,
    ): Flow<CommonResponse>

    /**
     * 投票
     * @param forumId 吧ID
     * @param threadId 贴ID
     * @param option 选项
     */
    fun addPollPostProtobuf(
        forumId: Long?,
        threadId: Long,
        option: String,
    ): Flow<AddPollPostReponse>
}
