# Pointer - `STREAM-BANK`

| | |
| --- | --- |
| **Id** | `STREAM-BANK` |
| **Version** | 2.1, active. Owner: this product |
| **Home** | `stream-catalog/README.md` in the shared contracts catalog |
| **Role here** | producer (the publish script) and consumer (the phone and watch importers) |

## What this repository must do to stay conformant

- Producer: keep `streams.csv` the **first** ZIP entry, only ever append columns, never pair a
  `favicon_index` with an atlas from another build, and publish `artwork-manifest.json` in the run that
  uploads the payload it describes (rules 2, 6, 12).
- Consumer: match columns by **header name** and ignore unknown ones; drop rows missing `url` or `name`;
  let a blank **or unrecognised** `media_kind` fall back to the URL classifier (rules 3, 4, item M).
- Merge by `url` and touch only `CATALOG`-origin rows - a row the user added or imported by hand is never
  updated, moved or deleted by a refresh (rule 5).
- Treat `access` as opaque: blank means open, any other value is a restriction the app does not model
  (rule 10, item E).
- Bounds-check every atlas index and degrade to no-thumbnail; derive sheet geometry from the image
  (rules 8, 9).
- A change to a column, an asset name or an atlas ceiling is agreed in the catalog first.

## Where it lives here

- Producer: `scripts/streams/collect-stream-candidates.ps1` and `scripts/streams/modules/`; the
  operating page is `delivery/stream-catalog/README.md`; the asset-name gate is
  `scripts/quality/assert-stream-asset-revisions.ps1`.
- Phone consumer: `app_v2/.../domain/usecase/streams/ImportStreamCatalogUseCase.kt`,
  `app_v2/.../data/repository/StreamCatalogCsvParser.kt`,
  `app_v2/.../data/repository/StreamSourceRepository.kt` (`mergeCatalog`),
  `app_v2/.../domain/usecase/streams/StreamMediaKindClassifier.kt`,
  `app_v2/.../ui/streams/FaviconAtlasSlicer.kt`, `app_v2/.../data/delivery/ArtworkManifestClient.kt`,
  `app_v2/.../ui/streams/StreamSourceAdapter.kt`,
  `app_v2/.../ui/dialog/helpers/StreamPropertiesFormatter.kt`.
- Watch consumer: `wear/.../domain/usecase/ImportWearStreamCatalogUseCase.kt`,
  `wear/.../data/repository/WearStreamCatalogCsvParser.kt`.
