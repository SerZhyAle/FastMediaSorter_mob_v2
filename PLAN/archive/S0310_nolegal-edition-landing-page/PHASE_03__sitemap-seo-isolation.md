# Phase 03 - Sitemap, SEO, Index Isolation

**Strategic spec:** [`../S0310_nolegal-edition-landing-page.md`](../S0310_nolegal-edition-landing-page.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-05-30
**Completed:** 2026-05-30

---

## Objective

Register the three noLegal pages in `sitemap.xml` (indexed per owner decision), confirm the hreflang group is self-contained, and assert the hard invariant that no `index*.html` links to any `nolegal*.html`.

---

## Prerequisites

- [ ] Phases 01 and 02 ✅ Done - all three pages exist.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `sitemap.xml` | Modified | ≤ 80 |

---

## Steps

### Step 03.1 - Add three noLegal URLs to `sitemap.xml`

**Files:** `sitemap.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add three `<url>` entries for `nolegal.html`, `nolegal-ru.html`, `nolegal-uk.html`, following the existing entry shape (loc, changefreq, priority, lastmod `2026-05-30`). Each entry's `xhtml:link` hreflang alternates point ONLY at the three noLegal pages (en/ru/uk + x-default → `nolegal.html`) - never at any `index*.html`. Use a lower `priority` than the index entries (e.g. 0.5).

**Verification:**

- `Grep` - `nolegal.html`, `nolegal-ru.html`, `nolegal-uk.html` each appear in `sitemap.xml`.
- `Grep` - within the noLegal `<url>` blocks, hreflang alternates reference only `nolegal*` (no `index` token in those alternates).
- expected: 3 new `<loc>` lines | actual: 3.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification PASS. expected 3 new loc | actual 3. noLegal url blocks contain 0 `index` references; priority 0.5; hreflang alternates limited to noLegal triplet.

---

### Step 03.2 - Assert index isolation (no inbound links)

**Files:** `index.html`, `index-ru.html`, `index-uk.html`
**Depends on:** Step 03.1

**Prompt for developer:**

> Verify none of the three `index*.html` files contains any link or hreflang reference to a `nolegal*` page. This is the core requirement - the page must exist publicly but be unreachable by navigation from the main site.

**Verification:**

- `Grep -n "nolegal"` across `index.html`, `index-ru.html`, `index-uk.html` - expected: 0 hits | actual: 0. Any hit is a hard failure.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification PASS. expected 0 | actual 0 in all three index files. Index isolation invariant holds.

---

### Step 03.3 - Assert outbound links resolve

**Files:** `nolegal.html`, `nolegal-ru.html`, `nolegal-uk.html`
**Depends on:** Step 03.2

**Prompt for developer:**

> Verify each outbound link target referenced by the noLegal pages exists on disk (the `.md` source for each `docs/*.html` target, plus `index*.html`). GitHub repo link is external and exempt.

**Verification:**

- `Glob` - for each `docs/<NAME>.html` target, the corresponding `docs/<NAME>.md` source exists (e.g. `docs/VR_EDITION.md`, `docs/DOWNLOADS_EN.md`, `docs/FAQ.md` and the `_RU`/`_UK` variants).
- `Glob` - `index.html`, `index-ru.html`, `index-uk.html` exist.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification PASS. All 18 outbound targets exist on disk (VR_EDITION/SIDELOAD/CONTROLS ×3 langs, DOWNLOADS ×3, FAQ ×3, index ×3). missing=0.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` is `[x] done`.
- [ ] `sitemap.xml` is well-formed (no unclosed tags).
- [ ] Zero `nolegal` references in any `index*.html`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for `sitemap.xml`.

---

## Handoff Notes to Next Phase

Pages registered and isolated. Phase 04 records dev log closure and notes the publish-to-`main` git step.

---

## Rollback Plan

Revert the `sitemap.xml` entries - no data, no app surface affected.
