---
name: old-capture-may-be-superseded
description: Verify a /spec-draft capture's factual claims against the tree before speccing it - an aged one may already be half-shipped, and even a same-day one can simply be wrong.
metadata:
  type: feedback
---

**Rule.** When a `/spec-draft` capture is more than a couple of weeks old and still a skeleton, find out what
shipped in that area *since the capture date* before writing the strategic spec. Search the code for the
behaviour the owner asked for, and check the tickets that touched it. Only then decide what the problem
statement is.

**Why.** S1235 (captured 2026-07-27: "a tap on a device profile is select plus Next") sat as a skeleton while
S1383 - an agent-authored bugfix, not an owner request - shipped a *different* answer to it on 2026-08-04:
the first tap selects and expands the tile description, a second tap on the already-selected tile advances.
Nothing recorded that against S1235, so the ticket still read as untouched. Speccing it cold would have
produced one of two wrong outcomes: implementing the literal one-tap over a deliberate, device-verified
design, or closing it as "already done" when the owner's actual words were never satisfied for any tile but
the preselected one. The gap was only visible from a KDoc line in the page holder naming S1383.

**How to apply.**

- **Age is not the tell, and a fresh capture is not a trusted one.** A capture states evidence as fact
  ("neither model carries `@SerializedName`", "no keep rule covers it"); re-check each such claim against the
  tree before it becomes §1, whatever the date. S1638 was captured and worked the same day, yet its central
  claim was false: both models had been fully annotated a month earlier by S0957, which also shipped a JVM
  guard test and a minified R8 proof. Accepting the capture would have "fixed" what was already fixed and
  closed a priority-90 ticket as a no-op. What was genuinely open sat one line below, in the capture's second
  bullet, and matched S0957's own recorded residual verbatim - so the capture was worth keeping, just not
  worth believing.
- An old skeleton adds the second failure mode on top: later work may have shipped a variant nobody recorded
  against the original ticket.
- Read the code path the request names *before* writing §1. A KDoc citing a ticket id is the cheapest
  evidence that something already landed there; `temp/done/` holds archived specs when the id is not in
  `PLAN/`.
- When a later ticket partly answered the request, that fact **is** §1 - write what shipped, what the owner
  asked for, and where they differ. Do not silently pick one.
- Whether the shipped variant counts as satisfying the request is the owner's call, not a judgement call:
  park it with the costed options rather than closing the ticket as done.
- An agent-authored predecessor carries less weight than the owner's own words, but it is not nothing when
  it was verified on device - say who decided what, and let the owner choose.

**Related memories:** [[spec-dev-continue-verify-code-first]], [[dead-code-vs-active-tickets]],
[[never-attribute-agent-inference-to-owner]].
