# Phase 05 — verify-tv-visibility

**Strategic spec:** [`../S0220_google-tv-availability-research.md`](../S0220_google-tv-availability-research.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 04 (apply-manifest-fixes)
**Blocks:** Phase 06 (docs-catalog-cleanup)
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

Publish a new build with the Phase 04 manifest fixes, confirm Panasonic MX700 becomes visible as `supported` in Play Console Device Catalog, and verify the app is installable from Google TV Play Store on the device.

---

## Prerequisites

- [ ] Phase 04 ✅ Done — manifest fixes applied and phone build passes.
- [ ] Play Console access to publish to internal test or production track.
- [ ] Panasonic MX700 available for physical verification.

---

## Files Touched

| File | New / Modified | Note |
|------|:--------------:|------|
| _(Play Console — external publish action)_ | — | No local file changes |

---

## Steps

### Step 5.1 — Publish updated build to internal test track

**Files:** _(Play Console — external)_
**Depends on:** Phase 04 ✅ Done

**Prompt for developer:**

> Build a signed `standardRelease` APK or AAB using `/build`. Upload it to Play Console → Internal testing track. Wait for Play Store to process the new artifact (typically 1–4 hours). Do not proceed to Step 5.2 until the build is fully processed (status shows "Available" in the internal track).

**Verification:**

- Document: build uploaded to Play Console and shows "Available" in internal test track.

**Status:** `[ ]` not done

---

### Step 5.2 — Verify Device Catalog shows Panasonic MX700 as supported

**Files:** _(Play Console — external)_
**Depends on:** Step 5.1

**Prompt for developer:**

> In Play Console → Release → Device Catalog, re-check the status of Panasonic MX700 for the newly published build. Expected outcome: status changes from `incompatible` to `supported`. If still `incompatible`, record the remaining incompatibility reason — this indicates a missed fix in Phase 04 and requires a return to that phase. Expected: `supported` | Actual: _record_.

**Verification:**

- Document: Device Catalog status for Panasonic MX700 — expected: `supported` | actual: _record_.

**Status:** `[ ]` not done

---

### Step 5.3 — Verify app appears in Google TV Play Store on MX700

**Files:** _(device — external)_
**Depends on:** Step 5.1

**Prompt for developer:**

> On Panasonic MX700, open Google TV Play Store, search for "Fast Media Sorter". Expected: app appears in results and is available to install. Install from Play Store and verify launch. If still absent: check whether the Google account on the TV is the same as the one used in Play Console internal test (for internal track, only invited testers can see the app). For production track, allow 24–48 hours for full propagation.

**Verification:**

- Document: app found in Play Store on MX700 — expected: visible and installable | actual: _record_.
- Document: app installs and launches from Play Store on MX700 — expected: success | actual: _record_.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every Step 5.* above is `[x] done`.
- [ ] Device Catalog shows Panasonic MX700 as `supported`.
- [ ] App confirmed installable from Google TV Play Store on MX700.
- [ ] Dev log entry added: `.\scripts\add_to_dev_log.ps1 "PLAN/S0220_google-tv-availability-research/PHASE_05__verify-tv-visibility.md" "spec-dev" "S0220 Phase 05: TV visibility verified"`.

---

## Handoff Notes to Next Phase

If Step 5.2 still shows `incompatible`, the spec returns to Phase 04 (re-enter `In Progress`). If Step 5.3 succeeds, the spec is ready for final cleanup in Phase 06.

---

## Rollback Plan

If the publish introduces a regression on phone (detected by user reports): publish a hotfix revert of the Phase 04 manifest commit.
