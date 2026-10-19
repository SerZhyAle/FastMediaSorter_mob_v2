<sub class="doc-stamp">26.10.18 17:47</sub>

# Pointer - `UI-SIZING`

| | |
| --- | --- |
| **Id** | `UI-SIZING` |
| **Version** | 1.1, active. Owner: shared |
| **Home** | `ui-sizing/README.md` in the shared contracts catalog |
| **Role here** | consumer - phone, launcher and watch |

## What this repository must do to stay conformant

- Component scale and touch targets follow the contract's profiles, measured on real components rather than asserted from dimensions.
- Not implemented on the existing surfaces yet - not "not applicable": the migration, and the evidence the
  contract asks for before conformance is claimed, are owned by S4352.
- The deviations currently open are recorded as exceptions in the catalog's registry.

## Where it lives here

- Sizing resources under `app_v2/src/main/res/values*/dimens.xml` and the "Compact elements" setting.
