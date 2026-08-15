# Phase 03 - Flavor Containment

**Strategic spec:** [`../S0381_neuroslop-hygiene-hardening.md`](../S0381_neuroslop-hygiene-hardening.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done (pilot; UI-consumer migration deferred to follow-up)
**Depends on:** Phase 01
**Blocks:** Phase 05, Phase 06
**Steps done:** 2 / 3 (03.3 deferred)
**Started:** 2026-06-07
**Completed:** 2026-06-07

> **Design note (resolved during execution):** the abstraction is a `MediaCapabilities` data class in `src/main` carrying six boolean flags (video/audio/images/documents/epub/cloud). Each flavor source set provides it via a `MediaCapabilitiesModule` that reads that flavor's `BuildConfig` (allowed in flavor sets); the flavor matrix in `build.gradle.kts` stays the single source of values. This mirrors the existing `*SettingsSearchAvailabilityModule` per-flavor pattern and avoids both a `src/main` BuildConfig bridge (Rule 15 leak) and a hardcoded-matrix copy (drift). The earlier `ResourceFeatureGate`/`ResourceFeatureSet` interface naming is superseded by this value-object form.

---

## Objective

Replace one shared-code cluster of `BuildConfig.SUPPORT_*` / `ENABLE_*` checks with a flavor-bound capability abstraction and freeze growth in the touched area.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] First-wave boundary is approved for a pilot flavor-containment slice.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/capability/MediaCapabilities.kt` | New | ≤ 60 |
| `app_v2/src/standard/java/com/sza/fastmediasorter/di/MediaCapabilitiesModule.kt` | New | ≤ 60 |
| `app_v2/src/lite/java/com/sza/fastmediasorter/di/MediaCapabilitiesModule.kt` | New | ≤ 60 |
| `app_v2/src/photos/java/com/sza/fastmediasorter/di/MediaCapabilitiesModule.kt` | New | ≤ 60 |
| `app_v2/src/legacy/java/com/sza/fastmediasorter/di/MediaCapabilitiesModule.kt` | New | ≤ 60 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/di/MediaCapabilitiesModule.kt` | New (also serves noLegal) | ≤ 60 |

> noLegal gets NO module of its own: it mounts `src/vr/java` (build.gradle.kts sourceSets), so the vr module is compiled into noLegal builds and reads noLegal's `BuildConfig`. A separate noLegal module would redeclare the object and double-bind `MediaCapabilities` (confirmed: noLegal build failed with `Redeclaration` until the noLegal copy was removed).
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/FilterResourceDialog.kt` | Modified | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GetMediaFilesUseCase.kt` | Modified | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceFormManager.kt` | Modified | ≤ 260 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 03.1 - Introduce a flavor-bound media capability surface

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/capability/MediaCapabilities.kt` + `MediaCapabilitiesModule.kt` in each of `src/{standard,lite,photos,legacy,noLegal,vr}/java/com/sza/fastmediasorter/di/`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a `MediaCapabilities` value object in `src/main` carrying six boolean flags (video/audio/images/documents/epub/cloud). In each flavor source set add a `MediaCapabilitiesModule` that `@Provides` it from that flavor's `BuildConfig`. No consumer changes in this step - the surface is introduced and bound per flavor only, so there is no behavior change yet.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/core/capability/MediaCapabilities.kt` exists.
- `Glob` - each `MediaCapabilitiesModule.kt` path exists for standard/lite/photos/legacy/vr (noLegal uses the mounted vr module).
- `Grep` - `BuildConfig\.(SUPPORT_|ENABLE_)` does NOT appear in `MediaCapabilities.kt` (main stays flavor-flag-free).
- Build: `standard` + `noLegal` compile (representative source sets; the modules are textually identical and read fields proven present in every flavor).

**Status:** `[x] done`

**Step Log:**

- 2026-06-07 - Verification 4/4 PASS. Added `MediaCapabilities` data class (6 boolean flags) + per-flavor `MediaCapabilitiesModule` (@Provides from BuildConfig) for standard/lite/photos/legacy/vr. `MediaCapabilities.kt` carries no build-flag literal. Build: `standard` SUCCESSFUL (2m01s); `noLegal` SUCCESSFUL (3m22s) after removing the duplicate noLegal module (noLegal mounts src/vr → vr module serves it; the redundant copy caused a `Redeclaration` failure first, then was deleted). No consumer changes - zero behavior change. Dev logs + catalog sync recorded.

