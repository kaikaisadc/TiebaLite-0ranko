package com.huanchengfly.tieba.post.components

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.google.common.base.Preconditions.checkArgument
import com.google.common.net.InternetDomainName
import com.huanchengfly.tieba.post.App
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.api.retrofit.exception.getErrorMessage
import com.huanchengfly.tieba.post.arch.ControlledRunner
import com.huanchengfly.tieba.post.components.ClipBoardLinkDetector.checkClipBoard
import com.huanchengfly.tieba.post.repository.ForumRepository
import com.huanchengfly.tieba.post.repository.PbPageRepository
import com.huanchengfly.tieba.post.ui.page.Destination
import com.huanchengfly.tieba.post.utils.QuickPreviewUtil
import com.huanchengfly.tieba.post.utils.QuickPreviewUtil.Icon
import com.huanchengfly.tieba.post.utils.QuickPreviewUtil.PreviewInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

sealed class ClipBoardLink(val url: String) {

    class Forum(url: String, val forumName: String) : ClipBoardLink(url)

    class Thread(url: String, val threadId: Long) : ClipBoardLink(url)

    // Convert ClipBoardLink to Navigation Route
    fun toRoute(avatarUrl: String? = null): Destination {
        return when (this) {
            is Forum -> Destination.Forum(forumName = forumName, avatar = avatarUrl)

            is Thread -> Destination.Thread(threadId = threadId)
        }
    }
}

/**
 * 检测剪贴板中的贴吧链接.
 *
 * 由于Android Q 引入的隐私限制, 仅当界面拥有焦点时读取剪贴板.
 *
 * @see checkClipBoard
 * @see [android.app.Activity.onWindowFocusChanged]
 * */
object ClipBoardLinkDetector {

    private val linkPattern = Regex("""https?://[^\s<>"'，。！？；、）】》]+""", RegexOption.IGNORE_CASE)

