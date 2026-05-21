---
name: feedback_features_nolegal
description: docs/FEATURES*.md are for published standard/VR builds only; noLegal features go to docs/FEATURES_noLegal.md (gitignored)
metadata:
  type: feedback
---

Do NOT write noLegal-specific features into `docs/FEATURES.md`, `docs/FEATURES_RU.md`, or `docs/FEATURES_UK.md`. These files are for published (standard, VR) builds only.

**Why:** noLegal is a personal sideload-only build. Its features often involve GPL libraries, Chaquopy, heavy runtimes, or store-policy-violating tools - not suitable for a public feature list.

**How to apply:** Before adding any user-facing feature line to `docs/FEATURES.md` (+ `_RU`/`_UK`), check the spec's flavor scope. If the new capability is gated to `noLegal` only (S0156 epic, any spec with `noLegal` source-set placement, any class living under `src/noLegal/java/`), route the doc update to `docs/FEATURES_noLegal.md` + `_RU` + `_UK` instead - those mirrors are gitignored (.gitignore lines 263-265) and exist only locally. Never copy a `noLegal` line into the public files even by accident during a doc batch. Standard/lite/photos/legacy/vr features keep going to the public trio as usual.
