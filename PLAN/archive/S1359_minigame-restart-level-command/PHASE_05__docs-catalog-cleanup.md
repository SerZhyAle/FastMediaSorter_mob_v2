# Phase 05 - Docs, inventory and catalog cleanup

**Strategic spec:** [`../S1359_minigame-restart-level-command.md`](../S1359_minigame-restart-level-command.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Record the delivered capability in the developer inventory and close the ticket mechanically.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified | append one record |
| `dev/CHANGELOG.md` | Modified | via script only |
| `dev/CATALOG/app_v2.jsonl` | Modified | regenerated |

> `docs/FEATURES*.md` is deliberately absent - per CLAUDE.md §11 it is `/skill-release`-owned.

---

## Steps

### Step 05.1 - Record the capability in the developer inventory

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Append one record through `pwsh -NoProfile -File scripts/all_features/add.ps1` in the `Game` area describing the command in English: the player can restart the current level at will during play, at the same score penalty as being caught. Set `-Spec S1359`. Take the flavor list from strategic §3 - the mini-game compiles into every flavor and is gated only by the runtime `embeddedGameEnabled` setting. Validate with `scripts/all_features/validate.ps1` and require exit 0.

**Why:**

Strategic §8 states the change is user-visible, and CLAUDE.md §11 makes `docs/ALL_FEATURES.jsonl` the per-spec home for that record.

**Verification:**

- `Grep` - `S1359` present in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-06 - Added `game.voluntary-level-restart`; `S1359` matches once in `docs/ALL_FEATURES.jsonl`; validate exit 0, 649 records. Flavors `standard,lite,photos,legacy` copied from the four sibling `game.*` records rather than restated from the strategic text.

---

### Step 05.2 - Close through the post-change facade

**Files:** `dev/CHANGELOG.md`, `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 05.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/post-change.ps1` once for the ticket, naming every changed file with `-Files` and adding `-ScopeToFile`, `-ChangeType Mixed -Module app_v2` and `-KeyPrefix "game_restart"`. Read the printed verdict: only a bare `post-change: PASS` counts as clean, and exit 2 means the gates did not run.

**Why:**

not stated in strategic spec

**Verification:**

- `pwsh -NoProfile -File scripts/post-change.ps1 ..` exits 0 and prints `post-change: PASS`.
- `Grep` - `S1359` present in `dev/CHANGELOG.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-06 - First run closed `PASS WITH ADVISORIES (1)`: the document-registry gate wanted `feature-inventory` acknowledged and named `docs/ALL_FEATURES.schema.json` as the sibling to check. The schema needs no edit - the new record uses only existing fields and `validate.ps1` already passed against it - so the second run added `-RegistryAck 'feature-inventory'` and closed a bare `post-change: PASS`. The repeat did not double the changelog row: `[DEV_LOG] SKIP duplicate`.
- 2026-08-06 - `S1359` present in `dev/CHANGELOG.md`. Scoped detekt `PASS [scoped]` - two files carry new findings project-wide, neither among the changed set, which is the concurrent-session WIP this tree always has.

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entry added via the facade.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] Phase-boundary audit - skipped, doc-only phase.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s) - documentation and catalog only.
