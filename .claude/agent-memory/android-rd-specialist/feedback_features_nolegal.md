---
name: feedback-features-nolegal
description: docs/FEATURES*.md are for published standard/VR builds only; noLegal features go to docs/FEATURES_noLegal.md (gitignored)
type: feedback
---

Do NOT write noLegal-specific features into `docs/FEATURES.md`, `docs/FEATURES_RU.md`, or `docs/FEATURES_UK.md`. These files are for published (standard, VR) builds only.

**Why:** noLegal is a personal sideload-only build. Its features often involve GPL libraries, Chaquopy, heavy runtimes, or store-policy-violating tools - not suitable for a public feature list.

**How to apply:** Any spec tagged noLegal-only (S0156 epic, or any spec with "noLegal" flavor gate) → document in `docs/FEATURES_noLegal.md`. That file is gitignored (.gitignore lines 263-265). The `/doc-update` skill must not touch FEATURES*.md for noLegal specs. When a /spec-dev or /spec-all step says "update FEATURES docs" for a noLegal feature, redirect to FEATURES_noLegal.md instead.
