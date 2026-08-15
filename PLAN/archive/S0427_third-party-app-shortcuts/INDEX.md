# Tactical Plan: S0427 - third-party-app-shortcuts

**Strategic spec:** [`../S0427_third-party-app-shortcuts.md`](../S0427_third-party-app-shortcuts.md)
**Research inputs:** none on disk - the S0404 research folder was archived with its epic; findings are carried in strategic §8.
**Feature:** published app-shortcuts of installed apps on the launcher home surface
**Tier:** 4 - Strategic (ad-hoc)
**Priority:** 40
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-07-24

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | shortcut-data-source | - | ✅ Done | 3/3 | [PHASE_01__shortcut-data-source.md](PHASE_01__shortcut-data-source.md) |
| 02 | shortcut-popup | 01 | ✅ Done | 4/4 | [PHASE_02__shortcut-popup.md](PHASE_02__shortcut-popup.md) |
| 03 | long-press-wiring | 02 | ✅ Done | 3/3 | [PHASE_03__long-press-wiring.md](PHASE_03__long-press-wiring.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- none - strategic §4.1 and §4.2 are both Resolved.

---

## Platform notes binding on every phase

- The launcher surface is mounted only by `standard` and `noLegal` (`src/launcherEnabled`), both minSdk 26, so the API-25 floor of `LauncherApps.getShortcuts` is always met. No `SDK_INT` guard is needed; `legacy` never reaches this code because it mounts `src/launcherDisabled`.
- `LauncherApps.getShortcuts` / `startShortcut` throw `SecurityException` the moment the app stops being the default launcher. Every call site checks `hasShortcutHostPermission()` first AND catches `SecurityException`, because the role can be revoked between the check and the call.
- Query flags are `FLAG_MATCH_MANIFEST or FLAG_MATCH_DYNAMIC` only (strategic §4.2).
- Shortcut launches are not written to `LauncherJournalRepository`: the journal stores `LauncherCellCommand` values and a shortcut is not one.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip: strategic has no FEATURES sentence; `/skill-release` owns the showcase.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated - new public classes are added.
- [ ] `/spec-check S0427` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0427`.

---

## Blockers Log

- none yet.

---

## Change Log

- 2026-07-24 - Initial tactical plan authored by `/spec-tech`.
