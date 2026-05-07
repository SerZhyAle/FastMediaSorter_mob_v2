# Phase 04 — Docs and Catalog Cleanup

**Strategic spec:** [`../S0104_playback-order-mode.md`](../S0104_playback-order-mode.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all prior phases
**Blocks:** —
**Steps done:** 4 / 4
**Started:** 2026-05-06
**Completed:** 2026-05-06

---

## Objective

Update the trilingual feature documentation, regenerate the code catalog, and add all dev log entries for changed files.

---

## Prerequisites

- [ ] Phases 01–03 are ✅ Done.
- [ ] `/spec-check S0104` has been run at least once (may be Partial — final Verified comes after this phase).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | current + 3 |
| `docs/FEATURES_RU.md` | Modified | current + 3 |
| `docs/FEATURES_UK.md` | Modified | current + 3 |
| `dev/CATALOG/app_v2.jsonl` | Modified | regenerated |
| `dev/CATALOG/app_v2.md` | Modified | regenerated |

---

## Steps

### Step 4.1 — Update `docs/FEATURES.md`

**Files:** `docs/FEATURES.md`
**Depends on:** Phases 01–03 done

**Prompt for developer:**

> In `docs/FEATURES.md`:
>
> - In **§7 Video Player**, add a new bullet after the existing "Playback position save & restore" bullet:
>   > **Playback order modes**: Tap the order button in the command panel to cycle between Loop List (repeat forever), Play Through (stop at end), Shuffle (random infinite), and Repeat One — each mode is remembered separately for audio and video.
>
> - In **§9 Audio Player**, add the same bullet after the "Background playback" bullet.

**Verification:**

- `Grep` — `Playback order modes` present in `docs/FEATURES.md` (exactly 2 occurrences, one per section).

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification 1/1 PASS (2 occurrences). Dev log recorded.

---

### Step 4.2 — Update `docs/FEATURES_RU.md` and `docs/FEATURES_UK.md`

**Files:** `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** Step 4.1

**Prompt for developer:**

> Mirror the §7 and §9 bullets added in Step 4.1 into the RU and UK files, translated consistently with the string values from Step 3.1:
>
> **RU (both §7 and §9):**
> > **Режимы порядка воспроизведения**: нажмите кнопку порядка в командной панели, чтобы переключаться между «По кругу» (бесконечный цикл), «До конца» (остановиться в конце списка), «Вперемішку» (случайный бесконечный) и «Повторять один». Режим запоминается отдельно для аудио и видео.
>
> **UK (both §7 and §9):**
> > **Режими порядку відтворення**: натисніть кнопку порядку в командній панелі, щоб перемикатися між «По колу» (нескінченний цикл), «До кінця» (зупинитися в кінці списку), «Перемішати» (випадковий нескінченний) та «Повторювати один». Режим запам'ятовується окремо для аудіо та відео.

**Verification:**

- `Grep` — `Режими порядку відтворення` present in `docs/FEATURES_UK.md` (2 occurrences).
- `Grep` — `Режимы порядка воспроизведения` present in `docs/FEATURES_RU.md` (2 occurrences).

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification 2/2 PASS (UK 2 occ., RU 2 occ.). Dev log recorded.

---

### Step 4.3 — Regenerate code catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Steps 4.1–4.2

**Prompt for developer:**

> Run the catalog scan and render scripts:
> ```powershell
> & "/c/Program Files/PowerShell/7/pwsh.exe" -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> & "/c/Program Files/PowerShell/7/pwsh.exe" -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
>
> For the new `PlaybackOrderMode.kt`, set `role` and `status` manually via:
> ```powershell
> & "/c/Program Files/PowerShell/7/pwsh.exe" -File dev/CATALOG/scripts/set.ps1 `
>     -Module app_v2 `
>     -Class PlaybackOrderMode `
>     -Role "Domain enum for playback order cycling" `
>     -Status Active
> ```
> Then re-render.

**Verification:**

- `Grep` — `PlaybackOrderMode` present in `dev/CATALOG/app_v2.md`.
- `Glob` — `dev/CATALOG/app_v2.jsonl` exists (modified timestamp updated).

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification 2/2 PASS (PlaybackOrderMode in app_v2.md, jsonl updated). Dev log recorded.

---

### Step 4.4 — Add dev log entries and advance spec status

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 4.3

**Prompt for developer:**

> Run `add_to_dev_log.ps1` for every file changed across all phases. Minimum set:
> ```powershell
> $s = ".\scripts\add_to_dev_log.ps1"
> & $s "app_v2/src/main/java/com/sza/fastmediasorter/domain/model/PlaybackOrderMode.kt" "S0104" "Add PlaybackOrderMode enum"
> & $s "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlPreferences.kt" "S0104" "Add playback order prefs keys"
> & $s "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt" "S0104" "Add playbackOrderMode + shuffle management"
> & $s "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerNavigationCoordinator.kt" "S0104" "Mode-aware nextFile/previousFile"
> & $s "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioServiceController.kt" "S0104" "Add applyPlaybackOrderMode()"
> & $s "app_v2/src/main/res/drawable/ic_loop_list.xml" "S0104" "Add loop-list icon"
> & $s "app_v2/src/main/res/menu/overflow_menu_player.xml" "S0104" "Add menu_playback_order item"
> & $s "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt" "S0104" "Add PLAYBACK_ORDER command"
> & $s "app_v2/src/main/res/layout/activity_player_unified.xml" "S0104" "Add btnPlaybackOrderCmd (portrait)"
> & $s "app_v2/src/main/res/layout-land/activity_player_unified.xml" "S0104" "Add btnPlaybackOrderCmd (landscape)"
> & $s "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt" "S0104" "Wire playback order button"
> & $s "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt" "S0104" "Delegate onPlaybackOrderClicked"
> & $s "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt" "S0104" "onPlaybackOrderClicked handler + prefs restore"
> & $s "docs/FEATURES.md" "S0104" "Add playback order modes feature bullet"
> & $s "docs/FEATURES_RU.md" "S0104" "Mirror playback order modes bullet (RU)"
> & $s "docs/FEATURES_UK.md" "S0104" "Mirror playback order modes bullet (UK)"
> ```
>
> Then advance the spec catalog status to `Implemented`:
> ```powershell
> & "/c/Program Files/PowerShell/7/pwsh.exe" -File scripts/spec_catalog/update.ps1 -Id S0104 -Status Implemented
> ```

**Verification:**

- `Grep` — `S0104` present in `dev/CHANGELOG.md` (at least 16 entries).
- Run `pwsh -File scripts/spec_catalog/select.ps1 -Id S0104 -Format json` → `"status": "Implemented"`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification 2/2 PASS (38 S0104 entries in CHANGELOG, status=Implemented).

---

## Phase Done Criteria

- [x] Every `Step 4.*` above is `[x] done`.
- [x] `docs/FEATURES.md`, `_RU`, `_UK` each contain exactly 2 occurrences of the playback order bullet.
- [x] `dev/CATALOG/app_v2.md` contains `PlaybackOrderMode`.
- [x] Spec catalog status is `Implemented`.
- [ ] Run `/spec-check S0104` to advance to `Verified`. MANUAL-REQUIRED

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit. Documentation-only changes; no functional code touched.
