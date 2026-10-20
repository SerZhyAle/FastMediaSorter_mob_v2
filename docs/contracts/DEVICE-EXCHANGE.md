<sub class="doc-stamp">26.10.18 17:46</sub>

# Pointer - `DEVICE-EXCHANGE`

| | |
| --- | --- |
| **Id** | `DEVICE-EXCHANGE` |
| **Version** | 1.5, active - 2026-10-19 (S4395: exchange server mark added to item CW and CV; mark of EXCHANGE-MARK); 1.4 - 2026-10-18 (S4356: `REMOTE-ADMIN.md`, the administration stream, its operations and the ten reply codes); 1.3 - 2026-10-17 (the consolidation amendment: a higher `FMSEXCH` prefix answers the canonical `format-unsupported` refusal, and the server transcript corpora are the client's replay evidence); 1.2 - 2026-10-17 (S4272: item DJ gains the registering first box of a grant, item DH allows its empty `keys`; no wire member changes); 1.1 - 2026-10-15 (S4276: `vectors/fmsw-224/` adopted beside the earlier server corpus; no wire byte changes); 1.0 - declared 2026-10-15 (S4245, the owner's decision 22 of milestone S4203: every section binds, the text is that of 0.30 and no wire byte changes; the contract only grows from here); 0.30 - consolidation edition, 2026-10-15 (S4266: the amendments folded into the sections they amend, every item letter kept, old addresses mapped in `HISTORY.md`); 0.29 - end-to-end encryption of content (section 27; section 26, 0.28, how long a phone shares and why it went offline; section 25, 0.27, the shared terms, the names of the programs and the one table of connection messages; section 24, 0.26, the path "via Google Drive" carrying files; section 23, 0.25, the roles of every product, the watch, the account's broadcast list, cast and the hand-over to the watch; section 22, 0.24, the clipboard; section 21, 0.23, folded the open proposals). Owner: FastMediaSorter Android |
| **Home** | `device-exchange/README.md` in the shared contracts catalog; `HISTORY.md` beside it holds the log and the map of old section addresses, `PHONE-AND-WATCH.md` the digest for the phone and the watch |
| **Role here** | owner; phone - `fms-android`: resource producer and consumer, broadcaster, receiver, enrolls the watch; watch - `fms-wear`: broadcaster of sound only, receiver of sound and video, resource consumer through the phone's hand-over or its own entry, never a resource; phone and watch send and receive the clipboard (section 22). The exchange server is FMS_W's to implement |

## What this repository owes it once implementations exist

- Enroll once per account entry and keep only its device token, in Keystore-backed storage; never store the
  account password.
- Pin the exchange server's certificate on first contact and refuse a changed one, as a host-key mismatch.
- Publish the embedded SFTP server's share as a `sftp-share` resource with a stable `resourceId`; reach an
  attached resource by its direct endpoints first and by a tunnel second.
- Never delete an attached resource because its producer went offline, unpublished or was revoked; deletion
  is the user's act.
- Keep the live-broadcast consumer rules on every exchange transport, and the watch's cap of 4 listeners across
  LAN, tunnel and relay together.
- Request no Drive scope beyond `drive.appdata` and no new Android permission.
- Run the Drive channel in `appDataFolder` under the name "via Google Drive" / "via GD": keep reading the shipped
  `cross_device_transfer` packets (manifest version 1) and write version 2 with `senderDeviceId`, `targetDeviceId` and
  `files` with size and SHA-256; upload above 5 MiB resumably from a stream; check every received file; delete a
  targeted packet after receipt; judge expiry on Drive's clock (section 24, items CL to CN and CR).
- Answer `REQUEST` `list` / `fetch` / `changes` for a resource published on Drive and refuse a protected root
  `pin-required`; send and take the clipboard as one untargeted `CLIPBOARD` packet under its own option; poll on the
  item CQ cadence with exponential back-off; check free space before an upload, `packetBytes` 5 GiB; the watch has
  no Drive channel of its own (items CO to CU). Delivered: `vectors/drive-packets-v1.json`, produced by
  `CrossDevicePacketCodecTest` (S4231, 2026-10-15).
- Frames: at most 16384 bytes until `welcome` / `enrolled`, up to 262144 after, a record at most 16 KiB, no
  paging; a relay chunk's length field, flag byte plus payload, is at most 1 MiB (items T and U).
- Every outsider `connect` is TLS only; there is no plain fallback (item V).
- Refuse a connection string with a higher `FMSEXCH` prefix before reading any key, with the canonical
  `format-unsupported` text in the app language and no update demand; the wire `version-unsupported` keeps its
  own words (1.3).
- A share's `access` reaches Drive only with the user's explicit opt-in, off by default and worded to name
  Google; a Drive record without `access` is listed and cannot be attached (item X).
- Read and write the `FMSEXCH1` connection string by its content - paste, `.fmsexch` file, QR - with the
  WHATWG form-urlencoded canonical bytes, and replay `vectors/connection-string-v1.json` and
  `vectors/fmsw-224/connection-string-v1.json`, the latter with the `i` cases of item AR (section 5.5).
- Hold several account entries, a `deviceId` each, identified by address and login; bind an attached resource
  to that identity and its `resourceId`; removing an entry revokes its own device (items AA, AB).
- Publish and broadcast through one outgoing entry; leaving it as a producer unpublishes and keeps the entry;
  declare every role at `enroll` (item AC).
- Play the exchange endpoints of any server a broadcast descriptor names, with no entry for it (item AF).
- Attach a `pin-v1` resource only through the PIN proof, keep the PIN only in the Keystore-backed store and never
  in a log, and list any other protection locked (items AG to AJ).
- Register by invitation - the `i` of an invitation string or a typed shared invitation password - and offer
  account deletion beside it; change the password only with the old one; on `signed-out` keep the entry and ask
  for the password again; sign out on this device by removing the entry; hide producer actions on a `guest`
  account (items AO to AV).
- Keep the server key hash beside the leaf pin and accept a leaf renewed with the same key with no prompt; hand
  both values to the watch; negotiate TLS 1.2 at least (items AX and AY).
- Write `hostKeyFingerprint` and `root` on every `sftp-share` record; keep one record per resource identity with every
  channel learned - direct, port forward, tunnel per entry, link, Drive - race them fastest first and say "no connection"
  only after all; merge duplicates only on proven identity (section 20, items BA and BB).
- Turning the share on publishes it on the LAN code, the outgoing entry and Drive at once (item BC).
- Give one folder or broadcast to a `guest` with `grant` and a login confined to the root; offer a public link with a
  password as an `FMSSFTP2` code without `pw`, checked by the embedded server (items BD and BE).
- Protect a broadcast handed outside with an exchange endpoint: `auth` in the descriptor, Digest at the phone's
  servers, relay `verifiers`, `credentials` to own devices (item BF).
- Keep the PIN with the resource's record and use it for scheduled operations; stop an unattended run on a refused
  kept PIN; read-only stays the user's choice when opening (items BG and BH).
- Send and receive the clipboard on the phone and the watch: declare the `clipboard` role, offer the actions only
  where `welcome.features` lists `clipboard` and the account is `member`; send one value - text, image or file -
  with `clipboard-put` and exactly `size` raw bytes to every member entry; get the value with the latest
  `storedAt` and check its size and SHA-256 before placing it; refuse several objects on the device (section 22,
  items BU to CA).
- Read the clipboard only in a window holding focus (a shortcut or tile opens one); place a text too large for
  the system clipboard as a `.txt` file; on the watch send through "Send to.." and open a received value in the
  player, never touching the system clipboard; save received files under a safe name without overwriting (item
  CB).
- Never connect on a connection string that arrived from outside the screen: fill the form, show the host:port of `x`
  before the password field, send the password to that endpoint only, and treat a string whose `fp` names another key as a
  changed certificate (item BN). Order two records of one id by the store's order and not by `updatedAt` (item
  BP); report a stream closed with no frame after the handshake as unanswered, and refuse a credential that is empty or
  unread before the wire (item BR).
