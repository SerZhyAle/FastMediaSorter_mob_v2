---
description: "Use when locating a Kotlin class or feature, or refreshing the class-catalog index. Triggers: 'where is class X', 'find the feature', 'run catalog_sync', class/role/injection queries before grep."
---

# Catalog Guide - File/Class Database

> **GLOBAL DIRECTIVES (ANTI-BUREAUCRACY):**
> 1. Dry technical prose only - no filler.
> 2. Autonomy: silently fix minor/non-structural inaccuracies; block only for critical business-logic decisions.
> 3. Terse report: one dry statement of what was done and why.

Fast index of every Kotlin class. Locate functionality before grepping; keep class metadata fresh after changes.

- **Source of truth:** `dev/CATALOG/<module>.jsonl` (machine-readable).
- **Human view:** `dev/CATALOG/<module>.md` (auto-generated).
- **Full reference:** `dev/CATALOG/README.md`.

## Usage

```
/catalog [optional: specific question or action]
```

Examples:
- `/catalog` - show this reference.
- `/catalog find classes that touch SMB` - catalogue query.
- `/catalog who depends on ResourceDao?` - DI-graph query.
- `/catalog decomposition candidates` - big files needing a split.
- `/catalog refresh app_v2` - run `catalog_sync.ps1` wrapper after code changes.
- `/catalog describe <class>` - fill `role` / function descriptions.

---

## When to invoke (mandatory triggers)

Query catalogue **before ANY code change or analysis** - faster than grepping 750+ files, carries semantic context (role, status, side effects, DI graph). Fall back to `Grep` only when it yields nothing.

Invoke **before**:

| Situation | Filter / why |
|-----------|-----|
| "Where does feature X happen?" | `Role` / `ClassMatches` / `PathMatches` - one query replaces many greps. |
| Planning refactor/decomposition | `-MinLoc 800` + `-Layer` - candidates with LOC, DI graph, side effects. |
| Auditing who uses a type | `-Injected <TypeName>` - every constructor consumer. |
| Adding a new class | `-ClassMatches "*XYZ*"` - check near-duplicates first. |
| "What touches disk/network/DB?" | `-SideEffect disk\|network\|db\|prefs`. |
| "What surfaces UI?" | `-UserFeedback`. |
| Triaging stale code | `-Status legacy` + `-TouchedBefore <date>`. |

Invoke **after** (each handled by `scan.ps1`):

| Change | scan.ps1 effect |
|--------|--------|
| New `.kt` file | Creates record; then fill `role` + `status` via `set.ps1`. |
| Renamed/deleted class or file | Auto-drops stale records, re-creates under new name. |
| Added/removed/renamed function | Refreshes `functions[]`; old descriptions survive by name. |
| Changed `@Inject constructor` params | Refreshes `injected`. |
| Moved between layers (`ui`→`domain`, etc.) | Updates `layer`. |

After bulk edits use `scripts/catalog_sync.ps1 -Module <m>` (scan + render, single process). Commit `.jsonl` + `.md` together with code change.

---

## Process

On `$ARGUMENTS`:

**Step 1 - Parse intent.**
- Empty → output this reference.
- Research ("find", "where", "who uses", "what touches") → `query.ps1` with matching filters.
- Maintenance ("refresh", "update", "rescan") → `scripts/catalog_sync.ps1 -Module <m>`.
- Annotation ("describe", "mark as legacy", "set role") → `set.ps1`.

**Step 2 - Research queries:**
1. Translate wording to `query.ps1` filters (mapping below).
2. Run; default Markdown table, `-Json` only when piping further.
3. No results → relax one filter, retry once; still empty → fall back to `Grep`.
4. Report as `path:class - role` bullets; link paths.

**Step 3 - Maintenance:**
1. Identify module (`app_v2` | `wear`) from context.
2. `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module <m>`; report added/updated/dropped (diff line counts before/after).
3. For every **new** record, prompt for `role` + `status`, or propose from class body.
4. Re-render, report updated file paths.

**Step 4 - Commit pairing.** If user about to commit code, remind: `dev/CATALOG/<module>.jsonl` + `<module>.md` go in same commit as structural change.

