---
name: drift-check-scans-kotlin-only
description: drift-check.ps1 reports CLEAN for a ticket whose fix is XML/resource-only - it counts .kt markers and commits, never layout comments
metadata:
  type: feedback
---

`scripts/spec_catalog/drift-check.ps1` verdict `CLEAN` does not mean "no code exists for this ticket" - it
means no `Sxxxx:` marker was found in `.kt` and no commit carried the id. A fix that lives entirely in
`res/layout*/*.xml` (or any non-Kotlin resource) reads CLEAN even when the layout header already carries an
`Sxxxx:` comment describing the finished fix.

**Why:** on S1590 (2026-08-12) the ScrollView fix was already in `dialog_camera_settings.xml`, the spec was
still `Draft` with empty sections, and drift-check said CLEAN - only reading the layout revealed the work was
done. Acting on the CLEAN verdict would have meant re-implementing an existing fix.

**How to apply:** for a ticket whose symptom is layout/visual, grep the candidate `res/layout*` files for
`Sxxxx` before trusting a CLEAN drift verdict, then switch to review mode ([[spec-dev-continue-verify-code-first]])
instead of re-implementing.
