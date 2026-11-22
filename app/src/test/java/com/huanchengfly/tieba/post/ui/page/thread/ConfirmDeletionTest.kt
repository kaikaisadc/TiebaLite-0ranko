package com.huanchengfly.tieba.post.ui.page.thread

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfirmDeletionTest {
    @Test
    fun confirmingTwiceSendsOneRequest() {
        val target = MutableStateFlow<Long?>(42L)
        var requests = 0

        repeat(2) { target.confirmDeletion(42L) { requests++ } }

        assertEquals(1, requests)
        assertNull(target.value)
    }

    @Test
    fun staleConfirmationDoesNotDeleteNewTarget() {
        val target = MutableStateFlow<Long?>(43L)
        target.confirmDeletion(42L) { error("Unexpected deletion") }
        assertEquals(43L, target.value)
    }
}
