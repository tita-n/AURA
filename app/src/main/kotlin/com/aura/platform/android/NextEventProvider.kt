package com.aura.platform.android

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.aura.home.NextEventInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Calendar-based Next Event provider — local, event-driven via ContentObserver,
 * permission-guarded like the contacts ask (contextual READ_CALENDAR).
 */
class NextEventProvider(private val context: Context) {

    companion object {
        const val PERMISSION = Manifest.permission.READ_CALENDAR
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version

    private var observer: ContentObserver? = null

    fun startObserving() {
        if (observer != null) return
        val cr = context.contentResolver
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                _version.value = System.currentTimeMillis()
            }
        }
        try {
            // Instances is a virtual table derived from Calendars + Events — observe both
            cr.registerContentObserver(CalendarContract.Events.CONTENT_URI, true, obs)
            cr.registerContentObserver(CalendarContract.Instances.CONTENT_URI, false, obs)
            observer = obs
        } catch (_: Exception) {}
    }

    fun stopObserving() {
        observer?.let {
            try { context.contentResolver.unregisterContentObserver(it) } catch (_: Exception) {}
            observer = null
        }
    }

    /**
     * Return the next upcoming event (or currently ongoing) in the upcoming 30-day window,
     * or null if none / permission denied.
     * Runs on the caller thread; expected to be called from a dispatchers.IO block.
     */
    fun queryNextEvent(nowMillis: Long = System.currentTimeMillis(), windowDays: Int = 30): NextEventInfo? {
        if (!hasPermission()) return null
        return try { query(nowMillis, windowDays) } catch (_: Exception) { null }
    }

    private fun query(nowMillis: Long, windowDays: Int): NextEventInfo? {
        val cr: ContentResolver = context.contentResolver
        val begin = nowMillis - 12L * 60 * 60 * 1000 // catch long ongoing events
        val end = nowMillis + windowDays.toLong() * 24 * 60 * 60 * 1000
        val instancesUri = Uri.withAppendedPath(CalendarContract.Instances.CONTENT_URI, "$begin/$end")
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY
        )
        val sortOrder = "${CalendarContract.Instances.BEGIN} ASC"
        cr.query(instancesUri, projection, null, null, sortOrder)?.use { c ->
            val ti = c.getColumnIndex(CalendarContract.Instances.TITLE)
            val bi = c.getColumnIndex(CalendarContract.Instances.BEGIN)
            val ei = c.getColumnIndex(CalendarContract.Instances.END)
            val ai = c.getColumnIndex(CalendarContract.Instances.ALL_DAY)
            while (c.moveToNext()) {
                val title = if (ti >= 0) c.getString(ti) else null
                val b = if (bi >= 0) c.getLong(bi) else 0L
                val e = if (ei >= 0) c.getLong(ei) else 0L
                val allDay = if (ai >= 0) c.getInt(ai) == 1 else false
                if (e < nowMillis) continue // already ended
                val safeTitle = title?.takeIf { it.isNotBlank() } ?: "Event"
                return NextEventInfo(safeTitle, b, e, allDay)
            }
        }
        return null
    }
}
