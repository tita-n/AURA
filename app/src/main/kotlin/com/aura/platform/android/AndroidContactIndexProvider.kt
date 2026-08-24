package com.aura.platform.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.ContactsContract
import com.aura.resolver.ContactIndexSource
import com.aura.resolver.L0IndexFactory

/**
 * ONLY place in AURA that imports ContactsContract/ContentResolver/Cursor.
 * Data minimization: extracts stable id, display name, phone numbers, email addresses — nothing else.
 * In-memory only: no caching to disk, no logging of numbers/addresses, no transmission.
 * Returns an empty list (never a fake error) when READ_CONTACTS is not granted.
 */
class AndroidContactIndexProvider(
    private val context: Context
) : ContactIndexSource {

    companion object {
        const val CONTACTS_PERMISSION = Manifest.permission.READ_CONTACTS
    }

    fun hasContactsPermission(): Boolean =
        context.checkSelfPermission(CONTACTS_PERMISSION) == PackageManager.PERMISSION_GRANTED

    override fun getContactEntities(): List<com.aura.resolver.IndexedEntity> {
        if (!hasContactsPermission()) return emptyList()
        val phonesByContact = mutableMapOf<Long, MutableList<String>>()
        val emailsByContact = mutableMapOf<Long, MutableList<String>>()

        // Phones — ordered by contact, primary first for deterministic target selection.
        queryInto(ContactsContract.CommonDataKinds.Phone.CONTENT_URI) { c ->
            val id = c.getLong(c.getColumnIndexOrThrow(ContactsContract.Data.CONTACT_ID))
            val number = c.getString(c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)) ?: return@queryInto
            val primary = c.getInt(c.getColumnIndexOrThrow(ContactsContract.Data.IS_SUPER_PRIMARY))
            val list = phonesByContact.getOrPut(id) { mutableListOf() }
            if (primary == 1) list.add(0, number.trim()) else list.add(number.trim())
        }

        // Emails
        queryInto(ContactsContract.CommonDataKinds.Email.CONTENT_URI) { c ->
            val id = c.getLong(c.getColumnIndexOrThrow(ContactsContract.Data.CONTACT_ID))
            val address = c.getString(c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.ADDRESS)) ?: return@queryInto
            emailsByContact.getOrPut(id) { mutableListOf() }.add(address.trim())
        }

        // Display names
        val entities = mutableListOf<com.aura.resolver.IndexedEntity>()
        queryInto(ContactsContract.Contacts.CONTENT_URI) { c ->
            val id = c.getLong(c.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
            val name = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)) ?: return@queryInto
            if (name.isBlank()) return@queryInto
            // Data minimization: a contact is indexable only if reachable (has phone or email)
            val phones = phonesByContact[id].orEmpty().filter { it.isNotBlank() }
            val emails = emailsByContact[id].orEmpty().filter { it.isNotBlank() }
            if (phones.isEmpty() && emails.isEmpty()) return@queryInto

            val disambiguation = when {
                phones.isNotEmpty() && emails.isNotEmpty() -> "phone · email"
                phones.isNotEmpty() -> "phone"
                else -> "email"
            }
            entities += L0IndexFactory.contactEntity(
                contactId = id.toString(),
                displayName = name,
                disambiguation = disambiguation,
                phones = phones.distinct(),
                emails = emails.distinct()
            )
        }
        return entities
    }

    private inline fun queryInto(uri: android.net.Uri, row: (Cursor) -> Unit) {
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use { while (it.moveToNext()) row(it) }
        } catch (_: SecurityException) {
            // Permission revoked mid-use — degrade gracefully; partial data is acceptable.
        } catch (_: IllegalArgumentException) {
            // Bad column on some OEM builds — skip this dataset rather than crash.
        }
    }
}
