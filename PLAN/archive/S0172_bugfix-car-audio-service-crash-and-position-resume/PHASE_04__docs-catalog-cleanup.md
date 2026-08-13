# PHASE 04 — Docs, Catalog, and Cleanup

**Ticket:** S0172  
**Phase:** 04 of 04  
**Pillar:** Docs / audit / release gates

---

## Goal

Ensure feature docs, class catalog, string locale parity, and spec status are all updated after implementation. Plant debug Timber tags at changed flow entry points and move spec status to `BlockNeedUserTest`.

---

## Context

Per project mandatory post-change rules:
- `dev/CHANGELOG.md` — must be updated after every code change (handled per phase via `add_to_dev_log.ps1`).
- `docs/FEATURES*.md` — must be updated after any new user-facing feature.
- Class catalog (`dev/CATALOG/app_v2.jsonl` + `app_v2.md`) — must be regenerated after every `.kt` change.
- String locale parity — verify EN/RU/UK keys are present.
- Spec status must move to `BlockNeedUserTest` with debug tags planted.

---

## Steps

### Step 4.1 — Verify string locale parity

- [ ] Run:
```powershell
pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "playback_resumed_from"
```
- [ ] If exit code 1 — add missing RU/UK entries via `set-android-string.ps1` (Step 3.1a covers this).
- [ ] **Verification:** exit code 0.

### Step 4.2 — Update `docs/FEATURES.md` (EN)

- [ ] Open `docs/FEATURES.md`.
- [ ] Under the **Audio Playback** section (or create one if absent), add:

```markdown
- SFTP/SMB/FTP audio: playback position is saved automatically every 15 s and restored when the file is reopened (matching local file and video behavior).
```

- [ ] **Verification:** bullet is present and the section title is correct.

### Step 4.3 — Update `docs/FEATURES_RU.md` (RU)

- [ ] Open `docs/FEATURES_RU.md`.
- [ ] Add matching bullet in the same section:

```markdown
- SFTP/SMB/FTP аудио: позиция воспроизведения сохраняется каждые 15 с и восстанавливается при следующем открытии файла (как для локальных файлов и видео).
```

### Step 4.4 — Update `docs/FEATURES_UK.md` (UK)

- [ ] Open `docs/FEATURES_UK.md`.
- [ ] Add matching bullet in the same section:

```markdown
- SFTP/SMB/FTP аудіо: позиція відтворення зберігається кожні 15 с і відновлюється під час наступного відкриття файлу (як для локальних файлів і відео).
```

### Step 4.5 — Regenerate class catalog

- [ ] Run:
```powershell
pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
```
- [ ] If `AudioPlaybackService` is not in the catalog, add it via:
```powershell
pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class "AudioPlaybackService" -Role "MediaSessionService for SFTP/local audio background playback" -Status "active"
```
- [ ] Commit updated `dev/CATALOG/app_v2.jsonl` + `app_v2.md`.
- [ ] **Verification:** `grep -n "AudioPlaybackService" dev/CATALOG/app_v2.md` — entry exists.

### Step 4.6 — Plant `Timber.d("S0172: ...")` debug tags at flow entry points

Per `CLAUDE.md` debug verification tag rules, tags must be present while spec is in `BlockNeedUserTest`.

- [ ] Confirm the following tags are in place (planted in Phases 01, 02, 03):

| File | Tag line |
|------|----------|
| `AudioPlaybackService.kt` | `Timber.d("S0172: AudioPlaybackService startForeground called in onCreate")` |
| `AudioPlaybackService.kt` | (in `startPositionSaving`) `Timber.d("S0172: ...")` or verify it's covered by existing Timber calls |
| `PlayerMediaLoaderManager.kt` | `Timber.d("S0172: SFTP audio resume seekTo $savedPositionMs ms for $path")` |

- [ ] Run: `Select-String -Path "app_v2\src\main\java\com\sza\fastmediasorter\**\*.kt" -Pattern "S0172:" -Recurse`
- [ ] **Verification:** at least 2 distinct files returned.

### Step 4.7 — Run lint check

- [ ] Run: `.\gradlew.bat lintStandardDebug`
- [ ] Fix any new lint warnings in touched files (`AudioPlaybackService.kt`, `PlayerMediaLoaderManager.kt`).
- [ ] **Verification:** lint exits with code 0 (or same baseline as before this ticket).

### Step 4.8 — Update spec catalog: status → `BlockNeedUserTest`

- [ ] Run:
```powershell
pwsh -File scripts/spec_catalog/update.ps1 -Id S0172 -Status BlockNeedUserTest
```
- [ ] **Verification:**
```powershell
pwsh -File scripts/spec_catalog/select.ps1 -Id S0172 -Format json
```
Output: `"status":"BlockNeedUserTest"`.

### Step 4.9 — Update strategic spec file status field

- [ ] Open `PLAN/S0172_bugfix-car-audio-service-crash-and-position-resume.md`.
- [ ] Change `Status: Tactical` → `Status: BlockNeedUserTest`.
- [ ] **Verification:** `grep -n "^Status:" PLAN/S0172_bugfix-car-audio-service-crash-and-position-resume.md` returns `BlockNeedUserTest`.

### Step 4.10 — Final dev log entries

```powershell
.\scripts\add_to_dev_log.ps1 "docs/FEATURES.md" "FEATURES (EN/RU/UK)" "S0172 Phase 04: added SFTP position resume bullet to feature docs"
.\scripts\add_to_dev_log.ps1 "dev/CATALOG/app_v2.md" "Class catalog" "S0172 Phase 04: regenerated class catalog after AudioPlaybackService changes"
.\scripts\add_to_dev_log.ps1 "PLAN/S0172_bugfix-car-audio-service-crash-and-position-resume.md" "S0172" "Phase 04 complete — status moved to BlockNeedUserTest"
```

---

## Verification summary

| Check | Command / signal |
|-------|-----------------|
| String locale parity | `scripts/check_strings_localized.ps1 -KeyPrefix "playback_resumed_from"` → exit 0 |
| FEATURES.md updated (all 3 locales) | grep for new bullet in EN/RU/UK docs |
| Catalog regenerated | `grep -n "AudioPlaybackService" dev/CATALOG/app_v2.md` |
| Timber debug tags present | `Select-String ... -Pattern "S0172:"` — ≥ 2 files |
| Lint clean | `.\gradlew.bat lintStandardDebug` → exit 0 |
| Spec status = `BlockNeedUserTest` | `select.ps1 -Id S0172 -Format json` |

---

## On-device test protocol (before closing spec)

After Phase 04 is complete, the spec enters `BlockNeedUserTest`. Run the following manual test flow:

1. Connect to an SFTP share, navigate to an audio file.
2. Start playback, wait > 30 s.
3. Lock the device / switch to another app / kill the app.
4. Re-open the same file via the app.
5. **Expected:** seek to ≈ previous position + "Resumed from X:XX" toast.
6. Press a hardware media button while the app is dead (cold restart).
7. **Expected:** service starts without crash; media notification appears.
8. Collect logcat: `.\scripts\utils\extract-device-logs.ps1`
9. Search: `.\scripts\utils\search-log.ps1 -Pattern "S0172" -Errors`
10. If no `S0172` errors and behavior matches expectations → hand off to `/spec-check` for `Verified`.
