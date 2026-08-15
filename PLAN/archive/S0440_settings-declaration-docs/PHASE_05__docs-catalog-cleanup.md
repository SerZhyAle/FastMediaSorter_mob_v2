# Phase 05 - Docs and Catalog Cleanup

**Strategic spec:** [`../S0440_settings-declaration-docs.md`](../S0440_settings-declaration-docs.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** none - final phase
**Steps done:** 4 / 4
**Started:** 2026-06-19
**Completed:** 2026-06-19

---

## Objective

Surface the new reference in the docs index and FEATURES, and finalize catalog/dev-log bookkeeping.

---

## Prerequisites

- [ ] Phases 01-04 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | ≤ +3 |
| `docs/FEATURES_RU.md` | Modified | ≤ +3 |
| `docs/FEATURES_UK.md` | Modified | ≤ +3 |
| `docs/DOCS_MAP.md` | Modified | ≤ +6 |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |

---

## Steps

### Step 05.1 - FEATURES sentence (trilingual)

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one sentence (strategic §8) announcing the new "Settings reference" page and linking to `SETTINGS_REFERENCE.md` (and the localized variants in each translation). Keep the three files in lockstep. Follow `docs/COMMUNICATION_POLICY.md` §2/§6; `..` not `...`; mandatory ё/Ё in RU.

**Verification:**

- `Grep` - `SETTINGS_REFERENCE` referenced in all three FEATURES files.
- Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1` is not applicable (docs, not strings) - instead confirm trilingual parity by Grep in each file.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - DEVIATION from the original step (superseded by current policy). CLAUDE.md §11 + the `/spec-dev` skill forbid per-spec edits to `docs/FEATURES*.md` (showcase is populated ONLY by `/skill-release` from the `ALL_FEATURES` diff). Instead recorded the delivered capability in `docs/ALL_FEATURES.jsonl` via `scripts/all_features/add.ps1` (id `settings-navigation.settings-reference-page`, area "Settings & Navigation", flavors standard,lite,photos,legacy, spec S0440). `/skill-release` will surface the FEATURES sentence at the next plateau. Strategic §8 intent (FEATURES visibility) is preserved through the inventory path.

---

### Step 05.2 - DOCS_MAP entry

**Files:** `docs/DOCS_MAP.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Add a row for the Settings Reference under "User Guides & Manuals" with the EN/RU/UK links, matching the existing table style.

**Verification:**

- `Grep` - `SETTINGS_REFERENCE` referenced in `docs/DOCS_MAP.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - Verification PASS. Added a "Settings Reference" row under "User Guides & Manuals" in `docs/DOCS_MAP.md` (EN primary + inline RU/UK links), matching the existing table style.

---

### Step 05.3 - Catalog regen

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 05.2

**Prompt for developer:**

> Regenerate the class catalog so the new test/util classes from Phase 01 are indexed: `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Set `role`/`status` for new classes via `set.ps1` if the sync leaves them blank.

**Verification:**

- `Bash` - `SettingsManifestSerializer` present in `dev/CATALOG/app_v2.jsonl` (or `.md`).

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - Verification adjusted. `catalog_sync.ps1 -Module app_v2` re-run (1898 records, OK). The two Phase-01 classes (`SettingsManifestSerializer`, `SettingsManifestExportTest`) live in `app_v2/src/test/` and are intentionally NOT indexed - the catalog scanner scans production/flavor source sets only, not test sources (so a test-class grep cannot pass by design). Catalog is current for all main-source changes. No new main-source `.kt` introduced by this ticket.

---

### Step 05.4 - Dev changelog finalization

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 05.3

**Prompt for developer:**

> Confirm `dev/CHANGELOG.md` has an entry for every file created or modified across Phases 01-05 via `.\scripts\add_to_dev_log.ps1`. Add any missing entries.

**Verification:**

- `Grep` - `SETTINGS_REFERENCE` and `settings-manifest` referenced in `dev/CHANGELOG.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - Verification PASS. `dev/CHANGELOG.md` references SETTINGS_REFERENCE (2), settings-manifest (2), settings-annotations (3); dev-log entries recorded per phase for every created/modified file across Phases 01-05.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] `assert-settings-doc-sync.ps1 -Gate` still exits 0.
- [x] `dev/CHANGELOG.md` covers every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.
- [x] Ready for `/spec-check S0440`.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s) - docs-only edits plus catalog regen. No code or data migration changed.
