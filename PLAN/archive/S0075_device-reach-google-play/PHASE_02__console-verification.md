# Phase 02 — Console Verification

**Strategic spec:** [`../S0075_device-reach-google-play.md`](../S0075_device-reach-google-play.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01 (+ release build published to Google Play)
**Blocks:** —
**Steps done:** 0 / 2
**Started:** —
**Completed:** —

---

## Objective

After the Phase 01 build is published to Google Play, verify in Play Console that the "Manifest restrictions" exclusions are eliminated and document the before/after device count.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Release build containing Phase 01 changes is published (at minimum to Internal Testing track).
- [ ] §6.1 blocker resolved: baseline "Excluded" count documented before publishing.
- [ ] Owner has access to Google Play Console for this app.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CHANGELOG.md` (via script) | Modified | — |

---

## Steps

### Step 2.1 — Record baseline "Excluded" count before publishing

**Files:** `dev/CHANGELOG.md` (via `add_to_dev_log.ps1`)
**Depends on:** — do this BEFORE publishing the Phase 01 build

**Prompt for developer:**

> In Google Play Console, navigate to **Release → Reach and devices → Device catalog**. Filter by "Excluded". Note the total excluded device count and, if visible, the number excluded specifically due to "Manifest restrictions". Record these numbers in `dev/CHANGELOG.md` via the dev log script before publishing the Phase 01 APK/AAB, so a before/after comparison is possible.
>
> Command: `.\scripts\add_to_dev_log.ps1 "dev/CHANGELOG.md" "S0075" "Baseline: <N> excluded devices (<M> manifest restrictions) before Phase 01 publish"`

**Verification:**

- `Grep` — `S0075` and `Baseline` present in `dev/CHANGELOG.md`.

**Status:** `[ ]` not done

---

### Step 2.2 — Verify reduction in "Manifest restrictions" after publish

**Files:** `dev/CHANGELOG.md` (via `add_to_dev_log.ps1`)
**Depends on:** Step 2.1; release build published

**Prompt for developer:**

> After the Phase 01 build propagates in Play Console (may take up to 24 hours), navigate to **Release → Reach and devices → Device catalog → Excluded** and confirm:
> 1. No entries with cause "Manifest restrictions" for `android.hardware.wifi` remain.
> 2. If Step 1.2 was applied: no `android.hardware.touchscreen` entries remain.
> 3. Note the new total excluded count.
> If any unexpected "Manifest restrictions" remain, open a new research item and update strategic §6 accordingly.
>
> Record the result: `.\scripts\add_to_dev_log.ps1 "dev/CHANGELOG.md" "S0075" "After Phase 01: <N> excluded devices (<delta> change). Manifest restrictions cleared: [yes/no]."`

**Verification:**

- `Grep` — `S0075` and `After Phase 01` present in `dev/CHANGELOG.md`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 2.*` above is `[x] done`.
- [ ] Before/after device exclusion counts are recorded in `dev/CHANGELOG.md`.
- [ ] Any remaining unexpected "Manifest restrictions" have a follow-up action noted.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

No code changes in this phase. If Play Console shows regressions, revert Phase 01 commit and republish.
