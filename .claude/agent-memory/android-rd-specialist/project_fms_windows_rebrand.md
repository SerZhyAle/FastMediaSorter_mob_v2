---
name: fms-windows-rebrand
description: Windows sibling app rebranded 2026-07 to "Fast Media Sorter for Windows"; repo folder, URLs, Store/winget listing stay FastMediaSorter_Lite / FastMediaSorter LITE deliberately
metadata:
  type: project
---

The Windows sibling project (`P:\WINDOWS\FastMediaSorter_Lite`) had a light rebrand in 2026-07: display name **FastMediaSorter LITE** -> **Fast Media Sorter for Windows**. Android-repo docs/site synced 2026-07-11 (index*.html, README*, docs/README*, FAQ*, HOW_TO* EN/RU/UK).

**Why:** owner's naming rule in that project's CLAUDE.md - the Microsoft Store listing, winget manifests, Inno installer, exe/mutex/registry/ProgID identifiers and the GitHub repo name are all FROZEN under the old identity (winget ARP correlation would break). Only the display name changed.

**How to apply:**
- Display name in prose: "Fast Media Sorter for Windows" (mention "formerly FastMediaSorter LITE" once per doc where install instructions appear - Store search still needs the old name).
- NEVER "fix" URLs `github.com/SerZhyAle/FastMediaSorter_Lite`, `serzhyale.github.io/FastMediaSorter_Lite/`, winget id `SerZhyAle.FastMediaSorter`, or Store listing "FastMediaSorter LITE" - they are intentionally the old name.
- Do not confuse with the Android **lite flavor** (`FastMediaSorter_lite_*.apk/zip`, `scripts/builders/build-lite-*.ps1`) - unrelated, keep as-is.
- Related: [[fms-companion-subproject]] (S0421 companion feature ported into this app).
