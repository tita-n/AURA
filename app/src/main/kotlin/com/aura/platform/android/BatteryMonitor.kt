package com.aura.platform.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.aura.home.BatteryUiModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Battery monitor — event-driven via the sticky ACTION_BATTERY_CHANGED broadcast.
 * No polling; a single BroadcastReceiver registered while the Composable is disposed.
 */
class BatteryMonitor(private val context: Context) {

    private val _state: MutableStateFlow<BatteryUiModel?> = MutableStateFlow(null)
    val state: StateFlow<BatteryUiModel?> = _state

    private var receiver: BroadcastReceiver? = null

    @Synchronized
    fun start() {
        if (receiver != null) return
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        // ACTION_BATTERY_CHANGED is sticky — immediate delivery plus event-driven updates.
        val sticky = context.registerReceiver(null, filter)
        if (sticky != null) ingest(sticky)
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent != null) ingest(intent)
            }
        }
        context.registerReceiver(r, filter)
        receiver = r
    }

    @Synchronized
    fun stop() {
        receiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
            receiver = null
        }
    }

    private fun ingest(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else return
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING
            || status == BatteryManager.BATTERY_STATUS_FULL
            || plugged != 0
        _state.value = BatteryUiModel(percent, charging)
    }

    companion object {
        /** Pure helper for tests — no Intent needed. */
        fun parse(level: Int, scale: Int, status: Int, plugged: Int): BatteryUiModel? {
            if (level < 0 || scale <= 0) return null
            val percent = (level * 100 / scale).coerceIn(0, 100)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL
                || plugged != 0
            return BatteryUiModel(percent, charging)
        }
    }
}
