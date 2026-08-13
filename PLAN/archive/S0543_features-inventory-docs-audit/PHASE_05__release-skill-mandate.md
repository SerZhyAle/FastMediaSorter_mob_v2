# Phase 05 - Release-Skill Mandate

**Strategic spec:** [`../S0543_features-inventory-docs-audit.md`](../S0543_features-inventory-docs-audit.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none (dependency-free - may run first)
**Steps done:** 3 / 3

---

## Objective

Make `/skill-release` reliably populate `docs/FEATURES*` from the `ALL_FEATURES` release diff - all three locales plus noLegal, with flavor labels taken from inventory `flavors`, and the `Last updated` line bumped. This restores the release-only invariant after the Phase 03 catch-up.

---

## Audit outcome (summary)

`/skill-release` Step 12b was **already solid**: it reads `scripts/all_features/diff.ps1 -From $PREV_TAG`, promotes standout records into `FEATURES.md` + `_RU` + `_UK` in lockstep, routes noLegal-only items to gitignored `FEATURES_noLegal*`, and runs the `[INVENTORY MISSED]` sanity check. Two narrow gaps fixed this phase.

---

## Steps

### Step 05.1 - Confirm the diff-to-showcase step

**Verification:**

- `Grep` `.claude/commands/skill-release.md` - `diff.ps1` + FEATURES population present (Step 12b, lines 376-398). PASS.

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - Confirmed Step 12b reads `diff.ps1 -From $PREV_TAG` and promotes standout records into the showcase. It is the single sanctioned place `FEATURES*` is edited.

---

### Step 05.2 - Enforce locale + noLegal + flavor-label coverage

**Files:** `.claude/commands/skill-release.md`

**Verification:**

- RU/UK lockstep + noLegal routing already present. PASS.
- Flavor-label-from-inventory rule added. PASS (this phase).

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - Added Step 12b point 3: "Set each bullet's flavor label from the inventory record's `flavors` field - never guess; noLegal-only must not appear in public FEATURES*." Directly prevents recurrence of the screen-capture `[Standard]` mislabel found in Phase 03 scope.

---

### Step 05.3 - Parity + freshness check

**Files:** `.claude/commands/skill-release.md`

**Verification:**

- `Last updated` bump + EN/RU/UK count parity confirmation added. PASS (this phase).

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - Added Step 12b point 5: bump `Last updated:` in `FEATURES.md` (+ `_RU`/`_UK`) to release date and confirm section/bullet counts match across the three locales before publishing.

---

## Phase Done Criteria

- [x] Steps 05.1-05.3 are `[x]`.
- [x] Release skill enforces locale + noLegal + flavor-label + freshness/parity.
- [x] One dev-log entry for the release-skill change.
