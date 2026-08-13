# S0889 - Docs/site iteration 2: replace emoji with real interface icons inline

**Ticket:** S0889
**Status:** Archived
**Priority:** 45
**Date:** 2026-07-02
**Tier:** 3 - Moderate (ad-hoc)

<!-- discovered by /spec-all S0815 - 2026-07-02 (deferred iteration 2) -->

## 0. Raw capture (inbox)

**Captured:** 2026-07-02, during S0815 (icon inventory + trilingual legend, iteration 1, Verified).

S0815 built the icon system - `docs/icons/icon-inventory.json`, 58 exported `currentColor` SVGs under `docs/icons/svg/`, a trilingual legend `docs/ICON_LEGEND{,_RU,_UK}.md`, and a drift gate `assert-icon-inventory-sync.ps1`. Iteration 1 deliberately scoped itself to a central legend page and deferred the heavier per-page inline embedding (research/03 D5, INDEX "Out of scope").

Iteration 2 = apply the icons INLINE where the docs/site currently use emoji or bare text:
- `index.html` / `index-ru.html` / `index-uk.html` card slots (`<span class="card-icon">emoji</span>`) - swap each emoji for the matching `docs/icons/svg/<drawable>.svg` (1:1 slot, trilingual HTML).
- `docs/howto/index.md` scenario-picker bullets (emoji).
- `docs/DOCS_MAP.md` section headers (emoji).
- Generated `docs/SETTINGS_REFERENCE*.md` - inline the matching icon per settings row (mechanical, but a change to `render-settings-reference.ps1`, separate blast radius).

Also candidates for iteration 2:
- Optional emoji-ban mechanical gate for docs (iteration 1 intentionally did NOT add one - would flag all existing site emoji).
- noLegal VR legend variant (VR icons are `public=false`, excluded from the public legend).

## 1. Why deferred

- High trilingual-edit volume (S0815 §7 "большой объём трилингвальных правок") - the exact risk iteration 1 phased out.
- The landing pages are static HTML (not the generated legend), so each swap is a hand-edit x3 locales; the SETTINGS_REFERENCE inline touches a generator with its own drift gate.

## 2. Preconditions (all shipped by S0815)

- `docs/icons/svg/<drawable>.svg` assets exist and are gated.
- `docs/icons/icon-inventory.json` maps surface/key/feature -> drawable.
- Icon meanings resolve from app strings (see `render-icon-legend.ps1`).

## 3. Open questions (for /spec-tech)

- Per-page-family embedding rules (static HTML card vs generated MD row vs hand-authored prose).
- Whether the emoji-ban gate ships here or stays deferred.
- Accessibility: every inline icon keeps its adjacent text label (S0815 §3.2 constraint carries over).

## Research 2026-07-03 (/spec-all, pre-tactical)

Inventory vocabulary check against every target surface - the blocker is NOT effort, it is that most emoji slots have no canonical app icon:

- Inventory surfaces: player-command (54), program-nav (21), send-to (10), settings-header (14), settings-row (20). No doc-genre icons exist.
- `SETTINGS_REFERENCE*` sections: canonical settings-header icons exist for 6 of 9 sections (images -> ic_image, video -> ic_video, audio -> ic_audio, documents -> ic_book, streams -> ic_cast, other -> ic_camera_capture); `general`, `playback`, `destinations` have NO app icon (they are reference groupings, not app headers).
- `docs/howto/index.md` bullets (11 slots): canonical only for SMB (ic_resource_smb) and calculator (ic_calculator); defensible approximations for radio (ic_cast), camera backup (ic_camera_capture), photo frame (ic_image), cinema (ic_video), car music (ic_audio), notes (ic_menu_edit); NO mapping for organizer (ic_menu_sort_alphabetically is a text-encoding sort), widgets, and VR (ic_vr_headset is public=false - excluded from exported SVGs).
- Landing `index*.html` cards (12 slots x 3 locales): same mapping gaps PLUS a styling decision - exported SVGs are `fill="currentColor"`, which inside `<img>` renders black; the landing is dual-theme (prefers-color-scheme), so dark mode needs inline `<svg>` embedding or a CSS mask/filter treatment. No site precedent exists.
- `docs/DOCS_MAP.md` headers: document-category pictograms (map, rocket, globe, scales, wrench) - no app-icon vocabulary at all.

## Owner decisions (2026-07-03)

Resolved via /spec-quiz-style AskUserQuestion; supersedes the "Blocking questions" framing below.

