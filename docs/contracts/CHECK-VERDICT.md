# Pointer - `CHECK-VERDICT`

| | |
| --- | --- |
| **Id** | `CHECK-VERDICT` |
| **Version** | 0.9, draft; wire carrier: process exit code (0/1/2/3) plus one verdict line. Owner: this product |
| **Home** | `automated-checks/README.md` section 2, in the shared contracts catalog |
| **Role here** | owner and reference implementation |

## What this repository must do to stay conformant

- Four exit codes with fixed meanings; "could not verify" is never collapsed into a pass.
- Every non-zero exit prints its reason, and every documented code is reachable.
- One machine-readable verdict line; advisories are named, not counted.

## Where it lives here

- `scripts/post-change.ps1` and the checks under `scripts/quality/`.
- Enforced by `scripts/quality/assert-exit-contract.ps1`; the advisory class by
  `scripts/quality/lib/fixed-input-scope.ps1`.
