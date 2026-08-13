# Phase 02 - RU and UK noLegal Pages

**Strategic spec:** [`../S0310_nolegal-edition-landing-page.md`](../S0310_nolegal-edition-landing-page.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** 2026-05-30
**Completed:** 2026-05-30

---

## Objective

Mirror `nolegal.html` into `nolegal-ru.html` and `nolegal-uk.html` with full content parity, translated copy, localized outbound links, and a hreflang group limited to the noLegal triplet.

---

## Prerequisites

- [ ] Phase 01 ✅ Done - `nolegal.html` is the canonical structure.
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `nolegal-ru.html` | New | ≤ 600 |
| `nolegal-uk.html` | New | ≤ 600 |

> Author style for Russian copy: `..` not `...`; always use `ё`/`Ё`. Ukrainian copy follows standard orthography.

---

## Steps

### Step 02.1 - Create `nolegal-ru.html` (Russian)

**Files:** `nolegal-ru.html`
**Depends on:** - start of phase

**Prompt for developer:**

> Copy `nolegal.html` structure verbatim into `nolegal-ru.html` with `<html lang="ru">`. Translate all copy to Russian (topology block, six feature entries with details, "почему не в магазине" notes). Set `.lang-switcher` so RU is `active`. Set canonical to the RU URL; keep the same noLegal-only hreflang group. Localize outbound links: main home → `index-ru.html`, VR materials → `docs/VR_EDITION_RU.html` / `docs/VR_SIDELOAD_RU.html` / `docs/VR_CONTROLS_RU.html`, downloads → `docs/DOWNLOADS_RU.html`, FAQ → `docs/FAQ_RU.html`, GitHub unchanged.

**Verification:**

- `Glob` - `nolegal-ru.html` exists.
- `Grep` - `<html lang="ru">` present; `lang-btn active` on the RU button.
- `Grep` - `index-ru.html`, `docs/DOWNLOADS_RU.html`, `docs/FAQ_RU.html` present.
- `Grep` - zero `hreflang.*index` matches.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 4/4 PASS. Files: nolegal-ru.html (New, full RU mirror). html lang=ru; RU lang-btn active=1; index-ru/DOWNLOADS_RU/FAQ_RU present; hreflang.*index=0.

---

### Step 02.2 - Create `nolegal-uk.html` (Ukrainian)

**Files:** `nolegal-uk.html`
**Depends on:** Step 02.1

**Prompt for developer:**

> Copy the structure into `nolegal-uk.html` with `<html lang="uk">`. Translate all copy to Ukrainian. Set `.lang-switcher` so UK is `active`. Set canonical to the UK URL; keep the noLegal-only hreflang group. Localize outbound links: main home → `index-uk.html`, VR materials → `docs/VR_EDITION_UK.html` / `docs/VR_SIDELOAD_UK.html` / `docs/VR_CONTROLS_UK.html`, downloads → `docs/DOWNLOADS_UK.html`, FAQ → `docs/FAQ_UK.html`, GitHub unchanged.

**Verification:**

- `Glob` - `nolegal-uk.html` exists.
- `Grep` - `<html lang="uk">` present; `lang-btn active` on the UK button.
- `Grep` - `index-uk.html`, `docs/DOWNLOADS_UK.html`, `docs/FAQ_UK.html` present.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 3/3 PASS. Files: nolegal-uk.html (New, full UK mirror). html lang=uk; UK lang-btn active=1; index-uk/DOWNLOADS_UK/FAQ_UK present.

---

### Step 02.3 - Cross-link the language switcher across all three pages

**Files:** `nolegal.html`, `nolegal-ru.html`, `nolegal-uk.html`
**Depends on:** Step 02.2

**Prompt for developer:**

> Confirm every one of the three pages' `.lang-switcher` links to the other two noLegal pages (not to index). Each page marks exactly one button `active`.

**Verification:**

- `Grep` - in each of the three files, both sibling `href="nolegal-*.html"` (or `nolegal.html`) targets are present.
- `Grep` - each file has exactly one `lang-btn active`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification PASS. All three pages link to nolegal/-ru/-uk (1 each) and have exactly one lang-btn active.

---

### Step 02.4 - Content-parity check across languages

**Files:** `nolegal.html`, `nolegal-ru.html`, `nolegal-uk.html`
**Depends on:** Step 02.3

**Prompt for developer:**

> Verify the three pages carry the same sections and the same six feature entries (no language has fewer features or a missing topology block).

**Verification:**

- `Grep` - all six feature anchors/titles present in each of the three files.
- `Grep` - topology statement present in each of the three files.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification PASS. nl-num feature blocks=6 in each of the three files; topology statement present in each.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` is `[x] done`.
- [ ] All three pages render and switch between each other.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for `nolegal-ru.html` and `nolegal-uk.html`.

---

## Handoff Notes to Next Phase

Three pages exist and cross-link. Phase 03 registers them in `sitemap.xml` and verifies the index-isolation invariant.

---

## Rollback Plan

Delete `nolegal-ru.html` and `nolegal-uk.html` - no data, no app surface affected.
