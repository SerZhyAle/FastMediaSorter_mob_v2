# Phase 04 - Docs and closure

**Strategic spec:** [`../S1445_atlas-tile-random-access.md`](../S1445_atlas-tile-random-access.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Document the container for third-party consumers, record the fix in the capability inventory, and hand the ticket to the device verdict.

---

## Prerequisites

- [ ] Phase 02 and Phase 03 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `delivery/stream-catalog/README.md` | Modified | - |
| `docs/ALL_FEATURES.jsonl` | Modified (via script) | - |

---

## Steps

### Step 04.1 - Document the tile container

**Files:** `delivery/stream-catalog/README.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Extend the existing atlas section with the tile container: asset names, that entries are the slot index as a plain decimal string with no extension, that entries are stored uncompressed, that the `url -> index` sidecar is shared with the sprite sheet, and that the sheet remains published unchanged for consumers who prefer it.

**Why:**

Strategic §2 goal 5 and ADR-2 keep the third-party contract intact, which only holds if the second container a consumer can now see in the release is described rather than left to be guessed.

**Verification:**

- `Grep` - `channel-preview-tiles` present in `delivery/stream-catalog/README.md`.
- `Grep` - the sprite-sheet section is still present and unedited in substance.

**Status:** `[x]` done

---

### Step 04.2 - Record the capability fix

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add the Streams `FIX` record through `scripts/all_features/add.ps1`, stating that channel previews and station logos now appear as soon as the grid is shown instead of one at a time.

**Why:**

Strategic §8 assigns this ticket a `FIX` record in the Streams area, and the release showcase is generated from that inventory diff.

**Verification:**

- `Grep` - `S1445` present in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` - expected exit 0.

**Status:** `[x]` done

---

### Step 04.3 - Insert the device probe and park the verdict

**Files:** `ChannelPreviewAtlasSlicer.kt` (or the reader), `StreamLogoAtlasSlicer.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Add one `Timber.d("S1445: <desc>")` probe at the entry of the changed read path per payload, build the standard debug APK once so code and probes are validated together, then set the ticket to `BlockNeedUserTest` with a status note naming the exact device scenario: update the streams catalog, accept the artwork download, switch to the video filter, and confirm the visible screen fills with pictures at once rather than one by one.

**Why:**

Strategic §11 criterion 7 is a visual verdict that only the owner's device can render, and CLAUDE.md binds the probe lifetime to exactly that status.

**Verification:**

- `Grep` - `Timber.d("S1445:` matches once per changed read path.
- `pwsh -NoProfile -File ./a.ps1 d` - expected exit 0.
- `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id S1445 -Format json` - status `BlockNeedUserTest` with a non-empty status note.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [ ] Dev log entry added for the files in "Files Touched".
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. The probes must be deleted when the ticket leaves `BlockNeedUserTest`.

---

## Rollback Plan

Revert the phase commit - documentation and inventory only, plus probe lines that are temporary by contract.
