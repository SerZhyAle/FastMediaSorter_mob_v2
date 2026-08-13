# Tactical Plan: S0463 - send-to-commands-toggle-labels

**Strategic spec:** [`../S0463_send-to-commands-toggle-labels.md`](../S0463_send-to-commands-toggle-labels.md)
**Feature:** Unique labels + description subtitles + help buttons for "Send file to.." settings toggles
**Tier:** 2 - Easy (ad-hoc)
**Priority:** 50
**Status:** Implemented
**Phases:** 1 / 1 done
**Last updated:** 2026-06-16

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | labels-and-help | - | ✅ Done | 4/4 | [PHASE_01__labels-and-help.md](PHASE_01__labels-and-help.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked`

---

## Pre-Implementation Blockers

- [x] `ShareTargetModule` and registry populated (S0459 Phases 01–04, confirmed Done).
- [x] `SettingsToggleRow` has `setHelp(@StringRes titleRes, @StringRes messageRes)` method (confirmed present in widget).
- [x] `PlaybackSettingsFragment.setupSendCommandsGroup()` builds toggles from the registry (confirmed present).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] No changes to `docs/FEATURES.md` (strategic §8: no new user-facing feature, only UI improvement).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (model field added).
- [ ] `/spec-check S0463` returns `Verified`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-16 - Initial tactical plan authored inline (via spec-dev S0463).
