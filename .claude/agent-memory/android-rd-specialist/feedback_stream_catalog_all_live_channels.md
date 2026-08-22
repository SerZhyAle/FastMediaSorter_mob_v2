---
name: stream-catalog-all-live-channels
description: Owner wants EVERY reachable channel that carries signal (grey-area iptv-org + -/-) in the shipped catalog; legal-scope filter removed from code+README; only defunct/header-gated/dead dropped
type: feedback
---

Owner wants **all live channels** in `delivery/stream-catalog/streams.csv` - including the bulk
`iptv-org` "Live TV" entries that `delivery/stream-catalog/README.md` labels grey-area / "Explicitly
excluded". Treat the collector's appended `alive` rows as wanted; never hold, gate, or "legality-audit"
the catalog append/upload in `/spec-prerelease` Step 0 or elsewhere.

**Why:** On the 2026-06-22 prerelease sweep the collector grew the catalog 435 -> 2303 (+1868 iptv-org
Live TV). I read the README's legal-scope section as policy and parked S0627 to block publishing them as
a "legal-scope regression". The owner rejected the premise outright ("мне нужны все живые каналы .. убери
этот запрет"), so S0627 was archived, the +1868 append kept, and `stream-catalog.zip` re-uploaded to the
`delivery-so-v1` release. The README legal-scope text is documentation, not the operative policy.

**How to apply:** In Step 0, run the collector append + health-probe and re-package/upload the asset
(`gh release upload delivery-so-v1 temp/stream-catalog.zip --clobber`). Surface the `+N appended` count
on the report line, but do NOT propose a legality hold or a "grey-area" gate. Pruning stays the only
human-gated, conservative action (dead-from-here may be alive on a user's device).

**Update 2026-06-22:** owner escalated the policy from "don't gate publish" to "remove the filter
entirely" ("поменяй политику! все какие каналы существуют хочу"). The legal-scope filter is now gone
from both the code and `delivery/stream-catalog/README.md` (was "## Legal scope" -> now "## Inclusion
policy"): the catalog keeps **every reachable channel that carries signal**, and the owner explicitly
opted -/- in too (the iptv-org `is_-` skip in `Get-IptvCandidates` was removed). Only three
drops remain, all because the stream cannot play: `closed`/defunct, header-gated (referrer/User-Agent
the app can't supply), and confirmed-dead. --in-a-Play-Store-downloaded-catalog is a content-rating
exposure the owner accepted knowingly - do not re-raise it as a blocker.

Liveness now has two depths in `collect-stream-candidates.ps1`: the default header probe (status of the
playlist URL only) and `-CatalogOnly -DeepSignal`, which pulls a few KB of real media body (HLS
master->media->first segment) so an HLS master that 200s but serves no segment is correctly `dead` -
the "declared but not playing" case. Use `-DeepSignal` for accurate prune decisions; `-Throttle` 48+
for the full 2300-row sweep.

**Update 2026-07-19 (S1117) - policy changed, geo split out:** the "unknown is never removed" rule
above is now SUPERSEDED for the deep-signal prune path. `Invoke-SignalProbe` gives HTTP 403/451 its own
verdict `geo` (region-locked from the build machine, may still play in-region), separate from
`dead`/`unknown`. On an un-pinned `-CatalogOnly -DeepSignal -PruneDead` run, prune now widens to
`dead`,`unknown` (non-geo failures: timeout/SSL/401/5xx) and KEEPS `geo` rows, stamping them
`access=geo` in a new trailing CSV column (col 19). Header-only prune stays conservative (`dead` only).
The owner's rule: tag geo only where region-lock is proven (403/451), delete every other non-live row.
This scrubbed the catalog 2691 -> ~2182 across two prunes (375 dead, then 134 dead+unknown), 42 geo kept.
App side (S1117 phase B): `StreamSourceEntity.access` (Room v42 migration 41->42, additive nullable),
parser reads the `access` cell by name, `StreamSourceAdapter` shows a globe "region-locked" chip in the
Streams list (UI-clarify: full list only, icon+text, no playback fallback). Prerelease Step 0 now runs
the deep-signal report (alive/dead/geo/unknown) so ballast can't re-accumulate unseen.

**Update 2026-07-01 (S0805):** the discovery/append path now runs the deep-signal probe by DEFAULT as a
second stage after the header probe - only header-alive candidates are re-probed for real media bytes,
and only signal-verified rows are appended, so pseudo-alive channels (playlist 2xx, no segment) can no
longer enter the shipped catalog on a routine collection run. Opt out with `-SkipDeepSignal` (fast
prowl); `-SkipLiveness` still skips both stages. Key asymmetry to preserve: **append = strict**
(deep-signal gate, don't let pseudo-alive in) but **prune = conservative** (still header-only + human
`-PruneDead`, since dead-from-here may be alive on a user's device). Do NOT auto-drive prune off deep
signal. Side-finding S0843 (RESOLVED 2026-07-02, Implemented): `Get-WebcamSeeds`
refreshed 3 dead seeds -> 12 deep-signal-verified 24/7 feeds (NASA dropped entirely - public akamai HLS
dead/403 after the NASA+ move; DW dwstream105 -> DW English amagi; + France24/AlJazeera/CGTN/InWonder/
WildEarth/RedBull/AKC/30A across Documentary/News/Science & Space/Outdoor). Re-verify seed URLs with
`-Axis webcam -PreviewOnly` before publish - CDN/akamai paths rotate.

**Update 2026-08-19 - policy REVERSED for geo, owner ruling:** "в нашем списке трансляций много таких,
которые существуют, но недоступны с моего адреса отсюда из Мальты - нужно их отфильтровать и не
публиковать. мне всё равно если это будет доступно пользователю из его страны - пусть добавляет
вручную." So the shipped bank is now defined as **what plays from the build machine**, not as
"everything that exists somewhere". The two rules recorded above are superseded: `geo` rows are no
longer kept-and-tagged, and "dead-from-here may be alive on a user's device" is no longer a reason to
keep a row. Prune set becomes `dead`,`unknown`,`geo`.

**How to apply:** the machinery already supports it with no code change -
`-CatalogOnly -DeepSignal -PruneStatuses dead,unknown,geo [-PruneDead] [-Publish]`; pinning
`-PruneStatuses` sets `$script:PruneStatusesExplicit`, which disables the S1117 auto-widening and uses
exactly the pinned list. Dry-run first (omit `-PruneDead`) - it writes only
`temp/stream-catalog-liveness.csv` and prints "Would prune N", and the per-row prune listing is
thousands of lines, so redirect the whole run to a log file instead of reading it inline. Keep the
trailing `access` CSV column even though it will now always be blank - the column count is part of the
shared contract with the Windows consumer, and changing it is exactly the breakage being repaired
elsewhere. Consequence to schedule: the S1117 "region-locked" globe chip in the Streams list loses its
only producer and becomes dead code.

**Correction 2026-08-20 - the "no two-strike safety is needed" claim above was wrong, and it cost
1 321 live stations (S1830).** It read: "discovery re-appends a wrongly-dropped channel on the next
collection run, since only signal-verified rows are appended". Both halves fail. A re-appended row
gets a **new UUID**, so the user's pin and collection membership do not come back - the row returns,
the user's data does not. And re-appending depends on the channel being re-discovered by the axes,
which is not guaranteed for a bulk provider. What actually happened on the 2026-08-19 prune: of
1 906 removed rows, **1 512 carried the verdict `unknown`**, not `dead`; 1 321 of them were a single
provider (`*.stream.laut.fm`), and a sequential re-probe on 2026-08-20 got **200 on 25 of 25**. Two
identical deep-signal runs six minutes apart over the same 19 534 rows disagreed by ~95 rows, so
`unknown` is not a property of the channel at all - it is our probe under self-inflicted load
(`throttle 64` with no per-provider cap).

**How to apply:** treat a probe verdict as evidence about the probe, not about the channel. Before any
`-PruneDead` run that widens past `dead`, group the would-prune set by **registrable domain (eTLD+1),
not host** - per-station subdomains hide a bulk provider completely (laut.fm never rose above "17
rows" in a host histogram) - and re-probe a sample of the biggest group sequentially. The pre-prune
CSV is saved automatically by `Backup-IfExists` to `temp/streams.csv.<ts>.bak`; that file is the only
rollback that exists, and it restores the list but never the users' pins.
