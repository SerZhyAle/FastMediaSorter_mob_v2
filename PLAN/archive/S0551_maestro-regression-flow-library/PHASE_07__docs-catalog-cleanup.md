# Phase 07 - Docs & Catalog Cleanup

**Strategic spec:** [`../S0551_maestro-regression-flow-library.md`](../S0551_maestro-regression-flow-library.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress
**Depends on:** Phase 02, 03, 04, 05
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** -
**Completed:** -

---

## Objective

Delete the phantom/slop docs, rewrite `maestro/README.md` to the real on-disk flow set, and run the dev-log closure. No catalog regen (no app-runtime API change).

---

## Prerequisites

- [ ] Phases 02-05 ✅ Done (final flow set on disk known).
- [ ] Cleanup inventory: `research/06__doc-cleanup.md`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `maestro/*.md`, `maestro/*.txt` (delete set) | Deleted | - |
| `maestro/README.md` | Modified | ≤ 200 |
| `dev/CHANGELOG.md` | Modified (via script) | - |

---

## Steps

### Step 07.1 - Delete phantom and slop docs

**Files:** `maestro/FEATURE_TESTS_CATALOG.md`, `maestro/FEATURE_TESTS_COMPLETE.md`, `maestro/IMPLEMENTATION_COMPLETE.md`, `maestro/SETUP_COMPLETE.md`, `maestro/ISSUE_RESOLVED.md`, `maestro/MAESTRO_STATUS.txt`, `maestro/QUICK_REFERENCE.txt`, `maestro/help.txt`, `maestro/MAESTRO_QUICK_START.md`, `maestro/QUICK_START.md`, `maestro/MAESTRO_SETUP_GUIDE.md`, `maestro/MAESTRO_INTEGRATION.md`, `maestro/WINDOWS_MANUAL_INSTALL.md`, `maestro/WINDOWS_QUICK_INSTALL.md`, `maestro/PATH_FIX_GUIDE.md`, `maestro/FIX_WRONG_PACKAGE.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Delete the 16 files listed in `research/06` DELETE set. Before deleting `PATH_FIX_GUIDE.md` / `FIX_WRONG_PACKAGE.md`, fold any still-live tip into `TROUBLESHOOTING.md`. Keep the 8 KEEP docs.

**Verification:**

- `Glob` - `maestro/FEATURE_TESTS_CATALOG.md` does not exist.
- `Glob` - `maestro/*_COMPLETE.md` returns zero files.
- `Glob` - `maestro/README.md` and `maestro/TROUBLESHOOTING.md` still exist.

**Status:** `[x]` done

---

### Step 07.2 - Rewrite `README.md` to the real flow set

**Files:** `maestro/README.md`
**Depends on:** Step 07.1

**Prompt for developer:**

> Rewrite the directory map and test tables to reflect the actual on-disk flows after Phases 02-05: `smoke/` (app_launch, local_browse, 3d-video×2, video_prefetch), `critical/` (file_operations, settings, video_offload), `features/` (browse, files, player, slideshow, edge), `_shared/`. Document the revived runner contract (suite/category/flow selection, `-DeviceId`, `-Json`, off-context log, exit codes) and the oracle convention pointer. Remove any reference to the deleted phantom `features/audio|documents|images|..` catalog. No claim of tests that are not on disk.

**Verification:**

- `Grep` - `features/player` referenced in `maestro/README.md`.
- `Grep` - `FEATURE_TESTS_CATALOG` returns zero hits across `maestro/` (no dangling link).
- `Grep` - `run-tests.ps1` exit-code contract documented (`exit` codes or `-Json`).

**Status:** `[x]` done

---

### Step 07.3 - Dev-log closure for the whole ticket

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 07.2

**Prompt for developer:**

> Run `./scripts/add_to_dev_log.ps1` once for the ticket-level change (revived Maestro suite + prerelease integration + doc cleanup), batching the multi-file set. No `docs/ALL_FEATURES.jsonl` entry (test infra, not a shippable user capability). No `catalog_sync.ps1` (no app-runtime class change).

**Verification:**

- `Grep` - a `dev/CHANGELOG.md` entry referencing `maestro` and `S0551` (or the dev-log script's recorded line) present.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 07.*` is `[x] done`.
- [x] `maestro/` doc set reduced to the 8 KEEP files; no `*_COMPLETE` / `*_RESOLVED` / phantom catalog remain.
- [x] `README.md` lists only flows that exist on disk.
- [ ] Full suite green end-to-end: `pwsh -NoProfile -File maestro/run-tests.ps1 -Suite all -Json` → PASS on a clean seeded emulator.
- [x] Dev log entry recorded.

**Validation note:** static documentation cleanup checks pass. Full on-device suite proof remains pending.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next: `/spec-check S0551` to flip the strategic spec to `Verified`.

---

## Rollback Plan

Revert the phase commit; deleted docs return and the README reverts. No app surface touched.
