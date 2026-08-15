# S1582 Research - Acceptance Probe Contract

## Scope

S1582 validates that an explicit expected diagnostic signal recorded for a ticket awaiting device
verification is present in the active source tree. It does not infer a requirement from arbitrary
quoted prose.

## Findings

- `S1290` already separates probe existence from semantic correspondence and explicitly lists the
  latter as a separate broader task.
- `assert-no-ticket-logs.ps1` reconstructs complete `Timber` calls before matching. This handles a
  message literal located on a later physical line and is the required source-scanning behaviour.
- Status notes are stored by `update.ps1` and mirrored into the strategic header. `S1504` proves
  the write path accepts a complete quoted value when the caller passes it as one argument.
- S1417 has a live camera-menu template but no `hardwareLevel` field; S1478 has
  `S1478: headless lens=%s rotation=%d aspect=%d` rather than the stale `headless capture` phrase.
- S1419 and S1579 have no live temporary probes, so they cannot truthfully carry a source-backed
  literal. Their acceptance contract must declare alternative evidence rather than substitute an
  invented message.
- Additional stale candidates exist in S0429 and S1424. The audit must not parse historic Last
  Audit prose, because it can describe a superseded observation.

## Decision

Use explicit `Probe literal:` and `Probe template:` markers for source-backed expectations, plus
`Probe none:` with a named alternative evidence type when no temporary probe is expected. A literal
or template must start with its own `S####:` prefix and include a meaningful static suffix. The
checker extracts only source-backed markers, matches them against reconstructed temporary `Timber`
templates across `app_v2` and `wear`, and treats format or interpolation tails as runtime-variable.
This keeps explanation prose out of the contract and preserves the existing multiline-call safety
property.
