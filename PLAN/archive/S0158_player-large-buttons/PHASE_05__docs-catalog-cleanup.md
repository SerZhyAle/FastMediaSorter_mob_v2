# Phase 05 — Docs & Catalog Cleanup

**Strategic spec:** [`../S0158_player-large-buttons.md`](../S0158_player-large-buttons.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04
**Blocks:** nothing — final phase
**Steps done:** 3 / 3
**Started:** 2026-05-13
**Completed:** 2026-05-13

---

## Objective

Update user-facing feature documentation in all three locales, regenerate the class catalog, and verify the dev changelog is complete.

---

## Prerequisites

- [ ] Phase 04 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | — |
| `docs/FEATURES_RU.md` | Modified | — |
| `docs/FEATURES_UK.md` | Modified | — |
| `dev/CATALOG/app_v2.jsonl` | Modified (auto) | — |
| `dev/CATALOG/app_v2.md` | Modified (auto) | — |

---

## Steps

### Step 05.1 — Update `docs/FEATURES.md` + mirrors

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** Phase 04 done

**Prompt for developer:**

> Read `docs/COMMUNICATION_POLICY.md` §2 before writing. Invoke `/doc-update` skill to update all three locale files with the feature text from strategic §8:
>
> - EN: `Player interface: "Big Buttons Mode" scales player controls and the top toolbar to full screen width with 2× height — designed for car head units and one-handed use while driving.`
> - RU: `Интерфейс проигрывателя: «Режим большие кнопки» переводит кнопки управления и верхнюю панель в полноширинный вид с двойной высотой — для автомагнитол и управления одной рукой.`
> - UK: `Інтерфейс програвача: «Режим великі кнопки» переводить кнопки керування та верхню панель у повноширинний вигляд з подвійною висотою — для автомагнітол та керування однією рукою.`
>
> Place the bullet in the **Player Interface** section of each file.

**Verification:**

- `Grep` — `Big Buttons Mode` present in `docs/FEATURES.md`.
- `Grep` — `Режим большие кнопки` present in `docs/FEATURES_RU.md`.
- `Grep` — `Режим великі кнопки` present in `docs/FEATURES_UK.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification 3/3 PASS. Big Buttons Mode bullet added to FEATURES.md, FEATURES_RU.md, FEATURES_UK.md. Dev log recorded.

---

### Step 05.2 — Regenerate class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Phase 04 done

**Prompt for developer:**

> Run:
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1   -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
>
> Then set role + status for the new class:
> ```powershell
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class PlayerBigButtonsModeManager -Role "ui-helper" -Status "active"
> ```
>
> Commit updated `app_v2.jsonl` and `app_v2.md` together with this phase's code changes.

**Verification:**

- `Grep` — `PlayerBigButtonsModeManager` present in `dev/CATALOG/app_v2.md`.
- `Grep` — `PlayerBigButtonsModeManager` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification 2/2 PASS. PlayerBigButtonsModeManager present in app_v2.jsonl and app_v2.md with role=ui-helper. Dev log recorded.

---

### Step 05.3 — Dev changelog completeness check

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Steps 05.1 and 05.2

**Prompt for developer:**

> Verify that `dev/CHANGELOG.md` contains entries for all files modified across Phases 01–05. Run `add_to_dev_log.ps1` for any file not yet logged. Files to check:
>
> - `PlayerLayoutModePrefs.kt`
> - `CommandPanelLayoutPlanner.kt`
> - `res/values/strings.xml` + `-ru` + `-uk`
> - `PlayerBigButtonsModeManager.kt`
> - `res/layout/fragment_settings_playback.xml` + `layout-land` counterpart
> - `PlaybackSettingsFragment.kt`
> - `PlayerManagerInitializer.kt`
> - `CommandPanelController.kt`
> - `PlayerControlsSetupManager.kt`
> - `docs/FEATURES.md` + `_RU.md` + `_UK.md`
> - `dev/CATALOG/app_v2.jsonl` + `app_v2.md`

**Verification:**

- Manual: open `dev/CHANGELOG.md`, confirm entries for each file listed above are present.

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — All 12 required files confirmed present in dev/CHANGELOG.md (12/12 ✓). No gaps.

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] `Grep` — `PlayerBigButtonsModeManager` in `dev/CATALOG/app_v2.md`.
- [ ] `Grep` — `Big Buttons Mode` in `docs/FEATURES.md`.
- [ ] Dev log entries for all Phase 01–04 files confirmed complete.
- [ ] Run `/spec-check S0158` — expect `Verified` or `Partial` with explicit findings.

---

## Handoff Notes to Next Phase

Final phase — see `INDEX.md` Completion Gate.

---

## Rollback Plan

Revert phase commit(s) — docs and catalog only; no runtime code changed.
