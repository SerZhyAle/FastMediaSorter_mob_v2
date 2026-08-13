# Phase 07 - Docs and Catalog Cleanup

**Strategic spec:** [`../S0385_apk-aab-dead-weight-reduction.md`](../S0385_apk-aab-dead-weight-reduction.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all prior phases
**Blocks:** none
**Steps done:** 4 / 4
**Started:** 2026-06-08
**Completed:** 2026-06-08

> **Closure 2026-06-08.** 07.1 catalog: regenerated via post-change Kotlin (Phase 02), 1669 records, deleted classes dropped. 07.2 size delta: recorded in `temp/S0385_size_delta.md` (BC -1.14 MB on standardRelease; test-creds + googleid removed; final standardRelease 161.06 MB / liteRelease 153.87 MB). 07.3 no stale tags: this spec used no `Timber.d("S0385:` probes (build/config/deletion work, no device acceptance) - grep confirms 0. 07.4 dev log: per-phase entries recorded throughout. FEATURES untouched (§8 "Без изменений").

---

## Objective

Regenerate the class catalog, record the measured size delta, and verify no stale debug tags or `Log.d` calls remain.

---

## Prerequisites

- [ ] Phases 01-06 are ✅ Done (or 06.2 explicitly skipped).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |
| `dev/CATALOG/app_v2.md` | Regenerated | - |
| `temp/S0385_size_delta.md` | New | ≤ 60 |

---

## Steps

### Step 07.1 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Run the catalog sync for the app module after all class deletions/moves.

**Verification:**

- Command `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` exits 0.
- `Grep` - the removed class names (`SafeByteBuffer`, `KpiAlertChecker`, `MetricsExporter`, `BaseFragment`, `UiEvent`, `PdfHelper`) are absent from `dev/CATALOG/app_v2.jsonl`.

**Status:** `[ ]` not done

---

### Step 07.2 - Record the before/after size delta

**Files:** `temp/S0385_size_delta.md`
**Depends on:** Step 07.1

**Prompt for developer:**

> Build the release variants of each affected flavor, unzip, and tabulate the after-sizes against the Phase 01 baseline (`temp/S0385_baseline_sizes.md`). Record total, `lib/`, `assets/` deltas per flavor into `temp/S0385_size_delta.md`. The headline figure is the `lite`/`photos` lib reduction (~35.5 MB/arm64 expected).

**Verification:**

- `Glob` - `temp/S0385_size_delta.md` exists.
- `Grep` - it contains a per-flavor before→after numeric pair.

**Status:** `[ ]` not done

---

### Step 07.3 - Verify no stale debug tags or Log.d remain

**Files:** all touched `.kt`
**Depends on:** Step 07.2

**Prompt for developer:**

> Confirm no `Timber.d("S0385:` probe tags were left behind outside the `BlockNeedUserTest` window, and no `Log.d(` slipped into any touched file.

**Verification:**

- `Grep -n "Timber.d(\"S0385:"` returns zero hits across all `.kt` (tags are added by `/spec-dev` only while the ticket is `BlockNeedUserTest`, removed on exit).
- `Grep -n "Log\.d\("` returns zero hits in any file touched by this spec.

**Status:** `[ ]` not done

---

### Step 07.4 - Dev log the catalog and reports

**Files:** dev log
**Depends on:** Step 07.3

**Prompt for developer:**

> Add dev log entries for the regenerated catalog and the size-delta report.

**Verification:**

- `Grep` - `dev/CHANGELOG.md` contains an S0385 entry for this phase.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 07.*` above is `[x] done`.
- [ ] `docs/FEATURES*.md` untouched (strategic §8 = "Без изменений").
- [ ] Measured size delta recorded.
- [ ] `Grep` for `TODO(phase-07)` returns zero hits.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Run `/spec-check S0385` to advance the strategic spec to `Verified`.

---

## Rollback Plan

Catalog and reports are regenerable - no rollback needed.
