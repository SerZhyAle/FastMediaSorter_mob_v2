# Phase 10 - Extensions Entry Relocation & Dual Placement

**Strategic spec:** [`../S0386_ondemand-ocr-translation-delivery.md`](../S0386_ondemand-ocr-translation-delivery.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done (device-verified on emulator API 33)
**Depends on:** Phase 08
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-06-10
**Completed:** 2026-06-10

---

## Objective

Move the primary "Downloadable Extensions" entry out of the OCR/translation settings group onto the General tab (it aggregates downloads for the whole app, not just OCR), keep a contextual shortcut in the Translation/OCR group, and constrain the entry width in both orientations (strategic §5.1 Pillar G, §3.3, owner request 2026-06-10).

---

## Prerequisites

- [ ] Phase 08 ✅ Done (the Extensions screen + the current entry exist).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_general.xml` | Modified | - |
| `app_v2/src/main/res/layout-land/fragment_settings_general.xml` | Modified (if present) | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt` | Modified | ≤ +60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsSectionsHelper.kt` | Modified | ≤ +60 |
| `app_v2/src/main/res/layout/fragment_settings_other.xml` | Modified | - |
| `app_v2/src/main/res/layout-land/fragment_settings_other.xml` | Modified (if present) | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OtherMediaSettingsFragment.kt` | Modified | ≤ +40 |

---

## Steps

### Step 10.1 - Primary entry on the General tab

**Files:** `fragment_settings_general.xml` (+ `layout-land` counterpart), `GeneralSettingsFragment.kt`, `GeneralSettingsSectionsHelper.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a "Downloadable Extensions" entry to the General settings tab, positioned after the last collapsible section and before the standalone utility buttons (reset/default). It must NOT span the full width - constrain it (e.g. `wrap_content` / `layout_gravity="start"` / a max width), and keep the same constraint in `res/layout-land/` (Rule 11). Tapping it navigates to `ExtensionsManagerFragment` via the activity FragmentManager (reuse the navigation already wired in Phase 08; do not use the ViewPager child FM). Keyboard/D-pad/mouse focusable (Rule 16); inside systemBars safe bounds (Rule 17).

**Verification:**

- `Grep` - an extensions-manager entry id is present in `fragment_settings_general.xml` and its `layout-land` counterpart.
- `Grep` - the General fragment opens `ExtensionsManagerFragment` (same nav call as Phase 08).
- Build `standardDebug`; on-device the entry shows on General after the groups and is not full-width in portrait or landscape.

**Status:** `[x]` done

---

### Step 10.2 - Contextual shortcut in the Translation/OCR group

**Files:** `fragment_settings_other.xml` (+ `layout-land`), `OtherMediaSettingsFragment.kt`
**Depends on:** Step 10.1

**Prompt for developer:**

> Keep a secondary entry in the "Translation, OCR and Google Lens" group, re-labelled as a contextual shortcut (offer to go download OCR/translation packs) rather than the generic manager title. It opens the same `ExtensionsManagerFragment` (optionally deep-linking to the OCR/Translation sections from Phase 11). Same width constraint + orientation parity + focus rules as 10.1.

**Verification:**

- `Grep` - the Translation/OCR group still has an entry that opens `ExtensionsManagerFragment`.
- `check_strings_localized.ps1` passes for any new/renamed string key (EN/RU/UK parity).

**Status:** `[x]` done

---

### Step 10.3 - Strings + docs

**Files:** `res/values*/strings.xml` (via `set-android-string.ps1`), `docs/FEATURES*.md`
**Depends on:** Step 10.1, 10.2

**Prompt for developer:**

> Add/rename the entry + shortcut strings across EN/RU/UK (use `scripts/utils/set-android-string.ps1 -Action add`). Adjust the FEATURES "Downloadable Extensions manager" wording only if the placement description changed; keep it a one-line clarification, no duplication.

**Verification:**

- `check_strings_localized.ps1 -KeyPrefix <prefix>` exit 0.
- `Grep` - FEATURES EN/RU/UK consistent.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 10.*` is `[x] done`.
- [ ] `standardDebug` builds; entry on General (not full-width, both orientations) + contextual shortcut in Translation/OCR both open the screen.
- [ ] `assert-neuroslop.ps1 -Gate` PASS (no hardcoded hex in the new layout rows).
- [ ] Dev log entry per touched file; catalog re-synced.

---

## Rollback Plan

Revert the phase commit - the entry returns to its Phase 08 location. No data migration.
