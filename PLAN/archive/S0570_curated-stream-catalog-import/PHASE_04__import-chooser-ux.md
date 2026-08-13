# Phase 04 - "Import list" chooser UX

**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** Implemented
**Depends on:** Phase 03
**Blocks:** Phase 05

## Objective

Turn the existing `action_stream_import` toolbar action into a chooser: "Update FastMediaSorter
catalog" (one-tap, our resource) and "Import from URL" (the existing manual `.m3u`). Wire the catalog
import through the ViewModel; report added/updated/removed; keep the Activity logic-free.

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsViewModel.kt` | Modified | <= +30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt` | Modified | <= +40 |
| `app_v2/src/main/res/values/strings.xml` (+ `values-ru` + `values-uk`) | Modified | <= +24 |

## Steps

### Step 04.1 - Strings (EN/RU/UK lockstep)

> Via `scripts/utils/set-android-string.ps1 -Action add` (one call per key, EN/RU/UK): `streams_import_choose_title` ("Import list"), `streams_import_catalog` ("Update FastMediaSorter catalog"), `streams_import_from_url` ("Import from URL"), `streams_catalog_updated` ("Catalog: +%1$d new, %2$d updated, %3$d removed"), `streams_catalog_empty` ("Catalog is empty or unavailable"). RU uses Ё where correct. Pass `docs/COMMUNICATION_POLICY.md` tone.

**Verification:** `pwsh scripts/check_strings_localized.ps1 -KeyPrefix "streams_import_"` and `-KeyPrefix "streams_catalog_"` exit 0.

### Step 04.2 - ViewModel `onImportCatalog`

> Inject `ImportStreamCatalogUseCase`. Add `fun onImportCatalog()` launching on `viewModelScope`: set a loading flag, call the use case, map `Success` -> a new `StreamsEvent.CatalogUpdated(added, updated, removed)`, `Empty` -> `Message(streams_catalog_empty)`, `Failure` -> `Message(streams_error_network)`. Add `CatalogUpdated` to the `StreamsEvent` sealed interface. No View types.

**Verification:** `Grep` - `onImportCatalog`, `ImportStreamCatalogUseCase`, `CatalogUpdated` present; `viewModelScope` used.

### Step 04.3 - Activity chooser

> On `action_stream_import`, show a small chooser (MaterialAlertDialog with two items, or a bottom sheet consistent with the app) titled `streams_import_choose_title`: item 1 -> `viewModel.onImportCatalog()`; item 2 -> the existing add/import URL dialog. Collect `CatalogUpdated` via `collectOnLifecycle`/`repeatOnLifecycle` and show `streams_catalog_updated` toast/snackbar. No business logic in the Activity - it only forwards to the ViewModel. Use a themed context for the dialog (avoid the transparent-theme inflate trap - see S0571/S0573).

**Verification:** `Grep` - `onImportCatalog` invoked from the Activity; `CatalogUpdated` handled; `repeatOnLifecycle`/`collectOnLifecycle` present (no bare `lifecycleScope.launch { collect }`).

**Status:**
- [x] Step 04.1 - strings (EN/RU/UK lockstep, 5 keys)
- [x] Step 04.2 - ViewModel `onImportCatalog` + `CatalogUpdated`
- [x] Step 04.3 - Activity chooser + event handling

## Phase Done Criteria

- [x] Steps 04.1-04.3 done.
- [ ] `.\a.ps1 fc` PASS (standard) + Kotlin compile (lite). - central build deferred to orchestrator.
- [x] `check_strings_localized.ps1` exits 0 for the new prefixes (parity validated per-key on add).
- [x] Activity holds no import logic; chooser dialog uses `MaterialAlertDialogBuilder(this)` (BaseActivity is already Material-themed - no ContextThemeWrapper needed, matching existing dialogs on this screen).

## Step Log

- 04.1: added `streams_import_choose_title`, `streams_import_catalog`, `streams_import_from_url`, `streams_catalog_updated`, `streams_catalog_empty` via `set-android-string.ps1 -Action add` (UTF-8 temp wrapper to keep Cyrillic clean). RU uses Ё where applicable. Each key parity-validated on add.
- 04.2: `StreamsViewModel` now injects `ImportStreamCatalogUseCase`; `onImportCatalog()` runs on `viewModelScope`, toggles `isImporting`, maps `Success -> CatalogUpdated`, `Empty -> Message(streams_catalog_empty)`, `Failure -> Message(streams_error_network)`. Added `CatalogUpdated(added,updated,removed)` to the `StreamsEvent` sealed interface. Hilt provides the use case via its `@Inject` constructor (all deps `@Inject`-constructible) - no DI module entry required.
- 04.3: `action_stream_import` now opens `showImportChooser()` (MaterialAlertDialog 2-item list, title `streams_import_choose_title`): item 0 -> `viewModel.onImportCatalog()`, item 1 -> existing `showSourceDialog(isImport = true)`. `CatalogUpdated` handled in the existing `collectOnLifecycle(events)` block -> `streams_catalog_updated` toast. Activity only forwards.
