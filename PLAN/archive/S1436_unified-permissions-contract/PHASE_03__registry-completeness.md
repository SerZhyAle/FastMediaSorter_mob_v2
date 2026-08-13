# Phase 03 - Registry completeness

**Strategic spec:** [`../S1436_unified-permissions-contract.md`](../S1436_unified-permissions-contract.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04, Phase 05
**Steps done:** 8 / 8
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Make the registry list everything this build can ask the user about and nothing it cannot: add the three missing user-facing permissions, correct the two wrong conditions, move the onboarding-versus-settings difference into the registry, and give both screens one grouping implementation.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved - items 4, 5 and 6 carry the owner rulings this phase implements.
- [ ] Working tree is clean or on a feature branch.

---

## UI placement decision (recorded)

Strategic §3.3 "UI placement contract" carries the owner ruling verbatim, dated 2026-08-06: no new screen appears; the missing permissions become rows of the existing permissions screen and onboarding page, in the same groups as the rest; the gesture toggle stays a feature switch and keeps its own grant route with the row added above it; install-from-file gets a row only in the build without legal restrictions; capture consent gets an informational row with no grant button; permissions granted silently at install get no row. Strategic §12 records the same four decisions as the quiz answers they came from. No placement decision is left to implementation.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImpl.kt` | Modified | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/PermissionEntry.kt` | Modified | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/CheckPermissionStatusUseCase.kt` | Modified | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionGrantIntentFactory.kt` | Modified | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BuildPermissionRowsUseCase.kt` | New | ≤ 140 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PermissionsManagementFragment.kt` | Modified | ≤ 240 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/helpers/WelcomePermissionsManager.kt` | Modified | ≤ 280 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImplTest.kt` | Modified | ≤ 200 |
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/PermissionRow.kt` | New (step 03.6) | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PermissionRowAdapter.kt` | Modified (step 03.6) | ≤ 200 |
| `app_v2/src/main/res/layout/item_permission_entry.xml` | Modified (boundary audit) | n/a |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern). No file in this phase is over 500 LOC.
>
> **Layout note:** `item_permission_entry.xml` was edited after all - the boundary audit found its one-line description cap cutting off the only content the capture-consent row has. It still has no `layout-land` variant, so landscape parity does not apply.

---

## Steps

### Step 03.1 - Add the overlay permission as a row

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImpl.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/CheckPermissionStatusUseCase.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionGrantIntentFactory.kt`, `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a `system_alert_window` entry: manifest name `Manifest.permission.SYSTEM_ALERT_WINDOW`, group `SYSTEM`, `optional = true`, `grantKind = SYSTEM_SCREEN`, `buildGates = setOf("DECLARES_OVERLAY_PERMISSION")`, `minSdk = 23`.
>
> Resolve its status in `CheckPermissionStatusUseCase` with `Settings.canDrawOverlays(appContext)`, mapping to `GRANTED` or `DENIED`. Add its `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` arm with a `package:` data URI to `PermissionGrantIntentFactory`.
>
> Add the title and description keys across EN/RU/UK in one `set-android-string.ps1 -Action add` call, checking the wording against `docs/COMMUNICATION_POLICY.md` §2 and §6. Leave the gesture toggle in `WelcomeGesturesManager` and `OperationsGesturesManager` untouched.

**Why:**

Strategic §6 item 4 rules that the toggle stays the feature switch and keeps its own grant route while the list row is added above it, because the toggle stores `gestureOverlayEnabled` - "permission granted" and "gesture strip shown" are two different states and the row expresses only the first.

**Verification:**

