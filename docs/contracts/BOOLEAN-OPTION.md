<sub class="doc-stamp">26.10.18 17:47</sub>

# Pointer - `BOOLEAN-OPTION`

| | |
| --- | --- |
| **Id** | `BOOLEAN-OPTION` |
| **Version** | 2.0, active. Owner: shared |
| **Home** | `boolean-option/README.md` in the shared contracts catalog |
| **Role here** | consumer - phone, launcher and watch settings |

## What this repository must do to stay conformant

- A boolean option is drawn as the contract's component and states its value on the whole row, not as a free-standing switch.
- Not implemented on the existing surfaces yet - not "not applicable": the migration, and the evidence the
  contract asks for before conformance is claimed, are owned by S4352.
- The deviations currently open are recorded as exceptions in the catalog's registry.

## Where it lives here

- The shared settings row `app_v2/src/main/res/layout/view_settings_toggle_row.xml`.
