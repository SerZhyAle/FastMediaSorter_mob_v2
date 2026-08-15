# Phase 07 - Docs and catalog cleanup

**Strategic spec:** [`../S1190_internationalization-docs-website-top-languages.md`](../S1190_internationalization-docs-website-top-languages.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, 02, 03, 04, 05, 06
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-08-05
**Completed:** 2026-08-05

---

## Objective

Record the delivered capability, refresh the settings documentation the language row belongs to, and close the ticket through the mechanical facade.

---

## Prerequisites

- [ ] Phases 01-06 are ✅ Done or explicitly ⏭️ Skipped with a reason in the Blockers Log.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/settings/settings-manifest.json` | Regenerated | - |
| `docs/SETTINGS_REFERENCE*.md` | Regenerated | - |
| `docs/ALL_FEATURES.jsonl` | Modified | ≤ 3 |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |

---

## Steps

### Step 07.1 - Regenerate the settings documentation

**Files:** `docs/settings/settings-manifest.json`, `docs/SETTINGS_REFERENCE*.md`, `docs/settings/settings-annotations.json`
**Depends on:** - start of phase

**Prompt for developer:**

> The general-settings language row changed what it opens and what it displays, which is a settings surface change under CLAUDE.md Rule 22. Regenerate the manifest and the reference, and update the row's annotation to describe the searchable picker rather than a three-item list.

**Verification:**

- `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-05 - Verification 1\1 PASS. `assert-settings-doc-sync.ps1` exits 0: catalog complete, manifest fresh, annotations covered, reference up to date, HOW_TO recipes in sync.
- The gate was already green **before** the edit, which is the point worth recording: it proves freshness, not accuracy. The `rowLanguage` annotation still read `Selects the language used for the app interface.` - true, but silent about both changes this ticket made. It did not describe the searchable picker the step expected to correct, and it did not describe the Play download gate added in Phase 05. A reader of `SETTINGS_REFERENCE.md` would not have learned that choosing a language can now be refused. Rewritten in all three locales to name the searchable list and the download-first behaviour, then `render-settings-reference.ps1` re-rendered all four references.
- Tooling note: the phase text points at a generator name that does not exist. `scripts/docs/generate-settings-reference.ps1` exits 64 (pwsh usage error, no such script); the renderer is `scripts/docs/render-settings-reference.ps1`, which is the name the gate's own failure message prints.

---

### Step 07.2 - Record the capability

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 07.1

**Prompt for developer:**

> Add the capability through `scripts/all_features/add.ps1` (EN only): the interface can be switched to any of thirteen languages, chosen from a searchable list with flags, with the language delivered on demand on Play builds. Read the flavor list off the actual gate - strings live in `src/main`, so every flavor ships this - and read the written record back to confirm.

**Verification:**

- `Grep` - the new record matches once in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-05 - Verification 2\2 PASS. `general.interface-language-thirteen` matches exactly once in `docs/ALL_FEATURES.jsonl`, read back verbatim after writing; `validate.ps1` PASS over 643 records.
- Checked for a duplicate first: `S1190` already owned one record, `setup-onboarding.language-selection-on-welcome`, written when Phase 02 landed the Welcome picker. It covers the first-run surface only, so this is a second capability rather than a rewrite of the first - the settings surface plus the on-demand delivery that Phase 05 added.
- Flavors read off the gate rather than guessed, as the step demands: `locales_config.xml`, `LocaleHelper`, `UiLanguageCatalog`, `LanguageSplitInstaller` and every `values-*` resource live in `src/main`, and `feature-delivery-ktx` is declared as a plain `implementation` in `app_v2/build.gradle.kts` with no per-flavor variant. Nothing gates this by flavor, so all six ship it. Flavor names taken from `docs/FLAVOR_MATRIX.md`, never from memory (S1392).

---

### Step 07.3 - Close the change

**Files:** `dev/CHANGELOG.md`, `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 07.2

**Prompt for developer:**

> Close through `scripts/spec_catalog/close-and-log.ps1` with one dev-log entry per touched file, the feature record, and the catalog scan in a single pass. The ticket goes to `BlockNeedUserTest`: the RTL pass and the Play split download are device gates listed in the INDEX.

**Verification:**

- `close-and-log.ps1` exits 0 and reports the status transition.
- `pwsh -NoProfile -File scripts/quality/assert-no-ticket-logs.ps1` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-05 - Verification 2\2 PASS. `close-and-log.ps1` exit 0, reporting `S1190 In Progress -> BlockNeedUserTest`, header and `**Status note:**` synced, six dev-log rows written, catalog scanned and rendered. `assert-no-ticket-logs.ps1` exit 0 - `expected: 0 | actual: 0`, the two new probes counted among the allowed `BlockNeedUserTest` set. `select.ps1` reads the status and the note back from the journal.
- **Probe tags were inserted before the validating build, not after.** This phase is documentation-only and carries no build criterion of its own, so the rule "tags are the last code edits before the final phase's `Project compiles` build" had nowhere obvious to land. Inserting them here and running one `.\a.ps1 dq` (exit 0, `hiltJavaCompileStandardDebug` executed) satisfies the intent - a single build validates implementation plus tags - instead of shipping tags that never compiled. Two entry points, one per changed flow: `LanguageSplitInstaller.ensureLanguage` (the delivery flow) and `GeneralSettingsViewSetupHelper.applyLanguageWhenAvailable` (the settings switch flow).
- **Status set before the gate ran.** `assert-no-ticket-logs` treats a `Timber.d("S1190:` line as a defect unless the catalog says `BlockNeedUserTest`; running it first would have failed on tags that are correct.
- `-FuncOp` deliberately omitted from the closure: Step 07.2 already wrote the inventory record through `add.ps1`, and the facade produces the same record by id, so passing it again would only re-upsert what is already verified.
- The status note names both device gates in operational terms, including the trap that makes the second one easy to fake: a debug APK carries every locale, so `SplitInstallManager` answers "already installed" and the download path is never exercised. Proving it needs a Play-installed build.

---

## Phase Done Criteria

- [x] Every `Step 07.*` above is `[x] done`.
- [x] `docs/FEATURES*.md` untouched - the showcase is release-owned. The capability went to `docs/ALL_FEATURES.jsonl` only; `/skill-release` picks it up from the inventory diff.
- [x] Ticket advanced to `BlockNeedUserTest` with a status note naming both device gates.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Documentation-only phase - revert the commit; no runtime surface is affected.
