<sub class="doc-stamp">26.10.30 11:36</sub>

# Play Console - Local Network declaration (S4502)

Source of truth for the **Local Network** permission declaration in Play Console
(`Policy and programs -> App content`, announced by Google on 2026-10-29 for apps targeting API 37).

The form lives only in the web console and has no API, so this file is the only reviewable copy of what
is declared. Edit **here** first, then paste into the console. Do not compose the text in the console.

The walk-through is `PLAY_CONSOLE_CHECKLIST.md`, next to the Location declaration. The status is recorded in
`docs/PLAY_PUBLISHING_STATE.md`, in the block marked `s4502:transcribed:local-network-declaration`.

---

## Permission

- Permission: `android.permission.ACCESS_LOCAL_NETWORK`, declared with `android:minSdkVersion="37"`.
- Flavor: `standard` (the Play build). Package: `com.sza.fastmediasorter`.
- `lite` does not declare the permission. `noLegal` and `foss` are distributed outside Play.
- Enforcement: apps targeting 37 from 2027-08-25. The app targets 37 since S4502.

---

## Use case (paste)

FastMediaSorter is a media manager that opens, sorts and plays the user's own photos, videos, music and
documents stored on their home network. The user adds a network source by typing its address or by
choosing a device the app found on the network: Windows and Samba shares (SMB), SFTP and FTP servers, and
the FastMediaSorter companion app on the user's computer, which announces itself over mDNS. The app keeps a
connection to those hosts while the user browses, copies, moves, deletes and plays files on them.

The app also accepts connections from the local network on the user's request: a built-in SFTP server lets
the user's other devices reach the phone's chosen folders, a live broadcast lets devices on the same Wi-Fi
watch the phone's camera or a file, and casting hands a local file to a Cast receiver through a short-lived
local stream.

A system device picker cannot serve these use cases. A picker returns one discovered service for one
session, while the app connects to addresses the user typed - including servers that do not announce
themselves - keeps them as saved sources and reconnects to them later. File operations, playback and
thumbnails open many connections to the same hosts. The built-in server and the broadcast must accept
incoming connections from devices the phone did not choose, which no picker covers.

Every network feature starts from an explicit user action, and the app asks for the permission at that
moment. Without the grant, local sources show a clear message and the rest of the app keeps working.

---

## What a reviewer can check

- Adding an SMB, SFTP or FTP source by typed address: `Add resource` screen.
- Companion discovery over mDNS: the device list on the same screen.
- Built-in SFTP server: the SFTP server panel in `Settings -> General`.
- Live broadcast: the broadcast screen opened from the main menu.

---

## Rollback

If Play rejects the declaration, the store build can keep `targetSdk = 36` until the text is corrected;
the permission itself stays declared only for API 37 devices.
