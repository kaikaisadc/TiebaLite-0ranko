package com.huanchengfly.tieba.post.api.interfaces.impls

import com.huanchengfly.tieba.post.App
import com.huanchengfly.tieba.post.api.ClientVersion
import com.huanchengfly.tieba.post.api.buildAdParam
import com.huanchengfly.tieba.post.api.buildAppPosInfo
import com.huanchengfly.tieba.post.api.buildCommonRequest
import com.huanchengfly.tieba.post.api.buildProtobufRequestBody
import com.huanchengfly.tieba.post.api.getScreenHeight
import com.huanchengfly.tieba.post.api.getScreenWidth
import com.huanchengfly.tieba.post.api.interfaces.TiebaForumApi
import com.huanchengfly.tieba.post.api.models.CommonResponse
import com.huanchengfly.tieba.post.api.models.ForumGuideBean
import com.huanchengfly.tieba.post.api.models.ForumRecommend
import com.huanchengfly.tieba.post.api.models.GetForumListBean
import com.huanchengfly.tieba.post.api.models.LikeForumResultBean
import com.huanchengfly.tieba.post.api.models.MSignBean
import com.huanchengfly.tieba.post.api.models.SignResultBean
import com.huanchengfly.tieba.post.api.models.protos.GeneralTabList.GeneralTabListRequest
import com.huanchengfly.tieba.post.api.models.protos.GeneralTabList.GeneralTabListRequestData
import com.huanchengfly.tieba.post.api.models.protos.GeneralTabList.GeneralTabListResponse
import com.huanchengfly.tieba.post.api.models.protos.forumGuide.ForumGuideRequest
import com.huanchengfly.tieba.post.api.models.protos.forumGuide.ForumGuideRequestData
import com.huanchengfly.tieba.post.api.models.protos.forumGuide.ForumGuideResponse
import com.huanchengfly.tieba.post.api.models.protos.forumRecommend.ForumRecommendRequest
import com.huanchengfly.tieba.post.api.models.protos.forumRecommend.ForumRecommendRequestData
import com.huanchengfly.tieba.post.api.models.protos.forumRecommend.ForumRecommendResponse
import com.huanchengfly.tieba.post.api.models.protos.forumRuleDetail.ForumRuleDetailRequest
import com.huanchengfly.tieba.post.api.models.protos.forumRuleDetail.ForumRuleDetailRequestData
import com.huanchengfly.tieba.post.api.models.protos.forumRuleDetail.ForumRuleDetailResponse
import com.huanchengfly.tieba.post.api.models.protos.frsPage.FrsPageRequest
import com.huanchengfly.tieba.post.api.models.protos.frsPage.FrsPageRequestData
import com.huanchengfly.tieba.post.api.models.protos.frsPage.FrsPageResponse
import com.huanchengfly.tieba.post.api.models.protos.getBawuInfo.GetBawuInfoRequest
import com.huanchengfly.tieba.post.api.models.protos.getBawuInfo.GetBawuInfoRequestData
import com.huanchengfly.tieba.post.api.models.protos.getBawuInfo.GetBawuInfoResponse
import com.huanchengfly.tieba.post.api.models.protos.getForumDetail.GetForumDetailRequest
import com.huanchengfly.tieba.post.api.models.protos.getForumDetail.GetForumDetailRequestData
import com.huanchengfly.tieba.post.api.models.protos.getForumDetail.GetForumDetailResponse
import com.huanchengfly.tieba.post.api.models.protos.getHistoryForum.GetHistoryForumRequest
import com.huanchengfly.tieba.post.api.models.protos.getHistoryForum.GetHistoryForumRequestData
import com.huanchengfly.tieba.post.api.models.protos.getHistoryForum.GetHistoryForumResponse
import com.huanchengfly.tieba.post.api.models.protos.getLevelInfo.GetLevelInfoRequest
import com.huanchengfly.tieba.post.api.models.protos.getLevelInfo.GetLevelInfoRequestData
import com.huanchengfly.tieba.post.api.models.protos.getLevelInfo.GetLevelInfoResponse
import com.huanchengfly.tieba.post.api.models.protos.getMemberInfo.GetMemberInfoRequest
import com.huanchengfly.tieba.post.api.models.protos.getMemberInfo.GetMemberInfoRequestData
import com.huanchengfly.tieba.post.api.models.protos.getMemberInfo.GetMemberInfoResponse
import com.huanchengfly.tieba.post.api.models.protos.threadList.AdParam
import com.huanchengfly.tieba.post.api.models.protos.threadList.ThreadListRequest
import com.huanchengfly.tieba.post.api.models.protos.threadList.ThreadListRequestData
import com.huanchengfly.tieba.post.api.models.protos.threadList.ThreadListResponse
import com.huanchengfly.tieba.post.api.models.web.ForumHome
import com.huanchengfly.tieba.post.api.retrofit.ApiResult
import com.huanchengfly.tieba.post.api.retrofit.RetrofitTiebaApi
import com.huanchengfly.tieba.post.api.urlEncode
import com.huanchengfly.tieba.post.utils.AccountUtil
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import retrofit2.Call

