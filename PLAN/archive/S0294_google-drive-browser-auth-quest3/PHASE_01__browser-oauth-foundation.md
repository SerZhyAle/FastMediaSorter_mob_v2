# Phase 01 - Browser OAuth Foundation

**Strategic spec:** [../S0294_google-drive-browser-auth-quest3.md](../S0294_google-drive-browser-auth-quest3.md)
**Tactical index:** [INDEX.md](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 2 / 2
**Started:** 2026-05-24
**Completed:** 2026-05-24

---

## Objective

Introduce the Google Drive browser OAuth substrate and redirect completion path without changing the Add Resource UI contract yet.

---

## Prerequisites

- [x] Pre-Implementation Blockers in [INDEX.md](INDEX.md) are resolved.
- [x] Working tree is on a DEBUG branch.
- [x] Redirect URI contract is fixed to `com.sza.fastmediasorter:/oauth2redirect`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/build.gradle.kts` | Modified | ≤ 40 |
| `app_v2/src/main/AndroidManifest.xml` | Modified | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/GoogleDriveBrowserAuthManager.kt` | New | ≤ 350 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cloudauth/GoogleDriveAuthCompletionActivity.kt` | New | ≤ 180 |

---

## Steps

### Step 01.1 - Add AppAuth dependency and redirect surface

**Status:** `[x] done`

**Files:** `app_v2/build.gradle.kts`, `app_v2/src/main/AndroidManifest.xml`
**Depends on:** - start of phase

**Prompt for developer:**

- Add `net.openid:appauth:0.11.x` to `app_v2/build.gradle.kts`.
- Register the Google Drive browser OAuth completion activity and the `com.sza.fastmediasorter:/oauth2redirect` intent-filter in `app_v2/src/main/AndroidManifest.xml`.
- Preserve existing Dropbox and MSAL auth activities unchanged.

**Verification:**

- `grep: app_v2/build.gradle.kts contains net.openid:appauth`
- `grep: app_v2/src/main/AndroidManifest.xml contains com.sza.fastmediasorter:/oauth2redirect callback activity`

**Step Log:**

- Added `net.openid:appauth:0.11.1` to `app_v2/build.gradle.kts` and registered the redirect receiver plus `GoogleDriveAuthCompletionActivity` in `AndroidManifest.xml`.
- Validation: `./gradlew.bat :app_v2:compileStandardDebugKotlin --no-daemon` -> exit 0.

### Step 01.2 - Implement browser auth manager and completion activity

**Status:** `[x] done`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/GoogleDriveBrowserAuthManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/cloudauth/GoogleDriveAuthCompletionActivity.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

- Create a singleton browser auth manager that launches AppAuth with Google Drive scopes, offline access, and the fixed redirect URI.
- Exchange the auth code for tokens, fetch a stable account email, and persist the credential blob via encrypted storage.
- Publish one pending interactive result that Add Resource / Browse re-auth flows can consume on `onResume`.
- Do not route Google auth through WebView.

**Verification:**

- `grep: GoogleDriveBrowserAuthManager defines startInteractiveSignIn + consumePendingInteractiveResult`
- `grep: GoogleDriveAuthCompletionActivity calls into GoogleDriveBrowserAuthManager to finalize the redirect`
- `grep: no WebView reference exists in the new Google Drive browser auth files`

**Step Log:**

- Added `GoogleDriveBrowserAuthManager` with AppAuth round-trip, encrypted credential persistence, and pending-result handoff.
- Added `GoogleDriveAuthCompletionActivity` to finalize the browser redirect without a WebView path.
- Validation: `./gradlew.bat :app_v2:compileStandardDebugKotlin --no-daemon` -> exit 0.

---

## Phase Done Criteria

- [x] AppAuth dependency and redirect activity are present.
- [x] Browser auth manager can persist a credential blob and a pending interactive result without touching the identity domain.
- [x] No Google auth URL is routed through WebView in this phase.

---

## Change Log

- 2026-05-24 - Phase created.
- 2026-05-24 - Phase completed and compile-validated.