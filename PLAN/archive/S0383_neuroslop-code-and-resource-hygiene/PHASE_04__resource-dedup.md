# Phase 04 - Resource Deduplication

**Strategic spec:** [`../S0383_neuroslop-code-and-resource-hygiene.md`](../S0383_neuroslop-code-and-resource-hygiene.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⏭️ Deferred (descoped at S0383 close, 2026-06-08)
**Depends on:** Phase 01
**Blocks:** Phase 06
**Steps done:** 1 / 4 (detection done; 04.2-04.4 deferred; detector holds the floor)
**Started:** 2026-06-08
**Completed:** -

> DEFERRED, not abandoned. 04.1 detection is complete (see its Step Log + the Re-scope block). The `assert-layout-hardcoded-colors` detector caps layout colors at its floor (150); no new hardcoded colors can land. 04.2-04.4 are descoped from S0383 (strategic §2 «Доставленный объём») and await owner inputs: RU/UK translations for the ~39 parity keys, a theme-attr mapping convention for colors, and a reviewed dedup shortlist (the blind-dedup premise is mostly false positives).

---

## Objective

Deduplicate semantically identical strings across the thematic `strings_*.xml` set, realign EN/RU/UK parity, and replace hardcoded layout colors flagged by `assert-layout-hardcoded-colors.ps1` with theme attributes - then ratchet the layout-color baseline DOWN.

---

## Re-scope (2026-06-08, after 04.1 detection)

Detection invalidated the original autopilot assumptions. Phase split into three owner-gated sub-tracks; each starts only when its owner input is supplied. Original Steps 04.2-04.4 below are superseded by this scoping.

- **04a - genuine string dedup.** The 426 same-value keys are mostly false positives (layout-tool pseudo-strings + intentional per-context labels from the S0306/S0339 thematic split). Drop blind dedup. Owner input: a reviewed shortlist of truly-mergeable semantic duplicates. Until then, no merges.
- **04b - locale parity.** Two parts. (i) Safe now, no translation: mark the ~7 locale-invariant format/config keys `translatable="false"` (`number_format`, `string_format`, `string_format_two_args`, `path_format`, `loading_with_progress`, `default_ftp_port`, `dropbox_app_key`). (ii) Blocked: ~9 RU + ~30 UK user-facing keys need real RU/UK copy - owner/translator input (CLAUDE.md forbids fabricating translations).
- **04c - layout colors.** 150 hardcoded hex in 26 layout files. Owner input: a theme-attr mapping convention (which `?attr/...` for text/surface; which stay as named `@color/...` scrims; which immersive overlays are intentional and kept). Then sweep with paired `layout-land` edits and ratchet the baseline.

Owner inputs required (tracked as INDEX Pre-Implementation Blockers):
- RU/UK translations for the ~39 user-facing parity keys (04b-ii).
- Theme-attr mapping convention for layout colors (04c).
- Reviewed dedup shortlist, or confirmation to drop dedup entirely (04a).

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] INDEX Pre-Implementation Blocker "execution mode for destructive cleanup" is checked.
- [ ] `themes.xml` / `attrs.xml` reviewed for the theme color attributes to point layout colors at.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings*.xml` (+ `values-ru/`, `values-uk/`) | Modified | dedup + parity |
| (set from `assert-layout-hardcoded-colors.ps1`) `app_v2/src/main/res/layout/*.xml` | Modified | color attr refs |
| paired `app_v2/src/main/res/layout-land/*.xml` | Modified | landscape parity (Strict Rule 12) |

> Scope: `src/main/res` only (resolved §6.3). Flavor overrides (`noLegal`, `vr`, `debug`) are touched only on key collision, with manual EN/RU/UK grep parity - `check_strings_localized.ps1` does not cover flavor source sets.

---

## Steps

### Step 04.1 - Detect duplicate strings and parity gaps

**Files:** (read-only) `app_v2/src/main/res/values*/strings*.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Across all thematic `strings_*.xml` (and the legacy `strings.xml`), find keys whose EN value is byte-identical to another key's EN value (semantic duplicates), and find keys missing from any of EN/RU/UK (parity gaps - strategic §11 records EN 2018 / RU 2002 / UK 1980 in the legacy file). Produce a candidate merge/fill list. Do not auto-merge.

**Verification:**

