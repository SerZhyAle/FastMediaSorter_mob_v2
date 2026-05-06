# Phase 04 — docs-catalog-cleanup

**Strategic spec:** [`../S0099_sftp-concurrent-access-fix.md`](../S0099_sftp-concurrent-access-fix.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** —
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

Regenerate the code catalog, update feature docs in all three locales, write dev changelog entries for every modified file, and advance spec status to Implemented.

---

## Prerequisites

- [ ] Phases 01, 02, 03 are all ✅ Done.
- [ ] `/build` exits 0 on the combined changes.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Modified (generated) | — |
| `dev/CATALOG/app_v2.md` | Modified (generated) | — |
| `docs/FEATURES.md` | Modified | — |
| `docs/FEATURES_RU.md` | Modified | — |
| `docs/FEATURES_UK.md` | Modified | — |
| `dev/CHANGELOG.md` | Modified | — |

---

## Steps

### Step 04.1 — Regenerate app_v2 catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** — start of phase

**Prompt for developer:**

> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```

**Verification:**

- `Glob` — `dev/CATALOG/app_v2.jsonl` modified timestamp is today.

**Status:** `[ ]` not done

---

### Step 04.2 — Update FEATURES docs (EN / RU / UK)

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> In `docs/FEATURES.md`, find the SFTP section and append the following bullet:
> ```
> - Parallel playback and file copy from the same SFTP source without mutual interruptions.
> ```
>
> In `docs/FEATURES_RU.md`, find the SFTP section and append:
> ```
> - Параллельное воспроизведение и копирование файлов с одного SFTP-источника без взаимных прерываний.
> ```
>
> In `docs/FEATURES_UK.md`, find the SFTP section and append:
> ```
> - Паралельне відтворення та копіювання файлів з одного SFTP-джерела без взаємних переривань.
> ```

**Verification:**

- `Grep` — `Parallel playback and file copy from the same SFTP` present in `docs/FEATURES.md`.
- `Grep` — `Параллельное воспроизведение и копирование файлов с одного SFTP` present in `docs/FEATURES_RU.md`.
- `Grep` — `Паралельне відтворення та копіювання файлів з одного SFTP` present in `docs/FEATURES_UK.md`.

**Status:** `[ ]` not done

---

### Step 04.3 — Dev changelog entries

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 04.2

**Prompt for developer:**

> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt" "S0099" "Add playbackConnectionPool; isolate ExoPlayer sessions from FILE_OPS sessions"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpDownloadExhaustedException.kt" "S0099" "New typed exception for exhausted SFTP download retries"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt" "S0099" "Replace single-retry downloadFile with 3-attempt backoff loop"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/network/SftpFileOperationHandler.kt" "S0099" "Map SftpDownloadExhaustedException to user-friendly localized error in bridge copy"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values/strings.xml" "S0099" "Add error_sftp_copy_failed_server/access_denied/connection_limit (EN)"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-ru/strings.xml" "S0099" "Add error_sftp_copy_failed_server/access_denied/connection_limit (RU)"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-uk/strings.xml" "S0099" "Add error_sftp_copy_failed_server/access_denied/connection_limit (UK)"
> ```

**Verification:**

- `Grep` — `S0099` present in `dev/CHANGELOG.md`.

**Status:** `[ ]` not done

---

### Step 04.4 — Advance spec status to Implemented

**Files:** `PLAN/spec-catalog.jsonl`
**Depends on:** Step 04.3

**Prompt for developer:**

> ```powershell
> pwsh -File scripts/spec_catalog/update.ps1 -Id S0099 -Status Implemented
> ```

**Verification:**

- `pwsh -File scripts/spec_catalog/select.ps1 -Id S0099 -Format json` → `"status":"Implemented"`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every step above is `[x] done`.
- [ ] `dev/CHANGELOG.md` has `S0099` entries for all 7 modified files.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated.
- [ ] Catalog `.jsonl` updated.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate. Run `/spec-check S0099`.

---

## Rollback Plan

Revert catalog regen commit. No code changes in this phase.
