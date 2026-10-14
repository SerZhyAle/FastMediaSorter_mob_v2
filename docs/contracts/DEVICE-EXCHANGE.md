<sub class="doc-stamp">26.10.14 01:28</sub>

# Pointer - `DEVICE-EXCHANGE`

| | |
| --- | --- |
| **Id** | `DEVICE-EXCHANGE` |
| **Version** | 0.20, draft - receiver declaration bounds and the unanswered enroll, 2026-10-14 (section 18). Owner: FastMediaSorter Android |
| **Home** | `device-exchange/README.md` in the shared contracts catalog |
| **Role here** | owner; phone - resource producer and consumer, broadcaster, receiver, enrolls the watch; watch - audio broadcaster and receiver. The exchange server is FMS_W's to implement |

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
- Frames: at most 16384 bytes until `welcome` / `enrolled`, up to 262144 after, a record at most 16 KiB, no
  paging; a relay chunk's length field, flag byte plus payload, is at most 1 MiB (section 15, items T and U).
- Every outsider `connect` is TLS only; there is no plain fallback (item V).
- A share's `access` reaches Drive only with the user's explicit opt-in, off by default and worded to name
  Google; a Drive record without `access` is listed and cannot be attached (item X).
- Read and write the `FMSEXCH1` connection string by its content - paste, `.fmsexch` file, QR - with the
  WHATWG form-urlencoded canonical bytes, and replay `vectors/connection-string-v1.json` (section 5.5).
- Hold several account entries, a `deviceId` each, identified by address and login; bind an attached resource
  to that identity and its `resourceId`; removing an entry revokes its own device (section 16, items AA, AB).
- Publish and broadcast through one outgoing entry; leaving it as a producer unpublishes and keeps the entry;
  declare every role at `enroll` (item AC).
- Play the exchange endpoints of any server a broadcast descriptor names, with no entry for it (item AF).
- Attach a `pin-v1` resource only through the PIN proof, keep the PIN only in the Keystore-backed store and never
  in a log, and list any other protection locked (items AG to AJ).

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
- S4200 - the bounds of an `enroll` (section 18, items AM and AN) and the unanswered-enroll result.
- S4201 - the PIN proof, its entry and its storage (section 17, items AG to AJ).

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
