---
name: catalog-set-ps1-stops-on-error
description: dev/CATALOG/scripts/set.ps1 throws and aborts batch when path is missing - wrap in try/catch
metadata:
  type: project
---

`dev/CATALOG/scripts/set.ps1` throws an exception (`No record found for path '<path>' in <module>`) and aborts the entire pipeline whenever the path argument doesn't match a catalog entry exactly. In a batched ForEach loop (e.g. filling role/status for N new classes), one missing path stops all subsequent entries.

**Why:** Encountered during S0200 audit fixup (2026-05-17) - a batch of 18 entries terminated mid-way because cloudEnabled source-set classes weren't yet in the catalog (separate scan.ps1 limitation - see [[catalog-scan-source-sets]]). After fixing the scan and re-running, set.ps1 still failed atomically on the next missing path.

**How to apply:** When batch-updating multiple catalog records via `set.ps1`:
- Wrap each call in `try { & 'dev/CATALOG/scripts/set.ps1' ... } catch { Write-Warning "Skipped: $($_.Exception.Message)" }` so a missing path skips, not aborts.
- Always run `scan.ps1` first to populate fresh entries before batch-filling `role`/`status`.
- For very large batches, prefer the inline ForEach with try/catch over a chain of standalone calls.

**Path format (also stops the script if wrong):** `-Path` must use the catalog's relative form starting from the Kotlin package root (`com/sza/fastmediasorter/...`), NOT the repo-root form (`app_v2/src/main/java/com/sza/...`). Same error message ("No record found for path ..."). Confirmed S0002 Wave 47 (2026-05-17). Also: `-Status` is a closed enum - valid values are `new,tested,legacy,todo,unknown`; words like `active` fail validation.
