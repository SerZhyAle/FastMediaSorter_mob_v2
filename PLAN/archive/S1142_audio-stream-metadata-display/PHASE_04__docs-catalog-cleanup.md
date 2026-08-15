# Phase 04 - Docs, catalog, cleanup

**Strategic spec:** [`../S1142_audio-stream-metadata-display.md`](../S1142_audio-stream-metadata-display.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-07-23
**Completed:** 2026-07-23

---

## Objective

Regenerate the class catalog for the new public `NowPlayingMetadata` class and record the delivered capability in the feature inventory. No user-facing string or FEATURES showcase edits (strategic §8: showcase only via `/skill-release`).

---

## Prerequisites

- [ ] Phase 01-03 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |
| `docs/ALL_FEATURES.jsonl` | Appended (via tool) | - |

> No `strings.xml` edits (no new user-visible strings - the surfaces reuse existing metadata). No `docs/FEATURES*.md` edits (owned by `/skill-release`).

---

## Steps

### Step 04.1 - Regenerate class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` so the new `com.sza.fastmediasorter.core.playback.NowPlayingMetadata` class is indexed. Then set its role/status via `dev/CATALOG/scripts/set.ps1` if the sync leaves them `unknown`.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -ClassMatches "*NowPlayingMetadata*"` returns the class.

**Status:** `[x] done`

**Step Log:**

- 2026-07-23 - catalog_sync (via close-and-log scan) + set.ps1 role=model status=tested. NowPlayingMetadata indexed. PASS.

---

### Step 04.2 - Record capability in feature inventory

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 04.1

**Prompt for developer:**

> Record the shippable capability via `pwsh -NoProfile -File scripts/all_features/add.ps1` in area **Streams**: audio-stream now-playing (artist/title) shown in the system notification, lock screen, inline mini-control, and the active grid tile. Flavors from the `SUPPORT_STREAMS` gate: `standard,legacy,noLegal,vr` (lite/photos excluded). This is normally emitted by `/spec-dev` on the `Implemented` flip via `close-and-log.ps1 -FuncOp`; if that already recorded it, skip.

**Verification:**

- `Grep` - `S1142` present in `docs/ALL_FEATURES.jsonl`.

**Status:** `[x] done`

**Step Log:**

- 2026-07-23 - close-and-log -FuncOp ADD (Streams, flavors standard,legacy,noLegal,vr). S1142 present in ALL_FEATURES.jsonl. PASS.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Catalog regenerated; `NowPlayingMetadata` indexed.
- [ ] Feature inventory has an `S1142` record.
- [ ] Dev log entry added for the ticket.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Headline (notification/lock-screen live-track) is device-verified via `/spec-test-device`; ticket lands `BlockNeedUserTest` until then.

---

## Rollback Plan

Documentation/catalog only - regenerate or revert the inventory append. No code rollback.
