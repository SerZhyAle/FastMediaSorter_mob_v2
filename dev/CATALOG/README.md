# CATALOG - File/class database

Machine-readable catalogue of all Kotlin classes in the project. Lets agents
answer "where does X happen?" questions without scanning the whole codebase.

## Layout

| Path | Purpose |
|------|---------|
| `app_v2.jsonl` | Source of truth for `app_v2` module (1 class = 1 line JSON). |
| `wear.jsonl` | Source of truth for `wear` module. |
| `app_v2.md`, `wear.md` | Human-readable Markdown, generated from JSONL. |
| `scripts/scan.ps1` | Re-scans a module. Auto-extracts signatures/flags; preserves manual fields. |
| `scripts/render.ps1` | JSONL → Markdown. |
| `scripts/set.ps1` | Update manual fields on a record. |
| `scripts/query.ps1` | Filter records by layer / status / side effects / LOC / etc. |
| `scripts/remove.ps1` | Delete a record (rare - scan auto-removes stale entries). |

## Record fields

| Field | Source | Meaning |
|-------|--------|---------|
| `path` | auto | Relative path under a supported Kotlin source root (`<module>/src/main/java` or `<module>/src/vr/java`). |
| `class` | auto | Primary class/object/interface name. |
| `layer` | auto | `ui` / `domain` / `data` / `di` / `core` / `utils` / `worker` / `widget` / `service` / `vr` / `other`. |
| `loc` | auto | Line count. |
| `lastTouched` | auto | Date of last git commit touching the file. |
| `noFlavors` | **manual** | Flavors where the class is irrelevant. Empty = used everywhere. Valid: `standard`, `lite`, `photos`, `legacy`, `vr`, `noLegal`. Source-set placement (`src/<flavor>/java/`) governs physical isolation; `noFlavors` is the declarative hint for consumers and audits. |
| `injected` | auto | Types from `@Inject constructor(..)`. |
| `constructorDeps` | auto | All primary-constructor parameter types (superset of `injected`: captures every constructor param, not only `@Inject`-annotated, so non-Hilt and `@Inject`-free classes still expose their collaborators). Imports are not parsed. |
| `hasTests` | auto | A test sibling exists for the class. The test source root is resolved from the file's own source root (any `src/<root>/`, including flavor roots like `vr` / `noLegal` / `lite` / `photos` / `legacy`), not from `src/main` alone. Matches either the `<ClassName>Test.kt` convention or a same-relative-path file under `src/test` / `src/androidTest`. |
| `coroutines` | auto | Uses `suspend` / `Flow` / `launch` / `CoroutineScope`. |
| `usesTimber` | auto | Calls `Timber.*`. |
| `sideEffects` | auto | Heuristic: `db` / `network` / `disk` / `prefs`. |
| `userFeedback` | auto | Surfaces Toast / Snackbar / Dialog / Notification. |
| `status` | **manual** | `new` / `tested` / `legacy` / `todo` / `unknown`. |
| `role` | **manual** | 1-line description of the class's role. |
| `functions[]` | auto name/sig, **manual description** | Top-level functions. |

Manual fields survive re-runs of `scan.ps1` - merge key is `path + class` for
records, and `name` for function descriptions.

The schema is **append-only** (S0314, ADR-1): the `constructorDeps` and hardened
`hasTests` enrichment was added without renaming, reordering, or removing any
existing field, so prior JSONL consumers keep working. `constructorDeps` is an
auto field positioned after `injected`; it is recomputed on every scan and is
never copied from the prior record (only `role`, `status`, `noFlavors`, and
function `description` are merged back as manual fields).

## Scripts - CRUD reference

### scan.ps1 - structural refresh (create/update auto-fields, auto-remove deleted)

```powershell
pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
```

