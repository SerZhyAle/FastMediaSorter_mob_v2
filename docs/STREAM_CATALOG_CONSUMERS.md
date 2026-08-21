# Stream catalog: external consumers and publishing discipline

Who outside this repository reads our published stream-catalog artifacts, what each of them has pinned in its own code, and which of the contract rules is actually protected by a check.

This document does **not** describe the catalog format. That description lives in `dev/handoff/streams-source-spec/`, and `01_delivery_contract.md` in particular; this file names the consumers and the obligations, and points at that set rather than repeating it.

Everything published here is produced by one offline CLI, `scripts/streams/collect-stream-candidates.ps1`,
which loads the implementation modules under `scripts/streams/modules/`.

---

## Why this file exists

A rule that lives only in correspondence is not a rule. Most of the obligations below break silently: nothing fails in our build or our publication, and the damage appears in somebody else's application - another station's logo on a channel, a lost pin, a failed catalog update. Before this file, the only way to answer "may this asset be renamed" was to write to the maintainer and wait.

---

## Consumer: StreamsPlayer (Windows)

- **Reads:** `stream-catalog.zip` (the `streams.csv` entry and the `favicon-atlas.png` entry), plus the revisioned channel-preview assets attached to the same GitHub release.
- **Reader component:** `StreamBankReader`, in the consumer's repository.
- **Pinned numbers:** `StreamBankReader.MaximumAtlasBytes` = 30 MiB. This is a second independent literal for the same number our publisher holds as `$MaxAtlasBytes = 31457280`. The consumer has asked to reference a shared declaration once one exists; the ticket owning that number is S1827.
- **Pinned numbers, channel-preview sheet:** 48 MiB, declared by the consumer for the preview sheet specifically. Our publisher holds it as `$MaxPreviewAtlasBytes = 50331648` (S1831). **This is a different contract from the 30 MiB above and the two must not be conflated** - 30 MiB is the favicon atlas, and applying it to the preview sheet would refuse a legal build, while applying 48 MiB to the favicon atlas would let an over-cap one ship and wipe every user's icons. Until 2026-08-20 the number lived only in the S1828 correspondence and the preview sheet was checked against nothing at all; a reader consulting this table found the 30 MiB row and applied it to the wrong asset. The consumer declared no limit on tile count or row count, and none is assumed: the 2026-08-20 build is 2830 tiles in 84 rows at 15.9 MiB.
- **Not pinned, and confirmed only by absence:** whether anything on the consumer's side depends on the former 2040-tile / 60-row shape of the preview sheet. No boundary of that kind was ever declared, and the sheet is now taller than it has ever been, so a dependency would show up as a rendering fault rather than as an error. Worth one confirming question the next time the consumer is in contact (S1831 §6.1).
- **Behaviour when the atlas is over the ceiling:** the consumer discards the incoming atlas, keeps the previously installed sheet, and applies the new CSV indices to it. The result is not missing icons but wrong ones - a channel shows another station's logo, and the application looks healthy. Since 2026-08-19 the consumer logs `CATALOG ATLAS | bank_atlas=absent`, but shows the user nothing.
- **Acceptance signal:** one line saying the upload happened is enough. The consumer then runs `dotnet run --project tools/StreamsPlayer.CatalogHarness -- artifacts/favicon-sample.png`, which downloads the live asset with the same limits the product uses and reports whether `streams.csv` is entry zero, how many rows parsed, the atlas byte count, the maximum tile index, and cuts a real tile to a file for visual inspection.
- **Parser tolerance:** an unrecognised `access` token reads as "open", so restoring a producer for that column needs no release on the consumer's side.

## Asset with no declared consumer: the stream-logo sheet

- **Read by:** nobody outside this repository has declared themselves. In-app the readers are `StreamLogoAtlasSlicer` and `StreamLogoAtlasStore`, both here.
- **Pinned numbers, consumer side:** none. The app declares tile width, tile height and column count and derives the row from the tile index. It declares no row count and no byte ceiling, so sheet height and tile count are ours to change without notice - which is why S1841 could retire the row cap without touching a contract.
- **Producer-side ceiling, ours alone:** `$MaxLogoAtlasBytes = 50331648` (48 MiB), added 2026-08-20 by S1841. This is **not** a consumer contract and nothing outside is bound by it. It exists because until that date the logo sheet was checked against nothing at all: `Assert-AtlasBudget` has one call site and guards the favicon atlas, and `$MaxPreviewAtlasBytes` guards the preview sheet, so a logo sheet of any size reached publication unopposed. Treat it as a regression alarm, not a budget: the 2026-08-20 rebuild measures 16.0 MB at 4148 tiles, and even the format ceiling's 7080 tiles would cost only about 28.6 MB.
- **Capacity, and what actually bounds it:** the WebP dimension limit of 16383 px, which at a 136 px tile is 120 rows = 7080 tiles. Before S1841 a self-imposed 60-row cap silently dropped everything past 3540 tiles; measured on the day it was found, 608 logos reaching 1593 channel urls were being dropped with their artwork already in the cache, and the run still exited clean.
- **Behaviour over the ceiling:** the publisher now refuses. Over the format limit it throws before allocating the bitmap, naming the tile count, the rows needed, what fits and how many stations would go without; over the byte ceiling it throws after the encode, before anything is published. Neither case trims and publishes a partial sheet any more.
- **When a consumer does appear:** it gets a row in the pinned-assets table below, and the 48 MiB above stops being ours to pick and becomes a number to agree on.

