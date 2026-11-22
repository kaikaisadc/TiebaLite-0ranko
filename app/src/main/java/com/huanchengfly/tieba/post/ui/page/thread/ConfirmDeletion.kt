package com.huanchengfly.tieba.post.ui.page.thread

import kotlinx.coroutines.flow.MutableStateFlow

internal inline fun <T : Any> MutableStateFlow<T?>.confirmDeletion(target: T, delete: () -> Unit) {
    if (compareAndSet(target, null)) delete()
}
