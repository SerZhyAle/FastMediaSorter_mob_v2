package com.sza.fastmediasorter.data.launcher

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import com.sza.fastmediasorter.domain.model.launcher.LauncherContactAction
import com.sza.fastmediasorter.domain.model.launcher.LauncherContactChannel
import com.sza.fastmediasorter.domain.model.launcher.LauncherContactTarget
import com.sza.fastmediasorter.util.queryIntentActivitiesCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1176: the only seam onto `ContactsContract`, and the only place that turns a picked record into the
 * snapshot a desktop cell stores.
 *
 * **This class works without any contacts permission, and that is deliberate - do not "fix" it by
 * adding `READ_CONTACTS`.** Every read here happens on a URI the system contact picker just handed
 * back, under the one-time grant it attached to that single record. The user chose which contact to
 * expose, which is exactly the trade the owner approved (strategic §3.2): the app never sees the
 * address book, and in exchange the cell holds a snapshot that does not follow later edits.
 *
 * The grant is narrow in a second way that shapes [readMessageChannels]: it covers the picked record,
 * so a provider that refuses the deeper read is an expected outcome, not a defect. Every query
 * degrades to "nothing found" and the caller tells the user, rather than crashing on a SecurityException.
 *
 * Nothing about the person is logged - not the name, the number, the lookup key, nor the channel. Only
 * the failure kind ever reaches Timber.
 */
