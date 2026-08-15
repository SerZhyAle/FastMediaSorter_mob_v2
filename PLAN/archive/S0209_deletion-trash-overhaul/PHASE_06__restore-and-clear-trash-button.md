# Phase 06 — Restore + manual Clear Trash button alignment

**Strategic spec:** [`../S0209_deletion-trash-overhaul.md`](../S0209_deletion-trash-overhaul.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 07
**Steps done:** 3 / 3
**Started:** 2026-05-15
**Completed:** 2026-05-15

---

## Objective

Wire the manual `btnClearTrash` action and the existing restore flow through the same contract installed in Phase 01–02. Manual clear removes every trash snapshot (`maxAgeMs = 0L`) across all LOCAL resources; restore continues to read snapshots from the canonical `.trash/<ts>/metadata.json` layout.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsViewModel.kt` | Modified | ≤ 800 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/CleanupTrashFoldersUseCase.kt` | Verify only | — |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/RestoreDeletedUseCase.kt` | Verify only | — |
| `app_v2/src/main/res/values/strings.xml` (potentially) | Modified | +0..2 lines |
| `app_v2/src/main/res/values-ru/strings.xml` (potentially) | Modified | +0..2 lines |
| `app_v2/src/main/res/values-uk/strings.xml` (potentially) | Modified | +0..2 lines |

> `SettingsViewModel.kt` size unknown — Grep before edit to confirm ≤ 1500 lines.

---

## Steps

### Step 06.1 — Audit `SettingsViewModel.clearAllTrash`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsViewModel.kt`
**Depends on:** Phase 02

**Prompt for developer:**

> Locate `clearAllTrash`. Trace the entire call chain. The expected behaviour after Phase 02: iterate all LOCAL `MediaResource` entries and call `cleanupTrashFoldersUseCase.cleanup(rootDir, maxAgeMs = 0L)`. If the current implementation references `CleanupTrashUseCase` (the singular, possibly removed in Phase 02), rewire to the folders use-case. If it already uses the folders use-case, this step is read-only.

**Verification:**

- `Grep -n "clearAllTrash" app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsViewModel.kt` returns ≥ 1.
- `Grep -n "cleanupTrashFoldersUseCase" app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsViewModel.kt` returns ≥ 1 inside the `clearAllTrash` method body.
- Target variant compiles.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — `SettingsViewModel.clearAllTrash` now injects and uses `CleanupTrashFoldersUseCase`, filters LOCAL resources, and calls `cleanup(rootDir, maxAgeMs = 0L)` instead of the old singular cleanup path.

---

### Step 06.2 — User-visible confirmation message for manual clear

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsViewModel.kt`, optionally `strings.xml` trio
**Depends on:** Step 06.1

**Prompt for developer:**

> After `clearAllTrash` runs, surface a result Toast/snackbar with the count of removed snapshots across all resources. If a suitable string id already exists (e.g. `R.string.trash_cleared_count`), reuse it; otherwise add `trash_cleared_count` with a plurals-friendly EN/RU/UK trio. Compliance with `docs/COMMUNICATION_POLICY.md` §2 ("Operation completed — N items") and §6 mandatory. Wire through `SnackbarBus`/`Toast` or whatever the project standard for settings feedback is.

**Verification:**

- Manual clear path delivers a single user feedback message (visible in UI test).
- If new strings added: `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "trash_cleared_count"` exit 0.
- Strings pass `docs/COMMUNICATION_POLICY.md` §6 checklist.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Reused the existing `trash_cleared` key with count-based wording aligned to communication policy in EN/RU/UK. Localization check passed.

---

### Step 06.3 — Round-trip test: delete → restore on canonical layout

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/RestoreDeletedUseCaseTest.kt` (new) OR a section in an existing test
**Depends on:** Phase 02

**Prompt for developer:**

> Add an isolated unit test (instrumentation not required) that:
> 1. Creates a temporary directory tree with a real file.
> 2. Calls a stub of the soft-delete sequence (mimic `BaseFileOperationHandler.executeDelete` for the local strategy by directly invoking `TrashFolderContract.buildSnapshotPath` + `File.renameTo`).
> 3. Confirms the file appears in `.trash/<ts>/`.
> 4. Calls `RestoreDeletedUseCase.invoke(currentPath)` against the temp directory.
> 5. Asserts the file is back at its original location and `.trash/<ts>/` has been pruned.
> If `RestoreDeletedUseCase` cannot be tested directly in pure JVM (depends on `Context`), inject a `@Mock` Context and verify behaviour via the strategy seam.

**Verification:**

- New test file exists.
- `Grep` — `@Test` matches at least once.
- Test passes: `expected: 1 passed | actual: <observed>`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Added `RestoreDeletedUseCaseTest.kt` with a canonical `.trash/<ts>/metadata.json` round-trip fixture and fixed host-side snapshot discovery by normalizing backslash-separated paths in `RestoreDeletedUseCase`.
- 2026-05-15 — Fixed `TrashMetadata` serialization: replaced `org.json.JSONObject` with Gson to resolve JVM unit test stub failure (`org.json` returns empty stubs with `isReturnDefaultValues=true`).
- 2026-05-15 — Test passes: `expected: 1 passed | actual: 1 passed` (`testStandardDebugUnitTest` BUILD SUCCESSFUL).

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

- All trash entry points (creator, automatic cleaner, manual cleaner, restore) share one naming contract. Phase 07 closes docs and final catalog/feature audit.

---

## Rollback Plan

- Revert the phase commits. Manual clear and restore fall back to whatever path Phase 02 left in place.
