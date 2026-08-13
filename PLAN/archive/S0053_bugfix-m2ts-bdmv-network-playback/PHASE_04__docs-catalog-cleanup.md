# Phase 04 — Docs, Catalog, Cleanup

**Strategic spec:** [`../S0053_bugfix-m2ts-bdmv-network-playback.md`](../S0053_bugfix-m2ts-bdmv-network-playback.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, 02, 03
**Blocks:** nothing — final phase
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

Update feature docs (EN/RU/UK), regenerate the class catalog, run dev log entries, and finalize the spec ticket to `Implemented`.

---

## Prerequisites

- [ ] Phases 01, 02, 03 are all ✅ Done.
- [ ] Build passes (`/build` → standard debug, no errors).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | — |
| `docs/FEATURES_RU.md` | Modified | — |
| `docs/FEATURES_UK.md` | Modified | — |
| `dev/CATALOG/app_v2.jsonl` | Modified (auto-generated) | — |
| `dev/CATALOG/app_v2.md` | Modified (auto-generated) | — |

---

## Steps

### Step 4.1 — Update FEATURES trilingual docs

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** — start of phase (Phase 03 ✅ assumed)

**Prompt for developer:**

> Add one bullet to the Video Playback section in each file. Add only if Phase 02-03 succeeded (i.e., `.m2ts` actually plays — not just the informative error). If only Phase 01 shipped, skip this step and mark `[x]` with a note "feature doc skipped — playback not yet working".
>
> **docs/FEATURES.md** (English):
> ```
> - Blu-ray Transport Stream (.m2ts, BDMV/STREAM) playback from network sources (SFTP, SMB, FTP) via transparent 192-byte BD-TS packet stripping.
> ```
>
> **docs/FEATURES_RU.md** (Russian):
> ```
> - Воспроизведение Blu-ray Transport Stream (.m2ts, BDMV/STREAM) с сетевых ресурсов (SFTP, SMB, FTP) через прозрачную обработку 192-байтовых BD-TS пакетов.
> ```
>
> **docs/FEATURES_UK.md** (Ukrainian):
> ```
> - Відтворення Blu-ray Transport Stream (.m2ts, BDMV/STREAM) з мережевих ресурсів (SFTP, SMB, FTP) через прозору обробку 192-байтових BD-TS пакетів.
> ```

**Verification:**

- `Grep` — `\.m2ts` present in `docs/FEATURES.md` OR step note records "skipped — playback not yet working".
- `Grep` — `\.m2ts` present in `docs/FEATURES_RU.md` OR step note records "skipped".
- `Grep` — `\.m2ts` present in `docs/FEATURES_UK.md` OR step note records "skipped".

**Status:** `[ ]` not done

---

### Step 4.2 — Regenerate class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** — independent of 4.1

**Prompt for developer:**

> Run catalog scan and render to pick up the three new classes (`BdTsStripDataSource`, `BdTsStripDataSourceFactory`, `BdTsPlaybackHelper`):
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
>
> Then set role and status for each new class via `set.ps1` (see `dev/CATALOG/README.md`):
> - `BdTsStripDataSource` — role: `BD-TS 192→188 byte adapter DataSource`, status: `active`
> - `BdTsStripDataSourceFactory` — role: `Factory for BdTsStripDataSource`, status: `active`
> - `BdTsPlaybackHelper` — role: `Extension: wraps DataSource.Factory for BD-TS paths`, status: `active`

**Verification:**

- `Grep` — `BdTsStripDataSource` present in `dev/CATALOG/app_v2.md`.
- `Grep` — `BdTsStripDataSourceFactory` present in `dev/CATALOG/app_v2.md`.
- `Grep` — `BdTsPlaybackHelper` present in `dev/CATALOG/app_v2.md`.

**Status:** `[ ]` not done

---

### Step 4.3 — Run dev log entries for all changed files

**Files:** `dev/CHANGELOG.md` (via script — never edit directly)
**Depends on:** Steps 4.1, 4.2

**Prompt for developer:**

> Run `.\scripts\add_to_dev_log.ps1` for each file modified across all phases. At minimum:
> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt" "S0053" "BD-TS format error detection + informative dialog"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values/strings.xml" "S0053" "Add error_bdts_format_title/message (EN)"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-ru/strings.xml" "S0053" "Add error_bdts_format_title/message (RU)"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-uk/strings.xml" "S0053" "Add error_bdts_format_title/message (UK)"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/BdTsStripDataSource.kt" "S0053" "New: BD-TS 192-byte packet stripper DataSource"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/BdTsStripDataSourceFactory.kt" "S0053" "New: factory for BdTsStripDataSource"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/BdTsPlaybackHelper.kt" "S0053" "New: wrapForBdTs extension"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SftpPlaybackHelper.kt" "S0053" "Wire BdTs wrapper for .m2ts paths"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SmbPlaybackHelper.kt" "S0053" "Wire BdTs wrapper for .m2ts paths"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/FtpPlaybackHelper.kt" "S0053" "Wire BdTs wrapper for .m2ts paths"
> .\scripts\add_to_dev_log.ps1 "dev/CATALOG/app_v2.jsonl" "S0053" "Catalog regen: BdTs* classes added"
> ```
> Also run for docs/FEATURES files if Step 4.1 added content.

**Verification:**

- `Grep` — `S0053` present in `dev/CHANGELOG.md` (at least one entry).

**Status:** `[ ]` not done

---

### Step 4.4 — Advance spec to Implemented

**Files:** `PLAN/spec-catalog.jsonl` (via `update.ps1` — never edit directly)
**Depends on:** Steps 4.1, 4.2, 4.3

**Prompt for developer:**

> ```powershell
> pwsh -File scripts/spec_catalog/update.ps1 -Id S0053 -Status Implemented
> ```

**Verification:**

- `pwsh -File scripts/spec_catalog/select.ps1 -Id S0053 -Format json` → `"status":"Implemented"`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [x] Every `Step 4.*` above is `[x] done`.
- [ ] `/spec-check S0053` run — result `Verified` or `Partial` (manual on-device test deferred). [deferred — runs in Stage F5]
- [ ] Strategic spec `Status:` set to `Verified` or `BlockNeedUserTest` depending on `/spec-check` outcome. [pending F5]
- [x] INDEX.md `Phases: 4 / 4 done`.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

Manual verification items (on-device, deferred):
- Play a real `.m2ts` file from SFTP/SMB/FTP; confirm video+audio.
- Seek to mid-file position in a 500+ MB `.m2ts` file; confirm seek works.
- Play `.m2ts` from local storage; confirm no regression.

---

## Rollback Plan

Revert doc edits. Catalog regen is idempotent — safe to re-run. Dev log entries in `dev/CHANGELOG.md` are append-only and cannot be rolled back (they remain as historical record).
