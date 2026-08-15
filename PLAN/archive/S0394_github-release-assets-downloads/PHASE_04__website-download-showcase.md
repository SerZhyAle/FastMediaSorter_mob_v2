# Phase 04 - Website download showcase

**Strategic spec:** [`../S0394_github-release-assets-downloads.md`](../S0394_github-release-assets-downloads.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-06-10
**Completed:** 2026-06-10

---

## Objective

Render direct download buttons on the website from the latest GitHub Release: public flavors on `index*.html`, noLegal only on `nolegal*.html`, with a live version label and a static fallback.

---

## Prerequisites

- [ ] Phase 03 ✅ Done (assets named `FastMediaSorter-<flavor>-<version>.apk` exist on the release).
- [ ] Research 03 read (`research/03__website-release-rendering.md`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `index.html` | Modified | ≤ 1700 |
| `index-ru.html` | Modified | ≤ 1700 |
| `index-uk.html` | Modified | ≤ 1700 |
| `nolegal.html` | Modified | ≤ 600 |
| `nolegal-ru.html` | Modified | ≤ 600 |
| `nolegal-uk.html` | Modified | ≤ 600 |
| `styles.css` | Modified | ≤ 400 added |

> Website files, not Android `res/layout` - landscape parity rule N/A. No Kotlin/flavor source set - flavor discipline N/A. Reuse existing `styles.css` CSS variables; no hardcoded download-button colors.

---

## Steps

### Step 04.1 - Public-flavor download buttons on index*.html

**Files:** `index.html`, `index-ru.html`, `index-uk.html`
**Depends on:** - start of phase

**Prompt for developer:**

> In the "Download APK Builds" area of each `index*.html`, add a button container plus a self-contained JS renderer that fetches `https://api.github.com/repos/SerZhyAle/FastMediaSorter_mob_v2/releases/latest`, reads `tag_name` + `assets[]`, and renders one download button per PUBLIC flavor {standard, vr, lite, photos, legacy, wear} by matching `asset.name` against `FastMediaSorter-<flavor>-`. Show the live version (`tag_name`) as a label. EXCLUDE noLegal from this mapping. On fetch failure, render a single static fallback link to `https://github.com/SerZhyAle/FastMediaSorter_mob_v2/releases/latest`. Links must be keyboard-focusable. Keep EN/RU/UK button labels and surrounding copy in parity (translate the visible strings per page).

**Verification:**

- `Grep` - `releases/latest` present in all three `index*.html`.
- `Grep` - `noLegal` / `nolegal` NOT present in the download renderer block of any `index*.html`.
- `Grep` - the static fallback `releases/latest` page link present in each file.

**Status:** `[x]` done

**Step Log:**

- 2026-06-10 - Verification PASS. Added a visible "Download APK Builds" section + shared JS renderer (fetches `releases/latest`, maps public flavors by `FastMediaSorter-<flavor>-` asset name, live version label, static fallback) to index.html / index-ru.html / index-uk.html; retitled the old guide card to "APK Editions & Mirrors". `releases/latest`=2 each; `nolegal`=0 in all three (public set excludes noLegal). Existing Download card retitled to avoid a duplicate heading.

---

### Step 04.2 - noLegal download button on nolegal*.html

**Files:** `nolegal.html`, `nolegal-ru.html`, `nolegal-uk.html`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add the same renderer to each `nolegal*.html`, but map ONLY the noLegal asset (`FastMediaSorter-noLegal-`) to a download button with the live version, plus the static fallback link. EN/RU/UK copy in parity. Do not add the public-flavor buttons here.

**Verification:**

- `Grep` - `FastMediaSorter-noLegal-` (or noLegal asset match) present in all three `nolegal*.html`.
- `Grep` - `releases/latest` present in all three `nolegal*.html`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-10 - Verification PASS. Added a "Sideload Download" section + noLegal-only renderer (`data-flavors="noLegal"`, `apk-btn-nolegal`) to all three `nolegal*.html`; `releases/latest`=2 each. Reworded the intro paragraph that previously claimed "there is no download link here" to reflect the now-available signed sideload APK (still not advertised from the main site).

---

### Step 04.3 - Shared button styles in styles.css

**Files:** `styles.css`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add CSS classes for the download buttons reusing existing theme variables (colors via existing `var(--..)` tokens, never hardcoded hex). Cover hover/focus-visible states for keyboard navigation. Keep the buttons visually consistent with existing cards.

**Verification:**

- `Grep` - new download-button class present in `styles.css`.
- `Grep` - no new `#` hex literal introduced in the added block (uses `var(--..)`).

**Status:** `[x]` done

**Step Log:**

- 2026-06-10 - Verification PASS. Appended `.apk-downloads` / `.apk-btn` / `.apk-btn-nolegal` / `.apk-fallback` styles to `styles.css` with hover + `focus-visible` states; 0 hex literals in the added block (all `var(--..)` tokens, incl. `--color-nolegal` / `--color-nolegal-glow`).

---

## Phase Done Criteria

- [x] Every `Step 04.*` is `[x] done`.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] noLegal appears in `nolegal*.html` download blocks (`data-flavors="noLegal"`) and is absent from `index*.html` download blocks (`nolegal`=0 confirmed).
- [x] Dev log entry added for every touched file.

---

## Handoff Notes to Next Phase

The site now self-updates from the latest release; no per-release HTML edits. Phase 05 points the Downloads docs at the same GitHub Release source.

---

## Rollback Plan

Revert the phase commit - the "Download APK Builds" card returns to the prior doc link; no asset or release affected.
