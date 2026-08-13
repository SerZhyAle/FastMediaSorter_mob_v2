# Phase 02 — play-console-audit

**Strategic spec:** [`../S0220_google-tv-availability-research.md`](../S0220_google-tv-availability-research.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⛔ Blocked
**Depends on:** none — independent research phase (parallel with 01 and 03)
**Blocks:** Phase 04 (apply-manifest-fixes)
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

Audit Play Console settings for `com.sza.fastmediasorter` to identify any explicit exclusions, device targeting rules, or form factor configuration that prevents Panasonic MX700 from seeing the app in Google TV Play Store.

---

## Prerequisites

- [ ] Access to Play Console for `com.sza.fastmediasorter`. **← MANUAL BLOCKER — requires human login.**
- [ ] Strategic §6 items 4, 6, 7 are still Open (this phase resolves §6.4 and §6.6). §6.7 resolved in Phase 01.

> **Blocker note (2026-05-16):** Phase 02 requires interactive access to Google Play Console. All steps are manual — cannot be automated. Owner must perform Steps 2.1–2.3, record findings here, and flip status to ✅ Done.

---

## Files Touched

| File | New / Modified | Note |
|------|:--------------:|------|
| _(none — external Play Console audit)_ | — | Manual investigation in Google Play Console UI |

---

## Steps

### Step 2.1 — Check Device Catalog for Panasonic MX700

**Files:** _(Play Console — external)_
**Depends on:** — start of phase

**Prompt for developer:**

> In Play Console → Release → Device Catalog, search for "Panasonic MX700" (or equivalent Panasonic TV model identifier). Record the compatibility status shown: `supported`, `incompatible`, or `excluded`. If `incompatible`, record the specific incompatibility reason displayed by Play Console (it lists the exact manifest attribute or missing feature). If the device does not appear in Device Catalog, record that as a finding. Document the exact model name / codename that Play Console uses for Panasonic MX700.

**Verification:**

- Document: Device Catalog status for Panasonic MX700 recorded with exact reason. Write finding in strategic §6.4 as Resolved.

**Status:** `[ ]` not done

---

### Step 2.2 — Check device targeting and excluded devices list

**Files:** _(Play Console — external)_
**Depends on:** Step 2.1

**Prompt for developer:**

> In Play Console → Release → Advanced settings → Device targeting (or equivalent): check whether any rule manually excludes TV form factor devices, specific OEM (Panasonic), or Android version ranges that cover MX700. Also check if the production track has any geographic restrictions that might affect the TV Play Store in the user's region. Document any manual exclusion rules found.

**Verification:**

- Document: confirmed no manual exclusion rules target Panasonic MX700, OR documented which rule causes the exclusion and what change is needed.

**Status:** `[ ]` not done

---

### Step 2.3 — Check Google TV form factor and Google Play Services compatibility

**Files:** _(Play Console — external)_
**Depends on:** Step 2.1

**Prompt for developer:**

> In Play Console, check: (1) Whether the app's listing is configured for the TV form factor — is "Android TV / Google TV" listed as a supported form factor under Store presence? (2) Check the minimum Google Play Services version requirement and compare with the version reported on Panasonic MX700 (if determinable via ADB `adb shell dumpsys package com.google.android.gms | grep versionName`). (3) Review whether Play Console shows a "Google TV" distribution channel separate from "Android TV" and whether the app is opted in. Document all findings.

**Verification:**

- Document: Google Play Services version compatibility confirmed or flagged. Write finding in strategic §6.6 as Resolved.
- Document: Google TV form factor status in Play Console confirmed.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every Step 2.* above is `[x] done`.
- [ ] Strategic §6.4 and §6.6 — marked Resolved.
- [ ] Device Catalog status for Panasonic MX700 is documented with the exact reason code (if incompatible).
- [ ] Dev log entry added: `.\scripts\add_to_dev_log.ps1 "PLAN/S0220_google-tv-availability-research/PHASE_02__play-console-audit.md" "research" "S0220 Phase 02: Play Console audit complete"`.
- [ ] No code changes in this phase — no build required.

---

## Handoff Notes to Next Phase

If Device Catalog shows `incompatible` with a specific manifest reason, that reason directly maps to a fix step in Phase 04. If it shows `excluded` due to manual targeting, Phase 04 adds a Play Console targeting change alongside manifest fixes.

---

## Rollback Plan

Read-only phase — no rollback needed.
