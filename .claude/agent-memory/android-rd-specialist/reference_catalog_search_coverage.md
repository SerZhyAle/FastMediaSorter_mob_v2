---
name: catalog-search-coverage
description: query.ps1 -Search is the feature-discovery entry point; role field 97% empty so semantic/synonym lookups miss
metadata:
  type: reference
---

`dev/CATALOG/scripts/query.ps1 -Search <term>` is the primary feature/where-does-it-live entry point - full-text over class name, path, `role`, `injected`, `constructorDeps`, and function names+descriptions. Reach for it FIRST, before Grep/Glob (Catalog-first rule).

**Coverage (app_v2, 2175 classes; role bootstrap applied 2026-07-15):**
- `role` is now **100% populated** (0 empty; was 97% empty at session start). ~1408 roles came from class KDoc (high quality); the rest synthesised from class name + top function names (mechanical, still searchable - polish via `set.ps1` opportunistically).
- Result: multi-word / semantic feature queries resolve, e.g. `-Search "thumbnail cache"` / `"quick settings tile"` / `"screenshot gesture"` / `"trash cleanup"` return focused hits (were ~zero before: multi-word bug + empty roles).
- function `description` is still 100% empty; function *names* are searchable.
- Residual gap: a synonym the code never uses still misses (e.g. "onboarding" when the class/role says "welcome"). If `-Search` misses, retry with the code's own term before Grep.

**Bulk role-fill tooling (permanent, added 2026-07-15):** `dev/CATALOG/scripts/generate-role-drafts.ps1` (draft empty roles -> review TSV) + `apply-role-drafts.ps1` (write reviewed drafts back; only fills empty; fills all records sharing a `path::class`). Use after a large `scan.ps1` leaves many empty roles. `-IncludeAll` covers non-entry-point classes too.

**-Search fix applied 2026-07-15:** tokenizes multi-word queries (all tokens AND'd) and strips the fixed `com/sza/fastmediasorter/` package prefix from the match haystack. Before the fix, domain words that are substrings of "fastmediasorter" (`sort`, `sorter`, `media`) matched all 2174 records, and multi-word phrases ("screen capture", "vr player") matched none.

**Durability:** manual `role`/`status`/function descriptions are preserved across `scan.ps1` re-runs (old record merged into regenerated one, `scan.ps1` line ~299). So filling `role` via `set.ps1 -Role` is durable - not wiped by `catalog_sync`.

**Why:** owner (Serhii) asked why I wasn't leveraging his class/function library when starting tasks. Root cause was two -Search defects (now fixed) plus the empty semantic layer - not a missing memory. Duplicating catalog data into agent-memory was rejected: it would go stale (working tree = truth) and cannot fit the 200-line MEMORY.md budget.

**How to apply:** for "where does feature X live", run `query.ps1 -Module app_v2 -Search "<word or phrase>"` FIRST (multi-word now works). Treat a miss as a possible vocabulary gap (try the code's term), not proof the feature is absent. Roles decay/grow as the catalog evolves - re-verify the 66% figure before relying on it. See [[project_catalog_scan_source_sets]].
