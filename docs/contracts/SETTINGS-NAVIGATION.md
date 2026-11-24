<sub class="doc-stamp">26.11.24 12:37</sub>

# Pointer - `SETTINGS-NAVIGATION`

| | |
| --- | --- |
| **Id** | `SETTINGS-NAVIGATION` |
| **Version** | 2.4, active. Owner: shared |
| **Home** | `settings-navigation/README.md` in the shared contracts catalog |
| **Role here** | consumer - the main settings, every settings dialog and the watch settings |

## What this repository must do to stay conformant

- Nothing on the phone: the portfolio owner decided on 2026-11-15 that the phone settings keep their own form -
  a top tab strip, About as the last group of General, and each host screen's settings in its own dialog (S4634).
  The deviation is the catalog registry exception row "S4634"; the proposal
  `PROPOSAL-2026-11-15-fms-android-scope-windows-apps.md` asks to scope the contract to Windows settings windows.
- The watch settings follow the 2.1 item C reading (`SettingsDestinationCatalog` in `wear/`).
- Since 2.3 (item F) the contract binds the settings windows of Windows desktop applications and the settings surface of
  an Android phone application is outside it, which is the shape the phone settings already have; the watch reading of
  item C stays bound.
- Since 2.4 (additive, items G and H): static rail section headings and a marked cross-window action row are permitted
  on a rail. Neither the phone tab strip nor the watch page list has a rail, so neither is used and nothing is owed.

## Where it lives here

- `app_v2/src/main/res/layout/activity_settings.xml` and `app_v2/.../ui/settings/SettingsActivity.kt`; the watch settings screens.
