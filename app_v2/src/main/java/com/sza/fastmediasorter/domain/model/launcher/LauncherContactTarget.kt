package com.sza.fastmediasorter.domain.model.launcher

/**
 * What a contact cell does when tapped. One target kind, four outcomes (strategic ADR-4).
 *
 * The order is a persistence format: [LauncherCellCommand.Contact] stores the entry by name, so a new
 * action is appended and an existing one is never reordered or renamed.
 */
enum class LauncherContactAction {
    PROFILE,
    DIAL,
    MESSAGE,

    /** S0428: hands the number to the system messaging app, which [MESSAGE] cannot - it needs a thread. */
    SMS,
}

/**
 * S1176: a contact the user pinned to the launcher desktop, captured **at pin time**.
 *
 * **This is a snapshot, and that is the whole design (ADR-1).** The values here were read once, from
 * the record the system contact picker handed back under its own one-time grant. Nothing re-reads the
 * address book afterwards, so a contact later renamed, re-numbered or deleted keeps showing the old
 * label on the cell until the user re-pins it. `READ_CONTACTS` is declared and optional since S1335,
 * but nothing here asks for it: the pick reads under the picker's own grant, so a cell costs the user
 * no permission at all. S1206 is where a live refresh path, if it is ever wanted, belongs.
 *
 * Fields are per-action and only one of them is load-bearing at a time: [lookupKey] opens the profile,
 * [phoneNumber] fills the dialler, and [messageDataId] + [messagePackage] address the exact messaging
 * row the user picked. The others stay empty rather than being modelled as three classes, because the
 * three differ by outcome, not by nature, and one storage format lets a fourth action (SMS, video call)
 * arrive without a migration.
 */
data class LauncherContactTarget(
    val action: LauncherContactAction,
    /** `ContactsContract` lookup key - stable across a contact's own edits, unlike a raw id. */
    val lookupKey: String = "",
    val phoneNumber: String = "",
    /** Data-row id of the picked messaging channel; [NO_DATA_ID] when this is not a MESSAGE target. */
    val messageDataId: Long = NO_DATA_ID,
    /** Package that registered the picked messaging row - the row means nothing without its app. */
    val messagePackage: String = "",
    /** Cell caption. User data: it may contain any character, including the encoder's separator. */
    val displayName: String = "",
) {

    /** Whether this snapshot carries what its own action needs; a half-written cell renders as broken. */
    val isUsable: Boolean
        get() = when (action) {
            LauncherContactAction.PROFILE -> lookupKey.isNotEmpty()
            LauncherContactAction.DIAL, LauncherContactAction.SMS -> phoneNumber.isNotEmpty()
            LauncherContactAction.MESSAGE -> messageDataId != NO_DATA_ID && messagePackage.isNotEmpty()
        }

    companion object {
        const val NO_DATA_ID = -1L
    }
}

/**
 * S1176: one messaging channel a contact offers, as a ready-to-pin [target] plus the [label] to show
 * while choosing between several.
 *
 * The label is whatever the messaging app itself wrote on the contact's row ("Message +7 999 .."), so
 * it already reads the way that app's users expect; the app's own name is only the fallback. The pair
 * exists because the choice is transient - once the user has picked, only the target is stored.
 */
data class LauncherContactChannel(
    val target: LauncherContactTarget,
    val label: String,
)
