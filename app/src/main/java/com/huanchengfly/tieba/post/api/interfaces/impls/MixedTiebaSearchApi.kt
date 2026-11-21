package com.huanchengfly.tieba.post.api.interfaces.impls

import com.huanchengfly.tieba.post.api.ClientVersion
import com.huanchengfly.tieba.post.api.booleanToString
import com.huanchengfly.tieba.post.api.buildCommonRequest
import com.huanchengfly.tieba.post.api.buildProtobufRequestBody
import com.huanchengfly.tieba.post.api.interfaces.TiebaSearchApi
import com.huanchengfly.tieba.post.api.models.SearchForumBean
import com.huanchengfly.tieba.post.api.models.SearchThreadBean
import com.huanchengfly.tieba.post.api.models.SearchUserBean
import com.huanchengfly.tieba.post.api.models.protos.searchSug.SearchSugRequest
import com.huanchengfly.tieba.post.api.models.protos.searchSug.SearchSugRequestData
import com.huanchengfly.tieba.post.api.models.protos.searchSug.SearchSugResponse
import com.huanchengfly.tieba.post.api.retrofit.RetrofitTiebaApi
import com.huanchengfly.tieba.post.api.urlEncode
import com.huanchengfly.tieba.post.utils.CuidUtils

internal object MixedTiebaSearchApi : TiebaSearchApi {
    override suspend fun searchUser(keyword: String): SearchUserBean =
        RetrofitTiebaApi.HYBRID_TIEBA_API.searchUser(keyword)

    override suspend fun searchForum(keyword: String): SearchForumBean =
        RetrofitTiebaApi.HYBRID_TIEBA_API.searchForum(keyword)

    override suspend fun searchThread(
        keyword: String, page: Int, sort: Int,
    ): SearchThreadBean =
        RetrofitTiebaApi.HYBRID_TIEBA_API.searchThread(
            keyword,
            page,
            sort
        )

    override suspend fun searchPost(
        keyword: String,
        forumName: String,
        forumId: Long,
        sortType: Int,
        filterType: Int,
        page: Int,
        pageSize: Int,
    ): SearchThreadBean =
        RetrofitTiebaApi.HYBRID_TIEBA_API.searchThread(
            keyword,
            page,
            sortType,
            filterType,
            pageSize,
            forumName,
            ct = 2,
            isUseZonghe = null,
            clientVersion = ClientVersion.TIEBA_V12.version,
            referer = "https://tieba.baidu.com/mo/q/hybrid-usergrow-search/searchGlobal?entryPage=frs&loadingSignal=1&forumName=${forumName.urlEncode()}&forumId=$forumId&customfullscreen=1&nonavigationbar=1&cuid=${CuidUtils.getNewCuid()}&cuid_galaxy2=${CuidUtils.getNewCuid()}&cuid_gid=&timestamp=${System.currentTimeMillis()}&_client_version=${ClientVersion.TIEBA_V12.version}&_client_type=2"
        )

    override suspend fun searchSuggestions(keyword: String, isForum: Boolean): SearchSugResponse {
        return RetrofitTiebaApi.OFFICIAL_PROTOBUF_TIEBA_V12_API.searchSug(
            buildProtobufRequestBody(
                SearchSugRequest(
                    SearchSugRequestData(
                        common = buildCommonRequest(clientVersion = ClientVersion.TIEBA_V12),
                        word = keyword,
                        isforum = isForum.booleanToString()
                    )
                ),
                clientVersion = ClientVersion.TIEBA_V12,
                needSToken = true
            )
        )
    }
}
