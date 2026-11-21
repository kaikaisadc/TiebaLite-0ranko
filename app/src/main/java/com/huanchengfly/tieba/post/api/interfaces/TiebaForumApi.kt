package com.huanchengfly.tieba.post.api.interfaces

import com.huanchengfly.tieba.post.api.models.*
import com.huanchengfly.tieba.post.api.models.protos.GeneralTabList.GeneralTabListResponse
import com.huanchengfly.tieba.post.api.models.protos.forumGuide.ForumGuideResponse
import com.huanchengfly.tieba.post.api.models.protos.forumRecommend.ForumRecommendResponse
import com.huanchengfly.tieba.post.api.models.protos.forumRuleDetail.ForumRuleDetailResponse
import com.huanchengfly.tieba.post.api.models.protos.frsPage.FrsPageResponse
import com.huanchengfly.tieba.post.api.models.protos.getBawuInfo.GetBawuInfoResponse
import com.huanchengfly.tieba.post.api.models.protos.getForumDetail.GetForumDetailResponse
import com.huanchengfly.tieba.post.api.models.protos.getHistoryForum.GetHistoryForumResponse
import com.huanchengfly.tieba.post.api.models.protos.getLevelInfo.GetLevelInfoResponse
import com.huanchengfly.tieba.post.api.models.protos.getMemberInfo.GetMemberInfoResponse
import com.huanchengfly.tieba.post.api.models.protos.threadList.ThreadListResponse
import com.huanchengfly.tieba.post.api.models.web.ForumHome
import com.huanchengfly.tieba.post.api.retrofit.ApiResult
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import retrofit2.Call

interface TiebaForumApi {
    /**
     * 关注吧列表
     *
     * **需登录**
     *
     * **返回列表限制200个吧**
     */
    fun forumRecommend(): Call<ForumRecommend>

    /**
     * 关注吧列表
     *
     * **需登录**
     *
     * **返回列表限制200个吧**
     */
    fun forumRecommendAsync(): Deferred<ApiResult<ForumRecommend>>

    /**
     * 关注吧列表
     *
     * **需登录**
     *
     * **返回列表限制200个吧**
     */
    fun forumRecommendFlow(): Flow<ForumRecommend>

    /**
     * 获取首页关注吧列表（网页版接口）
     *
     * @param sortType 排序方式（0 = 等级排序，1 = 关注顺序）
     * @param page 分页页码（从 0 开始）
     */
    fun forumHomeAsync(
        sortType: Int,
        page: Int = 0
    ): Deferred<ApiResult<ForumHome>>

    /**
     * 取关一个吧
     *
     * **需登录**
     *
     * @param forumId 吧 ID
     * @param forumName 吧名
     * @param tbs tbs（长）
     */
    fun unlikeForum(
        forumId: String,
        forumName: String,
        tbs: String
    ): Call<CommonResponse>

    /**
     * 取关一个吧
     *
     * **需登录**
     *
     * @param forumId 吧 ID
     * @param forumName 吧名
     * @param tbs tbs（长）
     */
    fun unlikeForumFlow(
        forumId: String,
        forumName: String,
        tbs: String
    ): Flow<CommonResponse>

    /**
     * 关注一个吧
     *
     * **需登录**
     *
     * @param forumId 吧 ID
     * @param forumName 吧名
     * @param tbs tbs（长）
     */
    fun likeForum(
        forumId: String,
        forumName: String,
        tbs: String
    ): Call<LikeForumResultBean>

    /**
     * 关注一个吧
     *
     * **需登录**
     *
     * @param forumId 吧 ID
     * @param forumName 吧名
     * @param tbs tbs（长）
     */
    fun likeForumFlow(
        forumId: String,
        forumName: String,
        tbs: String
    ): Flow<LikeForumResultBean>

    /**
     * 吧签到
     *
     * **需登录**
     *
     * @param forumName 吧名
     * @param tbs tbs
     */
    fun signAsync(
        forumName: String,
        tbs: String
    ): Deferred<ApiResult<SignResultBean>>

    /**
     * 吧签到
     *
     * **需登录**
     *
     * @param forumId 吧 ID
     * @param forumName 吧名
     * @param tbs tbs
     */
    fun signFlow(
        forumId: String,
        forumName: String,
        tbs: String
    ): Flow<SignResultBean>

