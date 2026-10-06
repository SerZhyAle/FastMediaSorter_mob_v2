<sub class="doc-stamp">26.10.05 22:01</sub>

# Pointer - `ANYWHERE-ACCESS`

| | |
| --- | --- |
| **Id** | `ANYWHERE-ACCESS` |
| **Version** | 0.10, draft - [PROPOSED] in full, nothing ships against it yet. Owner: FastMediaSorter Android |
| **Home** | `anywhere-access/README.md` in the shared contracts catalog |
| **Role here** | owner, and reference producer and consumer - the phone's embedded SFTP server (S3041) shares over the rendezvous/tunnel, and this app reads others' shares |

## What this repository owes it once implementations exist

- Keep `FMSSFTP1` decoding, encoding and behaviour unchanged forever; a v2-capable reader accepts
  both prefixes, and an anywhere-mode producer keeps a legacy pairing action beside the v2 code.
- `.fmscfg` stays schema 2: the tunnel path travels as a new `accessPaths[].kind` an older reader
  ignores; refuse-above-own-schema and the canonical vectors are untouched.
- The host-key fingerprint stays the identity anchor on every transport; `SHARE-SESSION` governs
  the live session over the tunnel exactly as over LAN; `LAN-DISCOVERY` is untouched.
- No new permission and no new OAuth scope: the Drive channel uses the existing `drive.appdata`
  scope and the app's private `appDataFolder` space.
- Registering outward only: the producer never requires an inbound port; reconnect discipline is
  `SHARE-SESSION` rules 6-10 applied to the registration.

## Where it will live here

- Producer: `app_v2/.../data/remote/sftp/server/` (S3041 server, tunnel registration to come) and
  `app_v2/.../service/SftpServerService.kt` (lifecycle).
- Consumer: `app_v2/.../data/remote/sftp/SftpEndpointResolver.kt` (endpoint groups, the tunnel as one
  more candidate with the same pin) and the `FMSSFTP1`/`.fmscfg` import paths under
  `app_v2/.../ui/companionimport/` and `app_v2/.../data/companion/`.
- Drive rendezvous: `app_v2/.../data/cloud/helpers/GoogleDriveAppDataApi.kt` and the S3040
  cross-device transfer queue as the store-shape precedent.

## Ticket

- S4094 - strategic spec, research and cross-repo work packages:
  `PLAN/S4094_sftp-server-access-from-anywhere/`.
