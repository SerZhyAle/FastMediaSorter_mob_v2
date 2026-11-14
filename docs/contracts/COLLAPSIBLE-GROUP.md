<sub class="doc-stamp">26.11.14 16:01</sub>

# Pointer - `COLLAPSIBLE-GROUP`

| | |
| --- | --- |
| **Id** | `COLLAPSIBLE-GROUP` |
| **Version** | 1.2, active. Owner: shared |
| **Home** | `collapsible-group/README.md` in the shared contracts catalog |
| **Role here** | consumer - phone, launcher and watch |

## What this repository must do to stay conformant

- A collapsible group shows its state by its marker, toggles from the whole header and keeps its stored state.
- Wear OS (1.2, section 7 item A) binds a group header a watch app draws: its marker stands at the reading-start
  side of the toolkit's safe area, the header keeps whole-header activation at the `UI-SIZING` touch baseline, and the
  shape is recorded as a permanent variant in the size-profile record. The watch draws one group header itself, on
  the System info screen; it keeps this contract's geometry and does not meet it yet (`docs/ui/WEAR_SIZE_PROFILE.md`,
  open deviation 2).
- Not implemented on the existing surfaces yet - not "not applicable": the migration, and the evidence the
  contract asks for before conformance is claimed, are owned by S4619.
- The deviations currently open are recorded as exceptions in the catalog's registry.

## Where it lives here

- `app_v2/.../ui/common/widget/CollapsibleSectionHeader.kt`.
