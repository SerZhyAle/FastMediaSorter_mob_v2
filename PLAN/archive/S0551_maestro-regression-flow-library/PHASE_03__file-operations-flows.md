# Phase 03 - File Operations Flows

**Strategic spec:** [`../S0551_maestro-regression-flow-library.md`](../S0551_maestro-regression-flow-library.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress
**Depends on:** Phase 01
**Blocks:** Phase 06, 07
**Steps done:** 5 / 5
**Started:** -
**Completed:** -

---

## Objective

Real-oracle flows for copy, move, rename, soft-delete (trash), undo, and overwrite against the seeded `Ops/src` + `Ops/dst` tree, using the `FileOperationProgressDialog: Completed` marker where present and element assertions where not.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Seeded `Ops/src`, `Ops/dst` registered as LOCAL resources; trash enabled in settings.
- [ ] Marker/id reference: `research/02` (operation-bar ids `btnCopy/btnMove/btnRename/btnDelete/btnUndo`; popup items are text-only).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `maestro/critical/file_operations.yaml` | Modified (rewrite) | ≤ 120 |
| `maestro/features/files/file_rename.yaml` | New | ≤ 70 |
| `maestro/features/files/file_trash_undo.yaml` | New | ≤ 90 |
| `maestro/features/files/file_overwrite.yaml` | New | ≤ 80 |

---

## Steps

### Step 03.1 - Rewrite `file_operations` (copy + move) with real oracles

**Files:** `maestro/critical/file_operations.yaml`
**Depends on:** - start of phase

**Prompt for developer:**

> Replace the all-`optional` body. Copy `IMG_copy_01.png` from `Ops/src` to `Ops/dst`: select the file, tap `btnCopy`, pick destination, confirm; assert success by reopening `Ops/dst` and `assertVisible` the copied filename, and assert the source still present (matrix 1.3). Then move `IMG_move_01.png`: tap `btnMove`, confirm; assert it appears in `Ops/dst` and is `assertNotVisible` in `Ops/src` (1.4). Use exact ids; no regex long-press text.

**Verification:**

- `Grep` - `btnCopy` and `btnMove` present, not under `optional: true`.
- `Grep` - both `assertVisible` and `assertNotVisible` present (move proves removal).
- `Grep` - `.*` index-0 text locator removed (zero hits of `text: ".*"`).

**Status:** `[x]` done

---

### Step 03.2 - New `file_rename` flow

**Files:** `maestro/features/files/file_rename.yaml`
**Depends on:** Step 03.1

**Prompt for developer:**

> Select `ops_rename_me.png`, tap `btnRename`, input a new name, confirm. Assert the new name `assertVisible` and the old name `assertNotVisible`, and no duplicate (matrix 1.5). Rename has no completion marker - assert purely by element.

**Verification:**

- `Glob` - `maestro/features/files/file_rename.yaml` exists.
- `Grep` - `btnRename` present.
- `Grep` - `assertNotVisible` present (old name gone).

**Status:** `[x]` done

---

### Step 03.3 - New `file_trash_undo` flow

**Files:** `maestro/features/files/file_trash_undo.yaml`
**Depends on:** Step 03.2

**Prompt for developer:**

> Soft-delete `ops_delete_soft.png` via `btnDelete` with trash on; assert it disappears from the list and `btnUndo` becomes visible (matrix 1.6). Then delete `ops_undo_me.png`, tap `btnUndo`, assert it is restored (`assertVisible`) (1.7). Element-only oracle (no single undo marker).

**Verification:**

- `Glob` - `maestro/features/files/file_trash_undo.yaml` exists.
- `Grep` - `btnDelete` and `btnUndo` present.
- `Grep` - `assertVisible` after undo present.

**Status:** `[x]` done

---

### Step 03.4 - New `file_overwrite` flow

**Files:** `maestro/features/files/file_overwrite.yaml`
**Depends on:** Step 03.3

**Prompt for developer:**

> Copy `ops_overwrite_A.png` from `Ops/src` to `Ops/dst` where it already exists; assert the overwrite-confirm dialog appears (matrix 1.9) by its dialog text, then confirm and assert completion (`FileOperationProgressDialog: Completed` marker wait or destination file still present).

**Verification:**

- `Glob` - `maestro/features/files/file_overwrite.yaml` exists.
- `Grep` - an overwrite/confirm dialog `assertVisible` present.

**Status:** `[x]` done

---

### Step 03.5 - Register the new flows' resources in the run config note

**Files:** `maestro/features/files/file_overwrite.yaml`
**Depends on:** Step 03.4

**Prompt for developer:**

> At the top of each new files flow, add a comment block listing the exact seeded resources/files it requires (`Ops/src`, `Ops/dst`, specific filenames) so a failing flow points at its precondition, mirroring the `3d-video` precondition comments. This is the only allowed non-step doc touch and stays inside the YAML.

**Verification:**

- `Grep` - `Ops/src` referenced in a comment in `maestro/features/files/file_overwrite.yaml`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` is `[x] done`.
- [ ] `pwsh -NoProfile -File maestro/run-tests.ps1 -Suite features\files -Json` → suite pass on a clean seeded emulator (plus `critical/file_operations`).
- [x] `Grep` for `text: ".*"` across these files returns zero hits.
- [x] Dev log entry added for every file in Files Touched.

**Validation note:** static implementation checks pass. Full on-device suite proof remains pending.

---

## Handoff Notes to Next Phase

File-op oracle pattern set (marker `FileOperationProgressDialog: Completed` + element asserts for marker-less ops). Player phase (04) is independent of this phase - both depend only on Phase 01.

---

## Rollback Plan

Revert the phase commit; `critical/file_operations.yaml` returns to prior form, three new flows disappear. No app surface touched.