- `Grep` - `system_alert_window` matches in `PermissionRegistryRepositoryImpl.kt`.
- `Grep` - `canDrawOverlays` matches in `CheckPermissionStatusUseCase.kt`.
- `Grep` - `ACTION_MANAGE_OVERLAY_PERMISSION` matches in `PermissionGrantIntentFactory.kt`.
- `Grep` - `gestureOverlayEnabled` still matches in `WelcomeGesturesManager.kt` and `OperationsGesturesManager.kt`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "perm_"` exits 0.
- Strings pass `docs/COMMUNICATION_POLICY.md` §6 checklist.

**Status:** `[x]` done

---

### Step 03.2 - Add install-from-file as a row in the build that declares it

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImpl.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/CheckPermissionStatusUseCase.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionGrantIntentFactory.kt`, `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add a `request_install_packages` entry: manifest name `Manifest.permission.REQUEST_INSTALL_PACKAGES`, group `SYSTEM`, `optional = true`, `grantKind = SYSTEM_SCREEN`, `buildGates = setOf("IS_NO_LEGAL_FLAVOR")`, `minSdk = 26`.
>
> Resolve its status with `PackageManager.canRequestPackageInstalls()` through the `*Compat` helper in `util/PackageManagerCompat.kt` if one applies (Rule 21). Add its `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` arm with a `package:` data URI to the factory. Add the trilingual strings in one call, checked against `docs/COMMUNICATION_POLICY.md` §2 and §6. Leave the in-flow request during an actual install in place.

**Why:**

Strategic §6 item 5 rules the permission is shown as a row on equal terms, but only in the build without legal restrictions where it is declared, and that the in-flow request during installation stays - the visibility condition being the same general rule the whole spec establishes.

**Verification:**

- `Grep` - `request_install_packages` matches in `PermissionRegistryRepositoryImpl.kt`.
- `Grep` - `IS_NO_LEGAL_FLAVOR` matches in the `buildGates` of that entry.
- `Grep` - `ACTION_MANAGE_UNKNOWN_APP_SOURCES` matches in `PermissionGrantIntentFactory.kt`.
- `.\a.ps1 fkn` exits 0 (the entry's only live flavor).
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "perm_"` exits 0.

**Status:** `[x]` done

---

### Step 03.3 - Add the capture-consent informational row

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImpl.kt`, `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 03.2

**Prompt for developer:**

> Add a `screen_capture_consent` entry: group `SYSTEM`, `optional = true`, `grantKind = PER_USE_CONSENT`, `buildGates = setOf("DECLARES_SCREEN_CAPTURE")`. Its `manifestName` is `Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION` - the manifest permission the capture path declares - so that phase 04 can pair the row with a real manifest entry; the row itself reports "asked every time" because the consent is the MediaProjection dialog, not the manifest permission.
>
> Add the trilingual title and description in one call, checked against `docs/COMMUNICATION_POLICY.md` §2 and §6, and say plainly that the system asks each time a capture starts and that this cannot be granted in advance.

**Why:**

Strategic §6 item 6 rules this is shown as an informational row without a grant button, with a state of "asked every time" rather than granted or denied, so that the user can see in the list that the app is capable of asking - which strategic §2 goal 1 requires of the full list.

**Verification:**

- `Grep` - `screen_capture_consent` matches in `PermissionRegistryRepositoryImpl.kt`.
- `Grep` - `PermissionGrantKind.PER_USE_CONSENT` matches exactly once in `PermissionRegistryRepositoryImpl.kt`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "perm_"` exits 0.
- `.\a.ps1 fc` exits 0.

**Status:** `[x]` done

---

### Step 03.4 - Correct the two conditions that do not match the build

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImpl.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> Change the `record_audio` entry's gate from `SUPPORT_AUDIO` to `SUPPORT_MIC_RECORDING`, the flag every real microphone feature is gated on.
>
> Add `DECLARES_BATTERY_OPTIMIZATION` to the `battery_optimization` entry's `buildGates`.

**Why:**

Strategic §1 records both defects concretely: the microphone is tied to the audio-support flag instead of the microphone-recording flag, so the `lite` build offers a permission for a capability it does not contain, and the battery row is shown unconditionally although the release manifest strips that permission - and strategic §11 criteria 2 and 3 state the fixes as acceptance conditions.

**Verification:**

- `Grep` - `SUPPORT_AUDIO` returns zero hits in `PermissionRegistryRepositoryImpl.kt`.
- `Grep` - `SUPPORT_MIC_RECORDING` matches in the `record_audio` entry.
- `Grep` - `DECLARES_BATTERY_OPTIMIZATION` matches in the `battery_optimization` entry.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

### Step 03.5 - Declare the onboarding difference in the registry and drop the dead icon field

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/PermissionEntry.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImpl.kt`, `app_v2/src/test/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImplTest.kt`
**Depends on:** Step 03.4

**Prompt for developer:**