1. **Mapping policy:** No emoji at all. "We have all drawings here - turn it into png to show on site." -> source icons from the **full app drawable set** (`app_v2/src/main/res/drawable*`), not only the 58 public exported SVGs; where an emoji slot has no obvious public icon, pick the matching app drawable and export it. Site display format = PNG (raster), except the landing (see #2).
2. **Landing cards:** inline `<svg currentColor>` (theme-correct in the dual light/dark landing). Owner's explicit pick over `<img>` (which renders black in dark mode).
3. **Reference sections without a header icon (General / Playback / Destinations):** add supplemental drawables so every `SETTINGS_REFERENCE` section carries an icon (author via the icon-annotations sidecar + export).

### Resolved approach

- Zero emoji across `index*.html`, `docs/howto/index.md`, `docs/DOCS_MAP.md`, `docs/SETTINGS_REFERENCE*.md`.
- Non-landing surfaces (markdown): follow the shipped `ICON_LEGEND` pattern - `<img>` embedding the icon, but as **PNG** rasterized from the app vector drawable (owner's format call).
- Landing (`index*.html` x3 locales): inline `<svg currentColor>` per card.
- Extend the export pipeline: it currently emits SVG for the public inventory only; needs (a) a raster/PNG step, (b) coverage of the extra app drawables the doc slots need, (c) 3 supplemental section drawables for the reference.

### Open sub-items for /spec-tech (autonomous, from codebase)

- Enumerate every emoji slot; map each to a concrete `@drawable/*` (verify "we have all drawings" - flag any slot with no real app drawable back to the owner, do not invent).
- PNG rasterization of Android vector-drawable XML for the web (rendering path / tooling).
- Cross-locale parity gate for the doc/site icons (model: `assert-howto-settings-paths.ps1`).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0815 (parent - icon inventory/legend/gate), S0885, S0886, S0887 (sibling settings-icon tasks).
- **UI/asset scope:** replace all decorative emoji in docs+site with the app's own drawables; markdown surfaces use PNG `<img>`, landing uses inline `<svg currentColor>`; 3 supplemental section drawables for the settings reference. (Owner decisions 2026-07-03, above.)

### 3.4 Feasibility (2026-07-03, from codebase)

App ships 382 drawables incl. profile/widget icons that map cleanly to the doc scenarios - `ic_profile_photo_frame`, `ic_profile_car_head_unit`, `ic_profile_media_player`, `ic_profile_audio_player`, `ic_profile_vr_headset`, `ic_sort`/`ic_sort_random`, `ic_widget_resource_launch`, `ic_calculator`, `ic_book`, `ic_camera_capture`, `ic_resource_smb`. "We have all drawings here" holds for the howto scenario picker; DOCS_MAP category headers (map/rocket/scales) remain the one set with no direct app drawable - flag per-slot during /spec-tech.

---

## Implementation State (2026-07-03)

**Done (pipeline foundation):**
- Tooling: `cairosvg` 2.9.0 installed into `.venv` - SVG->PNG rasterization available.
- Refactor: `Convert-VectorToSvg`/`ConvertTo-SvgPaint` extracted to `scripts/docs/lib/vectordrawable-svg.ps1`; `export-icon-svgs.ps1` dot-sources it. SVG output byte-identical (S0815 drift gate PASS, 0 pruned) - the doc-icon PNG exporter reuses the same conversion path.
- Convertibility proven: all 20 candidate feature drawables (incl. every `ic_profile_*`) are simple (no group/gradient) -> convert cleanly.

**Scope finding (blocks the content phase):** the emoji slots split into two classes.
- **Feature slots** (app drawable exists): howto scenarios + ~21 landing feature cards + settings-reference sections. Fully coverable by "use our drawings".
- **Meta/nav/legal slots** (NO app drawable): ~16 landing cards + DOCS_MAP headers - FAQ, Privacy Policy, Terms, GitHub, Roadmap, Architecture, User Manual, Technical Spec, Terminology, What's New, Quick Start, Troubleshooting, APK Editions, Documentation Map. These are documentation categories, not app features; the app has no icon for them. "No emoji, use our drawings" cannot cover them without either non-app icons or keeping emoji there.

**Remaining (pending the meta-slot decision):** slot->drawable map (all surfaces), `export-doc-icon-pngs.ps1` + generated PNGs, rewrite `howto/index*.md` + `DOCS_MAP.md` + `render-settings-reference.ps1`, inline `<svg currentColor>` in `index*.html` (37 cards x3 locales), parity gate.

**Meta-slot decision (owner 2026-07-03):** "Nearest app drawable for all" - every emoji slot maps to an existing app drawable, even if a stretch; no new SVGs, no deferral.

**Meta-slot decision addendum (owner 2026-07-03, follow-up session):** reversed for the landing surface only. The 16 meta/nav/legal landing cards - What's New, Quick Start Guide, FAQ, Troubleshooting, How-To & Scenarios, APK Editions & Mirrors, Documentation Map, Settings Reference, User Manual, Technical Specification, Architecture Overview, Terminology Reference, Development Roadmap, GitHub Repository, Privacy Policy, Terms of Service - drop the icon entirely instead of taking a stretch drawable: text-only label, no emoji, no icon/image element at all. No new SVGs, no deferral, not re-litigated per-slot. All other slots (howto scenarios, ~21 landing feature cards, settings-reference sections) keep the original "nearest app drawable" resolution unchanged - see `## Last Audit` below for what those already deliver. `docs/DOCS_MAP.md` section headers are a separate, smaller set (7 headers, own titles) not named in this addendum and left untouched - at least one (VR/XR Edition -> `ic_vr_headset`) is a genuine non-stretch match, not a meta-slot in the same sense.

**Addendum delivery:** `scripts/docs/strip-landing-meta-icons.ps1` (new, one-time positional migration) removed the `card-icon` span from the 16 target cards in `index.html`/`index-ru.html`/`index-uk.html` (37 -> 21 spans/file, verified by exact SVG-path alignment across all 3 locales before stripping). `docs/icons/doc-icon-map.json` `landing[]` trimmed 37 -> 21 entries (feature cards only); `_comment` records the addendum. Re-ran `export-doc-icon-pngs.ps1` - pruned 3 now-orphaned drawables (`ic_cloud_download`, `ic_storage`, `ic_schedule`, 6 files: distinct drawables 27 -> 24). Re-ran `apply-doc-icons.ps1` - confirms 21/21 cards inlined per locale, markdown surfaces unchanged (0/N replaced, already icon-ified from the base pass). `assert-doc-icons-sync.ps1 -Gate` PASS (24 drawables in sync); `assert-icon-inventory-sync.ps1` (S0815) PASS, untouched (58 svgs). `docs/howto/index*.md`, `docs/DOCS_MAP.md`, `docs/SETTINGS_REFERENCE*.md` not touched by this addendum - out of its scope per the owner's exact wording and title cross-reference.

---

## Last Audit

**Date:** 2026-07-03
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 10 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

Addendum audit (meta-slot text-only reversal) layered on the prior Verified base pass - the full original delivery (37-card asset pipeline, trilingual landing/markdown/settings-reference rewrite, S0907 scope-boundary parking) is unchanged and stays recorded in `## Implementation State` above rather than repeated here.

Validation:
- Landing `card-icon` count: `index.html`/`index-ru.html`/`index-uk.html` each 21 (was 37) - matches trimmed `doc-icon-map.json` `landing[]`.
- All 16 addendum titles (What's New .. Terms of Service) confirmed absent from `landing[]` - 0/16 residual.
- `assert-doc-icons-sync.ps1 -Gate` - expected: PASS | actual: PASS (24 drawables in sync).
- `assert-icon-inventory-sync.ps1` (S0815) - expected: PASS (untouched) | actual: PASS (58 svg).
- `export-doc-icon-pngs.ps1` re-run - expected: prune 3 orphaned drawables | actual: pruned `ic_cloud_download`, `ic_storage`, `ic_schedule` (27 -> 24 distinct, 6 files).
- `apply-doc-icons.ps1` re-run - expected: 21/21 per locale | actual: 21/21; markdown 0/N replaced (already icon-ified, correct no-op).
- `docs/howto/index*.md`, `docs/DOCS_MAP.md`, `docs/SETTINGS_REFERENCE*.md` - expected: untouched (out of addendum scope) | actual: untouched, confirmed by title cross-reference (DOCS_MAP's VR/XR header keeps a genuine non-stretch `ic_vr_headset` match).
- Debug-tag invariant - expected: 0 `Timber.d("S0889:` in `.kt` | actual: 0 (docs-only ticket, none ever existed).
- Dev log entry - expected: present | actual: present (`dev/CHANGELOG.md`).
- Owner decision + delivery recorded in spec - expected: dated/attributed | actual: `## Owner decisions` addendum + `## Implementation State` delivery record.
- EXEMPT: FEATURES trilingual - display-treatment refinement of the already-recorded S0889 capability, no new user-visible capability to add.

### Manual / on-device

- [ ] Render `index.html` (+RU/UK) in a browser and eyeball the 16 text-only cards - verified structurally (grep + `styles.css` `.card-title-row` flex rule: a lone `<h3>` child leaves no reserved icon gap) but not rendered in an actual browser this session.

---

## Related

- S0815 (icon inventory + legend + drift gate - iteration 1, Verified) - this is its documented iteration 2.
