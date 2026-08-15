# Phase 04 — MANAGE_MEDIA fallback for soft-delete

**Strategic spec:** [`../S0209_deletion-trash-overhaul.md`](../S0209_deletion-trash-overhaul.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 07
**Steps done:** 4 / 4
**Started:** 2026-05-15
**Completed:** 2026-05-15

---

## Objective

Detect when soft-delete cannot succeed on API ≥ 30 without `MANAGE_MEDIA` (rename of shared-storage media file fails), surface a deterministic fallback to hard-delete with a clear user-visible message, and prevent the "phantom dup" (file copied into `.trash/` but original not deleted). Implements strategic §11 criterion 3.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/LocalOperationStrategy.kt` | Modified | ≤ 600 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/BaseFileOperationHandler.kt` | Modified | ≤ 450 |
| `app_v2/src/main/res/values/strings.xml` | Modified | +3 lines |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | +3 lines |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | +3 lines |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImpl.kt` | Verify only | — |

> `LocalOperationStrategy.kt` is ~538 lines today. After edit may exceed 500 — back up to `temp/` before editing (timestamped copy). If it exceeds 700 — abort and replan via helper extraction.

---

## Steps

### Step 04.1 — Detect rename-failure-without-MANAGE_MEDIA in `moveFile`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/LocalOperationStrategy.kt`
**Depends on:** Phase 02

**Prompt for developer:**

> Add a new typed exception inside `LocalOperationStrategy` (or under `data/transfer/`):
> ```
> class TrashRenameUnavailableException(
>     val sourcePath: String,
>     val reason: String
> ) : Exception("Trash rename unavailable for $sourcePath: $reason")
> ```
> In `moveFile`: before the existing fallback (copy + delete), check explicitly for the failing-shared-storage scenario:
> - source is shared storage (reuse existing `isSharedStorage` check),
> - and Android API ≥ R,
> - and MANAGE_MEDIA is not granted (reuse the same checkSelfPermission used by `deleteViaMediaStore`),
> - and `sourceFile.renameTo(destFile)` returned false.
> If all four are true, throw `TrashRenameUnavailableException(sourcePath, "shared storage on API ${SDK_INT} without MANAGE_MEDIA")`. Do not attempt the copy+delete fallback in that case — copy succeeds but delete cannot, producing the phantom-dup bug. For all other cases, keep the existing copy+delete fallback.

**Verification:**

- `Grep -n` — `class TrashRenameUnavailableException` matches exactly once.
- `Grep -n` — `throw TrashRenameUnavailableException` inside `moveFile`.
- `Grep -n` — `MANAGE_MEDIA` referenced inside `moveFile` (not only `deleteViaMediaStore`).
- Target variant compiles.
- `Grep -n` — `Log\.d\(` returns zero matches.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Added `TrashRenameUnavailableException` and guarded the rename fallback path in `LocalOperationStrategy.moveFile`: shared-storage soft-delete on API >= R without `MANAGE_MEDIA` now throws instead of copy+deleteing into a phantom duplicate.

---

### Step 04.2 — Catch the exception in soft-delete branch and downgrade to hard-delete

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/BaseFileOperationHandler.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> In `executeDelete`, wrap the per-file `moveToTrash` call (currently inside the `files.forEach { ... }` block when `trashFolderCreated && currentTrashPath != null`) with a try/catch for `TrashRenameUnavailableException`. On catch:
> - Drop into the `deleteFile(filePath)` branch (hard delete) for that single file.
> - Accumulate the file path into a new local list `softDeleteUnavailablePaths`.
> - Do NOT call `errors.add` for these files — successful hard-delete is success, not error.
> After the per-file loop, if `softDeleteUnavailablePaths` is not empty, add a single result-level note (mechanism TBD: see Step 04.3) so UI can show one toast instead of N. Strategic §11 criterion 3 requires deterministic behaviour without silent dup.

**Verification:**

- `Grep -n` — `TrashRenameUnavailableException` referenced in `BaseFileOperationHandler.kt`.
- `Grep -n` — `softDeleteUnavailablePaths` (or equivalent local) declared.
- Target variant compiles.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — `BaseFileOperationHandler.executeDelete` now catches `TrashRenameUnavailableException`, downgrades only the affected file to hard-delete, and carries the affected paths upward as fallback metadata instead of reporting false errors.

---

### Step 04.3 — UX message for the fallback

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`, and the result wiring in `BaseFileOperationHandler.kt` / consumer in `BrowseDeleteManager` / `PlayerDeleteUndoCoordinator`
**Depends on:** Step 04.2

**Prompt for developer:**

> Add a new translatable string `delete_trash_unavailable_fallback_to_hard_delete` in all three locales. EN: "Trash is unavailable for some files — they were deleted permanently." RU: "Корзина недоступна для части файлов — удалены без возможности восстановления." UK: "Кошик недоступний для частини файлів — видалені остаточно."
> Pass the count or presence of `softDeleteUnavailablePaths` up the result chain so `BrowseDeleteManager` / `PlayerDeleteUndoCoordinator` can show a Toast or snackbar. Implementation freedom: extend `FileOperationResult.Success` / `PartialSuccess` with `softDeleteFellBackPaths: List<String> = emptyList()`, or add a sibling property.
> Compliance with `docs/COMMUNICATION_POLICY.md`: the message follows §2 formula for "Operation completed with caveat". Apply the §6 tone checklist before commit.

**Verification:**

- `Grep -n delete_trash_unavailable_fallback_to_hard_delete app_v2/src/main/res/values/strings.xml` returns 1.
- `Grep -n delete_trash_unavailable_fallback_to_hard_delete app_v2/src/main/res/values-ru/strings.xml` returns 1.
- `Grep -n delete_trash_unavailable_fallback_to_hard_delete app_v2/src/main/res/values-uk/strings.xml` returns 1.
- `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "delete_trash_unavailable_fallback_to_hard_delete"` returns exit 0. `expected: exit 0 | actual: <observed>`.
- Strings pass `docs/COMMUNICATION_POLICY.md` §6 tone checklist (manual review).
- UI consumer (`BrowseDeleteManager` or equivalent) references the new string id.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Added `delete_trash_unavailable_fallback_to_hard_delete` in EN/RU/UK, extended `FileOperationResult` with `softDeleteFallbackPaths`, and taught browse/player delete UI to show the caveat message while suppressing misleading undo for permanently deleted files.

---

### Step 04.4 — Verify MANAGE_MEDIA permission flow surfaces in settings

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImpl.kt` (verify only), and possibly tooltip in `OperationsSettingsFragment.kt`
**Depends on:** Steps 04.1, 04.2, 04.3

**Prompt for developer:**

> Read `PermissionRegistryRepositoryImpl.kt` — confirm `MANAGE_MEDIA` is registered with a user-facing label and rationale. The Trash setting tooltip in `OperationsSettingsFragment.kt` (resource `R.string.tooltip_use_trash_message`) should be reviewed: if it does not mention "without MANAGE_MEDIA the Trash may be unavailable on Android 12+ for some folders", add one sentence in all three locales explaining the dependency. Compliance with `docs/COMMUNICATION_POLICY.md` §6 mandatory.
> If the existing string is already explicit, document the choice in commit message and skip the locale edits.

**Verification:**

- `Grep -n MANAGE_MEDIA app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImpl.kt` returns ≥ 1 match.
- If tooltip text edited: `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "tooltip_use_trash_"` returns exit 0.
- Target variant compiles.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verified `MANAGE_MEDIA` is already registered in `PermissionRegistryRepositoryImpl` and updated the Trash tooltip text in EN/RU/UK to explain that some Android 12+ folders require `MANAGE_MEDIA` for Trash availability. Localization check passed.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] No file in "Files Touched" exceeds 700 lines after edit.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

- Soft-delete now degrades deterministically on API ≥ 30 without MANAGE_MEDIA. Phantom-dup is eliminated. Phase 05 cleans up the duplicate MediaStore delete implementations independently of this work.

---

## Rollback Plan

- Revert the phase commits. The previous behaviour (silent phantom dup) returns — accept this only as a temporary measure.
