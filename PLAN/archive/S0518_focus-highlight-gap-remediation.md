# S0518 - Focus-highlight gap remediation (ratchet baseline to 0)

**Status:** Archived
**Priority:** 40
**Date:** 2026-06-18
**Tier:** 3 - Moderate (ad-hoc)
**Origin:** follow-up of S0507 (focus-highlight coverage gate)

> **Scope:** Compact spec (Simple path). Drive the S0507 baseline (63) to its justified minimum by covering real focus targets and teaching the gate to recognise already-handled cases.

---

## Goal (RU)

S0507 построил механический гейт `scripts/quality/assert-focus-highlight.ps1` и зафиксировал baseline на текущем долге = 63 интерактивных вью без распознанной focus-индикации (Rule 16). Этот тикет сводит baseline к 0, разделяя 63 пробела на три класса: реальные D-pad-цели (добавить focus-foreground), кастомные виджеты с собственной focus-логикой (научить гейт распознавать класс), не-цели — модальные скримы, блокировщики passthrough, marquee-драйверы (документированный id-whitelist в гейте).

## Проблема

63 focusable/clickable вью не несут *распознанной* focus-индикации. Часть из них - настоящие цели без обводки (карточки списков, строки настроек, цветовые свотчи, мини-плеер). Часть - уже покрыты, но гейт их не видит: `?android:attr/selectableItemBackground` (android-namespace ripple) и кастомные виджеты, выставляющие фон в коде. Часть - полноэкранные скримы/контейнеры, которым обводка по периметру экрана бессмысленна.

## Acceptance criteria

1. Гейт `assert-focus-highlight.ps1` распознаёт `?android:attr/selectableItemBackground[Borderless]` (с android-namespace), а не только `?attr/..`.
2. Гейт распознаёт кастомные виджеты с собственной focus-логикой: `SettingsToggleRow` (ставит `selectableItemBackground` в конструкторе), `TranslationOverlayView` (кастомный интерактивный canvas-overlay).
3. Гейт несёт документированный id-whitelist для подтверждённых не-целей (модальные скримы, блокировщики passthrough, корневые контейнеры, marquee-драйвер); whitelist - точечный по id, новый кликабельный контейнер по-прежнему флагуется.
4. Реальные D-pad-цели несут `android:foreground="@drawable/focus_button_background"` (прозрачный default + 2dp focus/hover-обводка, не трогает существующий фон), с landscape-паритетом (Rule 11) там, где land-вариант существует.
5. `focus-highlight-baseline.txt` доведён до 0; `-Gate` exit 0.
6. Сборка ресурсов проходит.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0507 (gate + baseline 63), S0289 (multimodal parity, focus-drawables), S0383 (neuroslop ratchet-gate pattern).

---

## Phase 01 - Teach the gate to recognise handled cases + whitelist non-targets

**Files:**
- `scripts/quality/assert-focus-highlight.ps1`

**Done:**

- Broaden ripple recognition to `?(android:)?attr/selectableItemBackground[Borderless]` (covers `tvVersionInfo` and other android-namespace ripples).
- Recognise `SettingsToggleRow` (sets `selectableItemBackground` in its constructor) and `TranslationOverlayView` (custom interactive canvas overlay) as covered by tag.
- Add a documented `$nonTargetIds` whitelist for confirmed non-targets: control-bar / loading / root containers, player overlays + passthrough scrims, marquee-driver title. Whitelist is exact-id; a new clickable container still fails the gate.

**Status:** `[x]` done

---

## Phase 02 - Add focus indication to genuine targets

**Files (portrait; + `layout-land/` counterpart where it exists):**
- `item_destination.xml`, `item_dropbox_folder.xml`, `item_google_drive_folder.xml`, `item_onedrive_folder.xml`, `item_duplicate_file.xml`, `item_duplicate_group.xml`, `item_device_profile_tile.xml`, `item_scheduled_operation.xml`, `item_stats_card.xml` (RecyclerView item roots - no land variant)
- `view_mini_now_playing.xml` (no land variant)
- `player_draw_overlay_toolbar_content.xml` (colour swatches; land variant exists)
- `dialog_error_detail.xml` (details toggle; land variant exists)
- `fragment_settings_destinations.xml` (5 clickable rows; land variant exists)
- `fragment_settings_general.xml` (open-statistics + saved-authorizations cards; land variant exists)

**Done:**

- Add `android:foreground="@drawable/focus_button_background"` to each flagged genuine target, mirrored into `layout-land/` where that file exists.

**Status:** `[x]` done

---

## Phase 03 - Ratchet baseline + build

**Files:**
- `scripts/quality/focus-highlight-baseline.txt`

**Done:**

- `assert-focus-highlight.ps1 -List` shows zero residual gaps.
- `-UpdateBaseline` ratchets `focus-highlight-baseline.txt` 63 -> 0; `-Gate` exits 0.
- Resource build passes.

**Status:** `[x]` done

---

## Верификация

- `assert-focus-highlight.ps1 -Gate` exit 0 at baseline 0.
- `.\a.ps1 fr` (resources/manifest) passes.

## Связь

- S0507 (focus-highlight coverage gate) - built the gate + baseline 63 that this ticket drives to 0.
- S0289 (multimodal parity), CLAUDE.md Rule 16.

---

## Last Audit

**Date:** 2026-06-18
**Mode:** strategic (Simple)
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 6 - WARN 0 - FAIL 0 - MANUAL 0 - EXEMPT 0

- [PASS §1] `assert-focus-highlight.ps1` ripple regex broadened to `\?(android:)?attr/selectableItemBackground` - `tvVersionInfo` and other android-namespace ripples now recognised.
- [PASS §2] `$customFocusViews = @('SettingsToggleRow', 'TranslationOverlayView')` recognised as covered (verified each renders its own focus: `SettingsToggleRow` sets `selectableItemBackground` in its constructor; `TranslationOverlayView` is a custom canvas overlay).
- [PASS §3] `$nonTargetIds` whitelist (10 ids) for confirmed scrims / passthrough-blockers / root containers / marquee-driver; exact-id, so a new clickable container still fails the gate. `extensionsManagerRoot` id added to portrait + land so the screen-root container is whitelistable.
- [PASS §4] `android:foreground="@drawable/focus_button_background"` added to genuine targets across 16 layout files; landscape parity held for the 4 with a `layout-land/` counterpart (settings_destinations, settings_general, dialog_error_detail, player_draw_overlay_toolbar_content); item_* and view_mini_now_playing have no land variant.
- [PASS §5] `focus-highlight-baseline.txt` ratcheted 63 -> 0; `assert-focus-highlight.ps1 -Gate` exit 0.
- [PASS §6] `.\a.ps1 fr` (`processStandardDebugResources`) BUILD SUCCESSFUL; neuroslop gate unchanged (no new hardcoded hex).
- Mechanical / a11y-coverage change - no new discrete ALL_FEATURES capability (extends S0289 focus-drawable coverage). Zero `Timber.d("S0518:` tags.
