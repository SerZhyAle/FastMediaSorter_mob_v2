---
name: howto-settings-path-gate
description: HOW_TO "Settings -> .." recipe drift gate (S0558) - where it lives and the vocab extension point when it fails
type: reference
---

S0558 added a drift gate for the "Settings -> tab -> label" recipes embedded in `docs/HOW_TO*.md` (EN/RU/UK), mirroring the S0440 SETTINGS_REFERENCE mechanism.

- Gate: `scripts/quality/assert-howto-settings-paths.ps1` (pure text, no gradle). Detects arrow (U+2192) chains anchored on the localized word for "Settings", resolves every segment against `settings-manifest.json` titles + a vocab, cross-checks the leaf's destination, and enforces EN/RU/UK structural parity (same recipe count + positional signature).
- Runs as **stage 5** of `assert-settings-doc-sync.ps1` and as a standalone narrow step in `post-change.ps1` on `docs/HOW_TO*.md` edits.

**How to apply:** when the gate fails with "segment '<x>' has no matching setting/header/sub-section", first check it is real label drift (fix the HOW_TO to match the manifest title). If the segment is a legitimate tab / Media sub-section / non-settings screen (e.g. "Controls & Keybindings") not stored in the manifest, extend `docs/settings/howto-path-vocab.json` (tabs / mediaSubsections / allowedScreens, all three locales). Tab completeness is self-checked against the manifest destination enum; sub-sections and screens are extend-as-needed allow-lists.