---

## Query-phrase → filter mapping

| User phrase | Filter |
|-------------|--------|
| "big files" / "decomposition candidates" | `-MinLoc 800` |
| "data layer" / "ui layer" / etc. | `-Layer <name>` |
| "touches disk / db / network / prefs" | `-SideEffect disk\|db\|network\|prefs` |
| "who uses <Type>" / "depends on" | `-Injected <Type>` |
| "named / matching <pattern>" | `-ClassMatches "*<pattern>*"` |
| "in path containing" | `-PathMatches "*<frag>*"` |
| "legacy" / "new" / "todo" | `-Status <value>` |
| "coroutines" / "suspend" / "flow" | `-Coroutines` |
| "shows user" / "ui feedback" / "toast" | `-UserFeedback` |
| "without tests" | `-NoTests` |
| "tested" | `-Tests` |
| "recently touched / changed" | `-TouchedSince <YYYY-MM-DD>` |
| "stale" / "not touched since" | `-TouchedBefore <YYYY-MM-DD>` |
| "missing description" / "not documented" | `-Missing role` or `-Missing description` |

Combine freely - all filters AND'd.

---

## Script reference

All scripts in `dev/CATALOG/scripts/`. Invoke from repo root via the PowerShell tool.

### Query

```powershell
pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -Layer data -SideEffect network
pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -Injected ResourceDao
pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -MinLoc 800 -Json
```

### Refresh (after code changes)

Preferred - one-shot wrapper (scan + render chained, single process):

```powershell
pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2
```

Fallback - separate calls (only when you need scan without render, e.g. before `set.ps1` to fill role/status manually):

```powershell
pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1   -Module app_v2
pwsh -NoProfile -File dev/CATALOG/scripts/render.ps1 -Module app_v2
```

### Set / edit a record

```powershell
# Class-level role + status (fuzzy path; use filename if unique)
pwsh -NoProfile -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Path "Foo.kt" `
    -Role "Orchestrates SMB scan and caches results" -Status tested

# Flavor exclusions - valid: standard, noLegal, lite, photos, legacy, vr (the six in
# productFlavors; S0250 archived the former vrUnlicensed).
# Physical isolation is governed by source-set placement (src/<flavor>/java/);
# `noFlavors` is the searchable declarative hint. A VR-only class typically declares
# everything except vr+noLegal.
pwsh -NoProfile -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Path "Foo.kt" `
    -NoFlavors "lite,photos"
pwsh -NoProfile -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Path "VrPlayerActivity.kt" `
    -NoFlavors "standard,lite,photos,legacy,noLegal"

# Function description
pwsh -NoProfile -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Path "Foo.kt" `
    -Function "refresh" -Description "Re-scans and replaces the cached listing for the given resource"
```

### Remove a record (rare)

```powershell
pwsh -NoProfile -File dev/CATALOG/scripts/remove.ps1 -Module app_v2 -Path "com/sza/.../Old.kt"
```

Scan auto-removes records whose source is gone - use `remove.ps1` only to clean up mistakes.

---

## Record fields (quick reference)

Full table: `dev/CATALOG/README.md`. Manual fields you fill with `set.ps1`:

- **`role`** - 1-line statement of what class does in system.
- **`status`** - `new` / `tested` / `legacy` / `todo` / `unknown`.
- **`noFlavors`** - flavors where class is irrelevant (empty = all).
- **`functions[].description`** - 1-line per public method, only where name alone doesn't explain behaviour.

Auto-fields (read, never edit): `path`, `class`, `layer`, `loc`, `lastTouched`, `injected`, `hasTests`, `coroutines`, `usesTimber`, `sideEffects`, `userFeedback`, `functions[].name`, `functions[].signature`.

---

## Author style (writing `role` / descriptions)

Per CLAUDE.md author-style rules:

- English in catalogue fields.
- `..` (two dots), never `...`.
- One line, one clause; no "this class ..".
- State what it *does*, not what it *is*.


**Good:** "Refreshes recent-resource app shortcuts on Android launcher."
**Bad:** "This is a manager class that is responsible for..."
