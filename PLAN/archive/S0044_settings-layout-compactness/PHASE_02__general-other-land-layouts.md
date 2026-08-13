# Phase 02 — General And Other Land Layouts

**Strategic spec:** [../S0044_settings-layout-compactness.md](../S0044_settings-layout-compactness.md)
**Tactical index:** [INDEX.md](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-05-01
**Completed:** 2026-05-01

---

## Objective

Normalize the two existing settings landscape layouts so they consume shared settings dimensions, preserve the canonical trigger-row structure, and remove hardcoded typography or spacing drift. The invariant is that landscape `General` and `Other` no longer rely on hardcoded `dp`/`sp` values for settings rows.

## Files Touched

| File | Action | Note |
|------|--------|------|
| `app_v2/src/main/res/layout-land/fragment_settings_general.xml` | Modified | Align typography and spacing with the canonical settings row pattern. |
| `app_v2/src/main/res/layout-land/fragment_settings_other.xml` | Modified | Replace hardcoded sizing, restore helper-row consistency, and consume shared dims. |

---

## Steps

### Step 2.1 — Normalize landscape General rows

**Status:** `[x] done`
**Depends on:** none
**Blocks:** Step 2.2

**Prompt for developer:**

Update `layout-land/fragment_settings_general.xml` so the settings rows use the canonical settings text sizes and the compact landscape dimensions from Phase 01. Keep each helper icon as the rightmost child of its row, preserve existing view ids, and avoid changing fragment logic.

**Files Touched:** `app_v2/src/main/res/layout-land/fragment_settings_general.xml`

**Verification:**

```text
Glob: app_v2/src/main/res/layout-land/fragment_settings_general.xml -> 1 result
Grep: "@dimen/toggler_title_text_size" in app_v2/src/main/res/layout-land/fragment_settings_general.xml -> 1+ hits
Grep: "android:id=\"@\+id/iconHelpAllFiles\"" in app_v2/src/main/res/layout-land/fragment_settings_general.xml -> 1 hit
```

### Step 2.2 — Normalize landscape Other rows and remove hardcoded sizing

**Status:** `[x] done`
**Depends on:** Step 2.1
**Blocks:** Phase 03

**Prompt for developer:**

Update `layout-land/fragment_settings_other.xml` to replace hardcoded `dp`/`sp` values in settings rows with dimension resources, keep helper-enabled settings visually aligned with their own row, and preserve all existing ids required by the fragment code. If a helper icon is missing from a row that already has one in portrait, add the matching `ImageButton` with the same id and icon resource so landscape behavior stays functionally equivalent.

**Files Touched:** `app_v2/src/main/res/layout-land/fragment_settings_other.xml`

**Verification:**

```text
Glob: app_v2/src/main/res/layout-land/fragment_settings_other.xml -> 1 result
Grep: "android:textSize=\"14sp\"" in app_v2/src/main/res/layout-land/fragment_settings_other.xml -> 0 hits
Grep: "android:id=\"@\+id/iconHelpTranslation\"|android:id=\"@\+id/iconHelpTranslationBlocks\"" in app_v2/src/main/res/layout-land/fragment_settings_other.xml -> 1+ hits
```

---

## Phase Done Criteria

- [x] Project compiles (BUILD-REQUIRED — run `/build standard-debug`).
- [x] Landscape `General` rows use settings typography resources instead of alternate title/body sizes.
- [x] Landscape `Other` has no hardcoded `14sp` settings-row text sizes and preserves portrait helper affordances.

---

## Step Log

- 2026-05-01 — Step 2.1 done. Normalized landscape `General` switch rows to `toggler_title_text_size` / `toggler_desc_text_size` and replaced repeated card/container margins with `settings_margin_standard`. Verification PASS.
- 2026-05-01 — Step 2.2 done. Replaced hardcoded sizing in landscape `Other` and restored `iconHelpTranslation` / `iconHelpTranslationBlocks`. Verification PASS.
- 2026-05-01 — Phase done. `assembleStandardDebug` PASS after Phase 02 layout changes.
