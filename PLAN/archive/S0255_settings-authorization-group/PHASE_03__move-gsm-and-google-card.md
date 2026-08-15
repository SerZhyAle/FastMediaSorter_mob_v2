# Phase 03 — Move GSM Banner & Google Account Card

**Strategic spec:** [`../S0255_settings-authorization-group.md`](../S0255_settings-authorization-group.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress
**Depends on:** Phase 02
**Blocks:** Phase 04, 05
**Steps done:** 3 / 4
**Started:** 2026-05-19
**Completed:** -

---

## Objective

Move the existing `tvGmsSettingsLink` banner and the `<include layout="@layout/card_google_account" />` from their independent top-of-fragment positions into the `containerAuthorization` of the new Authorization card, in both portrait and landscape variants. Order inside the container: GSM banner first, Google account card second. Saved-authorizations row (third) lands in Phase 04.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done (`headerAuthorization` / `containerAuthorization` exist in both layouts).
- [ ] Working tree clean or on the feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_general.xml` | Modified | ≤ 600 |
| `app_v2/src/main/res/layout-land/fragment_settings_general.xml` | Modified | ≤ 600 |

---

## Steps

### Step 03.1 — Move GSM banner & Google account card (portrait)

**Files:** `app_v2/src/main/res/layout/fragment_settings_general.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Cut the entire `MaterialTextView android:id="@+id/tvGmsSettingsLink"` block (currently lines ~20–33, including the `<!-- S0097: GMS update banner ... -->` comment immediately above it). Cut the entire `<include layout="@layout/card_google_account" />` line and its preceding S0200 comment (currently lines ~35–37).
>
> Re-insert them inside `containerAuthorization` (added in Phase 02), in this exact order:
>
> 1. The `<!-- S0097: GMS update banner ... -->` comment + `tvGmsSettingsLink` `MaterialTextView` block, with its existing attributes verbatim (full width, `background="?attr/colorSurfaceVariant"`, `padding="16dp"`, `textColor="?attr/colorError"`, `visibility="gone"`, `tools:visibility="visible"`).
> 2. The S0200 comment + `<include layout="@layout/card_google_account" />` line, verbatim.
>
> Leave `containerAuthorization` with no other children for now (saved-auth row arrives in Phase 04). The old positions (above the Interface card) must no longer contain these elements; the `containerDeviceStorageInfo` row stays as the only block above the Interface card.

**Verification:**

- `Grep` — `tvGmsSettingsLink` matches exactly once in the file.
- `Grep` — `card_google_account` matches exactly once in the file.
- `Grep` confirms `tvGmsSettingsLink` appears AFTER `containerAuthorization` opening tag and BEFORE the closing of `containerAuthorization`.
- `Grep` confirms `card_google_account` appears AFTER `tvGmsSettingsLink` and BEFORE the closing of `containerAuthorization`.
- `Grep` confirms `tvGmsSettingsLink` does NOT appear between `containerDeviceStorageInfo` and `headerInterface`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 — Static verification PASS. Portrait `tvGmsSettingsLink` and `<include layout="@layout/card_google_account" />` now live inside `containerAuthorization`; old top-of-fragment copies removed.

---

### Step 03.2 — Move GSM banner & Google account card (landscape)

**Files:** `app_v2/src/main/res/layout-land/fragment_settings_general.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

> Apply the identical move to the landscape variant. Same elements (`tvGmsSettingsLink`, `<include card_google_account>`), same order (GSM first, card second), same target (`containerAuthorization` interior). Preserve all attribute values verbatim.

**Verification:**

- `Grep` — `tvGmsSettingsLink` matches exactly once in `app_v2/src/main/res/layout-land/fragment_settings_general.xml`.
- `Grep` — `card_google_account` matches exactly once in the file.
- `Grep` confirms ordering inside `containerAuthorization`: `tvGmsSettingsLink` precedes `card_google_account`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 — Static verification PASS. Landscape layout mirrors the same move and order inside `containerAuthorization`.

---

### Step 03.3 — Verify Kotlin binding paths unchanged

**Files:** none modified
**Depends on:** Steps 03.1, 03.2

**Prompt for developer:**

> Confirm `GeneralSettingsFragment.kt` references to `binding.tvGmsSettingsLink` (visibility logic, click listener) still resolve. The binding field is generated from the layout: since IDs and attributes were preserved verbatim, the field path stays `binding.tvGmsSettingsLink`. Same for the elements inside `card_google_account` (`binding.cardGoogleAccount`, `binding.tvAccountTitle`, etc.) — they continue to be auto-flattened by viewBinding because `<include>` is reused with the same layout reference.

**Verification:**

- `Grep` — `binding.tvGmsSettingsLink` matches at least 3 times in `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt` (visibility set, visibility set, click listener).
- `Grep` — `Log\.d\(` returns zero hits in `GeneralSettingsFragment.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 — Static verification PASS. `GeneralSettingsFragment.kt` still owns `binding.tvGmsSettingsLink` visibility/click logic; binding path preserved after the move.

---

### Step 03.4 — Build & manual sanity check

**Files:** none modified
**Depends on:** Steps 03.1, 03.2, 03.3

**Prompt for developer:**

> Run `/build` (standard debug). On a device or emulator, open Settings → "Основные" → expand the "Authorization" group. Confirm GSM banner appears first (only when GMS not OK), Google account card appears second. Above the Interface group only the device storage info row remains — no orphaned banner or floating Google card. Rotate to landscape and re-verify.

**Verification:**

- `/build` standard debug returns PASS.
- Manual: in portrait, expanded group shows GSM banner (when applicable) above Google account card; nothing extra appears between device-storage row and Interface card. Landscape mirrors portrait.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 23:33 — `.\a.ps1 bd` PASS (`assembleStandardDebug`, 45s). Banner + Google card now live inside `containerAuthorization`; the storage info row is the only block above the Interface card. `GeneralSettingsFragment.setupGmsBanner()` still drives banner visibility via the unchanged `binding.tvGmsSettingsLink` field. Manual portrait/landscape sanity check pending operator verification.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

`containerAuthorization` now contains GSM banner + Google account card. Phase 04 will append the third child (saved-authorizations row) and atomically migrate its handlers from `PlaybackSettingsFragment.kt` to `GeneralSettingsFragment.kt`.

---

## Rollback Plan

Revert phase commit — pure layout move, no Kotlin or schema impact. Layout IDs preserved, so prior Kotlin references would resolve against either version.
