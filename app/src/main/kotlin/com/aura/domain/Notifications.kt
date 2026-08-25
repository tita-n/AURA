package com.aura.domain

/**
 * Pure notification domain model — no Android framework types.
 * Populated exclusively by the platform adapter (NotificationListenerService boundary).
 */
data class NotificationItem(
    val key: String,              // stable Android notification key
    val packageName: String,
    val appLabel: String,
    val title: String?,
    val body: String?,
    val timestamp: Long,
    val category: String? = null,
    val isOngoing: Boolean = false,
    val isConversation: Boolean = false,
    val importance: Int = 0       // Android importance (0-5) used deterministically, never surfaced raw
)

enum class NotificationTier { PRIORITY, STANDARD, LOW }

/**
 * Deterministic v1 priority rules (PRD §9.4 conservative tier):
 * PRIORITY — conversations, calls, calendar alerts, high-importance alerting.
 * LOW      — silent (importance <= low), ongoing/background.
 * STANDARD — everything else that alerted.
 */
object NotificationRules {

    fun tier(item: NotificationItem): NotificationTier {
        val cat = item.category?.uppercase()
        if (item.isConversation) return NotificationTier.PRIORITY
        if (cat == "CALL" || cat == "ALARM") return NotificationTier.PRIORITY
        if (item.importance >= 4) return NotificationTier.PRIORITY      // HIGH/MAX alerting
        if (item.isOngoing) return NotificationTier.LOW                 // ongoing/system
        if (item.importance in 0..2) return NotificationTier.LOW        // silent/min/low
        return NotificationTier.STANDARD
    }

    /** Newest first within a tier — stable tiebreak on key. */
    fun sortedByRecency(items: List<NotificationItem>): List<NotificationItem> =
        items.sortedWith(compareByDescending<NotificationItem> { it.timestamp }.thenBy { it.key })
}

/**
 * Deterministic grouping: PRIORITY notifications stay individual (distinction matters);
 * STANDARD and LOW collapse per app. Groups never mix apps.
 */
object NotificationGrouping {

    data class Group(val packageName: String, val appLabel: String, val items: List<NotificationItem>)

    data class Model(
        val priority: List<NotificationItem>,
        val otherGroups: List<Group>
    )

    fun build(items: List<NotificationItem>): Model {
        val priority = items.filter { NotificationRules.tier(it) == NotificationTier.PRIORITY }
        val rest = items.filter { NotificationRules.tier(it) != NotificationTier.PRIORITY }

        val groups = rest.groupBy { it.packageName }
            .map { (pkg, list) ->
                Group(pkg, list.firstOrNull()?.appLabel ?: pkg, NotificationRules.sortedByRecency(list))
            }
            .sortedWith(compareByDescending<Group> { it.items.size }.thenBy { it.appLabel })

        return Model(NotificationRules.sortedByRecency(priority), groups)
    }
}
