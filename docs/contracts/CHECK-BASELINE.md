# Pointer - `CHECK-BASELINE`

| | |
| --- | --- |
| **Id** | `CHECK-BASELINE` |
| **Version** | 0.9, draft. Owner: this product |
| **Home** | `automated-checks/README.md` section 3, in the shared contracts catalog |
| **Role here** | owner |

## What this repository must do to stay conformant

- A baseline may fall and never rise; a class exposed to a wholesale re-freeze is judged by identifier
  set, not by count.
- Accepting new debt is an explicit act with a reason and a journal row, reviewable in a diff.

## Where it lives here

- The baseline files beside their checks under `scripts/quality/`.
- Set semantics enforced by `scripts/quality/assert-detekt-baseline-absorption.ps1`.
