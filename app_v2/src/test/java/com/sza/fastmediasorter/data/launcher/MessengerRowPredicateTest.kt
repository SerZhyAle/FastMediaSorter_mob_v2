package com.sza.fastmediasorter.data.launcher

import android.provider.ContactsContract
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2240: the rule that decides whether an app resolving a contact data row is a messenger.
 *
 * Worth its own test because it is the one part of the messenger enumeration that can be wrong without
 * failing - a rule that is too loose puts the system contacts app in the messenger list, and one that is
 * too tight shows an empty list on a phone that has messengers installed. Neither crashes.
 */
@Suppress("FunctionNaming")
class MessengerRowPredicateTest {

    @Test
    fun `an app declaring its own contact row type is a messenger`() {
        assertTrue(
            declaresMessengerMimeType(listOf("vnd.android.cursor.item/vnd.org.telegram.messenger.tgm")),
        )
    }

    @Test
    fun `a messenger is still found among built-in types`() {
        val declared = listOf(
            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
            "vnd.android.cursor.item/vnd.com.whatsapp.profile",
        )

        assertTrue(declaresMessengerMimeType(declared))
    }

    /** The dialler and the contacts editor resolve contact rows too, and neither is a messenger. */
    @Test
    fun `an app declaring only platform types is not a messenger`() {
        val declared = listOf(
            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE,
        )

        assertFalse(declaresMessengerMimeType(declared))
    }

    @Test
    fun `an app declaring no types at all is not a messenger`() {
        assertFalse(declaresMessengerMimeType(emptyList()))
    }

    /**
     * `IntentFilter` stores a wildcard type pattern as the bare family, which is how the system contacts
     * app claims every contact row at once. Claiming everything names nothing, so it is not a messenger.
     */
    @Test
    fun `an app claiming the whole contact-item family is not a messenger`() {
        assertFalse(declaresMessengerMimeType(listOf("vnd.android.cursor.item")))
    }

    @Test
    fun `an explicit wildcard subtype is not a messenger either`() {
        assertFalse(declaresMessengerMimeType(listOf("vnd.android.cursor.item/*")))
    }

    /** A directory-level type is a different family - a list of people, not a row on one. */
    @Test
    fun `a contact directory type is not a messenger`() {
        assertFalse(declaresMessengerMimeType(listOf("vnd.android.cursor.dir/contact")))
    }
}
