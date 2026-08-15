# Research 02 - Docs + site publication pipeline

**Артефакт research для S0815 F2.** Source: read-only agent sweep 2026-07-02. Evidence = quoted live paths.

## Site pipeline

- **Jekyll via GitHub Pages** - `.github/workflows/jekyll-gh-pages.yml`: builds+deploys on push to `main`, `source: ./` (whole repo is Jekyll source), theme `jekyll-theme-cayman`, `markdown: kramdown` (`_config.yml`).
- **Trigger paths** (`jekyll-gh-pages.yml:12-18`): `docs/**`, `dev/Q/**`, root `*.md`, `_config.yml`, **`assets/**`**, workflow file. Note: `assets/` path is in the trigger list but **no `assets/` dir exists** - placing new assets under `docs/**` is the safe choice (already triggers).
- **Publication is decoupled from `/skill-release`** - release only touches WHATS_NEW/README/FEATURES; the site rebuilds automatically on any `docs/**` push. No explicit "build site" step.
- Pages with Jekyll frontmatter (`docs/*.md`, `docs/howto/*.md`) get wrapped in the theme; static HTML (`index*.html`, `nolegal*.html`) passes through verbatim.

## Trilingual convention

Dominant: **EN = bare filename, RU/UK = `_RU.md`/`_UK.md`** (e.g. `HOW_TO.md` / `HOW_TO_RU.md` / `HOW_TO_UK.md`). (Other patterns coexist - howto uses lowercase `-ru.md`; `.ru.md`; `_EN` - but `_RU`/`_UK` is the dominant root-docs convention and the one to follow for a new legend page.)

User-facing pages: `README`, `QUICK_START`, `HOW_TO`, `FAQ`, `TROUBLESHOOTING`, `LIMITATIONS`, `MODULE_SELECTION`, `FEATURES`, `SETTINGS_REFERENCE`, `WHATS_NEW`. Index: `docs/DOCS_MAP.md`.

## Existing asset conventions (3 coexisting)

- Flat repo-root: `icon.png`, `favicon*`, `logo-*.png` - referenced by root `index*.html`.
- `docs/images/*.png` - screenshots for `docs/README.md` (`<img src="images/..">`).
- `docs/howto/screenshots/*.png` - scenario step shots (`![alt](screenshots/..png)`).

No `docs/icons/` yet. **SVG already renders through the pipeline** - `docs/README.md:9-11` embeds shields.io SVG badges via Markdown `![]()`. Format is proven; no local SVG embedded yet.

## Emoji-as-icon hotspots (targets for the "icons instead of emoji" convention)

- `index.html`/`index-ru`/`index-uk` cards: `<span class="card-icon">emoji</span>` throughout (per-card slot, 1:1 swappable).
- `docs/howto/index.md:18-28` scenario-picker bullets.
- `docs/DOCS_MAP.md` section headers.
- `docs/SETTINGS_REFERENCE*.md` is generated and **zero-emoji** (pure table - clean mechanical inline target).

## Generator + gate precedent (direct template)

- `SettingsManifestExportTest.kt` (Robolectric) live-scans real settings layouts (`LayoutSettingsSearchSource`) -> exports `docs/settings/settings-manifest.json` (freshness-asserted; regen via `-Dsettings.manifest.generate=true`).
- `scripts/docs/render-settings-reference.ps1` merges manifest + hand-authored `settings-annotations.json` (EN/RU/UK prose) -> deterministic `docs/SETTINGS_REFERENCE*.md`.
- `scripts/quality/assert-settings-doc-sync.ps1` - 5-stage composite: catalog completeness -> manifest freshness (JVM test) -> annotation coverage -> reference re-render+diff -> HOW_TO recipe freshness. Wired into `post-change.ps1` (Rule 22).
- `scripts/quality/assert-howto-settings-paths.ps1` (S0558) - cross-locale parity gate: per-locale signature compared positionally across EN/RU/UK. Model for icon cross-locale parity.
- `scripts/utils/update_docs_frontmatter.ps1` - idempotent Jekyll frontmatter injector for `docs/*.md` (top-level only).
- `XmlAttributeReader.kt` - reusable `XmlPullParser` attr reader for scanning icon attrs off layout/menu XML.

## Key design resolution inputs

- **Tint (open Q):** source VectorDrawables are "Tinted at usage site" with placeholder `fillColor="@android:color/white"` (`ic_add.xml:2,9`). Naive path->SVG yields wrong color. -> export with `fill="currentColor"` so icons inherit surrounding text color (works with the site light/dark toggle `index.html:98`).
- **Location:** single shared `docs/icons/` (SVGs under `docs/icons/svg/`), under `docs/**` (auto-triggers rebuild), reachable from root HTML (`docs/icons/..`) and nested docs (`icons/..`).
- **noLegal split precedent:** `render-settings-reference.ps1:47,51` already branches output by flavor (public vs gitignored `_noLegal`) - mirror if a noLegal legend variant is ever needed (not iteration 1).

## /spec-draft candidate (parked separately)

`logo-dark.png` / `logo-light.png` at repo root referenced nowhere (dead-weight, Rule 20).
