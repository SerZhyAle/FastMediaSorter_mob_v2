# PHASE 02 — Catalog Sync + Dev Log

**Spec:** S0077
**Files:** `dev/CATALOG/app_v2.jsonl` (catalog scan/render), `dev/CHANGELOG.md` (via script)

---

## Pre-condition

Phase 01 complete and build passing.

---

## Step 2.1 — Catalog sync for `app_v2`

`NetworkThumbnailExtractionPolicy.kt` was modified (inline `object`, no class rename). Run scan + render:

```powershell
pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
```

Expected: no errors; `dev/CATALOG/app_v2.md` regenerated.

---

## Step 2.2 — Dev log entry

```powershell
.\scripts\add_to_dev_log.ps1 `
  "app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkThumbnailExtractionPolicy.kt" `
  "NetworkThumbnailExtractionPolicy" `
  "S0077: added vob/m2ts/mts/m2t/ts/ifo/bup to BLOCKED_EXTENSIONS — prevents NoModelLoaderAvailableException on optical-disc network browse"

.\scripts\add_to_dev_log.ps1 `
  "app_v2/src/main/res/values-ru/strings.xml" `
  "strings-ru" `
  "S0077: added thumbnail_unavailable_network_format RU translation (S0063 gap)"

.\scripts\add_to_dev_log.ps1 `
  "app_v2/src/main/res/values-uk/strings.xml" `
  "strings-uk" `
  "S0077: added thumbnail_unavailable_network_format UK translation (S0063 gap)"
```

---

## Step 2.3 — Update spec status to `Implemented`

```powershell
pwsh -File scripts/spec_catalog/update.ps1 -Id S0077 -Status "Implemented"
```

---

## Phase Completion Checklist

- [ ] `dev/CATALOG/app_v2.md` updated (timestamp newer than Phase 01 edit).
- [ ] `dev/CHANGELOG.md` has 3 new rows for S0077.
- [ ] Spec status = `Implemented`.