> Add `shownInWelcomeDespiteGates: Boolean = false` to `PermissionEntry` and set it on `post_notifications`. Rewrite `getWelcomeEntries()` to apply the SDK window to every entry and the build gates only to entries where the flag is `false`, deleting the hand-written `POST_NOTIFICATIONS` re-add block. Keep the existing comment's reasoning on the flag's KDoc.
>
> Delete the `iconRes` field from `PermissionEntry` and its `iconRes = 0` argument from every entry - it is always zero and no row layout has an icon view.
>
> Extend the test to assert `getWelcomeEntries()` is a superset of `getEntries()` and that the two differ only on entries carrying the flag.

**Why:**

Strategic §2 goal 6 requires any difference between the onboarding composition and the settings composition to be declared in the registry itself and visible next to the other rules, and strategic §5.1's cleanup pillar requires displaced dead weight to go in the same change; research artifact 01 records `iconRes` as always zero with no icon view to render it.

**Verification:**

- `Grep` - `iconRes` returns zero hits in `PermissionEntry.kt` and in `PermissionRegistryRepositoryImpl.kt`.

  **Predicate corrected 2026-08-06.** The original read "zero hits under `app_v2/src/main`", which no edit to this ticket could ever satisfy: `iconRes` is an ordinary field name several unrelated classes own - `InternalRouteCatalog`, `ResolveLauncherCommandLabelUseCase`, `ResolveAppLaunchPanelTilesUseCase`, every launcher gadget, and a local `val` inside `PermissionRowAdapter.bindStateIndicator`. Scoped to the two files this step owns. Verified separately that the permission-domain `iconRes` had no reader at all before deletion.
- `Grep` - `shownInWelcomeDespiteGates` matches in `PermissionEntry.kt`, and exactly one **entry** sets it in `PermissionRegistryRepositoryImpl.kt` (two hits in that file: the one assignment plus the single read in `getWelcomeEntries`, which is the minimum a working rewrite can have).
- `Grep` - `POST_NOTIFICATIONS` returns zero hits inside `getWelcomeEntries`.
- `.\a.ps1 fu` runs `PermissionRegistryRepositoryImplTest` green (record `expected: PASS | actual: <result>`).

**Status:** `[x]` done

---

### Step 03.6 - Give both screens one grouping implementation

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BuildPermissionRowsUseCase.kt` (New), `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PermissionsManagementFragment.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/helpers/WelcomePermissionsManager.kt`
**Depends on:** Step 03.5

**Prompt for developer:**

> Create `BuildPermissionRowsUseCase`, taking the entry list and a context and returning the `PermissionRow` list: required entries grouped under their group headers, then optional entries under the "optional" header, skipping empty groups. Move the body of the two existing `buildRows` implementations into it unchanged in behaviour.
>
> Have `PermissionsManagementFragment` and `WelcomePermissionsManager` call it, each passing its own entry list - `getEntries()` and `getWelcomeEntries()` respectively - so the composition difference stays in the registry and the grouping no longer exists twice.

**Why:**

Research artifact 01 records the two `buildRows` methods as near-identical hand-maintained copies, and strategic §5.2 requires both surfaces to receive the grouping rule from the registry instead of maintaining two almost-identical copies separately.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BuildPermissionRowsUseCase.kt` exists.
- `Grep` - `private fun buildRows` returns zero hits in `PermissionsManagementFragment.kt` and `WelcomePermissionsManager.kt`.
- `Grep` - `perm_group_optional` matches exactly once under `app_v2/src/main/java`.
- `.\a.ps1 fc` exits 0.

**Status:** `[x]` done

---

### Step 03.7 - Add the write permission the storage check already demands

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImpl.kt`, `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase. Numbered last because it was written last, by the phase-02 boundary audit; it touches only the registry row and its two strings, so it shares no file state with 03.1-03.6 and was executed first, to close the audit's P1 before phase 02 flipped Done rather than carrying it through six steps.

**Prompt for developer:**

> Add a `write_external_storage` entry: manifest name `Manifest.permission.WRITE_EXTERNAL_STORAGE`, group `STORAGE`, `optional = false`, `minSdk = 23`, `maxSdk = 28` - the window the manifest already declares with `android:maxSdkVersion="28"`. Leave `grantKind` at its `RUNTIME_DIALOG` default: it is an ordinary runtime permission inside its window.
>
> Then delete the manifest-sourced exception from the `StoragePermissionRule` KDoc and its `WRITE_EXTERNAL_STORAGE_MAX_SDK` constant's comment, since the window now has a registry row like every other, and re-point `StoragePermissionRuleTest`'s write case at that row.

