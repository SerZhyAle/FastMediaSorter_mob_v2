---
name: recheck-catalog-status-before-final-flip
description: Re-read the catalog row right before any end-of-round status flip - the preflight status is a snapshot and a sibling session can advance the ticket mid-round even when your lease was granted.
metadata:
  type: feedback
---

Before flipping a ticket's status at the end of a round, re-read its catalog row with
`select.ps1 -Id Sxxxx -Format json`. Never flip from the status the preflight handed you.

**Why:** on 2026-08-11 `/spec-next` preflight handed S1560 as `In Progress` and `ticket-lease.ps1 -Verb Claim`
returned exit 0 - the ticket was mine. A concurrent session worked it anyway: mid-round it ticked the phase-05
steps, rewrote INDEX to 5/5, ran a full `/spec-check` (PASS 15 · FAIL 0 · MANUAL 1) and set the ticket to
`Verified`. Acting on the stale `In Progress` snapshot I judged §11's clean-install criteria to need a device
and ran `update.ps1 -Status BlockNeedUserTest`, which printed the real prior state only *after* the write:
`S1560 Verified -> BlockNeedUserTest`. That regressed an audited ticket and pulled in five `Timber.d("S1560:`
probes that then had to be removed again. A granted lease is not proof of exclusivity - leases lapse between
steps, and the sibling took no lease at all.

**How to apply:** the cheap tell is `update.ps1`'s own `<old> -> <new>` line - read it, and if `<old>` is not
what you expected, stop and reconcile before doing anything else. Cheaper still, re-run `select.ps1` first.
Two signals that a sibling is inside your round: spec/INDEX files reported as "modified since read" by the Edit
tool, and a `## Last Audit` block appearing in a spec whose preflight said `last_audit_present: false`. A real
`/spec-check` outcome outranks your own judgement about whether a device gate is needed - `Verified` with a
MANUAL on-device checkbox is a sanctioned terminal, not an unfinished one. To undo a wrong `Block*` flip, use
the two-step in [[close-ps1-two-step-unblock]]. See also [[ide-open-spec-may-finalize-midtask]] and
[[ticket-busyness-is-a-lease-not-a-status]].
