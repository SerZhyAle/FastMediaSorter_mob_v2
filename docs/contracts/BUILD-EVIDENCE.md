<sub class="doc-stamp">26.11.20 20:17</sub>

# Pointer - `BUILD-EVIDENCE`

| | |
| --- | --- |
| **Id** | `BUILD-EVIDENCE` |
| **Version** | 0.12, draft. Owner: this product |
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
- One disclosed repeat of an automated end-to-end UI flow after a failed assertion or an execution error is not a test
  retry in the sense of rule 5.3 (0.12, item D, S4650): the attempt count, both statuses and both logs are kept, a pass
  on the repeat is marked flaky beside the verdict, and a final failure or an application crash is classified as before;
  the ban on re-running a unit test to green is unchanged.

## Where it lives here

- `scripts/quality/assert-artifact-version-fresh.ps1`, `scripts/quality/assert-no-test-retry.ps1`
  (quarantine ledger `docs/test-flaky-quarantine.jsonl`), `scripts/quality/assert-gate-timing-claims.ps1`.
- Rule 8 binding: the tested tree equals the tagged tree and the built tree, plus the versionCode read back from the built bundle - `scripts/quality/assert-release-tree-binding.ps1`, run by `/skill-release` before step 12a; stated in `docs/DEV_OPS.md` "Release tree binding".
