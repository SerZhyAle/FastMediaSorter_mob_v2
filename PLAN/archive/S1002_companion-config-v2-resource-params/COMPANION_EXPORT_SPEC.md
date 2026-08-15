# Companion Export Spec - `.fmscfg` schema v2 (resource params)

**Audience:** the companion/exporter program (Windows "Fast Media Sorter for Windows" / `FastMediaSorter_Lite`, Go worker `fms-share-worker.exe`).
**Owner ticket (Android side):** S1002. **Supersedes:** schema v1 (S0421).
**Status:** contract frozen on the Android side; companion side to implement.

This document is self-contained: implement it without reading the Android source. It defines the v2 exchange format, what each field does on import, the transport envelopes (file + QR/barcode), and the UI you must add so a user can set these params before export.

---

## 1. What changed vs v1

- v1 shared only **access** (host/port/user/password/fingerprint) and a bare list of shared roots (`virtualPath` + `label`). On import each root became an SFTP resource with fixed defaults.
- v2 lets each shared root **also carry the target resource's configuration**: type/profile, media types, scan conditions, destination flag, comment, PIN, slideshow interval.
- **`schemaVersion` goes from `1` to `2`.** Everything else about the v1 envelope is unchanged.
- **Backward/forward compatibility:**
  - A v1 file (`schemaVersion:1`) is still accepted by new Android apps unchanged.
  - A v2 file opened by an **old** Android app (that only knows v1) is politely rejected with an "update the app" message (the app rejects any `schemaVersion` greater than it supports). This is expected - ship the app update first / tell users to update.
  - Every v2 field is **optional**. Omit a field and the Android side applies the v1 default for it (see §4). Emit only what the user actually configured to keep the payload small (important for the QR path).

---

## 2. JSON schema v2

Top-level object (unchanged from v1 except `schemaVersion`):

| Field | Type | Req | Notes |
| --- | --- | --- | --- |
| `schemaVersion` | int | yes | **Must be `2`** for a v2 file. |
| `resourceName` | string | no | Human name of the PC/share (used in the import confirmation dialog and as the default comment prefix). |
| `protocol` | string | yes | Must be `"sftp"` (only protocol supported). |
| `accessPaths` | array | yes | 1+ entries; LAN first, then port-forward. See below. |
| `username` | string | yes | SFTP user. |
| `password` | string | yes* | May be empty `""` (passwordless share - recipient types it at import). |
| `hostKeyFingerprintSha256` | string | yes* | May be empty `""` (no TOFU pin). Non-empty must be canonical `SHA256:base64`. |
| `roots` | array | yes | 1+ shared roots; **each root == one imported resource** (see §3). |
| `createdAt` | string | no | ISO-8601 UTC, e.g. `2026-07-11T12:00:00Z`. Informational. |

`accessPaths[]` entry:

| Field | Type | Req | Notes |
| --- | --- | --- | --- |
| `kind` | string | yes | `"lan"` or `"portforward"`. |
| `host` | string | yes | IP or hostname. |
| `port` | int | yes | 1..65535. |

\* `password` and `hostKeyFingerprintSha256` keys must be present; their **value** may be an empty string.

---

## 3. `roots[]` entry - v2 resource params

`virtualPath` + `label` are the v1 fields. Every field below is **new in v2 and optional**.

