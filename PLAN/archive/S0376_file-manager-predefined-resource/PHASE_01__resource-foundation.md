# Phase 01 - Resource Foundation

**Strategic spec:** [../S0376_file-manager-predefined-resource.md](../S0376_file-manager-predefined-resource.md)
**Tactical index:** [INDEX.md](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Started:** 2026-06-07
**Completed:** 2026-06-07

---

## Objective

Introduce the shared predefined-resource contract, idempotent creation flow, and browse ordering needed for the All Files entry point.

---

## Prerequisites

- [x] No pre-implementation blockers remain unchecked in `INDEX.md`.
- [x] Working tree is on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/EnsureAllFilesPredefinedResourceUseCase.kt` | New | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ProfileImpliesAllFilesUseCase.kt` | New | ≤ 160 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/AddResourceUseCase.kt` | Modified | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/loading/BrowseLoadingManager.kt` | Modified | ≤ 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseNavigationManager.kt` | Modified | ≤ 520 |

---

## Steps

### Step 01.1 - Add the reusable predefined-resource creator

**Files:** `EnsureAllFilesPredefinedResourceUseCase.kt`, `AddResourceUseCase.kt`
**Depends on:** - start of phase
**Prompt for developer:** Add a dedicated use case that creates the predefined local `All Files` resource exactly once, inserts it at the first ordinary display slot, and keeps later triggers idempotent. Reuse `AddResourceUseCase` by adding an explicit top-insert path instead of duplicating repository ordering logic.
**Verification:** The new use case returns an existing id when the predefined resource is already present, otherwise creates a local all-files resource with newest-first sorting and top-of-list insertion.
**Status:** `[x] done`

**Step Log:**

- 2026-06-07 - PASS. Added `EnsureAllFilesPredefinedResourceUseCase` plus top-insert ordering support in `AddResourceUseCase`.

### Step 01.2 - Add the profile-intent detector

**Files:** `ProfileImpliesAllFilesUseCase.kt`
**Depends on:** 01.1
**Prompt for developer:** Add a small profile helper use case that reads the preset CSV overrides and returns `true` when a selected device profile enables the global all-files shortcut. Keep the decision data-driven from the preset matrix rather than hard-coding profile ids.
**Verification:** The helper reads the preset source and resolves `true` only for profiles whose preset sets `allFiles=TRUE`.
**Status:** `[x] done`

**Step Log:**

- 2026-06-07 - PASS. Added CSV-driven `ProfileImpliesAllFilesUseCase` so profile triggers reuse the preset matrix instead of hard-coded profile ids.

### Step 01.3 - Align subfolder browse sorting with the file-manager contract

**Files:** `BrowseLoadingManager.kt`, `BrowseNavigationManager.kt`
**Depends on:** 01.2
**Prompt for developer:** Ensure subfolder-mode directory listings keep folders on top but still sort regular files by the resource sort mode, and pass the resource hidden-file flag through folder navigation reloads so the predefined resource behaves consistently after entering child folders.
**Verification:** Subfolder-mode loads call the sorter even for small folders, and directory reloads no longer hard-code `showHiddenFiles=false`.
**Status:** `[x] done`

**Step Log:**

- 2026-06-07 - PASS. Subfolder-mode browse now re-sorts small folder listings by the resource sort mode and preserves the hidden-file flag during folder navigation reloads.

---

## Phase Done Criteria

- [x] The predefined creator is idempotent and can insert at the top of ordinary resources.
- [x] Profile-to-all-files detection is data-driven from the preset matrix.
- [x] Subfolder-mode browse keeps newest-first file ordering and respects hidden-file visibility.
