# Tactical Plan: S0360 - drawing-editor-delete-file

**Strategic spec:** [`../S0360_drawing-editor-delete-file.md`](../S0360_drawing-editor-delete-file.md)
**Feature:** "Delete file" action in the drawing-editor overflow menu
**Tier:** 2 - Easy (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-06-05

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | delete-flow | - | ✅ Done | 2/2 | [PHASE_01__delete-flow.md](PHASE_01__delete-flow.md) |
| 02 | editor-menu | 01 | ✅ Done | 4/4 | [PHASE_02__editor-menu.md](PHASE_02__editor-menu.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 3/3 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Strategic §6 research item is resolved by code inspection - no open blocker.

- [x] **Research:** trash / restore inheritance (strategic §6.1). Resolved: the player single-file delete already routes through `PlayerDeleteUndoCoordinator` -> `FileOperationUseCase`, which soft-deletes local files to `.trash` (restorable via the app trash) and hard-deletes network files. The editor delete reuses this coordinator, so it inherits the existing trash behavior as-is.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (strategic §8 mandates a FEATURES bullet).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S0360` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0360`.

---

## Design Decisions (resolved from strategic spec)

- **Delete target:** the player's current source file (`PlayerState.currentFile`) on the current resource - the image the editor was opened over. This is the "исходник" the owner wants to discard.
- **Reuse:** delete is delegated to the existing `PlayerDeleteUndoCoordinator.deleteCurrentFile()` (ADR-1) - no new delete path. Trash/undo/network handling inherited.
- **Confirmation:** mandatory before delete (strategic §2.4 + risk mitigation), regardless of Safe-Mode setting. Reuses `confirm_delete_title` / `confirm_delete_message`.
- **Post-delete navigation:** on success the editor closes and the player finishes, returning the user to browse (strategic §2.3 / §5.1 / §11.4). Implemented via a `finishOnSuccess` path that emits `FinishActivity` instead of advancing to the next file. The deleted file disappears from browse through the existing journal-driven `FileModified` refresh.
- **On failure:** the editor stays open and an error message is shown (strategic §5.1) - the coordinator's failure branch keeps the activity alive.
- **Item visibility:** the "Delete file" item is shown only when a source file is present (`currentFile != null`); read-only resources are guarded at click time with the existing `error_read_only` message.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-05 - Initial tactical plan authored by `/spec-tech`.
