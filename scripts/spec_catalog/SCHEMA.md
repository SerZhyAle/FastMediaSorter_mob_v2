# Spec Catalog - Journal Schema

**Journal file:** `PLAN/spec-catalog.jsonl` (one JSON object per line, UTF-8 no BOM).

> **Read-only outside the CLI.** Direct edits to this file are forbidden by project policy. Use only `scripts/spec_catalog/{insert,update,select,delete,validate}.ps1`.

## Record fields

| Field | Type | Required | Description |
|-------|------|:--------:|-------------|
| `id` | string | yes | Stable ticket id, format `^S\d{4}$`. Allocated once; never reused. |
| `name` | string | yes | Slug - the post-prefix portion of the spec file name. Example: `decompose-giant-files`. No `spec_` prefix. |
| `status` | string | yes | One of the values listed below. |
| `priority` | integer | yes | 0..100. Higher = more urgent. See priority guide below. |
| `tier` | integer | no | Roadmap tier 0..4 from the strategic spec header. Omit when not applicable. |
| `file` | string | yes | Path to primary `.md` relative to repo root. Must start with `PLAN/S\d{4}_` and **must not** contain a `_spec_` segment. |
| `created` | string | yes | `YYYY-MM-DD` of allocation. |
| `updated` | string | yes | `YYYY-MM-DD HH:mm` of last mutation (local time, 24h). |

## Priority guide

| Range | Meaning |
|-------|---------|
| 90..100 | Build-blocker / release-blocker bug. Top of the queue. |
| 70..89  | Critical bug or high-impact feature. |
| 40..69  | Standard feature or enhancement. **Default = 50.** |
| 10..39  | Minor polish, nitpick. |
| 0..9    | Wishlist / idea. Archived records use `0`. |

## Status values

Active lifecycle:

`Draft`, `Approved`, `Tactical`, `In Progress`, `Implemented`, `Verified`, `Partial`, `Broken`.

Block states (any active spec may transition into one of these and back):

- `BlockByOtherTask`  - depends on another spec; record the dependency in the ticket body.
- `BlockNeedUserTest` - implementation done, awaiting hands-on verification.
- `BlockQuestions`    - awaiting clarification from the user.
- `BlockExternal`     - waiting on an external dependency (library release, hardware, third party).

Terminal:

- `Archived` - soft-deleted; record stays forever, id never reused.

State transitions:

```text
Draft -> Approved -> Tactical -> In Progress -> Implemented -> Verified
                                                              \-> Partial
                                                              \-> Broken
any active state <-> Block{ByOtherTask|NeedUserTest|Questions|External}
any -> Archived  (soft-delete, never reused)
```

## Staleness

The CLI computes "days since `updated`" for the report (`a.ps1 ss`):

- `>= 14d` and status not in `{Verified, Implemented, Archived}` → warn.
- `>= 30d` under the same constraint → alert. Suggests `/spec-update <id>`.

## Invariants (validated by `validate.ps1`)

1. Every line is a complete JSON object.
2. Every record has every required field (including `priority`).
3. `id` is unique across the journal (active and archived).
4. `id` matches `^S\d{4}$`.
5. `status` is in the enum above.
6. `priority` is an integer in `0..100`.
7. `file` is a relative path under `PLAN/`, starts with `PLAN/S\d{4}_`, contains no `_spec_` segment, no `..` components.
8. For every non-archived record, the `file` exists on disk.
9. For every `PLAN/S\d{4}_*` artifact on disk, there is a journal record whose `id` matches the prefix.

## Example record

```json
{"id":"S0023","name":"bugfix-vr-player-activity-stale-references","status":"Verified","priority":90,"tier":1,"file":"PLAN/S0023_bugfix-vr-player-activity-stale-references.md","created":"2026-04-28","updated":"2026-04-28 12:55"}
```

## Optional fields

All fields below are optional. Absence in any record - old or new - is valid and passes `validate.ps1` without error.

- `title` (string) - human-readable display name; free text; used by `search.ps1` for substring matching alongside `name`.
- `tags` (string array) - thematic labels, e.g. `["tooling","scripts"]`; filterable by `search.ps1 -Tag`.
- `type` (string) - work kind; one of `feature`, `bugfix`, `tooling`, `research`; filterable by `search.ps1 -Type`.
- `blocked_by` (string array) - ids of tickets this one depends on, e.g. `["S0099"]`; informational, not enforced by `validate.ps1`.
- `closed_at` (string) - `YYYY-MM-DD` date of intentional finalization; written by `close.ps1`; absent until the ticket is closed.
- `has_tactical` (boolean) - `true` when a `PLAN/Sxxxx_*/INDEX.md` tactical folder exists; written by `/spec-tech` during the Tactical status transition.

---

## Why JSONL

- Append-friendly; one record = one line.
- Human-readable; `git diff` shows meaningful changes.
- No external dependencies; PowerShell's `ConvertFrom-Json` parses each line.
- Sorted-on-write ordering keeps diffs local to the changed record.
