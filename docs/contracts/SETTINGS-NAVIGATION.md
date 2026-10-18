<sub class="doc-stamp">26.10.18 17:47</sub>

# Pointer - `SETTINGS-NAVIGATION`

| | |
| --- | --- |
| **Id** | `SETTINGS-NAVIGATION` |
| **Version** | 2.0, active. Owner: shared |
| **Home** | `settings-navigation/README.md` in the shared contracts catalog |
| **Role here** | consumer - the main settings, every settings dialog and the watch settings |

## What this repository must do to stay conformant

- Settings are navigated the way the contract lays out, with About reachable as its last page.
- Not implemented on the existing surfaces yet - not "not applicable": the migration, and the evidence the
  contract asks for before conformance is claimed, are owned by S4352.
- The deviations currently open are recorded as exceptions in the catalog's registry.

## Where it lives here

- `app_v2/src/main/res/layout/activity_settings.xml` and `app_v2/.../ui/settings/SettingsActivity.kt`; the watch settings screens.
