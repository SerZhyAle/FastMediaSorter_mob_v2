<sub class="doc-stamp">26.10.07 12:00</sub>

# Pointer - `DEVICE-EXCHANGE`

| | |
| --- | --- |
| **Id** | `DEVICE-EXCHANGE` |
| **Version** | 0.16, draft - [PROPOSED] in full, nothing ships against it yet. Owner: FastMediaSorter Android |
| **Home** | `device-exchange/README.md` in the shared contracts catalog |
| **Role here** | owner; phone - resource producer and consumer, broadcaster, receiver, enrolls the watch; watch - audio broadcaster and receiver. The exchange server is FMS_W's to implement |

## What this repository owes it once implementations exist

- Enroll once per installation and keep only the device token, in Keystore-backed storage; never store the
  account password.
- Pin the exchange server's certificate on first contact and refuse a changed one, as a host-key mismatch.
- Publish the embedded SFTP server's share as a `sftp-share` resource with a stable `resourceId`; reach an
  attached resource by its direct endpoints first and by a tunnel second.
- Never delete an attached resource because its producer went offline, unpublished or was revoked; deletion
  is the user's act.
- Keep the live-broadcast consumer rules on every exchange transport, and the watch's cap of 4 listeners across
  LAN, tunnel and relay together.
- Request no Drive scope beyond `drive.appdata` and no new Android permission.

## Where it will live here

- Exchange client and wire codec: next to the S4094 tunnel code under `app_v2/.../data/remote/sftp/`.
- Broadcast producer and receiver: `app_v2/.../data/broadcast/` and `wear/.../data/broadcast/`.
- Drive records: `app_v2/.../data/cloud/helpers/GoogleDriveAppDataApi.kt`.

## Ticket

- S4116 - the owner's input, the contract outcome and the work this repository owes.
