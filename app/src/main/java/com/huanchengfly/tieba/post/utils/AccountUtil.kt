package com.huanchengfly.tieba.post.utils

import android.content.Context
import android.os.Build
import android.util.Log
import android.webkit.CookieManager
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.work.WorkRequest
import com.huanchengfly.tieba.post.App
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.api.retrofit.exception.TiebaNotLoggedInException
import com.huanchengfly.tieba.post.arch.shareInBackground
import com.huanchengfly.tieba.post.components.ShortcutInitializer
import com.huanchengfly.tieba.post.models.database.Account
import com.huanchengfly.tieba.post.models.database.TbLiteDatabase
import com.huanchengfly.tieba.post.models.database.Timestamp
import com.huanchengfly.tieba.post.models.database.dao.AccountDao
import com.huanchengfly.tieba.post.models.database.dao.TimestampDao
import com.huanchengfly.tieba.post.repository.source.network.UserProfileNetworkDataSource
import com.huanchengfly.tieba.post.repository.user.Settings
import com.huanchengfly.tieba.post.toastShort
import com.huanchengfly.tieba.post.utils.StringUtil.getShortNumString
import com.huanchengfly.tieba.post.workers.NewMessageWorker
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

val LocalAccount = staticCompositionLocalOf<Account?> { error("No Account provided") }

class AccountUtil private constructor(context: Context) {