---

### Pinned assets

Names the consumer has hard-coded. Its clients fetch these addresses and do not roll forward on their own, so none of these names may be deleted.

`Coverage` says whether our publisher still produces the name: `default` means the current revision defaults reproduce it on every run, `frozen` means it is deliberately no longer republished and must survive untouched.

<!-- pinned-assets:begin -->

| Asset base name | Pinned revision | Coverage | Consumer | Reason |
|---|---|---|---|---|
| `channel-preview-atlas` | v1 | frozen | StreamsPlayer | Fielded clients fetch `channel-preview-atlas-v1.webp` from a constant in their code and do not roll forward. Deleting it breaks the preview download offer for everyone who has not imported yet. |
| `channel-preview-coords` | v1 | frozen | StreamsPlayer | Coordinate sheet belonging to the `v1` atlas. Useless apart from it and equally pinned. |
| `channel-preview-atlas` | v3 | default | StreamsPlayer | The consumer raised its preview constant from v1 to v3 on 2026-08-20; both revisions are now live in the field. Produced by `$SheetRev`. |
| `channel-preview-coords` | v3 | default | StreamsPlayer | Coordinate sheet belonging to the `v3` atlas. Produced by `$CoordsRev`. |
| `stream-logo-atlas` | v3 | default | none declared | No external consumer has declared a pin. Listed so that a future pin has a row to land in rather than a rename to discover. |
| `stream-logo-coords` | v3 | default | none declared | As above. |

<!-- pinned-assets:end -->

The revision substitution happens in `Invoke-PublishChannelPreviewAtlas` and `Invoke-PublishStreamLogoAtlas`, from the two script parameters `$SheetRev` and `$CoordsRev`. Both default to `v3`.

`scripts/quality/assert-stream-asset-revisions.ps1` reads the block above and refuses a publication in which a `default` row stopped being produced - which is what a raised revision default looks like before anybody decides what happens to the revision it displaced.

---

## Contract rules and their verdicts

One row per obligation the consumer named. `Verdict` is one of three tokens:

- `checked` - a mechanical refusal exists.
- `by-construction` - the code cannot currently produce a violation, but nothing asserts it, so a refactor may remove the property silently.
- `unprotected` - nothing enforces it at all.

The `Address` column is where the verdict is re-checked when someone returns to this table in a year. A row whose address no longer exists is a stale row, and that is the failure this column exists to make findable.

<!-- invariants:begin -->

