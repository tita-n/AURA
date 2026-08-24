package com.aura.domain

/**
 * Pure mapping from a tapped ACTION chip + its parent result to the proposed action.
 * Deterministic, no Android APIs. The mapped result still passes through L3 validation
 * before execution — this only proposes.
 *
 * Targets (phone/email) are carried over from the parent's validated proposal so L3
 * can confirm capability; a genuinely missing target surfaces as Unavailable, never
 * as a guessed action.
 */
object ActionChipMapper {

    fun map(parent: ResolvedResult, chip: ActionChipData): ResolvedResult? {
        val (contactId, phone, emailAddress) = when (val action = parent.action) {
            is AuraAction.SendMessage -> Triple(action.contactId, action.phone, null)
            is AuraAction.Dial -> Triple(action.contactId ?: "", action.phoneNumber, null)
            is AuraAction.SendEmail -> Triple(action.contactId, null, action.emailAddress)
            else -> Triple(parent.id.removePrefix("contact:"), null, null)
        }

        return when (chip.id.lowercase()) {
            "message" -> parent.copy(
                type = ResultType.Message,
                action = AuraAction.SendMessage(
                    contactId = contactId,
                    channel = "default",
                    message = null,
                    phone = phone
                )
            )
            "call" -> parent.copy(
                type = ResultType.Call,
                action = AuraAction.Dial(
                    phoneNumber = phone ?: "",
                    contactId = contactId.ifBlank { null }
                )
            )
            "email" -> parent.copy(
                type = ResultType.Email,
                action = AuraAction.SendEmail(
                    contactId = contactId,
                    emailAddress = emailAddress
                )
            )
            else -> null // unknown chip — never invented into an action
        }
    }
}