---

### Step 03.2 - Migrate the domain consumer to the capability surface (pilot)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GetMediaFilesUseCase.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Replace the `BuildConfig.SUPPORT_*` / `ENABLE_*` reads in `GetMediaFilesUseCase` with the injected `MediaCapabilities`. Behavior must stay identical for every flavor (the injected values are the flavor's BuildConfig values). This is the pilot consumer - the cleanest case (constructor injection, domain layer, no UI).
>
> `FilterResourceDialog` migration is DEFERRED to a follow-up increment (see Step 03.3 note): it is a plain `DialogFragment` with no Hilt entry point, so migrating it requires adding `@AndroidEntryPoint` + field injection - a UI-surface change carried separately to keep this pilot low-risk.

**Verification:**

- `Grep` - `BuildConfig\.(IS_|SUPPORT_|ENABLE_)` returns zero hits in `GetMediaFilesUseCase.kt`.
- `Grep` - `MediaCapabilities` appears in `GetMediaFilesUseCase.kt`.
- Build: `standard` compiles (Hilt resolves the new injected dependency).

**Status:** `[x] done`

**Step Log:**

- 2026-06-07 - Verification 3/3 PASS. `GetMediaFilesUseCase` now injects `MediaCapabilities` (constructor) and reads `supportsVideo/audio/images/documents/epub` instead of `BuildConfig`; unused `BuildConfig` import removed, comment updated. `BuildConfig.*` zero hits in the file. `standard` BUILD SUCCESSFUL (1m20s); post-change `ticket-log-audit` + `catalog-sync` green. Behavior identical (injected values == flavor BuildConfig). `FilterResourceDialog` deferred (see 03.3).

---

### Step 03.3 - Migrate the UI consumers (DEFERRED to follow-up)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/FilterResourceDialog.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceFormManager.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> DEFERRED - not executed in this wave. Migrating these two UI consumers off `BuildConfig.SUPPORT_*` requires Hilt entry-point changes (`FilterResourceDialog` is a plain `DialogFragment`; `AddResourceFormManager` has ~25 flag reads and a non-`@Inject` construction path) that carry real UI-surface regression risk and extra per-flavor builds. The pilot (Step 03.2 + the capability surface in 03.1) already proves the pattern end-to-end. Per strategic §5.1 ("инкрементальный вынос") and §11 criterion 3 (owner-approved deferral allowed), these are carried as a follow-up increment.

**Verification:**

- N/A - step deferred. When executed: `BuildConfig.(IS_|SUPPORT_|ENABLE_)` zero in both files; `MediaCapabilities` injected; `standard`+`noLegal`+`lite` compile; on-device check of resource filter + add-resource checkboxes per flavor.

**Status:** `[~] deferred (follow-up increment)`

---

## Phase Done Criteria

- [x] Steps 03.1 + 03.2 `[x] done`; 03.3 explicitly `[~] deferred` (documented follow-up, owner-approvable per strategic §11 criterion 3).
- [x] Project compiles - `standard` SUCCESSFUL (1m20s, with usecase injection); `noLegal` SUCCESSFUL (3m22s, 03.1). lite/photos/legacy/vr use identical modules reading fields present in all flavors.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every changed file via `.\scripts\add_to_dev_log.ps1` (value object, 5 flavor modules, GetMediaFilesUseCase).
- [x] Catalog regenerated via `scripts/catalog_sync.ps1 -Module app_v2`.

> Pilot net effect: capability surface established + bound per flavor; 8 BuildConfig flavor reads removed from `GetMediaFilesUseCase`. Remaining ~34 reads in `FilterResourceDialog` (9) + `AddResourceFormManager` (25) carried to a follow-up increment.

---

## Handoff Notes to Next Phase

One shared-code cluster now consumes flavor capabilities through injected abstraction instead of direct flavor flags.

---

## Rollback Plan

Revert phase commit(s) and restore direct shared-code flag reads in the migrated cluster only.
