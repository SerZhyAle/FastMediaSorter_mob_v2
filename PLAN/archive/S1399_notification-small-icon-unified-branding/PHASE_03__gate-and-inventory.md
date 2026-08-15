# Phase 03 - Gate and inventory

**Strategic spec:** [`../S1399_notification-small-icon-unified-branding.md`](../S1399_notification-small-icon-unified-branding.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-08-08
**Completed:** 2026-08-08

---

## Objective

Make the fix permanent with a mechanical check, and record the shipped capability.

---

## Anchors

- `scripts/quality/` - the `assert-*.ps1` family; `assert-neuroslop.ps1` is the ratchet-baseline shape to copy.
- `scripts/quality/assert-fast-gates.ps1` - the batch `.\a.ps1 fg` runs.
- `scripts/post-change.ps1` - where source gates are chained.
- `scripts/all_features/add.ps1` - the feature inventory writer.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/assert-notification-small-icon.ps1` | New | ≤ 160 |
| `scripts/quality/assert-fast-gates.ps1` | Modified | unchanged |
| `docs/ALL_FEATURES.jsonl` | Modified (via script) | n/a |

---

## Steps

### Step 03.1 - Write the gate

**Files:** `scripts/quality/assert-notification-small-icon.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Fail when a small-icon setter is handed a drawable literal instead of the phase-01 owner. Scan `app_v2/src/**` for `setSmallIcon` and the Media3 provider's equivalent, and report any whose argument is an `R.drawable.` literal. Follow CLAUDE.md Rule 7 on exit codes: the header lists the codes actually returned, and any `Write-Error` before a non-1 `exit` uses `-ErrorAction Continue`.

**Why:**

Strategic ADR-4 puts the rule in a gate rather than a comment, because the defect survived thirteen call sites precisely by never being checked, and Rule 19/20 requires a recurring finding to become a mechanical gate.

**Verification:**

- Script exits 0 on the current tree.
- Temporarily reintroducing one `R.drawable.` literal at a call site makes it exit non-zero, and the message names that file and line. Revert the probe afterwards.

**Status:** `[x]` done

**Step Log:**

- 2026-08-08 - Verification 2\2 PASS. `assert-notification-small-icon.ps1` exits 0 on the current tree
  (2751 `.kt` files scanned). The regression probe - `DuplicateDetectionWorker` temporarily set back to
  `R.drawable.ic_launcher_foreground` - made it exit 1 with
  `app_v2/src/main/java/com/sza/fastmediasorter/worker/DuplicateDetectionWorker.kt:125`, naming the file
  and the line as required. Probe reverted, and the gate re-run after the revert exits 0 again.
- Rule 7 exit contract honoured and independently confirmed: `assert-exit-contract.ps1 -Gate` exits 0
  with 0 unreachable exit sites. The one non-1 code (2, "no source root to scan") is reached through
  `Write-Error -ErrorAction Continue`, so it survives `$ErrorActionPreference = 'Stop'`.
- One regex covers all three builders because `NotificationCompat.Builder`, `Notification.Builder` and
  the Media3 provider all spell the setter `setSmallIcon`. Trailing comments are stripped before matching
  so this ticket's own rationale comments cannot trip it.
- The gate is deliberately narrow: `Icon.createWithResource` on the quick-settings tile and the widget
  glyphs are not notification small icons and stay unflagged, matching the scope 02.4 recorded.
- 2026-08-08 - **AUDIT-FIX (phase-boundary audit, P2): the gate was blind to a wrapped call.** The scan
  ran line by line, so `setSmallIcon(` with the argument on the next line matched nothing - the argument
  line has no setter and the setter line has no literal. That is exactly the fourteenth call site the
  gate exists to catch, and a formatter produces the shape without anyone choosing it. Now the
  comment-stripped lines are rejoined and matched as one text, with the line number recovered from the
  match offset, so `\s*` spans the newline.
- Re-verification 2\2 PASS after the fix, and the hole is measured rather than argued: with
  `DuplicateDetectionWorker` temporarily carrying a **wrapped** `R.drawable.ic_launcher_foreground`, the
  gate exits 1 naming `app_v2/.../DuplicateDetectionWorker.kt:125`, while the previous per-line regex run
  over the same probe file returns **0** matches. Probe reverted; the gate exits 0 again on 2755 files,
  and `assert-exit-contract.ps1 -Gate` still reports 0 unreachable exit sites.
- **AUDIT-P3, deliberately not changed:** `-Gate` is declared and never read, so the gate is fail-closed
  whether or not a caller passes it. That matches the parameter surface the other 30 `assert-*` scripts
  expose, which is what lets `assert-fast-gates.ps1` pass it uniformly; making it advisory-without-`-Gate`
  would weaken a check whose whole point is that nothing was checking.

---

### Step 03.2 - Chain it into the fast gates

**Files:** `scripts/quality/assert-fast-gates.ps1`
**Depends on:** Step 03.1

**Prompt for developer:**

> Register the new gate in the fast-gates batch so `.\a.ps1 fg` runs it in the same process as the other source gates. Match the surrounding registration shape exactly - do not invent a second reporting style.

**Why:**

A gate nobody runs is a comment with a shebang; CLAUDE.md section 9 makes `fg` the batch every agent runs, which is what turns the check into enforcement.

**Verification:**

- `.\a.ps1 fg` exit 0, and its output names the new gate.

**Status:** `[x]` done

**Step Log:**

- 2026-08-08 - Verification 2\2 PASS. `.\a.ps1 fg` exit 0, and its summary names the new gate:
  `assert-notification-small-icon.ps1  PASS (6079 ms)`. Registered last in the `$gates` table with
  `-Quiet`, matching the surrounding shape, and listed in the `.DESCRIPTION` gate roll.
- **An unrelated gate had to be cleared first, and it was not this ticket's doing.** The first `fg` run
  exited 1 while the new gate itself passed: `assert-memory-budget` reported the agent-memory index at
  18254 B against a 16595 B ceiling. Nothing in S1399 touches that file - a sibling session had appended
  a pointer line to it mid-session (the `drift_check_false_positive` entry was absent from this session's
  own loaded copy). Its ceiling is a ratchet that `-UpdateBaseline` refuses to raise, so the only
  legitimate remedy is to actually shrink the index, which is what the gate's own message asks for.
- Compacted it to 16448 B in two passes - shortened link titles and dropped hooks that merely restated
  their title. **No pointer was removed:** all 232 links still resolve to a file on disk, and the three
  memory files that are not indexed were already unindexed before this edit. Fixed in place rather than
  parked because the file is the agent's own housekeeping, not product scope, and leaving it red would
  have kept `.\a.ps1 fg` failing for every ticket that runs it next.
- `docs/SCRIPT_CHEATSHEET.md` was regenerated too - adding a script in 03.1 made it stale, and it is a
  render target, so it is regenerated with `help.ps1 -Generate`, never hand-edited.

---

### Step 03.3 - Record the capability

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 03.2

**Prompt for developer:**

> Add the record through `scripts/all_features/add.ps1` as a FIX for all six flavors, in English. Do not hand-edit the JSONL, and do not touch `docs/FEATURES*.md` - that is `/skill-release`'s to write from the diff.

**Why:**

Strategic §8 records a user-visible change, and CLAUDE.md section 11 makes the JSONL the developer inventory while the showcase stays release-owned.

**Verification:**

- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.
- `Grep` - `docs/ALL_FEATURES.jsonl` contains exactly one record whose `spec` field is `S1399`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-08 - Verification 2\2 PASS. `scripts/all_features/validate.ps1` exits 0 (674 records), and
  `docs/ALL_FEATURES.jsonl` carries exactly one record whose `spec` is `S1399`:
  `general.notification-status-bar-app-logo`, all six flavors, written through
  `scripts/all_features/add.ps1` - the JSONL was not hand-edited and `docs/FEATURES*.md` was not touched.
- Area is `General` rather than a new `Notifications` one: the inventory has no notifications area, and
  `General` is what the other app-wide cross-cutting records use (`general.dialog-keyboard-consistency`,
  `general.dpad-initial-focus-redirect-off-scrollable`). Adding a one-record area would have split the
  cross-cutting group for no reader benefit.
- Wording comes from strategic §8, expanded with the three workers named in §1 so the release diff reads
  as a user-visible fix rather than an internal refactor.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] `.\a.ps1 fg` exit 0.
- [x] Dev log entry added via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - script-layer only; Layer 1 plus the Rule 7 exit-code contract.

---

## Handoff Notes to Next Phase

None - last phase. On-device verification is part of acceptance (strategic §11.1 and §11.4), so the ticket
goes to `BlockNeedUserTest` with debug tags, not straight to `Implemented`.

---

## Rollback Plan

Delete the gate and unregister it; remove the inventory record with the `all_features` CLI.
