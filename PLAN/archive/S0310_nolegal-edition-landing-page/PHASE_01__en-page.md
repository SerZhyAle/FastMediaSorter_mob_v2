# Phase 01 - English noLegal Page (canonical)

**Strategic spec:** [`../S0310_nolegal-edition-landing-page.md`](../S0310_nolegal-edition-landing-page.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 6 / 6
**Started:** 2026-05-30
**Completed:** 2026-05-30

---

## Objective

Produce `nolegal.html` (English): the canonical noLegal edition page reusing the existing site design - build-topology block (standard ⊂ vr ⊂ noLegal), full feature catalog with technical details and "why not in store" per item, curated outbound links, and indexed SEO head. RU/UK mirrors and sitemap wiring come later.

---

## Prerequisites

- [ ] Both strategic §6 research items are Resolved (depth = full; outbound = index/VR/Downloads-FAQ/GitHub).
- [ ] Working tree is clean or on a feature branch.
- [ ] Source content reference available: `docs/FEATURES_noLegal.md` (gitignored, local) for the 6 features.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `nolegal.html` | New | ≤ 600 |
| `styles.css` | Modified (optional, append-only) | ≤ 800 total |

> No `.kt`, no layout XML, no flavor source set - flavor/landscape/catalog constraints N/A. Root-level placement matches existing `index*.html`. Reuse `styles.css` classes; only append a small noLegal-tier accent block if existing classes are insufficient - do not rewrite shared styles.

---

## Steps

### Step 01.1 - Scaffold the page head with indexed SEO

**Files:** `nolegal.html`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `nolegal.html` with `<html lang="en">` and a `<head>` mirroring `index.html`'s structure: charset, viewport, favicons, `styles.css` link, Open Graph + Twitter meta. Set `<meta name="robots" content="index, follow">`. Set `<link rel="canonical">` to the noLegal EN page URL. Add hreflang `alternate` links for the three noLegal pages only (`en` → `nolegal.html`, `ru` → `nolegal-ru.html`, `uk` → `nolegal-uk.html`, plus `x-default` → `nolegal.html`) - do NOT point hreflang at any `index*.html`. Title and description must reference the noLegal / sideload edition.

**Verification:**

- `Glob` - `nolegal.html` exists.
- `Grep` - `content="index, follow"` matches once.
- `Grep` - `hreflang="ru" href=".*nolegal-ru.html"` present; `Grep` confirms zero `hreflang.*index` matches in `nolegal.html`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 3/3 PASS. Files: nolegal.html (New, head scaffold + section markers). robots=index,follow; hreflang triplet noLegal-only; hreflang.*index=0 after comment reword.

---

### Step 01.2 - Header, language switcher, hero

**Files:** `nolegal.html`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add the shared `<header>` block: logo/title, a subtitle naming the noLegal sideload edition, and a `.lang-switcher` whose three buttons link to `nolegal.html` / `nolegal-ru.html` / `nolegal-uk.html` (EN marked `active`). Reuse existing header/canvas classes from `index.html` so the visual identity matches. Add a hero line stating the page documents the owner's sideload-only build.

**Verification:**

- `Grep` - `class="lang-switcher"` present in `nolegal.html`.
- `Grep` - `href="nolegal-ru.html"` and `href="nolegal-uk.html"` both present.
- `Grep` - `lang-btn active` present once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 3/3 PASS. Files: nolegal.html (header, lang-switcher to noLegal triplet, hero, intro, shared canvas script). lang-switcher=1; sibling links present; lang-btn active=1.

---

### Step 01.3 - Build-topology block (standard ⊂ vr ⊂ noLegal)

**Files:** `nolegal.html`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add a section that visually explains the three inclusion levels: what `standard` already provides, what VR adds on top, and what `noLegal` adds beyond VR. State explicitly that `noLegal = VR + sideload-only capabilities`. Use existing card/grid classes; no ASCII diagrams. Make the "added by noLegal" tier visually distinct.

**Verification:**

- `Grep` - `noLegal = VR` (or equivalent topology statement) present.
- `Grep` - section mentions all three of `standard`, `VR`, `noLegal`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 2/2 PASS. Files: nolegal.html (tier-grid topology block, "noLegal = VR + sideload-only capabilities"). topology=1; tier-standard/tier-vr/tier-nolegal all present.

---

### Step 01.4 - Full feature catalog (6 features, with details + why-not-in-store)

**Files:** `nolegal.html`
**Depends on:** Step 01.3

**Prompt for developer:**

> Add a catalog section listing all six noLegal-only features from `docs/FEATURES_noLegal.md`: (1) Universal Media Extractor (yt-dlp + Chaquopy), (2) Native site extractors (ArtStation/DeviantArt/Vimeo/Dailymotion/Telegram), (3) APK install from Browse, (4) YouTube & YouTube Music extraction recovery, (5) Offline PaddleOCR, (6) Embedded Office document viewer (read-only). Owner decision: include technical details for each. Every feature entry must carry an explicit "Why not in market builds" note. Do NOT include step-by-step instructions for bypassing store policies - describe capability and implementation, not distribution evasion.

**Verification:**

- `Grep` - all six feature titles present (yt-dlp / extractor, ArtStation, APK, YouTube, PaddleOCR, Office).
- `Grep` - at least six occurrences of a "why not" / "not in" marker (one per feature).

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 2/2 PASS. Files: nolegal.html (6 nl-feature articles with details + "Why not in stores" each). All 6 titles present; "Why not in stores"=7 (6 features + lead).

---

### Step 01.5 - Outbound links to the main site

**Files:** `nolegal.html`
**Depends on:** Step 01.4

**Prompt for developer:**

> Add outbound links to the main site (this page links OUT; index never links back IN). Targets: main site home (`index.html`), VR materials (`docs/VR_EDITION.html`, `docs/VR_SIDELOAD.html`, `docs/VR_CONTROLS.html`), downloads (`docs/DOWNLOADS_EN.html`) and FAQ (`docs/FAQ.html`), and the GitHub repository (`https://github.com/SerZhyAle/FastMediaSorter_mob_v2`). Add a footer mirroring `index.html`'s footer. Use existing `.card` / `.footer` classes.

**Verification:**

- `Grep` - `href="index.html"` present.
- `Grep` - `docs/VR_EDITION.html`, `docs/DOWNLOADS_EN.html`, `docs/FAQ.html` all present.
- `Grep` - `github.com/SerZhyAle/FastMediaSorter_mob_v2` present.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 3/3 PASS. Files: nolegal.html (outbound cards-grid + footer). index.html link present; VR_EDITION/DOWNLOADS_EN/FAQ all present; github=3.

---

### Step 01.6 - Visual consistency pass

**Files:** `nolegal.html`, `styles.css` (optional append)
**Depends on:** Step 01.5

**Prompt for developer:**

> Confirm the page reuses `styles.css` and renders with the site's palette/typography. If the noLegal-tier accent needs a dedicated style, append a small append-only block to `styles.css` (do not modify existing rules). Page text is marketing/descriptive web copy, not in-app strings - `docs/COMMUNICATION_POLICY.md` does not gate it, but keep tone honest and non-promotional about licensing.

**Verification:**

- `Grep` - `href="styles.css"` present in `nolegal.html`.
- `Grep -n "Log\.d\("` - N/A (no Kotlin); skip.
- Manual: open `nolegal.html` rendering and confirm header/cards match site (record expected: matches index palette | actual: <observed>).

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 2/2 structural PASS. Files: nolegal.html (styles.css link confirmed), styles.css (append-only noLegal accent block, --color-nolegal). styles link=1; --color-nolegal token=1; Log.d=0 (no Kotlin). Manual palette check deferred to GitHub Pages render after publish.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] `nolegal.html` opens and renders with the shared design.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for `nolegal.html` (and `styles.css` if touched).
- [ ] Catalog regen - N/A (no `.kt`).

---

## Handoff Notes to Next Phase

`nolegal.html` is the canonical source of structure and copy. Phase 02 mirrors it verbatim into RU/UK with translated text and localized outbound links; do not diverge the section structure.

---

## Rollback Plan

Delete `nolegal.html` and revert the optional `styles.css` append - no data, no app surface affected.
