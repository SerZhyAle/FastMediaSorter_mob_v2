# Pointer - `BUILD-EVIDENCE`

| | |
| --- | --- |
| **Id** | `BUILD-EVIDENCE` |
| **Version** | 0.10, draft. Owner: this product |
| **Home** | `automated-checks/README.md` section 5, in the shared contracts catalog |
| **Role here** | owner |

## What this repository must do to stay conformant

- A check names the subject it checked.
- A produced artifact carries its own build's version.
- No test is re-run to green; the cure for a flaky test is a quarantine row.
- A run time stated in prose is judged against the telemetry median.
- A release artifact rebuilt outside the tested build states its binding to it: tests in the
  publishing job, or the tested tree equal to the tagged tree plus a version read back from the
  published artifact (rule 8).

## Where it lives here

- `scripts/quality/assert-artifact-version-fresh.ps1`, `scripts/quality/assert-no-test-retry.ps1`
  (quarantine ledger `docs/test-flaky-quarantine.jsonl`), `scripts/quality/assert-gate-timing-claims.ps1`.
- Rule 8 binding: the tested tree equals the tagged tree and the built tree, plus the versionCode read back from the built bundle - `scripts/quality/assert-release-tree-binding.ps1`, run by `/skill-release` before step 12a; stated in `docs/DEV_OPS.md` "Release tree binding".
