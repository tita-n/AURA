package com.aura.domain

/**
 * Pure mapping from a tapped ACTION chip + its parent result to the proposed action.
 * Deterministic, no Android APIs. The mapped result still passes through L3 validation
 * before execution — this only proposes.
 */
object ActionChipMapper {

    fun map(parent: ResolvedResult, chip: ActionChipData): ResolvedResult? {
        val contactId = when (val action = parent.action) {
            is AuraAction.SendMessage -> action.contactId
            is AuraAction.Dial -> action.phoneNumber
            else -> parent.id.removePrefix("contact:")
        }
        return when (chip.id.lowercase()) {
            "message" -> parent.copy(
                type = ResultType.Message,
                action = AuraAction.SendMessage(contactId, channel = "default", message = null),
                subtitle = parent.subtitle
            )
            "call" -> parent.copy(
                type = ResultType.Call,
                action = AuraAction.Dial(contactId)
            )
            "email" -> parent.copy(
                type = ResultType.Email,
                action = AuraAction.SendEmail(contactId)
            )
            else -> null // unknown chip — never invented into an action
        }
    }
}
