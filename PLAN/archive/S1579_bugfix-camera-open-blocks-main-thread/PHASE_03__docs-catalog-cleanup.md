# Phase 03 - Docs and catalog cleanup

**Strategic spec:** [`../S1579_bugfix-camera-open-blocks-main-thread.md`](../S1579_bugfix-camera-open-blocks-main-thread.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-08-11
**Completed:** 2026-08-11

---

## Objective

Close the ticket mechanically: regenerate the class catalog, run the closure facade over the whole changed set, and record the debug probes the device check needs.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |
| `dev/CHANGELOG.md` | Appended via script | n/a |

---

## Steps

### Step 03.1 - Insert the device-verification probes

**Files:** the files changed in Phase 01 and Phase 02
**Depends on:** - start of phase

**Prompt for developer:**

> Insert one `Timber.d("S1579: <entry point>")` per changed flow entry - the resolved output answer, the quick-capture scratch directory answer, and the extension-map read in `bindToLifecycle` reporting hit or miss. One tag per flow entry, not per modified line, and only because the ticket is about to enter `BlockNeedUserTest`.

**Why:**

Strategic §4 requires the device run to prove the four sites are silent under StrictMode while a live violation from another path proves StrictMode fired at all, and the probes are what identify the flows in that log.

**Verification:**

- `Grep` - `Timber.d("S1579:` returns at least three hits across the changed files.
- `Grep` - no `S1579` string in any `Timber.i`, `Timber.w` or `Timber.e` call.

**Status:** `[x]` done

---

### Step 03.2 - Run the closure facade over the whole changed set

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CHANGELOG.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> Run `scripts/post-change.ps1` once with `-Files` naming every `.kt` file changed by Phases 01-03, `-ChangeType Kotlin`, `-Module app_v2` and `-ScopeToFile`, and read its verdict. `docs/FEATURES*.md` stays untouched: the strategic spec is a bugfix and carries no showcase sentence.

**Why:**

not stated in strategic spec

**Verification:**

- `post-change.ps1` exits 0 and prints `post-change: PASS` (or `PASS WITH ADVISORIES` with each advisory named).
- `Grep` - `dev/CHANGELOG.md` carries a row naming S1579.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `scripts/catalog_sync.ps1 -Module app_v2`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
