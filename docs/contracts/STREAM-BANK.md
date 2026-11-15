<sub class="doc-stamp">26.11.14 17:19</sub>

# Pointer - `STREAM-BANK`

| | |
| --- | --- |
| **Id** | `STREAM-BANK` |
| **Version** | 2.10, active. Owner: this product |
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
- A blank `is_live` is "not stated": it may read as `false` only while no surface shows or filters on
  it (rule 13).
- Producer and importers emit only vocabulary languages (one entry per language, `english` when a non-blank
  cell names none, blank stays blank) and assigned ISO 3166-1 alpha-2 country codes, an unassigned value
  being blank (rule 14, item O).
- Treat `access` as opaque: blank means open, any other value is a restriction the app does not model
  (rule 10, item E).
- Bounds-check every atlas index and degrade to no-thumbnail; derive sheet geometry from the image
  (rules 8, 9).
- First connection (2.6, `06` section 6.1): a stream that has not reached READY since it was prepared surfaces the
  section 5.4 dialog at the first 15 s buffering-without-ready timeout and spends no watchdog recovery; the section 6
  budget applies after the first READY only, and a user Retry is a new prepare. The phone's `StreamStallWatchdog`
  bounds the first connection as section 6.1 requires.
- Live stream closed by its server (2.7, `06` section 2.4): a live radio stream that its server closes cleanly, after
  it has played and while it is live or of unknown duration, is reconnected with the section 5.2 ladder; a
  known-duration item that ends is a finished track and a broadcast is outside the rule. S4598 carries it.
- Offline health sweep and grid frame capture (2.8, `05` sections 9.2 and 16): "Check channels" needs one active
  transport and, started with none, shows the no-network toast and probes nothing; a probe or a grid frame capture
  that fails after the device lost its transport is not written, and the sweep ends there, so a stored OK is never
  replaced by the device's own outage. A failure while a transport is up is still written UNKNOWN. S4617 carries it.
- Inline audio failure (2.10, `06` section 2.5): both inline backends classify a playback error with the
  section 5.3 rule and the HTTP status, surface a hard failure (a 4xx other than 429, an untrusted TLS certificate)
  at once as the section 5.4 dialog, and retry a transient one with the section 5.2 ladder. Not held yet: a dated
  exception in the catalog registry, carrier S4641.
- A change to a column, an asset name or an atlas ceiling is agreed in the catalog first.

## Where it lives here

- Producer: `scripts/streams/collect-stream-candidates.ps1` and `scripts/streams/modules/`; the
  operating page is `delivery/stream-catalog/README.md`; the asset-name gate is
  `scripts/quality/assert-stream-asset-revisions.ps1`.
- Phone consumer: `app_v2/.../domain/usecase/streams/ImportStreamCatalogUseCase.kt`,
  `app_v2/.../data/repository/StreamCatalogCsvParser.kt`,
  `app_v2/.../data/repository/StreamSourceRepository.kt` (`mergeCatalog`),
  `app_v2/.../domain/usecase/streams/StreamMediaKindClassifier.kt`,
  `app_v2/.../core/streams/FaviconAtlasSlicer.kt`, `app_v2/.../data/delivery/ArtworkManifestClient.kt`,
  `app_v2/.../ui/streams/StreamSourceAdapter.kt`,
  `app_v2/.../ui/dialog/helpers/StreamPropertiesFormatter.kt`.
- Phone probe and capture (2.8): `app_v2/.../ui/streams/helpers/StreamHealthProbeManager.kt`,
  `app_v2/.../ui/streams/helpers/StreamFrameSnapshotManager.kt`, the refresh action in
  `app_v2/.../ui/streams/StreamsActivity.kt`, and the write rule `RecordStreamPlayOutcomeUseCase.isProbeResultWritable`.
- Watch consumer: `wear/.../domain/usecase/ImportWearStreamCatalogUseCase.kt`,
  `wear/.../data/repository/WearStreamCatalogCsvParser.kt`.
