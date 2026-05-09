# ACTIVITY_CATALOG — Activity entry-point database

Focused catalog of all Android Activity classes in the project.
For the general class catalog (all ~700+ Kotlin classes) see `dev/CATALOG/`.

## Layout

| Path | Purpose |
|------|---------|
| `app_v2.jsonl` | Source of truth for `app_v2` module. |
| `wear.jsonl` | Source of truth for `wear` module. |
| `app_v2.md`, `wear.md` | Human-readable Markdown, generated from JSONL. |
| `scripts/scan.ps1` | Re-scans manifests; auto-extracts fields; preserves manual fields. |
| `scripts/render.ps1` | JSONL → Markdown. |
| `scripts/set.ps1` | Update manual fields (`role`, `roleRu`, `tags`, `status`, `notes`). |
| `scripts/query.ps1` | Filter records by keyword, tag, module, launcher flag, etc. |
| `SCHEMA.md` | Field definitions and allowed values. |

## Quick commands

```powershell
# Find Activities related to playback
pwsh -File dev/ACTIVITY_CATALOG/scripts/query.ps1 -Module app_v2 -Search "player"

# Find all launcher Activities
pwsh -File dev/ACTIVITY_CATALOG/scripts/query.ps1 -Module all -Launcher

# Find Activities matching a Russian-language term
pwsh -File dev/ACTIVITY_CATALOG/scripts/query.ps1 -Module app_v2 -Search "плеер"

# Regenerate after adding a new Activity
pwsh -File dev/ACTIVITY_CATALOG/scripts/scan.ps1 -Module app_v2
pwsh -File dev/ACTIVITY_CATALOG/scripts/render.ps1 -Module app_v2

# Fill manual fields for a new Activity
pwsh -File dev/ACTIVITY_CATALOG/scripts/set.ps1 -Module app_v2 -Class "NewActivity" `
    -Role "..." -RoleRu "..." -Tags "tag1,tag2" -Status new
```

## When to update

- After adding, renaming, or removing an Activity from `AndroidManifest.xml`: run `scan.ps1`.
- After filling or editing a description: use `set.ps1` (auto-renders).
- Commit `*.jsonl` + `*.md` together with the code change.
