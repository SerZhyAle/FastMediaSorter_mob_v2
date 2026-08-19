---
name: streams-player-catalog-consumer
description: StreamsPlayer (Windows) is a second consumer of stream-catalog.zip whose failure modes differ from the app's - a missing atlas gives WRONG icons there, not absent ones
metadata:
  type: project
---

`delivery-so-v1` has a second consumer besides this app: **StreamsPlayer**, a Windows product in
another repository, maintained by the owner. Its maintainer sent a written contract note on
2026-08-20; the full text plus a point-by-point reconciliation against this working tree lives in
`PLAN/S1828_stream-catalog-external-consumer-contract.md` §0.

**Why this matters:** the two consumers fail *differently* on the same bad publish, and the
publisher's guards were written only for this app's failure mode.

- **Atlas absent or over 30 MiB.** This app: `FaviconAtlasStore.write(null, coords)` wipes every
  favicon - visible, diagnosable. StreamsPlayer: **keeps the previously installed atlas and applies
  the new CSV's indices to it**, so channels show *other stations' logos* and the app looks healthy.
  That makes `-AllowFaviconlessPublish` (the documented escape hatch on the S0925 guard) far more
  expensive than its own error text claims - it now ships silently wrong icons to a third party.
  Their ceiling `StreamBankReader.MaximumAtlasBytes` equals our `$MaxAtlasBytes = 31457280` by
  coincidence of two independent literals in two repositories, not by a shared contract (S1827).
- **`streams.csv` not ZIP entry 0.** Hard `InvalidDataException` there, whole update aborts. Our side
  already asserts this after packing, in `Invoke-PublishCatalog`, before the upload - the only item in
  their whole note that is mechanically gated here rather than merely agreed.
- **Pinned asset names - now TWO revisions.** They fetch the preview sheet by a compiled-in constant
  that does not self-upgrade, and as of 2026-08-20 they run both `-v1` and `-v3`: keep
  `channel-preview-atlas-v1.webp` + `channel-preview-coords-v1.json` **and**
  `channel-preview-atlas-v3.webp` + `channel-preview-coords-v3.json`. Never delete a revisioned asset
  from the release. No script here calls `gh release delete-asset` (verified 2026-08-20), so the only
  real risk is a manual cleanup.
- **`--clobber` is delete-then-upload**, so `stream-catalog.zip` 404s for a window. Their client shows
  a visible "could not update" and offers its bundled snapshot; ours falls back quietly. Publish in a
  quiet hour.

**How to apply:** after any `stream-catalog.zip` publish, tell the owner so the other side can run its
verification harness (`dotnet run --project tools/StreamsPlayer.CatalogHarness -- <sample.png>`) - it
checks entry order, row count, atlas bytes and max index against the live asset and cuts a real tile
to look at. Treat "publish the catalog" as a two-party operation, not a local chore. Related:
[[stream-catalog-atlas-publish]], [[stream-favicon-atlas-delivery]], [[stream-artwork-tile-packs]],
[[stream-catalog-publish]].
