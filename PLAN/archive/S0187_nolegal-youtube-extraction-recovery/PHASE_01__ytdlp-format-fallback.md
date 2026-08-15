# Phase 01 — yt-dlp Format-Error Fallback

**Strategic spec:** [`../S0187_nolegal-youtube-extraction-recovery.md`](../S0187_nolegal-youtube-extraction-recovery.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 2 / 2
**Started:** 2026-05-14
**Completed:** 2026-05-14

---

## Objective

Make `YtDlpExtractionStrategy.open()` return `OpenResult.NotFound` (instead of `OpenResult.Error`) when yt-dlp raises "Requested format is not available", so the extraction cascade continues to `NewPipeSiteExtractionStrategy`.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/YtDlpExtractionStrategy.kt` | Modified | ≤ 520 |

> File is 508 lines — projected >500 after change → backup step required (Step 01.1).

---

## Steps

### Step 01.1 — Back up YtDlpExtractionStrategy before edit

**Files:** _(temp/ only — not a source file)_
**Depends on:** — start of phase

**Prompt for developer:**

> Copy `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/YtDlpExtractionStrategy.kt`
> to `temp/YtDlpExtractionStrategy_S0187_<timestamp>.kt.backup` (use `System.currentTimeMillis()` or
> a date string for `<timestamp>`). This satisfies the ">500 LOC backup" rule before the edit in Step 01.2.

**Verification:**

- `Glob` — `temp/YtDlpExtractionStrategy_S0187_*.kt.backup` returns at least one match.

**Status:** `[x]` done

**Step Log:**

- 2026-05-14 — Verification 1/1 PASS. Files: temp/YtDlpExtractionStrategy_S0187_20260514_022109.kt.backup. Dev log N/A (temp file).

---

### Step 01.2 — Extend not-applicable error patterns in YtDlpExtractionStrategy.open()

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/YtDlpExtractionStrategy.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `YtDlpExtractionStrategy.open()`, locate the `getOrElse` block (around line 332) that checks
> `error.message` and returns either `OpenResult.NotFound("ytdlp_not_applicable")` or
> `OpenResult.Error(error)`. Extend the existing `if` condition with one additional OR clause:
> `|| msg.contains("Requested format is not available", ignoreCase = true)`.
>
> The final condition must cover all four patterns:
> - `"There is no video in this post"`
> - `"Unsupported URL:"`
> - `"Instagram sent an empty media response"`
> - `"Requested format is not available"` ← add this
>
> No other changes to the file. The `Timber.d` log call and `OpenResult.NotFound("ytdlp_not_applicable")`
> return value already exist and apply to the new case without modification.

**Verification:**

- `Grep` — `Requested format is not available` matches exactly once in `YtDlpExtractionStrategy.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `YtDlpExtractionStrategy.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-14 — Verification 2/2 PASS. Files: YtDlpExtractionStrategy.kt (+4 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles — BUILD SUCCESSFUL in 31s.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for `YtDlpExtractionStrategy.kt` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

- `YtDlpExtractionStrategy.open()` now returns `NotFound` for YouTube format-unavailable errors.
- `LinkAutoDownloadCoordinator` will `continue` to the next strategy (`NewPipeSiteExtractionStrategy`) when yt-dlp can't select a YouTube format.
- No changes to probe logic — yt-dlp probe still matches YouTube URLs first (ordering unchanged).

---

## Rollback Plan

Revert the one-line change to `YtDlpExtractionStrategy.kt`. No data migration or user-facing surface changed.
