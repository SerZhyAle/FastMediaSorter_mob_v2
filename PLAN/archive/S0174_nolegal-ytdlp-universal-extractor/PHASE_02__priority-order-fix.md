# Phase 02 — Priority Order Fix

**Strategic spec:** [`../S0174_nolegal-ytdlp-universal-extractor.md`](../S0174_nolegal-ytdlp-universal-extractor.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-05-12
**Completed:** 2026-05-12

---

## Objective

Insert `"ytdlp"` at position 0 of `CANONICAL_ORDER` in `LinkExtractionRegistry` so that `YtDlpExtractionStrategy` (to be added in Phase 04) runs before all other strategies. The id is inert in standard/lite/photos/legacy flavors — an unknown id simply matches no registered strategy and the sort falls back to `Int.MAX_VALUE` without error.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkExtractionRegistry.kt` is readable.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkExtractionRegistry.kt` | Modified | ≤ 30 (file is 26 LOC) |

---

## Steps

### Step 02.1 — Insert "ytdlp" at position 0 in CANONICAL_ORDER

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkExtractionRegistry.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `LinkExtractionRegistry.kt`, change the `CANONICAL_ORDER` companion object constant from:
>
> ```kotlin
> val CANONICAL_ORDER = listOf("site", "direct", "html", "dynamic")
> ```
>
> to:
>
> ```kotlin
> // S0174: "ytdlp" is registered only in the noLegal flavor DI module.
> // In other flavors this id matches no strategy — sort falls back to Int.MAX_VALUE, harmless.
> val CANONICAL_ORDER = listOf("ytdlp", "site", "direct", "html", "dynamic")
> ```
>
> No other changes to the file.

**Verification:**

- `Grep` — `"ytdlp"` appears in `LinkExtractionRegistry.kt`.
- `Grep` — `listOf("ytdlp", "site", "direct", "html", "dynamic")` matches exactly (order matters).
- `Grep` — `S0174` comment present on the line before or inline.

**Status:** `[x]` done

**Step Log:**

- 2026-05-12 — Verification 3/3 PASS. Files: LinkExtractionRegistry.kt (+2 LOC). Dev log recorded.

---

### Step 02.2 — Verify no other flavor is affected

**Files:** (no file change — verification only)
**Depends on:** Step 02.1

**Prompt for developer:**

> Confirm that the `CANONICAL_ORDER` change does not introduce a compile error in standard flavor. Run `/build` → `standardDebug`. The build must pass. The `"ytdlp"` id in the list does not require any class to exist at compile time — it is a plain string.

**Verification:**

- Build `standardDebug` passes with exit code 0.
- `Grep` — `"ytdlp"` appears in `LinkExtractionRegistry.kt` exactly once (inside `CANONICAL_ORDER`).

**Status:** `[x]` done

**Step Log:**

- 2026-05-12 — Verification 2/2 PASS. standardDebug BUILD SUCCESSFUL.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] `standardDebug` build passes — BUILD SUCCESSFUL (2026-05-12).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for `LinkExtractionRegistry.kt`.

---

## Handoff Notes to Next Phase

Phase 02 establishes: `CANONICAL_ORDER` = `["ytdlp", "site", "direct", "html", "dynamic"]`. Phase 03 may now implement the cookie bridge independently; Phase 04 depends on both 02 and 03.

---

## Rollback Plan

Revert the single-line change to `LinkExtractionRegistry.kt`. No data migration or user-facing surface changed.
