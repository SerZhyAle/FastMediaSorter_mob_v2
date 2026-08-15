# S1245 - StreamLogoAtlasSlicerTest still asserts the retired 135x135 / 60-col grid

**Status:** Archived
**Priority:** 55

## 0. Raw capture

Found on 2026-07-28 while verifying S1220 (atlas slicer crash guard). Out of scope of that ticket - S1220 moves a bounds check inside a `try` and touches no constant - so parked per CLAUDE.md 3.1.

`app_v2/src/test/java/com/sza/fastmediasorter/ui/streams/StreamLogoAtlasSlicerTest.kt` fails 3 of its 4 cases:

```
tests="4" skipped="0" failures="3" errors="0"

rectFor matches the 135x135 60-col contract
    expected:<Rect(0, 0 - 135, 135)> but was:<Rect(0, 0 - 136, 136)>
isInBounds rejects negative and over-range indices on the published sheet
    last tile of the last row fits
rectFor column wraps at COLS
    expected:<270> but was:<272>
```

Reproduce with `pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests "*AtlasSlicerTest"`. The sibling `ChannelPreviewAtlasSlicerTest` (3/3) and `FaviconAtlasSlicerTest` (5/5) are green, so this is one stale class, not a broken harness.

## 1. Which side is wrong - checked, not assumed

The production side is right and the test is stale. Two independent sources agree on 136 x 136 / 59 columns:

- `StreamLogoAtlasSlicer.kt` companion: `TILE_W = 136`, `TILE_H = 136`, `COLS = 59`.
- The offline packer that writes the sheet, `scripts/streams/collect-stream-candidates.ps1`: `$script:LogoTileW = 136`, `$script:LogoTileH = 136`, `$script:LogoCols = 59`.

App and packer are the two halves of one grid contract; if they agree, the sheet on disk really is 136/59 and the test is asserting a geometry that no longer exists anywhere.

The Kotlin comment records why the size moved off 135: the sheet is lossy WebP, which is always 4:2:0, so an odd tile edge would put every second tile boundary mid-chroma-block and bleed one tile's edge colour into its neighbour. That is a deliberate change from S1201, and its test was simply not carried along.

**Do not shortcut this to "make the numbers match".** Rewriting assertions until they pass is how a real geometry bug gets erased. The reason it is safe *here* is specifically that the packer corroborates the app; that check is the work, and it is done.

## 2. Not purely a find-and-replace

- `rectFor matches the ..` and `rectFor column wraps at COLS` are mechanical: 135 -> 136, and the wrap expectation 270 -> 272.
- `isInBounds rejects negative and over-range indices on the published sheet` also encodes the *sheet* size, not just the tile size - the last-tile-of-last-row case has to be recomputed from the real published sheet dimensions, which come from the packer's row budget, not from `COLS` alone. Get that number from the packer rather than from arithmetic on the failing assertion.
- The class name and its KDoc both say "135x135 60-col contract" and must move too, or the next reader trusts the wrong number.

## 3. Why it matters beyond a red bar

A permanently-failing class trains everyone to read `testStandardDebugUnitTest FAILED` as background noise. It also means the logo grid currently has **no** working regression test: if `TILE_W`/`COLS` drifted again, or drifted apart from the packer, these tests would keep failing exactly as they do now and nobody would learn anything new.

## 4. Related

- S1201 - introduced `StreamLogoAtlasSlicer` and the 136/59 grid.
- S1220 - the crash guard whose verification surfaced this.
- S1244 - the full suite dies on OOM before reaching `ui.*`, which is why these failures were invisible in `.\a.ps1 fu` and only appeared under a `--tests` filter.

## Last Audit

**Date:** 2026-07-28. **Verdict:** Verified.

- Test class rewritten to the live 136x136 / 59-col contract, geometry expressed through the
  slicer constants instead of literals so the numbers cannot silently split again.
- §2's bounds case now asserts against the packer's row BUDGET (`$script:LogoMaxRows` = 60), not
  a point-in-time published sheet size - the published height varies per catalog publish
  (`rowsNeeded = ceil(count / cols)`), which is exactly how the old test went stale.
- §3's "no regression test" gap closed for real: a new packer-parity test reads
  `LogoTileW/LogoTileH/LogoCols/LogoMaxRows` out of `collect-stream-candidates.ps1` and compares
  them to the app constants - either side drifting turns the suite red.
- Run: `check-standard-fast -Mode Unit -Tests ..StreamLogoAtlasSlicerTest` - tests=5 failures=0
  (19:41); scoped detekt PASS. Class KDoc updated to the new contract with the chroma rationale.
