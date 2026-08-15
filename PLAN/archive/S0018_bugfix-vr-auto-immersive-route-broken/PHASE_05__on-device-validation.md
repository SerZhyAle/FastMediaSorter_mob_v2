# Phase 05 — On-Device Validation

**Strategic spec:** [`../S0018_bugfix-vr-auto-immersive-route-broken.md`](../S0018_bugfix-vr-auto-immersive-route-broken.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⏭️ Skipped — manual; deferred to human (no device hardware available to the pipeline)
**Depends on:** Phase 02, Phase 03, Phase 04
**Blocks:** Phase 06
**Steps done:** 0 / 2 (both steps `[manual — deferred to human]`)
**Started:** —
**Completed:** —

---

## Objective

Confirm on a real Quest 3 that (a) `vrAutoImmersive=false` keeps stereo content on the panel for at least three distinct stereo formats; (b) settings screen open produces zero `NO fields changed` warnings; (c) the (route, reason) pair in the new log line is always consistent.

This phase is intrinsically manual — device hardware required. Steps tick as `[manual — deferred to human]` until executed.

---

## Prerequisites

- [ ] Phase 02 ✅ Done.
- [ ] Phase 03 ✅ Done.
- [ ] Phase 04 ✅ Done.
- [ ] Quest 3 device available with USB debugging enabled.
- [ ] Test files: at least one each of `EQUIRECT_360_SBS`, `VR180_FISHEYE_SBS`, `EQUIRECT_360_MONO`, plus one plain `MONO` video.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `temp/S0018_on-device-report_<UTC-timestamp>.md` | New | ≤ 200 |

---

## Steps

### Step 05.1 — Run the disabled-auto-immersive matrix on device

**Files:** `temp/S0018_on-device-report_<UTC-timestamp>.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Build a fresh `vr debug` APK via `/build`. Install on Quest 3. Toggle "Auto-вход в иммерсив" OFF in settings. Open each of the four test files in turn. After each file: pull `logs/current.log`, locate the line `route decision file=..`, record `(route, reason)` and the `vrImmersiveActive` next state. Expected: route is `STANDARD_PANEL_FALLBACK`, reason is `auto-immersive-disabled` for stereo files / `plain-2d-video` for the MONO file, immersive is never entered. Save findings to `temp/S0018_on-device-report_<UTC-timestamp>.md` with verbatim log excerpts.

**Verification:**

- `Glob` — at least one file matches `temp/S0018_on-device-report_*.md`.
- `Grep` — inside that report, `route=STANDARD_PANEL_FALLBACK` matches at least 4 times.
- `Grep` — inside that report, `route=CINEMA_IMMERSIVE` matches zero times in any line that also contains `auto-immersive-disabled`.

**Status:** `[manual — deferred to human]` — pipeline cannot run device hardware; user must execute on Quest 3 and append the on-device report.

---

### Step 05.2 — Verify settings idempotency on device

**Files:** `temp/S0018_on-device-report_<UTC-timestamp>.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> With the same APK, open Settings 5 times in a row without changing any value. Pull `logs/current.log`. Append to the on-device report a `## Settings idempotency` section with the count of `NO fields changed — possible no-op write` and the count of `idempotent — skipping DataStore write` matches. Expected: the warning count is zero; the verbose-level idempotency log appears at least once per open.

**Verification:**

- `Grep` — inside the on-device report, `## Settings idempotency` matches exactly once.
- `Grep` — inside the on-device report, `NO fields changed — possible no-op write: 0` matches exactly once (or equivalent zero-count statement).

**Status:** `[manual — deferred to human]` — pipeline cannot run device hardware; user must execute on Quest 3 and append the on-device report.

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done` or `[manual — deferred to human]`.
- [ ] On-device report exists in `temp/`.
- [ ] No regression observed: previously working scenarios (auto-immersive ON) still enter immersive for stereo files.
- [ ] Dev log entry added for the on-device report file via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Phase 06 reads the on-device report to formulate the catalog and dev-log entries. If `[manual — deferred to human]` is still set, Phase 06 may proceed with a documentation note that on-device verification is pending.

---

## Rollback Plan

No code changes in this phase — verification only. If unexpected behaviour is found, file findings and re-open Phase 02/03/04 as needed.
