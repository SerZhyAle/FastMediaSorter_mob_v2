# Pointer - `BUILD-EVIDENCE`

| | |
| --- | --- |
| **Id** | `BUILD-EVIDENCE` |
| **Version** | 0.9, draft. Owner: this product |
| **Home** | `automated-checks/README.md` section 5, in the shared contracts catalog |
| **Role here** | owner |

## What this repository must do to stay conformant

- A check names the subject it checked.
- A produced artifact carries its own build's version.
- No test is re-run to green; the cure for a flaky test is a quarantine row.
- A run time stated in prose is judged against the telemetry median.

## Where it lives here

- `scripts/quality/assert-artifact-version-fresh.ps1`, `scripts/quality/assert-no-test-retry.ps1`
  (quarantine ledger `docs/test-flaky-quarantine.jsonl`), `scripts/quality/assert-gate-timing-claims.ps1`.
