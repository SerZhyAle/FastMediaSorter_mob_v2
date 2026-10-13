<sub class="doc-stamp">26.10.13 13:16</sub>

# Pointer - `DEVICE-EXCHANGE`

| | |
| --- | --- |
| **Id** | `DEVICE-EXCHANGE` |
| **Version** | 0.19, draft - original resource PIN amendment accepted 2026-10-12. Owner: FastMediaSorter Android |
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

## Original resource PIN boundary (section 17, items AG to AL)

The original resource PIN is separate from exchange enrollment, certificate pinning and the account
password. The embedded SFTP server's roots are folders picked in its own settings and carry no access PIN,
so this producer publishes no `protection` (item AG's truth rule, 14.5 item O). The phone consumer takes
item AJ's explicit unsupported path: any protection marker, including malformed or unknown protection,
remains locked. The rendezvous codec removes all access material, preserves the protection fact through
Drive wrapper schema 2, and the directory excludes it from legacy attachment import. The phone does not
advertise `pin-v1` or claim SSH PIN authorization; the per-root proof waits on the amendment item AK owes
to `SHARE-SESSION` and `WORKER-IPC`. Wear's exchange client decodes no resource record, so it attaches
nothing protected.

ResourceProtectionTest exercises known, future and malformed protection plus protected Drive round trips.
`vectors/protection-v1.json` is not replayed here yet, and item AL's joint negative-access run has not run
for this client pair.
