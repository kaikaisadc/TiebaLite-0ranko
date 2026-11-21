package com.huanchengfly.tieba.post.api.interfaces

import com.huanchengfly.tieba.post.api.models.*
import com.huanchengfly.tieba.post.api.models.protos.getUserInfo.GetUserInfoResponse
import com.huanchengfly.tieba.post.api.models.protos.hotThreadList.HotThreadListResponse
import com.huanchengfly.tieba.post.api.models.protos.pbFloor.PbFloorResponse
import com.huanchengfly.tieba.post.api.models.protos.pbPage.PbPageResponse
import com.huanchengfly.tieba.post.api.models.protos.personalized.PersonalizedResponse
import com.huanchengfly.tieba.post.api.models.protos.profile.ProfileResponse
import com.huanchengfly.tieba.post.api.models.protos.topicList.TopicListResponse
import com.huanchengfly.tieba.post.api.models.protos.userLike.UserLikeResponse
import com.huanchengfly.tieba.post.api.models.protos.userPost.UserPostResponse
import com.huanchengfly.tieba.post.api.models.web.HotMessageListBean
import com.huanchengfly.tieba.post.api.retrofit.ApiResult
import com.huanchengfly.tieba.post.models.DislikeBean
import com.huanchengfly.tieba.post.models.MyInfoBean
import java.io.File
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import retrofit2.Call

