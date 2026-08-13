# Phase 07 — Docs and Catalog Cleanup

**Strategic spec:** [`../S0054_m2ts-playback-support.md`](../S0054_m2ts-playback-support.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress
**Depends on:** all previous phases
**Blocks:** — (final phase)
**Steps done:** 3 / 3
**Started:** 2026-05-04
**Completed:** —

---

## Objective

Update the trilingual feature documentation, regenerate the class catalog, and write a dev-log entry for every file modified across all phases of S0054.

---

## Prerequisites

- [ ] Phases 01–06 are all ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | existing |
| `docs/FEATURES_RU.md` | Modified | existing |
| `docs/FEATURES_UK.md` | Modified | existing |
| `dev/CATALOG/app_v2.jsonl` | Modified (auto-generated) | — |
| `dev/CATALOG/app_v2.md` | Modified (auto-generated) | — |

---

## Steps

### Step 07.1 — Update `docs/FEATURES.md`

**Files:** `docs/FEATURES.md`
**Depends on:** — start of phase

**Prompt for developer:**

> In section **7. Video Player**, add a bullet after existing `.m2ts` / BD-TS entries (or create one if absent):
>
> ```
> - Blu-ray Transport Stream (.m2ts): BD-TS 192-byte packet format auto-detected for local, SMB, SFTP, FTP, and cloud sources; 188-byte plain MPEG-TS files with .m2ts extension play without unnecessary stripping. Unsupported audio tracks (TrueHD, DTS-HD MA) are reported with a one-time notification listing detected codecs.
> ```

**Verification:**

- `Grep` — `BD-TS 192-byte packet format auto-detected` present in `docs/FEATURES.md`.
- `Grep` — `Unsupported audio tracks` present in `docs/FEATURES.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-04 — Verification 2/2 PASS. Files: docs/FEATURES.md (+1 bullet). Dev log recorded.

---

### Step 07.2 — Update `docs/FEATURES_RU.md` and `docs/FEATURES_UK.md`

**Files:** `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** Step 07.1

**Prompt for developer:**

> Add the same bullet in the matching section of both files.
>
> **RU:**
> ```
> - Blu-ray Transport Stream (.m2ts): формат пакетов BD-TS (192 байт) определяется автоматически для локальных, SMB, SFTP, FTP и облачных источников; файлы .m2ts с обычными 188-байтными пакетами воспроизводятся без лишней обработки. При наличии неподдерживаемых аудиодорожек (TrueHD, DTS-HD MA) пользователь видит однократное уведомление с перечнем кодеков.
> ```
>
> **UK:**
> ```
> - Blu-ray Transport Stream (.m2ts): формат пакетів BD-TS (192 байти) визначається автоматично для локальних, SMB, SFTP, FTP і хмарних джерел; файли .m2ts зі звичайними 188-байтними пакетами відтворюються без зайвої обробки. За наявності непідтримуваних аудіодоріжок (TrueHD, DTS-HD MA) користувач бачить одноразове повідомлення з переліком кодеків.
> ```

**Verification:**

- `Grep` — `BD-TS` present in `docs/FEATURES_RU.md`.
- `Grep` — `BD-TS` present in `docs/FEATURES_UK.md`.
- `Grep` — `TrueHD` present in `docs/FEATURES_RU.md`.
- `Grep` — `TrueHD` present in `docs/FEATURES_UK.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-04 — Verification 4/4 PASS. Files: FEATURES_RU.md (+1 bullet), FEATURES_UK.md (+1 bullet). Dev log recorded.

---

### Step 07.3 — Regenerate catalog and write dev-log entries

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Steps 07.1, 07.2

**Prompt for developer:**

> Run the catalog scan and render for `app_v2`:
>
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
>
> For every new `.kt` file introduced in S0054, set `role` and `status` via `set.ps1` (see `dev/CATALOG/README.md`):
>
> | File (class) | Role | Status |
> |---|---|---|
> | `TsPacketFormat` | domain | stable |
> | `TsPacketFormatDetector` | domain | stable |
>
> Then write dev-log entries for every file modified across all S0054 phases that does not already have an entry. Minimum set:
>
> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/TsPacketFormat.kt" "S0054" "New: TS packet format enum"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/TsPacketFormatDetector.kt" "S0054" "New: byte-level TS packet format detector"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/BdTsPlaybackHelper.kt" "S0054" "Add detectTsFormatSuspend + wrapForBdTs(TsPacketFormat)"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/LocalPlaybackHelper.kt" "S0054" "BD-TS detection + player recreation for local .m2ts"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CloudPlaybackHelper.kt" "S0054" "BD-TS detection via range request for cloud .m2ts"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SmbPlaybackHelper.kt" "S0054" "Replace extension-based wrapForBdTs with byte-level detection"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/SftpPlaybackHelper.kt" "S0054" "Replace extension-based wrapForBdTs with byte-level detection"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/FtpPlaybackHelper.kt" "S0054" "Replace extension-based wrapForBdTs with byte-level detection"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt" "S0054" "Audio-unsupported Toast in onTracksChanged for .m2ts"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values/strings.xml" "S0054" "Fix error_bdts_format_message; add warning_m2ts_audio_unsupported EN"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-ru/strings.xml" "S0054" "Fix error_bdts_format_message; add warning_m2ts_audio_unsupported RU"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-uk/strings.xml" "S0054" "Fix error_bdts_format_message; add warning_m2ts_audio_unsupported UK"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES.md" "S0054" "BD-TS auto-detect + audio diagnostics bullet"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_RU.md" "S0054" "BD-TS auto-detect + audio diagnostics bullet RU"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_UK.md" "S0054" "BD-TS auto-detect + audio diagnostics bullet UK"
> ```

**Verification:**

- `Grep` — `TsPacketFormat` present in `dev/CATALOG/app_v2.md` (catalog reflects new class).
- `Grep` — `TsPacketFormatDetector` present in `dev/CATALOG/app_v2.md`.
- `Grep` — `S0054` present at least once in `dev/CHANGELOG.md` (dev-log entry written).

**Status:** `[x]` done

**Step Log:**

- 2026-05-04 — Verification 3/3 PASS. Files: dev/CATALOG/app_v2.jsonl+app_v2.md regenerated; TsPacketFormat+TsPacketFormatDetector role=domain status=tested; dev log entries written. Dev log recorded.

---

## Phase Done Criteria

- [x] Every Step 07.* above is `[x] done`.
- [x] `dev/CATALOG/app_v2.jsonl` + `app_v2.md` are up-to-date and committed.
- [x] `Grep` for `TODO(phase-07)` returns zero hits.
- [ ] `/spec-check S0054` returns `Verified` (or `Partial` with documented reasons). PENDING-MANUAL-TESTS

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s) — docs and catalog only; no code or schema impact.
