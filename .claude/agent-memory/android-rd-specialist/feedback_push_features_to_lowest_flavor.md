---
name: push-features-to-lowest-flavor
description: When drafting/breaking down specs, place new functionality at the lowest (broadest) flavor level it can legally live in - don't default to noLegal; if the level isn't obvious, ASK the owner
metadata:
  type: feedback
---

When composing or decomposing a spec for new functionality, default to placing it at the **lowest (broadest, most-shipped) flavor level** it can legally occupy - not the most restricted one. Only gate a feature higher (e.g. into `noLegal`, or `vr`-only) when there is a concrete legal / store-policy / capability reason it cannot ship in the broader flavor. If it is **not obvious** at which level the feature should appear, **ask the developer** before fixing the placement in the spec.

**Why:** Owner observed (2026-06-24) that a lot of new functionality landed in `noLegal` even though it was perfectly legal in `standard` and could have shipped to far more users. Over-gating to the most-restricted flavor is the path of least resistance (noLegal is all-inclusive, so "it builds there" is easy), but it silently shrinks reach and contradicts the inclusion hierarchy ([[vr-inclusion-hierarchy]]: `standard ⊂ vr ⊂ noLegal`). Placement is a real product decision, not a default.

**How to apply:**
- During `/spec`, `/spec-draft`, `/spec-tech`: when a feature's flavor home is unstated, do NOT silently put it in `noLegal`. First check whether anything legally/technically blocks it from `standard` (Play-store policy, GPL/yt-dlp/Python deps, VR/XR hardware, capability gate).
- If nothing blocks it -> target `standard` (or the broadest applicable flavor) so lite/photos/legacy can inherit per the matrix.
- If a blocker exists -> place it at the lowest flavor that clears the blocker (`vr` before `noLegal` when store-publishable).
- If you genuinely can't tell whether a blocker exists (ambiguous legality / capability) -> surface the question to the owner via AskUserQuestion, listing the candidate levels and the blocker you're unsure about. Don't guess.
- Source-set discipline from [[flavor-isolation-strict]] still applies once the level is chosen - this rule decides *which* flavor, that rule decides *how* to wire it.