**Why:**

Found by the phase-02 boundary audit, and it is the one hole in this ticket's own thesis. From API 26 the system grants only the permission that was actually requested, and every registry-driven grant surface - the permissions screen and onboarding, both of which build their batch from `getEntries()` / `getWelcomeEntries()` - can only request what the registry lists. `PermissionHelper.checkStoragePermissions` has demanded read **and** write on API 23-28 since long before this ticket, so on an API 26-28 device the screen reports storage as granted while `MainStoragePermissionsHelper.hasFullLocalPermissions` and `MainResumePlaybackHelper.shouldAttemptResume` read it as not granted - exactly the divergence strategic §1 describes, surviving because the permission has no row.

**Verification:**

- `Grep` - `write_external_storage` matches in `PermissionRegistryRepositoryImpl.kt`.
- `Grep` - `maxSdk = 28` matches in that entry.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "perm_"` exits 0.
- `.\a.ps1 fu --tests "*Permission*Test"` exits 0 and `StoragePermissionRuleTest` reports a fresh non-zero test count with zero failures.

**Status:** `[x]` done

---

## Step Log

- 2026-08-06 - **Phase-boundary audit** (layers 1-3; layer 4 not applicable). Layer 2 and 3 clean - the injected use case is synchronous and stateless, holds no `Context`, and neither screen's launcher registration changed. Two standing notes worth carrying: `WelcomePermissionsManager` is unscoped by design, so its never-removed lifecycle observer and never-nulled `activity`/`binding` die with the Activity - putting a scope annotation on that class would turn all three into real leaks; and the `screen_capture_consent` row stays out of the grant-all batch **only** because `CheckPermissionStatusUseCase` answers `ASKED_EACH_TIME` before the SDK window and before any `ContextCompat` check. That ordering is load-bearing and untested: phase 04 should pin it, because `FOREGROUND_SERVICE_MEDIA_PROJECTION` is install-time and would come back permanently DENIED from a runtime request.
  - **P1 fixed here, as step 03.8.** `ACCESS_COARSE_LOCATION` is declared in every flavor and had no row, so nothing ever requested it; from API 31 a fine-only request is ignored, and a user who answers "approximate" left the list reporting location as denied while the geotag path was happily using the coarse grant. Exactly the divergence this ticket exists to remove, one row away from being permanent.
  - **P2 fixed here:** the capture-consent row's description was its only content - the row has no button by the owner's ruling - and `item_permission_entry.xml` capped every description at one line with an ellipsis, so the sentence explaining why there is no control was itself cut off. Raised to three lines; every other description is short enough that nothing else changes shape. No `layout-land` variant of that file exists, so Rule 11 does not apply.
  - **P2 routed to phase 04, not fixed here, because each needs a decision the parity gate is the right place to record.** Three permissions are declared without an applicable registry row: `RECORD_AUDIO` in `lite` and `photos` (step 03.4 widened this from `photos` alone by moving the gate to `SUPPORT_MIC_RECORDING` - correct per spec, but the manifest still declares it), `POST_NOTIFICATIONS` in `lite` and `photos` on the Settings side only, and nothing else. Each is either a manifest that over-declares - which the canon's "declare only the permissions the runtime actually uses" says to fix in the manifest - or a deliberate exemption. Written into step 04.1, which owns the exemption list.
  - **P3 recorded:** the intent factory has no `PER_USE_CONSENT` branch and falls through to app details, unreachable today but silent; grant-all now walks up to six system screens in a row on `noLegal`, which is a consequence of the rows the owner asked for but was never itself ruled on; "Grant all" stays visible while any SYSTEM_SCREEN row is denied, since `isRequestable(DENIED)` cannot tell "can be asked again" from "was already offered this run".
  - A behaviour difference the rewrite introduced and step 03.5 did not record: the deleted block appended `POST_NOTIFICATIONS` **after** the filter, so on `lite`/`photos` it was the last onboarding row; the new filter preserves declaration order, so it now sits between `record_audio` and `battery_optimization`. Cosmetic, but visible on exactly the builds the flag exists for.

- 2026-08-06 - Step 03.6 DONE. `BuildPermissionRowsUseCase` created and both screens call it, each passing its own entry set - `getEntries()` from Settings, `getWelcomeEntries()` from onboarding - so the composition difference stays where the registry declares it and the grouping exists once. `perm_group_optional` `expected: exactly 1 under src/main/java | actual: 1`, which is the mechanical form of "the optional heading is built in one place".
  - **One thing the plan did not name.** `PermissionRow` was declared inside `ui/settings/fragments/PermissionRowAdapter.kt`, so a `domain/usecase` returning it would have made the domain depend on one screen's adapter - the inversion CLAUDE.md's layer order exists to prevent. It is a pure data holder over three domain types, so it moved to `domain/model/PermissionRow.kt` and the three files that name it import it from there. The alternative - leaving the use case in `ui/` - would have put the shared rule inside one of the two screens it is meant to serve.
  - `WelcomePermissionsManager` lost its `R` and `PermissionGroupHeader` imports with the deleted copy, and the fragment lost the fully-qualified `PermissionGroupHeader` constructor call it had inlined.
  - Verification 4/4 PASS: use-case file exists; `private fun buildRows` `expected: 0 hits in both screens | actual: 0`; `perm_group_optional` `expected: 1 | actual: 1`; `.\a.ps1 fc` `expected: exit 0 | actual: exit 0`. The Hilt graph is not proved by `fc` - the new `@Inject` sites are proved by the phase's `dq` build below.

- 2026-08-06 - Step 03.5 DONE. `getWelcomeEntries` is now one filter over the raw registry - SDK window on every entry, build gates only on entries not marked `shownInWelcomeDespiteGates` - and the hand-written `POST_NOTIFICATIONS` re-add block is gone. The flag carries the old block's reasoning on its KDoc, which is the point: the onboarding-versus-settings difference is a declared property of an entry rather than a special case coded into the query.
  - `iconRes` deleted from `PermissionEntry` and from all 18 entries. Checked before deleting, not after: the permission-domain `iconRes` had **no reader anywhere** - `PermissionRowAdapter.bindStateIndicator`'s `iconRes` is a local `val` for the state indicator, and every other hit under `src/main` belongs to an unrelated class with the same field name. Two test fixtures constructed it and were updated in the same step.
  - A stale comment went with it: `getGroups` still explained itself in terms of the welcome set "re-adding POST_NOTIFICATIONS past its gate", which stopped being true in this step. Rewritten to name the flag instead (Rule 9 - a comment that survives the code it described is worse than none).
  - New test `S1436 onboarding is a superset of settings and differs only on welcome-only entries` asserts both halves: onboarding never hides what Settings shows, and anything it adds carries the flag. That is what stops the next welcome-only exception from being coded into the query again.
  - Verification 4/4 PASS (one predicate corrected, see the step): `iconRes` `expected: 0 | actual: 0` in both owned files; the flag declared once and read once; `POST_NOTIFICATIONS` `expected: 0 inside getWelcomeEntries | actual: 0`; `.\a.ps1 fu --tests "*Permission*Test"` `expected: PASS | actual: PASS` - 5 classes, 30 tests, 0 failures, `PermissionRegistryRepositoryImplTest` up from 8 to 9, XMLs stamped 2026-08-06T17:19-17:20Z.

- 2026-08-06 - Step 03.4 DONE. `record_audio` moved from `SUPPORT_AUDIO` to `SUPPORT_MIC_RECORDING`, so the `lite` build stops offering a microphone permission for a capability it does not contain, and `battery_optimization` gained `DECLARES_BATTERY_OPTIMIZATION`, so the row disappears from the builds whose manifest strips the permission.
  - `SUPPORT_AUDIO` left the file entirely, its resolver-map line included - the predicate asks for zero hits and a mapping nothing declares is exactly the dead weight Rule 20 is about. The two registry tests that guard the gate set (`declaredBuildGateFields` names a real boolean `BuildConfig` field, and the resolver is total over the declared set) still hold: the map is allowed to be a superset, and it no longer is.
  - Verification 4/4 PASS: `SUPPORT_AUDIO` `expected: 0 hits | actual: 0`; `SUPPORT_MIC_RECORDING` present on `record_audio`; `DECLARES_BATTERY_OPTIMIZATION` present on `battery_optimization`; `.\a.ps1 fk` `expected: exit 0 | actual: exit 0`.

- 2026-08-06 - Step 03.3 DONE. `screen_capture_consent` added with `grantKind = PER_USE_CONSENT` and the `DECLARES_SCREEN_CAPTURE` gate, carrying `FOREGROUND_SERVICE_MEDIA_PROJECTION` as its manifest name - the permission `src/screenCapture/AndroidManifest.xml` actually declares, so phase 04 pairs the row with a real entry instead of an exemption. It is the first and only producer of the third grant kind, which phase 02 built and left unreachable; that gap is now closed.
  - The description says the thing plainly rather than describing a state the user cannot act on: "The system asks every time a capture starts - it cannot be allowed in advance". `docs/COMMUNICATION_POLICY.md` §6 walked - no exception text, no false promise of a control, and it explains why the row has no button before the user goes looking for one.
  - Verification 4/4 PASS: `screen_capture_consent` in the registry, `PermissionGrantKind.PER_USE_CONSENT` `expected: exactly 1 | actual: 1`, `check_strings_localized.ps1 -KeyPrefix "perm_"` `expected: exit 0 | actual: exit 0` (62 keys), `.\a.ps1 fc` `expected: exit 0 | actual: exit 0`.

- 2026-08-06 - Step 03.2 DONE. `request_install_packages` added, gated on `IS_NO_LEGAL_FLAVOR` and `minSdk = 26`, with `ACTION_MANAGE_UNKNOWN_APP_SOURCES` as its route. Status comes from `canRequestPackageInstalls()`, which is the same shape as the overlay row above - an appop the generic `checkSelfPermission` arm reports DENIED for regardless of the user's answer. Rule 21 does not apply: it governs the `getPackageInfo` / `queryIntentActivities` flag overloads, and this is neither.
  - The SDK guard in both the status arm and the factory arm is for lint, not for logic - the entry's own `minSdk` already keeps the row off older levels, but `legacy` builds at minSdk 23 and lint reads the call, not the registry. Recorded so the next reader does not "simplify" it away.
  - Verification 5/5 PASS: `request_install_packages` in the registry, `IS_NO_LEGAL_FLAVOR` on its `buildGates`, `ACTION_MANAGE_UNKNOWN_APP_SOURCES` `expected: 1 | actual: 1`, `.\a.ps1 fkn` `expected: exit 0 | actual: exit 0` (the entry's only live flavor), `check_strings_localized.ps1 -KeyPrefix "perm_"` `expected: exit 0 | actual: exit 0` (60 keys in en/ru/uk).

- 2026-08-06 - Step 03.1 DONE. `system_alert_window` added to the registry, gated on `DECLARES_OVERLAY_PERMISSION` - the axis phase 01 set beside the manifest-injection condition it mirrors, so a build that does not declare the permission cannot offer a row for it. `CheckPermissionStatusUseCase` answers it through `Settings.canDrawOverlays`, and the comment says why that is not optional: `SYSTEM_ALERT_WINDOW` is an appop, so the generic `checkSelfPermission` arm would report DENIED to a user who had granted it. The factory routes it to `ACTION_MANAGE_OVERLAY_PERMISSION`, package-scoped.
  - The gesture toggle was left alone, as strategic §6 item 4 rules: `gestureOverlayEnabled` still lives in `WelcomeGesturesManager` and `OperationsGesturesManager` (`expected: still present | actual: 12 hits across the two`). The row and the toggle answer different questions - "is the permission held" and "is the strip switched on" - and the ticket only claims the first.
  - Strings `perm_title_system_alert_window` / `perm_desc_system_alert_window` added across EN/RU/UK in one call each. `docs/COMMUNICATION_POLICY.md` §6 walked: both name the capability in the user's terms ("Display over other apps", "Gesture strip on top of other apps"), neither carries exception text or a bare API level.
  - Verification 6/6 PASS: registry hit, `canDrawOverlays` `expected: 1 | actual: 1`, `ACTION_MANAGE_OVERLAY_PERMISSION` `expected: 1 | actual: 1`, toggle untouched, `check_strings_localized.ps1 -KeyPrefix "perm_"` `expected: exit 0 | actual: exit 0` (58 keys in en/ru/uk), `.\a.ps1 fk` `expected: exit 0 | actual: exit 0`.

- 2026-08-06 - Step 03.7 DONE, executed first in the phase. `write_external_storage` added to the registry with `minSdk = 23, maxSdk = 28`, the window the manifest already declares, so the permissions screen and onboarding can finally request the permission the storage check has always demanded on those API levels. `StoragePermissionRule` and its test lost the "manifest exception" wording with it - every window it owns now traces to a registry row.
  - Strings `perm_title_write_external_storage` / `perm_desc_write_external_storage` added across EN/RU/UK in two `set-android-string.ps1 -Action add` calls. Wording checked against `docs/COMMUNICATION_POLICY.md` §2 and §6: both are plain descriptions of what the permission is for, name no API number a user would not recognise ("Android 9 and older" rather than "API 28"), and carry no exception text.
  - Verification 4/4 PASS: `write_external_storage` present in the registry; `maxSdk = 28` `expected: 1 | actual: 1`; `check_strings_localized.ps1 -KeyPrefix "perm_"` `expected: exit 0 | actual: exit 0` (56 keys, all present in en/ru/uk); `.\a.ps1 fu --tests "*Permission*Test"` `expected: PASS | actual: PASS` - 5 classes, 29 tests, 0 failures, XMLs stamped 2026-08-06T17:05-17:06Z. `post-change: PASS (Mixed)`, exit 0.
  - The row is invisible on the test's own SDK level by design - it is filtered out above API 28 - so the SDK-window assertions live in `StoragePermissionRuleTest`, which drives every level rather than the one Robolectric is pinned to.

---

### Step 03.8 - Give the approximate location its own row

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImpl.kt`, `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 03.5

**Prompt for developer:**

> Add an `access_coarse_location` entry: manifest name `Manifest.permission.ACCESS_COARSE_LOCATION`, group `LOCATION`, `optional = true`, `minSdk = 23`, default grant kind. Add its title and description across EN/RU/UK in one `set-android-string.ps1 -Action add` call each.

**Why:**

Found by the phase-03 boundary audit. `ACCESS_COARSE_LOCATION` is declared in `src/main/AndroidManifest.xml` in all six flavors and had no registry row, so no grant surface ever requested it - and from API 31 the platform requires fine and coarse to be requested in the same call, which is exactly what a batch built from registry rows could not do. Its own row also stops the list from reporting location as ungranted when the user answered "approximate", which is a grant the geotag path accepts (`PermissionHelper.hasLocationPermission` is fine **or** coarse). Phase 04's parity test would have failed on it in every flavor.

**Verification:**

- `Grep` - `access_coarse_location` matches in `PermissionRegistryRepositoryImpl.kt`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "perm_"` exits 0.
- `.\a.ps1 fc` exits 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done` - 8 of 8, including 03.7 and 03.8, both written by boundary audits.
- [x] Project compiles - `.\a.ps1 dq` `expected: exit 0 | actual: exit 0`, which is also what proved the Hilt graph after `BuildPermissionRowsUseCase` gained two injection sites; `.\a.ps1 fc` re-proved code + resources after the audit fixes.
- [x] `Grep` for `TODO(phase-03)` returns zero hits - `expected: 0 | actual: 0`.
- [x] Dev log entry added for every file in "Files Touched".
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - `catalog_sync` ran in every close; `BuildPermissionRowsUseCase` and `PermissionRow` carry their role and status via `set.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1. The P1 (`ACCESS_COARSE_LOCATION` declared in every flavor with no row) was fixed here as step 03.8, and the truncated consent description with it. The P2s that need a decision rather than a fix are written into step 04.1, which owns the exemption list; the P3s are in the Step Log.
- [x] Screenshot: deferred, no device this run. The phase's own criteria do not demand one and the placement decisions are the owner rulings quoted above, so the deferral does not hold the phase open (S1338, "no device attached" branch). The capture-consent row and the three-line description are the two things worth looking at when a device is next attached.

---

## Handoff Notes to Next Phase

The registry now enumerates every permission the build asks the user about, each conditioned on the axes that actually decide whether it is in the manifest, and both screens read one composition rule and one grouping implementation. Phase 04 can therefore treat "registry entry applicable to this build" and "permission declared in this build's manifest" as two sets that must match.

---

## Rollback Plan

Revert phase commit(s) - no data migration. New rows appear and disappear with the revert; no persisted user state is written by any of them.
