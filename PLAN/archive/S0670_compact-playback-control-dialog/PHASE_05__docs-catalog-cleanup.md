# Phase 05 - Docs and catalog cleanup

**Strategic spec:** [`../S0670_compact-playback-control-dialog.md`](../S0670_compact-playback-control-dialog.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, 02, 03, 04
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-06-24
**Completed:** 2026-06-24

---

## Objective

Close out the change: localize-audit the new/renamed strings, regenerate the class catalog for the new capability field, and record dev-log entries for every touched file.

---

## Prerequisites

- [ ] Phases 01-04 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CHANGELOG.md` (via `add_to_dev_log.ps1`) | Modified | - |
| `dev/CATALOG/app_v2.jsonl` + `.md` (regenerated, gitignored) | Modified | - |

> No `docs/FEATURES*` edit (strategic §8 = "Без изменений"). No `docs/ALL_FEATURES.jsonl` entry - UX polish of an existing dialog, not a new shippable capability. No settings-manifest sync (Rule 22) - no setting changed.

---

## Steps

### Step 05.1 - String localization audit

**Files:** (validation only - no source edit)
**Depends on:** - start of phase

**Prompt for developer:**

> Run the localization audit for the touched string groups and fix any gap before proceeding: `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "playback_control_tab_"` and `-KeyPrefix "playback_control_speed_"`. Both must exit 0 (EN/RU/UK parity for the renamed audio label and the three speed presets).

**Verification:**

- `check_strings_localized.ps1 -KeyPrefix "playback_control_tab_"` exits 0.
- `check_strings_localized.ps1 -KeyPrefix "playback_control_speed_"` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-06-24 - Verification 2/2 PASS. tab- and speed- prefixes EN/RU/UK parity OK.

---

### Step 05.2 - Catalog sync and dev log

**Files:** `dev/CATALOG/app_v2.jsonl` (regen), `dev/CHANGELOG.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Regenerate the class catalog (new public field on `MediaCapabilities`) and record one dev-log entry per logical change via the post-change facade or batched `close-and-log.ps1 -DevLogs`: `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Then add dev-log entries covering: capability field (Phase 01), resources (Phase 02), layouts (Phase 03), dialog logic (Phase 04). Never hand-edit `dev/CHANGELOG.md`.

**Verification:**

- `catalog_sync.ps1 -Module app_v2` exits 0.
- `Grep` - `S0670` present in `dev/CHANGELOG.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-24 - Verification 2/2 PASS. close-and-log.ps1: 8 dev logs, ALL_FEATURES CHANGE (Video Player), catalog scan+render OK; status -> BlockNeedUserTest.

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] `/build` (standard) green; one VR build (`noLegal`) green.
- [ ] Ticket advanced to `BlockNeedUserTest` with a `-StatusNote` describing device checks: dialog height on audio vs video; 3D tab absent on standard / present on noLegal; audio tab hidden with single track; subs tab hidden with no subtitles; speed presets 0.5/1.5/2.0 apply; all in portrait and landscape.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Device verification via `/spec-test-device S0670`; `/spec-check S0670` flips to `Verified` and removes the `S0670:` debug tag.

---

## Rollback Plan

Documentation/catalog only - regenerate from source; no functional rollback needed.
