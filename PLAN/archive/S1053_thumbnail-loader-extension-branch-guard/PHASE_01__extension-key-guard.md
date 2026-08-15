# Phase 01 - Extension and binary thumbnail key guard

**Strategic spec:** [`../S1053_thumbnail-loader-extension-branch-guard.md`](../S1053_thumbnail-loader-extension-branch-guard.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none
**Blocks:** Phase 02
**Steps done:** 1 / 1
**Completed:** 2026-07-25

---

## Objective

Let the existing holder key suppress repeated static thumbnail rendering without changing thumbnail inputs or asynchronous favicon handling.

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/AdapterThumbnailLoader.kt` | Modified | ≤ 780 |

## Steps

### Step 01.1 - Apply the existing key guard before static branches

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/AdapterThumbnailLoader.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Keep the stream-favicon early return and add the existing common thumbnail key plus equality guard before audio, text, office-document, and binary rendering. Keep directories as a null-returning exception. Return the computed key after each static branch paints its image, so the existing holder contract records it. Do not alter the key fields, image/video/PDF/EPUB branches, favicon behavior, or binary-generator arguments.

**Verification:**

- `Grep` - the common `newKey` declaration occurs exactly once in the loader.
- `Grep` - `if (newKey == lastLoadedKey) return null` occurs before the audio branch.
- `Grep` - each static branch returns `newKey` after setting its image.
- `Grep` - `file.resourceId == SyntheticResourceIds.STREAM` occurs before the common key guard.
- Run `pwsh -NoProfile -File a.ps1 fk` - exit 0.

**Status:** `[x] done`

**Step Log:**

- 2026-07-25 - Verification 5/5 PASS. The common key guard now precedes all static branches; each returns the key after rendering.

## Phase Done Criteria

- [x] Step 01.1 is `[x] done`.
- [x] Kotlin compile check passes.
- [x] Dev log entry added for the modified Kotlin file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.
- [x] Phase-boundary audit reports no unresolved P0/P1 finding.

## Phase-boundary audit

- **P0:** none - the change does not add lifecycle, I/O, storage, or ownership paths.
- **P1:** none - the common key preserves refresh invalidation and keeps asynchronous favicon handling outside the guard.
- **P3:** the closure detekt run reports pre-existing `LongParameterList` and `ImportOrdering` findings in this file's earlier favicon plumbing. They are outside the S1053 edit and remain for their owning work.

## Rollback Plan

Restore the previous branch ordering in the single loader; no stored data or UI state changes.
