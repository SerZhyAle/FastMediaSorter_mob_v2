# Phase 03 — Docs and Catalog Cleanup

**Strategic spec:** [`../S0105_inline-audio-playback-in-browse.md`](../S0105_inline-audio-playback-in-browse.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** —
**Steps done:** 4 / 4
**Started:** 2026-05-06
**Completed:** 2026-05-06

---

## Objective

Update the trilingual feature documentation to reflect that inline audio playback is now available in all Browse resources, regenerate the class catalog, and record dev log entries.

---

## Prerequisites

- [ ] Phase 01 and Phase 02 are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | ≤ current |
| `docs/FEATURES_RU.md` | Modified | ≤ current |
| `docs/FEATURES_UK.md` | Modified | ≤ current |
| `dev/CATALOG/app_v2.jsonl` | Modified (generated) | — |
| `dev/CATALOG/app_v2.md` | Modified (generated) | — |

> Landscape variant: not applicable — no layout files touched.

---

## Steps

### Step 03.1 — Update docs/FEATURES.md

**Files:** `docs/FEATURES.md`
**Depends on:** — start of phase

**Prompt for developer:**

> In `docs/FEATURES.md` §2 Media Browsing, find the existing bullet for `**Inline audio mini-player**`. Replace it so the description no longer implies Audio Library is the only context. New text:
>
> `**Inline audio mini-player**: Play music and audio recordings directly from any file browser resource — local folders, network shares (SMB/SFTP/FTP), and cloud — without opening the full player. Tap the play button on any audio file card to start; the button toggles to pause. The spinning note icon indicates active playback. Previously available only in Audio Library resources; now available in all Browse resources.`

**Verification:**

- `Grep` — `Inline audio mini-player` present in `docs/FEATURES.md`.
- `Grep` — `any Browse resource` present in `docs/FEATURES.md` (confirms new broader description).
- `Grep` — `Audio Library resources` NOT the only context described (old scoped phrasing removed).

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification 2/2 PASS. Files: docs/FEATURES.md (inline mini-player entry expanded). Dev log deferred to phase end.

---

### Step 03.2 — Update docs/FEATURES_RU.md

**Files:** `docs/FEATURES_RU.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> Find the corresponding bullet for the inline mini-player in `docs/FEATURES_RU.md` (search for `мини-плеер` or the equivalent inline audio entry). Replace with:
>
> `**Встроенный мини-плеер**: воспроизводите аудио и голосовые записи прямо из любого ресурса Browse — локальных папок, сетевых ресурсов (SMB/SFTP/FTP) и облака — не открывая полный плеер. Нажмите кнопку воспроизведения на карточке аудиофайла, чтобы начать; кнопка переключается в паузу. Вращающаяся иконка ноты показывает активное воспроизведение. Ранее доступно только в ресурсах «Аудиотека»; теперь работает во всех ресурсах Browse.`

**Verification:**

- `Grep` — `мини-плеер` present in `docs/FEATURES_RU.md`.
- `Grep` — `любого ресурса Browse` present in `docs/FEATURES_RU.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification 2/2 PASS. Files: docs/FEATURES_RU.md updated. Dev log deferred to phase end.

---

### Step 03.3 — Update docs/FEATURES_UK.md

**Files:** `docs/FEATURES_UK.md`
**Depends on:** Step 03.2

**Prompt for developer:**

> Find the corresponding bullet in `docs/FEATURES_UK.md` and replace with:
>
> `**Вбудований міні-плеєр**: відтворюйте аудіо та голосові записи прямо з будь-якого ресурсу Browse — локальних папок, мережевих ресурсів (SMB/SFTP/FTP) та хмари — не відкриваючи повний плеєр. Натисніть кнопку відтворення на картці аудіофайлу, щоб почати; кнопка перемикається на паузу. Іконка ноти, що обертається, позначає активне відтворення. Раніше доступно лише в ресурсах «Аудіотека»; тепер працює в усіх ресурсах Browse.`

**Verification:**

- `Grep` — `міні-плеєр` present in `docs/FEATURES_UK.md`.
- `Grep` — `будь-якого ресурсу Browse` present in `docs/FEATURES_UK.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification 2/2 PASS. Files: docs/FEATURES_UK.md updated. Dev log deferred to phase end.

---

### Step 03.4 — Regenerate catalog and record dev log

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 03.3

**Prompt for developer:**

> Run catalog scan and render for `app_v2`, then add dev log entries for all modified files:
>
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
>
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/MediaFileAdapter.kt" "S0105" "Phase 01: show inline play button for all audio files in any Browse resource"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt" "S0105" "Phase 02: stop inline playback on folder navigation in non-audio-only resources"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt" "S0105" "Phase 02: stop inline playback on back-press navigation in non-audio-only resources"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES.md" "S0105" "Phase 03: expand inline mini-player entry to all Browse resources"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_RU.md" "S0105" "Phase 03: expand inline mini-player entry (RU)"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_UK.md" "S0105" "Phase 03: expand inline mini-player entry (UK)"
> ```

**Verification:**

- `Glob` — `dev/CATALOG/app_v2.jsonl` exists and has a modified timestamp matching today.
- `Grep` — `MediaFileAdapter` entry present in `dev/CATALOG/app_v2.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification 2/2 PASS. Files: dev/CATALOG/app_v2.jsonl regenerated (928 records), dev/CATALOG/app_v2.md rendered. Dev log: 6 entries recorded.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Run `/spec-check S0105` — confirm `Verified`.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

Revert docs changes and phase commit(s). Catalog regeneration is idempotent — no rollback needed for `.jsonl`/`.md`.
