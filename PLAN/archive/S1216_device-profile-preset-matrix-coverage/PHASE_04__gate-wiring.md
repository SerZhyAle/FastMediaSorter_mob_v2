# Phase 04 - Wire the coverage gate

**Strategic spec:** [`../S1216_device-profile-preset-matrix-coverage.md`](../S1216_device-profile-preset-matrix-coverage.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** Phase 06
**Steps done:** 3 / 3
**Started:** 2026-07-27
**Completed:** 2026-07-27

---

## Objective

Make the drift mechanically impossible to repeat: the coverage check runs as part of the fast static gates and of mechanical closure, so a new setting cannot reach a release without a matrix row or a registry entry.

---

## Prerequisites

- [x] Phase 01 and Phase 02 are ✅ Done. Phase 03 is partial by design: its structural steps 03.1-03.2 landed (stale rows removed, missing rows added empty), which is all this phase actually needs - the gate goes green on structure, not on values. Steps 03.3-03.5 write owner-signed values and stay blocked.
- [x] `pwsh -NoProfile -File scripts/check_device_profile_presets.ps1` exits 0 on the current tree - verified before wiring: `OK: matrix, registry and applier agree on every AppSettings field and profile.`
- [x] Working tree is clean or on a feature branch. On `DEBUG-v030`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/assert-device-profile-matrix.ps1` | New | ≤ 90 |
| `scripts/quality/assert-fast-gates.ps1` | Modified | ≤ 130 |
| `scripts/post-change.ps1` | Modified | ≤ 610 |

> `scripts/post-change.ps1` is 572 lines - past the 500-line threshold, so take a timestamped backup under `temp/S1216/` before editing it (CLAUDE.md Rule 5).

---

## Steps

### Step 04.1 - Add the gate wrapper in the quality folder

**Files:** `scripts/quality/assert-device-profile-matrix.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create a thin wrapper matching the shape of its siblings in `scripts/quality/`: accept `-Gate` and `-Quiet`, invoke `scripts/check_device_profile_presets.ps1`, and propagate its exit code. The wrapper exists so `assert-fast-gates.ps1` can discover the gate by folder convention and so the check keeps its standalone `-AddMissing` authoring mode outside the gate path. Document the returned exit codes in the comment-based header and keep any `Write-Error` before a non-1 `exit` on `-ErrorAction Continue` (CLAUDE.md Rule 7).

**Verification:**

- `Glob` - `scripts/quality/assert-device-profile-matrix.ps1` exists.
- `Grep` - `check_device_profile_presets.ps1` matches in the wrapper.
- Value equality - `pwsh -NoProfile -File scripts/quality/assert-device-profile-matrix.ps1 -Gate` returns exit code 0.
- Value equality - `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1 -Gate` returns exit code 0.

**Status:** `[x]` done

---

### Step 04.2 - Register the gate in the fast-gates batch

**Files:** `scripts/quality/assert-fast-gates.ps1`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add `'assert-device-profile-matrix.ps1' = @('-Quiet')` to the ordered `$gates` table. Place it after `assert-doc-pin-drift.ps1`: like that gate it parses data files rather than app sources, needs no gradle daemon, and is cheap. Add a one-line comment naming the ticket and the failure class it catches, matching the S1070 / S1075 comments already there.

**Verification:**

- `Grep` - `assert-device-profile-matrix.ps1` matches in `assert-fast-gates.ps1`.
- Value equality - `.\a.ps1 fg` reports the new gate in its summary with status PASS.
- Value equality - `.\a.ps1 fg` returns exit code 0.

**Status:** `[x]` done

---

### Step 04.3 - Run the gate from mechanical closure

**Files:** `scripts/post-change.ps1`
**Depends on:** Step 04.2

**Prompt for developer:**

> Back up the file to `temp/S1216/` first. Invoke the new gate from the closure chain alongside the other mechanical gates. Honour the existing `-ScopeToFile` convention: under `-ScopeToFile` the matrix check is advisory (warn, do not fail) like the other project-wide ratchet gates, so a dirty tree carrying another ticket's WIP does not block this ticket's closure; without it the gate is strict.

**Verification:**

- `Grep` - `assert-device-profile-matrix` matches in `scripts/post-change.ps1`.
- `Grep` - the invocation sits inside the same conditional block that downgrades the other project-wide gates under `-ScopeToFile`.
- Value equality - `pwsh -NoProfile -File scripts/post-change.ps1 -File "docs/settings/device-profile-nonpresettable.json" -Target "gate" -Description "wire matrix gate" -ChangeType Config` returns exit code 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] `.\a.ps1 fg` returns exit code 0 with the new gate listed PASS.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] Public API unchanged - no catalog regeneration needed.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

From here a new `AppSettings` field fails `.\a.ps1 fg` until it is either given a matrix row or registered as non-presettable. This is the ticket's primary deliverable - the matrix values are the visible half, this is the durable half.

---

## Rollback Plan

Revert phase commit(s) - scripts only. Removing the gate entry restores the previous (unguarded) behaviour with no other effect.

**Deviation from the written verification (2026-07-27).** Step 04.3's predicate named a throwaway -Target "gate" -Description "wire matrix gate" run. Closure was instead run with the real ticket target and description (one dev-log entry per logical change, CLAUDE.md journaling granularity); the predicate that matters - post-change exits 0 with the gate in the chain - is satisfied: `[device-profile-matrix-gate] PASS (745 ms)`, `post-change: PASS (Mixed, 39171 ms)`.


