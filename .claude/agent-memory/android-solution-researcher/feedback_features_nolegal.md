---
name: feedback-features-nolegal
description: docs/FEATURES*.md are for published standard/VR builds only; noLegal features go to docs/FEATURES_noLegal.md (gitignored)
metadata:
  type: feedback
---

Do NOT write noLegal-specific features into `docs/FEATURES.md`, `docs/FEATURES_RU.md`, or `docs/FEATURES_UK.md`. These files are for published (standard, VR) builds only.

**Why:** noLegal is a personal sideload-only build. Its features often involve GPL libraries, Chaquopy, heavy runtimes, or store-policy-violating tools - not suitable for a public feature list.

**How to apply:** When reading feature documentation during research, treat `docs/FEATURES.md` (+ `_RU`/`_UK`) as authoritative ONLY for standard/VR scope. For any noLegal-tagged topic, also read `docs/FEATURES_noLegal.md` (gitignored, .gitignore lines 263-265). In the research report, never cite a noLegal capability as if it lives in the public feature files; never claim a feature is "missing from docs" if it is a noLegal-only capability documented in the gitignored mirror.
