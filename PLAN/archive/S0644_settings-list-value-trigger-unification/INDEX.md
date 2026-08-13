# Tactical Plan: S0644 - settings-list-value-trigger-unification

**Strategic spec:** [`../S0644_settings-list-value-trigger-unification.md`](../S0644_settings-list-value-trigger-unification.md)
**Feature:** List-value trigger row etalon (chevron right after text, no full-width stretch)
**Tier:** UI consistency
**Priority:** 55
**Status:** Done
**Phases:** 1 / 1 done
**Last updated:** 2026-06-24

> **Scope:** tactical, English, developer handoff. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | row-etalon-widget | - | ✅ Done | 2/2 | [PHASE_01__row-etalon-widget.md](PHASE_01__row-etalon-widget.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- None. S0646 §6.1 dialog-vs-inline fork resolved (tap-row + ListSelectionDialog); owner confirmed the value-row etalon (chevron right after the text) on 2026-06-24, overriding the S0618 right-edge placement.

---

## Design note (owner decision 2026-06-24)

The value-row etalon = chevron `>` sits right after the text, content hugs the left, no full-width stretch; the whole row stays a full-width click target. This is the same hug-left layout S0645 ships for navigation rows (`applyInlineLayout`), minus dropping the touch-target band. Implemented once at the `SettingsSelectionRow` widget level, so every value-selection row inherits it - no per-row layout edits. Rows with a subtitle keep the full-width text group (subtitle not truncated); navigation rows collapse via `setNavigationMode`.

---

## Completion Gate

- [ ] Phase 01 ✅ Done.
- [ ] `dev/CHANGELOG.md` entry.
- [ ] `/spec-check S0644` returns `Verified` (after device visual confirmation).

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-24 - Tactical plan authored + implemented; S0646 unblock applied.
