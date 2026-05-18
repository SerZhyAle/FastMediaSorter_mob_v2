# ACTIVITY_CATALOG - JSONL Schema

Merge key: `module + class` (unique combination per record).

## Auto-populated fields (set/overwritten by `scan.ps1`)

| Field | Type | Description |
|-------|------|-------------|
| `class` | string | Simple class name (e.g. `PlayerActivity`) |
| `package` | string | Fully-qualified class name |
| `module` | string | `app_v2` or `wear` |
| `path` | string | Relative path to `.kt` source under module source root; empty if not found |
| `sourceSet` | string | `main`, `vr`, or `""` |
| `exported` | bool | Value of `android:exported` attribute |
| `launcher` | bool | `true` if has `MAIN + LAUNCHER` intent-filter |
| `intentActions` | string[] | All `android:name` values from `<action>` tags |
| `intentCategories` | string[] | All `android:name` values from `<category>` tags |
| `noFlavors` | string[] | Flavors where the Activity is absent (detected from flavor manifests) |
| `loc` | int | Source file line count; 0 if source not found |
| `lastTouched` | string | `yyyy-MM-dd` from `git log`; empty if no source |

## Manual fields (preserved on rescan)

| Field | Type | Description |
|-------|------|-------------|
| `role` | string | English one-line description of the Activity's purpose |
| `roleRu` | string | Russian one-line description (used for RU-language search) |
| `tags` | string[] | Keyword tags for fast search (e.g. `["player","portrait","pip"]`) |
| `status` | string | One of: `new`, `tested`, `todo`, `unknown` |
| `notes` | string | Free-text notes |

## Allowed `status` values

`new` · `tested` · `todo` · `unknown`

## Flavors recognised by `noFlavors`

`standard` · `lite` · `photos` · `legacy` · `vr`
