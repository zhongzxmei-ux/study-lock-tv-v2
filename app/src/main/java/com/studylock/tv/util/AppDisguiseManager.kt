package com.studylock.tv.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object AppDisguiseManager {
    private val aliases = listOf(
        "DefaultAlias",
        "SettingsAlias",
        "BatteryAlias",
        "SecurityAlias",
        "UpdateAlias"
    )

    fun switchAlias(context: Context, targetAlias: String) {
        val pm = context.packageManager
        val pkg = context.packageName
        aliases.forEach { alias ->
            val component = ComponentName(pkg, "$pkg.alias.$alias")
            val state = if (alias == targetAlias) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
        }
    }
}
