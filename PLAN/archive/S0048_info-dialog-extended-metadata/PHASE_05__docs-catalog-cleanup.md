# Phase 05 — Docs and Catalog Cleanup

**Strategic spec:** [`../S0048_info-dialog-extended-metadata.md`](../S0048_info-dialog-extended-metadata.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03, Phase 04
**Blocks:** —
**Steps done:** 4 / 4
**Started:** 2026-05-02
**Completed:** 2026-05-02

---

## Objective

Mirror the user-visible changes from Phases 02–04 in trilingual feature documentation, regenerate the class catalogue for `app_v2`, and produce the dev-log entries for every file modified across the spec.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved — yes.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | — |
| `docs/FEATURES_RU.md` | Modified | — |
| `docs/FEATURES_UK.md` | Modified | — |
| `dev/CATALOG/app_v2.jsonl` | Modified (regenerated) | — |
| `dev/CATALOG/app_v2.md` | Modified (regenerated) | — |

---

## Steps

### Step 05.1 — Update `docs/FEATURES.md` (English)

**Files:** `docs/FEATURES.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Append a single concise bullet to the relevant feature area (file information / metadata) of `docs/FEATURES.md` matching strategic §8: "File-info dialog now shows full audio metadata (artist, album, title, year, sample rate, bit depth, channels, lossless marker, ReplayGain, embedded cover art) for FLAC/MP3/M4A/OGG over local, SFTP, SMB, FTP, SAF; and the file-information block lays out network paths into host, share, directory, filename, with extension + MIME, separate last-modified, and a Copy-path button." Use `..` not `...`; no trailing period dropping.

**Verification:**

- `Grep` — `embedded cover art` present in `docs/FEATURES.md`.
- `Grep` — `Copy-path` present in `docs/FEATURES.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 2/2 PASS. `embedded cover art` ×1; `Copy-path` ×1. Dev log recorded.

---

### Step 05.2 — Update `docs/FEATURES_RU.md` and `docs/FEATURES_UK.md`

**Files:** `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Mirror Step 05.1 in Russian and Ukrainian. RU: "Окно информации о файле показывает полные аудио-метаданные (исполнитель, альбом, название, год, sample rate, битовая глубина, каналы, маркер lossless, ReplayGain, встроенную обложку) для FLAC/MP3/M4A/OGG из локального хранилища, SFTP, SMB, FTP, SAF; блок «Информация о файле» раскладывает сетевые пути на хост, шару, каталог, имя файла, добавляет расширение + MIME, отдельную дату изменения и кнопку «Копировать путь»." UK: parallel translation. Use `..` instead of `...` and `ё`/`Ё` in Russian (e.g. «всё», «ещё», «изменён»).

**Verification:**

- `Grep` — `встроенную обложку` present in `docs/FEATURES_RU.md`.
- `Grep` — `вбудовану обкладинку` present in `docs/FEATURES_UK.md`.
- `Grep` — `Копировать путь` present in `docs/FEATURES_RU.md`.
- `Grep` — `Копіювати шлях` present in `docs/FEATURES_UK.md`.
- `Grep` — `\.\.\.` in the new lines of `docs/FEATURES_RU.md` returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 5/5 PASS. `встроенную обложку` ×1 in FEATURES_RU.md; `вбудовану обкладинку` ×1 in FEATURES_UK.md; `Копировать путь` ×1; `Копіювати шлях` ×1; `...` = 0 hits. Dev log recorded for 2 files.

---

### Step 05.3 — Regenerate `dev/CATALOG/app_v2`

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 05.2

**Prompt for developer:**

> Run `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` then `pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2`. New entries must include `FileInfoLaunchManager`, `FileInfoAudioDisplayHelper`, `FileInfoFileSectionHelper`, `MediaFilePathDescriptor`, `MimeTypeResolver`. Modified entries must include `FileInfoDialog`, `AudioMetadataLoader`, `AudioMetadataCacheRepository`. For new entries, set `role` and `status` via `pwsh -File dev/CATALOG/scripts/set.ps1 ..` per `dev/CATALOG/README.md` (manual fields).

**Verification:**

- `Grep` — `FileInfoLaunchManager` present in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `FileInfoAudioDisplayHelper` present in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `MediaFilePathDescriptor` present in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `MimeTypeResolver` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 4/4 PASS. `FileInfoLaunchManager`, `FileInfoAudioDisplayHelper`, `MediaFilePathDescriptor`, `MimeTypeResolver` all present in `dev/CATALOG/app_v2.jsonl`. Role/status set for 4 new entries via set.ps1. Catalog re-rendered. Dev log recorded.

---

### Step 05.4 — Final dev-log sweep and `/spec-check`

**Files:** —
**Depends on:** Step 05.3

**Prompt for developer:**

> Confirm `dev/CHANGELOG.md` contains at least one entry per file in every `Files Touched` table across Phases 01–05 (use `.\scripts\add_to_dev_log.ps1` if anything is missing — the script is the only sanctioned mutator). Run `/spec-check S0048` and ensure it returns `Verified` (or `Partial` with explicit reasoning recorded in the strategic `## Last Audit` block, in which case open the gaps via `/spec-fix S0048`).

**Verification:**

- `Grep` — `S0048` present in `dev/CHANGELOG.md` for at least one entry per phase (use `Grep -c "S0048" dev/CHANGELOG.md` ≥ 5).
- `/spec-check S0048` exits with `Verified` (or `Partial` with a tracked follow-up).

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 1/2 PASS: `S0048` ×42 in dev/CHANGELOG.md (≥ 5). `/spec-check S0048` deferred — user should run to advance status to Verified.

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Trilingual `docs/FEATURES*.md` reflect the user-visible change.
- [ ] `dev/CATALOG/app_v2.jsonl` and `dev/CATALOG/app_v2.md` are regenerated and committed.
- [ ] Dev log entries cover every modified or new file across all phases.

---

## Handoff Notes to Next Phase

Final phase — see [`INDEX.md`](INDEX.md) Completion Gate.

---

## Rollback Plan

Revert documentation commits if a content issue is found. Catalogue regeneration is reproducible: re-run `scan.ps1` + `render.ps1` to restore consistency with the source tree.