| Field | Type | Default on import (if omitted) | Meaning |
| --- | --- | --- | --- |
| `virtualPath` | string | (required) | Server path of the shared root; must start with `/`. |
| `label` | string | `virtualPath` without leading `/` | **Resource name** shown in the app. This is the "наименование ресурса". |
| `profile` | string enum | `none` | **Resource type/preset** (the "аудиотека" case). One of the tokens in §3.1. Drives the media-type set + a couple of flags. |
| `mediaTypes` | string[] | derived from `profile`, else IMAGE+VIDEO+AUDIO+GIF | Explicit media-type set; **overrides** the profile-derived set. Tokens in §3.2. |
| `scanSubdirectories` | bool | `true` | Scan subfolders for media. |
| `showSubfoldersAsItems` | bool | `false` | Show subfolders as tappable items (only meaningful when `scanSubdirectories` is true). |
| `showHiddenFiles` | bool | `false` | Show dot-files/folders. |
| `allFiles` | bool | `false` | Show every file regardless of type (overrides `mediaTypes`). |
| `isDestination` | bool | `false` | Register as a copy/move destination. Implies the resource is **writable** (see §5). |
| `destinationColor` | int (ARGB) | app-assigned | Destination chip color as a signed 32-bit ARGB int (e.g. `-14575885` == `0xFF2196F3`). See §5 - the app reassigns destination colors from a palette, so this is best-effort. |
| `comment` | string | `"Companion: <resourceName>"` | Free-text note on the resource. Overrides the default prefix comment when non-empty. |
| `accessPin` | string | none (no PIN) | PIN that locks the resource in the app. **Travels in the file** - see §6. |
| `slideshowInterval` | int (seconds) | `10` | Seconds between images in slideshow. Must be > 0 to take effect. |

### 3.1 `profile` tokens

Exactly one of: `none`, `audio_library`, `video_library`, `photo_storage`, `documents`, `all_files`.

Each profile implies a media-type preset (the app applies it if you do not send an explicit `mediaTypes`):

| Token | Implied media types | Extra |
| --- | --- | --- |
| `none` | (app default) | - |
| `audio_library` | audio | also enables "remember file list" |
| `video_library` | video, audio | - |
| `photo_storage` | image, gif | - |
| `documents` | text, pdf, epub, office | - |
| `all_files` | (all, via `allFiles`) | sets `allFiles=true` |

Unknown/misspelled token -> the app ignores it and falls back to the default (it never rejects the file for a bad token). Still, send only the tokens above.

### 3.2 `mediaTypes` tokens

Any subset of: `image`, `video`, `audio`, `gif`, `text`, `pdf`, `epub`, `office`.

Unknown tokens in the array are ignored (not fatal). Send `mediaTypes` when you want an exact set independent of the profile; otherwise omit it and let `profile` decide.

---

## 4. Import semantics (what the Android side does)

For each `roots[]` entry the app builds one SFTP resource:

- **Name** = `label` (or the path tail if `label` is blank).
- **Media types** = `mediaTypes` if present; else the `profile` preset; else IMAGE+VIDEO+AUDIO+GIF (v1 default).
- **allFiles** = `allFiles` if present; else the profile's implied value; else false.
- **scanSubdirectories** = value if present; else `true`.
- **showSubfoldersAsItems / showHiddenFiles** = value if present; else `false`.
- **comment** = non-empty `comment`; else `"Companion: <resourceName>"`.
- **accessPin** = non-empty `accessPin`; else none.
- **slideshowInterval** = `slideshowInterval` if > 0; else 10.
- **isDestination** = value if present; else false. If true, the resource is created **writable** (read-only otherwise). Destination slot/order/color are assigned by the app (max 10 destinations; extra ones are added as non-destinations).
- **profile / rememberFileList** = from the resolved `profile`.
- Credentials, host-key pin, and the SFTP path are built from the top-level access fields exactly as in v1.

A v1 file (no v2 fields) reproduces the exact old behavior: ALL media types, scan subdirectories on, read-only, `"Companion: <name>"` comment.

---

## 5. Destination + read-only interaction

- Companion SFTP shares are typically served **read-only**. If you set `isDestination:true`, the app makes the resource writable - so the user can copy/move files **into** it. Only mark a root as a destination when the SFTP server actually accepts writes to that path; otherwise the destination will fail at write time.
- `destinationColor` is best-effort: when the resource lands in a real destination slot, the app assigns its chip color from a fixed palette by slot order, which overrides the value you send. Send it for completeness, but do not rely on the exact color surviving.

---

## 6. Security - password and PIN travel in the file

