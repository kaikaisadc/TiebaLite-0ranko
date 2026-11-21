package com.huanchengfly.tieba.post.repository.user

import android.content.Context
import android.util.Log
import androidx.collection.LongSet
import androidx.collection.mutableLongSetOf
import androidx.work.WorkInfo
import androidx.work.await
import com.huanchengfly.tieba.post.api.models.ForumGuideBean
import com.huanchengfly.tieba.post.api.models.MSignBean
import com.huanchengfly.tieba.post.api.retrofit.exception.TiebaException
import com.huanchengfly.tieba.post.api.retrofit.exception.getErrorMessage
import com.huanchengfly.tieba.post.arch.shareInBackground
import com.huanchengfly.tieba.post.di.ApplicationScope
import com.huanchengfly.tieba.post.models.database.Account
import com.huanchengfly.tieba.post.repository.HomeRepository
import com.huanchengfly.tieba.post.repository.source.network.HomeNetworkDataSource
import com.huanchengfly.tieba.post.repository.source.network.OKSignNetworkDataSource
import com.huanchengfly.tieba.post.repository.user.OKSignRepository.ProgressListener
import com.huanchengfly.tieba.post.ui.models.settings.SignConfig
import com.huanchengfly.tieba.post.utils.AccountUtil
import com.huanchengfly.tieba.post.utils.workManager
import com.huanchengfly.tieba.post.workers.OKSignWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom
import javax.inject.Inject
import javax.inject.Singleton

interface OKSignRepository {

    val isOKSignWorkerRunning: SharedFlow<Boolean>

    suspend fun sign(listener: ProgressListener?)

    fun scheduleWorker()

    suspend fun dispatchDailyRun(id: UUID): Boolean

    interface ProgressListener {
        fun onInit(total: Int, userName: String)

        fun onSigned(progress: Int, forum: String, signBonusPoint: Int?)

        fun onFailed(progress: Int, forum: String, error: String)

        fun onMSignFailed(e: Throwable)

        fun onFinish(succeed: Int)
    }
}

