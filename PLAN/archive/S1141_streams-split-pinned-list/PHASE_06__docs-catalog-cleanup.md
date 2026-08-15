# Phase 06 - Docs, catalog, debug tags, closure

**Strategic spec:** [`../S1141_streams-split-pinned-list.md`](../S1141_streams-split-pinned-list.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all prior phases
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-07-23
**Completed:** 2026-07-23

**Step Log:**

- 2026-07-23 - Step 06.1 PASS: 3 S1141 debug tags (applyMode, toggle, sections-wired); 0 in Timber.i/w/e; `.\a.ps1 dq` Build Successful exit 0.
- 2026-07-23 - Step 06.2 PASS: catalog_sync app_v2 regenerated; StreamsSectionsManager indexed; role/status set (new).
- 2026-07-23 - Step 06.3 PASS: ALL_FEATURES record streams.pinned-main-split-collapsible-sections added (standard,legacy,noLegal,vr); close-and-log flipped journal -> BlockNeedUserTest with device StatusNote, 5 batched dev logs.

---

## Objective

Insert the S1141 device-test debug tags at the changed flow entries, regenerate the class catalog for the new manager, record the shipped capability in the developer inventory, and run mechanical closure - leaving the ticket ready for `BlockNeedUserTest`.

---

## Prerequisites

- [ ] Phases 01-05 are ✅ Done and the standard debug build is green.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt` | Modified | ≤ 1085 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamsSectionsManager.kt` | Modified | ≤ 305 |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |
| `docs/ALL_FEATURES.jsonl` | Modified | +1 record |

---

## Steps

### Step 06.1 - Insert S1141 debug verification tags

**Files:** `ui/streams/StreamsActivity.kt`, `ui/streams/helpers/StreamsSectionsManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Insert one `Timber.d("S1141: <entry>")` per changed flow entry (CLAUDE.md §2 - the ticket is about to enter `BlockNeedUserTest`): (a) in `StreamsSectionsManager.applyMode` - log the pinned/unpinned counts and pinned-section visibility; (b) in the header-collapse toggle - log which section collapsed/expanded; (c) in `StreamsActivity` where the sections manager is wired. One tag per flow entry, not per line. Keep each line ≤120 chars (detekt, Rule 19). The `S1141:` prefix is reserved for these temporary probes - never in a persisted `Timber.i/w/e`.

**Verification:**

- `Grep` - `Timber.d("S1141:` matches at least twice across the two files.
- `Grep` - no `S1141:` inside any `Timber.i(`/`Timber.w(`/`Timber.e(` call.
- `.\a.ps1 dq` - `BUILD SUCCESSFUL`, exit 0.

**Status:** `[ ]` not done

---

### Step 06.2 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 06.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` once (scan + render) so the new `StreamsSectionsManager` class is indexed. Fill its `role` + `status` via `dev/CATALOG/scripts/set.ps1` if the sync leaves them blank.

**Verification:**

- `Grep` - `StreamsSectionsManager` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[ ]` not done

---

### Step 06.3 - Record the capability + mechanical closure

**Files:** `docs/ALL_FEATURES.jsonl`, `dev/CHANGELOG.md` (via script)
**Depends on:** Step 06.2

**Prompt for developer:**

> Record the shipped capability in `docs/ALL_FEATURES.jsonl` via `scripts/all_features/add.ps1` - area "Streams", flavors `standard,legacy,noLegal,vr` (the `SUPPORT_STREAMS` set), EN-only: the streams screen splits into a pinned section (shown when ≥1 channel is pinned) above the main section, each independently scrollable with a collapsible header, sharing one filter/order/display mode, with a single active playback. Then run mechanical closure through the facade: `pwsh -NoProfile -File scripts/post-change.ps1 -File "app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamsSectionsManager.kt" -Target "S1141" -Description "Streams split into pinned/main collapsible sections" -ChangeType Kotlin -Module app_v2 -ScopeToFile` (batch the dev-log for the whole ticket; do not per-file edit `dev/CHANGELOG.md`).

**Verification:**

- `Grep` - a `Streams` record referencing the split/collapsible sections present in `docs/ALL_FEATURES.jsonl`.
- `post-change.ps1` exits 0 (gates pass under `-ScopeToFile`).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] Project compiles - `/build` (`.\a.ps1 dq`) `BUILD SUCCESSFUL`.
- [ ] Debug tags present (ticket entering `BlockNeedUserTest`).
- [ ] `docs/ALL_FEATURES.jsonl` carries the Streams capability record.
- [ ] Ticket advanced to `BlockNeedUserTest` via `close-and-log.ps1 -StatusNote '<device checks>'` (owned by `/spec-dev` closure, not a plan step).

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. On-device verification (both orientations + w600, pinned present/absent, collapse each section, single playback across sections, grid+list) is the `BlockNeedUserTest` gate, handled by the parent loop's `/spec-sweep`.

---

## Rollback Plan

Revert the phase commit - tags/docs/catalog are additive; the feature code from Phases 01-05 stays intact.
