---
name: doc-pin-tooling-ownership-split
description: Two doc-vs-build pin tools split ownership - generate-toolchain-pins owns CLAUDE.md/TECH_STACK.md, check-doc-vs-gradle owns dev/TECH_REQUIREMENTS.md (S1075)
metadata:
  type: feedback
---

Two overlapping tools keep dependency/pin versions in docs honest, and since S1075 they have a **strict, non-overlapping ownership split**:

- `scripts/quality/generate-toolchain-pins.ps1` OWNS the pin block in `CLAUDE.md` + `docs/TECH_STACK.md` (a `<!-- toolchain-pins -->` managed generated block, format `- Label: value`, read from Gradle). Drift there is structurally impossible. Gate: `doc-pins-sync` in post-change.
- `scripts/check-doc-vs-gradle.ps1` (S0271, matcher manifest `scripts/doc-drift/pins.psd1`) now checks ONLY `dev/TECH_REQUIREMENTS.md` (the hand-maintained library/platform tables the generator does not touch). Gate: `doc-pin-drift` (wrapper `scripts/quality/assert-doc-pin-drift.ps1`) in `fg` + post-change.

**Why:** the two tools expect DIFFERENT text formats for the same pins, so before S1075 the S0271 matchers reported permanent false MISSING against the generator's managed block (e.g. `Kotlin: 2.2.10` vs matcher wanting `Kotlin 2.2.10`). Owner decision (Path A): let the generator be canonical for its two docs; in `pins.psd1` those mentions are `required=$false; matcher=$null`.

**How to apply:**
- Do NOT re-enable `required=$true` for a `CLAUDE.md`/`docs/TECH_STACK.md` entry in `pins.psd1` - it only re-introduces format-mismatch MISSING noise.
- A pin drift FAIL from the `doc-pin-drift` gate means `dev/TECH_REQUIREMENTS.md` lagged a Gradle bump - fix that doc's table, not the generated blocks.
- After a Gradle dependency bump: `generate-toolchain-pins.ps1 -Write` refreshes the two generated docs; `dev/TECH_REQUIREMENTS.md` is hand-edited and verified by `check-doc-vs-gradle.ps1` (exit 0 = clean).
- Wrapping a non-strict project script in a `Set-StrictMode -Version Latest` gate: invoke it in a SEPARATE `pwsh` process (`& $pwshExe -NoProfile -File ...`), not in-process `& $script`, or StrictMode leaks into the child and breaks its `.Count`/`$null` access.
