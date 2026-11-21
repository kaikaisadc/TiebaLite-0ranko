package com.huanchengfly.tieba.post.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.huanchengfly.tieba.post.MainActivityV2
import com.huanchengfly.tieba.post.components.ShortcutInitializer

enum class LauncherIcons {
    NEW_ICON, NEW_ICON_THEMED, NEW_ICON_INVERT, OLD_ICON;

    fun supportThemedIcon(): Boolean = this == NEW_ICON
}

object AppIconUtil {

    internal fun componentName(icon: LauncherIcons, context: Context): ComponentName {
        val name = when (icon) {
            LauncherIcons.NEW_ICON -> "MainActivityIconDefault"
            LauncherIcons.NEW_ICON_THEMED -> "MainActivityIconThemed"
            LauncherIcons.NEW_ICON_INVERT -> "MainActivityIconInvert"
            LauncherIcons.OLD_ICON -> "MainActivityIconOld"
        }
        return ComponentName(context, "com.huanchengfly.tieba.post.$name")
    }

    fun setIcon(icon: LauncherIcons, ctx: Context) {
        val context = ctx.applicationContext
        if (applyLauncherIcon(icon, context)) ShortcutInitializer().create(context)
    }

    internal fun applyLauncherIcon(icon: LauncherIcons, context: Context): Boolean {
        val manager = context.packageManager
        val main = ComponentName(context, MainActivityV2::class.java)
        val default = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        val enabled = PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        val disabled = PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        var changed = false

        fun setState(component: ComponentName, state: Int, matches: (Int) -> Boolean) {
            if (!matches(manager.getComponentEnabledSetting(component))) {
                manager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
                changed = true
            }
        }

        setState(main, default) { it == default || it == enabled }
        setState(componentName(icon, context), enabled) {
            it == enabled || (it == default && icon == LauncherIcons.NEW_ICON)
        }
        for (other in LauncherIcons.entries) {
            if (other != icon) {
                setState(componentName(other, context), disabled) {
                    it == disabled || (it == default && other != LauncherIcons.NEW_ICON)
                }
            }
        }
        return changed
    }
}