- `Grep` - candidate duplicate keys enumerated (expected: non-empty list given §11 | actual: 240 groups / 426 keys, but mostly false positives).
- Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1` - records the current parity delta (expected delta noted | actual: EN 3559 / RU 3543 / UK 3521; 16 EN keys missing in RU, 38 in UK; RU/UK have no extra keys).

**Status:** `[x]` done

**Step Log:**

- 2026-06-08 - Verification 2/2 PASS (detection complete). Findings that invalidate the autopilot path for 04.2-04.4:
  - **Dedup premise mostly false.** Of 240 same-EN-value groups (426 "redundant" keys), the large majority are NOT mergeable: layout-tool pseudo-strings (`*_boxBackgroundMode`="outline", `*_layoutManager`, `*_textAlignment`, `*_text`) and intentionally per-context labels (Cancel/Delete/Copy/Rename per screen, kept distinct by the S0306/S0339 thematic split). Blind merge would damage the string architecture; genuine semantic dups are few and need per-key owner review.
  - **Parity fill needs translation (forbidden).** 16 keys missing in RU, 38 in UK. ~7 of the RU set are locale-invariant format/config keys (`number_format`, `string_format`, `string_format_two_args`, `path_format`, `loading_with_progress`, `default_ftp_port`, `dropbox_app_key`) - fixable by `translatable="false"`, not translation. The remaining ~9 RU + ~30 UK are user-facing strings (e.g. `remove_resource`, `no_maps_app_available`, `translation_result_title`, `duplicate_*`, `permission_*`) requiring real RU/UK copy. CLAUDE.md / spec-dev Hard Stop #12 forbids fabricating translations.
  - **Color cleanup needs a convention.** 150 hardcoded hex in 26 layout files: pure white/black, semi-transparent scrims (`#80000000`, `#D0FFFFFF`), palette colors (`#A1A1AA`, `#2F2F37`, `#4CAF50`). Many sit in intentionally-dark immersive player/camera overlays. Mapping each to `?attr/` vs `@color/` vs keep is a per-site design decision needing a theme-attr inventory, plus paired `layout-land` edits.
  - **Conclusion:** 04.2-04.4 are blocked on owner inputs (RU/UK translations; theme-attr convention) and a premise correction (drop blind dedup). Recommend `/spec-update` to re-scope, or owner-provided inputs. Detection deliverable is this analysis.

---

### Step 04.2 - Merge duplicates and fill parity gaps

**Files:** `app_v2/src/main/res/values*/strings*.xml`
**Depends on:** Step 04.1

**Prompt for developer:**

> Collapse each verified duplicate to a single canonical key, repointing references in code and layouts. Fill every parity gap so EN/RU/UK keysets match. Use `scripts/utils/set-android-string.ps1` (byte-preserving): `-Action rename`/`remove` for merges, `-Action add -Key -En -Ru -Uk` for lockstep fills. New/edited Russian and Ukrainian copy obeys `..` / `ё` author style and `docs/COMMUNICATION_POLICY.md`.

**Verification:**

- `Grep` - each removed/merged key has zero remaining references in `src/main` `.kt` and `.xml`.
- Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1` - expected exit 0 (EN/RU/UK parity restored for `src/main`).
- Project compiles - run `/build`.

**Status:** `[ ]` not done

---

### Step 04.3 - Replace hardcoded layout colors

**Files:** `app_v2/src/main/res/layout/*.xml` + paired `app_v2/src/main/res/layout-land/*.xml`
**Depends on:** Step 04.1

**Prompt for developer:**

> For each layout flagged by `assert-layout-hardcoded-colors.ps1`, replace `="#hex"` with the matching `?attr/...` theme reference (or a named `@color/...` where a theme attr does not fit). Hotspots (strategic §11): camera-OCR screens, player overlays, audio touch zones, text viewer, draw toolbar. MANDATORY: when a `layout/*.xml` file has a `layout-land/` counterpart, apply the identical change there in the same step (Strict Rule 12). Do NOT touch vector drawables (`ic_*`, `ico_*`).

**Verification:**

- `Grep` - for every edited `layout/*.xml` that has a `layout-land/` twin, the twin shows the same color-attr change (expected: portrait+landscape parity | actual: diff inspection).
- Layout/manifest lint structure passes; project compiles - run `/build`.

**Status:** `[ ]` not done

---

### Step 04.4 - Ratchet the layout-color baseline down

**Files:** `scripts/quality/layout-hardcoded-colors-baseline.txt`
**Depends on:** Step 04.3

**Prompt for developer:**

> Run the detector with `-UpdateBaseline`, then `-Gate` to confirm the new floor.

**Verification:**

- Run `pwsh -NoProfile -File scripts/quality/assert-layout-hardcoded-colors.ps1 -UpdateBaseline` - expected exit 0, baseline lowered.
- Run `pwsh -NoProfile -File scripts/quality/assert-layout-hardcoded-colors.ps1 -Gate` - expected exit 0.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `check_strings_localized.ps1` exits 0; `assert-layout-hardcoded-colors.ps1 -Gate` exits 0.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for the touched files via `.\scripts\add_to_dev_log.ps1`.
- [ ] String key changes audited via `scripts/check_strings_localized.ps1` (exit 0 mandatory).

---

## Handoff Notes to Next Phase

Layout-color baseline lowered from `<old>` to `<new>`. String keyset now parity-equal across EN/RU/UK in `src/main`; list any duplicate intentionally kept (e.g. distinct contexts that happen to share copy).

---

## Rollback Plan

Revert the phase commit(s) and restore `layout-hardcoded-colors-baseline.txt`. String merges may have repointed code references - confirm a clean revert restores both the keys and their usages; no Room/data migration involved.
