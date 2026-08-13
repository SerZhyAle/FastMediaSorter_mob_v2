# Tactical Plan: S0270 - workspace-noise-and-lookup-strategy

**Strategic spec:** [`../S0270_workspace_noise_and_lookup_strategy.md`](../S0270_workspace_noise_and_lookup_strategy.md)
**Feature:** Workspace noise reduction and lookup rule split
**Tier:** 1 - Major (workspace environment)
**Priority:** 70
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-05-20

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | workspace-excludes | - | ✅ Done | 2/2 | [PHASE_01__workspace-excludes.md](PHASE_01__workspace-excludes.md) |
| 02 | research-order-rules | 01 | ✅ Done | 2/2 | [PHASE_02__research-order-rules.md](PHASE_02__research-order-rules.md) |
| 03 | catalog-guidance | 02 | ✅ Done | 2/2 | [PHASE_03__catalog-guidance.md](PHASE_03__catalog-guidance.md) |
| 04 | docs-catalog-cleanup | 01,02,03 | ✅ Done | 2/2 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research:** Keep exclude-list sync manual for now. No guard/script in this ticket. See strategic §6.1.
- [x] **Research:** Freeze the heavy-directory exclude set to `temp/`, `DOWNLOADS/`, `.venv/`, `logs/`, `.kotlin/`, `**/node_modules/**`. No extra tails justified by the fresh measurement. See strategic §6.2.
- [x] **Research:** Present the `CLAUDE.md` exact-match rule as a hybrid list with two short bullets and inline examples. See strategic §6.3.
- [x] **Research:** Replace the old `Never use find/Glob` sentence with a narrowed misuse-context rule only. See strategic §6.4.
- [x] **Research:** Keep `.vscode/settings.json` for editor defaults and `CLAUDE.md` for all agents; no separate source-of-truth file. See strategic §6.5.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `.vscode/settings.json` excludes the agreed heavy directories in `files.exclude`, `search.exclude`, `java.import.exclusions`, and `files.watcherExclude`.
- [x] `CLAUDE.md` "Research Order" explicitly separates semantic lookup from exact-match lookup and includes the agreed default `rg` exclude pattern.
- [x] `dev/CATALOG/README.md` documents `query.ps1` for semantic queries and direct `.jsonl` reads for narrow exact-match lookups, while keeping writes script-only.
- [x] `dev/CHANGELOG.md` has an entry for every modified spec/doc/config file.
- [x] `/spec-check S0270` returns `Verified`.
- [x] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/4 done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check <Sxxxx>`.

---

## Blockers Log

- 2026-05-20 - Initial tactical plan created with open research blockers from strategic §6. Next: resolve blockers, then run `/spec-dev S0270`.
- 2026-05-20 - Research blockers resolved inline during `/spec-all`; implementation completed and audit passed.

---

## Change Log

- 2026-05-20 - Initial tactical plan authored by `/spec-tech`.
- 2026-05-20 - `/spec-all` resolved blockers, applied workspace/doc updates, and closed the ticket as `Verified`.
