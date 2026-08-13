# Phase 03 - Partial rebind on status/pin change

**Strategic spec:** [`../S1169_stream-thumbnail-update-policy.md`](../S1169_stream-thumbnail-update-policy.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04, Phase 06
**Steps done:** 2 / 2
**Started:** 2026-07-24
**Completed:** 2026-07-24

**Step Log:**

- 2026-07-24 - Step 03.1: PASS (grid getChangePayload x1, payloads override x1, bindStatusOnly x2). compileStandardDebugKotlin EXIT=0.
- 2026-07-24 - Step 03.2: PASS (StreamAdapterPayloads.kt exists + referenced in both adapters, source getChangePayload x1). detekt scoped PASS on all 3 files.

---

## Objective

Give `StreamGridAdapter` and `StreamSourceAdapter` a `getChangePayload` so a change limited to `lastPlayOutcome` / `lastPlayOutcomeAt` (or `pinned`) repaints only the status bullet (and pin badge), never re-running the full `bind()` - no `setImageBitmap`, no `PopupMenu`/listener rebuild, no favicon re-decode. Mirrors the in-repo `MediaFileDiffCallback` precedent.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] `StreamGridAdapter.DIFF` / `StreamSourceAdapter` `DiffUtil.ItemCallback` unchanged from main.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamGridAdapter.kt` | Modified | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamSourceAdapter.kt` | Modified | ≤ 420 |

---

## Steps

### Step 03.1 - Payload diff + partial bind in `StreamGridAdapter`

**Files:** `ui/streams/StreamGridAdapter.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `DIFF`, override `getChangePayload(old, new)`: if the two items differ only in `lastPlayOutcome`/`lastPlayOutcomeAt` (guard with the `old.copy(lastPlayOutcome = new.lastPlayOutcome, lastPlayOutcomeAt = new.lastPlayOutcomeAt) == new` idiom from `MediaFileDiffCallback`), return a `PAYLOAD_STATUS` constant; if they differ only in `pinned`, return `PAYLOAD_PIN`; else return null (full rebind). Add `override fun onBindViewHolder(holder, position, payloads)`: when `payloads` contains `PAYLOAD_STATUS`, call only `holder.bindStatusOnly(getItem(position).lastPlayOutcome)`; `PAYLOAD_PIN` -> only toggle `tvPinBadge`; empty payloads -> delegate to the full `onBindViewHolder(holder, position)`. Expose a `VH.bindStatusOnly(outcome)` that reuses the existing `bindPlayStatus` body without touching the frame `ImageView`, menu, or click listeners. Do not clear/reset the frame image on a status payload.

**Verification:**

- `Grep` - `getChangePayload` present in `StreamGridAdapter.kt`.
- `Grep` - `onBindViewHolder(holder: VH, position: Int, payloads:` present.
- `Grep` - `bindStatusOnly` present.
- `.\a.ps1 fk` compiles.

### Step 03.2 - Same payload diff in `StreamSourceAdapter`

**Files:** `ui/streams/StreamSourceAdapter.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Apply the identical `getChangePayload` + `onBindViewHolder(..., payloads)` + `bindStatusOnly` treatment to the LIST adapter's `DiffUtil.ItemCallback` and view holder, reusing the same payload constant names (declare them once in a shared `object StreamAdapterPayloads` under `ui/streams/` and reference from both adapters, to avoid drift). LIST mode stays favicon-only (no frame) - the status payload repaints only the row's status bullet; a `pinned` payload repaints only the pin affordance. Full rebind still applies for any other field change.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamAdapterPayloads.kt` exists (shared constants).
- `Grep` - `StreamAdapterPayloads` referenced in both `StreamGridAdapter.kt` and `StreamSourceAdapter.kt`.
- `Grep` - `getChangePayload` present in `StreamSourceAdapter.kt`.
- `.\a.ps1 fk` compiles.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` is `[x] done`.
- [ ] `/build` passes.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry for all touched files.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new `StreamAdapterPayloads` type) - deferred to Phase 06 catalog regen.
- [ ] Phase-boundary audit: no listener registered without symmetric removal (payload path attaches none); frame `ImageView` never cleared on a status-only payload.

---

## Handoff Notes to Next Phase

A genuine OK<->UNKNOWN transition (the only outcome write that survives Phase 01) now repaints just the status bullet. Phase 04 addresses the remaining flicker sources (prewarm timing, panel chips).

---

## Rollback Plan

Revert the phase commit(s) - adapters fall back to full-rebind diffing; no data or schema change.
