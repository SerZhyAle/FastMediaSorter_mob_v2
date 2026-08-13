# Tactical Plan: S1178 - launcher-system-status-widgets

**Strategic spec:** [`../S1178_launcher-system-status-widgets.md`](../S1178_launcher-system-status-widgets.md)
**Research inputs:** none
**Feature:** Technical status gadgets on the launcher desktop
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** BlockNeedUserTest
**Phases:** 5 / 5 done
**Last updated:** 2026-08-08

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | metrics-foundation | - | ✅ Done | 4/4 | [PHASE_01__metrics-foundation.md](PHASE_01__metrics-foundation.md) |
| 02 | battery-runtime | 01 | ✅ Done | 4/4 | [PHASE_02__battery-runtime.md](PHASE_02__battery-runtime.md) |
| 03 | network-status | 01 | ✅ Done | 3/3 | [PHASE_03__network-status.md](PHASE_03__network-status.md) |
| 04 | technical-gadgets | 01, 02, 03 | ✅ Done | 6/6 | [PHASE_04__technical-gadgets.md](PHASE_04__technical-gadgets.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Source-set placement contract

- Every metric model, repository, platform source and use case lands in `app_v2/src/main/java/**` and carries no flavor guard - strategic §3.2 forbids flavor branching in shared code.
- Every gadget view, gadget registration and gadget layout lands in `app_v2/src/launcherEnabled/**`, which `app_v2/build.gradle.kts` mounts only for `standard` and `noLegal`. That placement, not a `BuildConfig` field, is what satisfies strategic §11.10.
- User-visible strings live in `app_v2/src/main/res/values*/strings.xml`, as every existing `launcher_gadget_*` key does.

---

## Pre-Implementation Blockers

None. Strategic §6 is closed: the gadget set was fixed by the owner on 2026-07-27, and the four "Найдено при тактике" items (2026-08-02, refreshed 2026-08-05) are resolved findings, not open questions.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/ALL_FEATURES.jsonl` carries the capability record added via `scripts/all_features/add.ps1`. `docs/FEATURES*.md` is not touched here - it is `/skill-release`-owned (CLAUDE.md §11).
- [x] `dev/CHANGELOG.md` has an entry for the ticket, written by `scripts/post-change.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - this ticket adds public classes.
- [ ] `/spec-check S1178` returns `Verified` - **waiting on the device test**. Every one of strategic §11's ten
  criteria is an on-device observation, so the ticket parks at `BlockNeedUserTest` with one probe in place. No
  device was online on 2026-08-08, so the automatic device-test gate was a no-op.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`, after the owner's device run.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1178`.

---

## Blockers Log

- none yet.

---

## Change Log

- 2026-08-05 - Initial tactical plan authored by `/spec-tech`.
- 2026-08-08 - Phases 03, 04 and 05 executed by `/spec-all`. Ticket parked at `BlockNeedUserTest`.
