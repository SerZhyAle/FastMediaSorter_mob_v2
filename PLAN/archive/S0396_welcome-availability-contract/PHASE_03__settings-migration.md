# Phase 03 - Settings Visibility Migration

**Strategic spec:** [`../S0396_welcome-availability-contract.md`](../S0396_welcome-availability-contract.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-06-10
**Completed:** 2026-06-10

---

## Objective

Replace the `BuildConfig.ENABLE_TRANSLATION` visibility reads in the two settings fragments with injected `CapabilityAvailability`, preserving exact per-flavor visibility behavior.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done (contract returns correct per-flavor values).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OtherMediaSettingsFragment.kt` | Modified | ≤ 700 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt` | Modified | ≤ 700 |

> Both fragments are `@AndroidEntryPoint` Hilt fragments - inject the contract via `@Inject lateinit var capabilityAvailability: CapabilityAvailability`. No layout/string changes. Backup not required (read before editing per Rule 8).

---

## Steps

### Step 03.1 - Migrate OtherMediaSettingsFragment

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OtherMediaSettingsFragment.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `@Inject lateinit var capabilityAvailability: CapabilityAvailability`. In `applyFlavorRestrictions()` replace `if (!BuildConfig.ENABLE_TRANSLATION)` with `if (!capabilityAvailability.isTranslationAvailable())`. In `updateTranslationPrewarmStatus()` (the line `if (!BuildConfig.ENABLE_TRANSLATION || !viewModel.settings.value.enableTranslation)`) replace the `BuildConfig.ENABLE_TRANSLATION` term with `capabilityAvailability.isTranslationAvailable()`. Remove the now-unused `BuildConfig` import if no other `BuildConfig` reference remains in the file (grep first). Do not touch the `else` branch calling `applyDeviceCapabilityRestrictions()` - device-runtime OCR gating is unchanged. Preserve the existing KDoc intent; update the comment on line ~101 to say availability comes from the capability contract, not the build flag.

**Verification:**

- `Grep` - `capabilityAvailability.isTranslationAvailable()` present at least twice in the file.
- `Grep` - `BuildConfig.ENABLE_TRANSLATION` returns zero hits in this file.
- `Grep` - `@Inject lateinit var capabilityAvailability` present once.

**Status:** `[x]` done

**Step Log:**

- 2026-06-10 - Verification 3/3 PASS. Injected CapabilityAvailability; replaced both ENABLE_TRANSLATION reads (applyFlavorRestrictions + updateTranslationPrewarmStatus) with isTranslationAvailable(). BuildConfig import retained (3 IS_NO_LEGAL_FLAVOR reads remain - out of S0396 scope).

---

### Step 03.2 - Migrate PlaybackSettingsFragment

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> NOTE (exec finding): this fragment is a plain `Fragment`, not `@AndroidEntryPoint` - add the `@AndroidEntryPoint` annotation (its settings host activity is already a Hilt entry point, as it hosts the @AndroidEntryPoint `OtherMediaSettingsFragment`) so `@Inject` resolves. Then add `@Inject lateinit var capabilityAvailability: CapabilityAvailability`. Both `hasOcrAndTranslation` computations (around lines 100 and 533) read `BuildConfig.ENABLE_TRANSLATION && DeviceCapabilities.isOcrSupported(requireContext())`. Replace the `BuildConfig.ENABLE_TRANSLATION` term with `capabilityAvailability.isTranslationAvailable()` in both. Leave the `DeviceCapabilities.isOcrSupported(requireContext())` term untouched. Keep the `BuildConfig` import - `SUPPORTS_DEFAULT_PLAYER`/`SUPPORT_MIC_RECORDING` reads remain (out of S0396 scope).

**Verification:**

- `Grep` - `capabilityAvailability.isTranslationAvailable()` present at least twice in the file.
- `Grep` - `BuildConfig.ENABLE_TRANSLATION` returns zero hits in this file.
- `Grep` - `DeviceCapabilities.isOcrSupported` still present (device gating retained).
- `Grep` - `@AndroidEntryPoint` present (fragment is now Hilt-injectable).

**Status:** `[x]` done

**Step Log:**

- 2026-06-10 - Verification 4/4 PASS. Added @AndroidEntryPoint (fragment was a plain Fragment) + injected CapabilityAvailability; replaced both ENABLE_TRANSLATION terms with isTranslationAvailable(); DeviceCapabilities device-gating retained; BuildConfig import kept (SUPPORTS_DEFAULT_PLAYER/SUPPORT_MIC_RECORDING out of scope).

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - standard debug BUILD SUCCESSFUL 1m44s (validates contract + contributions + both fragment consumers + the new @AndroidEntryPoint).
- [x] `Grep` for `BuildConfig.ENABLE_TRANSLATION` across `ui/settings/` returns zero hits (verified: 0).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for both modified files.

---

## Handoff Notes to Next Phase

Settings visibility now flows through `CapabilityAvailability`. Functional-gating `ENABLE_TRANSLATION` reads outside `ui/settings/` remain by design (strategic §11.2 amended) and are not this ticket's concern.

---

## Rollback Plan

Revert phase commit(s) - two fragment edits; restores the direct `BuildConfig` reads. No data or layout surface changed.
