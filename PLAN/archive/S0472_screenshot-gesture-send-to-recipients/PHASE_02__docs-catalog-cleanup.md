# Phase 02 - docs-catalog-cleanup

**Strategic spec:** [`../S0472_screenshot-gesture-send-to-recipients.md`](../S0472_screenshot-gesture-send-to-recipients.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-06-17
**Completed:** 2026-06-17

---

## Objective

Record the user-facing capability in the noLegal feature docs, then insert the S0472 device-verification probe and advance the ticket to `BlockNeedUserTest`.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done and the project compiles.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES_noLegal.md` | Modified (create if absent) | +1 entry |
| `docs/FEATURES_noLegal_RU.md` | Modified (create if absent) | +1 entry |
| `docs/FEATURES_noLegal_UK.md` | Modified (create if absent) | +1 entry |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/screencapture/ScreenshotGestureActionDispatcher.kt` | Modified | ≤ 102 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt` | Modified | ≤ 972 |

> This is a noLegal feature - the user-visible entry goes to gitignored `docs/FEATURES_noLegal*.md`, never to public `docs/FEATURES*.md` (which the `verifyNoPlatformNames` gate scans).

---

## Steps

### Step 02.1 - Document the feature in noLegal FEATURES (trilingual)

**Files:** `docs/FEATURES_noLegal.md`, `docs/FEATURES_noLegal_RU.md`, `docs/FEATURES_noLegal_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one feature line in EN/RU/UK describing the new screenshot-gesture behavior: after the edge gesture, the screenshot opens the curated «Send to..» dialog from the configured «Send file to» recipients (distinct from plain Share). Mirror the wording across the three files; create a file if it does not exist. Do not touch public `docs/FEATURES.md` / `_RU` / `_UK`.

**Verification:**

- `Grep` - the new entry's key phrase matches once in `docs/FEATURES_noLegal.md`.
- `Grep` - matches once in `docs/FEATURES_noLegal_RU.md`.
- `Grep` - matches once in `docs/FEATURES_noLegal_UK.md`.
- `Grep` - the same phrase returns zero hits in `docs/FEATURES.md` (not leaked to the public doc).
- Dev log entry added for each of the three files via `.\scripts\add_to_dev_log.ps1`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification 5/5 PASS. Added §10 bullet + changelog line for the "Send to recipients" post-capture action in FEATURES_noLegal EN/RU/UK; public docs/FEATURES.md untouched (0 hits). Dev logs recorded.

---

### Step 02.2 - Insert device-verification probe and enter BlockNeedUserTest

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/screencapture/ScreenshotGestureActionDispatcher.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> This step is bound to the `BlockNeedUserTest` transition - run it last, immediately before the status flip (per CLAUDE.md "Debug Verification Tags": the tag exists iff the spec is `BlockNeedUserTest`). Insert one probe at each entry of the new flow: in `ScreenshotGestureActionDispatcher.runPostSave`, inside the new `SEND_TO_RECIPIENTS` branch, add `Timber.d("S0472: dispatch SEND_TO_RECIPIENTS -> standalone viewer, uri=%s", savedUri)`; in `PhotoVideoStandaloneActivity.maybeRunAutoAction`, inside the `AUTO_ACTION_SEND_TO` branch, add `Timber.d("S0472: standalone auto-action SEND_TO -> curated send-to menu")`. Then advance the ticket: `pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id S0472 -Status BlockNeedUserTest -StatusNote 'noLegal: set a gesture direction to the new "send to recipients" action; trigger the gesture; confirm the curated Send-to dialog (from Player -> Send file to recipients) appears instead of the system chooser; confirm single-recipient direct send; confirm existing Share action unchanged.'`

**Verification:**

- `Grep` - `Timber.d("S0472:` matches exactly twice across `.kt` (one per entry).
- `Grep` - `S0472` does not appear in any `Timber.i(` / `Timber.w(` / `Timber.e(` (probe prefix reserved for `Timber.d`).
- `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id S0472 -Format json` shows `Status` = `BlockNeedUserTest`.
- Dev log entry added for both modified files.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification 4/4 PASS. Inserted 2 `Timber.d("S0472:` probes (dispatcher:59, standalone:623); 0 in i/w/e. noLegal debug build SUCCESSFUL (1m6s). Status flipped to BlockNeedUserTest via close-and-log (dev logs + func-log ADD + catalog). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] noLegal debug build passes - run `/build` (`.\a.ps1 nd` or `assembleNoLegalDebug`).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.
- [ ] Ticket is `BlockNeedUserTest` with exactly two `Timber.d("S0472:` probes present.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. After the user confirms on device, `/spec-check S0472` removes the two `S0472` probes and advances the spec to `Verified`.

---

## Rollback Plan

Revert the phase commit(s) - documentation and two debug probes only; no source behavior or schema change. If reverting before the status flip, ensure no `Timber.d("S0472:` line is left behind (the BlockNeedUserTest invariant requires tags iff that status).
