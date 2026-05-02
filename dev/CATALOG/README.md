# CATALOG — File/class database

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
| `scripts/remove.ps1` | Delete a record (rare — scan auto-removes stale entries). |

## Record fields

| Field | Source | Meaning |
|-------|--------|---------|
| `path` | auto | Relative path under a supported Kotlin source root (`<module>/src/main/java` or `<module>/src/vr/java`). |
| `class` | auto | Primary class/object/interface name. |
| `layer` | auto | `ui` / `domain` / `data` / `di` / `core` / `utils` / `worker` / `widget` / `service` / `vr` / `other`. |
| `loc` | auto | Line count. |
| `lastTouched` | auto | Date of last git commit touching the file. |
| `noFlavors` | **manual** | Flavors where the class is irrelevant. Empty = used everywhere. Valid: `standard`, `lite`, `photos`, `legacy`. |
| `injected` | auto | Types from `@Inject constructor(..)`. |
| `hasTests` | auto | Matching `*Test.kt` exists in `src/test` or `src/androidTest`. |
| `coroutines` | auto | Uses `suspend` / `Flow` / `launch` / `CoroutineScope`. |
| `usesTimber` | auto | Calls `Timber.*`. |
| `sideEffects` | auto | Heuristic: `db` / `network` / `disk` / `prefs`. |
| `userFeedback` | auto | Surfaces Toast / Snackbar / Dialog / Notification. |
| `status` | **manual** | `new` / `tested` / `legacy` / `todo` / `unknown`. |
| `role` | **manual** | 1-line description of the class's role. |
| `functions[]` | auto name/sig, **manual description** | Top-level functions. |

Manual fields survive re-runs of `scan.ps1` — merge key is `path + class` for
records, and `name` for function descriptions.

## Scripts — CRUD reference

### scan.ps1 — structural refresh (create/update auto-fields, auto-remove deleted)

```powershell
pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
```

Creates new records for new files, drops records for deleted files, refreshes
auto-fields (`loc`, `lastTouched`, `injected`, flags, function list).
For `app_v2`, the scanner covers both `src/main/java` and `src/vr/java`.
**Manual fields (`role`, `status`, `noFlavors`, function descriptions) are
preserved** by merging on `path + class` and function `name`.

### set.ps1 — edit manual fields

```powershell
# Set role + status in one call (fuzzy path match — substring must be unique)
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

# Skip auto-re-render (rare — when batching many edits)
pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Path "..." -Role "..." -NoRender
```

Re-renders Markdown after each successful edit unless `-NoRender` is passed.
Validates `-Status` and `-NoFlavors` against allowed values. Fails hard on
ambiguous fuzzy paths (shows all matches).

### query.ps1 — filter records

```powershell
# Big data-layer classes that touch disk
pwsh -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -Layer data -SideEffect disk -MinLoc 500

# Decomposition candidates
pwsh -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -MinLoc 800

# Records still missing a role description
pwsh -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -Missing role

# What depends on ResourceDao
pwsh -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -Injected ResourceDao

# Recently touched data-layer code
pwsh -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -Layer data -TouchedSince 2026-04-01

# Machine-readable output for further piping
pwsh -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -Layer ui -Coroutines -Json
```

All filters are AND'd. Supported: `-Layer`, `-Status`, `-SideEffect`,
`-MinLoc`, `-MaxLoc`, `-ClassMatches` (glob), `-PathMatches` (glob),
`-Injected`, `-Missing role|description`, `-Coroutines`, `-UserFeedback`,
`-Tests` / `-NoTests`, `-TouchedSince`, `-TouchedBefore`, `-Json`.

### remove.ps1 — drop a record manually

```powershell
pwsh -File dev/CATALOG/scripts/remove.ps1 -Module app_v2 -Path "com/sza/.../Old.kt"
```

Rare — `scan.ps1` already removes records for deleted files. Use this only
for cleaning up wrongly-added manual entries.

## When to use the catalogue

**Before planning or analysing code:** query the catalogue first. It's faster
than grepping 755 files and already carries semantic context (role, status,
side effects, DI graph).

**After changes to any file's public API:** re-run `scan.ps1` for the module.
New records start with empty `role`/`status`; fill them with `set.ps1`.

**Commit rule:** `.jsonl` and `.md` must stay in sync. Commit them together
with the code change that caused the update.
