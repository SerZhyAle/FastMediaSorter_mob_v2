# Phase 06 - Docs, FEATURES, catalog cleanup

**Strategic spec:** [`../S0473_statistics-collection-option-default-off.md`](../S0473_statistics-collection-option-default-off.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all (Phase 01-05)
**Blocks:** none - final phase
**Steps done:** 5 / 5
**Started:** 2026-06-17
**Completed:** 2026-06-17

**Step Log:**

- 2026-06-17 - FEATURES EN/RU/UK gained a "17. Usage Statistics" entry (no period claim). Functionality log ADD + catalog scan/render + dev logs batched via `close-and-log.ps1`. 5 `Timber.d("S0473:")` debug tags inserted (toggle / baseline / sink flush / window open / report build) before the final `.\a.ps1 d` build (BUILD SUCCESSFUL, APK packaged). Journal -> BlockNeedUserTest with device checklist.

---

## Objective

Land the user-facing documentation, functionality log, and class-catalog regeneration for the completed feature, then move the ticket into `BlockNeedUserTest` with the mandatory debug verification tags inserted at the changed-flow entry points.

---

## Prerequisites

- [ ] Phase 01-05 all ✅ Done.
- [ ] Full project compiles - `.\a.ps1 fc`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | - |
| `docs/FEATURES_RU.md` | Modified | - |
| `docs/FEATURES_UK.md` | Modified | - |
| `dev/FUNCTIONALITY.log` | Modified (via script) | - |
| selected `.kt` entry points (debug tags) | Modified | - |

> Strategic §8 mandates a FEATURES update (it is a new user-facing capability, not "Без изменений").

---

## Steps

### Step 06.1 - FEATURES trilingual entry

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one user-facing feature entry to all three FEATURES files, adapting the candidate line in strategic §8 to the ACTUAL shipped behavior (all-time totals only, no period selector - correct the §8 draft which still says "с выбором периода"). Describe: opt-in local usage statistics, off by default, data stored on device, view summary in the Statistics window, send summary to the author by one button. Keep the trilingual entries semantically identical; RU/UK with ё/є. Place under the appropriate feature section consistent with neighboring entries. Verify the feature label tag (`[Standard]` etc.) matches the real flavor availability (all flavors, categories adapt).

**Verification:**

- `Grep` - a statistics feature line present in `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`.
- `Grep` - the word "period"/"период"/"період" is NOT in the new line (shipped v1 has no period selector).

**Status:** `[x] done`

---

### Step 06.2 - Functionality log

**Files:** `dev/FUNCTIONALITY.log` (via `scripts/add_to_functionality_log.ps1`)
**Depends on:** Step 06.1

**Prompt for developer:**

> Record the new capability via `pwsh -NoProfile -File scripts/add_to_functionality_log.ps1` with an `ADD` entry describing the opt-in local statistics feature (collection toggle, dashboard, author send/export). Run this command standalone/last - it is known to leave a non-zero `$LASTEXITCODE` even on success; re-verify the journal line landed.

**Verification:**

- `Grep` - a new `ADD` line mentioning statistics present in `dev/FUNCTIONALITY.log`.

**Status:** `[x] done`

---

### Step 06.3 - Catalog regeneration

**Files:** `dev/CATALOG/app_v2.jsonl` + `app_v2.md` (gitignored local indexes)
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` to rescan + render. Then fill `role` + `status` for the new public classes (`StatsBaselineDataStore`, `StatsAggregateDataStore`, `StatisticsRepository(Impl)`, `StatsSink(Impl)`, `StatsSessionTracker`, `StatsCategoryAvailability`, `GetStatisticsUseCase`, `BuildStatisticsReportUseCase`, `SetStatisticsCollectionEnabledUseCase`, `StatisticsActivity`, `StatisticsViewModel`, `StatisticsAdapter`, `StatisticsReportShareManager`) via `set.ps1` where `unknown`.

**Verification:**

- `Bash` - `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -ClassMatches "*Statistics*"` lists the new classes.
- `Bash` - `query.ps1 -Module app_v2 -ClassMatches "*StatsSink*"` returns the sink.

**Status:** `[x] done`

---

### Step 06.4 - Dev changelog completeness

**Files:** `dev/CHANGELOG.md` (via `scripts/add_to_dev_log.ps1`)
**Depends on:** Step 06.1, Step 06.2, Step 06.3

**Prompt for developer:**

> Ensure every file created/modified across Phase 01-06 has a `dev/CHANGELOG.md` entry (added via `scripts/add_to_dev_log.ps1` during each phase). Add any missing entries now for this phase's doc files. Do not hand-edit `dev/CHANGELOG.md`.

**Verification:**

- `Grep` - `docs/FEATURES.md` and the statistics classes referenced in recent `dev/CHANGELOG.md` entries.

**Status:** `[x] done`

---

### Step 06.5 - Debug verification tags + BlockNeedUserTest transition

**Files:** changed-flow entry points (`.kt`)
**Depends on:** Step 06.1-06.4

**Prompt for developer:**

> Per CLAUDE.md "Debug Verification Tags", insert one `Timber.d("S0473: <entry-point description>")` at each changed-flow ENTRY point (not per line): the settings toggle handler (`SetStatisticsCollectionEnabledUseCase` / the General-tab switch listener), the always-on baseline launch record (startup task), the `StatsSink.record` enqueue path, the Statistics screen open (`StatisticsActivity.onCreate`), and the send-to-author action. These probes exist IF AND ONLY IF the spec is `BlockNeedUserTest`. Then transition: `pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id S0473 -Status BlockNeedUserTest -StatusNote '<device checklist>'`. The status note must list what the user verifies on device: toggle off by default + Statistics entry hidden; enabling shows the entry + window; activity (copy/move/delete/view/capture) reflected in the window; disabling wipes detailed but keeps first-launch/launch-count/install-version; send-to-author opens the mail client with TXT attachment + author address; EN/RU/UK + portrait/landscape + D-pad/TalkBack.

**Verification:**

- `Grep` - `Timber.d("S0473:` present in at least the toggle handler, startup baseline task, `StatsSinkImpl`, and `StatisticsActivity`.
- `Bash` - `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id S0473 -Format json` shows `"status":"BlockNeedUserTest"`.
- `Grep` - no permanent log line embeds `S0473` outside these `Timber.d("S0473:` probes (Rule: ticket id only in BlockNeedUserTest probes).

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] Full project compiles - `.\a.ps1 fc`.
- [ ] FEATURES trilingual entry present and consistent (no "period" claim).
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated; new classes carry `role`+`status`.
- [ ] Ticket is `BlockNeedUserTest` with debug tags present and a device checklist in the status note.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. After user device verification, `/spec-check S0473` removes the `S0473:` debug tags and advances the strategic spec to `Verified`.

---

## Rollback Plan

Docs and catalog changes are non-code; revert the doc commits if needed. Debug tags are removed on leaving `BlockNeedUserTest` (by `/spec-check` on `Verified`, or `/spec-update` on re-open) - never leave a stale `S0473:` tag once the status changes.
