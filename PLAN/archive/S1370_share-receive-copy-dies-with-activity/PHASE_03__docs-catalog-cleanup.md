# Phase 03 - Docs and catalog cleanup

**Strategic spec:** [`../S1370_share-receive-copy-dies-with-activity.md`](../S1370_share-receive-copy-dies-with-activity.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** -
**Completed:** 2026-08-03

---

## Objective

Close the ticket mechanically: regenerate the class catalog, record the capability change and run the closure facade over the whole changed set.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |
| `docs/ALL_FEATURES.jsonl` | Modified | n/a |
| `dev/CHANGELOG.md` | Appended via script | n/a |

---

## Steps

### Step 03.1 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` once for the whole ticket. Record the exit code.

**Why:**

Strategic §5.1 changes the public shape of the persisted request, so the catalog index that every later lookup reads would otherwise describe a signature that no longer exists.

**Verification:**

- `catalog_sync.ps1` exits 0.
- `Grep` - `sourcesOwnedByOperation` matches in `dev/CATALOG/app_v2.jsonl` or the regenerated index reports `BrowseFileTransferRequest`.

**Status:** `[x]` done

---

### Step 03.2 - Record the delivered capability

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add one record via `pwsh -NoProfile -File scripts/all_features/add.ps1` describing that a copy started from a received share continues in the background after the receive screen closes, with a foreground notification and a result notification. Write it in English and mark the spec as S1370. Validate with `scripts/all_features/validate.ps1`.

**Why:**

Strategic §2 goals 1 and 2 change what the user can rely on - a share copy no longer needs the screen to stay open - and CLAUDE.md section 11 makes this inventory the single developer-facing record of a shipped capability.

**Verification:**

- `Grep` - `S1370` matches in `docs/ALL_FEATURES.jsonl`.
- `scripts/all_features/validate.ps1` exits 0.

**Status:** `[x]` done

---

### Step 03.3 - Run the closure facade over the whole changed set

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 03.2

**Prompt for developer:**

> Run `scripts/post-change.ps1` once with `-Files` naming every file this ticket changed across Phase 01 and Phase 02, `-ChangeType Mixed`, `-Module app_v2` and `-ScopeToFile`. Read the printed verdict and cite it. Fix any gate failure before re-running; a failed closure writes no changelog row.

**Why:**

CLAUDE.md section 12 requires the scoped closure to judge every gate against the whole changed set, and naming fewer files than were changed would certify only part of the ticket.

**Verification:**

- `post-change.ps1` prints `post-change: PASS` and exits 0.
- `Grep` - `dev/CHANGELOG.md` contains a row naming `ReceiveShareActivity.kt`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s) - regenerated indexes and appended log rows carry no runtime behaviour.
