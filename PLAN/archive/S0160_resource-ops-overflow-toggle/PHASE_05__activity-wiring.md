# Phase 05 — activity-wiring

**Strategic spec:** [`../S0160_resource-ops-overflow-toggle.md`](../S0160_resource-ops-overflow-toggle.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03, Phase 04
**Blocks:** Phase 06
**Steps done:** 2 / 2
**Started:** 2026-05-13
**Completed:** 2026-05-13

---

## Objective

Wire `MainActivity` to pass the `onScanClick` callback into `ResourceAdapter` and to call `resourceAdapter.setOverflowModeEnabled()` from the existing settings observer.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Phase 03 is ✅ Done.
- [ ] Phase 04 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` | Modified | ≤ 1030 |

> `MainActivity.kt` is 1017 lines — backup required before edit: `Copy-Item ... temp/MainActivity_<timestamp>.kt.backup`.

---

## Steps

### Step 05.1 — Pass `onScanClick` to `ResourceAdapter` constructor

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt`
**Depends on:** — start of phase (all prerequisite phases done)

**Prompt for developer:**

> Backup `MainActivity.kt` first: `Copy-Item app_v2/.../MainActivity.kt temp/MainActivity_$(Get-Date -Format 'yyyyMMdd_HHmmss').kt.backup`.
>
> Locate the `resourceAdapter = ResourceAdapter(…)` constructor call in `MainActivity`. Add the `onScanClick` named argument:
>
> ```kotlin
> onScanClick = { resource ->
>     viewModel.scanSingleResource(resource)
> },
> ```
>
> Add it as the last argument in the constructor call (before the closing `)`). The default `{}` that was added in Phase 03 Step 03.3 is now replaced by a real callback.

**Verification:**

- `Grep` — `onScanClick` matches in `MainActivity.kt`.
- `Grep` — `viewModel.scanSingleResource` matches in `MainActivity.kt`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-13 — Verification 2/2 PASS. Files: ui/main/MainActivity.kt (+3 LOC). Backup: temp/MainActivity_20260513_185423.kt.backup.

---

### Step 05.2 — Apply `setOverflowModeEnabled` in the settings observer

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> In the `collectOnLifecycle(settingsRepository.getSettings()) { settings ->` block (the block that currently calls `resourceAdapter.setUseCompactElements(settings.useCompactElements)`), add:
>
> ```kotlin
> resourceAdapter.setOverflowModeEnabled(settings.resourceOpsInOverflowMenu)
> ```
>
> Place it directly after the `setUseCompactElements` call.

**Verification:**

- `Grep` — `setOverflowModeEnabled` matches in `MainActivity.kt`.
- `Grep` — `setOverflowModeEnabled` appears inside the `collectOnLifecycle(settingsRepository.getSettings())` block (confirm by context lines).
- Build passes with `/build`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-13 — Verification 2/2 PASS + Build ✅. Files: ui/main/MainActivity.kt (+1 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Project compiles and links with no errors — run `/build`.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entry added for `MainActivity.kt` via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- Feature is end-to-end functional: toggle in Settings → adapter switches between inline and overflow mode → ⋮ menu appears on every resource card → "Refresh" updates the resource → unavailability toast shown on failure.
- Ready for on-device smoke test before Phase 06.

---

## Rollback Plan

Revert phase commit — backup in `temp/` for `MainActivity.kt`. No data migration.