internal object MixedTiebaForumApi : TiebaForumApi {
    override fun forumRecommend(): Call<ForumRecommend> =
        RetrofitTiebaApi.MINI_TIEBA_API.forumRecommend()

    override fun forumRecommendAsync(): Deferred<ApiResult<ForumRecommend>> =
        RetrofitTiebaApi.MINI_TIEBA_API.forumRecommendAsync()

    override fun forumRecommendFlow(): Flow<ForumRecommend> =
        RetrofitTiebaApi.MINI_TIEBA_API.forumRecommendFlow()

    override fun forumHomeAsync(sortType: Int, page: Int): Deferred<ApiResult<ForumHome>> {
        return RetrofitTiebaApi.WEB_TIEBA_API.getForumHomeAsync(
            sortType,
            page,
            20,
            "",
            ""
        )
    }

    override fun unlikeForum(
        forumId: String,
        forumName: String,
        tbs: String
    ): Call<CommonResponse> = RetrofitTiebaApi.MINI_TIEBA_API.unlikeForum(forumId, forumName, tbs)

    override fun unlikeForumFlow(
        forumId: String,
        forumName: String,
        tbs: String
    ): Flow<CommonResponse> =
        RetrofitTiebaApi.OFFICIAL_TIEBA_API.unfavolike(forumId, forumName, tbs)

    override fun likeForum(
        forumId: String, forumName: String, tbs: String
    ): Call<LikeForumResultBean> =
        RetrofitTiebaApi.MINI_TIEBA_API.likeForum(forumId, forumName, tbs)

    override fun likeForumFlow(
        forumId: String,
        forumName: String,
        tbs: String
    ): Flow<LikeForumResultBean> =
        RetrofitTiebaApi.MINI_TIEBA_API.likeForumFlow(forumId, forumName, tbs)

    override fun signAsync(forumName: String, tbs: String): Deferred<ApiResult<SignResultBean>> =
        RetrofitTiebaApi.MINI_TIEBA_API.signAsync(forumName, tbs)

    override fun signFlow(forumId: String, forumName: String, tbs: String): Flow<SignResultBean> =
        RetrofitTiebaApi.OFFICIAL_TIEBA_API.signFlow(forumId, forumName, tbs)

    override fun getForumListFlow(): Flow<GetForumListBean> =
        RetrofitTiebaApi.OFFICIAL_TIEBA_API.getForumListFlow()

    override fun mSign(
        forumIds: String,
        tbs: String
    ): Flow<MSignBean> =
        RetrofitTiebaApi.OFFICIAL_TIEBA_API.mSignFlow(forumIds, tbs)

    override fun forumRecommendNewFlow(
        sortType: Int
    ): Flow<ForumRecommendResponse> {
        return RetrofitTiebaApi.OFFICIAL_PROTOBUF_TIEBA_API.forumRecommendFlow(
            buildProtobufRequestBody(
                ForumRecommendRequest(
                    ForumRecommendRequestData(
                        common = buildCommonRequest(),
                        like_forum = 1,
                        recommend = 1,
                        sort_type = sortType,
                        topic = 0
                    )
                )
            )
        )
    }

    override fun forumGuideNewFlow(
        sortType: Int,
    ): Flow<ForumGuideResponse> {
        return RetrofitTiebaApi.OFFICIAL_PROTOBUF_TIEBA_API.forumGuideFlow(
            buildProtobufRequestBody(
                ForumGuideRequest(
                    ForumGuideRequestData(
                        sort_type = sortType,
                        call_from = 0
                    )
                ),
                clientVersion = ClientVersion.TIEBA_V12
            ),
        )
    }