    companion object {
        private const val TAG = "AccountUtil"

        private const val UPDATE_EXPIRE_MILL = 0x240C8400 // one week

        private const val FETCH_EXPIRE_MILL = WorkRequest.MAX_BACKOFF_MILLIS // 5 hours

        @Volatile
        private var _instance: AccountUtil? = null

        fun getInstance(): AccountUtil {
            return _instance ?: synchronized(this) {
                _instance ?: AccountUtil(App.INSTANCE).also { _instance = it }
            }
        }

        inline fun <T> getAccountInfo(getter: Account.() -> T): T? = getLoginInfo()?.getter()

        private data class RequestAccount(val account: Account?)
        private val requestAccount = ThreadLocal<RequestAccount>()

        fun getLoginInfo(): Account? {
            val snapshot = requestAccount.get()
            return if (snapshot != null) snapshot.account else runBlocking { getInstance().getCurrentAccount() }
        }

        internal fun <T> withRequestAccount(request: () -> T): T {
            if (requestAccount.get() != null) return request()
            requestAccount.set(RequestAccount(getLoginInfo()))
            return try {
                request()
            } finally {
                requestAccount.remove()
            }
        }

        fun isLoggedIn(): Boolean = getLoginInfo() != null

        fun getSToken(): String? = getLoginInfo()?.sToken

        fun getCookie(): String? = getLoginInfo()?.cookie

        fun getUid(): String? = getLoginInfo()?.uid?.toString()

        fun getBduss(): String? = getLoginInfo()?.bduss

        fun getBdussCookie(): String? {
            val bduss = getBduss()
            return if (bduss != null) {
                getBdussCookie(bduss)
            } else null
        }

        fun getBdussCookie(bduss: String): String {
            return "BDUSS=$bduss; Path=/; Max-Age=315360000; Domain=.baidu.com; Httponly"
        }

        fun parseCookie(cookie: String): Map<String, String> {
            return cookie
                .split(";")
                .map { it.trim().split("=") }
                .filter { it.size > 1 }
                .associate { it.first() to it.drop(1).joinToString("=") }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + CoroutineName(TAG) + SupervisorJob())

    private val accountChanges = Mutex()

    private val networkDataSource = UserProfileNetworkDataSource

    private val accountUidSettings: Settings<Long> = (context as App).settingRepository.accountUid

    private val accountDao: AccountDao

    private val timeDao: TimestampDao

    private val database = TbLiteDatabase.getInstance(context)

    init {
        accountDao = database.accountDao()
        timeDao = database.timestampDao()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentAccount: SharedFlow<Account?> = accountUidSettings
        .flatMapLatest { uid ->
            if (uid != -1L) accountDao.observeById(uid) else flowOf(null)
        }
        .buffer(0)
        .distinctUntilChanged()
        .shareInBackground(SharingStarted.Eagerly)

    private suspend fun getCurrentAccount(): Account? {
        val uid = accountUidSettings.snapshot()
        if (uid == -1L) return null
        return currentAccount.replayCache.lastOrNull()?.takeIf { it.uid == uid }
            ?: accountDao.getById(uid)
    }

    val allAccounts: SharedFlow<List<Account>> = accountDao.observeAll()
        .shareInBackground()

    suspend fun updateSigningAccount(): Account {
        val account = getCurrentAccount() ?: throw TiebaNotLoggedInException()
        val lastUpdate = timeDao.get(account.uid, TimestampDao.TYPE_SIGN_INFO_UPDATED) ?: -1
        val duration = System.currentTimeMillis() - (lastUpdate + FETCH_EXPIRE_MILL)
        if (duration > 0) {
            Log.i(TAG, "onUpdateSigningAccount: Expired for ${duration / 1000}s")
            val zid = account.zid?.takeIf(String::isNotEmpty) ?: return account
            return fetchAccount(account.bduss, account.sToken, account.cookie, zid, refreshing = account)
        } else {
            return account
        }
    }

    suspend fun fetchAccount(bduss: String, sToken: String, cookie: String? = null, zid: String): Account =
        fetchAccount(bduss, sToken, cookie, zid, refreshing = null)

    private suspend fun fetchAccount(
        bduss: String,
        sToken: String,
        cookie: String?,
        zid: String,
        refreshing: Account?,
    ): Account {
        require(zid.isNotEmpty())
        // Fetching account login info, this is non-cancellable
        return scope.async {
            val (loginBean, userInfo) = networkDataSource.loginWithInit(bduss, sToken)
            val uid = loginBean.user.id.toLong()
            val nameShow = userInfo.nameShow.takeIf { it != loginBean.user.name }
            accountChanges.withLock {
                val account = accountDao.getById(uid)
                if (refreshing != null && (
                        refreshing.uid != uid || account == null || account.bduss != bduss || account.sToken != sToken ||
                            accountUidSettings.snapshot() != uid
                    )
                ) throw TiebaNotLoggedInException()
                // Update existing account
                val updated = if (account != null) {
                    account.copy(
                        name = loginBean.user.name,
                        nickname = nameShow,
                        bduss = bduss,
                        tbs = loginBean.anti.tbs,
                        portrait = loginBean.user.portrait,
                        sToken = sToken,
                        cookie = cookie ?: getBdussCookie(bduss),
                        tiebaUid = userInfo.tiebaUid,
                        zid = zid,
                    )
                } else {
                    Account(
                        uid = uid,
                        name = loginBean.user.name,
                        nickname = nameShow,
                        bduss = bduss,
                        tbs = loginBean.anti.tbs,
                        portrait = loginBean.user.portrait,
                        sToken = sToken,
                        cookie = cookie ?: getBdussCookie(bduss),
                        tiebaUid = userInfo.tiebaUid,
                        zid = zid,
                    )
                }
                database.transactionRunnerDao().invoke {
                    accountDao.upsert(updated)
                    timeDao.upsert(Timestamp(uid, TimestampDao.TYPE_SIGN_INFO_UPDATED))
                }
                updated
            }
        }
        .await()
    }

    /**
     * Refresh user profile
     * */
    suspend fun refreshCurrent(force: Boolean = false): Account {
        val account = getCurrentAccount() ?: throw TiebaNotLoggedInException()
        val duration = System.currentTimeMillis() - (account.lastUpdate + UPDATE_EXPIRE_MILL)
        // not force-refresh && not expire
        if (!force && duration < 0) {
            return account
        } else if (duration > 0) {
            Log.i(TAG, "onRefreshCurrent: Cache of ${account.uid} expired for ${duration / 1000}s")
        }

        val (user, anti) = networkDataSource.loadUserProfile(uid = account.uid)
        val birthday = user.birthday_info
        val updated = account.copy(
            nickname = user.nameShow,
            portrait = user.portrait,
            intro = user.intro,
            sex = user.sex,
            fans = user.fans_num.getShortNumString(),
            posts = user.post_num.getShortNumString(),
            threads = user.thread_num.getShortNumString(),
            concerned = user.concern_num.getShortNumString(),
            tbAge = user.tb_age.toFloatOrNull() ?: account.tbAge,
            age = birthday?.age?: account.age,
            birthdayShow = birthday?.birthday_show_status == 1,
            birthdayTime = birthday?.birthday_time ?: account.birthdayTime,
            constellation = birthday?.constellation,
            tiebaUid = user.tieba_uid,
            lastUpdate = System.currentTimeMillis(),
            blockDays = anti?.days_tofree?.takeIf { anti.block_stat == 1 } ?: 0,
        )
        accountChanges.withLock {
            val stored = accountDao.getById(account.uid)
            if (stored != null && stored.bduss == account.bduss && stored.sToken == account.sToken) {
                accountDao.update(updated)
            }
        }
        return updated
    }

    fun saveNewAccount(context: Context, account: Account) = scope.launch(Dispatchers.Main) {
        val isFirstAccount = accountChanges.withLock {
            accountDao.upsert(account)
            (getCurrentAccount() == null).also { if (it) accountUidSettings.setNow(account.uid) }
        }
        if (isFirstAccount) {
            // Init shortcuts that requires user login
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                ShortcutInitializer.initialize(loggedIn = true, context)
            }
        }
        val workManager = context.workManager()
        NewMessageWorker.schedulePeriodically(workManager)
        NewMessageWorker.startNow(workManager)
    }

    fun switchAccount(uid: Long) = scope.launch(Dispatchers.Main) {
        accountChanges.withLock {
            if (accountDao.getById(uid) != null) accountUidSettings.setNow(uid)
        }
    }

    fun exit(context: Context, oldAccount: Account) {
        scope.launch(Dispatchers.Main) {
            val nextAccount = accountChanges.withLock {
                val uid = accountUidSettings.snapshot()
                val active = if (uid == oldAccount.uid) {
                    CookieManager.getInstance().removeAllCookies(null)
                    accountDao.getAll().firstOrNull { it.uid != uid }.also {
                        accountUidSettings.setNow(it?.uid ?: -1)
                    }
                } else {
                    accountDao.getById(uid)
                }
                accountDao.deleteById(uid = oldAccount.uid)
                active
            }
            if (nextAccount != null) {
                context.toastShort(R.string.toast_exit_account_switched, nextAccount.nickname ?: nextAccount.name)
            } else {
                context.workManager().cancelAllWorkByTag(NewMessageWorker.TAG)
                context.toastShort(R.string.toast_exit_account_success)
                // Remove shortcuts that requires user login
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                    ShortcutInitializer.initialize(loggedIn = false, context)
                }
            }
        }
    }
}