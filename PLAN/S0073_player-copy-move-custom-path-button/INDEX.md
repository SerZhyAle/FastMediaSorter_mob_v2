# Tactical Plan: S0073 — player-copy-move-custom-path-button

**Strategic spec:** [`../S0073_player-copy-move-custom-path-button.md`](../S0073_player-copy-move-custom-path-button.md)
**Feature:** «..» button in player Copy/Move panels to pick any local folder
**Tier:** 2 — Easy
**Priority:** 50
**Status:** Not started
**Phases:** 4 / 4 done
**Last updated:** 2026-05-04

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | path-operations-extension | — | ✅ Done | 4/4 | [PHASE_01__path-operations-extension.md](PHASE_01__path-operations-extension.md) |
| 02 | player-folder-picker | 01 | ✅ Done | 5/5 | [PHASE_02__player-folder-picker.md](PHASE_02__player-folder-picker.md) |
| 03 | dotdot-button-ui | 02 | ✅ Done | 6/6 | [PHASE_03__dotdot-button-ui.md](PHASE_03__dotdot-button-ui.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 5/5 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Both open research items from strategic §6 are resolved by code inspection:

- [x] **Research §6.1 — SAF URI vs File in FileOperationUseCase** — RESOLVED: `BrowseFolderPickerHandler` converts SAF URI to `java.io.File` via `UriPathResolver.getPath(activity, uri)`. The same utility is available to player code. `FileOperation.Copy/Move` takes `destination: File` — no domain changes needed.
- [x] **Research §6.2 — Restrict to local only** — RESOLVED: no restriction required. The system `OpenDocumentTree` picker lets the user choose; `UriPathResolver.getPath()` returns `null` for non-resolvable URIs; the handler rejects them with a Toast. Behaviour matches Browse.

No unchecked blockers — Phase 01 may start immediately.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated after `.kt` file changes.
- [ ] `/spec-check S0073` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0073`.

---

## Blockers Log

_(none)_

---

## Change Log

- 2026-05-04 — Initial tactical plan authored by `/spec-tech`.