    val clipBoardManager by lazy {
        App.INSTANCE.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    private val _previewInfoStateFlow = MutableStateFlow<PreviewInfo?>(null)
    val previewInfoStateFlow: StateFlow<PreviewInfo?> = _previewInfoStateFlow.asStateFlow()

    private val previewTaskRunner = ControlledRunner<Unit>()

    @Volatile
    private var lastClipBoardHash: Int = -1

    private fun parseLink(uri: Uri): ClipBoardLink? {
        if (!uri.isTieba()) return null

        val path = uri.path ?: return null
        val url = uri.toString()
        val isLegacyPath = path.equals("/f", ignoreCase = true) || path.equals("/mo/q/m", ignoreCase = true)
        val threadId = when {
            path.startsWith("/p/") -> path.removePrefix("/p/")
                .substringBefore('&')
                .removeSuffix("/")
                .toThreadId()
            isLegacyPath -> uri.getQueryParameter("kz").toThreadId()
            else -> null
        }
        if (threadId != null) return ClipBoardLink.Thread(url, threadId)

        if (!isLegacyPath) return null
        val forumName = uri.getQueryParameter("kw")?.takeIf { it.isNotBlank() }
            ?: uri.getQueryParameter("word")?.takeIf { it.isNotBlank() }
            ?: return null
        return ClipBoardLink.Forum(url, forumName)
    }

    private fun String?.toThreadId(): Long? = this?.toLongOrNull()?.takeIf { it > 0 }

    fun parseDeepLink(uri: Uri): Result<ClipBoardLink?> = runCatching {
        if (uri.scheme == "com.baidu.tieba" && uri.host == "unidispatch") {
            when (val path = uri.path?.lowercase()) {
                "/frs" -> {
                    val forumName = uri.getQueryParameter("kw")?.takeIf { it.isNotBlank() }
                        ?: throw IllegalArgumentException("kw not found")
                    ClipBoardLink.Forum("https://tieba.baidu.com/f?kw=${Uri.encode(forumName)}", forumName)
                }

                "/pb" -> {
                    val threadId = uri.getQueryParameter("tid").toThreadId()
                        ?: throw IllegalArgumentException("Invalid tid")
                    ClipBoardLink.Thread("https://tieba.baidu.com/p/$threadId", threadId)
                }

                else -> throw IllegalArgumentException("Unsupported unidispatch path: $path.")
            }
        } else {
            parseLink(uri.normalizeScheme())
        }
    }

    suspend fun checkClipBoard(context: Context, forumRepo: ForumRepository, threadRepo: PbPageRepository) {
        val clipBoardText = getClipBoardText()
        if (clipBoardText == null || lastClipBoardHash == clipBoardText.hashCode()) return

        previewTaskRunner.cancelPreviousThenRun {
            val clipBoardLink = withContext(Dispatchers.Default) {
                linkPattern.findAll(clipBoardText).firstNotNullOfOrNull { match ->
                    val url = match.value.trimEnd('.', ',', ';', '!', '?', ')', ']', '}')
                    parseLink(url.toUri().normalizeScheme())
                }
            }
            if (clipBoardLink == null) {
                clear()
                return@cancelPreviousThenRun
            }

            lastClipBoardHash = clipBoardText.hashCode()
            val title = when(clipBoardLink) {
                is ClipBoardLink.Forum -> clipBoardLink.forumName
                is ClipBoardLink.Thread -> clipBoardLink.url
            }
            _previewInfoStateFlow.update {
                PreviewInfo(
                    clipBoardLink = clipBoardLink,
                    title = title,
                    subtitle = context.getString(R.string.subtitle_link),
                    icon = Icon(R.drawable.ic_link)
                )
            }
            runCatching {
                QuickPreviewUtil.loadPreviewInfo(context, clipBoardLink, forumRepo, threadRepo)
            }
            .onSuccess { preview ->
                yield()
                _previewInfoStateFlow.update { preview }
            }
            .onFailure { e ->
                yield()
                _previewInfoStateFlow.update {
                    PreviewInfo(clipBoardLink, title, e.getErrorMessage(), Icon(R.drawable.ic_error))
                }
            }
        }
    }

    fun onCopyTiebaLink(link: String) {
        clear()
        lastClipBoardHash = link.hashCode()
    }

    fun getClipBoardText(): String? {
        val data = clipBoardManager.primaryClip ?: return null
        val item = data.getItemAt(0)
        return item?.text?.toString()
    }

    // Return ture: 百度短链接
    private fun InternetDomainName.isBaiduShortLink(): Boolean = parts().first().let {
        it == "mr" || it == "mbd" || it == "t" || it == "rh" || (it.first() == 'm' && it.getOrNull(1)?.isDigit() == true)
    }

    fun Uri.isBaidu(skipSchemeCheck: Boolean = false): Boolean = try {
        if (!skipSchemeCheck) {
            checkArgument(this.isHttp())
        }
        with(InternetDomainName.from(host!!)) {
            when {
                isTopPrivateDomain -> "baidu.com" == host

                isUnderPublicSuffix -> {
                    parent().toString().endsWith("baidu.com") && !isBaiduShortLink() && path?.endsWith("checkurl") != true
                }

                else -> false
            }
        }
    } catch (_: Exception) {
        false
    }

    fun Uri.isHttp(): Boolean = scheme?.startsWith("http", ignoreCase = true) == true

    fun isTieba(url: String, skipSchemeCheck: Boolean = false): Boolean =
        url.toUri().normalizeScheme().isTieba(skipSchemeCheck)

    private fun Uri.isTieba(skipSchemeCheck: Boolean = false): Boolean {
        if (!isBaidu(skipSchemeCheck)) return false
        val hostname = host ?: return false
        return try {
            val subdomain = InternetDomainName.from(hostname).parts().first()
            (subdomain == "tieba" || subdomain == "tiebac" || subdomain == "wapp") &&
                queryParameterNames?.contains("tbjump") != true
        } catch (_: Exception) {
            false
        }
    }

    fun Uri.isLogin(): Boolean = path?.let { p ->
        host.equals("wappass.baidu.com") && p.startsWith("/passport") && queryParameterNames.contains("login")
    } == true

    fun clear() {
        previewTaskRunner.cancelCurrent()
        _previewInfoStateFlow.update { null }
    }
}