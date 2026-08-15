# Phase 05 - Docs & catalog cleanup

**Strategic spec:** [`../S0409_welcome-enable-all.md`](../S0409_welcome-enable-all.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-06-12
**Completed:** 2026-06-12

---

## Objective

Record the new user-facing capability in FEATURES (EN/RU/UK), regenerate the class catalog, and close
the dev changelog for every changed file.

---

## Prerequisites

- [ ] Phases 01-04 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | - |
| `docs/FEATURES_RU.md` | Modified | - |
| `docs/FEATURES_UK.md` | Modified | - |

---

## Steps

### Step 05.1 - Add the FEATURES trilingual entry

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one onboarding entry to all three FEATURES files (strategic §8): a welcome-screen "Enable all"
> button that selects the universal profile, enables every available function, requests all permissions
> in sequence, offers the app as default player for every supported type, and finishes setup in one step.
> Match the wording/section of the existing welcome entries in each language file. Do not duplicate an
> existing entry.

**Verification:**

- `Grep` - an "Enable all" / "Включить всё" / "Увімкнути все" onboarding line is present in the respective file.
- The three entries are semantically equivalent across languages.

**Status:** `[x] done`

**Step Log:**

- 2026-06-12 - Verification PASS. Added "One-tap Enable all" / "«Включить всё» в один тап" / "«Увімкнути все» в один дотик" to the Setup & Configuration section in FEATURES.md / _RU / _UK (`[Standard / VR]`, after the onboarding-functionality entry).

---

### Step 05.2 - Regenerate the class catalog

**Files:** (generated index - `dev/CATALOG/app_v2.jsonl` + `.md`, gitignored)
**Depends on:** Step 05.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` so the new
> `ApplyEnableAllSettingsUseCase` and `WelcomeEnableAllManager` are indexed. Set `role` + `status` for
> both new classes via `dev/CATALOG/scripts/set.ps1`.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "*WelcomeEnableAllManager*"` returns the class.
- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "*ApplyEnableAllSettingsUseCase*"` returns the class.

**Status:** `[x] done`

**Step Log:**

- 2026-06-12 - Verification PASS. catalog_sync app_v2 re-rendered (1779 records). Both classes indexed; role + status=new set via set.ps1 (ApplyEnableAllSettingsUseCase = domain use case; WelcomeEnableAllManager = ui orchestrator).

---

### Step 05.3 - Close the dev changelog

**Files:** (appends to `dev/CHANGELOG.md` via script)
**Depends on:** Step 05.2

**Prompt for developer:**

> Ensure every file changed across Phases 01-04 has a `dev/CHANGELOG.md` entry via
> `.\scripts\add_to_dev_log.ps1` (use case, test, two seams, orchestrator, layout, three strings files,
> Activity, three FEATURES files). Add a functionality-log entry for the new user-visible capability via
> `scripts/add_to_functionality_log.ps1`.

**Verification:**

- `Grep` - `dev/CHANGELOG.md` contains an entry referencing `WelcomeEnableAllManager`.
- `Grep` - `dev/CHANGELOG.md` contains an entry referencing `ApplyEnableAllSettingsUseCase`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-12 - Source/strings/layout files dev-logged per step via post-change (19+ S0409 entries). FEATURES trilingual + spec status line + orchestrator tag batched through close-and-log at finalization; functionality log ADD recorded.

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] `docs/FEATURES.md` + `_RU` + `_UK` carry the new entry.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. After this, advance the ticket to BlockNeedUserTest with a
status note describing the on-device sequence to verify (button on page 0 → OTHER profile + all settings
on → sequential permission dialogs → sequential default-player dialogs → setup finishes; plus rotation
mid-sequence resumes; plus lite has no default-player step).

---

## Rollback Plan

Docs/catalog only - revert the FEATURES edits; the catalog is a regenerated local index.
