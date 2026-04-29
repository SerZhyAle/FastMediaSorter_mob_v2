# Phase 03 — Optimization or Backlog Decision

**Strategic spec:** [`../S0014_vr-xr-cold-start.md`](../S0014_vr-xr-cold-start.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

Act on the Phase 02 recommendation: either implement the highest-ROI optimization identified during measurement, or formally close the task as backlog / won't-fix-now by updating the strategic spec with measured evidence.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Phase 02 Recommendation field is populated (`OPTIMIZE_NOW` or `BACKLOG`).
- [ ] If `OPTIMIZE_NOW`: the bottleneck stage and proposed change are identified from the measurement table.

---

## Files Touched

Branch A (`OPTIMIZE_NOW` — code change):

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| The file(s) identified as the bottleneck in Phase 02 | Modified | per-file ≤ 500 after edit (backup if > 500) |

Branch B (`BACKLOG` — documentation only):

| File                             | New / Modified | Line budget |
|----------------------------------|:--------------:|------------:|
| `PLAN/S0014_vr-xr-cold-start.md` |    Modified    |       ≤ 210 |

> Determine which branch applies by reading Phase 02 Recommendation. If `OPTIMIZE_NOW`, proceed with Branch A; if `BACKLOG`, proceed with Branch B. Only one branch executes.

---

## Steps

### Step 03.1 — Read Phase 02 recommendation

**Files:** `PLAN/S0014_vr-xr-cold-start/PHASE_02__measurement-run.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Read the Recommendation section of PHASE_02__measurement-run.md. Note which branch applies and which stage (if any) is the primary bottleneck.

**Verification:**

- `Grep "RECOMMENDATION:"` in `PLAN/S0014_vr-xr-cold-start/PHASE_02__measurement-run.md` returns exactly 1 match.

**Status:** `[ ]` not done

---

### Step 03.2 — Branch A: implement the identified optimization

**Files:** as identified in Phase 02 measurement table
**Depends on:** Step 03.1 (only if recommendation = `OPTIMIZE_NOW`)

**Prompt for developer:**

> Implement the optimization identified in Phase 02. Common candidates based on the cold-start chain:
>
> **Candidate 1 — Lazy HUD + panel swapchain init:**
> Move `VrHudRenderer.ensureSwapchainCreated()` and `VrInteractivePanelRenderer.ensureSwapchainCreated()` out of `initializeVrRenderPipeline()` into separate deferred calls triggered after the first frame renders. This avoids blocking the initial render on swapchain allocation. Gate behind a `@Volatile private var vrSwapchainsPending = true` flag; flush in `renderVrFrame` after the first successful frame.
>
> **Candidate 2 — Parallel GL resource init:**
> `renderer.initGl()` and `photoRenderer?.initGl()` are currently sequential. If they are independent (check their implementation), run them in two coroutines on the same GL thread via two-step dispatch. Only viable if the GL thread allows it.
>
> **Other candidates:** defer to whatever the Phase 02 measurement table identified as the dominant stage. Implement ONLY the highest-ROI change; do not combine multiple optimizations in one commit.
>
> After implementing, add a note in `initializeVrRenderPipeline` explaining what was deferred and why.

**Verification:**

- `[manual]` Re-run Phase 02 Steps 02.2–02.3 on device; confirm the target stage reduced by a measurable amount.
- `Grep "Log\.d\("` in any modified file returns 0 matches.
- Project compiles — run `/build`.

**Status:** `[ ]` not done

---

### Step 03.3 — Branch B: mark strategic spec as Backlog / Won't-fix-now

**Files:** `PLAN/S0014_vr-xr-cold-start.md`
**Depends on:** Step 03.1 (only if recommendation = `BACKLOG`)

**Prompt for developer:**

> In `PLAN/S0014_vr-xr-cold-start.md`, update the header block:
>
> 1. Change `**Status:** Approved` to `**Status:** Backlog / Won't-fix-now`.
> 2. Add a new section `## 13. Decision` immediately before the end of file:
>
> ```markdown
> ## 13. Решение
>
> **Outcome:** Backlog / Won't-fix-now — <YYYY-MM-DD>
>
> **Evidence:** Cold-start measured on Quest 3:
> - Total abs from xr_init_requested to first_frame_ready: XYZMS ms (cold) / ABC ms (warm).
> - Dominant stage: <stage name>, <duration> ms.
>
> **Rationale:** <copy justification from Phase 02 Recommendation section>
>
> **Next step:** Task remains in backlog. Re-evaluate if startup time exceeds 2 000 ms after future
> arch changes or if user reports increase.
> ```

**Verification:**

- `Grep "Backlog / Won't-fix-now"` in `PLAN/S0014_vr-xr-cold-start.md` returns at least 1 match.
- `Grep "## 13"` in that file returns 1 match.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done` or `[manual — deferred to human]`.
- [ ] Either: optimization code compiles and shows measured improvement (Branch A), or: strategic spec status updated to Backlog / Won't-fix-now (Branch B).
- [ ] If Branch A and code modified: `/build` passed.
- [ ] Dev log entry added for every modified file.
- [ ] If Branch A: `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.

---

## Handoff Notes to Next Phase

Phase 04 (docs-catalog-cleanup) proceeds regardless of which branch was taken.

---

## Rollback Plan

Branch A: revert the optimization commit. The timing markers from Phase 01 remain — they serve as permanent perf diagnostics.
Branch B: documentation only — revert the spec status change if the decision needs revisiting.