- Bind an imported live channel to the entry that listed it and end it only by that entry's own directory; reach a server named
  by a descriptor with no entry for it (items BJ and BK); end running broadcasts first when leaving an entry (item BL).
- Declare the roles of item CE with the `product` values `fms-android` and `fms-wear`; tell a watch by `product`
  and never by `platform`; the watch declares only what its build plays and never `resource-producer` (items CE and CH).
- Carry no stream over the phone-to-watch link: the hand-over of an entry (address, trust decision, one-time
  pairing code, no password), the hand-over of a resource (identity and pass, owed) and the hand-off of a live
  broadcast are small items, and every byte of data goes over the watch's own Wi-Fi or LTE; trust and passwords
  stay on the phone (items CF and CG, `LIVE-BROADCAST` item P).
- Let the watch receive by cast, by the phone's hand-off, by address and from the account's list, sound and
  video, and broadcast `AUDIO_ONLY` only with the cap of 4; its "Send to.." sends its own files to external
  resources with no phone (item CH).
- Show the account's broadcasts when the list opens (`list`, then `subscribe`), grouped by device, several
  broadcasters at once, with no refresh action; without a server a link; browse the local network for no
  broadcast (item CI). "Cast" is the act of 7.8 and not the platform's casting to a television (item CJ).
