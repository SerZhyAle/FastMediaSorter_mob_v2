# Research 03 - Print as a Send-to receiver + fate of the overflow print item

**Strategic §6 item:** 3
**Status:** Resolved

## Question

How is Print gated in the unified «Send to..» menu, and should the isolated overflow print item be removed?

## Finding

- `PrintShareTargetHandler.isSupportedBy(activity) = activity is SharePrintHost`. Print appears in the «Send to..»
  menu only when the host Activity implements `SharePrintHost` (S0459 ADR-10).
- The in-app `PlayerActivity` implements `SharePrintHost.printMediaFile(...)` and dispatches to its print manager.
- The standalone image host (`PhotoVideoStandaloneActivity`) does NOT implement `SharePrintHost`, so Print is hidden
  in its «Send to..». It instead prints via an isolated overflow item `menu_print` -> `printCurrentImage()` using
  `androidx.print.PrintHelper` on the displayed bitmap.
- The overflow menu `overflow_menu_standalone_player.xml` is shared by several standalone hosts (image/video/audio/
  document/text). `menu_print` is made visible per-host (`findItem(menu_print).isVisible = hasBitmap` in the image host).

## Decision

- The standalone image host implements `SharePrintHost`; `printMediaFile(...)` reuses the existing bitmap print path
  (returns false when no rendered bitmap is available, so the menu gate / dispatch fails cleanly).
- The isolated overflow print item is dropped FOR THIS HOST only: hide `menu_print` and remove its click branch in the
  image host. The shared menu XML keeps the item (other hosts that print documents still reference it).

## Implication for the plan

Print becomes a single-source receiver in «Send to..» for the standalone image host; the duplicate overflow entry is
removed there. No change to other hosts.
