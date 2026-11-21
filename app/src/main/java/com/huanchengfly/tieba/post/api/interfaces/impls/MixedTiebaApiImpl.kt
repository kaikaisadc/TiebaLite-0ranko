package com.huanchengfly.tieba.post.api.interfaces.impls

import android.text.TextUtils
import com.huanchengfly.tieba.post.App
import com.huanchengfly.tieba.post.api.ClientVersion
import com.huanchengfly.tieba.post.api.Param
import com.huanchengfly.tieba.post.api.booleanToString
import com.huanchengfly.tieba.post.api.buildAppPosInfo
import com.huanchengfly.tieba.post.api.buildCommonRequest
import com.huanchengfly.tieba.post.api.buildProtobufRequestBody
import com.huanchengfly.tieba.post.api.getScreenHeight
import com.huanchengfly.tieba.post.api.getScreenWidth
import com.huanchengfly.tieba.post.api.interfaces.ITiebaApi
import com.huanchengfly.tieba.post.api.interfaces.TiebaForumApi
import com.huanchengfly.tieba.post.api.interfaces.TiebaNotificationsApi
import com.huanchengfly.tieba.post.api.interfaces.TiebaPostActionsApi
import com.huanchengfly.tieba.post.api.interfaces.TiebaSearchApi
import com.huanchengfly.tieba.post.api.models.AgreeBean
import com.huanchengfly.tieba.post.api.models.CollectDataBean
import com.huanchengfly.tieba.post.api.models.CommonResponse
import com.huanchengfly.tieba.post.api.models.FollowBean
import com.huanchengfly.tieba.post.api.models.FollowListBean
import com.huanchengfly.tieba.post.api.models.GetUserBlackInfoBean
import com.huanchengfly.tieba.post.api.models.InitNickNameBean
import com.huanchengfly.tieba.post.api.models.LoginBean
import com.huanchengfly.tieba.post.api.models.NewCollectDataBean
import com.huanchengfly.tieba.post.api.models.PermissionListBean
import com.huanchengfly.tieba.post.api.models.PersonalizedBean
import com.huanchengfly.tieba.post.api.models.PicPageBean
import com.huanchengfly.tieba.post.api.models.Profile
import com.huanchengfly.tieba.post.api.models.ProfileBean
import com.huanchengfly.tieba.post.api.models.SubFloorListBean
import com.huanchengfly.tieba.post.api.models.Sync
import com.huanchengfly.tieba.post.api.models.ThreadContentBean
import com.huanchengfly.tieba.post.api.models.ThreadStoreBean
import com.huanchengfly.tieba.post.api.models.TopicDetailBean
import com.huanchengfly.tieba.post.api.models.UserLikeForumBean
import com.huanchengfly.tieba.post.api.models.UserPostBean
import com.huanchengfly.tieba.post.api.models.protos.getUserInfo.GetUserInfoRequest
import com.huanchengfly.tieba.post.api.models.protos.getUserInfo.GetUserInfoRequestData
import com.huanchengfly.tieba.post.api.models.protos.getUserInfo.GetUserInfoResponse
import com.huanchengfly.tieba.post.api.models.protos.hotThreadList.HotThreadListRequest
import com.huanchengfly.tieba.post.api.models.protos.hotThreadList.HotThreadListRequestData
import com.huanchengfly.tieba.post.api.models.protos.hotThreadList.HotThreadListResponse
import com.huanchengfly.tieba.post.api.models.protos.pbFloor.PbFloorRequest
import com.huanchengfly.tieba.post.api.models.protos.pbFloor.PbFloorRequestData
import com.huanchengfly.tieba.post.api.models.protos.pbFloor.PbFloorResponse
import com.huanchengfly.tieba.post.api.models.protos.pbPage.PbPageRequest
import com.huanchengfly.tieba.post.api.models.protos.pbPage.PbPageRequestData
import com.huanchengfly.tieba.post.api.models.protos.pbPage.PbPageResponse
import com.huanchengfly.tieba.post.api.models.protos.personalized.PersonalizedRequest
import com.huanchengfly.tieba.post.api.models.protos.personalized.PersonalizedRequestData
import com.huanchengfly.tieba.post.api.models.protos.personalized.PersonalizedResponse
import com.huanchengfly.tieba.post.api.models.protos.profile.ProfileRequest
import com.huanchengfly.tieba.post.api.models.protos.profile.ProfileRequestData
import com.huanchengfly.tieba.post.api.models.protos.profile.ProfileResponse
import com.huanchengfly.tieba.post.api.models.protos.threadList.AdParam
import com.huanchengfly.tieba.post.api.models.protos.topicList.TopicListRequest
import com.huanchengfly.tieba.post.api.models.protos.topicList.TopicListRequestData
import com.huanchengfly.tieba.post.api.models.protos.topicList.TopicListResponse
import com.huanchengfly.tieba.post.api.models.protos.userLike.UserLikeRequest
import com.huanchengfly.tieba.post.api.models.protos.userLike.UserLikeRequestData
import com.huanchengfly.tieba.post.api.models.protos.userLike.UserLikeResponse
import com.huanchengfly.tieba.post.api.models.protos.userPost.UserPostRequest
import com.huanchengfly.tieba.post.api.models.protos.userPost.UserPostRequestData
import com.huanchengfly.tieba.post.api.models.protos.userPost.UserPostResponse
import com.huanchengfly.tieba.post.api.models.web.HotMessageListBean
import com.huanchengfly.tieba.post.api.retrofit.ApiResult
import com.huanchengfly.tieba.post.api.retrofit.RetrofitTiebaApi
import com.huanchengfly.tieba.post.api.retrofit.body.MyMultipartBody
import com.huanchengfly.tieba.post.models.DislikeBean
import com.huanchengfly.tieba.post.models.MyInfoBean
import com.huanchengfly.tieba.post.toJson
import com.huanchengfly.tieba.post.utils.AccountUtil
import java.io.File
import java.net.URLEncoder
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Call

