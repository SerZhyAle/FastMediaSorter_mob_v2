# Tactical Plan: S1206 - launcher-contact-shortcuts-live-contacts

**Strategic spec:** [`../S1206_launcher-contact-shortcuts-live-contacts.md`](../S1206_launcher-contact-shortcuts-live-contacts.md)
**Research inputs:** [`research/as-is-contact-cell-pipeline.md`](research/as-is-contact-cell-pipeline.md)
**Feature:** Live contact name and photo on launcher shortcut cells, backed by `READ_CONTACTS`
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 40
**Status:** Done - awaiting device test
**Phases:** 5 / 5 done
**Last updated:** 2026-08-08

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | live-contact-source | - | ✅ Done | 4/4 | [PHASE_01__live-contact-source.md](PHASE_01__live-contact-source.md) |
| 02 | live-cell-visual | 01 | ✅ Done | 3/3 | [PHASE_02__live-cell-visual.md](PHASE_02__live-cell-visual.md) |
| 03 | permission-copy | - | ✅ Done | 3/3 | [PHASE_03__permission-copy.md](PHASE_03__permission-copy.md) |
| 04 | permission-at-pin | 03 | ✅ Done | 2/2 | [PHASE_04__permission-at-pin.md](PHASE_04__permission-at-pin.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 5/5 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Strategic §4 carries two questions and both are Resolved (quiz 2026-08-06). No open research items.

- None.

---

## Scope boundaries

Named here because each was considered and excluded, so no phase silently grows to cover it.

- **Choosing the number at tap time** for a contact with several numbers - strategic §3.3 puts it outside
  the first iteration.
- **Live messaging channels** - §3.3 scopes the first iteration to name and photo; channels stay as the
  snapshot pinned them.
- **A visible "contact deleted" indicator on the cell** - §3.3 decides the *behaviour* (fall back to the
  stored snapshot) but records no placement for an indicator, and this plan does not invent one. The
  feature is complete without it.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped; `/skill-release` owns them, this ticket records its
      capability in `docs/ALL_FEATURES.jsonl` instead.
- [x] `dev/CHANGELOG.md` has entry for every modified file - one row naming the set of 14.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - this ticket adds a public class.
- [ ] `/spec-check S1206` returns `Verified` - **blocked on the device test**, not on the code. See below.
- [ ] Strategic spec `Status:` advanced by `/spec-check`.

## Why this stops at BlockNeedUserTest

The two things the ticket delivers - a live photograph on a cell and an explanation before the system
permission dialog - are only observable on the launcher desktop, and that desktop is an emulator
ceiling: `LauncherHomeActivity` ships `android:enabled="false"` and the shell is refused
`pm enable` (`SecurityException: Shell cannot change component state`), so the screen exists only
after the app itself has been given the HOME role through onboarding. The live photo additionally
needs an address-book contact that has a photograph, and the rename check needs that contact edited
in the system contacts app. An emulator run would therefore end INCONCLUSIVE after a full
build-install-drive cycle, which is worse than not running it - it also tempts a premature
`/spec-check` that would strip the two debug probes before anyone had seen them fire.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1206`.

---

## Blockers Log

- none yet.

---

## Change Log

- 2026-08-08 - Initial tactical plan authored by `/spec-tech`.
