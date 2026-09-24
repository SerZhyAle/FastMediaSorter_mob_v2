# Pointer - `CHECK-PLACEMENT`

| | |
| --- | --- |
| **Id** | `CHECK-PLACEMENT` |
| **Version** | 0.10, draft. Owner: this product |
| **Home** | `automated-checks/README.md` section 4, in the shared contracts catalog |
| **Role here** | owner |

## What this repository must do to stay conformant

- Every check declares its runner class in one registry, checked in both directions.
- Membership in the operator-typed batch never satisfies a per-change class.
- A relocation is a registry row with a reason and a date, never a paragraph in a comment.
- A seeded registry record names an owner ticket that is still open; when that ticket closes
  without judging the record, the placement check refuses until the record is judged, re-pointed
  with a dated reason, or absorbed into the shrink-only seeded baseline.

## Where it lives here

- `scripts/quality/gate-placement.jsonl`, made binding by `scripts/quality/assert-gate-placement.ps1`, with the pre-rule seeded set absorbed in `scripts/quality/gate-placement-seeded-baseline.txt`.
