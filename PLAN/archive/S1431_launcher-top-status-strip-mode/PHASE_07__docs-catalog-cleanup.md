# Phase 07 - Docs and catalog cleanup

**Strategic spec:** [`../S1431_launcher-top-status-strip-mode.md`](../S1431_launcher-top-status-strip-mode.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all
**Blocks:** none - final phase
**Steps done:** 5 / 5
**Started:** 2026-08-09
**Completed:** 2026-08-09

---

## Objective

Record the delivered capability, regenerate the class catalog, and prove the mode is absent from the four
flavors that carry no launcher.

---

## Prerequisites

- [x] Phases 01-06 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified | - |
| ~~`docs/FEATURES*.md`~~ | NOT touched - `/skill-release` owns them (see step 07.3) | - |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |

---

## Steps

### Step 07.1 - Prove the mode is absent from the non-launcher flavors

**Files:** none - verification step against built output
**Depends on:** - start of phase

**Prompt for developer:**

> Compile the `lite` flavor and confirm the new settings row, the new strings' usage sites and the strip
> mode are all absent from it, since `src/launcherEnabled` is not mounted there. Record the command run
> and its exit code.

**Why:**

Strategic §11 criterion 10 requires nothing from this ticket to appear in `lite`, `photos`, `legacy` or
`vr`, and research 01 §6 records that those four mount `src/launcherDisabled` instead.

**Verification:**

- The `lite` compile exits 0.
- `Grep` - `launcherTopStatusStripMode` returns zero hits under `app_v2/src/launcherDisabled/`.
- `Grep` - `BuildConfig.SUPPORT_LAUNCHER` returns zero hits under `app_v2/src/main/` (Rule 14).

**Status:** `[x] done`

---

### Step 07.2 - Record the capability in the feature inventory

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 07.1

**Prompt for developer:**

> Add one record via `pwsh -NoProfile -File scripts/all_features/add.ps1` describing the mode in English,
> with `spec` set to `S1431` and the flavor list taken from the gate rather than from memory.

**Why:**

CLAUDE.md section 11 makes `docs/ALL_FEATURES.jsonl` the inventory every shipped capability is recorded
in, and strategic §8 states this ticket delivers a new one.

**Verification:**

- `Grep` - `S1431` matches in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[x] done`

---

### Step 07.3 - Leave the showcase files to the release pipeline

**Files:** none - `docs/FEATURES*.md` must NOT be touched here
**Depends on:** Step 07.2

**Prompt for developer:**

> Do not edit `docs/FEATURES.md`, `_RU.md` or `_UK.md`. Strategic §8's sentence is delivered by the
> `docs/ALL_FEATURES.jsonl` record written in step 07.2; `/skill-release` generates the showcase text
> from the inventory diff since the previous release.

**Why:**

Plan defect corrected during execution. CLAUDE.md section 11 states it directly: the showcase is
"populated ONLY by `/skill-release` from the `ALL_FEATURES` diff since the previous release - never
edited per-spec". A hand-written sentence here would be overwritten by the next release render, and
until then would claim a capability the release notes never announced. Strategic §8 is satisfied by the
inventory record, which is the input that sentence is generated from.

**Verification:**

- `Grep` - `S1431` returns zero hits in `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`.
- `Grep` - the inventory record from step 07.2 carries the capability, which is what §8 feeds.

**Status:** `[x] done`

---

### Step 07.4 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 07.3

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2 -Force` once for the whole ticket,
> then set `role` and `status` on any class this ticket introduced via `set.ps1`.

**Why:**

CLAUDE.md section 12 requires one catalog sync per ticket, and research 01 §1 showed the index can go
stale enough to report a path that no longer exists.

**Verification:**

- `catalog_sync.ps1` exits 0.
- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "*LauncherStatusStripManager*"`
  reports the class at its real `src/launcherEnabled` path.

**Status:** `[x] done`

---

### Step 07.5 - Close the ticket through the facade

**Files:** none - closure step
**Depends on:** Step 07.4

**Prompt for developer:**

> Close through `pwsh -NoProfile -File scripts/post-change.ps1` naming the whole changed set with
> `-Files` and `-ScopeToFile`, then set the ticket to `BlockNeedUserTest` with a `-StatusNote` describing
> exactly what the owner must check on a device: the switch's presence and gating, the clock with seconds
> at the left, the indicators at the right, the signal chips still visible, the tray gone from the Start
> panel with its row disabled and explained, more recents than before and more in landscape than in
> portrait, and everything restored when the mode is switched off.

**Why:**

CLAUDE.md section 12 routes mechanical closure through the facade, and the ticket's own probes from steps
04.5 and 05.4 are bound to the `BlockNeedUserTest` status they are inserted for.

**Verification:**

- `post-change.ps1` prints `post-change: PASS` and exits 0.
- `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id S1431 -Format json` reports
  `BlockNeedUserTest` with a non-empty `statusNote`.
- `Grep` - `Timber.d("S1431:` matches exactly twice across `app_v2/src` (the two probes, still present
  because the ticket is in `BlockNeedUserTest`).

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 07.*` above is `[x] done`.
- [x] Strategic §8's capability recorded in `docs/ALL_FEATURES.jsonl`. The showcase files stay untouched -
  see step 07.3; this criterion was written as "update `docs/FEATURES*.md`" and is corrected for the same
  reason the step is.
- [x] `dev/CHANGELOG.md` has an entry for the ticket.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Documentation-only phase apart from the catalog regeneration - revert the commit and re-run
`catalog_sync.ps1`.

---

## Step Log

- 2026-08-09 - Step 07.1 done. `check-standard-fast.ps1 -Mode CodeAndResources -Flavor Lite`: BUILD SUCCESSFUL, exit 0. This is not a redundant flavor compile - the settings dialog, its layout and the three new strings all live in `src/main` and therefore ship to `lite` too; only the launcher source set they refer to does not. expected: exit 0, `launcherTopStatusStripMode` 0 hits under `src/launcherDisabled/`, `BuildConfig.SUPPORT_LAUNCHER` 0 hits under `src/main/` | actual: exit 0, 0 hits, 1 hit - and that one predates this ticket.
- 2026-08-09 - Note on that single `SUPPORT_LAUNCHER` hit: `PermissionRegistryRepositoryImpl.kt:449` maps the flag name to its value inside a diagnostics table of every flavor flag. It is a report of the flag, not a behavioural guard on it, so it is not the Rule 14 shape; the `flavor-flags` gate has reported `new occurrences 0` on every closure of this ticket, which is the mechanical confirmation that it is accepted pre-existing state and not something this ticket introduced.
- 2026-08-09 - Step 07.2 done. One record added via `all_features/add.ps1`, flavors `standard,noLegal` read from `docs/FLAVOR_MATRIX.md`'s `SUPPORT_LAUNCHER` row rather than from memory - the four remaining flavors carry `[-]*`. expected: `validate.ps1` exit 0 | actual: `ALL_FEATURES validation PASS: 677 record(s)`.
- 2026-08-09 - Step 07.3 rewritten during execution, plan defect. The step asked for a hand-written sentence in `docs/FEATURES*.md`; CLAUDE.md section 11 reserves those files for `/skill-release`, generated from the `ALL_FEATURES` diff. A sentence written here would be overwritten at the next release render and would meanwhile claim a capability no release note announced. Strategic §8 is delivered through the step 07.2 record, which is the generator's input. expected: `S1431` 0 hits in each of the three showcase files | actual: 0, 0, 0.
- 2026-08-09 - Step 07.4 done. `catalog_sync.ps1 -Module app_v2` ran inside the phase 06 closure (2658 records rendered), which is the once-per-ticket sync CLAUDE.md section 12 asks for. This ticket introduced no new Kotlin class - only two new layouts - so no `set.ps1` role/status is due. expected: query reports `LauncherStatusStripManager` at its real path | actual: reported, `ui` layer, 296 LOC, matching the live file.
- 2026-08-09 - Document-registry loop at the phase boundary. Affected record: `settings-reference` (`docs/SETTINGS_REFERENCE*.md`, `settings-manifest.json`, `settings-annotations.json`), all regenerated in phase 06 rather than hand-edited. Not affected: `architecture` (no new layer, dependency or database change - the strip/tray seam stays inside existing layers), `settings-scope-exclusions` (the row lands in an already-classified doc-scope layout; the catalog gate reports all 25 layouts classified), `user-guides` (no HOW_TO recipe names this row, and `howto-settings-paths` reports all 50 recipes still resolving). Closed with `validate.ps1` exit 0, `generate.ps1` exit 0, `generate.ps1 -Check` exit 0 ("Generated document views are current").
- 2026-08-09 - Step 07.5 done, in the order the gates require rather than the order the step lists. The two probes went in FIRST, then the status flipped to `BlockNeedUserTest`, then the build ran, then the facade closed. That order matters: `assert-no-ticket-logs` reads the CURRENT catalog status, so probes inserted before the flip fail their own closure - which is exactly what happened in phase 04 and is why steps 04.5 and 05.4 were rewritten to defer them here.
- 2026-08-09 - Probes: one per changed flow entry, two total across `app_v2/src`. `LauncherStatusStripManager` line 109 reports `mode / clock pinned / indicators pinned`; `LauncherTaskbarManager` line 118 reports `tray visible / recents capacity`. Both are single-line so a line-wrapped literal cannot hide from the gate's grep.
- 2026-08-09 - F4 build gate: `a.ps1 d` (full standard debug, which validates the code AND the probes in one pass, per the ticket-tag rule). expected: exit 0 | actual: `BUILD SUCCESSFUL in 1m 30s`, exit 0, APK `FastMediaSorter_standard_debug_v2.60.8082.309-DEBUG.apk`.
- 2026-08-09 - Closure: `post-change.ps1 -Files <3> -ScopeToFile -ChangeType Mixed`. expected: PASS | actual: `post-change: PASS WITH ADVISORIES (1)`, exit 0. The one advisory is `document-registry (exit 1)`, and the facade itself says it could not attribute it to this change. Checked directly rather than accepted: `document_registry/validate.ps1` exit 0 (`PASS: 27 record(s)`) and `generate.ps1 -Check` exit 0 (`Generated document views are current`). A concurrent session was adding classes to the same tree during the run - the catalog moved from 2658 to 2667 records mid-closure - which is the likeliest source. Not attributable to S1431, and both authoritative checks are green.
- 2026-08-09 - Catalog status after the flip: `select.ps1 -Id S1431` reports `BlockNeedUserTest` with a 1571-character status note, and the strategic spec header carries the same note under `**Status note:**`.
