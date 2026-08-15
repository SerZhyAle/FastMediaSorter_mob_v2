# S0122 — Settings IA Wave 2: Cross-Tab Relocations

<!-- auto-approved by /spec-all — 2026-05-08 -->

**Parent:** S0119  
**Depends on:** S0121 (Verified)  
**Status:** Approved  
**Priority:** 60

## Goal

Wave 2 of the Settings Information Architecture migration defined in S0119.
Executes migration items M1 and M2 from the S0119 migration map:

- M1: move `switchAllowDelete` from PlaybackSettingsFragment (File Operations section) to OperationsSettingsFragment (Safety & Confirmation section).
- M2: remove the duplicate `switchConfirmDelete` that exists in PlaybackSettingsFragment alongside the canonical copy in OperationsSettingsFragment.

After this spec, PlaybackSettingsFragment no longer contains any safety/confirmation switches, and `SettingsSearchRegistry` no longer points `playback.allow_delete` to the PLAYBACK tab.

**Key architectural fact discovered during research:** `OperationsSettingsFragment` uses `fragment_settings_destinations.xml` as its layout (binding class `FragmentSettingsDestinationsBinding`). The enum value for the Operations/Destinations tab is `SettingsSearchDestination.DESTINATIONS` (index 3). There is no `fragment_settings_operations.xml` — the destinations layout IS the operations layout. No landscape counterpart exists for `fragment_settings_destinations.xml`.

## Phases

### Phase 01 — Remove switches from PlaybackSettingsFragment

**M1 — Remove `switchAllowDelete`:**
- `fragment_settings_playback.xml`: remove `<LinearLayout android:id="@+id/containerDeleteConfirm">` and its entire subtree (contains both `layoutAllowDelete`/`switchAllowDelete` and `layoutConfirmDelete`/`switchConfirmDelete`).
- `res/layout-land/fragment_settings_playback.xml`: identical removal.
- `PlaybackSettingsFragment.kt`: remove `binding.switchAllowDelete.setOnCheckedChangeListener { ... }` block.
- `PlaybackSettingsFragment.kt` `observeData()`: remove `if (binding.switchAllowDelete.isChecked != settings.allowDelete) { binding.switchAllowDelete.isChecked = settings.allowDelete }`.

**M2 — Remove `switchConfirmDelete` (playback copy):**
- Both changes are already covered by the `containerDeleteConfirm` removal above — `switchConfirmDelete` in playback lives in the same `containerDeleteConfirm` LinearLayout.
- `PlaybackSettingsFragment.kt`: remove `binding.switchConfirmDelete.setOnCheckedChangeListener { ... }` block.
- `PlaybackSettingsFragment.kt` `observeData()`: remove `if (binding.switchConfirmDelete.isChecked != settings.confirmDelete) { binding.switchConfirmDelete.isChecked = settings.confirmDelete }`.

**Verification:** `fragment_settings_playback.xml` contains no `switchAllowDelete`, no `switchConfirmDelete`, no `containerDeleteConfirm`. `PlaybackSettingsFragment.kt` contains no references to `switchAllowDelete` or `switchConfirmDelete`.

### Phase 02 — Add `switchAllowDelete` to OperationsSettingsFragment

- `fragment_settings_destinations.xml` Safety section: add a new row for `switchAllowDelete` after the `layoutUseTrash` row (i.e., after `</LinearLayout>` closing `layoutUseTrash`), inside `containerSafety`.
- The row follows the same structure as `layoutUseTrash`: full-width horizontal LinearLayout with the switch, then a vertical labels block.
- View id: `@+id/layoutAllowDelete` (outer), `@+id/switchAllowDelete` (switch).
- Labels: `@string/allow_delete` (title), `@string/setting_allow_delete_desc` (description).
- No landscape counterpart file exists — no landscape edit needed.
- `OperationsSettingsFragment.kt` `setupViews()`: add listener after `switchUseTrash` block:
  ```kotlin
  binding.switchAllowDelete.setOnCheckedChangeListener { _, isChecked ->
      if (isUpdatingFromSettings) return@setOnCheckedChangeListener
      viewModel.updateSettings(viewModel.settings.value.copy(allowDelete = isChecked))
  }
  ```
- `OperationsSettingsFragment.kt` `observeData()` settings collect block: add:
  ```kotlin
  binding.switchAllowDelete.isChecked = settings.allowDelete
  ```

**Verification:** `fragment_settings_destinations.xml` contains `switchAllowDelete`. `OperationsSettingsFragment.kt` binds `switchAllowDelete` and reads `settings.allowDelete`.

### Phase 03 — Update SettingsSearchRegistry

- `SettingsSearchIndex.kt`: update entry with `key = "playback.allow_delete"`:
  - `sectionId`: `"playback"` → `"operations"`
  - `destination`: `SettingsSearchDestination.PLAYBACK` → `SettingsSearchDestination.DESTINATIONS`
  - The `viewId` stays `R.id.switchAllowDelete` — the id is the same in the destinations layout.
  - `localizedKeywords` is preserved unchanged.

**Verification:** `SettingsSearchRegistry` entry `playback.allow_delete` has `destination = DESTINATIONS` and `sectionId = "operations"`. No entry uses `destination = PLAYBACK` for `allow_delete`.

### Phase 04 — Debug tag insertion

- Add `Timber.d("S0122: allow_delete relocated — OperationsSettingsFragment setupViews")` at the entry of `setupViews()` in `OperationsSettingsFragment.kt`.
- Add `Timber.d("S0122: playback switches removed — PlaybackSettingsFragment setupViews")` at the entry of `setupViews()` in `PlaybackSettingsFragment.kt`.

### Phase 05 — Verify build and confirm

- Run standard debug build; must pass with zero errors.
- Non-regression checklist:
  - Search "allow delete" / "разрешить удаление" returns Operations tab.
  - `PlaybackSettingsFragment` shows no `switchAllowDelete`, no `switchConfirmDelete`.
  - `OperationsSettingsFragment` shows `switchAllowDelete` in Safety section.
  - `allowDelete` preference key unchanged; existing user setting survives.
  - `confirmDelete` preference key unchanged; existing user setting survives.

## Last Audit

**2026-05-08 — Verified**

Checks passed:
- `fragment_settings_playback.xml` (portrait + landscape): no `switchAllowDelete`, no `switchConfirmDelete`, no `containerDeleteConfirm`.
- `PlaybackSettingsFragment.kt`: no references to `switchAllowDelete` or `switchConfirmDelete`.
- `fragment_settings_destinations.xml`: `switchAllowDelete` present in Safety section after `layoutUseTrash`.
- `OperationsSettingsFragment.kt`: `binding.switchAllowDelete` listener set in `setupViews()` and state read in `observeData()`.
- `SettingsSearchRegistry` entry `playback.allow_delete`: `destination = DESTINATIONS`, `sectionId = "operations"`.
- `standard debug` build: SUCCESSFUL (12 s, no errors).
- No landscape counterpart for `fragment_settings_destinations.xml` exists — none required.
- Timber debug tags inserted at both fragment `setupViews()` entry points.
