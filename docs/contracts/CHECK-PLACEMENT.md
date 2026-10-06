<sub class="doc-stamp">26.10.02 20:43</sub>

# Pointer - `CHECK-PLACEMENT`

| | |
| --- | --- |
| **Id** | `CHECK-PLACEMENT` |
| **Version** | 0.11, draft. Owner: this product |
| **Home** | `automated-checks/README.md` section 4, in the shared contracts catalog |
| **Role here** | owner |

## What this repository must do to stay conformant

- Every check declares its runner class in one registry, checked in both directions.
- Membership in the operator-typed batch never satisfies a per-change class.
- Each registry class maps onto the contract's runner classes - per-change, agent-closure, build,
  release, hand-run - and the mapping is written beside the registry (rule 8).
- A runner's trigger path filter is compared with the inputs of every check placed in it (rule 2).
- A relocation is a registry row with a reason and a date, never a paragraph in a comment.
- A seeded registry record names an owner ticket that is still open; when that ticket closes
  without judging the record, the placement check refuses until the record is judged, re-pointed
  with a dated reason, or absorbed into the shrink-only seeded baseline.

## Where it lives here

- `scripts/quality/gate-placement.jsonl`, made binding by `scripts/quality/assert-gate-placement.ps1`, with the pre-rule seeded set absorbed in `scripts/quality/gate-placement-seeded-baseline.txt`.
- The class mapping (rule 8): `scripts/quality/gate-placement-classes.jsonl`, one row per registry class - per-ticket is agent-closure, fast-batch is per-change, release-scope and prerelease-content are release, build is build, hand-run is hand-run; a stage inherits its parent's class and a runner checks nothing.
- The trigger comparison (rule 2): the fast-batch row names `.github/workflows/static-gates.yml` with the whole tree as its input, so that workflow carries no path filter; `assert-gate-placement.ps1` refuses a filter that does not cover a declared input.
