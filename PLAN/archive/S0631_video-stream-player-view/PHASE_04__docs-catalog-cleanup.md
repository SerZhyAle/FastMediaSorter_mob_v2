# Phase 04 - Docs & catalog cleanup

**Strategic spec:** [`../S0631_video-stream-player-view.md`](../S0631_video-stream-player-view.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 02, Phase 03
**Blocks:** none - final phase
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Record the shipped capability, regenerate the class catalog, and close the dev log. No `docs/FEATURES*.md`
edits (release-only).

---

## Prerequisites

- [ ] Phase 02 and Phase 03 are ✅ Done.
- [ ] Project compiles (`/build`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified (via script) | - |
| `dev/CATALOG/app_v2.jsonl` | Regenerated (gitignored) | - |
| `dev/CHANGELOG.md` | Modified (via script) | - |

---

## Steps

### Step 04.1 - Record the capability in `ALL_FEATURES.jsonl`

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one EN-only capability record via `pwsh -NoProfile -File scripts/all_features/add.ps1` describing:
> the player shows a stream-tailored control set for live video streams (fullscreen, snapshot, rotation,
> info, PiP, video-control dialog, cast, send-to) and «Send to..» shares the stream link. Then validate
> with `scripts/all_features/validate.ps1`. Do NOT edit `docs/FEATURES*.md` - the public showcase is
> populated only by `/skill-release` from the `ALL_FEATURES` diff.

**Verification:**

- `Grep` - a new record mentioning the stream control set / share-link exists in `docs/ALL_FEATURES.jsonl`.
- Script: `scripts/all_features/validate.ps1` exits 0.

**Status:** `[x]` done

---

### Step 04.2 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 04.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` to rescan + re-render after the
> Kotlin changes. The index is gitignored - regenerate, do not commit.

**Verification:**

- Script: `catalog_sync.ps1 -Module app_v2` exits 0 and reports the scan/render lines.

**Status:** `[x]` done

---

### Step 04.3 - Close the dev log for the ticket

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 04.2

**Prompt for developer:**

> Ensure `dev/CHANGELOG.md` has one logical entry for the S0631 change batch via
> `.\scripts\add_to_dev_log.ps1` (batch the touched files). Confirm every modified source file from
> Phases 01-03 is represented.

**Verification:**

- `Grep` - `S0631` (or the change description) present in `dev/CHANGELOG.md`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] `docs/ALL_FEATURES.jsonl` validates (`scripts/all_features/validate.ps1` exit 0).
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `dev/CHANGELOG.md` has the S0631 entry.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next: `/spec-dev S0631` to implement, then `/spec-check S0631`.

---

## Rollback Plan

Documentation/catalog only - revert the `ALL_FEATURES.jsonl` and `CHANGELOG.md` entries; the catalog index
regenerates from source.