Creates new records for new files, drops records for deleted files, refreshes
auto-fields (`loc`, `lastTouched`, `injected`, flags, function list).
For `app_v2`, the scanner covers both `src/main/java` and `src/vr/java`.
**Manual fields (`role`, `status`, `noFlavors`, function descriptions) are
preserved** by merging on `path + class` and function `name`.

### set.ps1 - edit manual fields

```powershell
# Set role + status in one call (fuzzy path match - substring must be unique)
pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Path "AppShortcutsManager.kt" `
    -Role "Refreshes recent-resource app shortcuts on Android launcher" `
    -Status tested

# Mark a class as excluded from some flavors
pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Path "CloudAuthManager.kt" `
    -NoFlavors "lite,photos"

# Describe a specific function
pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Path "MediaFilesCacheManager.kt" `
    -Function "updateFile" `
    -Description "Replaces cache entry for a renamed/moved file within a resource"

# Skip auto-re-render (rare - when batching many edits)
pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Path "..." -Role "..." -NoRender
```

Re-renders Markdown after each successful edit unless `-NoRender` is passed.
Validates `-Status` and `-NoFlavors` against allowed values. Fails hard on
ambiguous fuzzy paths (shows all matches).

### query.ps1 - filter records

```powershell
# Big data-layer classes that touch disk
pwsh -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -Layer data -SideEffect disk -MinLoc 500

# Decomposition candidates
pwsh -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -MinLoc 800

# Records still missing a role description
pwsh -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -Missing role

# What injects ResourceDao (@Inject constructor only)
pwsh -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -Injected ResourceDao

# What takes ResourceDao as a constructor parameter (superset of -Injected;
# also catches non-Hilt / @Inject-free collaborators)
pwsh -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -DependsOn ResourceDao

# Search across class, path, role, dependencies, or functions
pwsh -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -Search "Welcome"

# Recently touched data-layer code
pwsh -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -Layer data -TouchedSince 2026-04-01

# Machine-readable output for further piping
pwsh -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -Layer ui -Coroutines -Json
```

All filters are AND'd. Supported: `-Layer`, `-Status`, `-SideEffect`,
`-MinLoc`, `-MaxLoc`, `-ClassMatches` (glob), `-PathMatches` (glob), `-Search`,
`-Injected`, `-DependsOn`, `-Missing role|description`, `-Coroutines`,
`-UserFeedback`, `-Tests` / `-NoTests`, `-TouchedSince`, `-TouchedBefore`,
`-Json`.

`-Search <Term>` searches (case-insensitively) across class name, path, role description, injected types, constructor dependencies, and function names/descriptions.

`-Injected <Type>` matches only `@Inject constructor` parameters; `-DependsOn
<Type>` matches the broader `constructorDeps` (any constructor parameter type),
so it also finds non-Hilt and `@Inject`-free collaborators.

### remove.ps1 - drop a record manually

```powershell
pwsh -File dev/CATALOG/scripts/remove.ps1 -Module app_v2 -Path "com/sza/.../Old.kt"
```

Rare - `scan.ps1` already removes records for deleted files. Use this only
for cleaning up wrongly-added manual entries.

## When to use the catalogue

**Semantic lookup:** use `query.ps1` first when the question is about role,
ownership, DI wiring, side effects, size, or feature boundaries. It is faster
than grepping the whole repo and already carries semantic context (role,
status, side effects, DI graph).

**Exact-match lookup:** for a known class/file/token name, read
`app_v2.jsonl` or `wear.jsonl` directly, or use `rg` if the target is not a
catalogued Kotlin symbol. Keep this path narrow and deterministic.

**Read/write rule:** direct `.jsonl` reads are allowed for narrow lookup, but
writes remain script-only through `scan.ps1`, `set.ps1`, and `remove.ps1`.
Never hand-edit catalogue records.

**After changes to any file's public API:** re-run `scan.ps1` for the module.
New records start with empty `role`/`status`; fill them with `set.ps1`.

**Commit rule:** `.jsonl` and `.md` must stay in sync. Commit them together
with the code change that caused the update.
