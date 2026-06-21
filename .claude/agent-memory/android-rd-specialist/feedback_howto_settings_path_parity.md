---
name: howto-settings-path-parity
description: HOW_TO settings-path recipes using arrow → are gate-validated AND require EN/RU/UK positional parity; ASCII > is not validated
type: feedback
---

The S0558 gate `scripts/quality/assert-howto-settings-paths.ps1` (a sub-stage of `settings-doc-sync`, pure text - no gradle) validates "Settings -> .." navigation recipes in `docs/HOW_TO.md` / `_RU` / `_UK`.

**A recipe is any line containing the `→` (U+2192) char AND the localized "Settings" anchor word** (`Settings`/`Настройки`/`Налаштування`). ASCII `>` or `->` chains are NOT recipes and are NOT validated.

Two enforced invariants:
- Each non-tab segment must resolve case-sensitively to a manifest entry title under that tab's destination, a Media sub-section (`docs/settings/howto-path-vocab.json` `mediaSubsections`), or an allow-listed screen. Tab names come from the vocab `tabs`. `Clean-Seg` strips surrounding quotes (incl. `«»`), a trailing `= value`, and " tab"/"вкладка", so decorations are fine but the core label must match the manifest exactly (e.g. PLAYBACK tab is `Воспроизведение`/`Відтворення`, NOT `Плеер`; the toggle is `Увімкнути трансляції` lowercase).
- **Cross-locale parity:** EN/RU/UK must have the SAME number of `→` recipes and positionally (by scan order) the same signature. Adding a `→` recipe to one locale only breaks parity.

**How to apply:** when documenting a feature's settings path in HOW_TO, either (a) make the recipe identical and present in all three locales in the same position (hard when the feature lands in different sections per language), or (b) write the nav with ASCII `>` so it is not gate-counted - this is what existing EN Streams docs do (`Settings > Media > Streams`). For a quick doc-only feature add, prefer `>` to avoid the positional-parity trap. Run the gate standalone (fast, no gradle) to check: `assert-howto-settings-paths.ps1`.
