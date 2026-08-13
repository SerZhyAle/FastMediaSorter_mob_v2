# Phase 04 - Docs, catalog & cleanup

**Strategic spec:** [`../S0444_player-send-email.md`](../S0444_player-send-email.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** -
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Record the user-facing feature in FEATURES (EN/RU/UK), regenerate the class catalog for the new send-action/Hilt-module class(es), and ensure dev-log coverage. Unlike S0452 (no direct user effect), S0444 ships a visible command, so FEATURES IS updated.

---

## Prerequisites

- [ ] Phases 01-03 are ✅ Done and the build is green.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified (one entry) | - |
| `docs/FEATURES_RU.md` | Modified (one entry) | - |
| `docs/FEATURES_UK.md` | Modified (one entry) | - |
| `dev/CATALOG/app_v2.jsonl` (regenerated, gitignored) | Regenerated | - |
| `dev/CHANGELOG.md` (via script) | Appended | - |

---

## Steps

### Step 04.1 - FEATURES entry (EN/RU/UK)

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one entry in the player/share section of `docs/FEATURES.md` and its `_RU`/`_UK` mirrors: a "Send to Email" command that attaches the current file to a new email and is toggled in Player settings ("Send file to.." group). Match the surrounding tone/format and the phrasing of the existing Telegram/Keep send entries. RU/UK use `..` and ё where grammatically correct. This is a standard published-build feature, so it goes in `docs/FEATURES*.md` (NOT `FEATURES_noLegal*`).

**Verification:**

- `Grep -n "Email"` over `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md` - one new send-to-email entry each.

**Status:** `[ ]` not done

---

### Step 04.2 - Regenerate catalog + set roles

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 04.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Then set `role` + `status` via `set.ps1` for the new public class(es) from Phase 02 (`EmailShareInvoker` if it was created as a standalone class; `EmailShareTargetModule`). If `EmailShareInvoker` was folded into `PlayerShareManager`/`SystemShareInvoker`, only the Hilt module is new.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "*EmailShareTargetModule*"` returns the class.
- If standalone: `query.ps1 -ClassMatches "*EmailShareInvoker*"` returns the class.

**Status:** `[ ]` not done

---

### Step 04.3 - Dev-log coverage for all modified files

**Files:** `dev/CHANGELOG.md` (via `add_to_dev_log.ps1`)
**Depends on:** Step 04.2

**Prompt for developer:**

> Ensure every file touched across Phases 01-03 (share invoker/module, strings x3, menu xml, planner, availability updater, controller, callback impl, PlayerActivity, share manager) has a `dev/CHANGELOG.md` entry via `.\scripts\add_to_dev_log.ps1`. Do not hand-edit `dev/CHANGELOG.md`.

**Verification:**

- `Grep -n "share_to_email|EmailShareTargetModule|SEND_TO_EMAIL|sendCurrentFileToEmail"` over `dev/CHANGELOG.md` - the Email feature appears in the dev-log.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] `docs/FEATURES.md` + `_RU` + `_UK` each have the send-to-email entry.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated; new class(es) queryable.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next: `/spec-check S0444`, then on-device verification (`BlockNeedUserTest` set centrally if desired - debug tags inserted only at that transition).

---

## Rollback Plan

FEATURES edits are append-only text; catalog is regenerable; dev-log is append-only. No rollback needed.
