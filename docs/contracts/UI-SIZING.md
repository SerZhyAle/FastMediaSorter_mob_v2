<sub class="doc-stamp">26.11.20 20:17</sub>

# Pointer - `UI-SIZING`

| | |
| --- | --- |
| **Id** | `UI-SIZING` |
| **Version** | 1.4, active. Owner: shared |
| **Home** | `ui-sizing/README.md` in the shared contracts catalog |
| **Role here** | consumer - phone, launcher and watch |

## What this repository must do to stay conformant

- Component scale and touch targets follow the contract's profiles, measured on real components rather than asserted from dimensions.
- The watch app is a covered surface under the watch profile (1.2, section 6): it takes the touch profile. Its
  Compose for Wear OS chips, buttons and lists on the round display are product-owned toolkit controls whose pill
  ends and edge scaling are a permanent variant, recorded in the product's size-profile record with a review
  condition; a control the product draws itself keeps rule 5. The record is `docs/ui/WEAR_SIZE_PROFILE.md` (S4600):
  touch profile, 48 dp floor, no compact mode, the toolkit variant with its review condition, and three open deviations.
- The phone and launcher record is `docs/ui/PHONE_SIZE_PROFILE.md` (S4625): touch profile, 48 dp ordinary floor,
  declared compact floor 24 by 24 dp for the "Compact elements" setting, the profile change applied at the next
  opening, and its open deviations.
- Since 1.3 and 1.4 the text names Windows applications only (the tray popup menu's system font in section 8, the
  pointer to `WINDOWS-SCALE` rule S1 in section 9); neither changes the phone, launcher or watch profile.
- Not implemented on the existing surfaces yet - not "not applicable": the migration, and the evidence the
  contract asks for before conformance is claimed, are owned by S4619.
- The deviations currently open are recorded as exceptions in the catalog's registry.

## Where it lives here

- Sizing resources under `app_v2/src/main/res/values*/dimens.xml` and the "Compact elements" setting.
