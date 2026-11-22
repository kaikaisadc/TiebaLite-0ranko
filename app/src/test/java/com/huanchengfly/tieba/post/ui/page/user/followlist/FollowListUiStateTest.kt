package com.huanchengfly.tieba.post.ui.page.user.followlist

import com.huanchengfly.tieba.post.api.models.FollowListBean
import com.huanchengfly.tieba.post.api.models.FollowListBean.FollowUserBean
import com.huanchengfly.tieba.post.ui.models.user.ConcernType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FollowListUiStateTest {
    @Test
    fun latePagePreservesUnfollowAndCount() {
        val oldRow = FollowUserBean(id = 1, portrait = "one", hasConcerned = ConcernType.MUTUAL).toFollowUser()
        val newRow = FollowUserBean(id = 2, portrait = "two").toFollowUser()
        val loading = FollowListUiState(users = listOf(oldRow), totalFollowNum = 8, isLoadingMore = true)
        val unfollowed = loading.updateConcern(oldRow.uid, ConcernType.FANS, countChange = -1)

        val result = unfollowed.applyPage(
            FollowListBean(pageNum = 2, totalFollowNum = 8),
            listOf(oldRow, newRow, newRow),
            refresh = false,
        )

        assertEquals(listOf(oldRow.copy(concernType = ConcernType.FANS), newRow), result.users)
        assertEquals(7, result.totalFollowNum)
        assertEquals(2, result.currentPage)
        assertFalse(result.isLoadingMore)
    }

    @Test
    fun refreshReplacesRowsAndKeepsMutualFilter() {
        val old = FollowUserBean(id = 1).toFollowUser()
        val row = FollowUserBean(id = 2).toFollowUser()
        val loading = FollowListUiState(filter = FollowListFilter.Mutual, users = listOf(old), isRefreshing = true)
        val result = loading.applyPage(FollowListBean(totalFollowNum = 10), listOf(row), refresh = true)

        assertEquals(FollowListFilter.Mutual, result.filter)
        assertEquals(listOf(row), result.users)
        assertEquals(10, result.totalFollowNum)
        assertFalse(result.isRefreshing)
    }

    @Test
    fun missingPortraitKeepsNavigableRowWithoutFollowAction() {
        for (portrait in listOf(null, "", "  ")) {
            val row = FollowUserBean(id = 42, name = "test", portrait = portrait).toFollowUser()
            assertEquals(42L, row.uid)
            assertEquals("test", row.displayName)
            assertEquals("", row.avatar)
            assertFalse(row.canChangeFollow)
        }
    }
}