- Say every term of item CW in the words of the table, in the language shown - exchange server, direct access (the
  sharing service), via Google Drive / via GD, broadcast, the clipboard and its four operations, the invitation
  kinds "own device or family" and "guest", a link with a password - and never a wire value as a word (items CV and CW).
- Name the Windows program "Fast Media Sorter & Sharing for Windows", leave the platform word out of a store
  title and keep it elsewhere, and never write "mobile" for a program or a device (item CX).
- Show a failed connection with the one table of item CZ: what happened, then what to try from the ladder of item
  CY; the code only in a details line and the log, one language at a time, on the watch the fixed line that sends
  to the phone for details, and no secret in the text (items CY to DA).
- Seal every content path through the exchange server (section 27): a P-256 key pair per account entry with
  `e2eKey` and `e2e-v1` declared at `enroll`, the private key only in Keystore-backed storage and never in a backup
  or a hand-over; trust each device key on first sight, the watch's from its hand-over answer, show `key-changed` for a
  changed one and seal only to devices this program lists (items DF and DG).
- Use the sealed box of item DH (HPKE Auth mode, DHKEM(P-256, HKDF-SHA256), HKDF-SHA256, AES-128-GCM; AES-256-GCM
  payload) after passing the RFC 9180 A.3 vectors; publish `sealedAccess` instead of `access` and take the SSH host key
  from the opened descriptor; seal grants to the guest's devices (items DI and DJ).
- Seal broadcasts: the record's box with `relayKey`, `tunnelPin` and `credentials`, the relay as key-ring and media
  records with a new epoch by a replacement upload when a recipient is dropped, TLS inside a broadcast tunnel on a
  per-broadcast pinned leaf, the keys in an outward descriptor; play a sealed relay through the loopback proxy, on the
  phone and the watch (items DK to DM).
- Send and get the clipboard as kind `sealed` in 64 KiB segments; never place a value that fails to open (item DN).
- Send no content through a server whose `features` lacks `sealed` (`server-no-e2e`) and use no clear content from a
  device without `e2eKey` (`e2e-missing`); leave the Drive path unsealed; show the honest text of item DE wherever the
  server or the Drive channel is described, and the four rows of item DR (items DO to DR). Delivered:
  `vectors/e2e-v1.json`, produced by `E2eVectorsGenerator` and replayed by `E2eVectorsTest` in the phone and
  watch modules (S4240, 2026-10-15).
