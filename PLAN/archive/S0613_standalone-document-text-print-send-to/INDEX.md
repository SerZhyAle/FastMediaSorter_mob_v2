# Tactical Plan: S0613 - standalone-document-text-print-send-to

**Strategic spec:** [`../S0613_standalone-document-text-print-send-to.md`](../S0613_standalone-document-text-print-send-to.md)
**Research inputs:** [`research/01__text-print-path.md`](research/01__text-print-path.md) · [`research/02__document-materialization-reuse.md`](research/02__document-materialization-reuse.md) · [`research/03__flavor-office-print.md`](research/03__flavor-office-print.md)
**Feature:** Document/text print via «Отправить в..» in standalone players
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Implemented - BlockNeedUserTest
**Phases:** 4 / 4 done
**Last updated:** 2026-06-22

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | print-host-seam | - | ✅ Done | 5/5 | [PHASE_01__print-host-seam.md](PHASE_01__print-host-seam.md) |
| 02 | document-host-print | 01 | ✅ Done | 3/3 | [PHASE_02__document-host-print.md](PHASE_02__document-host-print.md) |
| 03 | text-host-print | 01 | ✅ Done | 3/3 | [PHASE_03__text-host-print.md](PHASE_03__text-host-print.md) |
| 04 | docs-catalog-cleanup | 01, 02, 03 | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `✅ Done` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None - all strategic §6 research items are Resolved (see Research inputs). Phase 01 may start immediately.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - NOT edited per-spec; the public showcase is populated only by `/skill-release` from the `ALL_FEATURES` diff (CLAUDE.md §11). The delivered capability is recorded in `docs/ALL_FEATURES.jsonl` (Phase 04).
- [ ] `dev/CHANGELOG.md` has an entry for every logical change.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new `DocumentPrintHost`, renamed `PrintShareFallbackManager`, hosts gain print capability).
- [ ] `/spec-check S0613` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0613`.

---

## Device-test entry points (S0613 tags)

The user-facing print invocation enters through `printMediaFile(..)` on each new host. Per CLAUDE.md "Debug Verification Tags", `/spec-dev` inserts `Timber.d("S0613: <entry>")` at these two entry points as the final edits before the last build, when the ticket enters `BlockNeedUserTest`:

- `DocumentStandaloneActivity.printMediaFile`
- `TextStandaloneActivity.printMediaFile`

No per-phase debug tags - they break the permanent-log ticket-id gate.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-22 - Initial tactical plan authored by `/spec-tech`.
