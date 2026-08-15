# Phase 06 - Docs & Site Audit

**Strategic spec:** [`../S0543_features-inventory-docs-audit.md`](../S0543_features-inventory-docs-audit.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done (broad doc fixes applied EN/RU/UK via 3 doc-agents; owner resolved Legacy=match-code; minor residuals noted)
**Depends on:** Phase 03
**Steps done:** 5 / 5

---

## Objective

Sync the published documentation and the site pages (`index*.html` EN/RU/UK + `nolegal*.html`) with the reconciled feature surface. Settings documentation is S0440-owned: flag drift, do not rewrite.

---

## Steps

### Step 06.1 - Audit DOCS_MAP

**Prompt:**

> Check `docs/DOCS_MAP.md` for stale `Last Updated` cells, dead links (files that no longer exist or were renamed), and missing entries for current docs. Emit `temp/s0543/docs_map_audit.txt`.

**Verification:**

- Audit emitted; dead links and stale rows listed.

**Status:** `[ ]`

---

### Step 06.2 - Audit user guides vs showcase

**Prompt:**

> Compare `README*`, `QUICK_START*`, `HOW_TO*`, `FAQ*`, `MODULE_SELECTION.md` against the reconciled `FEATURES*`. List capabilities described inaccurately or missing, and references to removed capabilities. Settings-specific content is flagged for S0440, not edited here.

**Verification:**

- Drift list emitted per document.
- Settings items clearly tagged `-> S0440`.

**Status:** `[ ]`

---

### Step 06.3 - Audit site pages

**Prompt:**

> Compare `index.html`, `index-ru.html`, `index-uk.html` and `nolegal.html`, `nolegal-ru.html`, `nolegal-uk.html` against `FEATURES*`. List drift per page (missing standout features, removed features still advertised, wrong flavor framing). Treat `nolegal*` against `FEATURES_noLegal*`.

**Verification:**

- Per-page drift list emitted.
- Record `expected: 6 pages audited | actual: <n> pages, <n> drift items`.

**Status:** `[ ]`

---

### Step 06.4 - Apply non-settings fixes; hand settings to S0440

**Prompt:**

> Fix the clear, non-settings drift in docs and site (wrong/removed feature mentions, missing standout features, flavor framing). For settings-doc drift, append a consolidated note to S0440's tactical Phase 05 (docs-catalog-cleanup) inputs or its strategic §0 - do not rewrite settings docs here.

**Verification:**

- Non-settings fixes applied; `Grep` confirms corrected mentions.
- Settings drift recorded against S0440 (link/path cited).

**Status:** `[ ]`

---

### Step 06.5 - EN/RU/UK parity for edits

**Prompt:**

> Ensure every doc/site edit is mirrored across EN/RU/UK. RU/UK obey `ё`/hyphen rules.

**Verification:**

- Parity check across the three locales for each edited doc/page.

**Status:** `[ ]`

---

## Results (2026-06-19)

Audit agent swept DOCS_MAP, README/QUICK_START/HOW_TO/FAQ/MODULE_SELECTION/LIMITATIONS, and the 6 site pages.

SITE: `index*.html` read the feature grid dynamically from FEATURES.md, so the Phase 03 FEATURES fixes auto-propagate to all 3 locales. One hardcoded item remains: the `index.html` "Chromecast Playback" scenario badge says "Standard Only" (Chromecast is also lite/legacy) - hardcoded, not FEATURES-driven; fix pending.

DOC drift found (fixes PENDING - broad, trilingual; queued for next pass):

- README: stale version badges (Kotlin 1.9.0->2.2.10, Glide 4.15.1->4.16.0, Room "v6"->2.7.0); widget list says 2 widgets (actual 13); virtual-source names wrong.
- HOW_TO flavor matrix: Legacy row wrong for SMB/SFTP/FTP and Cloud (now =yes); Lite/Photos wrong for Documents/EPUB/OCR (=no); image-editing wrong for vr/noLegal (=no).
- QUICK_START: Lite/Legacy framing; touch-zone diagram inconsistent with FAQ.
- FAQ: "2 widgets" answer; slideshow background-music excludes photos (it has it).
- DOCS_MAP: stale WHATS_NEW date/version; date-format inconsistency.

SETTINGS -> S0440: no SETTINGS_REFERENCE page exists yet (nothing to audit); handed to S0440. No settings docs rewritten here.

OWNER DECISION (blocks the legacy doc claims): user docs describe Legacy as "local-only, no cloud/network", but shipped legacy has `SUPPORT_CLOUD=true` + `SUPPORT_LOCAL_NETWORK=true`. Either the docs are stale (fix to match code) OR the legacy build flags are an accidental regression (fix code, keep docs). Cannot resolve from code alone - intent needed. The lite/photos accuracy fixes are unambiguous and not gated by this.

---

## Phase Done Criteria

- [ ] Steps 06.1-06.5 are `[x]`.
- [ ] Non-settings docs/site drift fixed; settings drift handed to S0440.
- [ ] EN/RU/UK parity holds for edits.
- [ ] One dev-log entry for the docs/site reconciliation.
