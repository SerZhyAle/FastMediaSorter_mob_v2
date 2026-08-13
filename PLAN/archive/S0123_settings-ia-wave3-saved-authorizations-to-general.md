# S0123 Settings IA Wave 3 — Move Saved Authorizations Row to General Tab

<!-- auto-approved by /spec-all — 2026-05-08 -->

**Ticket:** S0123  
**Status:** Verified  
**Priority:** 60  
**Parent:** S0119  
**Depends on:** S0121 (Verified), S0122 (Verified)

## Goal

Move `rowSavedAuthorizations` (`@+id/row_saved_authorizations`) from the Playback tab
(Input section) to the General tab (Network/Credentials area). Saved OAuth session tokens
are a network credentials concern, not a playback setting — they belong alongside the
default user/password fields. Completes migration item M3 from S0119 migration-map.

After this wave, users managing credentials find all credential-related controls
(default user, default password, saved OAuth sessions) in the same card in the
General tab, and the Playback tab no longer exposes this row.

## Scope

Module: `app_v2`. Touches: 2 portrait layouts, 2 landscape layouts, 1 Fragment Kotlin
file (remove binding), 1 ViewSetupHelper Kotlin file (add binding), 1 SearchIndex Kotlin
file (add/update entry).

## Key Facts (from research)

- `rowSavedAuthorizations` view id: `@+id/row_saved_authorizations`
- String keys: `@string/setting_saved_authorizations_title`, `@string/setting_saved_authorizations_summary`
- Playback click handler (line 251-253): `binding.rowSavedAuthorizations.setOnClickListener { AuthSessionsActivity.start(requireContext()) }`
- Playback `isEnabled` tie (line 441): `binding.rowSavedAuthorizations.isEnabled = settings.linkAutoDownloadEnabled` — must be removed; row is always enabled in General tab
- `SettingsSearchDestination.GENERAL` = `GENERAL(0)` (tabIndex 0)
- No existing search entry for saved authorizations
- Landscape playback layout `fragment_settings_playback.xml` exists and contains the row at line 346
- Landscape general layout `fragment_settings_general.xml` exists — insertion point: after `iconHelpDefaultCredentials` ConstraintLayout, before `containerSettingsData`
- Click handler should be added inside `GeneralSettingsViewSetupHelper` (handles all credential-area bindings) rather than directly in `GeneralSettingsFragment.onViewCreated`
- Import: `com.sza.fastmediasorter.ui.settings.auth.AuthSessionsActivity` already imported in PlaybackSettingsFragment — must be added to GeneralSettingsViewSetupHelper

## Phases

### Phase 1 — Remove from Playback tab

**Files:** `fragment_settings_playback.xml`, `layout-land/fragment_settings_playback.xml`, `PlaybackSettingsFragment.kt`

1. Remove the `<!-- S0116 §5.1 pillar K: … -->` comment block and `<LinearLayout android:id="@+id/row_saved_authorizations" … >` (3-line block) from `fragment_settings_playback.xml`.
2. Remove the same block from `layout-land/fragment_settings_playback.xml`.
3. In `PlaybackSettingsFragment.kt`, remove lines:
   - `binding.rowSavedAuthorizations.setOnClickListener { AuthSessionsActivity.start(requireContext()) }`
   - `binding.rowSavedAuthorizations.isEnabled = settings.linkAutoDownloadEnabled`
4. If after removal the `import com.sza.fastmediasorter.ui.settings.auth.AuthSessionsActivity` line in `PlaybackSettingsFragment.kt` becomes unused, remove it too.

**Verification:** `fragment_settings_playback.xml` contains no `row_saved_authorizations`; `PlaybackSettingsFragment.kt` has no `rowSavedAuthorizations` reference; build passes.

### Phase 2 — Add to General tab layouts

**Files:** `fragment_settings_general.xml`, `layout-land/fragment_settings_general.xml`

1. In portrait `fragment_settings_general.xml`, insert after the closing `</androidx.constraintlayout.widget.ConstraintLayout>` of the Default User/Password block (after line 228), before the `<!-- S0121: Settings Data sub-section -->` comment:

```xml
                    <!-- S0123: Saved authorizations sub-screen entry — network credentials concern -->
                    <LinearLayout android:id="@+id/row_saved_authorizations" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginBottom="@dimen/margin_small" android:gravity="center_vertical" android:minHeight="@dimen/settings_item_min_height" android:orientation="vertical" android:clickable="true" android:focusable="true">
                        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/setting_saved_authorizations_title" android:textSize="@dimen/toggler_title_text_size" />
                        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/setting_saved_authorizations_summary" android:textSize="@dimen/toggler_desc_text_size" android:textColor="@color/text_color_secondary" />
                    </LinearLayout>
```

