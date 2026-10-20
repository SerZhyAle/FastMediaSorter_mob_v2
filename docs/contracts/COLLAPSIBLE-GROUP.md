<sub class="doc-stamp">26.10.18 17:47</sub>

# Pointer - `COLLAPSIBLE-GROUP`

| | |
| --- | --- |
| **Id** | `COLLAPSIBLE-GROUP` |
| **Version** | 1.1, active. Owner: shared |
| **Home** | `collapsible-group/README.md` in the shared contracts catalog |
| **Role here** | consumer - phone and launcher |

## What this repository must do to stay conformant

- A collapsible group shows its state by its marker, toggles from the whole header and keeps its stored state.
- Not implemented on the existing surfaces yet - not "not applicable": the migration, and the evidence the
  contract asks for before conformance is claimed, are owned by S4352.
- The deviations currently open are recorded as exceptions in the catalog's registry.

## Where it lives here

- `app_v2/.../ui/common/widget/CollapsibleSectionHeader.kt`.
