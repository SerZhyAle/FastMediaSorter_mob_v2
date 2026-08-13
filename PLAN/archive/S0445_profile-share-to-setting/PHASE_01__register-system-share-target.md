# Phase 01 - Register the system-Share target

**Strategic spec:** [`../S0445_profile-share-to-setting.md`](../S0445_profile-share-to-setting.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** - (S0452 foundation is Verified)
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

---

## Objective

Contribute one `ShareTarget` for the system Share to the Hilt `Set<ShareTarget>` multibinding. After this phase the toggle auto-appears in the "Send file to.." settings group (the group un-hides because the registry is no longer empty), and `IsShareTargetEnabledUseCase("system_share", settings)` returns a meaningful value. No consumer is gated yet - that is Phases 02-04.

---

## Prerequisites

- [ ] S0452 classes present: `core/share/ShareTarget.kt`, `ShareTargetRegistry.kt`, `ShareTargetAvailabilityResolver.kt`, `core/share/di/ShareTargetModule.kt`, `domain/usecase/IsShareTargetEnabledUseCase.kt`.
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/share/di/ShareTargetModule.kt` | Modified | ≤ 120 |

---

## Steps

### Step 01.1 - Contribute the `system_share` target via multibinding

**Files:** `core/share/di/ShareTargetModule.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `ShareTargetModule`, add a `@Provides @IntoSet` that returns a `ShareTarget` with: `id = "system_share"`, `titleRes = R.string.share` (reuse the existing Share label - do not add a new string), `defaultEnabled = ShareTargetDefault.ALWAYS_ON`, `availability = ShareTargetAvailability.ALWAYS`, `packages = emptyList()`. Keep the `@Multibinds` declaration. Use the `R` from the app package. Provider is a pure declaration - no logic.
>
> Rationale (do not restate in code): system chooser is present on every device and always desirable, so availability is ALWAYS and default is ALWAYS_ON; this also satisfies "migration = enabled".

**Verification:**

- `Grep` - `"system_share"` literal present in `ShareTargetModule.kt`.
- `Grep` - `@IntoSet` present in `ShareTargetModule.kt`.
- `Grep` - `ShareTargetDefault.ALWAYS_ON` and `ShareTargetAvailability.ALWAYS` referenced.
- `Grep` - `R.string.share` referenced (no new string key introduced for the title).

**Status:** `[ ]` not done

---

### Step 01.2 - Compile and confirm the toggle renders

**Files:** - (build only)
**Depends on:** Step 01.1

**Prompt for developer:**

> Run `.\a.ps1 fk` (Kotlin compile). The settings group "Send file to.." is built from the registry by the foundation; with one target registered it un-hides and shows a single "Share" toggle, checked by default. No code change in this step - it confirms the multibinding compiles and the foundation picks the entry up. (Manual on-device confirmation of the toggle is recorded in `/spec-check` / device test, not here.)

**Verification:**

- `.\a.ps1 fk` exits 0.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] `.\a.ps1 fk` exits 0.
- [ ] PIB-1 (title-only row vs. Share icon) resolved and recorded in INDEX Blockers Log.

---

## Handoff Notes to Next Phase

- The target id `"system_share"` is the contract string for all gates in Phases 02-04. Use exactly this literal everywhere.
- Availability is ALWAYS, so consumer gates reduce to `IsShareTargetEnabledUseCase("system_share", settings)` - no need to also call `ShareTargetAvailabilityResolver.isAvailable` in the consumers (the use-case does not consult availability; visibility = flag only here, which is correct because availability is constant true).

---

## Rollback Plan

Remove the `@Provides @IntoSet` provider. Registry returns to empty, settings group re-hides, no consumer was changed yet.
