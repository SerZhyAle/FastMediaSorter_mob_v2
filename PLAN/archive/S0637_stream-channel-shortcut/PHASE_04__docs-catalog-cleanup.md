# Phase 04 - docs-catalog-cleanup

**Strategic spec:** [`../S0637_stream-channel-shortcut.md`](../S0637_stream-channel-shortcut.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-06-23
**Completed:** 2026-06-23

---

## Objective

Regenerate the class catalog, record the delivered capability, and publish the trilingual showcase sentence mandated by strategic §8.

---

## Prerequisites

- [ ] Phases 01-03 are ✅ Done.
- [ ] Working tree compiles.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |
| `docs/ALL_FEATURES.jsonl` | Modified | n/a |

> `docs/FEATURES*.md` is NOT edited here. Per CLAUDE.md §11 the public showcase is populated ONLY by `/skill-release` from the `ALL_FEATURES.jsonl` diff since the previous release. Recording the capability in `ALL_FEATURES.jsonl` (Step 04.2) is the per-spec deliverable; the trilingual showcase sentence from strategic §8 lands at the next release.

---

## Steps

### Step 04.1 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Then set role/status for the new class via `set.ps1` for `StreamShortcutPinManager` (role: home-screen pin-shortcut builder for one stream source; status: new). No `-NoFlavors` hint - the class lives in `src/main` and ships in every Streams-capable flavor.

**Verification:**

- `Grep` - `StreamShortcutPinManager` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-23 - Catalog scanned/rendered via close-and-log; set.ps1 set role + status=new for StreamShortcutPinManager. Verification PASS.

---

### Step 04.2 - Record the capability and dev log

**Files:** `docs/ALL_FEATURES.jsonl`, `dev/CHANGELOG.md` (via script)
**Depends on:** Step 04.1

**Prompt for developer:**

> Add one capability record via `pwsh -NoProfile -File scripts/all_features/add.ps1` (EN-only) describing: pin a chosen internet stream to the home screen as a one-tap launch shortcut. Confirm a `dev/CHANGELOG.md` entry exists for every file touched across Phases 01-03 (batch via `close-and-log.ps1 -DevLogs` if not already logged per phase).

**Verification:**

- `Grep` - a new record mentioning stream/home-screen shortcut in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-06-23 - ALL_FEATURES record internet-streams.home-screen-shortcut added (id corrected to area-slug prefix); validate.ps1 PASS (386 records). 8 dev logs batched via close-and-log.

---

### Step 04.3 - Showcase sentence (deferred to release) ⏭️

**Files:** none (release-owned)
**Depends on:** Step 04.2

**Prompt for developer:**

> Do NOT edit `docs/FEATURES*.md` here. Per CLAUDE.md §11 the public showcase is regenerated only by `/skill-release` from the `ALL_FEATURES.jsonl` diff. The capability recorded in Step 04.2 carries the strategic §8 sentence into that diff. The intended EN copy for the eventual showcase: "Pin a specific internet stream to your home screen: pick a channel in the Streams list and place a one-tap launch shortcut that plays that channel directly."

**Verification:**

- `Grep` - `docs/ALL_FEATURES.jsonl` contains the stream home-screen shortcut record (covered by Step 04.2); no per-spec edit to `docs/FEATURES*.md`.

**Status:** `[x]` skipped - release-owned (CLAUDE.md §11)

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done` (04.3 skipped - release-owned).
- [ ] `docs/ALL_FEATURES.jsonl` validates.
- [ ] `dev/CATALOG/app_v2.jsonl` contains `StreamShortcutPinManager`.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next action after merge: `/spec-check S0637`.

---

## Rollback Plan

Docs/catalog only - revert the phase commit; no runtime impact.
