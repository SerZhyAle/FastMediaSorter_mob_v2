# S1545 research 02: rule diagnostics

## Method

Compare the responsibilities of the common rule registry, the unfiltered umbrella, and each narrow wrapper.

## Findings

- The common registry gives every rule a stable name, a baseline and a failure message.
- The unfiltered umbrella delegates to the common runner without an `Only` filter.
- The narrow wrappers supply only their respective rule name through `Only` and otherwise delegate to the same runner.
- Therefore the narrow facade-step label is not the only source of diagnostic identity. The common-runner result can preserve the rule name while avoiding the duplicated scan.

## Decision

Route automatic closure through the common pass once and retain the individual rule name in its emitted failure record. Keep the narrow wrappers for explicit operator invocation and compatibility, not as a second closure route.

## Evidence

- `scripts/quality/lib/source-matchers.ps1` common rule records.
- `scripts/quality/assert-neuroslop.ps1` common-runner delegation.
- `scripts/quality/assert-deprecated-pm-flags.ps1` and equivalent narrow wrappers.