@Singleton
class ContactSnapshotDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * The system picker whose result [readProfile] / [readDialTarget] / [readMessageChannels] consume.
     *
     * `DIAL` gets the phone-number picker rather than the contact picker on purpose: it returns the
     * exact number row the user chose, so a contact with a work and a mobile number pins the one they
     * meant instead of whichever the app would have guessed - and that row is readable under the grant
     * with no deeper query at all.
     */
    fun pickIntent(action: LauncherContactAction): Intent = when (action) {
        LauncherContactAction.DIAL ->
            Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)

        LauncherContactAction.PROFILE, LauncherContactAction.MESSAGE ->
            Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
    }

    /** Contact-level read: enough to open the person's card later, nothing more. */
    suspend fun readProfile(contactUri: Uri): LauncherContactTarget? = withContext(Dispatchers.IO) {
        readRow(contactUri, PROFILE_PROJECTION) { cursor ->
            LauncherContactTarget(
                action = LauncherContactAction.PROFILE,
                lookupKey = cursor.getString(INDEX_PROFILE_LOOKUP_KEY).orEmpty(),
                displayName = cursor.getString(INDEX_PROFILE_NAME).orEmpty(),
            )
        }?.takeIf { it.isUsable }
    }

    /** Reads the picked phone row itself, so the number is the one the user pointed at. */
    suspend fun readDialTarget(phoneUri: Uri): LauncherContactTarget? = withContext(Dispatchers.IO) {
        readRow(phoneUri, PHONE_PROJECTION) { cursor ->
            LauncherContactTarget(
                action = LauncherContactAction.DIAL,
                lookupKey = cursor.getString(INDEX_PHONE_LOOKUP_KEY).orEmpty(),
                phoneNumber = cursor.getString(INDEX_PHONE_NUMBER).orEmpty(),
                displayName = cursor.getString(INDEX_PHONE_NAME).orEmpty(),
            )
        }?.takeIf { it.isUsable }
    }

    /**
     * The messaging channels this contact carries, as their own apps registered them.
     *
     * No messenger is hardcoded and none is discovered by package name: a channel exists because some
     * app wrote a data row of its own MIME type onto this contact, and that same MIME type is what
     * resolves back to the app. An app since uninstalled therefore resolves to nothing and drops out,
     * which is the "only offer channels whose app is installed" rule falling out of the lookup itself.
     */
    suspend fun readMessageChannels(contactUri: Uri): List<LauncherContactChannel> =
        withContext(Dispatchers.IO) {
            val entityUri = Uri.withAppendedPath(
                contactUri,
                ContactsContract.Contacts.Entity.CONTENT_DIRECTORY,
            )
            val channels = mutableListOf<LauncherContactChannel>()
            queryOrNull(entityUri, ENTITY_PROJECTION)?.use { cursor ->
                while (cursor.moveToNext()) {
                    channelFrom(cursor)?.let(channels::add)
                }
            }
            channels.distinctBy { it.target.messageDataId }
        }

    private fun channelFrom(cursor: Cursor): LauncherContactChannel? {
        val mimeType = cursor.getString(INDEX_ENTITY_MIMETYPE)
            ?.takeIf { it.isNotEmpty() && it !in BUILT_IN_MIME_TYPES }
            ?: return null
        val dataId = cursor.getLong(INDEX_ENTITY_DATA_ID)
        val dataUri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, dataId)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(dataUri, mimeType)
        return context.packageManager.queryIntentActivitiesCompat(intent).firstOrNull()?.let { owner ->
            LauncherContactChannel(
                target = LauncherContactTarget(
                    action = LauncherContactAction.MESSAGE,
                    lookupKey = cursor.getString(INDEX_ENTITY_LOOKUP_KEY).orEmpty(),
                    messageDataId = dataId,
                    messagePackage = owner.activityInfo.packageName,
                    displayName = cursor.getString(INDEX_ENTITY_NAME).orEmpty(),
                ),
                label = cursor.getString(INDEX_ENTITY_LABEL)?.takeIf { it.isNotBlank() }
                    ?: owner.loadLabel(context.packageManager).toString(),
            )
        }
    }

    private fun <T> readRow(uri: Uri, projection: Array<String>, map: (Cursor) -> T): T? =
        queryOrNull(uri, projection)?.use { cursor ->
            if (cursor.moveToFirst()) map(cursor) else null
        }

    /**
     * A refused or vanished read is a normal state of the world here - the grant is one-time and scoped
     * to the picked record - so it degrades to "no data" with the failure kind logged and nothing else.
     */
    private fun queryOrNull(uri: Uri, projection: Array<String>): Cursor? = runCatching {
        context.contentResolver.query(uri, projection, null, null, null)
    }.getOrElse { error ->
        Timber.i("Launcher contacts: read unavailable (%s)", error.javaClass.simpleName)
        null
    }

    private companion object {
        val PROFILE_PROJECTION = arrayOf(
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
        )
        const val INDEX_PROFILE_LOOKUP_KEY = 0
        const val INDEX_PROFILE_NAME = 1

        val PHONE_PROJECTION = arrayOf(
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
        )
        const val INDEX_PHONE_LOOKUP_KEY = 0
        const val INDEX_PHONE_NUMBER = 1
        const val INDEX_PHONE_NAME = 2

        val ENTITY_PROJECTION = arrayOf(
            ContactsContract.Contacts.Entity.DATA_ID,
            ContactsContract.Contacts.Entity.MIMETYPE,
            ContactsContract.Contacts.Entity.DATA3,
            ContactsContract.Contacts.Entity.LOOKUP_KEY,
            ContactsContract.Contacts.Entity.DISPLAY_NAME_PRIMARY,
        )
        const val INDEX_ENTITY_DATA_ID = 0
        const val INDEX_ENTITY_MIMETYPE = 1

        /** `DATA3` is where a messaging app writes the row's action text ("Message +7 999 .."). */
        const val INDEX_ENTITY_LABEL = 2
        const val INDEX_ENTITY_LOOKUP_KEY = 3
        const val INDEX_ENTITY_NAME = 4

        /**
         * Spelled out rather than read from `Im` / `SipAddress`, whose constants are deprecated. The
         * strings are not a copy of an API - they are what is written in the contacts database, so a
         * row created years ago still carries them and still has to be skipped.
         */
        const val LEGACY_IM_MIME_TYPE = "vnd.android.cursor.item/im"
        const val LEGACY_SIP_MIME_TYPE = "vnd.android.cursor.item/sip_address"

        /**
         * Rows the platform itself defines. A messaging channel is by definition a row nobody but its
         * own app understands, so the filter is "not one of these" rather than a list of known
         * messengers - which would have to grow with every app the user installs.
         */
        val BUILT_IN_MIME_TYPES = setOf(
            ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Identity.CONTENT_ITEM_TYPE,
            LEGACY_IM_MIME_TYPE,
            ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Relation.CONTENT_ITEM_TYPE,
            LEGACY_SIP_MIME_TYPE,
            ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE,
        )
    }
}
