# Phase 04 - Drift Gate

**Strategic spec:** [`../S0440_settings-declaration-docs.md`](../S0440_settings-declaration-docs.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** 2026-06-19
**Completed:** 2026-06-19

---

## Objective

Make settings-doc drift mechanically impossible to merge: one gate that proves the manifest is fresh, the scan catalog is complete, annotations cover all keys, and the reference is regenerated; wired into `post-change.ps1` and encoded as a project rule.

---

## Prerequisites

- [ ] Phases 01-03 ✅ Done - manifest, annotations, renderer all in place and idempotent.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/assert-settings-catalog-complete.ps1` | New | ≤ 140 |
| `scripts/quality/assert-settings-doc-sync.ps1` | New | ≤ 200 |
| `scripts/post-change.ps1` | Modified | ≤ +30 |
| `CLAUDE.md` | Modified | ≤ +6 |

---

## Steps

### Step 04.1 - Scan-catalog completeness assertion

**Files:** `scripts/quality/assert-settings-catalog-complete.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Write a gate that enumerates `app_v2/src/main/res/layout/fragment_settings_*.xml` and fails (exit 1) if any layout containing settings rows is absent from `SettingsSearchLayoutCatalog.layoutResIds`, except known tab-host containers (`fragment_settings_media_container`). This closes the documented leak in `SettingsSearchLayoutCatalog` (its KDoc warns rows are silently invisible if a layout is not appended) - a missing layout would silently drop settings from both search and docs.

**Verification:**

- `Glob` - `scripts/quality/assert-settings-catalog-complete.ps1` exists.
- Run it; record `expected: exit 0 | actual: <code>`.
- `Grep` - `fragment_settings_media_container` named as the allowed exclusion.

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - Verification PASS. `assert-settings-catalog-complete.ps1` enumerates `fragment_settings_*.xml`, parses `R.layout.fragment_settings_*` from `SettingsSearchLayoutCatalog.kt`, excludes `fragment_settings_media_container`. Run: expected exit 0 | actual 0 ("8 catalogued, 1 host exclusion, 0 missing").

---

### Step 04.2 - Composite drift gate

**Files:** `scripts/quality/assert-settings-doc-sync.ps1`
**Depends on:** Step 04.1

**Prompt for developer:**

> Write the composite gate with a `-Gate` switch. It must: (1) run `assert-settings-catalog-complete.ps1`; (2) run the manifest verify test (`./gradlew.bat testStandardDebugUnitTest --tests "*SettingsManifestExportTest"`) and fail if the committed manifest is stale; (3) run `scripts/docs/check-settings-annotations.ps1` for coverage/parity; (4) re-run `scripts/docs/render-settings-reference.ps1` into a temp dir and fail if it diffs from the committed `SETTINGS_REFERENCE*` files. Any sub-failure -> exit 1 with the failing stage named. Exit 0 only when all pass.

**Verification:**

- `Glob` - `scripts/quality/assert-settings-doc-sync.ps1` exists.
- `Grep` - all four stages referenced (`assert-settings-catalog-complete`, `SettingsManifestExportTest`, `check-settings-annotations`, `render-settings-reference`).
- Run `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1 -Gate`; record `expected: exit 0 | actual: <code>`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - Verification PASS. Composite gate runs all four stages (catalog-complete, manifest verify test, annotations check, render+byte-diff). Run: expected exit 0 | actual 0. `-SkipManifestTest` escape hatch provided for JVM-less environments.

---

### Step 04.3 - Wire the gate into post-change

**Files:** `scripts/post-change.ps1`
**Depends on:** Step 04.2

**Prompt for developer:**

> Add the new gate to `scripts/post-change.ps1` alongside the existing `assert-*` gates, following their invocation pattern. Trigger it only when the change touches settings surfaces: `app_v2/src/main/res/layout/fragment_settings_*.xml`, `app_v2/**/ui/settings/search/**`, the `*SettingsSearchAvailabilityModule.kt` files, or anything under `docs/settings/` / `docs/SETTINGS_REFERENCE*`. Keep it skipped for unrelated changes so the gate stays quiet.

**Verification:**

- `Grep` - `assert-settings-doc-sync.ps1` referenced in `scripts/post-change.ps1`.
- `Grep` - a settings-path trigger condition present near the new invocation.

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - Verification PASS. Added `$runsSettingsDocGate` path trigger (settings fragment layouts, `ui/settings/search/`, `*SettingsSearchAvailabilityModule.kt`, `docs/settings/`, `docs/SETTINGS_REFERENCE`) + invocation block, following the existing `all-features-gate` pattern. Quiet skip for unrelated changes.

---

### Step 04.4 - Encode the project rule

**Files:** `CLAUDE.md`
**Depends on:** Step 04.3

**Prompt for developer:**

> Add a Strict Rule (§10) stating: any change to a setting - presence, behavior, position, or naming - requires regenerating the settings manifest and reference and updating its annotation; the `assert-settings-doc-sync.ps1` gate enforces it. Reference the gate script by path. Keep it one rule, terse. This is the "prevent at source" half so the slop is not produced, not only detected.

**Verification:**

- `Grep` - `assert-settings-doc-sync` referenced in `CLAUDE.md`.
- `Grep` - the rule mentions presence/behavior/position/naming.

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - Verification PASS. Added Strict Rule 22 to CLAUDE.md §10: a setting change (presence/behavior/position/naming) must regenerate manifest + reference + annotation; references `scripts/quality/assert-settings-doc-sync.ps1` by path. One terse rule (prevent-at-source half).

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] `assert-settings-doc-sync.ps1 -Gate` exits 0 on the current tree.
- [x] A deliberate edit (rename one setting title, do not regenerate) makes the gate exit 1; revert after recording. (Appended a stray reference row -> stage 'reference-fresh' FAIL exit 1; re-rendered -> exit 0.)
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

Drift is now mechanically caught. Phase 05 only needs the catalog/dev-log/FEATURES bookkeeping.

---

## Rollback Plan

Revert phase commit(s) - remove the two gate scripts, the post-change wiring, and the CLAUDE.md rule. No runtime behavior changed.