    override fun frsPage(
        forumName: String,
        page: Int,
        loadType: Int,
        sortType: Int,
        goodClassifyId: Int?
    ): Flow<FrsPageResponse> {
        return RetrofitTiebaApi.OFFICIAL_PROTOBUF_TIEBA_V12_API.frsPageFlow(
            buildProtobufRequestBody(
                FrsPageRequest(
                    FrsPageRequestData(
                        ad_param = buildAdParam(),
                        app_pos = buildAppPosInfo(),
                        call_from = 0,
                        category_id = 0,
                        cid = goodClassifyId ?: 0,
                        common = buildCommonRequest(clientVersion = ClientVersion.TIEBA_V12),
                        ctime = 0,
                        data_size = 0,
                        hot_thread_id = 0,
                        is_default_navtab = 0,
                        is_good = if (goodClassifyId != null) 1 else 0,
                        is_selection = 0,
                        kw = forumName.urlEncode(),
                        last_click_tid = 0,
                        load_type = loadType,
                        net_error = 0,
                        pn = page,
                        q_type = 2,
                        rn = 90,
                        rn_need = 30,
                        scr_dip = App.ScreenInfo.DENSITY.toDouble(),
                        scr_h = getScreenHeight(),
                        scr_w = getScreenWidth(),
                        sort_type = sortType,
                        st_param = 0,
                        st_type = "recom_flist",
                        up_schema = "",
                        with_group = 1,
                        yuelaou_locate = ""
                    )
                ),
                clientVersion = ClientVersion.TIEBA_V12
            ),
            forumName = forumName.urlEncode()
        )
    }

    override fun threadList(
        forumId: Long,
        forumName: String,
        page: Int,
        sortType: Int,
        threadIds: String
    ): Flow<ThreadListResponse> {
        return RetrofitTiebaApi.OFFICIAL_PROTOBUF_TIEBA_V12_API.threadListFlow(
            buildProtobufRequestBody(
                ThreadListRequest(
                    ThreadListRequestData(
                        ad_param = AdParam(3, 0, null),
                        app_pos = buildAppPosInfo(),
                        common = buildCommonRequest(clientVersion = ClientVersion.TIEBA_V12),
                        scr_dip = App.ScreenInfo.DENSITY.toDouble(),
                        scr_h = getScreenHeight(),
                        scr_w = getScreenWidth(),
                        forum_id = forumId,
                        forum_name = forumName,
                        pn = page,
                        q_type = 2,
                        user_id = AccountUtil.getUid()?.toLongOrNull(),
                        thread_ids = threadIds,
                        sort_type = sortType,
                        need_abstract = 0,
                        st_type = 0,
                        last_click_tid = 0
                    )
                ),
                clientVersion = ClientVersion.TIEBA_V12
            )
        )
    }

    override fun generalTabList(
        forumId: Long,
        forumName: String,
        tabId: Int,
        tabType: Int,
        tabName: String,
        isGeneralTab: Int,
        pn: Int,
        sortType: Int,
        lastThreadId: Long,
        isDefaultNavTab: Int,
    ): Flow<GeneralTabListResponse> {
        return RetrofitTiebaApi.OFFICIAL_PROTOBUF_TIEBA_POST_API.generalTabListFlow(
            buildProtobufRequestBody(
                GeneralTabListRequest(
                    GeneralTabListRequestData(
                        common = buildCommonRequest(clientVersion = ClientVersion.TIEBA_V12),
                        tab_id = tabId,
                        forum_id = forumId,
                        pn = pn,
                        rn = 30,
                        scr_w = getScreenWidth(),
                        scr_h = getScreenHeight(),
                        scr_dip = App.ScreenInfo.DENSITY.toInt(),
                        last_thread_id = lastThreadId,
                        is_default_navtab = isDefaultNavTab,
                        tab_name = tabName,
                        is_general_tab = isGeneralTab,
                        sort_type = sortType,
                        tab_type = tabType,
                        ad_ext_params = "",
                        ad_bear_context = "",
                        has_ad_bear = 0,
                        ad_bear_sid = "",
                        ad_bear_sid_price = 0.0,
                        request_times = 0,
                        frs_common_info = "",
                        is_newfrs = 1,
                        is_video_doublerow = 0,
                    )
                ),
                clientVersion = ClientVersion.TIEBA_V12
            )
        )
    }

    override fun getForumDetailFlow(forumId: Long): Flow<GetForumDetailResponse> {
        return RetrofitTiebaApi.OFFICIAL_PROTOBUF_TIEBA_V12_API.getForumDetailFlow(
            buildProtobufRequestBody(
                GetForumDetailRequest(
                    GetForumDetailRequestData(
                        common = buildCommonRequest(clientVersion = ClientVersion.TIEBA_V12),
                        forum_id = forumId,
                    )
                ),
                clientVersion = ClientVersion.TIEBA_V12,
                needSToken = true
            )
        )
    }

