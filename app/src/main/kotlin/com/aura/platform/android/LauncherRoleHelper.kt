package com.aura.platform.android

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * Launcher-role boundary — ONLY place using RoleManager / HOME settings intents.
 * Android always owns the decision; AURA only asks or checks.
 */
object LauncherRoleHelper {

    fun isDefaultHome(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val rm = context.getSystemService(android.app.role.RoleManager::class.java) ?: return false
        return rm.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)
    }

    fun isRoleAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val rm = context.getSystemService(android.app.role.RoleManager::class.java) ?: return false
        return rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)
    }

    /** Intent that launches Android's native role-grant UI. Caller launches via activity result. */
    fun createRequestIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < 29) return null
        val rm = context.getSystemService(android.app.role.RoleManager::class.java) ?: return null
        if (!rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) return null
        return rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME)
    }

    /** Pre-API-29 fallback: system Home-settings screen. */
    fun homeSettingsIntent(): Intent =
        Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
