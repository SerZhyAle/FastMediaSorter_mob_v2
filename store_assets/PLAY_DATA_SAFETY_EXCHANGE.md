<sub class="doc-stamp">26.10.15 09:59</sub>

# Play Console - Data safety and account deletion, exchange between devices (S4241)

Source of truth for the **Data safety answers and the account-deletion block** that cover the exchange of
files, the clipboard and live broadcasts between a person's own devices - through an exchange server,
through the person's own Google Drive, or directly - for both form factors.

Like the location section (`PLAY_DATA_SAFETY_LOCATION.md`) and the All files access declaration
(`PLAY_PERMISSIONS_DECLARATION.md`), the form lives only in the web console: the Play Developer API has no
endpoint for it. Decide **here**, then answer in the console. Do not decide inside the console.

The repository holds no copy of the rest of the Data safety form, only the sections that have their own
file. Before answering, open the form as it stands in the console and reconcile it with this file; an answer
given here that the rest of the form contradicts is read by the reviewer as one form.

The set that must say the same thing, read together by the reviewer and by the user:

- `docs/PRIVACY_POLICY.md` (+ `-ru`, `-uk`), section "Exchange Between Your Devices";
- `docs/SECURITY_POSTURE.md`, section 3.1 and the network-surface rows;
- the in-app `perm_rationale_*` strings;
- `store_assets/PLAY_FGS_DECLARATIONS.md`;
- the contract text `DEVICE-EXCHANGE` 0.29, items DE (the honest text), DO (Drive not sealed), DQ (what the
  server keeps and sees).

---

## What leaves the device, to whom, and which clause decides it

Google's definitions and exemptions, read 2026-10-15 from
`https://support.google.com/googleplay/android-developer/answer/10787469`, verbatim:

> "Collect" means transmitting data from your app off a user's device.

> "Sharing" refers to transferring user data collected from your app to a third party.

> User data accessed by your app that is only processed locally on the user's device and not sent off device
> does not need to be disclosed.

- End-to-end encryption exemption: data "that is unreadable by you or anyone other than the sender and
  recipient as a result of end-to-end encryption", which "must not be readable by any intermediary entity,
  including the developer".
- Own cloud storage: "If the user chooses to upload their data directly to their own external drive or cloud
  storage account ... and your app never collects or accesses the data in question".
- Sharing exemption: "Transferring user data to a third party based on a specific user-initiated action".

What the page does **not** say: it has no rule for a server the user configures. The decision below rests on
the three clauses above and says so; it is not a restatement of a rule that does not exist.

Paths, and what each carries:

- **Content through the exchange server** - folder bytes (SSH inside the tunnel), broadcast media, the access
  records, the clipboard value with its kind, type and name. Sealed on the device (`DEVICE-EXCHANGE` items
  DE, DH, DI, DL, DM, DN); the server, and anyone running it, cannot read it. Covered by the end-to-end
  exemption.
- **Content through Google Drive** - files, the clipboard value, requests and replies, in the hidden
  `drive.appdata` folder of the person's own Google account, never addressed to another account. Not sealed
  (item DO); covered by the own-cloud clause, the reading this repository already uses for the person's own
  backup (`PLAY_CONTACTS_DECLARATION.md`, "Data safety - Contacts category").
- **Directory data to the exchange server** - not sealed, because the server cannot connect devices without
  it: the account login, each device's name, product, platform, roles and public key, presence and last-seen
  time, the names and roots of published folders, broadcast titles and modes, grants and the guest logins
  they name, and the size and timing of transfers (item DE lists them, item DQ restates). The server is a
  program the person runs or was invited to; the developer runs none, and nothing reaches the developer.
- **Directly on the network** - folder access and broadcasts between devices of one network. Nothing leaves
  the person's own devices.

---

## The answer

**Content: not declared** - end-to-end sealed on the exchange path, own-cloud on the Drive path.

**Directory data: declared as collected, not shared, optional.** This is the conservative reading, chosen
because the definition of "collect" is literal about any transmission off the device and the page has no
exception for a server the user entered. It is also what the privacy policy says in its own words: the server
knows the names of the account, devices, folders and broadcasts and which devices are online.

| Data type (console category) | What it is here | Collected | Shared | Optional | Purpose |
| --- | --- | --- | --- | --- | --- |
| Personal info - User IDs | the login of the exchange account | yes | no | yes | App functionality |
| Device or other IDs | the exchange device id and name of each own device, with presence | yes | no | yes | App functionality |
| Files and docs - Files and docs (names) | the names of published folders and broadcast titles | yes | no | yes | App functionality |

- **Encrypted in transit:** yes - TLS to the server, and the Google Drive path is HTTPS; content is
  additionally sealed on the device.
