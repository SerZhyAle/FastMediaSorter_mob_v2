# Phase 04 - WCAG numeric verification + S0569 audit closeout

**Strategic spec:** [`../S0611_bugfix-custom-theme-contrast.md`](../S0611_bugfix-custom-theme-contrast.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** 01, 02, 03
**Blocks:** none
**Steps done:** 3 / 3

> Verified by reading the SHIPPED resources (not the design copy): `temp/wcag_verify_resources.ps1` parses
> `values/colors.xml` + `values-night/colors.xml` and recomputes contrast = `ALL RESOURCE CHECKS PASS`.

---

## Objective

Prove every theme meets WCAG AA numerically, confirm the implemented hex matches the locked artifact, and record the
closeout of the S0569 deferred contrast audit point.

---

## Steps

### Step 04.1 - Re-run the numeric WCAG gate

**Prompt for developer:**

> Run `pwsh -NoProfile -File temp/wcag_s0611.ps1`. It must print `ALL CHECKS PASS` (66/66). If any pair regresses, the
> implemented hex drifted from the artifact - reconcile the resource value to the artifact (the artifact is the locked source).

**Verification:**

- `temp/wcag_s0611.ps1` prints `ALL CHECKS PASS`.

**Status:** `[x]` done

---

### Step 04.2 - Cross-check implemented hex vs artifact

**Prompt for developer:**

> Spot-check that the resource values actually written equal the artifact: for each theme, Grep the implemented
> `theme_<mode>_<c>_surface_container_high` and `..._primary` and confirm they match research artifact 01. No silent drift.

**Verification:**

- `Grep` - implemented `surface_container_high` hex for all 6 themes equals the artifact table.
- `Grep` - implemented DARK_* `primary` hex equals the lightened artifact values.

**Status:** `[x]` done

---

### Step 04.3 - Record S0569 audit closeout

**Prompt for developer:**

> In the strategic spec `## Last Audit` (written by `/spec-check`) or the change log, note that S0569's deferred point
> "§11.3 / §3.2 WCAG AA exact colors + >=4.5:1 per theme - owner visual judgment" is now closed with numeric measurement
> (replacing visual judgment) for all 9 themes. No edit to the archived S0569 file (read-only/done).

**Verification:**

- The closeout statement is present in the S0611 spec (audit/changelog section).

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] All three steps `[x] done`.
- [ ] `ALL CHECKS PASS` recorded.
- [ ] Manual item logged: device visual confirmation of all 9 themes (esp. the lightened DARK_* toolbars) deferred under no-build mode.

---

## Rollback Plan

Verification-only phase - nothing to roll back.
