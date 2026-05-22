---
name: feedback-timber-tags-before-test
description: When grepping for Timber.d("Sxxxx:") during research, presence is a live test-gate marker NOT evidence of finished work - cross-check the spec status first
metadata:
  type: feedback
---

When grepping for `Timber.d("Sxxxx:` across `.kt` files during research, treat the presence of a tag as a **live test-gate marker**, NOT as evidence that the spec is finished. The tag's lifecycle is bound to the `BlockNeedUserTest` status by CLAUDE.md "Debug Verification Tags".

**Why:** The tag is the operator's logcat probe for an in-progress device-test round. A tag whose `Sxxxx` is currently `BlockNeedUserTest` means "the code path is being verified on hardware right now". A tag whose `Sxxxx` is anywhere else (Verified, Implemented, Tactical, In Progress, Archived..) is **stale** - a sign the previous writer agent did not remove it on status transition. Mis-reading either signal corrupts the research report.

**How to apply:**
- When the research scope mentions Sxxxx and a grep returns a matching `Timber.d("Sxxxx:` tag: run `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id Sxxxx -Format json` first to get the current status. Do NOT infer status from the tag's presence or absence alone.
- In the research report, if the tag exists AND status is `BlockNeedUserTest`: cite it as "live test marker, status BlockNeedUserTest".
- If the tag exists AND status is anything else: report it as "stale verification tag - tag lifecycle broken" under "Risks Identified" (Low severity, tooling debt).
- If the tag is absent AND status is `BlockNeedUserTest`: report as "missing test marker - blocks device verification" under "Risks Identified" (Med severity).
- Never recommend in the report that the researcher itself remove or insert a tag - that is a writer-agent operation gated by status transitions.