- **Users can request deletion:** yes - see "Account deletion" below.
- **Shared: none.** Nothing is transferred to a third party by the developer. The exchange server is entered
  by the person, so the transfer is the person's own action.
- **No ads, no analytics, no data to the developer** - unchanged (`docs/SECURITY_POSTURE.md` section 4).

Each form factor is filled separately in the console and the Wear answer is not inherited from the phone:

| Form factor | Directory data | Content | Account creation |
| --- | --- | --- | --- |
| Phone / tablet | declared as above | not declared | yes - registration on a server from the app |
| Wear OS | declared as above - the watch enrolls its own device record and sends its device name and presence | not declared | no - the watch takes a sign-in from the paired phone; it has no registration screen |

**Fallback, if review reads the answer differently.** Two directions, both written here so they are not
re-derived:

- *Reviewer says directory data need not be declared* (the server is the person's own storage): remove the
  three rows. The privacy policy stays as written - it already states what the server sees.
- *Reviewer says content must be declared too* (the E2E exemption is refused because the operator could add
  a device of their own to the account, as `DEVICE-EXCHANGE` item DE states honestly): declare Photos and
  videos, Audio files, Files and docs and the clipboard (Other user-generated content) as collected, not
  shared, optional, App functionality, encrypted in transit. Change this file first, then the console.

---

## Account deletion

Source of the requirement, read 2026-10-15 from
`https://support.google.com/googleplay/android-developer/answer/13327111`:

> If your app allows users to create an account from within your app

An app account is "a unique user identity that developers provide as a user-facing feature". The owner treats
the exchange account as an internal tool of the person, like a PIN (S4203 decision 25), but the app does let a
person create it from an invitation, and offering creation anywhere in the app triggers the requirement
("you still need to offer app account deletion even if some features can be accessed without an account").
This file therefore answers **as an app that allows account creation** and meets both halves:

- **In the app:** My devices, the menu of the server card, "Delete my account", confirmed with the password
  (S4223). The server erases at once the account and its password hash, every device record and token, every
  folder and broadcast record, the clipboard, pairing codes and what was given to or by the account; the
  login becomes free.
- **On the web:** a page of the media exchange portal, "How to delete an account", published by S4243. The
  console field takes its address. Proposed address, to be confirmed against the page S4243 publishes:
  `https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/account-deletion.html`
  (`-ru` and `-uk` editions beside it). **Do not submit the form while the address does not answer a
  page.** Until then the in-app "How to delete an account" button opens `exchange-server.html`, which is
  not a deletion page.

What the page must contain, from the same Google page:

- It references the app and the developer by name.
- The deletion path is prominently featured and easy to find on the page.
- A person can request deletion "without sending the user back to the app and requiring them to re-download
  it" - for a lost or reset phone that means: the operator of the server deletes any account on it, and the
  page says how to reach the operator; for a server the person runs, the same operation in the server's own
  program (`DEVICE-EXCHANGE` item AU: the operator may delete any account, and the erasure is the same).
- It states what is retained: the server's log, within its operator's retention setting, carrying no
  password, token, invitation or content; copies already delivered to the person's other devices or to the
  people given access; files in the person's own Google Drive, deleted there. The privacy policy states the
  same ("Deleting your account").

**The honest gap.** The developer runs no exchange server and cannot delete an account on one that is not
his. For a server run by someone else the page can only name the person to ask. Google's text requires a
support flow where extra steps are needed; the page names the operator and the developer's contact
(`sza@ukr.net`, as in the privacy policy). A reviewer may read that as insufficient; the response is the
decision of S4203 (no public server in release 46, a server is the person's own or a trusted person's).

**Wear OS.** The watch cannot create or delete an account. Point the Wear form's deletion link at the same
page, and say in the page that deleting the account on the phone also ends the watch's enrollment.

---

## Operator steps

1. Wait for S4243 to publish the deletion page in three languages; confirm its address and that it answers.
2. Open `App content -> Data safety` and pick the form factor first - phone and Wear OS are filled
   separately.
3. Phone: declare the three directory rows of the table, collected and not shared, optional, App
   functionality, encrypted in transit; leave Content unchecked. Answer the deletion questions with the page
   address and the in-app path.
4. Wear OS: the same three rows; account creation answered "no"; deletion link to the same page.
5. Confirm the privacy policy page at its published address carries "Exchange Between Your Devices" in EN,
   RU and UK before sending anything for review.
6. Under a policy enforcement Play refuses automatic review - send the held edit from
   `Publishing overview -> Send changes for review`.
7. Write the state and the date into `docs/PLAY_PUBLISHING_STATE.md`.

**Timing.** Do not touch the console while a submission is under review; an edit restarts the review from
the beginning.
