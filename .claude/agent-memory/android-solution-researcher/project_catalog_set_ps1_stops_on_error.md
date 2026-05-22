---
name: catalog-set-ps1-stops-on-error
description: dev/CATALOG/scripts/set.ps1 throws and aborts batch when path is missing - wrap in try/catch
metadata:
  type: project
---

`dev/CATALOG/scripts/set.ps1` throws an exception (`No record found for path '<path>' in <module>`) and aborts the entire pipeline whenever the path argument doesn't match a catalog entry exactly. In a batched ForEach loop (e.g. filling role/status for N new classes), one missing path stops all subsequent entries.

**Why:** Encountered during S0200 audit fixup (2026-05-17) - a batch of 18 entries terminated mid-way because cloudEnabled source-set classes weren't yet in the catalog (separate scan.ps1 limitation - see [[catalog-scan-source-sets]]). After fixing the scan and re-running, set.ps1 still failed atomically on the next missing path.

**How to apply:** The research agent does NOT call `set.ps1` - it is a write-mode tool. The relevant takeaway when reading catalog state:
- A missing/empty `role` or `status` field on a catalog record may simply mean a batched `set.ps1` aborted before reaching that class, not that the class is intentionally unclassified. When citing role/status in a research report, treat empty values as "unknown - possibly a tooling gap", not as authoritative.
- `-Status` is a closed enum (`new,tested,legacy,todo,unknown`) - any value seen outside this set in the catalog is a data error worth flagging in the report.
- `-Path` always uses the catalog's relative form starting from the Kotlin package root (`com/sza/fastmediasorter/..`), NOT the repo-root form. When citing a class location for a writer-agent to fix later, use the package-root form so the downstream `set.ps1` call does not abort.
