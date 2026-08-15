# Phase 04 - Docs & catalog cleanup

**Strategic spec:** [`../S0435_settings-os-interaction-default-player.md`](../S0435_settings-os-interaction-default-player.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03 (and all prior)
**Blocks:** none
**Steps done:** 4 / 4
**Started:** 2026-06-15
**Completed:** 2026-06-15

---

## Objective

Regenerate the class catalog, record the new user-facing capability in the trilingual FEATURES docs, and close out the mandatory post-change bookkeeping.

---

## Prerequisites

- [ ] Phases 01-03 are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | n/a |
| `docs/FEATURES_RU.md` | Modified | n/a |
| `docs/FEATURES_UK.md` | Modified | n/a |
| `dev/CATALOG/app_v2.jsonl` (regenerated, gitignored) | Modified | n/a |

---

## Steps

### Step 04.1 - Regenerate catalog and set the new class role/status

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Then set role + status for the new class via `dev/CATALOG/scripts/set.ps1` for `DefaultPlayerSettingsManager` (role: settings UI helper that gates and dispatches default-player registration; status: active).

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "*DefaultPlayerSettingsManager*"` returns the class.

**Status:** `[x]` done

**Step Log:**

- 2026-06-15 - Verification PASS. Catalog scanned (post-change); set.ps1 applied role + status=new; query returns the class (ui layer, 54 loc).

---

### Step 04.2 - FEATURES trilingual update

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add one sentence (each locale, in the settings/playback area) stating that the app can now be set as the default handler for images, audio, video, and documents directly from the playback settings page, without the welcome screen. Match the existing FEATURES tone. Do not duplicate an existing entry.

**Verification:**

- `Grep` - the new sentence's anchor phrase present in each of `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-15 - Verification PASS. Added "Set as default app from settings" bullet to section 16 in EN/RU/UK (1 hit each).

---

### Step 04.3 - Dev changelog for all changed files

**Files:** (bookkeeping only)
**Depends on:** Step 04.2

**Prompt for developer:**

> Ensure `.\scripts\add_to_dev_log.ps1` entries exist for every file changed across Phases 01-04 (strings x6, layouts x2, manager, fragment, FEATURES x3) if not already logged at phase close.

**Verification:**

- `Grep` - `DefaultPlayerSettingsManager` present in `dev/CHANGELOG.md`.
- `Grep` - `fragment_settings_playback` present in `dev/CHANGELOG.md` dated entries for this work.

**Status:** `[x]` done

**Step Log:**

- 2026-06-15 - Verification PASS. CHANGELOG has DefaultPlayerSettingsManager + fragment_settings_playback entries (logged via per-step post-change).

---

### Step 04.4 - Neuroslop + localization gate

**Files:** (validation only)
**Depends on:** Step 04.3

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/quality/assert-neuroslop.ps1` and `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_default_player_"`. Fix any reported issue in the touched files.

**Verification:**

- `assert-neuroslop.ps1` exits 0.
- `check_strings_localized.ps1 -KeyPrefix "settings_default_player_"` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-06-15 - Verification PASS. assert-neuroslop exit 0; check_strings_localized -KeyPrefix settings_default_player_ exit 0 (7 keys EN/RU/UK).

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] FEATURES trilingual updated.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. After this, `/spec-dev` advances S0435 to `BlockNeedUserTest` (debug tag stays); device verification per strategic §11 follows.

---

## Rollback Plan

Revert phase commit(s) - docs/catalog only; catalog is regenerated from source, not authoritative.
