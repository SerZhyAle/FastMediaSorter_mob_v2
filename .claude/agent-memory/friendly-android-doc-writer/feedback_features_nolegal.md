---
name: feedback-features-nolegal
description: docs/FEATURES*.md are for published standard/VR builds only; noLegal features go to docs/FEATURES_noLegal.md (gitignored)
metadata:
  type: feedback
---

Do NOT write noLegal-specific features into `docs/FEATURES.md`, `docs/FEATURES_RU.md`, or `docs/FEATURES_UK.md`. These files are for published (standard, VR) builds only.

**Why:** noLegal is a personal sideload-only build. Its features often involve GPL libraries, Chaquopy, heavy runtimes, or store-policy-violating tools - not suitable for a public feature list.

**How to apply:** Before drafting a feature paragraph, decide if the capability is `noLegal`-only. If yes, write into `docs/FEATURES_noLegal.md` + `_RU` + `_UK` (gitignored); never touch the public `docs/FEATURES*.md`. If unsure about the gate, ask before drafting - one clarifying question beats a public-doc leak that has to be reverted in three locales.