@Singleton
class OKSignRepositoryImp @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    @ApplicationContext private val context: Context,
    private val homeRepo: HomeRepository,
    private val settingsRepo: SettingsRepository,
    private val networkDataSource: OKSignNetworkDataSource,
    private val homeDataSource: HomeNetworkDataSource,
): OKSignRepository {

    // Lazy init for instrumented test
    private val workManager by lazy { context.workManager() }
    private val scheduleLock = Mutex()

    override val isOKSignWorkerRunning: SharedFlow<Boolean> by lazy {
        combine(
            workManager.getWorkInfosByTagFlow(OKSignWorker.TAG_EXPEDITED),
            workManager.getWorkInfosByTagFlow(OKSignWorker.TAG)
        ) { expedited, oneshot ->
            // Is any OKSign worker running
            expedited.any { it.state == WorkInfo.State.RUNNING } || oneshot.any { it.state == WorkInfo.State.RUNNING }
        }
        .distinctUntilChanged()
        .shareInBackground()
    }

    init {
        scope.launch {
            // Older versions scheduled a one-time request under this name.
            handleScheduleError { workManager.cancelUniqueWork(OKSignWorker.TAG).await() }
            settingsRepo.signConfig
                .distinctUntilChangedBy { it.autoSign to it.autoSignTime }
                .collect { reconcileSchedule() }
        }
    }

    /**
     * 官方一键签到（实验性）
     * */
    suspend fun officialSign(forums: List<ForumSignParam>, tbs: String, listener: ProgressListener?): List<MSignBean.Info>? {
        if (forums.isEmpty()) return emptyList()

        val mSignInfo = try {
            networkDataSource.requestOfficialSign(forums, tbs)
        } catch (e: Throwable) {
            Log.e(TAG, "onOfficialSign: Abort", e)
            listener?.onMSignFailed(e)
            return null
        }

        val mSignFailed = mutableListOf<ForumSignParam>()
        var forumName: String
        mSignInfo.forEachIndexed { i, info ->
            forumName = info.forumName
            if (info.signed == "1") {
                listener?.onSigned(i, forumName, null)
            } else {
                mSignFailed.add(forums.first { forum -> forum.forumId == info.forumId })
                listener?.onFailed(i, forumName, info.error.usermsg)
            }
            delay(200)
        }
        return mSignInfo
    }

    /**
     * 普通签到
     *
     * @return 签到成功吧 ID 列表
     * */
    suspend fun normalSign(
        forums: List<ForumSignParam>,
        tbs: String,
        slowMode: Boolean,
        initialProgress: Int,
        listener: ProgressListener?
    ): LongSet {
        val succeed = mutableLongSetOf()
        var progress = initialProgress

        forums.forEach { (forumName, forumId) ->
            try {
                val result = networkDataSource.requestSign(forumId, forumName, tbs)
                if (result.isSignIn == 1) {
                    listener?.onSigned(progress, forumName, result.signBonusPoint)
                    succeed.add(forumId)
                }
            } catch (e: TiebaException) {
                val message = e.getErrorMessage()
                Log.w(TAG, "onNormalSign: Sign $forumName failed: $message")
                listener?.onFailed(progress, forumName, message)
            } finally {
                progress++
            }
            if (progress < forums.size) {
                delay(getSignDelay(slowMode))
            }
        }
        return succeed
    }

    private suspend fun signInternal(account: Account, signConfig: SignConfig, listener: ProgressListener?) {
        val start = System.currentTimeMillis()
        val forumListBean = networkDataSource.getForumList()
        val forumRecommendList = homeDataSource.getLikedForums()
        val mSignMinLevel = forumListBean.level.toInt()
        val mSignStepNum = forumListBean.msignStepNum.toInt()
        val useMSign = signConfig.okSignOfficial

        // Normal Sign
        val forums = mutableListOf<ForumSignParam>()
        // Official OkSign
        val mSignForums = mutableListOf<ForumSignParam>()

        // Split liked forums into NormalSign and MSign
        forumRecommendList.forEach {
            if (it.isSign != 1) {
                val canUseMSign = useMSign && it.levelId >= mSignMinLevel && mSignForums.size < mSignStepNum
                if (canUseMSign) {
                    mSignForums.add(ForumSignParam(forum = it))
                } else {
                    forums.add(ForumSignParam(forum = it))
                }
            }
        }
        val forumCount = forumRecommendList.size
        val totalCount = forums.size + mSignForums.size
        Log.i(TAG, "onSignInternal: Signing $totalCount/$forumCount forums.")

        // All forums are signed, return now
        if (totalCount == 0) {
            listener?.onFinish(succeed = 0)
            notifyForumSignDataChanged()
            return
        }

        currentCoroutineContext().ensureActive()
        listener?.onInit(total = totalCount, userName = account.name)
        val succeed = mutableLongSetOf()
        // Try Official OkSign
        val resultList = officialSign(mSignForums, tbs = account.tbs, listener = listener)
        // Failed, retry all mSignForums with normal sign
        if (resultList == null) {
            forums.addAll(mSignForums)
        } else {
            resultList.forEach { signInfo ->
                if (signInfo.signed == "1") { // Record succeed forum id
                    succeed.add(signInfo.forumId)
                } else {
                    val failedForum = mSignForums.find { it.forumId == signInfo.forumId }!!
                    // try with normal sign
                    forums.add(failedForum)
                }
            }
        }

        if (forums.isEmpty()) {
            listener?.onFinish(succeed = totalCount)
        } else {
            currentCoroutineContext().ensureActive()

            // Sign forums with signFlow now
            val succeedForumIds = normalSign(
                forums = forums.toList(),
                tbs = account.tbs,
                initialProgress = succeed.size,
                slowMode = signConfig.autoSignSlow,
                listener = listener
            )
            if (succeedForumIds.isNotEmpty()) {
                succeed += succeedForumIds
            }
            val duration = (System.currentTimeMillis() - start) / 1000
            val succeedCount = succeed.size
            Log.w(TAG, "onSignInternal: Done, $succeedCount/$totalCount signed, cost: ${duration}s")
            listener?.onFinish(succeed = succeedCount)
        }

        if (succeed.isNotEmpty()) {
            notifyForumSignDataChanged()
        }
    }

    private fun notifyForumSignDataChanged() {
        scope.launch(Dispatchers.Default) {
            homeRepo.refresh(cached = false)
        }
    }

    override suspend fun sign(listener: ProgressListener?) {
        val account = AccountUtil.getInstance().updateSigningAccount()
        val signConfig = settingsRepo.signConfig.snapshot()
        signInternal(account, signConfig, listener)
    }

    private suspend fun reconcileSchedule() = handleScheduleError {
        scheduleLock.withLock {
            val config = settingsRepo.signConfig.snapshot()
            val current = workManager.getWorkInfosForUniqueWorkFlow(OKSignWorker.DAILY_WORK_NAME)
                .first()
                .firstOrNull { !it.state.isFinished }
            if (!config.autoSign) {
                if (current != null) {
                    workManager.cancelUniqueWork(OKSignWorker.DAILY_WORK_NAME).await()
                    workManager.cancelUniqueWork(OKSignWorker.DAILY_RUN_NAME).await()
                }
            } else if (current == null || OKSignWorker.dailyTimeTag(config.autoSignTime) !in current.tags) {
                OKSignWorker.scheduleDaily(workManager, config.autoSignTime)
            }
        }
    }

    override fun scheduleWorker() {
        scope.launch { reconcileSchedule() }
    }

    override suspend fun dispatchDailyRun(id: UUID): Boolean = scheduleLock.withLock {
        val config = settingsRepo.signConfig.snapshot()
        if (!config.autoSign) return@withLock true

        val info = workManager.getWorkInfoByIdFlow(id).first()
        if (info?.state != WorkInfo.State.RUNNING) return@withLock true
        try {
            OKSignWorker.enqueueDailyRun(workManager)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Unable to start automatic sign-in", e)
            return@withLock false
        }
        handleScheduleError { OKSignWorker.scheduleDaily(workManager, config.autoSignTime) }
        true
    }

    private suspend fun handleScheduleError(schedule: suspend () -> Unit) {
        try {
            schedule()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Unable to update automatic sign-in schedule", e)
        }
    }

    companion object {

        data class ForumSignParam(val name: String, val forumId: Long, val signed: Boolean) {

            constructor(forum: ForumGuideBean.LikeForum): this(
                name = forum.forumName,
                forumId = forum.forumId,
                signed = forum.isSign == 1
            )
        }

        private const val TAG = "OKSignRepository"

        private fun getSignDelay(slowMode: Boolean): Long {
            return if (slowMode) {
                ThreadLocalRandom.current().nextInt(3500, 8000).toLong()
            } else {
                2000
            }
        }
    }
}
