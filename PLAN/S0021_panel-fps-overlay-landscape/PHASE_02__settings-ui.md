# Phase 02 — Settings UI

**Strategic spec:** [`../S0021_panel-fps-overlay-landscape.md`](../S0021_panel-fps-overlay-landscape.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** —
**Steps done:** 3 / 3
**Started:** —
**Completed:** —

---

## Objective

Surface a toggle "Show FPS over player" in `VideoSettingsFragment`, fully translated EN/RU/UK, distinct from the existing "Show VR FPS" toggle.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/layout/fragment_settings_video.xml` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/VideoSettingsFragment.kt` | Modified | ≤ 500 |

---

## Steps

### Step 02.1 — Add trilingual strings

**Files:** `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> In each of the three `strings.xml` files, add right next to the existing `settings_vr_show_fps`/`_desc` pair a second pair:
>
> - EN: `Show FPS over player` / `Display diagnostic FPS counter over the flat 2D player`
> - RU: `Показывать FPS поверх плеера` / `Отображать диагностический счётчик FPS поверх плоского 2D-плеера`
> - UK: `Показувати FPS над плеєром` / `Відображати діагностичний лічильник FPS над плоским 2D-плеєром`
>
> String resource keys: `settings_player_show_fps` and `settings_player_show_fps_desc`.

**Verification:**

- `Grep` — `name="settings_player_show_fps"` matches exactly 3 times across `values*/strings.xml`.
- `Grep` — `name="settings_player_show_fps_desc"` matches exactly 3 times across `values*/strings.xml`.

**Status:** `[x]` done

---

### Step 02.2 — Add switch row to `fragment_settings_video.xml`

**Files:** `fragment_settings_video.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> Below the existing "Show VR FPS switch" row in `fragment_settings_video.xml`, add a sibling `<LinearLayout>` row identical in structure but using:
>
> - `android:id="@+id/switchPlayerShowFps"`
> - title text → `@string/settings_player_show_fps`
> - description text → `@string/settings_player_show_fps_desc`
>
> Use the same default `android:checked="false"` and same margins/min-height as the VR FPS row to keep visual consistency.

**Verification:**

- `Grep` — `@+id/switchPlayerShowFps` matches exactly once in `fragment_settings_video.xml`.
- `Grep` — `@string/settings_player_show_fps` matches at least 1 time (title) and `@string/settings_player_show_fps_desc` at least 1 time (desc).

**Status:** `[x]` done

---

### Step 02.3 — Bind toggle in `VideoSettingsFragment`

**Files:** `VideoSettingsFragment.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Mirror the existing `switchVrShowFps` binding for the new `switchPlayerShowFps`:
>
> 1. In the listener-setup block (near line 257) add `binding.switchPlayerShowFps.setOnCheckedChangeListener { _, isChecked -> coroutineScope { ... viewModel.updateSettings(current.copy(playerShowFps = isChecked)) }`.
> 2. In the state-collection block (near line 322) add `binding.switchPlayerShowFps.isChecked = settings.playerShowFps`.
>
> Wrap the listener body in a guard that only calls `updateSettings` when `binding.switchPlayerShowFps.isChecked != settings.playerShowFps` to avoid the no-op-write pattern fixed in S0018 Phase 03.

**Verification:**

- `Grep` — `binding.switchPlayerShowFps.setOnCheckedChangeListener` matches exactly once in `VideoSettingsFragment.kt`.
- `Grep` — `binding.switchPlayerShowFps.isChecked = settings.playerShowFps` matches exactly once.
- `Grep` — `Log\.d\(` returns zero hits in this file (Timber-only invariant).

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — run `/build` for `standard debug`.
- [ ] All three locale files (`values/`, `values-ru/`, `values-uk/`) updated together.
- [ ] Dev log entries added for the three `strings.xml`, `fragment_settings_video.xml`, `VideoSettingsFragment.kt`.

---

## Handoff Notes to Next Phase

Phase 04 reads the bound state via `viewModel.settings` (or equivalent flow) to gate the overlay.

---

## Rollback Plan

Revert phase commit — UI-only addition, no behavioural change yet (overlay binding is in Phase 04).
