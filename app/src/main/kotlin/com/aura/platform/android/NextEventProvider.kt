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
import com.aura.home.CalendarRelevance
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

    /** Resolve excluded (holiday/birthday) calendar ids and a name/account map for the rest. */
    private fun calendarInfo(): Pair<Set<Long>, Map<Long, Pair<String?, String?>>> {
        val excluded = mutableSetOf<Long>()
        val names = mutableMapOf<Long, Pair<String?, String?>>()
        val uri = CalendarContract.Calendars.CONTENT_URI
        val proj = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE
        )
        try {
            context.contentResolver.query(uri, proj, null, null, null)?.use { c ->
                val idI = c.getColumnIndex(CalendarContract.Calendars._ID)
                val nameI = c.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val acctI = c.getColumnIndex(CalendarContract.Calendars.ACCOUNT_TYPE)
                while (c.moveToNext()) {
                    val id = if (idI >= 0) c.getLong(idI) else -1L
                    val name = if (nameI >= 0) c.getString(nameI) else null
                    val acct = if (acctI >= 0) c.getString(acctI) else null
                    names[id] = name to acct
                    if (CalendarRelevance.isHolidayOrNoiseCalendar(name, acct)) excluded.add(id)
                }
            }
        } catch (_: Exception) {}
        return excluded to names
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
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_ID
        )
        val sortOrder = "${CalendarContract.Instances.BEGIN} ASC"
        val (excluded, names) = calendarInfo()
        cr.query(instancesUri, projection, null, null, sortOrder)?.use { c ->
            val ti = c.getColumnIndex(CalendarContract.Instances.TITLE)
            val bi = c.getColumnIndex(CalendarContract.Instances.BEGIN)
            val ei = c.getColumnIndex(CalendarContract.Instances.END)
            val ai = c.getColumnIndex(CalendarContract.Instances.ALL_DAY)
            val ci = c.getColumnIndex(CalendarContract.Instances.CALENDAR_ID)
            while (c.moveToNext()) {
                val calId = if (ci >= 0) c.getLong(ci) else -1L
                if (calId in excluded) continue // holiday / birthday noise
                val title = if (ti >= 0) c.getString(ti) else null
                val b = if (bi >= 0) c.getLong(bi) else 0L
                val e = if (ei >= 0) c.getLong(ei) else 0L
                val allDay = if (ai >= 0) c.getInt(ai) == 1 else false
                if (allDay) continue // hide generic all-day noise by default
                if (e < nowMillis) continue // already ended
                val safeTitle = title?.takeIf { it.isNotBlank() } ?: "Event"
                val (calName, calAcct) = names[calId] ?: (null to null)
                return NextEventInfo(safeTitle, b, e, allDay, calName, calAcct)
            }
        }
        return null
    }
}
