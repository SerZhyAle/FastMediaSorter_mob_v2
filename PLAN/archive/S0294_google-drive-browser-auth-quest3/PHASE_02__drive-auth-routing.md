# Phase 02 - Drive Auth Routing

**Strategic spec:** [../S0294_google-drive-browser-auth-quest3.md](../S0294_google-drive-browser-auth-quest3.md)
**Tactical index:** [INDEX.md](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 3 / 3
**Started:** 2026-05-24
**Completed:** 2026-05-24

---

## Objective

Wire Google Drive interactive sign-in, credential restore, and account registration to the new browser-backed auth path while preserving Credential Manager on GMS-capable devices.

---

## Prerequisites

- [x] Phase 01 is ✅ Done.
- [x] Strategic scope remains limited to Google Drive resource flows.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/GoogleDriveInteractiveSignInCoordinator.kt` | New | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/GoogleDriveAuthPlugin.kt` | Modified | ≤ 160 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/GoogleDriveAuthCoordinator.kt` | Modified | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/GoogleDriveRestClient.kt` | Modified | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceConnectionManager.kt` | Modified | ≤ 120 |

---

## Steps

### Step 02.1 - Add a capability-based interactive sign-in router

**Status:** `[x] done`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/GoogleDriveInteractiveSignInCoordinator.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/GoogleDriveAuthPlugin.kt`
**Depends on:** Phase 01

**Prompt for developer:**

- Introduce a coordinator that chooses the Google Drive interactive path by capability: Credential Manager when GMS is available, browser OAuth when Credential Manager is unavailable on the current device.
- Keep the plugin result contract unchanged: emit exactly one `Success`, `Cancelled`, or `Error` per attempt.

**Verification:**

- `grep: GoogleDriveInteractiveSignInCoordinator references GmsAvailabilityChecker + GoogleDriveBrowserAuthManager`
- `grep: GoogleDriveAuthPlugin delegates interactive routing to GoogleDriveInteractiveSignInCoordinator`

**Step Log:**

- Added `GoogleDriveInteractiveSignInCoordinator` and switched `GoogleDriveAuthPlugin` to capability-based routing with deferred browser result consumption on `onResume`.
- Validation: `./gradlew.bat :app_v2:compileStandardDebugKotlin --no-daemon` -> exit 0.

### Step 02.2 - Teach Drive token plumbing to use browser-backed credentials

**Status:** `[x] done`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/GoogleDriveAuthCoordinator.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/GoogleDriveRestClient.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

- Extend the Google Drive auth coordinator so it can restore, refresh, and expose Google Drive access tokens from the browser credential blob when the identity domain is unavailable or unbound.
- Preserve the existing identity-domain path for GMS-capable devices.
- Register browser-authenticated Google accounts in the common `NetworkCredentialsRepository` account model so Add Resource account picking continues to work.

**Verification:**

- `grep: GoogleDriveAuthCoordinator can expose accountEmail and access token from browser credentials`
- `grep: GoogleDriveRestClient restore/authenticate path no longer assumes identityRepository-only credentials`
- `grep: browser auth success writes or reuses NetworkCredentialsEntity for CloudProvider.GOOGLE_DRIVE`

**Step Log:**

- Extended `GoogleDriveAuthCoordinator` and `GoogleDriveRestClient` to restore, refresh, and expose browser-backed Drive credentials alongside the existing identity-domain path.
- Static check: browser auth persistence still registers Google Drive accounts through `GoogleDriveBrowserAuthManager.registerAccountInDatabase`.
- Validation: `./gradlew.bat :app_v2:compileStandardDebugKotlin --no-daemon` -> exit 0.

### Step 02.3 - Reuse browser-backed account state in Add Resource

**Status:** `[x] done`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceConnectionManager.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

- Update Google Drive status and existing-account branching in Add Resource so stored browser-backed credentials are treated as a connected Google Drive account.
- Preserve the current folder-picker navigation contract and existing multi-account picker UI.

**Verification:**

- `grep: AddResourceConnectionManager no longer relies on identityRepository alone for Google Drive connected state`
- `grep: Google Drive account picker still routes existing accounts to GoogleDriveFolderPickerActivity`

**Step Log:**

- Updated Add Resource Google Drive connected-state checks to treat stored browser-backed credentials as an existing account without changing folder-picker navigation.
- Validation: `./gradlew.bat :app_v2:compileStandardDebugKotlin --no-daemon` -> exit 0.

---

## Phase Done Criteria

- [x] Interactive sign-in is capability-based: GMS -> Credential Manager, no GMS -> browser OAuth.
- [x] Stored browser credentials can restore Drive access and expose a stable account email.
- [x] Add Resource shows the browser-authenticated Google account in the same account picker flow.

---

## Change Log

- 2026-05-24 - Phase created.
- 2026-05-24 - Phase completed and compile-validated.