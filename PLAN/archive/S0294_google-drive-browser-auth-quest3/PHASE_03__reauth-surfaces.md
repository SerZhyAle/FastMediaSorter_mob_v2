# Phase 03 - Reauth Surfaces

**Strategic spec:** [../S0294_google-drive-browser-auth-quest3.md](../S0294_google-drive-browser-auth-quest3.md)
**Tactical index:** [INDEX.md](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-05-24
**Completed:** 2026-05-24

---

## Objective

Reuse the same Google Drive routing for Browse / Player re-auth flows and add explicit user-facing fallback copy for browser launch failures.

---

## Prerequisites

- [x] Phase 02 is ✅ Done.
- [x] Communication policy applies to any new or changed strings.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseCloudAuthManager.kt` | Modified | ≤ 220 |
| `app_v2/src/main/res/values/strings_s0294.xml` | New | ≤ 60 |
| `app_v2/src/main/res/values-ru/strings_s0294.xml` | New | ≤ 60 |
| `app_v2/src/main/res/values-uk/strings_s0294.xml` | New | ≤ 60 |

---

## Steps

### Step 03.1 - Route Browse / Player Google re-auth through the shared coordinator

**Status:** `[x] done`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseCloudAuthManager.kt`
**Depends on:** Phase 02

**Prompt for developer:**

- Remove the direct `identityRepository.signInPrimary` dependency from Browse / Player Google re-auth.
- Reuse the shared Google Drive interactive coordinator and browser pending-result consumption on `onResume`.

**Verification:**

- `grep: BrowseCloudAuthManager no longer calls identityRepository.signInPrimary directly for Google Drive`
- `grep: BrowseCloudAuthManager handles browser auth completion on onResume`

**Step Log:**

- Replaced the direct Browse / Player `identityRepository.signInPrimary` path with the shared `GoogleDriveInteractiveSignInCoordinator`.
- Added browser-result consumption on `onResume` so external sign-in round-trips complete through the existing callbacks.

### Step 03.2 - Add explicit browser fallback copy in EN/RU/UK

**Status:** `[x] done`

**Files:** `app_v2/src/main/res/values/strings_s0294.xml`, `app_v2/src/main/res/values-ru/strings_s0294.xml`, `app_v2/src/main/res/values-uk/strings_s0294.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

- Add only the user-visible strings needed for: no supported browser available, browser sign-in could not be started, and Google Drive browser auth failed after redirect.
- Keep the copy compliant with `docs/COMMUNICATION_POLICY.md` §2 and §6.

**Verification:**

- `grep: strings_s0294.xml exists in values + values-ru + values-uk with the same keys`
- `predicate: Strings pass COMMUNICATION_POLICY §6 checklist`

**Step Log:**

- Added EN/RU/UK browser fallback strings under the `s0294_` prefix and re-ran `scripts/check_strings_localized.ps1 -Module app_v2 -KeyPrefix s0294` -> exit 0.

### Step 03.3 - Map browser auth failures to the new copy

**Status:** `[x] done`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseCloudAuthManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceConnectionManager.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

- Replace generic browser-path failures and no-browser cases with the new concrete messages.
- Keep success / cancel behavior unchanged and avoid raw exception text in the primary user-facing message.

**Verification:**

- `grep: AddResourceConnectionManager and BrowseCloudAuthManager reference the S0294 browser fallback string keys`
- `grep: no user-visible Google browser auth message uses raw exception text as the primary copy`

**Step Log:**

- `BrowseCloudAuthManager` now surfaces the concrete S0294 browser fallback copy instead of generic Google auth failure text.
- `AddResourceConnectionManager` uses the same S0294 browser-auth failure message as a safe fallback when the provider message arrives blank.
- Validation: `./gradlew.bat :app_v2:compileStandardDebugKotlin --no-daemon` -> exit 0.

---

## Phase Done Criteria

- [x] Browse / Player Google re-auth uses the same capability-based router as Add Resource.
- [x] Browser fallback strings exist in EN/RU/UK with identical key coverage.
- [x] No-browser and launch-failure cases produce human-readable next steps instead of `UnknownError`.

---

## Change Log

- 2026-05-24 - Phase created.
- 2026-05-24 - Phase completed and compile-validated.