interface ITiebaApi :
    TiebaSearchApi,
    TiebaPostActionsApi,
    TiebaNotificationsApi,
    TiebaForumApi {
    /**
     * 个性推荐（每页 15 贴）
     *
     * @param loadType 加载类型（1 - 下拉刷新 2 - 加载更多）
     * @param page 分页页码
     */
    fun personalized(
        loadType: Int,
        page: Int = 1
    ): Call<PersonalizedBean>

    /**
     * 个性推荐（每页 15 贴）
     *
     * @param loadType 加载类型（1 - 下拉刷新 2 - 加载更多）
     * @param page 分页页码
     */
    fun personalizedAsync(
        loadType: Int,
        page: Int = 1
    ): Deferred<ApiResult<PersonalizedBean>>

    /**
     * 个性推荐（每页 15 贴）
     *
     * @param loadType 加载类型（1 - 下拉刷新 2 - 加载更多）
     * @param page 分页页码
     */
    fun personalizedFlow(
        loadType: Int,
        page: Int = 1
    ): Flow<PersonalizedBean>

    /**
     * 个性推荐（每页 15 贴）
     *
     * @param loadType 加载类型（1 - 下拉刷新 2 - 加载更多）
     * @param page 分页页码
     */
    fun personalizedProtoFlow(
        loadType: Int,
        page: Int = 1
    ): Flow<PersonalizedResponse>

    /**
     * 给贴子/回复点赞
     *
     * **需登录**
     *
     * @param threadId 贴子 ID
     * @param postId 回复 ID
     * @param opType 操作 0 = 点赞 1 = 取消点赞
     */
    fun opAgree(
        threadId: String,
        postId: String,
        opType: Int
    ): Call<AgreeBean>

    /**
     * 给贴子/回复点赞/取消点赞
     *
     * **需登录**
     *
     * @param threadId 贴子 ID
     * @param postId 回复 ID
     * @param opType 操作 0 = 点赞 1 = 取消点赞
     */
    fun opAgreeFlow(
        threadId: String,
        postId: String,
        opType: Int,
        objType: Int,
        agreeType: Int = 2,
    ): Flow<AgreeBean>

    /**
     * 给贴子/回复点踩
     *
     * **需登录**
     *
     * @param threadId 贴子 ID
     * @param postId 回复 ID
     * @param opType 操作 0 = 点踩 1 = 取消点踩
     */
    fun disagree(
        threadId: String,
        postId: String,
        opType: Int
    ): Call<AgreeBean>

    /**
     * 给贴子/回复点踩
     *
     * **需登录**
     *
     * @param threadId 贴子 ID
     * @param postId 回复 ID
     * @param opType 操作 0 = 点踩 1 = 取消点踩
     */
    fun disagreeFlow(
        threadId: String,
        postId: String,
        opType: Int
    ): Flow<AgreeBean>

    /**
     * 楼中楼页面
     *
     * @param threadId 贴 ID
     * @param page 分页页码
     * @param postId 回复 ID
     * @param subPostId 楼中楼回复 ID
     */
    fun floor(
        threadId: String,
        page: Int = 1,
        postId: String?,
        subPostId: String?
    ): Call<SubFloorListBean>

    /**
     * 查看用户关注的吧列表
     *
     * @param uid 用户 ID
     * @param page 分页页码（从 1 开始）
     */
    fun userLikeForum(
        uid: String,
        page: Int = 1
    ): Call<UserLikeForumBean>

    /**
     * 查看用户的所有主题贴/回复
     *
     * @param uid 用户 ID
     * @param page 分页页码（从 1 开始）
     * @param isThread 是否查看主题贴
     */
    fun userPost(
        uid: String,
        page: Int = 1,
        isThread: Boolean = true
    ): Call<UserPostBean>

    /**
     * 查看图片
     *
     * @param forumId 吧 ID
     * @param forumName 吧名
     * @param threadId 贴 ID
     * @param seeLz 是否只看楼主
     * @param picId 图片 ID
     * @param picIndex 图片索引
     * @param objType 原页面类型（pb - 贴页面 frs - 吧页面 index - 首页推荐）
     * @param prev 是否向前加载
     */
    fun picPage(
        forumId: String,
        forumName: String,
        threadId: String,
        seeLz: Boolean,
        picId: String,
        picIndex: String,
        objType: String,
        prev: Boolean
    ): Call<PicPageBean>

    /**
     * 查看图片
     *
     * @param forumId 吧 ID
     * @param forumName 吧名
     * @param threadId 贴 ID
     * @param seeLz 是否只看楼主
     * @param picId 图片 ID
     * @param picIndex 图片索引
     * @param objType 原页面类型（pb - 贴页面 frs - 吧页面 index - 首页推荐）
     * @param prev 是否向前加载
     */
    fun picPageFlow(
        forumId: String,
        forumName: String,
        threadId: String,
        seeLz: Boolean,
        picId: String,
        picIndex: String,
        objType: String,
        prev: Boolean
    ): Flow<PicPageBean>

    /**
     * 用户信息
     *
     * @param uid 用户 ID
     */
    fun profile(
        uid: String
    ): Call<ProfileBean>

    /**
     * 用户信息（Flow）
     *
     * @param uid 用户 ID
     */
    fun profileFlow(
        uid: String
    ): Flow<Profile>

    /**
     * 用户信息（异步）
     */
    fun myProfileAsync(): Deferred<ApiResult<com.huanchengfly.tieba.post.api.models.web.Profile>>

    /**
     * 查看收藏贴列表
     *
     * **需登录**
     *
     * @param page 分页页码（从 0 开始）
     * @param pageSize 每页贴数（默认 20）
     */
    fun threadStore(
        page: Int = 0,
        pageSize: Int = 20
    ): Call<ThreadStoreBean>

    /**
     * 查看收藏贴列表
     *
     * **需登录**
     *
     * @param page 分页页码（从 0 开始）
     * @param pageSize 每页贴数（默认 20）
     */
    fun threadStoreFlow(
        page: Int = 0,
        pageSize: Int = 20
    ): Flow<ThreadStoreBean>

    /**
     * 移除收藏
     *
     * **需登录**
     *
     * @param threadId 贴子 ID
     * @param tbs tbs
     */
    fun removeStore(
        threadId: String,
        tbs: String
    ): Call<CommonResponse>

    /**
     * 移除收藏
     *
     * **需登录**
     *
     * @param threadId 贴子 ID
     * @param tbs tbs
     */
    fun removeStoreFlow(
        threadId: Long,
        forumId: Long?,
        tbs: String
    ): Flow<CommonResponse>

    /**
     * 移除收藏
     *
     * **需登录**
     *
     * @param threadId 贴子 ID
     */
    fun removeStoreFlow(
        threadId: String
    ): Flow<CommonResponse>

    /**
     * 添加/更新收藏
     *
     * **需登录**
     *
     * @param threadId 贴子 ID
     * @param postId 收藏到的回复 ID
     * @param tbs tbs
     */
    fun addStore(
        threadId: String,
        postId: String,
        tbs: String
    ): Call<CommonResponse>

    /**
     * 添加/更新收藏
     *
     * **需登录**
     *
     * @param threadId 贴子 ID
     * @param postId 收藏到的回复 ID
     */
    fun addStoreAsync(
        threadId: Long,
        postId: Long
    ): Deferred<ApiResult<CommonResponse>>

    /**
     * 添加/更新收藏
     *
     * **需登录**
     *
     * @param threadId 贴子 ID
     * @param postId 收藏到的回复 ID
     */
    fun addStoreFlow(
        threadId: Long,
        postId: Long
    ): Flow<CommonResponse>

    /**
     * 贴页面
     *
     * @param threadId 贴 ID
     * @param page 分页页码（从 1 开始）
     * @param seeLz 是否只看楼主
     * @param reverse 是否逆序
     */
    fun threadContent(
        threadId: String,
        page: Int = 1,
        seeLz: Boolean = false,
        reverse: Boolean = false
    ): Call<ThreadContentBean>

    /**
     * 贴页面
     *
     * @param threadId 贴 ID
     * @param postId 回复 ID
     * @param seeLz 是否只看楼主
     * @param reverse 是否逆序
     */
    fun threadContent(
        threadId: String,
        postId: String?,
        seeLz: Boolean = false,
        reverse: Boolean = false
    ): Call<ThreadContentBean>

    /**
     * 贴页面
     *
     * @param threadId 贴 ID
     * @param page 分页页码（从 1 开始）
     * @param seeLz 是否只看楼主
     * @param reverse 是否逆序
     */
    fun threadContentAsync(
        threadId: String,
        page: Int = 1,
        seeLz: Boolean = false,
        reverse: Boolean = false
    ): Deferred<ApiResult<ThreadContentBean>>

    /**
     * 贴页面
     *
     * @param threadId 贴 ID
     * @param postId 回复 ID
     * @param seeLz 是否只看楼主
     * @param reverse 是否逆序
     */
    fun threadContentAsync(
        threadId: String,
        postId: String?,
        seeLz: Boolean = false,
        reverse: Boolean = false
    ): Deferred<ApiResult<ThreadContentBean>>

    /**
     * 推荐“不感兴趣”
     *
     * **需登录**
     *
     * @param dislikeBean “不感兴趣”信息 [com.huanchengfly.tieba.post.models.DislikeBean]
     * @param stoken stoken
     */
    fun submitDislike(
        dislikeBean: DislikeBean,
        stoken: String
    ): Call<CommonResponse>

    /**
     * 推荐“不感兴趣”
     *
     * **需登录**
     *
     * @param dislikeBean “不感兴趣”信息 [com.huanchengfly.tieba.post.models.DislikeBean]
     * @param stoken stoken
     */
    fun submitDislikeFlow(
        dislikeBean: DislikeBean
    ): Flow<CommonResponse>

    /**
     * 关注用户（web 接口）
     *
     * **需登录**
     *
     * @param portrait 头像
     * @param tbs tbs
     */
    fun follow(
        portrait: String,
        tbs: String
    ): Call<CommonResponse>

    /**
     * 取关用户（web 接口）
     *
     * **需登录**
     *
     * @param portrait 头像
     * @param tbs tbs
     */
    fun unfollow(
        portrait: String,
        tbs: String
    ): Call<CommonResponse>

    /**
     * 关注用户（客户端接口）
     *
     * **需登录**
     *
     * @param portrait 头像
     * @param tbs tbs
     */
    fun followFlow(
        portrait: String,
        tbs: String
    ): Flow<FollowBean>

    /**
     * 取关用户（客户端接口）
     *
     * **需登录**
     *
     * @param portrait 头像
     * @param tbs tbs
     */
    fun unfollowFlow(
        portrait: String,
        tbs: String
    ): Flow<CommonResponse>

    /**
     * 关注列表（客户端接口）
     *
     * **需登录**
     *
     * @param page 分页页码
     * @param uid 用户 uid，留空表示当前登录用户
     */
    fun followListFlow(
        page: Int = 1,
        uid: Long? = null,
    ): Flow<FollowListBean>

    /**
     * 获取用户所有关注（客户端接口，自动翻页聚合）
     *
     * **需登录**
     *
     * @param uid 用户 uid，留空表示当前登录用户
     */
    fun getAllFollowFlow(uid: Long? = null): Flow<FollowListBean>

    fun hotMessageList(): Call<HotMessageListBean>

    /**
     * 登录用户信息
     *
     * @param cookie 登录 Cookie 信息
     */
    fun myInfo(
        cookie: String
    ): Call<MyInfoBean>

    /**
     * 登录用户信息
     *
     * @param cookie 登录 Cookie 信息
     */
    fun myInfoAsync(
        cookie: String
    ): Deferred<ApiResult<MyInfoBean>>

    /**
     * 登录用户信息
     *
     * @param cookie 登录 Cookie 信息
     */
    fun myInfoFlow(
        cookie: String
    ): Flow<MyInfoBean>

    /**
     * 话题详情
     *
     * @param topicId 话题id
     * @param topicName 话题名
     * @param isNew
     * @param isShare
     * @param page 分页页码(初始为1)
     * @param pageSize 分页大小
     * @param offset （分页页码-1）* 分页大小
     * @param lastId 上次返回的最后一个feedid，初次请求留空
     */
     fun topicDetailFlow(
        topicId: String,
        topicName: String,
        isNew: Int,
        isShare: Int,
        page: Int,
        pageSize: Int,
        offset: Int,
        lastId: String,
    ): Flow<TopicDetailBean>

    /**
     * 获得当前用户昵称（需登录）
     */
    fun initNickNameFlow(): Flow<InitNickNameBean>

    /**
     * 获得用户昵称
     */
    fun initNickNameFlow(
        bduss: String,
        sToken: String,
    ): Flow<InitNickNameBean>

    /**
     * 更新登录信息（需登录）
     */
    fun loginFlow(): Flow<LoginBean>

    /**
     * 登录
     */
    fun loginFlow(
        bduss: String,
        sToken: String
    ): Flow<LoginBean>

    /**
     * 修改个人资料
     *
     * @param birthdayShowStatus 是否仅显示星座
     * @param birthdayTime 生日时间戳 / 1000
     * @param intro 个人简介（最多 500 字）
     * @param sex 性别（1 = 男，2 = 女）
     * @param nickName 昵称
     */
    fun profileModifyFlow(
        birthdayShowStatus: Boolean,
        birthdayTime: String,
        intro: String,
        sex: String,
        nickName: String,
    ): Flow<CommonResponse>

    /**
     * 上传头像
     *
     * @param file 图片 File 对象
     *
     */
    fun imgPortrait(
        file: File
    ): Flow<CommonResponse>

    /**
     * 关注动态
     *
     * @param pageTag 页码
     * @param lastRequestUnix 上次请求的时间戳（10位）
     * @param loadType 加载类型（1 = 下拉刷新，2 = 加载更多）
     */
    fun userLikeFlow(
        pageTag: String,
        lastRequestUnix: Long,
        loadType: Int
    ): Flow<UserLikeResponse>

    /**
     * 首页热榜
     *
     * @param tabCode Tab
     */
    fun hotThreadListFlow(
        tabCode: String
    ): Flow<HotThreadListResponse>

    /**
     * 话题榜
     */
    fun topicListFlow(): Flow<TopicListResponse>

    fun syncFlow(clientId: String? = null): Flow<Sync>

    /**
     * 用户信息（Flow）
     *
     * @param uid 用户 ID
     */
    fun userProfileFlow(
        uid: Long
    ): Flow<ProfileResponse>

    /**
     * 贴子页（Flow）
     *
     * @param threadId 贴子 ID
     * @param page 页码（从 1 开始）
     * @param postId 回复 ID
     * @param seeLz 是否只看楼主
     * @param back 是否向前翻页
     * @param sortType 排序类型
     * @param forumId 吧 ID
     * @param stType 来源类型（？）
     * @param mark 是否收藏
     * @param lastPostId 最后一条回复 ID
     */
    suspend fun pbPage(
        threadId: Long,
        page: Int = 0,
        postId: Long = 0L,
        seeLz: Boolean = false,
        back: Boolean = false,
        sortType: Int = 0,
        forumId: Long? = null,
        stType: String = "",
        mark: Int = 0,
        lastPostId: Long? = null,
    ): PbPageResponse

    /**
     * 楼中楼页面
     *
     * @param forumId 吧 ID
     * @param threadId 贴 ID
     * @param page 分页页码
     * @param postId 回复 ID
     * @param subPostId 楼中楼回复 ID
     */
    suspend fun pbFloor(
        threadId: Long,
        postId: Long,
        forumId: Long = 0L,
        page: Int = 1,
        subPostId: Long = 0L,
    ): PbFloorResponse

    /**
     * 查看用户的所有主题贴/回复
     *
     * @param uid 用户 ID
     * @param page 分页页码（从 1 开始）
     * @param isThread 是否查看主题贴
     */
    fun userPostFlow(
        uid: Long,
        page: Int = 1,
        isThread: Boolean = true,
    ): Flow<UserPostResponse>

    fun userLikeForumFlow(
        uid: String,
        page: Int = 1,
    ): Flow<UserLikeForumBean>

    /**
     * 获得当前用户信息（需登录）
     */
    fun getUserInfoFlow(): Flow<GetUserInfoResponse>

    /**
     * 获得用户信息
     */
    fun getUserInfoFlow(
        uid: Long,
        bduss: String?,
        sToken: String?,
    ): Flow<GetUserInfoResponse>

    /**
     * 禁止用户互动（转、评、赞踩、@）
     * @param blackUid 用户id
     * @param tbs tbs（长）
     * @param permList 参数列表：关注，互动，私信。(0,允许 1,禁止)
     */
    fun setUserBlackFlow(
        blackUid: Long,
        tbs: String,
        permList: PermissionListBean,
    ): Flow<CommonResponse>

    /**
     * 查询单个用户的拉黑信息
     * @param blackUid 被查询用户portrait
     */
    fun getUserBlackInfoFlow(
        blackUid: Long
    ): Flow<GetUserBlackInfoBean>

}
