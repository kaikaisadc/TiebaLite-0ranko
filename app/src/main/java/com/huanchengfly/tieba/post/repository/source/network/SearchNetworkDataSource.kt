package com.huanchengfly.tieba.post.repository.source.network

import android.text.TextUtils
import com.huanchengfly.tieba.post.api.TiebaApi
import com.huanchengfly.tieba.post.api.models.CommonResponse
import com.huanchengfly.tieba.post.api.models.SearchForumBean
import com.huanchengfly.tieba.post.api.models.SearchThreadBean
import com.huanchengfly.tieba.post.api.models.SearchUserBean
import com.huanchengfly.tieba.post.api.models.protos.searchSug.SearchSugResponseData
import com.huanchengfly.tieba.post.api.retrofit.exception.TiebaApiException
import com.huanchengfly.tieba.post.api.retrofit.interceptors.ConnectivityInterceptor
import com.huanchengfly.tieba.post.repository.source.network.ExploreNetworkDataSource.commonResponse
import kotlinx.coroutines.CancellationException

object SearchNetworkDataSource {

    suspend fun searchForum(keyword: String): SearchForumBean.DataBean {
        val response = searchRequest { TiebaApi.getInstance().searchForum(keyword) }
        return response.run {
            data ?: throw TiebaApiException(CommonResponse(errorCode ?: -1, errorMsg.orEmpty()))
        }
    }

    suspend fun searchPost(
        keyword: String,
        forumName: String,
        forumId: Long,
        sortType: Int,
        filterType: Int,
        page: Int
    ): SearchThreadBean.DataBean {
        if (TextUtils.isEmpty(keyword)) throw IllegalArgumentException("Empty keyword")
        if (page < 1) throw IllegalArgumentException("Invalid page number: $page")
        if (sortType !in 1..2) throw IllegalArgumentException("Invalid sort type: $sortType")
        if (filterType !in 1..2) throw IllegalArgumentException("Invalid filter type: $filterType")

        val response = searchRequest {
            TiebaApi.getInstance().searchPost(keyword, forumName, forumId, sortType, filterType, page)
        }
        return response.run {
            if (errorCode == 0) data else throw TiebaApiException(CommonResponse(errorCode, errorMsg))
        }
    }

    suspend fun searchThread(keyword: String, page: Int, sortType: Int): SearchThreadBean.DataBean {
        val response = searchRequest { TiebaApi.getInstance().searchThread(keyword, page, sortType) }
        return response.run {
            if (errorCode == 0) data else throw TiebaApiException(CommonResponse(errorCode, errorMsg))
        }
    }

    suspend fun searchSuggestions(keyword: String, searchForum: Boolean): SearchSugResponseData {
        val response = TiebaApi.getInstance().searchSuggestions(keyword, searchForum)
        return response.data_ ?: throw TiebaApiException(commonResponse = response.error.commonResponse)
    }

    suspend fun searchUser(keyword: String): SearchUserBean.SearchUserDataBean {
        val response = searchRequest { TiebaApi.getInstance().searchUser(keyword) }
        return response.run {
            data ?: throw TiebaApiException(CommonResponse(errorCode ?: -1, errorMsg.orEmpty()))
        }
    }

    private suspend inline fun <T> searchRequest(request: () -> T): T = try {
        request()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw ConnectivityInterceptor.wrapException(e)
    }
}
