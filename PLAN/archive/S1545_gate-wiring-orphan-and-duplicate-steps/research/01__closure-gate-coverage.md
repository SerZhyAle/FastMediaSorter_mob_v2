# S1545 research 01: closure gate coverage

## Method

Read the closure-facade eligibility predicates and each relevant invoked wrapper. Read the common source-rule registry rather than inferring coverage from wrapper names. Use the stored process audit only for aggregate cost context, not as a current per-rule timing result.

## Findings

- The closure facade invokes the flavour-flag, public-mutable-flow and deprecated-package-manager wrappers for code changes.
- The same facade invokes the unfiltered lexical umbrella for code and resource changes.
- The common registry includes rules named `flavor-flags`, `public-mutable-flow` and `deprecated-pm-flags`.
- Each of the three wrappers forwards exactly one `Only` value to the common runner, so it repeats a predicate and baseline already reached by the umbrella for every code-change invocation.
- The stored 2026-07-31 process audit shows that gate costs must be interpreted separately from lock wait and that broad optimisation should follow evidence. It also establishes that aggregate gate time is dominated by other work, so this ticket must preserve coverage and diagnostics rather than claim a release-scale performance win from three wrappers alone.

## Decision

The duplicate calls are a structural defect independently of a fresh per-wrapper timing table. The implementation will remove only redundant facade calls, retain rule definitions and wrappers, and record comparable own-duration evidence around the changed route. Lock waiting is reported separately.

## Evidence

- `scripts/post-change.ps1` eligibility and invocation blocks.
- `scripts/quality/lib/source-matchers.ps1` common rule registry.
- `scripts/quality/assert-neuroslop.ps1` unfiltered common-runner delegation.
- `dev/AGENT_PROCESS_AUDIT_2026-07-31.md`, gate-cost findings.