| Invariant | Verdict | Address |
|---|---|---|
| `streams.csv` must be entry number zero in the ZIP, not merely present | checked | `Assert-CatalogZipEntries` in `scripts/streams/modules/StreamPublisher.Delivery.ps1`, called by the CLI after packing |
| The favicon atlas must not exceed 30 MiB | checked | `Assert-AtlasBudget`, called from `Build-FaviconAtlas`; the number is also S1827's subject |
| The atlas and the CSV must come from one build, because `favicon_index` is an offset into the sheet shipped in the same ZIP | by-construction | `Set-FaviconIndices` rewrites the indices and the PNG in a single call, so the two cannot diverge |
| An absent icon must be written as an empty `favicon_index`, never as `0` | by-construction | The ternary in `Set-FaviconIndices`; `0` is assigned only to a real first tile |
| The ZIP entries must be named exactly `streams.csv` and `favicon-atlas.png` | by-construction | Default parameter names in the publisher; no path recomputes them |
| Columns are matched by header name, so a column may be added or reordered but never renamed | by-construction | The single `$Schema` literal, used by every write path |
| Published assets are never deleted, only overwritten in place | by-construction | No script in this repository calls `gh release delete-asset`; every publication uses `gh release upload --clobber` |
| The artwork manifest always carries a sha256 for each entry | by-construction | `Publish-TilePacks`, which computes the hash on every run |
| The archive must stay at or below 128 MB | checked | `Invoke-PublishCatalog` compares the packed archive against a declared ceiling and refuses before `gh release upload` (S1835) |
| The atlas entry name in the ZIP must match exactly | checked | `Assert-CatalogZipEntries` in `scripts/streams/modules/StreamPublisher.Delivery.ps1` tests `streams.csv` and `favicon-atlas.png` by equality, not by suffix (S1835) |
| Rows with an empty `name` or `url` must not ship | checked | `Invoke-PublishCatalog` counts them before packing and refuses; it deliberately refuses rather than stripping, because stripping is a silent prune (S1835) |
| The channel-preview sheet must not exceed 48 MiB | checked | `Build-ChannelPreviewAtlas` measures the encoded sheet against `$MaxPreviewAtlasBytes`, deletes it and refuses; checked where the sheet is made, not next to the upload (S1831) |
| The channel-preview sheet's height follows the tile count and is never truncated to fit | checked | `Build-ChannelPreviewAtlas` throws when the sheet would exceed the 16383 px WebP dimension limit, naming how many channels would be left uncovered. Before S1831 it silently dropped the overflow with a `Write-Warning` and published a partial sheet - 877 of 2917 video channels had no tile for that reason alone |
| A consumer must not assume a preview-sheet row count | by-construction | The width is fixed at 34 columns and the height is derived; the app's own `ChannelPreviewAtlasSlicer` declares only `TILE_W`/`TILE_H`/`COLS` and takes the atlas dimensions as arguments, so it needs no row count either |

<!-- invariants:end -->

As measured on 2026-08-20, no gate under `scripts/quality/` read the publishing script at all, so `.\a.ps1 fg` caught a regression in none of the fourteen rules above. `assert-stream-asset-revisions.ps1` is the first one that does, and it covers the pinned-revision rule only.

The three rules that carried `unprotected` on the same day were closed by S1835 and now refuse inside `Invoke-PublishCatalog` rather than at a static gate - they judge the artifact a run actually produced, which a static gate cannot see. S1831 added two more of the same kind inside `Build-ChannelPreviewAtlas`, for the same reason: a sheet's byte size and pixel height exist only once a run has built it. Seven rules remain `by-construction`: nothing asserts them, so a refactor can remove the property silently, and each stays a candidate for its own check.

---

## Findings that read wrong from the outside

**An empty `access` column is a run's outcome, not a missing producer.** The consumer observed no non-empty `access` in any of 17 628 rows and concluded the producer had disappeared. The conclusion is wrong. `Invoke-SignalProbe` assigns `geo` on HTTP 403 and 451, and `Invoke-CatalogMaintenance` moves it into the column on a run made with `-DeepSignal` and without `-Limit`. A snapshot without such responses therefore shows an empty column while the producer is intact. For calibration: even when the column was being filled, it covered 76 rows out of 19 534. Recorded here because the next investigation would otherwise repeat the same mistake.

**Our side no longer matches a channel's address byte for byte (S1832).** Until 2026-08-20 the app filed a user's pin, its position in the pinned list and the channel's play history under the row a catalog import created, and that row was matched by the exact `url` string. It now derives an identity from the address and files everything under that instead. The identity folds scheme case, host case, a trailing slash, a default port written out, and the difference between `http` and `https` - so a channel republished over `https` having previously been `http` keeps everything the user gave it. On the 2026-08-20 bank, 58 groups of published rows fold onto a shared identity this way.

What that does **not** change is what the publisher owes: an address whose host or path really changes is a different channel to us, and the user loses the pin, the position and the history filed under the old one. The consumer's own framing - that addresses have to be more stable than names - still holds, and the tolerance above buys back only the cosmetic differences. Nothing about the CSV, the column set or the published asset names changed; the address is still what we key on, only compared more forgivingly. The reader is `StreamChannelIdentity` in `app_v2/src/main/java/com/sza/fastmediasorter/data/util/`.

**`artwork-manifest.json` is an extension point, not a declared part of the contract.** It carries a sha256 and a stamp and would serve as the invalidation handle the consumer says is missing, but no consumer reads it yet. Declaring it would bind us in any future format change, so it stays undeclared until a reader exists. The decision is held by S1835.

---

## Related tickets

- S1827 - owner of the 30 MiB atlas budget, the number duplicated between our publisher and `StreamBankReader`.
- S1830 - why a republication removed 1 906 rows; the attribution to the 2026-08-19 geo ruling turned out to be wrong.
- S1835 - the three invariants above marked `unprotected`, and the open question about the artwork manifest.
- S1826 - orphaned outcome rows left by pruning.
- S1820 - catalog import limits on the application side.
