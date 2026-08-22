# S1840 Research - Publisher Boundaries and Test Harness

**Date:** 2026-08-20

## Measurements

- `scripts/streams/collect-stream-candidates.ps1` is 3,501 lines and 205,703 bytes.
- The script declares approximately 70 top-level functions and 64 parameters.
- No stream publisher test file exists under `scripts/`; only unrelated PowerShell test suites are present.
- Pester 3.4.0 is installed at `C:\Program Files\WindowsPowerShell\Modules\Pester\3.4.0`.

## Responsibility boundaries

- Shared helpers and CSV/schema operations: URL classification, title/duration formatting, backups, CSV output, topic mapping.
- Probing: header liveness, deep signal, provider balancing and media-kind detection.
- Candidate discovery: radio-browser, iptv-org, webcam and community source adapters.
- Artwork: favicon cache, favicon atlas, channel-preview capture/atlas, stream-logo atlas.
- Delivery: tile packs, manifests, catalog ZIP assembly, release upload and mode orchestration.

The existing script uses script-scoped state and a shared `$ua`/`$Schema` contract. Dot-sourced module files can preserve that state and the existing parameterized entry point while reducing the main file below the 1,500-line ceiling. The module boundaries must be loaded in dependency order and must not introduce a second CLI.

## Contract constraints

- Preserve `streams.csv` as ZIP entry 0.
- Preserve the 19 header-named CSV columns and append-only schema policy.
- Preserve favicon and preview atlas geometry, byte budgets and rollback behavior.
- Preserve default parameters, mode switches and the `gh release upload .. --clobber` publication target.
- Keep the Windows/GDI+ requirement explicit; tests must not require network access, `gh`, ffmpeg, or GDI+.

## Test seam

Pester tests should load the smallest module or a test fixture with deterministic input and cover:

- URL format/protocol classification and canonical topic mapping.
- Prune-status normalization and conservative prune rules.
- Atlas geometry and budget predicates using synthetic metadata or temporary files.
- ZIP entry order/name and favicon-index-without-atlas refusal through a stubbed publish boundary.

Network probes, source APIs, ffmpeg capture, GDI+ decoding and real GitHub upload remain integration/manual concerns and are excluded from unit tests.

## Resolved research items

- **Testing framework:** Pester 3.4.0 is available; use the repository's `*.Tests.ps1` convention.
- **Module strategy:** dot-sourced PowerShell modules preserve the current CLI and script-scoped state with the smallest compatibility risk.
- **Feature documentation:** no user-visible Android capability changes; `docs/FEATURES*` and `ALL_FEATURES` are unchanged.
