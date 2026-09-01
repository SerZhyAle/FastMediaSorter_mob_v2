# Spec Catalog - Journal Schema

**Active journal:** `PLAN/spec-catalog.jsonl` - non-`Archived` records only.
**Archive journal:** `PLAN/spec-catalog-archive.jsonl` - `Archived` records only.
**Burned ids:** `PLAN/spec-catalog-burned-ids.jsonl` - ids spent and since removed from both journals; its own two-field shape, see [Burned ids](#burned-ids-planspec-catalog-burned-idsjsonl).

The two journals are JSONL (one JSON object per line, UTF-8 no BOM) and share the same record schema. See [Archive split](#archive-split) below.

> **Read-only outside the CLI.** Direct edits to any of these files are forbidden by project policy. Use only `scripts/spec_catalog/{insert,update,select,delete,validate}.ps1`.

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

Closing gates: a transition INTO `Implemented` or `Verified` runs `check-evidence-durable.ps1` (a closed spec must not cite evidence under disposable `temp/`, S1606) and `check-open-items-carried.ps1` (a closed spec must not strand an open research item without a `Carrier: Sxxxx`, S1607). A transition INTO `Verified` additionally runs `check-audit-recorded.ps1` (the spec must carry a non-empty `## Last Audit` block, S2298). All are invoked from `Assert-ClosingGates` in `_lib.ps1`, which `update.ps1`, `close.ps1` and `bulk-update.ps1` all call, so no status-change path bypasses them. `Archived` is deliberately not gated. The third gate is `Verified`-only on purpose: `Verified` asserts that an audit passed and the verdict of that audit exists nowhere but the block, while `Implemented` asserts only that the code is done - true before any audit has run, so gating it would demand a verdict ahead of the run producing one. The research-section parse behind the second gate lives in `_research-items.ps1`, a leaf file `_lib.ps1` and `preview.ps1` both dot-source, so `preview.ps1`'s `research_open_count` / `research_uncarried_count` report exactly what the gate will enforce rather than a second opinion about it (S1621). The third gate's heading pattern lives in the same file for the same reason, and `preview.ps1`'s `last_audit_present` reads it: matched by heading TEXT, so the numbered form `## 6. Last Audit` counts, where the literal test it replaced reported that spec as unaudited in both places at once. The lifecycle-status sets live in `_status-sets.ps1` for the same reason: `Test-BlockerReleasedStatus` defines which statuses release a dependent (`Implemented`, `Verified`, `BlockNeedUserTest`, `Archived` - the set `RELEASE_READY.md` uses plus `Archived`) and is shared by `preview.ps1` and `_lib.ps1` so the preflight verdict and release-file routing answer from one definition (S1864).

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
10. Archive split: no `Archived` record sits in the active journal, and no non-`Archived` record sits in the archive journal.

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

## Archive split

`Archived` records (the bulk of the catalog over time) live in a separate `PLAN/spec-catalog-archive.jsonl` so routine reads scan only active tickets. The split is encapsulated in `_lib.ps1`; command behaviour is unchanged for callers.

- `Read-Catalog` (default) reads the active journal only. `Read-Catalog -IncludeArchived` merges both, sorted by id.
- `Find-Record -Id` resolves against the active journal first, then falls back to the archive - archived ids stay addressable.
- `select.ps1 -Id <id>` always resolves across both journals; listing commands (`select.ps1`, `search.ps1`) default to active and take `-IncludeArchived` for a full view.
- `stats.ps1` and `validate.ps1` always include the archive (full-catalog overviews).
- Archiving (`archive.ps1`, `delete.ps1`, `update.ps1`/`close.ps1`/`bulk-update.ps1` setting status `Archived`) **moves** the record into the archive journal and removes it from the active one. Reviving (`update.ps1` setting a non-`Archived` status on an archived id) moves it back.
- `Write-Catalog` writes the active journal only; the archive journal is mutated solely by `Add-ArchiveRecord` / `Write-ArchiveCatalog`.
- Backward compatible: if the archive journal is absent, behaviour is identical to a single-journal catalog.
- One-time migration: `migrate-archive-split.ps1` relocates pre-existing `Archived` records (idempotent).

## Burned ids (`PLAN/spec-catalog-burned-ids.jsonl`)

Ids that were allocated and later removed from both journals. Row shape: `id`, `reason`, `burned` (date). Append-only, deduplicated by id.

- `New-CatalogId` takes its maximum over the active journal, the archive **and** this registry. Deleting a record is the one way an id can silently return to circulation, and two tickets answering to one id would make every dev-log row, changelog entry and commit message already naming it ambiguous after the fact.
- `validate.ps1` subtracts registered ids from the `Monotonicity` gap list, so a deliberate hole reads as accounted for and a genuinely lost record still surfaces. Without this a single cleanup buries the check under known holes forever.
- Written only by `purge-probe-records.ps1`. No command removes a row.

## Alternate-catalog runs

`$env:FMS_SPEC_CATALOG_DIR` redirects all three journals - active, archive and burned ids - to that directory. `$script:RepoRoot` is deliberately not redirected, so spec `.md` files still resolve from the real `PLAN/` and a sandboxed run reads live spec bodies. A directory that does not exist is a hard error, never a silently created second catalog. Pair it with `$env:FMS_SKIP_RELEASE_QUEUE` so the run does not reconcile the real release plan.

`preview.tests/Run-Tests.ps1` is the reference consumer: it snapshots the journals into `temp/scratch/spec-catalog-sandbox-<pid>/`, runs every CLI child against the copy, and asserts against the real journals afterwards that nothing leaked. Before this existed the harness inserted probes into the production journal with real ids; soft delete left them in the archive for good, burning 21 spec ids and once racing a genuine insert into `Duplicate id`.

## One-off maintenance

- `purge-probe-records.ps1` - removes rows that are simultaneously status `Archived` and named `^preview-tests-probe`, recording each id in the burned registry first. Idempotent. Deliberately narrow: a general `-Purge` switch on `delete.ps1` was rejected, because it would put an irreversible operation into production tooling to compensate for a test defect.

## Release plan (`PLAN/RELEASE_QUEUE.md` + `PLAN/RELEASE_READY.md`)

The catalog knows a ticket's **status**; it cannot know the owner's **intent** - which release package a ticket belongs to and in what order the work should happen. That lives in two companion plain-text files the owner edits by hand.

The split is by one question: *is there work left on this ticket?*

- **`RELEASE_QUEUE.md`** - work remaining, the sorting surface. Every status below `Implemented`, including `BlockByOtherTask`, `BlockQuestions` and `BlockExternal`: blocked work still has to be planned around, so the owner wants it visible.
- **`RELEASE_READY.md`** - the release's finished content. `Implemented`, `Verified`, and `BlockNeedUserTest`. That last one is deliberate: some flows are very hard to verify and may sit unchecked for months; the owner treats them as shipped rather than as pending work. If one later proves broken it is simply reopened and rides the next package.

Shared mechanics:

- Line shape: `rel  ticket  changed  status`. `rel` = release package number (matches the `DEBUG-v0NN` branch), `--` = not scheduled. `ticket` = the spec file name without its extension. `changed` = the date the STATUS last moved, not the date the spec text was edited.
- Selection authority: the queue also decides **what gets worked on next**. `spec-next-preflight.ps1` and `release-plan.ps1` rank by release package ascending, then the owner's line order inside the package, then the ready side (`Implemented` rows in `RELEASE_READY.md`, finished content awaiting its audit), then `--` parked lines, then anything in neither file. Catalog `priority` is a tiebreak for unlisted tickets only. With no queue file present the ranking degrades to the old priority order, so a fresh checkout still works.
- Ownership split: the catalog owns `status`, these files own `rel` and line order. **A script may add a ticket, refresh its status/date, move a line to the sibling file, or drop one that left the active journal - it must never reorder lines or rewrite `rel`.** A ticket keeps its package when it crosses between files.
- Reconciliation is automatic: `Write-Catalog` calls `Sync-ReleaseQueue`, so every mutation path (insert / update / complete / archive / delete / bulk-update) keeps both files current without any skill knowing they exist. Crossing the ready boundary moves the line - in either direction, so a failed device test or a reopened bug lands back in the queue. `$env:FMS_SKIP_RELEASE_QUEUE` disables the hook (tests, alternate-catalog runs); a missing queue file is a no-op, never an error.
- A ready ticket present in neither file is never auto-added: it shipped in an earlier package.
- A new ticket is unfinished by definition, so it lands in the queue, at the end of the block named by the `current-next-release:` marker (falling back to the `DEBUG-v0NN` branch name, then `--`).
- Operator CLI: `release-queue.ps1 -Reconcile | -Validate | -List [-Ready] [-Release N] | -SetCurrent N | -Ship -Release N [-Version X] [-DryRun]`.
- Shipping: `-Ship` moves the READY block for that package to `PLAN/RELEASE_QUEUE_DONE.md` (newest first) and advances the marker. Unfinished queue lines still assigned to that package are reported, never shipped and never auto-moved - re-sorting them is the owner's call. `/skill-release` Step 12c runs the ship **before** the archive sweep: archiving first flips records to `Archived`, which drops those lines and loses the record of what shipped.
- All three files live under gitignored `PLAN/`, like the journal itself: working artifacts of this checkout, not tracked documents.

## Why JSONL

- Append-friendly; one record = one line.
- Human-readable; `git diff` shows meaningful changes.
- No external dependencies; PowerShell's `ConvertFrom-Json` parses each line.
- Sorted-on-write ordering keeps diffs local to the changed record.
