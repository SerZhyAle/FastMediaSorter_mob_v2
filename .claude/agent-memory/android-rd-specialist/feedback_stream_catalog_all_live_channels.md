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
