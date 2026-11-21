package com.huanchengfly.tieba.post.api.interfaces

import com.huanchengfly.tieba.post.api.models.SearchForumBean
import com.huanchengfly.tieba.post.api.models.SearchThreadBean
import com.huanchengfly.tieba.post.api.models.SearchUserBean
import com.huanchengfly.tieba.post.api.models.protos.searchSug.SearchSugResponse

interface TiebaSearchApi {
    /**
     * 搜索用户
     *
     * @param keyword 搜索关键词
     */
    suspend fun searchUser(
        keyword: String,
    ): SearchUserBean

    /**
     * 搜索吧
     *
     * @param keyword 关键词
     */
    suspend fun searchForum(
        keyword: String,
    ): SearchForumBean

    /**
     * 搜索主题贴
     *
     * @param keyword 关键词
     * @param page 分页页码
     * @param sort 结果排序（0 = 旧贴在前，2 = 相关程度，5 = 新贴在前）
     */
    suspend fun searchThread(
        keyword: String,
        page: Int,
        sort: Int,
    ): SearchThreadBean

    /**
     * 吧内搜索
     *
     * @param keyword 搜索关键词
     * @param forumName 搜索吧名
     * @param forumId 搜索吧 ID
     * @param sortType 排序模式（1 = 时间倒序，2 = 相关性排序）
     * @param filterType 过滤（1 = 全部，2 = 仅看主题贴）
     * @param page 分页页码（从 1 开始）
     * @param pageSize 每页贴数（默认 20）
     */
    suspend fun searchPost(
        keyword: String,
        forumName: String,
        forumId: Long,
        sortType: Int = 1,
        filterType: Int = 1,
        page: Int = 1,
        pageSize: Int = 20,
    ): SearchThreadBean

    /**
     * 搜索联想
     *
     * @param keyword 关键词
     * @param isForum 是否为吧
     */
    suspend fun searchSuggestions(
        keyword: String,
        isForum: Boolean = false,
    ): SearchSugResponse
}