    override fun getBawuInfoFlow(forumId: Long): Flow<GetBawuInfoResponse> {
        return RetrofitTiebaApi.OFFICIAL_PROTOBUF_TIEBA_V12_API.getBawuInfoFlow(
            buildProtobufRequestBody(
                GetBawuInfoRequest(
                    GetBawuInfoRequestData(
                        common = buildCommonRequest(clientVersion = ClientVersion.TIEBA_V12),
                        forum_id = forumId,
                    )
                ),
                clientVersion = ClientVersion.TIEBA_V12,
                needSToken = true
            )
        )
    }

    override fun getLevelInfoFlow(forumId: Long): Flow<GetLevelInfoResponse> {
        return RetrofitTiebaApi.OFFICIAL_PROTOBUF_TIEBA_V12_API.getLevelInfoFlow(
            buildProtobufRequestBody(
                GetLevelInfoRequest(
                    GetLevelInfoRequestData(
                        common = buildCommonRequest(clientVersion = ClientVersion.TIEBA_V12),
                        forum_id = forumId,
                    )
                ),
                clientVersion = ClientVersion.TIEBA_V12,
                needSToken = true
            )
        )
    }

    override fun getMemberInfoFlow(forumId: Long): Flow<GetMemberInfoResponse> {
        return RetrofitTiebaApi.OFFICIAL_PROTOBUF_TIEBA_V12_API.getMemberInfoFlow(
            buildProtobufRequestBody(
                GetMemberInfoRequest(
                    GetMemberInfoRequestData(
                        common = buildCommonRequest(clientVersion = ClientVersion.TIEBA_V12),
                        forum_id = forumId,
                    )
                ),
                clientVersion = ClientVersion.TIEBA_V12,
                needSToken = true
            )
        )
    }

    override fun forumRuleDetailFlow(forumId: Long): Flow<ForumRuleDetailResponse> {
        return RetrofitTiebaApi.OFFICIAL_PROTOBUF_TIEBA_V12_API.forumRuleDetailFlow(
            buildProtobufRequestBody(
                ForumRuleDetailRequest(
                    ForumRuleDetailRequestData(
                        common = buildCommonRequest(clientVersion = ClientVersion.TIEBA_V12),
                        forum_id = forumId,
                    )
                ),
                clientVersion = ClientVersion.TIEBA_V12,
                needSToken = true
            )
        )
    }

    override fun getHistoryForumFlow(history: String): Flow<GetHistoryForumResponse> {
        return RetrofitTiebaApi.OFFICIAL_PROTOBUF_TIEBA_V12_API.getHistoryForumFlow(
            buildProtobufRequestBody(
                GetHistoryForumRequest(
                    GetHistoryForumRequestData(
                        common = buildCommonRequest(clientVersion = ClientVersion.TIEBA_V12),
                        history = history,
                    )
                ),
                clientVersion = ClientVersion.TIEBA_V12,
                needSToken = true
            )
        )
    }

    override fun forumGuideFlow(
        sortType: Int?,
        callFrom: Int?,
        pageNo: Int,
        resNum: Int,
        topForumNum: Int?,
    ): Flow<ForumGuideBean> =
        RetrofitTiebaApi.OFFICIAL_TIEBA_API.forumGuideFlow(
            sortType,
            callFrom,
            pageNo,
            resNum,
            topForumNum
        )

    /**
     * 关注吧列表
     * @param sortType 排序方式
     * @param callFrom 1来自主页?(包含热搜数据),3 来自签到页?
     */
    override fun allForumGuideFlow(
        sortType: Int?,
        callFrom: Int?,
    ): Flow<ForumGuideBean> = flow {
        var currentPage = 1
        var hasMore = true
        var finalBean: ForumGuideBean? = null
        val allLikeForums = mutableListOf<ForumGuideBean.LikeForum>()

        while (hasMore) {
            val response = forumGuideFlow(
                sortType = sortType,
                callFrom = callFrom,
                pageNo = currentPage,
                resNum = 50,
                topForumNum = 0
            ).first()
            if (finalBean == null) {
                finalBean = response
            }
            response.likeForum.let { allLikeForums.addAll(it) }
            hasMore = response.likeForumHasMore == true
            currentPage++
        }

        finalBean!!.likeForum = allLikeForums
        emit(finalBean)
    }.flowOn(Dispatchers.IO)
}
