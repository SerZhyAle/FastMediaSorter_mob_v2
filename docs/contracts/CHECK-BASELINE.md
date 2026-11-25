<sub class="doc-stamp">26.11.15 13:59</sub>

# Pointer - `CHECK-BASELINE`

| | |
| --- | --- |
| **Id** | `CHECK-BASELINE` |
| **Version** | 0.12, draft. Owner: this product |
| **Home** | `automated-checks/README.md` section 3, in the shared contracts catalog |
| **Role here** | owner |

## What this repository must do to stay conformant

- A baseline may fall and never rise; a class exposed to a wholesale re-freeze is judged by identifier
  set, not by count.
- Accepting new debt is an explicit act with a reason and a journal row, reviewable in a diff.
- A file of metric acceptance bounds is not a baseline and is not declared as one (0.11, section 8).
- Every set baseline is compared with its copy at `HEAD`; an identifier added since is a rise and is
  accepted only through the journalled update path (0.12 item B).

## Where it lives here

- The baseline files beside their checks under `scripts/quality/`.
- Set semantics enforced by `scripts/quality/assert-detekt-baseline-absorption.ps1` for the detekt and lint
  snapshots, and by `scripts/quality/assert-set-baseline-rise.ps1` for every other set baseline.