- Both `password` and `accessPin` are stored **in plaintext** inside the `.fmscfg` file and the QR payload. Anyone who receives the file gets them.
- Before export, warn the user (as v1 already does for the password). Recommended: offer an **"exclude password"** toggle (already in v1) and, when the resource has a PIN, an **"exclude PIN"** toggle too - emit `accessPin:""`/omit it when excluded.
- Revoking shared access = change the SFTP password on the server (same as v1).
- The host-key fingerprint in the file is a **security bonus** (it gives the recipient TOFU pinning automatically) - keep sending it when you have it.

---

## 7. Transport envelopes

Two ways to deliver the same JSON; **both are unchanged from v1** and both are read by the same Android parser, so v2 works over both automatically:

1. **File `.fmscfg`** - plain UTF-8 JSON (starts with `{`). Primary transport for a full config. Delivered via Telegram / email attachment / share sheet.
2. **QR / barcode** - the string `FMSCFG1:` followed by base64(gzip(json)).
   - The `FMSCFG1` marker is the **envelope** version and stays `FMSCFG1` even for schema v2 (it is not the payload `schemaVersion`). Do **not** change it.
   - **Capacity:** a QR code holds ~2-3 KB of binary data. A v2 config with many roots and full params can exceed that. Requirements:
     - Emit only the fields the user set (omit defaults) to keep the payload small.
     - If the gzipped payload still will not fit a QR, **fall back to the file** and tell the user "too many settings for a QR code - share the file instead". Do not silently truncate.

---

## 8. Canonical v2 vector (freeze byte-for-byte)

Freeze this exact JSON on the companion side (in `docs/CONFIG_FORMAT.md` and the schema test, e.g. `internal/config/schema_test.go`), matching the Android fixture `app_v2/src/test/resources/companion/canonical_vector_v2.json`. A `schemaVersion` bump breaks the other side unless both adopt the identical vector.

```json
{"schemaVersion":2,"resourceName":"Home PC","protocol":"sftp","accessPaths":[{"kind":"lan","host":"192.168.1.23","port":2022},{"kind":"portforward","host":"203.0.113.7","port":2022}],"username":"fms","password":"k7PmQ2wXr9TzS4vGnHb3JdLe","hostKeyFingerprintSha256":"SHA256:8f6TQvCbXjDMOyu4A9JzKcWlEHmR5pNsGgVaU2wYqhk","roots":[{"virtualPath":"/Music","label":"Home Music","profile":"audio_library","mediaTypes":["audio"],"scanSubdirectories":true,"showSubfoldersAsItems":false,"showHiddenFiles":false,"allFiles":false,"isDestination":false,"comment":"Vinyl rips","accessPin":"1234","slideshowInterval":15},{"virtualPath":"/Inbox","label":"Drop Box","isDestination":true,"destinationColor":-14575885,"comment":"Move target"}],"createdAt":"2026-07-11T12:00:00Z"}
```

---

## 9. Companion UI requirements (before export)

Add per-shared-root controls so the user sets these params before generating the file/QR. Priority order (implement top-down):

1. **Resource name** (`label`) - text field. **(highest priority)**
2. **Type / profile** (`profile`) - dropdown with the 6 tokens; label the default one clearly and include "Audio library" (`audio_library`). **(highest priority)**
3. **Media types** (`mediaTypes`) - optional multi-select, shown as an "advanced/custom" override of the profile. Leave empty to use the profile.
4. **Scan conditions** - checkboxes: scan subdirectories, show subfolders as items, show hidden files, all files.
5. **Destination** (`isDestination`) - checkbox, with a note "the shared path must accept writes". Optional color.
6. **Comment** (`comment`) - text field.
7. **PIN** (`accessPin`) - text field + the "exclude PIN on export" safeguard (§6).
8. **Slideshow interval** (`slideshowInterval`) - number (seconds).

Emit a field only when the user changed it from the app default (keeps QR payloads small). Keep the v1 access UI (host/port/user/password/fingerprint) as-is.
