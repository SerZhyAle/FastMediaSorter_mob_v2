<sub class="doc-stamp">26.11.14 16:01</sub>

# Pointer - `SETTINGS-NAVIGATION`

| | |
| --- | --- |
| **Id** | `SETTINGS-NAVIGATION` |
| **Version** | 2.1, active. Owner: shared |
| **Home** | `settings-navigation/README.md` in the shared contracts catalog |
| **Role here** | consumer - the main settings, every settings dialog and the watch settings |

## What this repository must do to stay conformant

- Nothing on the phone: the portfolio owner decided on 2026-11-15 that the phone settings keep their own form -
  a top tab strip, About as the last group of General, and each host screen's settings in its own dialog (S4634).
  The deviation is the catalog registry exception row "S4634"; the proposal
  `PROPOSAL-2026-11-15-fms-android-scope-windows-apps.md` asks to scope the contract to Windows settings windows.
- The watch settings follow the 2.1 item C reading (`SettingsDestinationCatalog` in `wear/`).

## Where it lives here

- `app_v2/src/main/res/layout/activity_settings.xml` and `app_v2/.../ui/settings/SettingsActivity.kt`; the watch settings screens.
