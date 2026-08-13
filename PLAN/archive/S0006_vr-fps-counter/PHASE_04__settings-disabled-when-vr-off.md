# Phase 04 — Settings Row Disabled When VR Globally Off

**Strategic spec:** [`../S0006_vr-fps-counter.md`](../S0006_vr-fps-counter.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

Per strategic §6.3: when the global VR kill-switch (`AppSettings.disable3dVr == true`) is enabled, the "Show VR FPS" row stays visible but becomes disabled and shows the hint "Available when VR is enabled". The persisted `vrShowFps` value is preserved across the disabled→enabled transition.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done.
- [ ] EN/RU/UK string keys planned: `settings_vr_show_fps_hint_disabled`.
- [ ] Working tree clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_video.xml` | Modified | ≤ 1100 |
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 1500 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 1500 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/VideoSettingsFragment.kt` | Modified | ≤ 500 |

---

## Steps

### Step 04.1 — Add disabled-hint TextView to layout

**Files:** `app_v2/src/main/res/layout/fragment_settings_video.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Inside the existing `<!-- Show VR FPS switch -->` LinearLayout block, in the inner vertical `LinearLayout` that holds the title + description TextViews, append a third `TextView` with `android:id="@+id/tvVrShowFpsDisabledHint"`, `android:text="@string/settings_vr_show_fps_hint_disabled"`, `android:textColor="@color/text_color_secondary"`, `android:textSize="@dimen/toggler_desc_text_size"`, `android:visibility="gone"`. It must sit immediately after the existing description TextView, inside the same vertical LinearLayout.

**Verification:**

- `Grep` — `tvVrShowFpsDisabledHint` matches at least once in the layout file.
- `Grep` — `@string/settings_vr_show_fps_hint_disabled` matches at least once in the layout file.

**Status:** `[x] done`

**Step Log:**

- 2026-04-28 — Verification 2/2 PASS. Files: `fragment_settings_video.xml` (+9 LOC).

---

### Step 04.2 — Add EN/RU/UK strings for disabled hint

**Files:**
- `app_v2/src/main/res/values/strings.xml`
- `app_v2/src/main/res/values-ru/strings.xml`
- `app_v2/src/main/res/values-uk/strings.xml`

**Depends on:** Step 04.1

**Prompt for developer:**

> Add a new string resource `settings_vr_show_fps_hint_disabled` to all three locale files, immediately after the existing `settings_vr_show_fps_desc` entry.
>
> - `values/strings.xml`: `Available when VR is enabled`
> - `values-ru/strings.xml`: `Доступно при включённом VR`
> - `values-uk/strings.xml`: `Доступно за увімкненого VR`

**Verification:**

- `Grep` — `settings_vr_show_fps_hint_disabled">Available when VR is enabled</string>` matches in `values/strings.xml`.
- `Grep` — `settings_vr_show_fps_hint_disabled">Доступно при включённом VR</string>` matches in `values-ru/strings.xml` (note: `ё`, not `е`).
- `Grep` — `settings_vr_show_fps_hint_disabled">Доступно за увімкненого VR</string>` matches in `values-uk/strings.xml`.

**Status:** `[x] done`

**Step Log:**

- 2026-04-28 — Verification 3/3 PASS. Files: EN/RU/UK strings.xml (+1 line each).

---

### Step 04.3 — Bind row enable state and hint visibility to `disable3dVr`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/VideoSettingsFragment.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> In `observeData()` inside the `if (BuildConfig.SUPPORT_VR_PLAYER)` block, after `binding.switchVrShowFps.isChecked = settings.vrShowFps`, add three lines that compute `val vrGloballyEnabled = !settings.disable3dVr` and apply:
>
> 1. `binding.switchVrShowFps.isEnabled = vrGloballyEnabled`
> 2. `binding.tvVrShowFpsDisabledHint.visibility = if (vrGloballyEnabled) View.GONE else View.VISIBLE`
>
> Use `android.view.View` import if not already present. Do not modify the `setOnCheckedChangeListener` block — the persisted value must remain editable conceptually, only the runtime UI is gated.

**Verification:**

- `Grep` — `binding\.switchVrShowFps\.isEnabled = vrGloballyEnabled` matches at least once.
- `Grep` — `binding\.tvVrShowFpsDisabledHint\.visibility` matches at least once.
- `Grep` — `val vrGloballyEnabled = !settings\.disable3dVr` matches at least once.

**Status:** `[x] done`

**Step Log:**

- 2026-04-28 — Verification 3/3 PASS. Files: `VideoSettingsFragment.kt` (+3 LOC). `View` import already present.

---

## Phase Done Criteria

- [x] Every `Step 04.*` is `[x] done`.
- [x] Project compiles — `/build` `standard debug` PASS + `vr debug` PASS (auto-build — PASS).
- [x] Dev log entry added for each modified file via `.\scripts\add_to_dev_log.ps1`.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.

---

## Handoff Notes to Next Phase

The settings UI is now feature-complete per the strategic spec. Phase 05 only writes user-facing documentation and regenerates the catalog.

---

## Rollback Plan

Revert phase commit; the persisted `vrShowFps` value is unaffected by UI gating, so user data is never at risk.
