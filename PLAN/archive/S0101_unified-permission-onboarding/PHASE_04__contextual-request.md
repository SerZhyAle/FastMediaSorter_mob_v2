# Phase 04 — Contextual Permission Request

**Strategic spec:** [`../S0101_unified-permission-onboarding.md`](../S0101_unified-permission-onboarding.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 05, 06
**Steps done:** 4 / 4
**Started:** 2026-05-06
**Completed:** 2026-05-06

---

## Objective

Introduce the contextual permission request bottom sheet and the unified denial handler. These are the two shared UI components used by Settings feature toggles (Phase 06) and by the Settings management screen (Phase 05).

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Research §6.4 (button vs. toggle) resolved — confirmed interaction mode.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/permissions/PermissionRationaleBottomSheet.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/permissions/PermissionDenialHandler.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/RequestContextualPermissionUseCase.kt` | New | ≤ 80 |
| `app_v2/src/main/res/layout/bottom_sheet_permission_rationale.xml` | New | ≤ 50 |

---

## Steps

### Step 4.1 — Create bottom_sheet_permission_rationale.xml layout

**Files:** `app_v2/src/main/res/layout/bottom_sheet_permission_rationale.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Create `bottom_sheet_permission_rationale.xml`. Root: `ConstraintLayout` inside a `NestedScrollView`. Contents:
> - `ImageView @id/iv_perm_icon` (40dp, top center).
> - `TextView @id/tv_perm_title` (bold, 16sp).
> - `TextView @id/tv_perm_desc` (12sp, secondary color) — explains why the permission is needed.
> - `Button @id/btn_perm_grant` ("Grant" — primary action).
> - `Button @id/btn_perm_skip` ("Not now" — text-style secondary action).
> Landscape variant: not needed — bottom sheet handles its own height.

**Verification:**

- `Glob` — `app_v2/src/main/res/layout/bottom_sheet_permission_rationale.xml` exists.
- `Grep` — `btn_perm_grant` present in that file.
- `Grep` — `btn_perm_skip` present in that file.

**Status:** `[x] done`

**Step Log:**
- 2026-05-06 — Verification 3/3 PASS. Files: res/layout/bottom_sheet_permission_rationale.xml (new, 67 LOC). Landscape variant: not needed per step spec — BottomSheet self-manages height. Dev log recorded.

---

### Step 4.2 — Create PermissionRationaleBottomSheet

**Files:** `ui/common/permissions/PermissionRationaleBottomSheet.kt`
**Depends on:** Step 4.1

**Prompt for developer:**

> Create `PermissionRationaleBottomSheet.kt` in `ui/common/permissions/` as a `BottomSheetDialogFragment`.
>
> Companion factory: `fun newInstance(entry: PermissionEntry): PermissionRationaleBottomSheet` — pass `entry` via `Bundle` arguments (use `entry.id` as a string arg; the caller resolves the entry from the registry on the other side).
>
> In `onViewCreated`:
> - Bind icon, title (`entry.titleRes`), description (`entry.descriptionRes`) from injected `PermissionRegistryRepository.getEntries()` lookup by id.
> - `btn_perm_grant` triggers `ActivityResultContracts.RequestPermission` (via `registerForActivityResult` in the hosting fragment/activity — the bottom sheet delegates via a `callback: (Boolean) -> Unit` passed through a typed `interface PermissionRationaleCallback`).
> - `btn_perm_skip` dismisses the sheet; callback receives `false`.
>
> The sheet calls `MarkContextualShownUseCase.invoke(entry.id)` before showing (checked externally) — or after the user taps either button — to prevent future re-display.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/permissions/PermissionRationaleBottomSheet.kt` exists.
- `Grep` — `class PermissionRationaleBottomSheet` matches exactly once.
- `Grep` — `BottomSheetDialogFragment` present in that file.
- `Grep` — `Log\.d\(` returns zero hits in that file.

**Status:** `[x] done`

**Step Log:**
- 2026-05-06 — Verification 4/4 PASS. Files: ui/common/permissions/PermissionRationaleBottomSheet.kt (new, 65 LOC). Dev log recorded.

---

### Step 4.3 — Create PermissionDenialHandler

**Files:** `ui/common/permissions/PermissionDenialHandler.kt`
**Depends on:** — start of phase (independent)

**Prompt for developer:**

> Create `PermissionDenialHandler.kt` in `ui/common/permissions/`. A plain `object` (no DI needed):
>
> ```
> object PermissionDenialHandler {
>     fun handle(fragment: Fragment, entry: PermissionEntry)
>     fun handle(activity: Activity, entry: PermissionEntry)
> }
> ```
>
> Behavior: shows an inline `Snackbar` (or `AlertDialog` if Snackbar is not viable in context) with the text "To enable [permission title], open App Settings" and an "Open Settings" action button that launches `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`. No retry dialog, no second attempt. Uses `Timber.w` for logging, not `Log`.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/permissions/PermissionDenialHandler.kt` exists.
- `Grep` — `object PermissionDenialHandler` matches exactly once.
- `Grep` — `ACTION_APPLICATION_DETAILS_SETTINGS` present in that file.
- `Grep` — `Log\.d\(` returns zero hits in that file.

**Status:** `[x] done`

**Step Log:**
- 2026-05-06 — Verification 4/4 PASS. Files: ui/common/permissions/PermissionDenialHandler.kt (new, 37 LOC). Dev log recorded.

---

### Step 4.4 — Create RequestContextualPermissionUseCase

**Files:** `domain/usecase/RequestContextualPermissionUseCase.kt`
**Depends on:** Steps 4.2, 4.3

**Prompt for developer:**

> Create `RequestContextualPermissionUseCase.kt` in `domain/usecase/`. Purpose: the single entry point that Settings feature toggles call when a required permission is not yet granted.
>
> `fun invoke(fragment: Fragment, entry: PermissionEntry, onResult: (Boolean) -> Unit)`:
> 1. If `MarkContextualShownUseCase.isShown(entry.id)` is true → call `onResult(false)` (the user already dismissed once, do not pester again); return.
> 2. Else → show `PermissionRationaleBottomSheet` via `fragment.parentFragmentManager`; the sheet's callback feeds `onResult`.
>
> Inject `MarkContextualShownUseCase`.
> Annotate with `@Singleton`.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/RequestContextualPermissionUseCase.kt` exists.
- `Grep` — `class RequestContextualPermissionUseCase` matches exactly once.
- `Grep` — `MarkContextualShownUseCase` present in that file.

**Status:** `[x] done`

**Step Log:**
- 2026-05-06 — Verification 3/3 PASS. Files: domain/usecase/RequestContextualPermissionUseCase.kt (new, 26 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 4.*` above is `[x] done`.
- [x] Project compiles — run `/build`.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Phase 04 provides `PermissionRationaleBottomSheet`, `PermissionDenialHandler`, and `RequestContextualPermissionUseCase` — reused by Phase 05 (Settings screen) and Phase 06 (feature migration).

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed.
