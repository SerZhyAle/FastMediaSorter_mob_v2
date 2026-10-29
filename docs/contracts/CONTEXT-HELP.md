<sub class="doc-stamp">26.10.29 05:03</sub>

# Pointer - `CONTEXT-HELP`

| | |
| --- | --- |
| **Id** | `CONTEXT-HELP` |
| **Version** | 0.2, draft. Owner: shared |
| **Home** | `in-app-help/README.md` in the shared contracts catalog |
| **Role here** | consumer - the phone app `app_v2`, adoption `partial` (first slice: Main, the four Settings tabs, Welcome); and the watch app `wear`, which meets rule 4 with a reasoned `none` row per route |

## What this repository must do to stay conformant

- F1 with a hardware keyboard and the `?` header action open the same help of the surface (rules 1-3); F1 is a screen-level key, never a system-wide one.
- The `?` action adds to the top-end corner of a surface's header and never replaces, covers or re-purposes an existing control (rule 2); it draws `app.help` of `ICON-SET`, which is this product's `ic_help_outline`.
- Every surface has a row in the help map, with a reason for `inherit` and `none` (rules 4, 17).
- A help page follows the anatomy of rule 8, ships inside the package, opens offline in the in-app viewer with JavaScript off and makes no request (rule 12); the same generated files are published on the portal.
- A page exists in every shipped UI locale and falls back to English (rule 13); EN/RU/UK are written with the change, the other ten locales fan out at the release boundary (repo Rule 30).
- Screenshots are generated from the build with demo content and markers from the UI tree (rule 14); a help id never changes once shipped (rule 15).

## Decisions taken here (the contract delegates them to the product)

- A surface without a header carries its `?` in an overlay at the top-end corner, inside system-bar and cutout bounds. It follows the visibility of the tool's controls where those fade; forms reserve a clear strip above their content. The game keeps its existing rules entry. The water flashlight has no touch help entry: its wet-touch protection takes precedence, and F1 remains available (owner ruling, S4328).
- `page` is the only form built so far; `layer` may be added later without changing the map (S4288 ADR-3).
- Each Settings tab is its own page; the nested Media categories are sections of the Media page (S4288 ADR-3).
- The existing overflow "Help" items stay and route through the same resolver; the keyboard shortcut list opens from the help viewer header (S4288 ADR-4).
- Help ids join `docs/docs-pages-manifest.jsonl` as the `help` category, under the existing permanent-address gates (S4288 ADR-5).
- The watch app owes a map row and nothing else: every navigation route of `wear` has a `none` row with a reason, because a round screen has no top-right corner and no F1 and rule 4 accepts a reasoned `none`. No watch help page, no phone-side page per watch screen and no help screen on the watch (S4291). Dialogs and menus inside a watch screen are not surfaces; tiles and complications belong to the system.

## Where it lives here

- Help map: `app_v2/src/main/assets/help/help-map.json`; the watch's map is `wear/help-map.json`, outside `src/main` because nothing on the watch reads it.
- Surface enumerator: `scripts/docs/help/list-help-surfaces.ps1` (phone: manifest and dialog classes; watch, `-Module wear`: the `composable(..)` routes).
- Page sources: `docs/content/help/`; generator: `scripts/docs/help/generate-help-pages.ps1`.
- Gate (rung 1, static): `scripts/quality/assert-help-map.ps1`, baseline `scripts/quality/help-map-baseline.txt` (falls only).
- Adopting tickets: S4288 (phone), S4291 (watch).
