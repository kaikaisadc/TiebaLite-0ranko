package com.huanchengfly.tieba.post.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.room.withTransaction
import com.huanchengfly.tieba.post.arch.unsafeLazy
import com.huanchengfly.tieba.post.models.database.ForumHistory
import com.huanchengfly.tieba.post.models.database.History
import com.huanchengfly.tieba.post.models.database.TbLiteDatabase
import com.huanchengfly.tieba.post.models.database.ThreadHistory
import com.huanchengfly.tieba.post.models.database.UserProfile
import com.huanchengfly.tieba.post.models.database.dao.ForumHistoryDao
import com.huanchengfly.tieba.post.models.database.dao.ThreadHistoryDao
import com.huanchengfly.tieba.post.models.database.dao.UserProfileDao
import com.huanchengfly.tieba.post.utils.StringUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository that manages history data.
 * */
@Singleton
class HistoryRepository @Inject constructor(
    private val dataBase: TbLiteDatabase
) {

    private val threadHistoryDao: ThreadHistoryDao = dataBase.threadHistoryDao()

    private val forumHistoryDao: ForumHistoryDao = dataBase.forumHistoryDao()

    private val userProfileDao: UserProfileDao = dataBase.userProfileDao()

    private val defaultConfig by unsafeLazy {
        PagingConfig(pageSize = 20, prefetchDistance = 4, maxSize = 80)
    }

    fun getForumHistoryTop10(followedForumUid: Long? = null): Flow<List<ForumHistory>> =
        followedForumUid
            ?.takeIf { it > 0 }
            ?.let { forumHistoryDao.observeTopNotFollowed(uid = it, limit = 10) }
            ?: forumHistoryDao.observeTop(limit = 10)

    fun getForumHistory(
        followedForumUid: Long? = null,
        config: PagingConfig = defaultConfig,
    ): Flow<PagingData<ForumHistory>> {
        return Pager(
            config = config,
            pagingSourceFactory = {
                followedForumUid
                    ?.takeIf { it > 0 }
                    ?.let(forumHistoryDao::pagingSourceNotFollowed)
                    ?: forumHistoryDao.pagingSource()
            }
        ).flow
    }

    fun getThreadHistory(config: PagingConfig = defaultConfig): Flow<PagingData<ThreadHistory>> {
        return Pager(
            config = config,
            pagingSourceFactory = { threadHistoryDao.pagingSourceSorted() }
        ).flow
    }

    fun getUserHistory(config: PagingConfig = defaultConfig): Flow<PagingData<UserHistory>> {
        return Pager(
            config = config,
            pagingSourceFactory = { userProfileDao.pagingSourceSorted() }
        )
        .flow
        .map { it.map(transform = ::mapUiModel) }
        .flowOn(Dispatchers.Default)
    }

    suspend fun saveHistory(history: History) {
        withContext(NonCancellable) {
            when (history) {
                is ThreadHistory -> threadHistoryDao.upsert(history)

                is ForumHistory -> forumHistoryDao.upsert(history)

                // is UserHistory

                else -> throw RuntimeException()
            }
        }
    }

    suspend fun deleteHistory(history: History) {
        withContext(NonCancellable) {
            when (history) {
                is ThreadHistory -> threadHistoryDao.deleteById(threadId = history.id)

                is ForumHistory -> forumHistoryDao.deleteById(forumId = history.id)

                is UserHistory -> userProfileDao.deleteById(uid = history.id)

                else -> throw RuntimeException()
            }
        }
    }

    suspend fun deleteHistory(historyList: List<History>) {
        if (historyList.isEmpty()) return
        val threads = historyList.filterIsInstance<ThreadHistory>().map { it.id }
        val forums = historyList.filterIsInstance<ForumHistory>().map { it.id }
        val users = historyList.filterIsInstance<UserHistory>().map { it.id }
        withContext(NonCancellable) {
            dataBase.withTransaction {
                if (threads.isNotEmpty()) threadHistoryDao.deleteByIdList(threads)
                if (forums.isNotEmpty()) forumHistoryDao.deleteByIdList(forums)
                if (users.isNotEmpty()) userProfileDao.deleteByIdList(users)
            }
        }
    }

    suspend fun deleteAll() {
        withContext(NonCancellable) {
            dataBase.withTransaction {
                threadHistoryDao.deleteAll()
                forumHistoryDao.deleteAll()
                userProfileDao.deleteAll()
            }
        }
    }
}

class UserHistory(
    override val id: Long,
    override val avatar: String,
    override val name: String,
    val username: String?,
    override val timestamp: Long
) : History()

private fun mapUiModel(profile: UserProfile): UserHistory = with(profile) {
    val displayName = nickname ?: name
    UserHistory(
        id = uid,
        avatar = StringUtil.getAvatarUrl(portrait),
        name = displayName,
        username = name.takeIf { it.isNotEmpty() && it != displayName },
        timestamp = lastVisit
    )
}