    /**
     * 获取吧列表
     */
    fun getForumListFlow(): Flow<GetForumListBean>

    /**
     * 一键签到（官方）
     */
    fun mSign(
        forumIds: String,
        tbs: String
    ): Flow<MSignBean>

    /**
     * 关注吧列表
     *
     * **需登录**
     *
     * **返回列表限制200个吧**
     *
     * @param sortType 排序（0=更新排序 1=等级排序）
     */
    fun forumRecommendNewFlow(
        sortType: Int = 1
    ): Flow<ForumRecommendResponse>

    /**
     * 关注吧列表_V12版本
     *
     * **需登录**
     *
     * **返回列表限制200个吧**
     *
     * @param sortType 排序（0=更新排序 1=等级排序）
     */
    fun forumGuideNewFlow(
        sortType: Int = 2
    ): Flow<ForumGuideResponse>

    /**
     * 吧页面
     *
     * @param forumName 吧名
     * @param page 页码（从 1 开始）
     * @param loadType 加载类型（1 - 下拉刷新 2 - 加载更多）
     * @param sortType 排序
     * @param goodClassifyId 精品贴分类
     */
    fun frsPage(
        forumName: String,
        page: Int,
        loadType: Int,
        sortType: Int,
        goodClassifyId: Int? = null
    ): Flow<FrsPageResponse>

    /**
     * 吧页面 - 贴子列表
     *
     * @param forumId 吧 ID
     * @param forumName 吧名
     * @param page 页码（从 1 开始）
     * @param sortType 排序
     */
    fun threadList(
        forumId: Long,
        forumName: String,
        page: Int,
        sortType: Int,
        threadIds: String = "",
    ): Flow<ThreadListResponse>

    /**
     * 吧页面 - 通用标签列表
     */
    fun generalTabList(
        forumId: Long,
        forumName: String,
        tabId: Int,
        tabType: Int,
        tabName: String,
        isGeneralTab: Int,
        pn: Int = 1,
        sortType: Int = -1,
        lastThreadId: Long = 0,
        isDefaultNavTab: Int = 0,
    ): Flow<GeneralTabListResponse>

    /**
     * 获取吧信息
     *
     * @param forumId 吧 ID
     */
    fun getForumDetailFlow(
        forumId: Long,
    ): Flow<GetForumDetailResponse>

    /**
     * 获取吧务信息
     *
     * @param forumId 吧 ID
     */
    fun getBawuInfoFlow(
        forumId: Long,
    ): Flow<GetBawuInfoResponse>

    /**
     * 获取吧等级信息
     *
     * @param forumId 吧 ID
     */
    fun getLevelInfoFlow(
        forumId: Long,
    ): Flow<GetLevelInfoResponse>

    /**
     * 获取吧成员信息
     *
     * @param forumId 吧 ID
     */
    fun getMemberInfoFlow(
        forumId: Long,
    ): Flow<GetMemberInfoResponse>

    /**
     * 获取吧规
     *
     * @param forumId 吧 ID
     */
    fun forumRuleDetailFlow(
        forumId: Long,
    ): Flow<ForumRuleDetailResponse>

    /**
     * 获取吧浏览历史信息
     */
    fun getHistoryForumFlow(
        history: String,
    ): Flow<GetHistoryForumResponse>

    /**
     * 关注吧列表(分页)
     * @param sortType 排序方式
     * @param callFrom 1来自主页?(包含热搜数据),3 来自签到页?
     * @param pageNo 页码
     * @param resNum 单页数量
     * @param topForumNum 置顶吧数量
     */
    fun forumGuideFlow(
        sortType: Int? = 3,
        callFrom: Int? = 3,
        pageNo: Int,
        resNum: Int,
        topForumNum: Int? = 0,
    ): Flow<ForumGuideBean>

    /**
     * 关注吧列表
     * @param sortType 排序方式
     * @param callFrom 1来自主页?(包含热搜数据),3 来自签到页?
     */
    fun allForumGuideFlow(
        sortType: Int? = 3,
        callFrom: Int? = 3,
    ): Flow<ForumGuideBean>
}
