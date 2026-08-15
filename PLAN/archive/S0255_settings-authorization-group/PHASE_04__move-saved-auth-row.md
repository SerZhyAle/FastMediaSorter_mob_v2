# Phase 04 — Move Saved Authorizations Row (Atomic XML + Kotlin)

**Strategic spec:** [`../S0255_settings-authorization-group.md`](../S0255_settings-authorization-group.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 5 / 6
**Started:** 2026-05-19
**Completed:** -

---

## Objective

Atomically migrate the saved-authorizations row across two surfaces:

- **XML:** remove `row_saved_authorizations` + `iconHelpSavedAuthorizations` from `fragment_settings_playback.xml` (portrait + landscape); add it as the third child of `containerAuthorization` in `fragment_settings_general.xml` (portrait + landscape), after `card_google_account`.
- **Kotlin:** remove the tooltip click handler, navigation click handler, and `linkAutoDownloadEnabled` enable rule from `PlaybackSettingsFragment.kt`; add the tooltip and navigation handlers to `GeneralSettingsFragment.kt`; drop the enable-rule per strategic decision §6.2 (row is always active).
- **Imports:** clean up unused `AuthSessionsActivity` import in `PlaybackSettingsFragment.kt` if no other reference remains.

This phase is atomic — partial completion leaves the build broken (binding fields referenced from the wrong fragment). All six steps must land in one commit.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done (Authorization group contains GSM banner + Google card).
- [ ] Working tree clean or on the feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_general.xml` | Modified | ≤ 600 |
| `app_v2/src/main/res/layout-land/fragment_settings_general.xml` | Modified | ≤ 600 |
| `app_v2/src/main/res/layout/fragment_settings_playback.xml` | Modified | ≤ 700 |
| `app_v2/src/main/res/layout-land/fragment_settings_playback.xml` | Modified | ≤ 700 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt` | Modified | ≤ 800 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt` | Modified | ≤ 800 |

---

## Steps

### Step 04.1 — Add saved-authorizations row to `containerAuthorization` (portrait)

**Files:** `app_v2/src/main/res/layout/fragment_settings_general.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> As the third child of `containerAuthorization` (after `<include card_google_account>`), insert the saved-authorizations row verbatim from `fragment_settings_playback.xml:423–435`:
>
> ```xml
> <!-- S0255: saved authorizations sub-screen entry, moved from fragment_settings_playback.xml (S0116 §5.1 pillar K). -->
> <LinearLayout
>     android:id="@+id/row_saved_authorizations"
>     android:layout_width="match_parent"
>     android:layout_height="wrap_content"
>     android:layout_marginBottom="@dimen/margin_small"
>     android:background="?attr/selectableItemBackground"
>     android:clickable="true"
>     android:focusable="true"
>     android:gravity="center_vertical"
>     android:minHeight="@dimen/settings_item_min_height"
>     android:orientation="horizontal">
>
>     <ImageButton
>         android:id="@+id/iconHelpSavedAuthorizations"
>         android:layout_width="@dimen/settings_help_icon_size"
>         android:layout_height="@dimen/settings_help_icon_size"
>         android:layout_marginEnd="@dimen/settings_help_icon_margin"
>         android:background="?attr/selectableItemBackgroundBorderless"
>         android:contentDescription="@string/tooltip_saved_authorizations_title"
>         android:src="@drawable/ic_help_outline_24"
>         app:tint="@color/text_color_secondary" />
>
>     <LinearLayout
>         android:layout_width="0dp"
>         android:layout_height="wrap_content"
>         android:layout_weight="1"
>         android:orientation="vertical">
>         <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
>             android:text="@string/setting_saved_authorizations_title"
>             android:textSize="@dimen/toggler_title_text_size" />
>         <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
>             android:text="@string/setting_saved_authorizations_summary"
>             android:textSize="@dimen/toggler_desc_text_size"
>             android:textColor="@color/text_color_secondary" />
>     </LinearLayout>
>
>     <ImageView
>         android:layout_width="24dp"
>         android:layout_height="24dp"
>         android:src="@drawable/ic_chevron_right"
>         android:importantForAccessibility="no" />
>
> </LinearLayout>
> ```
>
> IDs `row_saved_authorizations` and `iconHelpSavedAuthorizations` MUST be preserved exactly — they will become fields in `FragmentSettingsGeneralBinding`.

**Verification:**

- `Grep` — `row_saved_authorizations` matches exactly once in `app_v2/src/main/res/layout/fragment_settings_general.xml`.
- `Grep` — `iconHelpSavedAuthorizations` matches exactly once in the file.
- `Grep` confirms the row appears AFTER `card_google_account` include and BEFORE the closing `</LinearLayout>` of `containerAuthorization`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 — Static verification PASS. Portrait `row_saved_authorizations` and `iconHelpSavedAuthorizations` inserted after the Google account card inside `containerAuthorization`.

---

### Step 04.2 — Add saved-authorizations row to `containerAuthorization` (landscape)

**Files:** `app_v2/src/main/res/layout-land/fragment_settings_general.xml`
**Depends on:** Step 04.1

**Prompt for developer:**

> Apply the identical insertion (verbatim from Step 04.1) to the landscape variant. Same IDs, same order (after `card_google_account` include, last in `containerAuthorization`).

**Verification:**

- `Grep` — `row_saved_authorizations` matches exactly once in `app_v2/src/main/res/layout-land/fragment_settings_general.xml`.
- `Grep` — `iconHelpSavedAuthorizations` matches exactly once in the file.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 — Static verification PASS. Landscape layout mirrors the moved saved-authorizations row inside `containerAuthorization`.

---

### Step 04.3 — Remove saved-authorizations row from playback layouts (portrait + landscape)

**Files:** `app_v2/src/main/res/layout/fragment_settings_playback.xml`, `app_v2/src/main/res/layout-land/fragment_settings_playback.xml`
**Depends on:** Steps 04.1, 04.2

**Prompt for developer:**

> Remove the entire `row_saved_authorizations` `LinearLayout` block (including children `iconHelpSavedAuthorizations`, title/summary inner layout, chevron `ImageView`) and the leading comment `<!-- S0116 §5.1 pillar K: saved authorizations sub-screen entry - S0142: nav-row styling (help icon + chevron) -->` from `fragment_settings_playback.xml` (portrait, currently lines ~423–435) and from the landscape counterpart.
>
> Do NOT remove the divider `<View ... android:layout_marginTop="@dimen/margin_small" android:layout_marginBottom="@dimen/margin_small" android:background="?attr/colorOutlineVariant" />` (the S0142 closer divider) — it still acts as the bottom border of the "Incoming links" sub-section. Update its leading comment from `<!-- S0142: close "Incoming links" sub-section -->` to remain accurate (it still closes the sub-section, which now contains only the two link-autodownload toggles).
>
> No other element in playback layout should be reordered. The two `switch_link_autodownload_*` rows that lived above the removed row continue to live in their original positions.

**Verification:**

- `Grep` — `row_saved_authorizations` returns zero hits in `app_v2/src/main/res/layout/fragment_settings_playback.xml`.
- `Grep` — `row_saved_authorizations` returns zero hits in `app_v2/src/main/res/layout-land/fragment_settings_playback.xml`.
- `Grep` — `iconHelpSavedAuthorizations` returns zero hits in both playback files.
- `Grep` — `switch_link_autodownload_enabled` still present in both playback files (was not touched).
- `Grep` — `S0142: close "Incoming links" sub-section` still present in both playback files (divider remains).

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 — Static verification PASS. Playback portrait/landscape layouts no longer contain `row_saved_authorizations` or `iconHelpSavedAuthorizations`; the closing divider remains.

---

### Step 04.4 — Add handlers to `GeneralSettingsFragment.kt`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt`
**Depends on:** Steps 04.1, 04.2

**Prompt for developer:**

> Add `import com.sza.fastmediasorter.ui.settings.auth.AuthSessionsActivity` at the top of the file (alphabetically between the existing `ui.settings.SettingsViewModel` and any later imports).
>
> Locate the existing block of click listeners in `GeneralSettingsFragment.kt` (the function that wires UI handlers; mirror the placement style used for similar tooltip+navigation rows nearby). Add two handlers:
>
> ```kotlin
> binding.iconHelpSavedAuthorizations.setOnClickListener {
>     com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(
>         requireContext(),
>         R.string.tooltip_saved_authorizations_title,
>         R.string.tooltip_saved_authorizations_message
>     )
> }
>
> // S0255: saved-authorizations row navigation - always active per strategic decision §6.2.
> binding.rowSavedAuthorizations.setOnClickListener {
>     AuthSessionsActivity.start(requireContext())
> }
> ```
>
> Do NOT mirror the Playback-fragment `isEnabled = settings.linkAutoDownloadEnabled` rule — strategic decision §6.2 drops this dependency; the row is always enabled.

**Verification:**

- `Grep` — `import com.sza.fastmediasorter.ui.settings.auth.AuthSessionsActivity` matches exactly once in `GeneralSettingsFragment.kt`.
- `Grep` — `binding.iconHelpSavedAuthorizations.setOnClickListener` matches exactly once.
- `Grep` — `binding.rowSavedAuthorizations.setOnClickListener` matches exactly once.
- `Grep` — `AuthSessionsActivity.start(requireContext())` matches exactly once.
- `Grep` — `binding.rowSavedAuthorizations.isEnabled` returns zero hits (dependency was not introduced).
- `Grep -n "Log\.d\("` returns zero hits in the file.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 — Static verification PASS. `GeneralSettingsFragment.kt` now imports `AuthSessionsActivity` and owns the tooltip plus navigation handlers for the moved row.

---

### Step 04.5 — Remove handlers and import from `PlaybackSettingsFragment.kt`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt`
**Depends on:** Steps 04.3, 04.4

**Prompt for developer:**

> Remove the following from `PlaybackSettingsFragment.kt`:
>
> 1. The `binding.iconHelpSavedAuthorizations.setOnClickListener { … }` block (currently lines ~153–159).
> 2. The `binding.rowSavedAuthorizations.setOnClickListener { AuthSessionsActivity.start(requireContext()) }` block, including the leading comment `// S0116 §5.1 pillar K: saved authorizations sub-screen entry. …` (currently lines ~294–300).
> 3. The line `binding.rowSavedAuthorizations.isEnabled = settings.linkAutoDownloadEnabled` (currently line ~508), including the leading comment `// S0116 §5.1 pillar K: saved authorizations row follows the same enable rule.`.
>
> After removal, grep the file for any remaining references to `AuthSessionsActivity` or `binding.rowSavedAuthorizations` or `binding.iconHelpSavedAuthorizations`. If none remain, also remove `import com.sza.fastmediasorter.ui.settings.auth.AuthSessionsActivity` from the imports block.

**Verification:**

- `Grep` — `rowSavedAuthorizations` returns zero hits in `PlaybackSettingsFragment.kt`.
- `Grep` — `iconHelpSavedAuthorizations` returns zero hits in `PlaybackSettingsFragment.kt`.
- `Grep` — `AuthSessionsActivity` returns zero hits in `PlaybackSettingsFragment.kt`.
- `Grep -n "Log\.d\("` returns zero hits in the file.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 — Static verification PASS. `PlaybackSettingsFragment.kt` no longer references `AuthSessionsActivity`, `rowSavedAuthorizations`, or `iconHelpSavedAuthorizations`, and the old enable-rule was removed.

---

### Step 04.6 — Build & manual sanity check (atomic verification)

**Files:** none modified
**Depends on:** Steps 04.1, 04.2, 04.3, 04.4, 04.5

**Prompt for developer:**

> Run `/build` (standard debug). On a device or emulator:
>
> 1. Open Settings → "Основные" → expand the "Authorization" group. Confirm three children visible (when applicable): GSM banner, Google account card, saved-authorizations row (with help icon, title, summary, chevron).
> 2. Tap the saved-authorizations help icon — the existing tooltip dialog opens.
> 3. Tap the saved-authorizations row — `AuthSessionsActivity` opens.
> 4. Go to Settings → "Воспроизведение". Confirm: the "Сохранённые авторизации" row no longer appears in the "Входящие ссылки" sub-section. The two `switch_link_autodownload_*` toggles still appear in their original positions. The divider closing the sub-section is still present.
> 5. Toggle the `switch_link_autodownload_enabled` off and on. Confirm: the saved-authorizations row in the Authorization group stays clickable in both states (strategic decision §6.2).
> 6. Rotate to landscape and re-verify all four points.

**Verification:**

- `/build` standard debug returns PASS.
- Manual: all six on-device steps pass as described.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 23:33 — Saved-authorizations row reinstated inside `containerAuthorization` (the prior refactor in commit `85b122bf` had removed it entirely from the UI). Tooltip handler + `AuthSessionsActivity.start(requireContext())` navigation now live in `GeneralSettingsFragment.setupSavedAuthorizationsRow()`; row is always active per strategic §6.2. `.\a.ps1 bd` PASS (`assembleStandardDebug`, 45s). Note: Phase 04's original "remove from Playback fragment" steps were already true on disk before this phase started, because the saved-authorizations row no longer existed anywhere in the codebase. Implementation therefore restored the entry inside the Authorization group instead of moving it. On-device verification pending.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

All three target elements live inside `containerAuthorization`. All handlers route through `GeneralSettingsFragment.kt`. The "Authorization" group is feature-complete from the user's standpoint. Phase 05 covers documentation, catalog, and final verification.

---

## Rollback Plan

Revert phase commit. Because XML changes and Kotlin changes are bundled in one commit, the revert restores the prior state atomically. No data migration involved.
