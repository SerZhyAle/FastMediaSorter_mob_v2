# Phase 05 - Docs and catalog cleanup

**Strategic spec:** [`../S1478_bugfix-headless-capture-ignores-camera-settings.md`](../S1478_bugfix-headless-capture-ignores-camera-settings.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** -
**Steps done:** 2 / 2
**Started:** 2026-08-07
**Completed:** 2026-08-07

---

## Objective

Register the new class, close the change through the facade, and hand the device-only predicates to the device-test gate.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |
| `dev/CHANGELOG.md` | Appended via script | - |

---

## Steps

### Step 05.1 - Register the new class and close the change

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CHANGELOG.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `scripts/post-change.ps1` once with every file this ticket touched, `-ScopeToFile`, and `-ChangeType Kotlin`. Then set the catalog `role` and `status` for `CapturedPhotoAspectCropper` with `set.ps1` - a new class enters the catalog without them.

**Why:**

Strategic §4 lists the mechanical checks the ticket must pass, and a new class that carries no catalog role is invisible to the query path every later ticket starts from.

**Verification:**

- `post-change.ps1` exits 0 and prints `PASS`.
- `Grep` - `CapturedPhotoAspectCropper` present in `dev/CATALOG/app_v2.jsonl` with a non-empty `role`.

**Status:** `[x] done`

---

### Step 05.2 - Park the device-only predicates

**Files:** `PLAN/spec-catalog.jsonl` (via CLI)
**Depends on:** Step 05.1

**Prompt for developer:**

> Insert one `Timber.d("S1478: ..")` probe at the headless capture entry point, then set the ticket to `BlockNeedUserTest` with a `-StatusNote` naming the four device checks from strategic §4: file orientation with auto-rotate off, identical field of view between the two routes, identical proportions at 16:9, and surviving GPS tags on a geotagged 16:9 shot. State in the note that an emulator cannot settle the rotation check.

**Why:**

Strategic §4.2 requires the plan to separate what is proven statically from what only a device can settle, and INDEX records that all four user-visible outcomes fall on the device side.

**Verification:**

- `Grep` - exactly one `Timber.d("S1478:` line exists across `.kt`.
- `select.ps1 -Id S1478` reports `BlockNeedUserTest` with a non-empty status note.

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] `docs/FEATURES*` untouched - the ticket ships no new user-visible capability, only parity for an existing one.

---

## Step Log

- 2026-08-07 - Step order corrected before execution: 05.2's status flip must precede 05.1's `post-change.ps1`, not follow it. Run in the planned order the closure fails - `assert-no-ticket-logs` reads a `Timber.d("S1478:` probe as a forbidden permanent log for any ticket not already `BlockNeedUserTest` (observed: FAIL, exit 1, "stale probe (ticket not BlockNeedUserTest)"). The plan text is left as written; this note is the correction.
- 2026-08-07 - Step 05.2 Verification 2/2 PASS. Exactly one `Timber.d("S1478:` line across `.kt` (`HeadlessPhotoCapturer.kt`, the headless capture entry point, carrying lens id, rotation bucket and aspect selection). It was first written wrapped across five lines, which the gate counted correctly but the step predicate - a literal grep - did not; rewritten to a single 107-char line so both agree. `select.ps1 -Id S1478` reports `BlockNeedUserTest` with a status note naming all four device checks.
- 2026-08-07 - Step 05.1 Verification 2/2 PASS. `post-change.ps1` over all five touched files with `-ScopeToFile -ChangeType Kotlin`: `post-change: PASS`, exit 0, every gate green (detekt scoped PASS, ticket-log 0 forbidden / 129 allowed probes, catalog synced to 2546 records, one changelog row). `CapturedPhotoAspectCropper` registered with role and `status=new` via `set.ps1 -Path`.
- 2026-08-07 - Compile after the probe rewrite: `.\a.ps1 fk` exit 0.
- Device-test gate: emulator-5554 is attached, but all four predicates are real-hardware ones (no orientation sensor and no ultra-wide module on the AVD). Handed to the batched device drain rather than run here.