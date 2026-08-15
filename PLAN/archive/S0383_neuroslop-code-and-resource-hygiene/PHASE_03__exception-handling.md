# Phase 03 - Exception Handling Audit

**Strategic spec:** [`../S0383_neuroslop-code-and-resource-hygiene.md`](../S0383_neuroslop-code-and-resource-hygiene.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⏭️ Deferred (descoped at S0383 close, 2026-06-08)
**Depends on:** Phase 01
**Blocks:** Phase 06
**Steps done:** 0 / 3 (cleanup deferred; detector built in Phase 01 holds the floor)
**Started:** -
**Completed:** -

> DEFERRED, not abandoned. The `assert-empty-catch` detector (Phase 01) is wired into `post-change.ps1` and caps the swallowing-catch count at its floor (75), so no new swallows can land. The bulk catch refactor is descoped from S0383 (see strategic §2 «Доставленный объём»). Triage finding before deferral: the detector over-flags - many flagged sites are intentional narrow guards (e.g. `catch (e: UninitializedPropertyAccessException) { // not initialized }`), NOT ADR-1 violations. Before any future cleanup, tighten the detector to flag only (a) truly-empty `catch {}` and (b) comment-only catches of BROAD types (Exception/Throwable/RuntimeException/Error), then fix the genuine targets "no new strings" first (internal/background swallows), routing UI-feedback cases through owner-provided translations.

---

## Objective

Replace swallowing catch blocks (empty or comment-only) flagged by `assert-empty-catch.ps1` with the project standard (ADR-1): state recovery, a safe default, or justified degradation logged at the correct level - and give UI-initiated failures user feedback - then ratchet the empty-catch baseline DOWN.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] INDEX Pre-Implementation Blocker "execution mode for destructive cleanup" is checked.
- [ ] `docs/COMMUNICATION_POLICY.md` read (any new user-facing error string must comply).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| (set from `assert-empty-catch.ps1`) `app_v2/src/main/**/*.kt` | Modified | ≤ ~10 lines/site |

> Hotspots from strategic §11: the player lifecycle helper (≈a dozen sites in one file), SMB/SFTP/FTP connection pools, cloud Glide loaders. Any `.kt` >500 lines gets a `temp/` backup before edit (Strict Rule 5).

---

## Steps

### Step 03.1 - Classify each swallow site

**Files:** (read-only) `scripts/quality/assert-empty-catch.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Run the detector in report mode. For each site, classify the surrounding operation as UI-initiated or background/non-critical (strategic §6.2). UI-initiated failures need user feedback (Snackbar/Toast per `docs/COMMUNICATION_POLICY.md`); background degradation needs a safe default plus a plain-English `Timber.i/w` (never `Timber.e` for expected fallbacks). Reserve `Timber.e` for errors the developer must act on.

**Verification:**

- Run `pwsh -NoProfile -File scripts/quality/assert-empty-catch.ps1` - expected exit 0.
- Value equality: site count equals the Phase 01 baseline (expected: baseline | actual: `<count>`).

**Status:** `[ ]` not done

---

### Step 03.2 - Apply the recovery standard

**Files:** `app_v2/src/main/**/*.kt` (subset from Step 03.1)
**Depends on:** Step 03.1

**Prompt for developer:**

> Rewrite each swallowing catch per its classification: add recovery / safe default / justified degradation, and a plain-English log line describing the failure consequence (ADR-1). For UI-initiated operations, surface feedback via the existing channel for that screen. Log level must match severity - downgrade expected device-capability fallbacks to `Timber.i/w`. Do NOT embed any `Sxxxx` ticket id in a permanent log line (CLAUDE.md). Any new user-visible string must pass `docs/COMMUNICATION_POLICY.md` §2 (message formula) and §6 (tone checklist), and obey `..` / `ё` author style.

**Verification:**

- `Grep` - `Log\.d\(` returns zero hits in every touched file (Timber only).
- `Grep` - no `Timber.(i|w|e)\(.*S\d{4}` ticket id in touched files.
- Strings (if any added) pass COMMUNICATION_POLICY §6 checklist.
- Project compiles - run `/build`; affected unit tests pass (per-class XML reports, not whole-suite).

**Status:** `[ ]` not done

---

### Step 03.3 - Ratchet the baseline down

**Files:** `scripts/quality/empty-catch-baseline.txt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Run the detector with `-UpdateBaseline`, then `-Gate` to confirm the new floor.

**Verification:**

- Run `pwsh -NoProfile -File scripts/quality/assert-empty-catch.ps1 -UpdateBaseline` - expected exit 0, baseline lowered.
- Run `pwsh -NoProfile -File scripts/quality/assert-empty-catch.ps1 -Gate` - expected exit 0.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build`; touched-area unit tests pass.
- [ ] `assert-empty-catch.ps1 -Gate` exits 0 at the lowered baseline.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for the touched files via `.\scripts\add_to_dev_log.ps1`.
- [ ] If a public method signature changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Empty-catch baseline lowered from `<old>` to `<new>`. Note any catch site intentionally left as a no-op (with a justifying comment) so a later audit does not re-flag it as regressed.

---

## Rollback Plan

Revert the phase commit(s) and restore `empty-catch-baseline.txt`. Behaviour change is limited to error paths - no data migration; verify the prior swallow behaviour is restored on revert.