object MixedTiebaApiImpl : ITiebaApi,
    TiebaSearchApi by MixedTiebaSearchApi,
    TiebaPostActionsApi by MixedTiebaPostActionsApi,
    TiebaNotificationsApi by MixedTiebaNotificationsApi,
    TiebaForumApi by MixedTiebaForumApi {
    override fun personalized(loadType: Int, page: Int): Call<PersonalizedBean> =
        RetrofitTiebaApi.MINI_TIEBA_API.personalized(loadType, page)

    override fun personalizedAsync(
        loadType: Int,
        page: Int
    ): Deferred<ApiResult<PersonalizedBean>> =
        RetrofitTiebaApi.MINI_TIEBA_API.personalizedAsync(loadType, page)

    override fun personalizedFlow(loadType: Int, page: Int): Flow<PersonalizedBean> {
        return RetrofitTiebaApi.OFFICIAL_TIEBA_API.personalizedFlow(loadType, page)
    }

    override fun personalizedProtoFlow(loadType: Int, page: Int): Flow<PersonalizedResponse> {
        return RetrofitTiebaApi.OFFICIAL_PROTOBUF_TIEBA_V12_API.personalizedFlow(
            buildProtobufRequestBody(
                data = PersonalizedRequest(
                    PersonalizedRequestData(
                        app_pos = buildAppPosInfo(),
                        common = buildCommonRequest(clientVersion = ClientVersion.TIEBA_V12),
                        load_type = loadType,
                        pn = page,
                        need_tags = 0,
                        page_thread_count = 11,
                        pre_ad_thread_count = 0,
                        sug_count = 0,
                        tag_code = 0,
                        q_type = 1,
                        need_forumlist = 0,
                        new_net_type = 1,
                        new_install = 0,
                        request_times = 0,
                        invoke_source = "",
                        scr_dip = App.ScreenInfo.DENSITY.toDouble(),
                        scr_h = getScreenHeight(),
                        scr_w = getScreenWidth()
                    )
                ),
                clientVersion = ClientVersion.TIEBA_V12
            )
        )
    }

    override fun myProfileAsync(): Deferred<ApiResult<com.huanchengfly.tieba.post.api.models.web.Profile>> =
        RetrofitTiebaApi.WEB_TIEBA_API.myProfileAsync("json", "", "")

    override fun opAgree(
        threadId: String,
        postId: String,
        opType: Int
    ): Call<AgreeBean> =
        RetrofitTiebaApi.MINI_TIEBA_API.agree(postId, threadId, op_type = opType)

    override fun disagree(
        threadId: String,
        postId: String,
        opType: Int
    ): Call<AgreeBean> =
        RetrofitTiebaApi.MINI_TIEBA_API.disagree(postId, threadId, op_type = opType)

    override fun opAgreeFlow(
        threadId: String,
        postId: String,
        opType: Int,
        objType: Int,
        agreeType: Int,
    ): Flow<AgreeBean> =
        RetrofitTiebaApi.MINI_TIEBA_API.opAgreeFlow(
            threadId,
            postId,
            opType = opType,
            objType = objType,
            agreeType = agreeType
        )

    override fun disagreeFlow(
        threadId: String,
        postId: String,
        opType: Int
    ): Flow<AgreeBean> =
        RetrofitTiebaApi.MINI_TIEBA_API.disagreeFlow(postId, threadId, op_type = opType)

    override fun floor(
        threadId: String, page: Int, postId: String?, subPostId: String?
    ): Call<SubFloorListBean> =
        RetrofitTiebaApi.OFFICIAL_TIEBA_API.floor(threadId, page, postId, subPostId)

    override fun userLikeForum(
        uid: String, page: Int
    ): Call<UserLikeForumBean> {
        val myUid = AccountUtil.getUid()
        return RetrofitTiebaApi.MINI_TIEBA_API.userLikeForum(
            page = page,
            uid = myUid,
            friendUid = if (!TextUtils.equals(uid, myUid)) uid else null,
            is_guest = if (!TextUtils.equals(uid, myUid)) "1" else null

        )
    }

    override fun userPost(
        uid: String, page: Int, isThread: Boolean
    ): Call<UserPostBean> =
        RetrofitTiebaApi.MINI_TIEBA_API.userPost(uid, page, if (isThread) 1 else 0)

    override fun picPage(
        forumId: String,
        forumName: String,
        threadId: String,
        seeLz: Boolean,
        picId: String,
        picIndex: String,
        objType: String,
        prev: Boolean
    ): Call<PicPageBean> = RetrofitTiebaApi.MINI_TIEBA_API.picPage(
        forumId,
        forumName,
        threadId,
        picId,
        picIndex,
        objType,
        prev = if (prev) 10 else 0,
        next = if (prev) 0 else 10,
        not_see_lz = if (seeLz) 0 else 1
    )

    override fun picPageFlow(
        forumId: String,
        forumName: String,
        threadId: String,
        seeLz: Boolean,
        picId: String,
        picIndex: String,
        objType: String,
        prev: Boolean
    ): Flow<PicPageBean> = RetrofitTiebaApi.MINI_TIEBA_API.picPageFlow(
        forumId,
        forumName,
        threadId,
        picId,
        picIndex,
        objType,
        prev = if (prev) 10 else 0,
        next = if (prev) 0 else 10,
        not_see_lz = if (seeLz) 0 else 1
    )

    override fun profile(uid: String): Call<ProfileBean> =
        RetrofitTiebaApi.MINI_TIEBA_API.profile(uid)

    override fun profileFlow(uid: String): Flow<Profile> =
        RetrofitTiebaApi.OFFICIAL_TIEBA_API.profileFlow(uid)

    override fun threadStore(page: Int, pageSize: Int): Call<ThreadStoreBean> =
        RetrofitTiebaApi.NEW_TIEBA_API.threadStore(
            pageSize,
            pageSize * page,
            AccountUtil.getUid()
        )

    override fun threadStoreFlow(page: Int, pageSize: Int): Flow<ThreadStoreBean> =
        RetrofitTiebaApi.OFFICIAL_TIEBA_API.threadStoreFlow(
            pageSize,
            pageSize * page
        )

    override fun removeStore(threadId: String, tbs: String): Call<CommonResponse> =
        RetrofitTiebaApi.NEW_TIEBA_API.removeStore(threadId, tbs)

    override fun removeStoreFlow(
        threadId: Long,
        forumId: Long?,
        tbs: String
    ): Flow<CommonResponse> =
        RetrofitTiebaApi.OFFICIAL_TIEBA_API.removeStoreFlow(
            threadId = threadId.toString(),
            forumId = forumId?.toString() ?: "null",
            tbs = tbs
        )

    override fun removeStoreFlow(threadId: String): Flow<CommonResponse> =
        RetrofitTiebaApi.OFFICIAL_TIEBA_API.removeStoreFlow(threadId)

    override fun addStore(threadId: String, postId: String, tbs: String): Call<CommonResponse> =
        RetrofitTiebaApi.NEW_TIEBA_API.addStore(
            listOf(
                CollectDataBean(
                    threadId,
                    postId,
                    "0",
                    "0"
                )
            ).toJson(),
            tbs
        )

    override fun addStoreAsync(threadId: Long, postId: Long): Deferred<ApiResult<CommonResponse>> =
        RetrofitTiebaApi.OFFICIAL_TIEBA_API.addStoreAsync(
            listOf(
                NewCollectDataBean(
                    threadId.toString(),
                    postId.toString(),
                    status = 1
                )
            ).toJson()
        )

    override fun addStoreFlow(threadId: Long, postId: Long): Flow<CommonResponse> =
        RetrofitTiebaApi.OFFICIAL_TIEBA_API.addStoreFlow(
            listOf(
                NewCollectDataBean(
                    threadId.toString(),
                    postId.toString(),
                    status = 1
                )
            ).toJson()
        )

    override fun threadContent(
        threadId: String, page: Int, seeLz: Boolean, reverse: Boolean
    ): Call<ThreadContentBean> = RetrofitTiebaApi.OFFICIAL_TIEBA_API.threadContent(
        threadId,
        page,
        last = if (reverse) "1" else null,
        r = if (reverse) "1" else null,
        lz = if (seeLz) 1 else 0
    )

    override fun threadContent(
        threadId: String, postId: String?, seeLz: Boolean, reverse: Boolean
    ): Call<ThreadContentBean> = RetrofitTiebaApi.OFFICIAL_TIEBA_API.threadContent(
        threadId,
        postId,
        last = if (reverse) "1" else null,
        r = if (reverse) "1" else null,
        lz = if (seeLz) 1 else 0
    )

    override fun threadContentAsync(
        threadId: String,
        page: Int,
        seeLz: Boolean,
        reverse: Boolean
    ): Deferred<ApiResult<ThreadContentBean>> =
        RetrofitTiebaApi.OFFICIAL_TIEBA_API.threadContentAsync(
            threadId,
            page,
            last = if (reverse) "1" else null,
            r = if (reverse) "1" else null,
            lz = if (seeLz) 1 else 0
        )

    override fun threadContentAsync(
        threadId: String,
        postId: String?,
        seeLz: Boolean,
        reverse: Boolean
    ): Deferred<ApiResult<ThreadContentBean>> =
        RetrofitTiebaApi.OFFICIAL_TIEBA_API.threadContentAsync(
            threadId,
            postId,
            last = if (reverse) "1" else null,
            r = if (reverse) "1" else null,
            lz = if (seeLz) 1 else 0
        )

    override fun submitDislike(
        dislikeBean: DislikeBean,
        stoken: String
    ): Call<CommonResponse> =
        RetrofitTiebaApi.OFFICIAL_TIEBA_API.submitDislike(
            listOf(dislikeBean).toJson(),
            stoken = stoken
        )

    override fun submitDislikeFlow(dislikeBean: DislikeBean): Flow<CommonResponse> =
        RetrofitTiebaApi.OFFICIAL_TIEBA_API.submitDislikeFlow(listOf(dislikeBean).toJson())

    override fun follow(
        portrait: String, tbs: String
    ): Call<CommonResponse> = RetrofitTiebaApi.WEB_TIEBA_API.follow(
        "https://tieba.baidu.com/i/?portrait=${
            URLEncoder.encode(
                portrait,
                "UTF-8"
            )
        }&cuid=&auth=&uid=&ssid=&from=&uid=&pu=&bd_page_type=2&auth=&originid=&mo_device=1&tbs=${tbs}&action=follow&op=follow"
    )

    override fun unfollow(
        portrait: String,
        tbs: String
    ): Call<CommonResponse> = RetrofitTiebaApi.WEB_TIEBA_API.follow(
        "https://tieba.baidu.com/i/?portrait=${
            URLEncoder.encode(
                portrait,
                "UTF-8"
            )
        }&cuid=&auth=&uid=&ssid=&from=&uid=&pu=&bd_page_type=2&auth=&originid=&mo_device=1&tbs=${tbs}&action=follow&op=unfollow"
    )

    override fun followFlow(
        portrait: String,
        tbs: String
    ): Flow<FollowBean> = RetrofitTiebaApi.OFFICIAL_TIEBA_API.followFlow(portrait, tbs)

    override fun unfollowFlow(
        portrait: String,
        tbs: String
    ): Flow<CommonResponse> = RetrofitTiebaApi.OFFICIAL_TIEBA_API.unfollowFlow(portrait, tbs)

    override fun followListFlow(page: Int, uid: Long?): Flow<FollowListBean> =
        RetrofitTiebaApi.OFFICIAL_TIEBA_API.followListFlow(page, uid)

    override fun getAllFollowFlow(uid: Long?): Flow<FollowListBean> = flow {
        var currentPage = 1
        var hasMore = true
        var finalBean: FollowListBean? = null
        val allUsers = mutableListOf<FollowListBean.FollowUserBean>()

        while (hasMore) {
            val response = followListFlow(currentPage, uid).first()
            if (finalBean == null) {
                finalBean = response
            }
            allUsers.addAll(response.followList)
            hasMore = response.hasMore == 1
            currentPage++
        }

        finalBean?.apply {
            this.followList = allUsers
        }?.let {
            emit(it)
        }
    }.flowOn(Dispatchers.IO)

    override fun hotMessageList(): Call<HotMessageListBean> =
        RetrofitTiebaApi.WEB_TIEBA_API.hotMessageList()

    override fun myInfo(cookie: String): Call<MyInfoBean> =
        RetrofitTiebaApi.WEB_TIEBA_API.myInfo(cookie)

    override fun myInfoAsync(cookie: String): Deferred<ApiResult<MyInfoBean>> =
        RetrofitTiebaApi.WEB_TIEBA_API.myInfoAsync(cookie)

    override fun myInfoFlow(cookie: String): Flow<MyInfoBean> =
        RetrofitTiebaApi.WEB_TIEBA_API.myInfoFlow(cookie)

    override fun topicDetailFlow(
        topicId: String,
        topicName: String,
        isNew: Int,
        isShare: Int,
        page: Int,
        pageSize: Int,
        offset: Int,
        lastId: String
    ): Flow<TopicDetailBean> =
        RetrofitTiebaApi.HYBRID_TIEBA_API.topicDetailFlow(
            topicId,
            topicName,
            isNew,
            isShare,
            page,
            pageSize,
            offset,
            lastId
        )

    override fun initNickNameFlow(): Flow<InitNickNameBean> =
        RetrofitTiebaApi.OFFICIAL_TIEBA_API.initNickNameFlow()

    override fun initNickNameFlow(bduss: String, sToken: String): Flow<InitNickNameBean> =
        RetrofitTiebaApi.OFFICIAL_TIEBA_API.initNickNameFlow(bduss, sToken)

    override fun loginFlow(): Flow<LoginBean> =
        RetrofitTiebaApi.OFFICIAL_TIEBA_API.loginFlow()

    override fun loginFlow(bduss: String, sToken: String): Flow<LoginBean> =
        RetrofitTiebaApi.OFFICIAL_TIEBA_API.loginFlow("$bduss|", sToken, null)

    override fun profileModifyFlow(
        birthdayShowStatus: Boolean,
        birthdayTime: String,
        intro: String,
        sex: String,
        nickName: String,
    ): Flow<CommonResponse> =
        RetrofitTiebaApi.OFFICIAL_TIEBA_API.profileModify(
            birthdayShowStatus.booleanToString(),
            birthdayTime,
            intro,
            sex,
            nickName
        )

    override fun imgPortrait(file: File): Flow<CommonResponse> {
        return RetrofitTiebaApi.OFFICIAL_TIEBA_API.imgPortrait(
            MyMultipartBody.Builder("--------7da3d81520810*").apply {
                setType(MyMultipartBody.FORM)
                addFormDataPart(Param.CLIENT_VERSION, ClientVersion.TIEBA_V12.version)
                addFormDataPart("pic", "file", file.asRequestBody())
            }.build()
        )
    }

    override fun userLikeFlow(
        pageTag: String,
        lastRequestUnix: Long,
        loadType: Int
    ): Flow<UserLikeResponse> {
        return RetrofitTiebaApi.OFFICIAL_PROTOBUF_TIEBA_API.userLikeFlow(
            buildProtobufRequestBody(
                UserLikeRequest(
                    UserLikeRequestData(
                        common = buildCommonRequest(),
                        pageTag = pageTag,
                        lastRequestUnix = lastRequestUnix,
                        followType = 1,
                        loadType = loadType
                    )
                )
            )
        )
    }

    override fun hotThreadListFlow(tabCode: String): Flow<HotThreadListResponse> {
        return RetrofitTiebaApi.OFFICIAL_PROTOBUF_TIEBA_API.hotThreadListFlow(
            buildProtobufRequestBody(
                HotThreadListRequest(
                    HotThreadListRequestData(
                        common = buildCommonRequest(),
                        tabCode = tabCode,
                        tabId = "1"
                    )
                )
            )
        )
    }

    override fun topicListFlow(): Flow<TopicListResponse> {
        return RetrofitTiebaApi.OFFICIAL_PROTOBUF_TIEBA_API.topicListFlow(
            buildProtobufRequestBody(
                TopicListRequest(
                    TopicListRequestData(
                        common = buildCommonRequest(),
                        call_from = "newbang",
                        list_type = "all",
                        need_tab_list = "0",
                        fid = 0L
                    )
                )
            )
        )
    }

    override fun syncFlow(clientId: String?): Flow<Sync> =
        RetrofitTiebaApi.OFFICIAL_TIEBA_API.sync(clientId)

    override fun userProfileFlow(uid: Long): Flow<ProfileResponse> {
        val selfUid = AccountUtil.getUid()?.toLongOrNull()
        val isSelf = selfUid == uid
        return RetrofitTiebaApi.OFFICIAL_PROTOBUF_TIEBA_V12_API.profileFlow(
            buildProtobufRequestBody(
                ProfileRequest(
                    ProfileRequestData(
                        common = buildCommonRequest(clientVersion = ClientVersion.TIEBA_V12),
                        friend_uid = uid.takeIf { !isSelf },
                        friend_uid_portrait = "",
                        has_plist = 1,
                        is_from_usercenter = 1,
                        is_guest = if (isSelf) 0 else 1,
                        need_post_count = 1,
                        page = 1,
                        pn = 1,
                        q_type = 0,
                        rn = 20,
                        scr_dip = App.ScreenInfo.DENSITY.toDouble(),
                        scr_h = getScreenHeight(),
                        scr_w = getScreenWidth(),
                        uid = selfUid,
                    )
                ),
                clientVersion = ClientVersion.TIEBA_V12
            )
        )
    }

    override suspend fun pbPage(
        threadId: Long,
        page: Int,
        postId: Long,
        seeLz: Boolean,
        back: Boolean,
        sortType: Int,
        forumId: Long?,
        stType: String,
        mark: Int,
        lastPostId: Long?,
    ): PbPageResponse {
        return RetrofitTiebaApi.OFFICIAL_PROTOBUF_TIEBA_V12_API.pbPage(
            buildProtobufRequestBody(
                PbPageRequest(
                    PbPageRequestData(
                        common = buildCommonRequest(clientVersion = ClientVersion.TIEBA_V12),
                        kz = threadId,
                        pid = postId,
                        pn = page,
                        r = sortType,
                        lz = if (seeLz) 1 else 0,
                        forum_id = forumId ?: 0,
                        ad_param = com.huanchengfly.tieba.post.api.models.protos.pbPage.AdParam(
                            load_count = 0,
                            refresh_count = 1,
                            is_req_ad = 1
                        ),
                        mark = mark,
                        last_pid = lastPostId ?: 0,
                        app_pos = buildAppPosInfo(),
                        back = if (back) 1 else 0,
                        banner = 0,
                        broadcast_id = 0,
                        floor_rn = 4,
                        floor_sort_type = 1,
                        from_push = 0,
                        from_smart_frs = 0,
                        immersion_video_comment_source = 0,
                        is_comm_reverse = 0,
                        is_fold_comment_req = 0,
                        is_jumpfloor = 0,
                        jumpfloor_num = 0,
                        need_repost_recommend_forum = 0,
                        obj_locate = "",
                        obj_param1 = "10",
                        obj_source = "",
                        ori_ugc_type = 0,
                        pb_rn = 0,
                        q_type = 2,
                        request_times = 0,
                        rn = 15,
                        s_model = 0,
                        scr_dip = App.ScreenInfo.DENSITY.toDouble(),
                        scr_h = getScreenHeight(),
                        scr_w = getScreenWidth(),
                        similar_from = 0,
                        source_type = 2,
                        st_type = stType,
                        thread_type = 0,
                        weipost = 0,
                        with_floor = 1
                    )
                ),
                clientVersion = ClientVersion.TIEBA_V12
            )
        )
    }

    override suspend fun pbFloor(
        threadId: Long,
        postId: Long,
        forumId: Long,
        page: Int,
        subPostId: Long
    ): PbFloorResponse {
        return RetrofitTiebaApi.OFFICIAL_PROTOBUF_TIEBA_V22_API.pbFloor(
            buildProtobufRequestBody(
                PbFloorRequest(
                    PbFloorRequestData(
                        common = buildCommonRequest(clientVersion = ClientVersion.TIEBA_V22),
                        forum_id = forumId,
                        kz = threadId,
                        pid = postId,
                        pn = page,
                        spid = subPostId,
                        scr_dip = App.ScreenInfo.DENSITY.toDouble(),
                        scr_h = getScreenHeight(),
                        scr_w = getScreenWidth(),
                        is_comm_reverse = 0,
                        ori_ugc_type = 0
                    )
                ),
                clientVersion = ClientVersion.TIEBA_V22,
                needSToken = false
            )
        )
    }

    override fun userPostFlow(uid: Long, page: Int, isThread: Boolean): Flow<UserPostResponse> {
        return RetrofitTiebaApi.OFFICIAL_PROTOBUF_TIEBA_V12_API.userPostFlow(
            buildProtobufRequestBody(
                UserPostRequest(
                    UserPostRequestData(
                        uid = uid,
                        rn = 20,
                        is_thread = if (isThread) 1 else 0,
                        need_content = 1,
                        pn = page,
                        common = buildCommonRequest(clientVersion = ClientVersion.TIEBA_V12),
                        scr_w = getScreenWidth(),
                        scr_h = getScreenHeight(),
                        scr_dip = App.ScreenInfo.DENSITY.toDouble(),
                        q_type = 1,
                        is_view_card = if (isThread) 1 else 0,
                        subtype = 0.takeUnless { isThread },
                    )
                ),
                clientVersion = ClientVersion.TIEBA_V12,
                needSToken = true
            )
        )
    }

    override fun userLikeForumFlow(uid: String, page: Int): Flow<UserLikeForumBean> {
        val myUid = AccountUtil.getUid()
        return RetrofitTiebaApi.OFFICIAL_TIEBA_API.userLikeForumFlow(
            page = page,
            uid = myUid,
            friendUid = if (!TextUtils.equals(uid, myUid)) uid else null,
            is_guest = if (!TextUtils.equals(uid, myUid)) "1" else null
        )
    }

    override fun getUserInfoFlow(): Flow<GetUserInfoResponse> {
        return getUserInfoFlow(AccountUtil.getUid()!!.toLong(), null, null)
    }

    override fun getUserInfoFlow(
        uid: Long,
        bduss: String?,
        sToken: String?,
    ): Flow<GetUserInfoResponse> {
        return RetrofitTiebaApi.OFFICIAL_PROTOBUF_TIEBA_V12_API.getUserInfoFlow(
            buildProtobufRequestBody(
                GetUserInfoRequest(
                    GetUserInfoRequestData(
                        common = buildCommonRequest(
                            clientVersion = ClientVersion.TIEBA_V12,
                            bduss = bduss,
                            stoken = sToken
                        ),
                        uid = uid,
                        scr_w = getScreenWidth()
                    )
                ),
                clientVersion = ClientVersion.TIEBA_V12,
                needSToken = true
            )
        )
    }

    override fun setUserBlackFlow(
        blackUid: Long,
        tbs: String,
        permList: PermissionListBean
    ): Flow<CommonResponse> =
        RetrofitTiebaApi.OFFICIAL_TIEBA_API.setUserBlackFlow(
            blackUid,
            tbs,
            permList.toJson()
        )

    override fun getUserBlackInfoFlow(
        blackUid: Long
    ): Flow<GetUserBlackInfoBean> =
        RetrofitTiebaApi.OFFICIAL_TIEBA_API.getUserBlackFlow(
            blackUid
        )

}
