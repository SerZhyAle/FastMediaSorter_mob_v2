# Phase 02 - Launcher Integration

**Strategic spec:** [`../S0273_build_failure_diagnostics.md`](../S0273_build_failure_diagnostics.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Expose the parser through `a.ps1 bf` and ensure the primary fast-debug wrapper leaves a fresh log in `temp/` for that command to consume.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on the current feature branch.
- [ ] `scripts/builders/get-last-build-failure.ps1` passes the fixture smoke checks.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `a.ps1` | Modified | ≤ 420 |
| `scripts/builders/build-debug.PS1` | Modified | ≤ 420 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 02.1 - Register the `bf` launcher alias

**Files:** `a.ps1`
**Depends on:** Phase 02 start

**Prompt for developer:**

> Add the `bf` alias to the `$scripts` table in `a.ps1`, pointing to `scripts\\builders\\get-last-build-failure.ps1` with an empty args hashtable. Update the unknown-command help block so `bf` appears in the same compact style as the existing aliases. Do not attach Chaquopy or release-worktree logic to this alias.

**Verification:**

- `Grep` - `'bf'\s*=\s*@\{\s*Path = 'scripts\\builders\\get-last-build-failure\.ps1'; Args = @\{\} \}` present in `a.ps1`.
- `Grep` - `bf   - Show last build failure block` present in `a.ps1`.
- `Grep` - `switch \(\$Command\)` still does not include `bf` in the Chaquopy state branch.

**Status:** `[x]` done

**Step Log:**

- 2026-05-20 - Verification 3/3 PASS. Files: a.ps1 (+3 LOC).

---

### Step 02.2 - Persist the fast-debug output into `temp/`

**Files:** `scripts/builders/build-debug.PS1`
**Depends on:** Step 02.1

**Prompt for developer:**

> Update `scripts/builders/build-debug.PS1` so every invocation writes the full raw Gradle output to a deterministic log under `temp/` before exit. Keep console behaviour unchanged, keep the retry logic intact, and make the saved log path stable enough for `bf` to find via `*build*.log` and `LastWriteTime`. The saved file must contain the final retry attempt output when retries happen.

**Verification:**

- `Grep` - `Join-Path \$projectRoot \"temp\"` present in `scripts/builders/build-debug.PS1`.
- `Grep` - `build_debug_` present in `scripts/builders/build-debug.PS1`.
- `Grep` - `Set-Content -Path \$buildLogPath` present in `scripts/builders/build-debug.PS1`.
- `Grep` - `Write-Host \"Build log:` present in `scripts/builders/build-debug.PS1`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-20 - Verification 4/4 PASS. Files: scripts/builders/build-debug.PS1 (+18 LOC).

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] Command `pwsh -NoProfile -File a.ps1 bf` resolves the new alias without "Unknown command".
- [x] Command `pwsh -NoProfile -File scripts/builders/get-last-build-failure.ps1 -LogPath scripts/builders/testdata/build-failure-middle.log` still exits `0` after the integration edits.

---

## Handoff Notes to Next Phase

Phase 02 guarantees a practical operator path: primary debug builds leave a fresh log, and `a.ps1 bf` becomes the shortest diagnostic entrypoint.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
