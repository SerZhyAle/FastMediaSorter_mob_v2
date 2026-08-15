---
name: project_catalog_set_ps1_stops_on_error
description: dev/CATALOG/scripts/set.ps1 throws and aborts batch when path is missing - wrap in try/catch
metadata:
  type: project
---

`dev/CATALOG/scripts/set.ps1` throws an exception (`No record found for path '<path>' in <module>`) and aborts the entire pipeline whenever the path argument doesn't match a catalog entry exactly. In a batched ForEach loop (e.g. filling role/status for N new classes), one missing path stops all subsequent entries.

**Why:** Encountered during S0200 audit fixup (2026-05-17) - a batch of 18 entries terminated mid-way because cloudEnabled source-set classes weren't yet in the catalog (separate scan.ps1 limitation - see [[project_catalog_scan_source_sets]]). After fixing the scan and re-running, set.ps1 still failed atomically on the next missing path.

**How to apply:** When a coding step adds several new `.kt` files and you need to set `role`/`status` for each:
- Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` first so new classes appear in the catalog.
- Build the batch as a ForEach loop with try/catch around each call: `try { & 'dev/CATALOG/scripts/set.ps1' ... } catch { Write-Warning "Skipped: $($_.Exception.Message)" }` - one missing path then skips instead of aborting the rest.
- Pass `-Path` in the catalog's relative form starting from the Kotlin package root (`com/sza/fastmediasorter/...`), NOT the repo-root form (`app_v2/src/main/java/com/sza/...`) - the wrong form throws the same "No record found" error.
- `-Status` is a closed enum: `new,tested,legacy,todo,unknown`. Words like `active` fail validation.
- There is **no `-Class` parameter**. The record is selected by `-Path` alone, so a file declaring several classes has all of its records updated together - you cannot set a role for one class in a multi-class file.
- Confirmed S0002 Wave 47 (2026-05-17); `-Class` absence re-confirmed S1651 (2026-08-14).
