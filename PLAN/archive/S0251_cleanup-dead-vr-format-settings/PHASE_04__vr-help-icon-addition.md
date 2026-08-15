# Phase 04 - VR Help Icon next to "Управление 3D-VR" header

**Strategic spec:** [`../S0251_cleanup-dead-vr-format-settings.md`](../S0251_cleanup-dead-vr-format-settings.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none (independent of Phases 01-03; touches different files)
**Blocks:** Phase 05, Phase 06
**Steps done:** 4 / 4
**Started:** 2026-05-19
**Completed:** 2026-05-19

---

## Objective

Add a help icon (`iconHelpVr`) next to the existing `headerVr` title in `fragment_settings_media_container.xml`. Wire the click in `MediaSettingsFragment` to open a `TooltipDialog` with the new strings `settings_3d_vr_help_title` and `settings_3d_vr_help_message`. Icon visibility follows `vrMediaSection.isAvailable` (same gate as the header itself). All input modes covered (keyboard / D-pad / mouse) per CLAUDE.md Rule 17.

---

## Prerequisites

- [ ] Owner-drafted EN/RU/UK content for `settings_3d_vr_help_title` and `settings_3d_vr_help_message` (Pre-Implementation Blocker §6.4).
- [ ] Working tree clean or on feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_media_container.xml` | Modified | ≤ 130 (currently 93) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/MediaSettingsFragment.kt` | Modified | ≤ 320 (currently 295) |
| `app_v2/src/main/res/values/strings.xml` | Modified | + 2 keys |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | + 2 keys |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | + 2 keys |

> The layout file uses a single `<TextView>` per section header. To accommodate an icon next to the VR header, the `headerVr` row is restructured into a horizontal `LinearLayout` containing the existing `TextView` (with `layout_weight="1"`) + a new `ImageButton` (`iconHelpVr`). Other section headers stay single-TextView (they have no help icon, so no symmetry violation).

---

## Steps

### Step 04.1 - Restructure `headerVr` row to host a help icon

**Files:** `app_v2/src/main/res/layout/fragment_settings_media_container.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> In `fragment_settings_media_container.xml`, locate the VR card (the `<com.google.android.material.card.MaterialCardView android:id="@+id/cardVr" ..>` block, lines 41-56). Inside its inner `<LinearLayout>`, replace the single `<TextView android:id="@+id/headerVr" ..>` element with a horizontal `<LinearLayout>` that contains:
>
> 1. The existing `<TextView android:id="@+id/headerVr" ..>` with `android:layout_width="0dp"` and `android:layout_weight="1"`. Move the `android:background`, `android:clickable`, `android:focusable`, `android:padding`, `android:textSize`, `android:textStyle` attributes to the new wrapping LinearLayout (background+padding) and to the TextView (text-style) appropriately - the wrapper takes the background and `padding=@dimen/settings_padding_vertical`, the TextView keeps `textSize` and `textStyle`.
> 2. A new `<ImageButton android:id="@+id/iconHelpVr" ..>` with `layout_width="@dimen/settings_help_icon_size"`, `layout_height="@dimen/settings_help_icon_size"`, `layout_marginEnd="@dimen/settings_padding_vertical"`, `background="?attr/selectableItemBackgroundBorderless"`, `src="@drawable/ic_help_outline_24"`, `app:tint="@color/text_color_secondary"`, `contentDescription="@string/settings_3d_vr_help_title"`, `focusable="true"`, `clickable="true"`.
>
> Critically, the existing click behavior of `headerVr` (collapse / expand the VR card) MUST continue to work. The accordion click listener in `MediaSettingsFragment.bindSectionToggle()` is attached to `binding.headerVr` directly. After this restructure, `binding.headerVr` is still the TextView - so the listener still fires when the user taps the title text but no longer fires when they tap the icon. That separation is intentional: tapping the title toggles the card; tapping the icon opens the tooltip.
>
> Do NOT touch other cards' headers (images / video / audio / documents / other) - they keep their single-TextView structure.

**Verification:**

- `Grep -n "@\+id/headerVr"` in this file → exactly 1 hit.
- `Grep -n "@\+id/iconHelpVr"` in this file → exactly 1 hit.
- `Grep -n "ic_help_outline_24"` in this file → at least 1 hit (the new icon).
- `Grep -n "@\+id/cardVr"` in this file → exactly 1 hit (intact).
- `Grep -n "android:layout_weight=\"1\""` inside the cardVr block → exactly 1 hit (the TextView inside cardVr).
- Open the layout in Android Studio preview - confirm header row renders as `▼ Управление 3D-VR    [?]` with the icon flush-right.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 5/5 PASS. Files: `app_v2/src/main/res/layout/fragment_settings_media_container.xml` (+30 LOC). Landscape counterpart expected: absent | actual: absent.

---

### Step 04.2 - Wire icon click in `MediaSettingsFragment`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/MediaSettingsFragment.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> In `MediaSettingsFragment.kt`, inside `onViewCreated()` after `setupSectionTitles()` and `attachChildFragments()`, add a call to a new helper `setupVrHeaderHelp()`. Implement it as:
>
> ```kotlin
> private fun setupVrHeaderHelp() {
>     val icon = binding.iconHelpVr
>     if (!vrMediaSection.isAvailable) {
>         icon.isVisible = false
>         return
>     }
>     icon.isVisible = true
>     icon.setOnClickListener {
>         com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(
>             requireContext(),
>             R.string.settings_3d_vr_help_title,
>             R.string.settings_3d_vr_help_message,
>         )
>     }
> }
> ```
>
> The `TooltipDialog.show(context, titleRes, messageRes)` API already exists - it is the same one used by other help icons in this screen (see `VideoSettingsFragment.setupSnapshotResourcePicker()` for an example). Place the call in `onViewCreated` AFTER `setupExpandableSections()` so the header is already laid out when we bind the icon listener.

**Verification:**

- `Grep -n "setupVrHeaderHelp"` in `MediaSettingsFragment.kt` → exactly 2 hits (declaration + call).
- `Grep -n "iconHelpVr"` in `MediaSettingsFragment.kt` → exactly 1 hit (inside the helper).
- `Grep -n "settings_3d_vr_help_title"` in `MediaSettingsFragment.kt` → exactly 1 hit.
- `Grep -n "settings_3d_vr_help_message"` in `MediaSettingsFragment.kt` → exactly 1 hit.
- `Grep -n "TooltipDialog.show"` in `MediaSettingsFragment.kt` → exactly 1 hit (the new call).
- `Grep -n "Log\.d\("` in this file → 0 hits (Timber-only policy).

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 6/6 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/MediaSettingsFragment.kt` (+15 LOC).

---

### Step 04.3 - Add EN/RU/UK strings for the new help dialog

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 04.2

**Prompt for developer:**

> Add two new string keys per locale. Insert each new pair next to the existing `vr_settings_block_title` and adjacent VR strings (around line 13 in `values/strings.xml`, line 26 in `values-ru/strings.xml`, line 25 in `values-uk/strings.xml`).
>
> Owner-resolved short variant (§6.4). Drop these EXACT strings in verbatim - do not paraphrase or expand.
>
> **English (`values/strings.xml`):**
>
> ```xml
> <string name="settings_3d_vr_help_title">About 3D VR mode</string>
> <string name="settings_3d_vr_help_message">3D VR provides immersive video and photo playback on headsets such as Meta Quest 3 and Android XR. On devices without an XR runtime, the master toggle stays inactive. The app detects video format (SBS, OU, 360°, VR180) automatically; manual override is available in the player dialog.</string>
> ```
>
> **Russian (`values-ru/strings.xml`):**
>
> ```xml
> <string name="settings_3d_vr_help_title">О режиме 3D VR</string>
> <string name="settings_3d_vr_help_message">3D VR — это погружённый просмотр видео и фото на гарнитурах типа Meta Quest 3 и Android XR. На устройствах без поддержки XR-среды переключатель остаётся недоступным. Приложение само определяет формат видео (SBS, OU, 360°, VR180); ручной выбор доступен в диалоге плеера.</string>
> ```
>
> **Ukrainian (`values-uk/strings.xml`):**
>
> ```xml
> <string name="settings_3d_vr_help_title">Про режим 3D VR</string>
> <string name="settings_3d_vr_help_message">3D VR — це занурений перегляд відео та фото на гарнітурах типу Meta Quest 3 і Android XR. На пристроях без підтримки XR-середовища перемикач залишається недоступним. Додаток сам визначає формат відео (SBS, OU, 360°, VR180); ручний вибір доступний у діалозі плеера.</string>
> ```
>
> Note for the Russian copy: `..` (two dots), not `...`. `ё` is mandatory in `погружённый`, `плеера`. Tone: must pass `docs/COMMUNICATION_POLICY.md` §6 tone checklist - dry, professional, no exclamation marks, no condescension. The supplied wording was vetted against the checklist at resolution time.

**Verification:**

- `Grep -n "settings_3d_vr_help_title"` in `app_v2/src/main/res/values/strings.xml` → exactly 1 hit (English).
- `Grep -n "settings_3d_vr_help_message"` in `app_v2/src/main/res/values/strings.xml` → exactly 1 hit.
- `Grep -n "settings_3d_vr_help_title"` in `app_v2/src/main/res/values-ru/strings.xml` → exactly 1 hit (Russian).
- `Grep -n "settings_3d_vr_help_message"` in `app_v2/src/main/res/values-ru/strings.xml` → exactly 1 hit.
- `Grep -n "settings_3d_vr_help_title"` in `app_v2/src/main/res/values-uk/strings.xml` → exactly 1 hit (Ukrainian).
- `Grep -n "settings_3d_vr_help_message"` in `app_v2/src/main/res/values-uk/strings.xml` → exactly 1 hit.
- Strings pass COMMUNICATION_POLICY §6 checklist (no exclamation marks, no informal contractions, no jargon left undefined, no implied-fault wording).
- Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_3d_vr"` → exit code 0.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 8/8 PASS. Files: EN/RU/UK `strings.xml` (+2 keys each). `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_3d_vr"` exit 0. COMMUNICATION_POLICY §6 PASS.

---

### Step 04.4 - Dev log + run target builds

**Files:** dev log
**Depends on:** Steps 04.1 - 04.3

**Prompt for developer:**

> Log entries:
>
> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/layout/fragment_settings_media_container.xml" "S0251" "Phase 04: wrap headerVr in horizontal LinearLayout; add iconHelpVr ImageButton"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/MediaSettingsFragment.kt" "S0251" "Phase 04: add setupVrHeaderHelp; open TooltipDialog on icon click"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values/strings.xml" "S0251" "Phase 04: add settings_3d_vr_help_title / _message (EN)"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-ru/strings.xml" "S0251" "Phase 04: add settings_3d_vr_help_title / _message (RU)"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-uk/strings.xml" "S0251" "Phase 04: add settings_3d_vr_help_title / _message (UK)"
> ```
>
> Run `/build` for `standardDebug`, `vrDebug`, `noLegalDebug`. In `standardDebug` the help icon is hidden (gate `vrMediaSection.isAvailable == false`); in `vrDebug`/`noLegalDebug` it is visible.

**Verification:**

- `Grep -n "S0251.*Phase 04"` in `dev/CHANGELOG.md` → exactly 5 hits.
- All three flavor builds pass compile + assembly.
- Manual confirmation on a `vrDebug` install: header `▼ Управление 3D-VR    [?]` renders, tapping the icon opens the dialog with the new text; tapping the title collapses/expands the card (existing accordion behavior preserved).
- Manual confirmation on a `standardDebug` install: the entire `cardVr` is hidden (existing behavior - icon hidden by extension).

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification PASS. Files: `dev/CHANGELOG.md`. Dev log entries expected 5 | actual 5. Builds PASS: `.\gradlew.bat assembleStandardDebug "-Pchaquopy.enabled=false"` exit 0; `.\gradlew.bat assembleVrDebug "-Pchaquopy.enabled=false"` exit 0; `.\gradlew.bat assembleNoLegalDebug "-Pchaquopy.enabled=true" --no-configuration-cache` exit 0. Manual device confirmation deferred to Phase 06 BlockNeedUserTest handoff.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] `/build` exits cleanly for `standardDebug`, `vrDebug`, `noLegalDebug`.
- [x] Locale audit `scripts/check_strings_localized.ps1 -KeyPrefix "settings_3d_vr"` exits 0.
- [x] Strings pass COMMUNICATION_POLICY §6 checklist.
- [x] Dev log carries 5 new S0251 Phase 04 entries.
- [x] Manual device check (vr-capable flavor): deferred to Phase 06 BlockNeedUserTest operator handoff.

---

## Handoff Notes to Next Phase

`fragment_settings_media_container.xml` now hosts an icon binding `iconHelpVr` next to the VR title. `MediaSettingsFragment` owns its click handler. Three locales have the two new keys. Phase 05 can safely remove the obsolete `settings_vr_help_title` / `settings_vr_help_message` and the rest of the dead VR-format strings - nothing in the codebase references them after Phase 01.

---

## Rollback Plan

Revert all five file diffs. The VR header returns to its single-TextView form; no string keys leak (the new keys are removed). No persisted state changed.
