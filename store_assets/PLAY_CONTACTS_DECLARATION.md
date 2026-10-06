<sub class="doc-stamp">26.10.02 14:53</sub>

# Play Console - Contacts Permission declaration (S4030)

Source of truth for the **Contacts Permission** form in Play Console
(`Policy and programs -> App content -> Contacts Permission`, as named in Google's April 2026 policy email).

The form lives only in the web console and has no API, so this file is the only reviewable copy of what
is declared. The earlier All files access declaration was rejected over text a reviewer refuted in a
minute (`PLAY_PERMISSIONS_DECLARATION.md`); the same mistake is avoided here by writing the text where it
can be read, measured against the code and corrected before it is submitted.

Edit **here** first, then paste into the console. Do not compose the text in the console.

The checklist step that walks the operator through the form is `PLAY_CONSOLE_CHECKLIST.md` section C5.
The status of the declaration is recorded in `docs/PLAY_PUBLISHING_STATE.md`, in the block marked
`s4030:transcribed:contacts-declaration`.

---

## Permission

- Permission: `android.permission.READ_CONTACTS`.
- Flavor: `standard` (the Play build). Package: `com.sza.fastmediasorter`.
- `noLegal` is distributed outside Play and is out of scope; it keeps the permission whatever Play decides.
- Enforcement: the policy applies to apps with targetSdk 37 or higher from 2027-01-27. The app is on
  targetSdk 36, so nothing is blocked yet; the declaration is prepared ahead of the bump.

---

## Use case (paste)

Uses 1629 characters (newlines counted as one character each, ASCII only). The form's own limit is not known
yet - see "Console limits".

```text
FastMediaSorter can act as the device home screen. On that desktop the user may pin a single person as a shortcut cell (open the contact card, call that person, send that person an SMS, or open that person's chat in a messenger the user chooses).

READ_CONTACTS is optional and is requested at runtime only at the moment the user pins a person, after an in-app explanation. The pick itself is made through the Android system contact picker (ACTION_PICK), which needs no permission. A contact card, a call and an SMS shortcut are pinned and keep working whether or not the permission is granted.

The permission serves two operations, both on the one contact the user pinned. First, the app reads that contact's display name and thumbnail photo URI, by its lookup key, and keeps the cell's name and photo current when the person is renamed or changes their photo. Second, when the user pins a shortcut that opens a messenger chat, the app reads the data rows of that one contact and keeps the row that the chosen messenger registered, so the shortcut opens that chat directly; the system picker's one-time grant does not cover those rows, so this lookup fails without the permission. The app never lists, searches or enumerates the address book and never reads e-mail addresses, postal addresses or any other field through this permission.

If the user declines or later revokes it, every shortcut already pinned keeps working from the data saved at pin time, and card, call and SMS shortcuts can still be pinned. Only a new messenger-chat shortcut cannot be created. The permission is not needed for any other feature of the app.
```

---

## What the app reads and what it never reads

Measured in the code on 2026-10-01 and on an API 35 emulator on 2026-10-02
(`PLAN/S4030_play-contacts-permission-declaration/research/02__contact-snapshot-data-paths.md`).

- Reads, with the permission, the display name and the thumbnail photo URI of the one contact the user
  pinned, looked up by that contact's lookup key, plus a change observer on the contacts provider that
  refreshes that one cell.
- Reads, with the permission, the data rows of the one contact the user picked when a messenger-chat
  shortcut is created: row id, MIME type, the row's action label, lookup key and display name. Rows of the
  built-in kinds are dropped, and only the row that the chosen messenger registered is kept. The query
  projection holds no phone number and no e-mail address.
- Never reads: a list of contacts, a search over contacts, e-mail addresses, postal addresses, groups,
  call logs or any other contacts field through this permission.
- Needs no permission: the pick of a contact card, a call or an SMS (the contact, its phone number, its
  name), which the system picker's one-time grant covers.
- Measured: with the permission revoked, creating a messenger-chat shortcut fails because the provider
  refuses the contact's `/entities` rows, and the app shows that the messenger has no channel for the
  contact. A messenger-chat shortcut pinned while the permission was granted still opens the chat after the
  permission is revoked; the tap uses the saved row id and the messenger package.
- Without the permission a card, call or SMS cell shows the name, number or generic label saved at pin time.

---

## Data handling - measured 2026-10-01

- The photo and the live name are read on demand and held in memory. The photo URI is never stored.
- The snapshot taken from the picker (lookup key, display name, phone number, messenger row id and
  package) is stored locally in the launcher cell table of the main database.
- That snapshot is included in Android Auto Backup and device-to-device transfer (the database is not
  excluded), in the user's own JSON backup (a local file or the user's own Google Drive) and in the
  settings transfer between devices.
- Nothing is sent to the developer or to any third-party service.
- Log lines carry only the failure kind, never a name, number, lookup key or photo URI.

---

## Data safety - Contacts category

Source: `https://support.google.com/googleplay/android-developer/answer/10787469` (read 2026-10-02).

Clauses that decide the question, verbatim:

> "Collect" means transmitting data from your app off a user's device.

> User data accessed by your app that is only processed locally on the user's device and not sent off device does not need to be disclosed.

> "Sharing" refers to transferring user data collected from your app to a third party.

> If the user chooses to upload their data directly to their own external drive or cloud storage account (such as Google Drive, Dropbox, or similar services) and this upload is governed by the external drive or cloud storage provider's terms of service and privacy policy, and your app never collects or accesses the data in question, then your app does not need to declare the collection of this data.

