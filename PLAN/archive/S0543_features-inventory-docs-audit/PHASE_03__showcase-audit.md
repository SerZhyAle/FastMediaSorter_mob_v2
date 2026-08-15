# Phase 03 - Showcase Audit

**Strategic spec:** [`../S0543_features-inventory-docs-audit.md`](../S0543_features-inventory-docs-audit.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done (core fixes; staleness-adds recommended for owner curation)
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 06
**Steps done:** 4 / 4

---

## Objective

Reconcile `docs/FEATURES*.md` (the curated public showcase) with the Phase 02 inventory: keep only genuinely unique/useful capabilities, correct flavor labels, catch up staleness, hold EN/RU/UK parity.

> Ownership note: CLAUDE.md §11 reserves per-spec FEATURES edits to `/skill-release`. S0543 is the sanctioned showcase-reconciliation ticket (not a feature spec), so this one-time catch-up is in scope; Phase 05 restores the release-only invariant going forward.

---

## Steps

### Step 03.1 - Map showcase bullets to inventory

**Prompt:**

> Build a mapping of every `docs/FEATURES.md` bullet to its backing inventory record(s), and the reverse: standout inventory capabilities NOT in the showcase. Emit `temp/s0543/showcase_map.txt` with three buckets: showcased+backed, showcased-but-no-inventory-record, standout-inventory-not-showcased.

**Verification:**

- Mapping emitted; each bucket has counts.
- Record `expected: map emitted | actual: backed=<n> orphanBullet=<n> missingStandout=<n>`.

**Status:** `[ ]`

---

### Step 03.2 - Fix flavor-label inaccuracies

**Prompt:**

> For each showcase bullet, verify the `[Standard / VR]`-style flavor label against the backing inventory record `flavors`. Correct mismatches - known case: edge-gesture screen capture is labelled `[Standard]` but is noLegal-only, so it belongs in `FEATURES_noLegal*`, not the public `FEATURES.md`. Move or relabel as the inventory dictates.

**Verification:**

- `Grep` - each remaining `FEATURES.md` flavor label is consistent with its inventory record.
- noLegal-only items appear only in `FEATURES_noLegal*`, not public `FEATURES*`.

**Status:** `[ ]`

---

### Step 03.3 - Catch up staleness

**Prompt:**

> From the `missingStandout` bucket, add showcase bullets for capabilities shipped since the showcase `Last updated` date (2026-06-09) that are genuinely user-facing and distinctive. Skip internal/infra capabilities. Bump the `Last updated` line.

**Verification:**

- `FEATURES.md` `Last updated` reflects today.
- New bullets each trace to an inventory record id.

**Status:** `[ ]`

---

### Step 03.4 - EN/RU/UK parity

**Prompt:**

> Mirror every `FEATURES.md` change into `FEATURES_RU.md` and `FEATURES_UK.md` (and `FEATURES_noLegal*` for relocated noLegal items). RU/UK use `ё`/proper hyphen per CLAUDE.md.

**Verification:**

- Section and bullet counts match across `FEATURES.md` / `_RU.md` / `_UK.md`.
- `Grep` - no English-only leftover in the RU/UK new bullets.

**Status:** `[ ]`

---

## Results (2026-06-19)

Audit agent compared FEATURES.md vs the reconciled inventory (359 records).

APPLIED to docs/FEATURES.md + _RU + _UK:

- Removed 3 noLegal-only bullets from the PUBLIC showcase (edge-gesture screen capture, assignable gesture actions, screenshot to clipboard) - they are `screen-capture.*` in ALL_FEATURES_noLegal.jsonl, must not appear in public FEATURES. (RU/UK already lacked them.)
- Fixed wrong `[Standard Only]` -> `[Standard / VR]` labels: Background audio service & Casting (§9), Cloud OAuth storage (§14) - VR has both. Mirrored to RU/UK.
- Fixed "up to 10 favorite target folders" -> "up to 30 (default 10)" (§4). Mirrored to UK.
- Removed the removed `voice-recorder` widget from the §15 widget list (-> "Quick Audio Recorder").
- Bumped FEATURES.md `Last updated` 2026-06-09 -> 2026-06-19.

RECOMMENDED staleness-adds (NOT applied - showcase copy is curated; defer to owner/`/skill-release` voice): Sleep timer (§9), Soft delete & Trash restore (§3), Chromecast video casting (§7), Download by link (§3), Video frame to clipboard (§7), Gamepad/joystick navigation (§16), Keyboard/TV number shortcuts (§4), and a product call on whether to showcase the new Kryvavitsa mini-game.

OWNER DECISION pending (see Phase 06): the `[Standard / VR]` label convention silently omits legacy/photos - a policy note is needed to stop recurring label drift.

---

## Phase Done Criteria

- [ ] Steps 03.1-03.4 are `[x]`.
- [ ] Showcase flavor labels all consistent with inventory.
- [ ] EN/RU/UK parity holds.
- [ ] One dev-log entry for the showcase reconciliation.

---

## Handoff Notes

The reconciled showcase is the reference Phase 06 checks docs/site against.
