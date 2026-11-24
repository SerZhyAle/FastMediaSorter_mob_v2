<sub class="doc-stamp">26.11.24 13:35</sub>

# Pointer - `BOOLEAN-OPTION`

| | |
| --- | --- |
| **Id** | `BOOLEAN-OPTION` |
| **Version** | 2.2, active. Owner: shared |
| **Home** | `boolean-option/README.md` in the shared contracts catalog |
| **Role here** | consumer - phone, launcher and watch settings |

## What this repository must do to stay conformant

- A boolean option is drawn as the contract's component and states its value on the whole row, not as a free-standing switch.
- Watch settings (2.1, section 7 item A): the contract binds a Wear OS screen under the watch profile of
  `UI-SIZING`, whose permanent variant names no toggle component - a toolkit toggle on the watch that differs from
  the checkbox pattern is a dated registry exception until a proposal with evidence extends the variant.
  `StandardWearToggleChip` draws a trailing switch glyph, so the watch boolean rows carry that exception (recorded
  2026-11-14 by S4600, until 2026-12-31, remediation S4619); its radio form is a single choice and outside this
  contract. Record: `docs/ui/WEAR_SIZE_PROFILE.md`.
- Phone and launcher rows: the shared row `SettingsToggleRow` keeps its `MaterialSwitch` at the row's start,
  before the text group, instead of the contract's checkbox, and the whole row toggles it - the portfolio owner's decision of 2026-11-15 (S4634), recorded as the catalog registry
  exception row "S4634".
- The deviations currently open are recorded as exceptions in the catalog's registry.

## Where it lives here

- The shared settings row `app_v2/src/main/res/layout/view_settings_toggle_row.xml`.
