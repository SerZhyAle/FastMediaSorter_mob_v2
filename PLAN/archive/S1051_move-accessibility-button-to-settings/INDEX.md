# Tactical Plan: S1051 - Relocate "Open accessibility settings" control into OS-interaction settings

**Ticket:** S1051
**Status:** Tactical
**Strategic spec:** `PLAN/S1051_move-accessibility-button-to-settings.md` (§5 approach + §3.3 owner-resolved decisions authoritative)
**Research:** `research/01__current-location-and-gate.md` (concrete anchors)

## Goal (RU)

Пара «подпись + кнопка "Открыть спец-возможности"» переезжает из диалога краевых жестов в группу настроек «Взаимодействие с операционной системой» (`containerSystemApps`). Поведение, видимость-гейт (`isFallbackCaptureAvailable()`, noLegal), fallback-диалог и обе ориентации сохраняются. Новых строк нет.

## Decisions (from strategic §3.3, do not re-litigate)

- Видимость - через существующий `ScreenGestureOverlayController.isFallbackCaptureAvailable()`, без `BuildConfig.IS_*`.
- Место - внутри сворачиваемой группы «Взаимодействие с ОС»; подпись над кнопкой.
- Поведение - тап -> `permissionSettingsIntent` через существующий `overlayPermissionLauncher`; при `ActivityNotFoundException` -> обучающий fallback-диалог (S0449).
- Строки переиспользуются: `setting_screenshot_accessibility_shortcut_hint`, `setting_screenshot_accessibility_shortcut_button`.

## Key insight (why this is Tier-2)

`OperationsSettingsFragment` (владелец `fragment_settings_destinations.xml` incl. `containerSystemApps`) УЖЕ инжектит `screenGestureControllers` и регистрирует `overlayPermissionLauncher` - тот же launcher, что оригинальный клик в диалоге использовал. Перенос behavior-preserving; новых DI/launcher не требуется.

## Phase overview

| Phase | Title | Status |
|-------|-------|--------|
| 01 | Relocate hint+button; strip dialog wiring; fix search gate | Done |

## Blockers

- None to implement (all decisions resolved from spec §3.3).
- **Device-verification (F3 terminal):** §11 criteria are visual+interaction (button present in settings, absent in dialog, both orientations, noLegal gate, fallback dialog) -> ticket lands `BlockNeedUserTest`; validated on a **noLegal** build.

## Completion gate

- `standard debug` compiles green (src/main change; capability interface compiles on every flavor); `fkn` (noLegal Kotlin) green since visibility is noLegal-gated.
- One `Timber.d("S1051: …")` probe at the relocated click entry (present only while BlockNeedUserTest).
- `docs/ALL_FEATURES.jsonl` - no ADD (control relocation, not a new capability; strategic §8 "Без изменений").
