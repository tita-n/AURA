package com.aura.platform.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

/**
 * Event-driven package change monitor — the ONLY place that registers package
 * broadcast receivers. No polling and no background loops: fires only
 * when Android delivers PACKAGE_ADDED / REMOVED / REPLACED while AURA is alive.
 */
class PackageChangeMonitor(
    private val context: Context,
    private val onChange: () -> Unit
) {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            // Ignore our own replacement mid-update churn; still refresh, but coalesce
            // is unnecessary at launcher scale (a few events per install session).
            onChange()
        }
    }

    fun register() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }

    fun unregister() {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
            // Already unregistered — idempotent.
        }
    }
}