2. In landscape `layout-land/fragment_settings_general.xml`, insert after the closing `</androidx.constraintlayout.widget.ConstraintLayout>` of the Default User/Password ConstraintLayout (containing `iconHelpDefaultCredentials`), before `<LinearLayout android:id="@+id/containerSettingsData"`:

```xml
                    <!-- S0123: Saved authorizations sub-screen entry — network credentials concern -->
                    <LinearLayout android:id="@+id/row_saved_authorizations" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginBottom="@dimen/margin_small" android:gravity="center_vertical" android:minHeight="@dimen/settings_item_min_height" android:orientation="vertical" android:clickable="true" android:focusable="true">
                        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/setting_saved_authorizations_title" android:textSize="@dimen/toggler_title_text_size" />
                        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/setting_saved_authorizations_summary" android:textSize="@dimen/toggler_desc_text_size" android:textColor="@color/text_color_secondary" />
                    </LinearLayout>
```

**Verification:** `fragment_settings_general.xml` (portrait and landscape) each contain `row_saved_authorizations`; layout renders without error.

### Phase 3 — Wire click handler in GeneralSettingsViewSetupHelper

**File:** `GeneralSettingsViewSetupHelper.kt`

1. Add import: `import com.sza.fastmediasorter.ui.settings.auth.AuthSessionsActivity`
2. In the `setup()` or credential-setup section (near `setupDefaultCredentials()` call, around line 158 where `iconHelpDefaultCredentials.setOnClickListener` is set), add:

```kotlin
binding.rowSavedAuthorizations.setOnClickListener {
    Timber.d("S0123: rowSavedAuthorizations clicked → launching AuthSessionsActivity")
    AuthSessionsActivity.start(fragment.requireContext())
}
```

**Verification:** Clicking the row in the General tab launches `AuthSessionsActivity`; Timber tag appears in logcat.

### Phase 4 — Update SettingsSearchRegistry

**File:** `SettingsSearchIndex.kt`

1. Add a new `SettingsSearchIndex` entry to `SettingsSearchRegistry.entries` list (insert before the closing `)`):

```kotlin
        SettingsSearchIndex(
            key = "general.saved_authorizations",
            title = "Saved authorizations",
            keywords = listOf("authorizations", "sessions", "oauth", "credentials", "tokens", "accounts"),
            sectionId = "general",
            destination = SettingsSearchDestination.GENERAL,
            viewId = R.id.row_saved_authorizations,
            localizedKeywords = mapOf(
                "ru" to listOf("авторизации", "сохранённые сессии", "аккаунты", "учётные данные", "токены"),
                "uk" to listOf("авторизації", "збережені сесії", "облікові дані", "токени", "акаунти")
            )
        )
```

**Verification:** Searching "authorizations", "авторизации", or "sessions" in settings search routes to General tab.

## Non-regression Checklist

- [ ] `AuthSessionsActivity` still launches from the new row in General tab
- [ ] Playback tab no longer shows `row_saved_authorizations`
- [ ] `rowSavedAuthorizations.isEnabled` tie to `linkAutoDownloadEnabled` is removed
- [ ] Both portrait and landscape General layouts updated
- [ ] Both portrait and landscape Playback layouts cleaned
- [ ] Search entry routes to `SettingsSearchDestination.GENERAL`
- [ ] Build passes (standard debug)

## Last Audit

**Date:** 2026-05-08  
**Result:** Verified  
**Build:** standard debug — PASS (34s, BUILD SUCCESSFUL)

All 8 verification predicates passed:
- Playback .kt: no `rowSavedAuthorizations` or `AuthSessionsActivity` refs
- Playback portrait layout: `row_saved_authorizations` removed
- Playback landscape layout: `row_saved_authorizations` removed
- General portrait layout: `row_saved_authorizations` present after credentials block
- General landscape layout: `row_saved_authorizations` present after credentials block
- `GeneralSettingsViewSetupHelper`: click handler wired → `AuthSessionsActivity.start()`
- `SettingsSearchIndex`: `general.saved_authorizations` entry with `GENERAL` destination
- `isEnabled` tie to `linkAutoDownloadEnabled` removed from Playback
- All `Timber.d("S0123:")` tags removed before Verified transition
