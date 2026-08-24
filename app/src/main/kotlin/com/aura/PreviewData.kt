package com.aura

import com.aura.domain.*

/**
 * Preview/sample data — fake actions only. No Android APIs are called.
 * These represent what the Deterministic Action Layer will validate and execute later.
 */
object PreviewData {

    val actApp = ResolvedResult(
        id = "1",
        title = "Chrome",
        subtitle = null,
        type = ResultType.App,
        action = AuraAction.OpenApp("com.android.chrome")
    )

    val actContactWithChips = ResolvedResult(
        id = "2",
        title = "Sarah",
        subtitle = "WhatsApp · usually",
        type = ResultType.Contact,
        action = AuraAction.SendMessage("sarah_1", "WhatsApp"),
        actionChips = listOf(
            ActionChipData("whatsapp", "Message"),
            ActionChipData("call", "Call")
        )
    )

    val actMath = ResolvedResult(
        id = "3",
        title = "13500",
        subtitle = null,
        type = ResultType.Math,
        action = AuraAction.Copy("13500"),
        inlineValue = "13,500",
        inlineQuery = "500 * 27"
    )

    val actAlarm = ResolvedResult(
        id = "4",
        title = "Alarm set for 6:30",
        subtitle = null,
        type = ResultType.Alarm,
        action = AuraAction.SetAlarm(6, 30),
        undoable = true
    )

    val askWhichSarah = CandidateGroup(
        label = "Which Sarah",
        candidates = listOf(
            CandidateItemData("s1", "Sarah Okafor", "sarah.okafor@email.com"),
            CandidateItemData("s2", "Sarah Lindqvist", "called yesterday"),
            CandidateItemData("s3", "Sarah M.", "mobile")
        )
    )

    val askChooseAction = CandidateGroup(
        label = "Choose an action",
        candidates = listOf(
            CandidateItemData("a1", "Message Sarah", "WhatsApp"),
            CandidateItemData("a2", "Call Sarah", "Phone"),
            CandidateItemData("a3", "Email Sarah", "Gmail")
        )
    )

    val askRelated = CandidateGroup(
        label = "Related to Sarah",
        candidates = listOf(
            CandidateItemData("r1", "Sarah — WhatsApp", "recent thread"),
            CandidateItemData("r2", "Notes · Sarah", "yesterday")
        )
    )

    val errorExample = CommandError(
        message = "Couldn't reach Settings",
        fallback = AuraAction.SearchPlayStore("Settings")
    )
}
