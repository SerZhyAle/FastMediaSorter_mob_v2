---
name: stream-catalog-all-live-channels
description: Owner wants ALL live streams (incl. grey-area iptv-org Live TV) in the shipped catalog; do not gate publish on the README legal scope
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
