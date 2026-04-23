# Catalog Guide — File/Class Database

Fast index of every Kotlin class in the project. Use it to locate
functionality before grepping, and to keep class-level metadata fresh after
changes.

**Source of truth:** `dev/CATALOG/<module>.jsonl` (machine-readable).
**Human view:** `dev/CATALOG/<module>.md` (auto-generated).
**Full reference:** `dev/CATALOG/README.md`.

## Usage

```
/catalog [optional: specific question or action]
```

Examples:
- `/catalog` — show this reference.
- `/catalog find classes that touch SMB` — run a query on the catalogue.
- `/catalog who depends on ResourceDao?` — DI-graph query.
- `/catalog decomposition candidates` — big files needing a split.
- `/catalog refresh app_v2` — run `scan.ps1` after code changes.
- `/catalog describe <class>` — fill in `role` / function descriptions.

---

## When to invoke this skill (mandatory triggers)

**Before ANY code change or analysis:** query the catalogue first. It is
faster than grepping 750+ files and already carries semantic context
(role, status, side effects, DI graph). Skip straight to `Grep` only when the
catalogue yields nothing.

Specifically, invoke **before**:

| Situation | Why |
|-----------|-----|
| "Where does feature X happen?" | Search by `Role` / `ClassMatches` / `PathMatches` — one query replaces many greps. |
| Planning a refactor or decomposition | `-MinLoc 800` + `-Layer` lists candidates with LOC, DI graph, side effects. |
| Auditing who uses a type | `-Injected <TypeName>` returns every constructor consumer. |
| Adding a new class | Check for near-duplicates by `-ClassMatches "*XYZ*"` before writing. |
| "What touches the disk / network / DB?" | `-SideEffect disk\|network\|db\|prefs`. |
| "What surfaces UI to the user?" | `-UserFeedback`. |
| Triaging stale code | `-Status legacy` + `-TouchedBefore <date>`. |

And **after** any of these:

| Change | Action |
|--------|--------|
| New `.kt` file | `scan.ps1` creates a record; then fill `role` + `status` via `set.ps1`. |
| Renamed/deleted class or file | `scan.ps1` auto-drops stale records and re-creates under the new name. |
| Added/removed/renamed function | `scan.ps1` refreshes the `functions[]` list; old descriptions survive by name. |
| Changed `@Inject constructor` params | `scan.ps1` refreshes `injected`. |
| Moved between layers (`ui` → `domain`, etc.) | `scan.ps1` updates `layer`. |

Always run `render.ps1` after bulk edits and commit `.jsonl` + `.md`
together with the code change.

---

## Process

When invoked with `$ARGUMENTS`:

**Step 1 — Parse the intent.**

- Empty `$ARGUMENTS` → output this reference.
- Research intent ("find", "where", "who uses", "what touches") → run `query.ps1` with matching filters.
- Maintenance intent ("refresh", "update", "rescan") → run `scan.ps1` then `render.ps1`.
- Annotation intent ("describe", "mark as legacy", "set role") → run `set.ps1`.

**Step 2 — For research queries:**

1. Translate the user's wording to `query.ps1` filters (see mapping below).
2. Run the script. Default to Markdown table output; switch to `-Json` only
   when piping into further processing.
3. If no results → relax one filter and retry once; if still empty, fall
   back to `Grep`.
4. Report findings as `path:class — role` bullets; link paths.

**Step 3 — For maintenance:**

1. Identify the module (`app_v2` or `wear`) from user context.
2. Run `scan.ps1 -Module <m>`; report how many records were added / updated / dropped (by diffing line counts before/after).
3. For every **new** record, prompt the user for `role` and `status`, or
   propose sensible values from the class body.
4. Re-render and report the updated file paths.

**Step 4 — Commit pairing.**

If the user is about to commit code, remind them: `dev/CATALOG/<module>.jsonl`
and `<module>.md` must be in the same commit as the structural change.

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

Combine freely — all filters are AND'd.

---

## Script reference

All scripts live in `dev/CATALOG/scripts/`. Canonical invocation from repo
root via the PowerShell tool.

### Query

```powershell
pwsh -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -Layer data -SideEffect network
pwsh -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -Injected ResourceDao
pwsh -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -MinLoc 800 -Json
```

### Refresh (after code changes)

```powershell
pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
```

### Set / edit a record

```powershell
# Class-level role + status (fuzzy path; use filename if unique)
pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Path "Foo.kt" `
    -Role "Orchestrates SMB scan and caches results" -Status tested

# Flavor exclusions
pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Path "Foo.kt" `
    -NoFlavors "lite,photos"

# Function description
pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Path "Foo.kt" `
    -Function "refresh" -Description "Re-scans and replaces the cached listing for the given resource"
```

### Remove a record (rare)

```powershell
pwsh -File dev/CATALOG/scripts/remove.ps1 -Module app_v2 -Path "com/sza/.../Old.kt"
```

Scan auto-removes records whose source is gone — only use `remove.ps1` for
cleaning up mistakes.

---

## Record fields (quick reference)

See `dev/CATALOG/README.md` for the full table. Key manual fields you fill
with `set.ps1`:

- **`role`** — 1-line statement of what the class does in the system.
- **`status`** — `new` / `tested` / `legacy` / `todo` / `unknown`.
- **`noFlavors`** — flavors where this class is irrelevant (empty = all).
- **`functions[].description`** — 1-line per public method, only where the
  name alone doesn't explain the behaviour.

Auto-fields to read but never edit: `path`, `class`, `layer`, `loc`,
`lastTouched`, `injected`, `hasTests`, `coroutines`, `usesTimber`,
`sideEffects`, `userFeedback`, `functions[].name`, `functions[].signature`.

---

## Author style (when writing `role` / descriptions)

Follow the project author-style rules from CLAUDE.md:

- English in catalogue fields.
- Use `..` (two dots), never `...`, if ellipsis is needed.
- Keep each description to **one line**; one clause; no "this class ..".
- State what the class/function *does*, not what it *is*.

**Good:** "Refreshes recent-resource app shortcuts on Android launcher."
**Bad:** "This is a manager class that is responsible for..."
