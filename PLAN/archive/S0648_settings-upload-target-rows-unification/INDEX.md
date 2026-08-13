# Tactical Plan: S0648 - settings-upload-target-rows-unification

**Strategic spec:** [`../S0648_settings-upload-target-rows-unification.md`](../S0648_settings-upload-target-rows-unification.md)
**Feature:** Two upload-target rows match the camera/video folder selector pattern
**Tier:** UI consistency
**Priority:** 50
**Status:** Done
**Phases:** 1 / 1 done
**Last updated:** 2026-06-24

> **Scope:** tactical, English. Rationale in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | upload-target-button-pattern | - | ✅ Done | 3/3 | [PHASE_01__upload-target-button-pattern.md](PHASE_01__upload-target-button-pattern.md) |

---

## Owner decision (2026-06-24)

S0648 vs S0644 etalon conflict resolved by owner: the **button pattern wins** for these two upload-target rows. They migrate from `SettingsSelectionRow` (tap-row + chevron) to the camera-folder pattern (label + value text + "Select" button), matching the "Camera, microphone and other functions" folder selectors. Other value rows keep the S0644 tap-row etalon; destination/folder-target rows use the button pattern.

---

## Pre-Implementation Blockers

- None. S0644 (row-etalon) reached BlockNeedUserTest; owner confirmed the button pattern for these rows.

---

## Completion Gate

- [ ] Phase 01 ✅ Done.
- [ ] `dev/CHANGELOG.md` entry.
- [ ] Settings manifest/annotations/reference regenerated (old row keys -> new button keys).
- [ ] `/spec-check S0648` returns `Verified` (after device visual confirmation).

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-24 - Tactical plan authored + implemented; S0644 unblock applied.