- Section 27 is met in code on the phone and the watch (S4240, 2026-10-15): device keys and trust, sealed access,
  grants, clipboard, relay and broadcast tunnel, the outward descriptor's keys and the honest text. The joint run
  with a server that lists `sealed` (rung 2 and rung 4 of item DR's conformance paragraph) is S4244.
- Administer the server over the network as 1.4 `REMOTE-ADMIN` describes (`native-administration-stream`,
  `administration-operations`, `administration-reply-codes`): the administration client, session, repository, use
  case and screen of S4356 implement the client side of every carried operation, with the operator password typed
  each time and nothing of the session kept. It stays `partial` until the joint run against FMS_Win 245, the
  server that accepts the stream.

## Where it will live here

- Exchange client and wire codec: next to the S4094 tunnel code under `app_v2/.../data/remote/sftp/`.
- Broadcast producer and receiver: `app_v2/.../data/broadcast/` and `wear/.../data/broadcast/`.
- Drive records: `app_v2/.../data/cloud/helpers/GoogleDriveAppDataApi.kt`.

## Ticket

- S4116 - the owner's input, the contract outcome and the work this repository owes.
- S4129 - the Drive opt-in and the listing of a record without `access`.
- S4153 - the owner's decision on the 2026-10-10 proposals and the 0.18 fold, with the vector file.
- S4154, S4155, S4156, S4157, S4158 - the connection-string codec, its intake, account entries, the producer
  entry's join and leave, the watch through the phone's entry.
- S4160 - receiving a broadcast from any exchange server its descriptor names, and the broadcast's own share
  carrying its exchange endpoints.
- S4172 - the 0.19 read (section 17) and this product's registry row.
- S4200 - the bounds of an `enroll` (items AM and AN) and the unanswered-enroll result.
- S4201 - the PIN proof, its entry and its storage (section 17, items AG to AJ).
- S4214 - the owner's account decisions folded as 0.21 (items AO to AZ).
- S4223 - the phone's registration, password, sign-out and account deletion flows against 0.21.
- S4215 - the owner's resource and outward-access decisions folded as 0.22 (section 20, items BA to BH), with
  `ANYWHERE-ACCESS` 0.14 and `LIVE-BROADCAST` item M.
- S4224 - the phone's one resource record across every channel against 0.22.
- S4216 - the owner's clipboard decisions and the platform research folded as 0.24 (section 22, items BU to CD).
- S4226, S4227 - the clipboard on the phone (buttons and shortcuts) and on the watch ("Send to.." and the player).
- S4212 - the proposals left open beside the contract answered as 0.23 (items BI to BT), with FMS_W's server
  corpus adopted as `vectors/server-frames-v2.json` and `vectors/server-records-v1.json`.
- S4217 - the roles of every product, the watch's two modes, the account's broadcast list, cast and the hand-over to
  the watch folded as 0.25 (items CE to CK), with `LIVE-BROADCAST` items N to P.
- S4228, S4229, S4230 - the watch's resources through the phone, its broadcasts by address and from the list, and its
  "Send to.." with no phone, against 0.25.
- S4218 - the owner's Drive decisions folded as 0.26 (section 24, items CL to CU): the Drive channel carries files.
- S4231 - the phone's path via GD against 0.26, with the packet vector file.
- S4219 - the owner's terms, names and connection-message decisions folded as 0.27 (items CV to DA).
- S4236 - the check ladder and the shared texts in the phone and watch programs against 0.27.
- S4237 - the terms, the name and the icons in strings, README and listings against 0.27.
- S4239 - the owner's decision 18 folded as 0.29 (section 27, items DE to DR): content sealed end to end, the honest text.
- S4240 - sealing on the phone and the watch against 0.29, with `vectors/e2e-v1.json`.
- S4225 - the phone share's lifetime and the reason it went offline folded as 0.28 (items DB to DD): the share resumes when the app is shown, a platform limit is written as `offlineReason: time-limit` on the Drive device record, and the consumer shows the `share-paused` row.
- S4245 - the contract at 1.0: the status of every section is binding, the ladder of section 11 is evidence, `vectors/drive-packets-v1.json` and `vectors/e2e-v1.json` are named as delivered, and the `seal` mirror of item DK is item Q of `LIVE-BROADCAST`.
- S4276 - the contract at 1.1: `vectors/fmsw-224/connection-string-v1.json` is replayed by `ExchangeConnectionStringVectorsTest`; the transcripts of `vectors/fmsw-224/frames-v2.json` have no client replay yet.
- S4356 - the phone as a `REMOTE-ADMIN` client at 1.4: the administration stream client, the words of the ten
  reply codes and the screen; the joint on-device run against FMS_Win 245 is what remains of 1.4 here.

## Original resource PIN (section 17, items AG to AL)

The original resource PIN is separate from exchange enrollment, certificate pinning and the account
password. The phone consumer implements the `pin-v1` capability of item AJ:

- It declares `pin-v1` at `enroll` and remembers that the held token's enrollment did, so an entry enrolled
  before it is told to sign in again - the server withholds a protected resource's `access` otherwise.
- It keeps the `access` of a `pin-v1` record and lists every other scheme locked, never attached and never
  treated as unprotected; a protected resource is attached only through the proof.
- The PIN is asked from the user once per resource and proved at the producer by SSH `keyboard-interactive`
  (`Resource`, `Password`, `PIN`, `SHARE-SESSION` 1.2) on a session of its own per root. It is kept only after
  the producer accepted it, Keystore-encrypted per binding (entry identity and `resourceId`) in the preferences
  store that backup excludes, moved with an address change, and cleared with the resource or when it is no
  longer published (item AI). No log line, record or envelope carries it.
- The embedded SFTP server's roots are folders picked in its own settings and carry no access PIN, so this
  producer publishes no `protection` (item AG's truth rule, 14.5 item O). Wear's exchange client decodes no
  resource record, so it attaches nothing protected.

`vectors/protection-v1.json` is replayed by `ProtectionVectorsTest` and the Drive wrapper of a protected record
by `ResourceProtectionTest`. Item AL's joint negative-access run - a wrong PIN, a changed PIN, another root's
credentials, a device without `pin-v1` - has not run for this client pair, so the registry row stays
unverified for it.
