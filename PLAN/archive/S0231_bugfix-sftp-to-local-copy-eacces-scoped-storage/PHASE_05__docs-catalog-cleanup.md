# Phase 05 — Docs, catalog, and status finalization

**Strategic spec:** [`../S0231_bugfix-sftp-to-local-copy-eacces-scoped-storage.md`](../S0231_bugfix-sftp-to-local-copy-eacces-scoped-storage.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all prior phases (01, 02, 03, 04)
**Blocks:** —
**Steps done:** 4 / 4
**Started:** 2026-05-17
**Completed:** 2026-05-17

---

## Objective

Regenerate the class catalog for `app_v2`, set `role`/`status` for the new classes, append a functionality log entry, ensure all dev log entries are in place, and transition the ticket to `BlockNeedUserTest` so on-device verification can begin.

`docs/FEATURES.md` is **not** modified per strategic §8 ("Без изменений") — S0231 fixes an existing capability, not a new feature.

---

## Prerequisites

- [ ] Phases 01–04 are all ✅ Done.
- [ ] All target-variant builds pass (`standardDebug`).
- [ ] All Phase Done Criteria from prior phases verified.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |
| `dev/CATALOG/app_v2.md` | Regenerated | n/a |
| `dev/FUNCTIONALITY.log` | Appended | n/a |
| `dev/CHANGELOG.md` | Auto-appended via script | n/a |
| `PLAN/spec-catalog.jsonl` | Mutated via `update.ps1` | n/a |

> Catalog `.jsonl` and `.md` files are gitignored locally per project setup — regeneration is still mandatory; downstream tooling reads them.

---

## Steps

### Step 05.1 — Regenerate `app_v2` catalog (scan + render) and fill role/status for new classes

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** — start of phase

**Prompt for developer:**

> 1. Run scan + render:
>    ```powershell
>    pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
>    pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
>    ```
>
> 2. For each new class introduced in Phase 01 and Phase 04, set role + status. Wrap calls in try/catch per `project_catalog_set_ps1_stops_on_error` — `set.ps1` aborts the batch on the first failure, so handle each separately.
>
>    Expected new classes:
>    - `LocalDestinationCategory` (sealed) — `role=domain-model`, `status=stable`
>    - `LocalDestinationClassifier` — `role=classifier`, `status=stable`
>    - `LocalSink` (interface) — `role=contract`, `status=stable`
>    - `LocalDestinationWriter` (interface) — `role=contract`, `status=stable`
>    - `MediaStoreLocalDestinationWriter` — `role=data-writer`, `status=stable`
>    - `LocalDestinationPermissionDeniedException` — `role=error-type`, `status=stable`
>
>    Use:
>    ```powershell
>    pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -ClassMatches "LocalDestinationClassifier" -Role "classifier" -Status "stable"
>    # ... repeat per class
>    ```

**Verification:**

- `pwsh -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -ClassMatches "*LocalDestination*"` — returns ≥ 5 records (5 new classes + the exception).
- Each new class has non-`unknown` `role` and `status` fields.

**Status:** `[x]` done

---

### Step 05.2 — Append a single FIX line to the functionality log

**Files:** `dev/FUNCTIONALITY.log`
**Depends on:** Step 05.1

**Prompt for developer:**

> Run:
> ```powershell
> pwsh -File scripts/add_to_functionality_log.ps1 -Id S0231 -Op FIX -Description "Network->local copy works in public collections (Music/Movies/Pictures/DCIM/Downloads) on Android 10+ without MANAGE_EXTERNAL_STORAGE; atomicity preserved via MediaStore IS_PENDING; non-public destinations surface a clear localized message on permission denied"
> ```

**Verification:**

- `Grep` in `dev/FUNCTIONALITY.log` — line containing `S0231 | FIX |` matches once at the tail of the file.

**Status:** `[x]` done

---

### Step 05.3 — Confirm dev changelog has entries for every modified file across Phases 01–04

**Files:** `dev/CHANGELOG.md`
**Depends on:** — runs against the cumulative state

**Prompt for developer:**

> Cross-check that every file in the Files-Touched tables of Phases 01–04 has at least one `dev/CHANGELOG.md` entry. The check is:
>
> ```powershell
> $files = @(
>   "app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/local/LocalDestinationCategory.kt",
>   "app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/local/LocalDestinationClassifier.kt",
>   "app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/local/LocalSink.kt",
>   "app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/local/LocalDestinationWriter.kt",
>   "app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/local/MediaStoreLocalDestinationWriter.kt",
>   "app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/local/LocalDestinationPermissionDeniedException.kt",
>   "app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/AtomicFileOperationStrategy.kt",
>   "app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SftpOperationStrategy.kt",
>   "app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/SmbOperationStrategy.kt",
>   "app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/FtpOperationStrategy.kt",
>   "app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/strategy/CloudOperationStrategy.kt",
>   "app_v2/src/main/java/com/sza/fastmediasorter/di/DirectoryStrategyModule.kt",
>   "app_v2/src/main/java/com/sza/fastmediasorter/data/network/SftpFileOperationHandler.kt",
>   "app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbFileOperationHandler.kt",
>   "app_v2/src/main/java/com/sza/fastmediasorter/data/network/FtpFileOperationHandler.kt",
>   "app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudFileOperationHandler.kt",
>   "app_v2/src/main/java/com/sza/fastmediasorter/domain/transfer/FileOperationError.kt",
>   "app_v2/src/main/java/com/sza/fastmediasorter/core/util/FileOperationErrorFormatter.kt",
>   "app_v2/src/main/res/values/strings.xml",
>   "app_v2/src/main/res/values-ru/strings.xml",
>   "app_v2/src/main/res/values-uk/strings.xml"
> )
> $missing = @()
> foreach ($f in $files) { if (-not (Select-String -SimpleMatch -Path dev/CHANGELOG.md -Pattern $f -Quiet)) { $missing += $f } }
> if ($missing.Count -gt 0) { Write-Host "Missing entries:"; $missing | ForEach-Object { Write-Host "  $_" }; exit 1 }
> Write-Host "All files have dev log entries."
> ```
>
> If anything is missing, append the corresponding entry via `.\scripts\add_to_dev_log.ps1`.

**Verification:**

- The check script above exits with `All files have dev log entries.` (exit 0).

**Status:** `[x]` done

---

### Step 05.4 — Transition ticket to `BlockNeedUserTest` and commit S0231 tag invariant

**Files:** `PLAN/spec-catalog.jsonl` (via CLI), `PLAN/S0231_bugfix-sftp-to-local-copy-eacces-scoped-storage.md`
**Depends on:** Steps 05.1, 05.2, 05.3

**Prompt for developer:**

> 1. Verify the Timber.d invariant before flipping the status. The spec is about to enter `BlockNeedUserTest`, so `Timber.d("S0231:` tags must be present:
>    ```powershell
>    rg -n 'Timber\.d\("S0231:' app_v2/src/main/java | Measure-Object | Select-Object -ExpandProperty Count
>    ```
>    Expected: ≥ 5 (one per network strategy + one in `AtomicFileOperationStrategy`).
>
> 2. Update strategic spec frontmatter `Status: Tactical` → `Status: BlockNeedUserTest`.
>
> 3. Update journal:
>    ```powershell
>    pwsh -File scripts/spec_catalog/update.ps1 -Id S0231 -Status BlockNeedUserTest
>    ```
>
> 4. Append to strategic spec a `## Last Audit` section (or update if already present) with: date, mode = tactical-handoff, list of S0231 tag sites (paths only), pointer to this Phase 05.
>
> 5. The tags remain in code until `/spec-check S0231` flips the status to `Verified` (which is the responsibility of `/spec-check`, not this phase).

**Verification:**

- `Grep` in `PLAN/S0231_bugfix-sftp-to-local-copy-eacces-scoped-storage.md` — `**Status:** BlockNeedUserTest` matches once.
- `pwsh -File scripts/spec_catalog/select.ps1 -Id S0231 -Format json` — output contains `"status":"BlockNeedUserTest"`.
- `rg -n 'Timber\.d\("S0231:' app_v2/src/main/java/` returns ≥ 5 hits.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 05.*` is `[x] done`.
- [x] Project still compiles — BUILD SUCCESSFUL (Step 05.4 evidence).
- [x] `dev/FUNCTIONALITY.log` contains the S0231 FIX line.
- [x] `dev/CATALOG/app_v2.jsonl` and `app_v2.md` are regenerated; 6 new local-destination classes have role+status set (1374 records total).
- [x] Ticket status is `BlockNeedUserTest` in both the spec file and the journal.
- [x] Timber probe tags are present at exactly 5 sites (SftpOperationStrategy, SmbOperationStrategy, FtpOperationStrategy, CloudOperationStrategy, AtomicFileOperationStrategy).

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

After this phase the spec hands off to the on-device tester. The tester exercises each of the 5 device paths listed below and, on success, `/spec-check S0231` flips the status to `Verified` and removes the `Timber.d("S0231:` tags.

On-device check matrix (for the tester):

1. SFTP → `/storage/emulated/0/Music/` on Android 10 (API 29) device without `MANAGE_EXTERNAL_STORAGE` granted.
2. SFTP → `/storage/emulated/0/Movies/` on Android 11+ (API 30+) without `MANAGE_EXTERNAL_STORAGE`.
3. SMB → `/storage/emulated/0/Pictures/` (any Android version with SMB enabled).
4. FTP → `/storage/emulated/0/Download/` (any Android version with FTP enabled).
5. Move (copy + delete source) SFTP → `/storage/emulated/0/Music/` — source removed only on full destination success.
6. Copy SFTP → arbitrary non-public path (e.g. `/storage/emulated/0/CustomFolder/`) on Android 11+ without `MANAGE_EXTERNAL_STORAGE` — expected: localized error message via Step 04.4 string, **not** raw `EACCES`.

All 6 paths should produce the corresponding `S0231:` Timber tag in logcat — that's the operator's probe.

---

## Rollback Plan

If on-device testing reveals a regression, `/spec-update S0231` reopens the ticket (status back to `Tactical` or earlier) and the `Timber.d("S0231:` tags are removed by that skill. Code rollback per phase as documented in prior Rollback Plan sections.
