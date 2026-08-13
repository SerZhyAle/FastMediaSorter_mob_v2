# Phase 02 — Selection bias: EMBEDDED_JSON subset wins over OG/IMG

**Strategic spec:** [`../S0197_threads-ig-data-sjs-extractor.md`](../S0197_threads-ig-data-sjs-extractor.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 1 / 1
**Started:** 2026-05-14
**Completed:** 2026-05-14

---

## Objective

Bias `CandidateSelectionPolicy.choose` so that whenever the candidate list contains any `EMBEDDED_JSON` entry, the policy restricts its winner search to that subset. For lists without `EMBEDDED_JSON`, behavior is unchanged.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done — embedded-JSON candidates now flow into both strategies.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/CandidateSelectionPolicy.kt` | Modified | ≤ 100 |

---

## Steps

### Step 02.1 — Restrict winner search to `EMBEDDED_JSON` subset when present

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/CandidateSelectionPolicy.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `CandidateSelectionPolicy.choose`, immediately after computing `httpOnly`, build a local `val pool = httpOnly.filter { it.source == HtmlMediaCandidate.Source.EMBEDDED_JSON }.takeIf { it.isNotEmpty() } ?: httpOnly`. Replace every subsequent use of `httpOnly` inside `choose` with `pool` (the `≥ 1 MiB` first-match, the size-max fallback, the HLS/DASH manifest fallback, and the final `pool.first()`). Keep the leading `httpOnly.isEmpty()` guard exactly as it is — the `EMBEDDED_JSON` filter never runs on an empty list.

**Verification:**

- `Grep` — `val pool = httpOnly.filter \{ it.source == HtmlMediaCandidate.Source.EMBEDDED_JSON \}` matches once in `CandidateSelectionPolicy.kt`.
- `Grep` — `httpOnly\.first\(\)` returns zero hits in `CandidateSelectionPolicy.kt` (replaced by `pool.first()`).
- `Grep` — `pool\.first\(\)` matches in `CandidateSelectionPolicy.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 3/3 PASS. Files: `app_v2/.../link/CandidateSelectionPolicy.kt` (+6 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for `CandidateSelectionPolicy.kt` via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

After Phase 02: when a Threads/IG post page emits both `EMBEDDED_JSON` (real post URL) and `OG_IMAGE`/`IMG_TAG`/`IMG_SRCSET` (channel preview, avatar, slide 1) candidates, the EMBEDDED_JSON candidate always wins regardless of probed size. Phase 03 handles the count-based Batch trigger for carousels and the SocialPreviewOnly bypass for IG photo posts.

---

## Rollback Plan

Revert the single file edit — `CandidateSelectionPolicy.choose` returns to size-first heuristic. No other surface affected.
