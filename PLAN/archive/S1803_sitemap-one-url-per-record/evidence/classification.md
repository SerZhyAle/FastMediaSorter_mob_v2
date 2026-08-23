# S1803 step 02.1 - per-group default for the newly announced addresses

Decided before looking at individual pages, per the step's own instruction: a group whose members are
all user-facing announces everything by default; a group that mixes audiences announces nothing by
default and earns each entry one at a time.

Measured 2026-08-18 against the working tree with `PLAN/S1803_sitemap-one-url-per-record/evidence/announced_scan.py`, which walks each
indexable record's path globs and asks whether the file's own front matter declares an address that
the generated `sitemap.xml` carries. Sitemap entries at the time of the pass: **73**.

## Per-group defaults

| Record | Files | Announced now | Default | Reason |
|---|---:|---:|---|---|
| `site-landing` | 4 | 3, confirmed in the sitemap | **announce all** | The site entry points. Their addresses come from the site root and the HTML files themselves, not from Markdown front matter, so the front-matter probe reported them silent - a limit of the probe, not an exclusion. Confirmed directly against the generated `sitemap.xml`: the root, `index-ru.html` and `index-uk.html` are all present, and `README.md` is the root's source rather than a fourth address. |
| `user-guides` | 49 | 47 | **announce all** | A group of owner-facing guides in three locales plus the how-to scenarios; every member exists to be found by a person setting the app up. Two members are not guides at all and are named as exceptions below. |
| `feature-showcase` | 9 | 6 | **announce all** | The published feature showcase, one page per locale. The three `noLegal` variants are a different distribution channel, not a different page, and are named as exceptions below. |
| `settings-reference` | 7 | 3 | **announce every rendered page; the data inputs are exclusions** | Mixed by construction: three rendered reference pages sit beside three machine-readable JSON inputs and one `noLegal` variant. Corrected from "announce nothing by default" once the mechanism was read: `sitemap_exclude` can only carve out, so a default of nothing cannot be expressed, and a rule the registry cannot state is a rule nobody enforces. The carve-outs say the same thing and are checkable. |
| `wear-docs` | 2 | 2 | **announce all** | Both members are watch-owner instructions, and both are already announced. Nothing to decide. |
| `legal-downloads` | 10 | 9 | **announce all** | Legal terms, privacy and download pages are exactly what a person looks for by search, and several are required to be reachable. One member is a generated licence dump and is named as an exception below. |
| `oss-notices` | 4 | 3 | **announce all** | Three rendered notice pages, one per locale. The fourth member is the JSON the pages are rendered from, named as an exception below. |

## Exception candidates handed to step 02.2

These are the members that the group default does not fit. Each still needs its own decision and a
stated reason in step 02.2 - listing one here is not deciding it.

- `docs/HOW_TO_DEVELOP_AND_RELEASE_RU.md` - a developer release procedure living inside the user-guide
  group. This is exactly the "search results filled with internal notes" risk strategic §7 names.
- `docs/howto/SCREENSHOTS.md` - an asset/inventory note for the how-to scenarios, not a scenario.
- `docs/FEATURES_noLegal.md`, `docs/FEATURES_noLegal_RU.md`, `docs/FEATURES_noLegal_UK.md` - the
  sideload channel's showcase; announcing it from the main site advertises a build the store listing
  does not describe.
- `docs/SETTINGS_REFERENCE_noLegal.md` - same channel argument, and note it already declares an
  address (`/docs/SETTINGS_REFERENCE_noLegal.html`) that the sitemap does not carry, so today's state
  is an undocumented exclusion rather than a decision.
- `docs/settings/howto-path-vocab.json`, `docs/settings/settings-annotations.json`,
  `docs/settings/settings-manifest.json`, `docs/legal/oss-notices.json` - machine-readable inputs to
  rendered pages. A JSON file has no reader-facing address to announce.
- `THIRD_PARTY_LICENSES.md` - the generated licence dump that ships inside the package; the reader-facing
  form of it is the `oss-notices` group.
