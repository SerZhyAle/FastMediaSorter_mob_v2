# Strategic spec: S0467 - Migrate deprecated PackageManager int-flag APIs

**Ticket:** S0467
**Status:** Archived
**Priority:** 35
**Date:** 2026-06-17
**Tier:** 1 - Small/Medium

> **Scope:** STRATEGIC draft skeleton. Captured finding only; no research/approval yet.

---

## 0. Captured material (inbox)

**Captured:** 2026-06-17 (parked during S0459 fix-implementation audit)

**Source:** code review of the unified «Send to..» share layer (S0459).

**Finding:**

- The app targets API 35 (`targetSdk 35`). `PackageManager.getPackageInfo(String, Int)` and `getApplicationInfo(String, Int)` with raw `Int` flags are deprecated since API 33; the replacements take `PackageManager.PackageInfoFlags.of(0L)` / `ApplicationInfoFlags.of(0L)`.
- The raw-int overloads are used across the share layer (and likely beyond). Known sites found during the S0459 audit:
  - `core/share/ShareTargetIconResolver.kt` - `getApplicationInfo` / `getPackageInfo` for icon + label resolution.
  - `core/share/ShareTargetAvailabilityResolver.kt` - `getPackageInfo` for availability.
  - `core/share/TelegramShareTargets.kt` - `getPackageInfo`.
  - `core/share/handlers/WhatsAppShareTargetHandler.kt` - `getPackageInfo`.
  - `core/share/handlers/InstagramShareTargetHandler.kt` - `getPackageInfo`.
- These compile today only because the deprecated overloads still exist; no runtime breakage. This is lint/forward-compat debt, not a bug.

**Why it is not folded into S0459:**

- Cross-cutting: touches S0452 foundation files and spans more than the share layer once a full grep is done.
- Needs its own approach: an API-guarded compat helper (`@RequiresApi(33)` branch + pre-33 fallback) applied uniformly, plus a full-repo sweep to find every raw-int call site.

**Attachments:** none.

---

## 1. Problem

Raw-int `PackageManager` flag overloads are deprecated on `targetSdk 35`. Each call is a deprecation warning and a forward-compatibility risk; there is no single compat seam, so new code keeps copying the deprecated pattern.

---

## 2. Approach

- One shared compat seam: `app_v2/src/main/java/com/sza/fastmediasorter/util/PackageManagerCompat.kt`.
- Four `PackageManager` extension functions, each holding the single `Build.VERSION.SDK_INT >= TIRAMISU` branch with `@Suppress("DEPRECATION")` on the pre-33 fallback: `getPackageInfoCompat`, `getApplicationInfoCompat`, `queryIntentActivitiesCompat`, `resolveActivityCompat`.
- `wear` is a separate module and cannot reach the `app_v2` seam; its single call site (`MainActivity.logAppInfo`) gets an inline guarded branch.
- Prevent-at-source: a ratchet grep gate keeps `src/main` at zero raw-int call sites; the seam file is the only allow-listed exception.

## 3. Implementation

- New seam: `util/PackageManagerCompat.kt`.
- Migrated call sites (`app_v2`): `core/share/TelegramShareTargets.kt`, `core/share/ShareTargetIconResolver.kt`, `core/share/ShareTargetAvailabilityResolver.kt`, `core/share/handlers/WhatsAppShareTargetHandler.kt`, `core/share/handlers/InstagramShareTargetHandler.kt`, `util/GoogleKeepAvailabilityChecker.kt`, `widget/CameraQuickCaptureLaunchManager.kt`, `ui/browse/managers/BrowseCameraCaptureManager.kt`, `ui/player/helpers/PlayerDrawingSaveHelper.kt`, `ui/player/helpers/OfficeDocumentOpenManager.kt`, `ui/settings/fragments/PlaybackSettingsFragment.kt`, `ui/settings/helpers/DefaultPlayerHelper.kt`, `domain/usecase/GatherSystemInfoUseCase.kt`, `ui/main/MainActivity.kt`, `data/repository/SettingsRepositoryImpl.kt`, `data/browser/CctAvailabilityChecker.kt`, `data/cloud/GoogleDriveBrowserAuthManager.kt`, `diagnostics/NoLegalDiagnosticsCollectors.kt` (noLegal).
- Migrated call site (`wear`): `MainActivity.kt`.
- Redundant `@Suppress("DEPRECATION")` removed from `OfficeDocumentOpenManager.queryCandidates` and `PlaybackSettingsFragment.resolveShareTargetLabel`.
- Gate: `scripts/quality/assert-deprecated-pm-flags.ps1` + `deprecated-pm-flags-baseline.txt` (0), wired into `scripts/post-change.ps1` for `Kotlin`/`Mixed` changes; `CLAUDE.md` Rule 21 documents the DON'T.

## 4. Out of scope

- `VrApkClassifier.readPackageArchive` already branches correctly for `getPackageArchiveInfo` (not migrated; not a raw-int-overload violation at runtime).
- `Intent.resolveActivity(PackageManager)` (single-arg, not deprecated) call sites are left untouched.

## 6. Resolved questions

- Inventory: 30 raw-int call sites across 18 `app_v2` files + 1 `wear` file (full grep recorded in §3).
- Shared compat helper chosen over per-call `@RequiresApi` branches (stops copy-paste; one seam to audit).
- Grep gate added (keep-at-zero ratchet) to prevent new raw-int usage at source.

---

## Last Audit

**Date:** 2026-06-17
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 9 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

> Pure API migration (deprecated raw-int `PackageManager` flag overloads -> `*Compat` seam). Compat seam `util/PackageManagerCompat.kt` present with all four extension functions (`getPackageInfoCompat`, `getApplicationInfoCompat`, `queryIntentActivitiesCompat`, `resolveActivityCompat`). Ratchet gate `assert-deprecated-pm-flags.ps1` reports src/main baseline 0 | actual 0 | delta 0 and is wired into `post-change.ps1` (Kotlin/Mixed). A full-source-set grep with the gate's own detector regex (main + noLegal + vr, seam excluded) finds zero residual raw-int two-arg overloads; `wear` `MainActivity.logAppInfo` carries the inline `SDK_INT >= TIRAMISU` guard. CLAUDE.md Rule 21 documents the DON'T. No user-visible behavior change -> FEATURES EXEMPT, no functionality-log entry. Earlier closure build (standard/noLegal/wear Kotlin) was already SUCCESSFUL.

### Manual / on-device

- [ ] None - static, build-verifiable API migration; no device test required.
