<sub class="doc-stamp">26.10.07 12:50</sub>

# Pointer - `CHECK-VERDICT`

| | |
| --- | --- |
| **Id** | `CHECK-VERDICT` |
| **Version** | 0.12, draft; wire carrier: process exit code (0/1/2/3, plus the reserved non-verdict 4) plus one verdict line. Owner: this product |
| **Home** | `automated-checks/README.md` section 2, in the shared contracts catalog |
| **Role here** | owner and reference implementation |

## What this repository must do to stay conformant

- Four exit codes with fixed meanings; exit `2` prints `COULD NOT VERIFY` and is never collapsed into
  a pass (rules 1, 2, 5).
- `4` means only "queued on a lock domain, nothing looked at"; the closure facade converts it to `2`.
- Every `assert-*` check names itself on a `PASS` / `FAIL` verdict line; the legacy residue is the
  shrink-only `scripts/quality/verdict-line-baseline.txt`.
- Every non-zero exit prints its reason, and every documented code is reachable.
- One machine-readable verdict line, the last line on stdout, owed on every path that sets a code,
  the failure path included; advisories are named, not counted (rules 5, 11).
- An empty determined change set is `0` with `PASS (0 file(s) inspected)`; an undetermined set, or a
  run that executed no tests, is `2` (rule 12).
- A wrapper over a runner that reports skips as passes counts the skips, prints the count, and makes
  a skip over a declared input `2` (rule 13).
- An aggregator quotes its children's verdict lines and answers FAIL, then COULD NOT VERIFY, then
  PASS WITH ADVISORIES, then PASS (rule 14).
- A wrapper of a product command maps each of its outcomes to `0`, `1`, `2` or `3` on purpose
  (rule 15).
- A check whose work the caller deliberately switched off exits `0` and ends `<subject>: NOT APPLICABLE`;
  one whose work was requested and did not run stays `2` (0.12, section 10 item A). An aggregator whose
  quoted children all answered it answers it, and counts such a child with the skipped.
- Only a change set the caller declared narrows a finding to the advisory class; a set a check derives
  for itself does not (0.12, section 10 item B).

## Where it lives here

- `scripts/post-change.ps1` and the checks under `scripts/quality/`.
- Enforced by `scripts/quality/assert-exit-contract.ps1`; the advisory class by
  `scripts/quality/lib/fixed-input-scope.ps1`.