> Contacts: Information about the user's contacts such as contact names, message history, and social graph information like usernames, contact recency, contact frequency, interaction duration and call history.

What the page does not say: it never mentions Android Auto Backup, device-to-device transfer or an operating-system
backup. The decision below therefore rests on the definition of "collect" and on the cloud-storage clause, not on
a sentence about Auto Backup, and it says so.

Paths a pinned contact's saved details can take off the device, and who moves them:

- Android Auto Backup and device-to-device transfer: moved by the operating system to the user's own Google
  account or to the user's next device. The app transmits nothing and cannot read the backup; the developer has
  no access to it. The main database is not excluded, so the snapshot is inside.
- The user's own backup: a JSON file written to a place the user picks (a local file or the user's own Google
  Drive). The user starts it, the cloud provider's terms govern it, and the developer never receives it.
- The settings transfer between the user's devices: user-started, device to device.
- Nothing is sent to the developer or to any third-party service by the app. The live name and photo read with
  the permission are held in memory and never stored or sent.

Decision: Contacts is **not collected and not shared**. No row of the Contacts category is ticked for the
phone / tablet form factor. Basis: the app never transmits contact data off the device (the "collect"
definition), the live name and photo are processed locally (the on-device exemption), and the copies that
exist are made by the operating system or at the user's own request into the user's own storage, which the
developer never accesses (the cloud-storage clause). Auto Backup stays as it is: extraction rules act on
whole files and the snapshot lives in the main database, so excluding it would exclude the whole database.

If Play review reads the backup copy as collection, the answer to give instead is: Contacts collected, not
shared, optional (the user can use the app without pinning a person), purpose App functionality. Change this
file first, then the console, and re-read the privacy policy and the in-app explanation, which already
describe the same paths (Auto Backup and the user's own backup).

---

## If the form asks for evidence

Show the operation that the permission serves, and nothing else:

1. On a device with a synthetic contact (created for the recording, never a real person's), deny the
   permission, then pin that contact as a contact card through the system picker. The cell works from the
   snapshot.
2. With the permission still denied, start a "Message in an app" shortcut for the same contact: the app
   cannot find the chat, which is the operation that needs the permission. Grant the permission and repeat
   it: the shortcut is created and opens the messenger chat.
3. Rename the contact in the Contacts app and return to the launcher: the cell follows the new name. Do the
   same with the photo.

Do not pad the video with the viewers, the players, the camera or the radio.

---

## Console limits

The character limit of the use-case field and the form's own questions are not known until the account
can see the form (the row was not on the App content list on 2026-10-01). The first operator who opens
the form trims this file to the real limit and records the limit and the date here, before pasting.

---

## Extension for an early targetSdk 37 bump

- If targetSdk 37 is planned before Play has answered the declaration, request the 30-day extension
  first. The enforcement date for targetSdk 37 and higher is 2027-01-27.
- Write the date of the request on the `**Extension requested:**` line of the contacts block in
  `docs/PLAY_PUBLISHING_STATE.md`, and set its `**State:**` to `extension requested`.
- The exact console path of the extension is not recorded yet; the operator who first uses it writes it
  here.

---

## If the declaration is rejected

The rollback is prepared and ships switched off: `fms.readContacts` in `gradle.properties` is `on`.

- Set `fms.readContacts=off` in `gradle.properties`. The `standard` build then drops
  `READ_CONTACTS` from its merged manifest, hides the permission row in Settings, skips the explanation
  dialog and does not ask for the permission. `noLegal` ignores the property and keeps the permission.
- The Play build loses the live refresh of the name and photo, and it loses the creation of new
  messenger-chat shortcuts: the "Message in an app" row is left out of the add dialog, because the lookup
  of the chat needs the permission.
- The Play build keeps pinning a contact card, a call and an SMS through the system picker, and every
  messenger-chat shortcut pinned before the switch keeps opening its chat from the saved row.
- In the same change edit the records that name the permission for `standard`: the `standard` entry of
  the `permissions.contacts-permission` record in `docs/ALL_FEATURES.jsonl` (through
  `scripts/all_features/`), the flavor column of the `READ_CONTACTS` row in `docs/SECURITY_POSTURE.md`,
  and regenerate `docs/FLAVOR_MATRIX.md`.
- Record the verdict and its date in `docs/PLAY_PUBLISHING_STATE.md` (`**State:** rejected`) and release
  without the permission.

---

## What the rejected All files access submission teaches

Each of the lessons below is enough on its own to lose a review.

- Describe an operation that breaks without the permission, not a verdict about the app. Here the two
  operations are the live refresh of one pinned contact's name and photo and the lookup of that contact's
  messenger chat when a chat shortcut is created; say those and nothing broader.
- Never claim the permission is core when it is not. The text above calls the permission optional and
  says what happens without it, because a reviewer will test exactly that.
- A statement the reviewer can refute in a minute (for example that a picker cannot do something it can,
  or that pinning "works whether or not the permission is granted" for every kind of pin) is worse than a
  shorter text. The 2026-10-02 emulator run refuted that sentence for the messenger-chat pin before it was
  ever submitted: the picker's one-time grant does not cover a contact's `/entities` rows. The picker is
  used for the pick; the permission is for the chat lookup and for keeping one cell current.
- Keep the declaration, the Data safety answers, the privacy policy and the in-app explanation saying
  the same thing, and re-read all of them whenever one is edited.

---

## Submission is manual

The Play Developer API exposes no endpoint for the Contacts Permission form, so this text is pasted by
hand. After submitting, the operator writes the state and the date into
`docs/PLAY_PUBLISHING_STATE.md` and reads the verdict there on every later release.
