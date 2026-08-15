# Phase 03 - Shadowing gate

**Strategic spec:** [`../S1282_landscape-dimens-dead-under-sw-qualifiers.md`](../S1282_landscape-dimens-dead-under-sw-qualifiers.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-07-31
**Completed:** 2026-07-31

---

## Objective

Add a mechanical gate that fails when a `values-land` or `values-w600dp` key is outranked by a
smallestWidth bucket with no combined bucket to rescue it, so this class of silent breakage cannot
return unnoticed. Satisfies strategic §11 criterion 5.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done - the gate must find the dimension files already clean.
- [ ] `scripts/quality/assert-layout-variant-id-parity.ps1` read as the shape reference for a resource-qualifier gate.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/assert-qualifier-shadowing.ps1` | New | ≤ 140 |
| `scripts/quality/qualifier-shadowing-baseline.txt` | New | ≤ 15 |
| `scripts/quality/assert-fast-gates.ps1` | Modified | ≤ 130 |

---

## Steps

### Step 03.1 - Write the shadowing detector

**Files:** `scripts/quality/assert-qualifier-shadowing.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Write a gate that walks every `*.xml` under `app_v2/src/main/res/values-land` and
> `app_v2/src/main/res/values-w600dp`, collects each declared resource name, and reports a key as
> shadowed when some `values-swNNNdp/<same filename>` declares it and the matching combined bucket
> `values-swNNNdp-land/<same filename>` does not. Report the threshold in the message so the fix is
> obvious. Treat `values-swNNNdp-land` buckets themselves as exempt - they are the remedy, and a
> tablet-threshold bucket outranking them is the deliberate outcome recorded in strategic §6.
> Follow the house script contract: `#requires -Version 7.0`, a `.SYNOPSIS` naming S1282, an
> `.OUTPUTS` block listing every exit code the script can return, `[switch]$Gate` and `[switch]$Quiet`
> parameters, and `Write-Error .. -ErrorAction Continue` before any non-1 `exit` so the documented
> code is reachable (CLAUDE.md §7). Read accepted entries from the sibling baseline file and subtract
> them before deciding the verdict, the same ratchet shape the other gates in this folder use.

**Verification:**

- `Glob` - `scripts/quality/assert-qualifier-shadowing.ps1` exists.
- `Grep` - `#requires -Version 7.0` on the first line.
- `Grep` - `.OUTPUTS` block present and lists exit 0 and exit 1.
- `Grep` - `values-w600dp` referenced in the script body.
- `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1 -Gate` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 5/5 PASS. `assert-exit-contract -Gate`: 0 unreachable exit sites, 0 silent scripts.
- 2026-07-31 - Detector rewritten after its own first run exposed three defects in it. (1) XML comment nodes were counted as resources - an `XmlNode` comment reports `Name` as `#comment`, so the `$_.name` filter admitted them; now restricted to `NodeType -eq 'Element'`. (2) The first rule failed on any smallestWidth shadow, which flagged `padding_xxlarge` - a key that is genuinely live below the 600dp threshold - and would have failed on the deliberate phone-versus-tablet split from strategic §6. (3) The combined-bucket rescue check only looked at the threshold where the shadow was found, so `resource_grid_column_count` was reported despite being rescued at 320 and 480. Final rule is narrow and provable: a declaration is dead only when `values-sw320dp` or `values-sw320dp-land` declares the key, because that threshold matches every device. The limitation this accepts - a value that dies only above 480dp is not caught - is documented in the script's `.DESCRIPTION`.

---

### Step 03.2 - Seed the baseline with the keys another ticket owns

**Files:** `scripts/quality/qualifier-shadowing-baseline.txt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Run the gate and expect it to fail on the integers files, which this ticket deliberately does not
> touch: `values-land/integers.xml` carries `welcome_feature_grid_columns`,
> `grid_column_count_landscape`, `grid_column_count_list` and `grid_column_count`, and
> `values-w600dp/integers.xml` repeats most of them. Record exactly what the gate reports, each entry
> commented with S1330 as the ticket that will clear it, so the gate ships green without hiding the
> debt. Do not baseline anything from a `dimens.xml` - if a dimension key still shadows after
> Phase 02, that is a Phase 02 defect to fix, not to accept. Do not hand-write the entries from this
> prompt either: seed them from an actual failing run, so the baseline matches the detector's own
> output format.

**Verification:**

- `Glob` - `scripts/quality/qualifier-shadowing-baseline.txt` exists.
- `Grep` - `S1330` present in the baseline file.
- `Grep` - `dimens.xml` returns zero hits in the baseline file.
- `pwsh -NoProfile -File scripts/quality/assert-qualifier-shadowing.ps1 -Gate` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 4/4 PASS. Baseline seeded from an actual failing run: 8 entries, all `integers.xml`, four per shadowable bucket, every one owned by S1330. Gate then PASS - 51 declarations checked, 8 baselined.
- 2026-07-31 - Predicate tightened while running it: `dimens.xml` must return zero *baselined entries*, matched as `dimens\.xml\|`, not zero mentions of the string. The header comment names `dimens.xml` deliberately - it is the rule telling a future reader that a dimension entry here is a regression to fix rather than accept - and a blanket string ban would forbid stating that rule.
- 2026-07-31 - Zero `dimens.xml` entries in the baseline is the proof that Phases 01-02 cleared the dimension side completely: the detector re-derives shadowing from the tree and finds nothing left there.

---

### Step 03.3 - Register the gate in the fast batch

**Files:** `scripts/quality/assert-fast-gates.ps1`
**Depends on:** Step 03.2

**Prompt for developer:**

> Add `assert-qualifier-shadowing.ps1` to the `$gates` ordered map with `@('-Quiet')`, placed next to
> `assert-layout-variant-id-parity.ps1` since both guard resource-qualifier siblings. Add the same
> style of comment the neighbouring entries carry: name the ticket, say what the gate catches, and
> say why it is cheap enough for the fast batch - it parses a handful of small XML files and starts
> no gradle daemon. Also extend the `.DESCRIPTION` gate list at the top of the file so the header
> keeps matching the map.

**Verification:**

- `Grep` - `assert-qualifier-shadowing.ps1` present in `assert-fast-gates.ps1`.
- `Grep` - `S1282` present in `assert-fast-gates.ps1`.
- `pwsh -NoProfile -File scripts/quality/assert-fast-gates.ps1` exits 0 and lists the new gate as PASS.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 3/3 PASS. Registered next to `assert-layout-variant-id-parity.ps1`; `.DESCRIPTION` gate list extended to match the map. `assert-fast-gates.ps1` exits 0, batch summary `pass: 18 | fail: 0`, new gate PASS in 366 ms.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] `.\a.ps1 fg` exits 0 with the new gate reported PASS (357 ms); batch `pass: 18 | fail: 0`.
- [x] Negative check recorded: `empty_state_padding` re-added to `values-land/dimens.xml` -> gate exit 1 naming `dimens.xml|values-land|empty_state_padding`; reverted -> 14 dimens restored, gate exit 0.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" - three via `post-change.ps1`, all PASS. Both script closures reported the cheatsheet as stale, which Phase 05 step 05.1 regenerates.
- [x] Phase-boundary audit run - no P0/P1. P2 logged and deliberately not fixed: `Get-ResourceNames` re-parses the same XML once per key (102 parses instead of 3); at 357 ms in the batch the win is unmeasurable, and re-editing a script whose behaviour was just proven by the negative check would invalidate that proof for nothing. P3: malformed XML raises instead of returning the documented exit 2 - such a file already fails the build.

---

## Handoff Notes to Next Phase

The gate now covers both landscape-flavoured buckets across every `values-*` file, so whatever
Phase 04 decides about the width copy, the decision stays enforced.

---

## Rollback Plan

Delete the two new script files and revert the `assert-fast-gates.ps1` entry. No app code is involved.
