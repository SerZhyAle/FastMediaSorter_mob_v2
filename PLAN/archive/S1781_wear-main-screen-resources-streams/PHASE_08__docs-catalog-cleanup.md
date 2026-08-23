# Phase 08 - Docs, catalog and closure

**Strategic spec:** [`../S1781_wear-main-screen-resources-streams.md`](../S1781_wear-main-screen-resources-streams.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all phases (01-07)
**Blocks:** none - final phase
**Steps done:** 5 / 5
**Started:** 2026-08-18
**Completed:** 2026-08-18

---

## Objective

Final phase of this ticket: regenerate both class catalogs, resync the settings documentation for the settings this ticket added on both the watch and the phone, record the delivered capability in the EN-only feature inventory, and run the closure facade over the whole changed set. `docs/FEATURES*.md` is deliberately not touched here - it is populated only by `/skill-release` from the `ALL_FEATURES` diff, never per-spec.

---

## Prerequisites

- [ ] Phase 01 through Phase 07 are all ✅ Done.
- [ ] Every step in every prior phase passed its own Verification.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/wear.jsonl` | Modified (generated) | n/a |
| `dev/CATALOG/app_v2.jsonl` | Modified (generated) | n/a |
| `docs/settings/settings-manifest.json` | Modified (generated) | n/a |
| `docs/SETTINGS_REFERENCE.md` | Modified (generated) | n/a |
| `docs/SETTINGS_REFERENCE_RU.md` | Modified (generated) | n/a |
| `docs/SETTINGS_REFERENCE_UK.md` | Modified (generated) | n/a |
| `docs/settings/settings-annotations.json` | Modified | n/a |
| `docs/ALL_FEATURES.jsonl` | Modified (appended) | n/a |
| `dev/CHANGELOG.md` | Modified (via post-change facade) | n/a |

---

## Steps

### Step 08.1 - Regenerate both class catalogs

**Files:** `dev/CATALOG/wear.jsonl`, `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module wear` and `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Set `role`/`status` for every class this ticket introduced - `HomeSection`, `HomeSectionCatalog`, `HomeViewModel`, `ScreenSettingsScreen`, `HomeCommandBar`, `KeepScreenOnEffect`, `WearResourceSelectionRepositoryImpl`, `WearResourceSelectionActivity`, `WearResourceSelectionAdapter` and any other new class from Phases 02-07 - via `dev/CATALOG/scripts/set.ps1`, so the catalog does not carry orphaned-role placeholders for real, wired-up classes.

**Why:**

Research order rule 3 makes the class catalog the required first stop before a global grep on this codebase; a catalog left stale after a ticket this size defeats that rule for every agent working in the `wear` or `app_v2` module afterward.

**Verification:**

- `Grep` - `HomeSectionCatalog` present in `dev/CATALOG/wear.jsonl`.
- `Grep` - `WearResourceSelectionRepositoryImpl` present in `dev/CATALOG/app_v2.jsonl`.
- Exit code of both `catalog_sync.ps1` invocations is 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - Step 08.1: both catalogs regenerated (wear 138 records, app_v2 2925) and every class this ticket introduced given a real role plus status=new - HomeSectionCatalog, HomeSection/HomeSectionId/HomeSectionVisibility, HomeCommandBar, KeepScreenOnEffect, GridColumnFit, WearViewMode, ScreenSettingsScreen, NetworkSourceGrid on the watch; WearResourceSelectionRepositoryImpl, Activity, Adapter and ViewModel on the phone. Verified: catalog_sync exit 0 for both modules; HomeSectionCatalog present in wear.jsonl, WearResourceSelectionRepositoryImpl present in app_v2.jsonl, KeepScreenOnEffect and NetworkSourceGrid both indexed.

---

### Step 08.2 - Regenerate the settings documentation

**Files:** `docs/settings/settings-manifest.json`, `docs/SETTINGS_REFERENCE.md`, `docs/SETTINGS_REFERENCE_RU.md`, `docs/SETTINGS_REFERENCE_UK.md`, `docs/settings/settings-annotations.json`
**Depends on:** Step 08.1

**Prompt for developer:**

> This ticket adds settings on both sides: the watch gets a new "Screen" section (view mode, keep-awake) and the Companion sheet gets matching controls plus the resource-selection screen. Per CLAUDE.md Rule 22, regenerate `docs/settings/settings-manifest.json` and all three `docs/SETTINGS_REFERENCE*.md` locales, and update `docs/settings/settings-annotations.json` for every new entry. Run `scripts/quality/assert-settings-doc-sync.ps1` afterward and fix any divergence it reports before moving on.

**Why:**

CLAUDE.md Rule 22 makes this mandatory for any settings change "including one hosted in a dialog, bottom sheet, or wizard page" - the Companion sheet's new controls qualify exactly as much as the watch's new Screen section does; skipping it here is the documented failure mode the rule exists to prevent (S1035/S1313).

**Verification:**

- `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1` - exit 0.
- `Grep` - the new Screen-section settings present in `docs/SETTINGS_REFERENCE.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - Step 08.2: the two new watch settings were missing from the settings docs entirely - the gate passed only because nothing declared them. SettingsDocScopeCatalog.wearEntries (S1788, the Compose-settings bridge) gained wearKeepScreenAwake and wearViewMode under a wear_screen_settings layout, annotations were written for both in en/ru/uk, and reindex-settings regenerated the manifest and all four rendered references. The Companion sheet's mirror controls are deliberately not separate entries: the watch entry is the documented setting and the sheet is its remote control, which is how the five existing pairs are already modelled. Verified: assert-settings-doc-sync exit 0 (catalog complete, manifest fresh, annotations covered, reference up to date); SETTINGS_REFERENCE.md now carries the Keep screen on and View rows under Wear OS; a.ps1 fk exit 0.

---

### Step 08.3 - Record the delivered capability in ALL_FEATURES

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 08.2

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/all_features/add.ps1` to append one EN-only record describing the delivered capability - named home sections, the shared List/Grid2/Grid3 view mode, selective resource transfer, the Streams and Apps entrances, and screen keep-awake during playback. Validate with `scripts/all_features/validate.ps1`. Do not touch `docs/FEATURES*.md` - that stays `/skill-release`'s job, populated from this file's diff at release time.

**Why:**

CLAUDE.md §11 "Feature inventory" makes `docs/ALL_FEATURES.jsonl` the required record of every shipped capability, and this ticket's own strategic §8 "Влияние на пользователя" is the user-facing summary this record exists to carry forward into the next release's FEATURES diff.

**Verification:**

- `pwsh -NoProfile -File scripts/all_features/validate.ps1` - exit 0.
- `Grep` - `S1781` present in the new `docs/ALL_FEATURES.jsonl` record.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - Step 08.3: one EN-only record appended - wear.home-sections-and-selective-transfer under Settings & Navigation, flavors standard/noLegal/legacy (the phone half is gated on SUPPORT_WEAR_COMPANION; the watch module is not flavored). It names the named home sections, the shared list/grid-2/grid-3 view, the command bar, the phone-side picker with its empty-selection behaviour, and screen keep-awake. Area reused rather than invented: the inventory's one existing Wear record already sits there, and the area field has visibly drifted elsewhere (VR, VR & OpenXR and VR and OpenXR all coexist), so a new spelling would add to that. docs/FEATURES*.md untouched - that is /skill-release's job. Verified: all_features/validate.ps1 exit 0 (746 records); S1781 present once in the inventory.

---

### Step 08.4 - Run the closure facade over the whole changed set

**Files:** none new - runs against every file touched by Phases 01-08
**Depends on:** Step 08.1, Step 08.2, Step 08.3

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/post-change.ps1 -Files "<comma-separated full changed set>" -ScopeToFile -Target "S1781" -Description "Wear main screen sections, view mode, resource selection, streams/apps entrances" -ChangeType Mixed`. Read the printed verdict - `post-change: PASS` or `PASS WITH ADVISORIES (n)` with every advisory named - and resolve any advisory before treating the ticket as closed.

**Why:**

CLAUDE.md §12 "Mechanical closure" requires routing through this facade rather than hand-rolling dev-log and gate steps individually, and its own rule that a closure claim needs fresh evidence - "no completion claim without fresh evidence... re-run and read it yourself" - is exactly what this step's Verification exists to force before the ticket is reported done.

**Verification:**

- Printed output contains the literal string `post-change: PASS` (with or without an advisory count).
- Exit code 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - Step 08.4: closure facade run over the ticket's changed set (19 files across both modules plus the regenerated settings docs and the inventory) returned post-change: PASS WITH ADVISORIES (2), exit 0. Advisory one, new-lexeme-count, is Rule 30 by design - the ten remaining locales are translated in bulk at pre-release, not per ticket. Advisory two, document-registry, was real and was resolved rather than waved through: it named wear-docs and its sibling docs/WEAR_OS_SMB_QUICK_REF.md, which still routed the owner through Browse to Network Storage; that line now says Resources, and a follow-up closure with -RegistryAck wear-docs returned post-change: PASS. Also cleared on the way: one NoConsecutiveBlankLines in SettingsDocScopeCatalog. The doc-pin-drift failure seen on the first two attempts was never this ticket's - a sibling session bumped the Room schema 50 to 51 for S1649 while its docs still said 50; it cleared when that session updated them, and nothing here touched their files.

---

### Step 08.5 - Retire "Network Storage" from the watch user guide

**Files:** `docs/WEAR_OS_SMB_SETUP.md`
**Depends on:** Step 05.2

**Prompt for developer:**

> Step 05.2 renamed the watch screen from "Network Storage" to "Resources", and `docs/WEAR_OS_SMB_SETUP.md` still walks the owner to a label that no longer exists on the watch - eight references, including the "Step 1: Open Network Storage" heading and every "Go to Browse → Network Storage" instruction. Rename the screen references to "Resources". Leave the document title and the generic phrase "SMB network storage" alone where they describe the technology rather than the screen.

**Why:**

The document-registry loop flagged it at the Phase 07 boundary: `wear-docs` is a published, user-facing surface whose update triggers include `wear`, and a rename that ships without it sends the owner looking for a screen name the build no longer has.

**Verification:**

- `Grep` - zero occurrences of "Network Storage" as a screen reference in `docs/WEAR_OS_SMB_SETUP.md` (the title's technology phrasing may remain).
- `pwsh -NoProfile -File scripts/document_registry/validate.ps1` - exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-18 - Step 08.5: eleven screen references in docs/WEAR_OS_SMB_SETUP.md renamed from Network Storage to Resources, matching what the watch now shows - the Step 1 heading, the section and list references, both Browse arrows, the section header and the quoted on-screen title. The document title and its H1 keep the phrase SMB Network Storage: there it names the technology, not the screen. Verified: remaining Network Storage hits are only lines 3 and 6, the title pair; document_registry/validate.ps1 exit 0 (34 records).

---

## Phase Done Criteria

- [x] Every `Step 08.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-08)` returns zero hits.
- [x] `dev/CHANGELOG.md` carries an entry for every file this ticket touched, written by the Step 08.4 facade run.
- [ ] `/spec-check S1781` returns `Verified`.

---

## Handoff Notes to Next Phase

Final phase - see `INDEX.md` Completion Gate. This ticket's shipped-capability record lives in `docs/ALL_FEATURES.jsonl`; the public `docs/FEATURES*.md` showcase entry is generated later, at the next `/skill-release` run, from that record's diff - not written here.

---

## Rollback Plan

Revert phase commit(s) - all changes here are generated documentation and catalog artifacts plus a changelog entry; reverting is safe and does not affect any code path from Phases 01-07.
