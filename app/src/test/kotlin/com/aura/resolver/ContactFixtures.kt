package com.aura.resolver

/**
 * Real-contact fixtures mirroring the spec's 10 cases. No real personal data.
 * Used to verify indexing, resolution, validation, and capability behavior.
 */
object ContactFixtures {

    fun all(): List<IndexedEntity> = listOf(
        // 1. Dad — one phone
        L0IndexFactory.contactEntity("c1", "Dad", "mobile", phones = listOf("+10000000001")),
        // 2. Mum — one phone
        L0IndexFactory.contactEntity("c2", "Mum", "mobile", phones = listOf("+10000000002")),
        // 3. Sarah A — phone + email
        L0IndexFactory.contactEntity("c3", "Sarah", "phone · email",
            phones = listOf("+10000000003"), emails = listOf("sarah.a@example.com")),
        // 4. Sarah B — phone only
        L0IndexFactory.contactEntity("c4", "Sarah", "phone", phones = listOf("+10000000004")),
        // 5. Sarah M. — email only
        L0IndexFactory.contactEntity("c5", "Sarah M.", "email", emails = listOf("sarah.m@example.com")),
        // 6/8. Multi-phone contact (two numbers, deterministic order)
        L0IndexFactory.contactEntity("c6", "Alex Work", "work · mobile",
            phones = listOf("+10000000061", "+10000000062")),
        // 9. Multi-email contact
        L0IndexFactory.contactEntity("c7", "Sam Dual", "personal · work",
            phones = listOf("+10000000071"),
            emails = listOf("sam.personal@example.com", "sam.work@example.com")),
        // 6b. Contact with no phone (email only) — Dial must be unavailable
        L0IndexFactory.contactEntity("c8", "NoPhone Pat", "email", emails = listOf("nop@example.com")),
        // 7b. Contact with no email — Email must be unavailable
        L0IndexFactory.contactEntity("c9", "NoMail Lee", "phone", phones = listOf("+10000000091"))
    )

    fun index(): L0Index = L0Index.build(all())
}
