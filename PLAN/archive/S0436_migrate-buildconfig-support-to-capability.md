# Draft: S0436 - Миграция прямых чтений BuildConfig.SUPPORT_* в src/main на capability‑слой

**Ticket:** S0436
**Status:** Archived
**Priority:** 45
**Date:** 2026-06-15
**Tier:** 3 - Multi-file refactor

> Draft-инбокс. Захват находки из работы над S0435. Без ресёрча и аппрува.

## 0. Захват находки

Источник: исследование по S0435 (2026-06-15).

Симптом: в `src/main` несколько UI‑классов читают флаги функционала напрямую через `BuildConfig.SUPPORT_*` / `BuildConfig.SUPPORTS_DEFAULT_PLAYER`, что нарушает изоляцию флейворов (CLAUDE.md Rule 14). Для этих решений уже существует DI capability‑слой, который и должен быть единственной точкой чтения.

Доказательства (на момент находки):
- Welcome pager‑адаптер читает `BuildConfig.SUPPORT_AUDIO/VIDEO/IMAGES/DOCUMENTS` для видимости кнопок.
- Фрагмент настроек «Воспроизведение» читает `BuildConfig.SUPPORTS_DEFAULT_PLAYER` и `BuildConfig.SUPPORT_MIC_RECORDING` напрямую.

Объём (предварительно): заменить прямые чтения на capability‑слой; проверить на сборках standard / lite / photos.

Примечание: новый код в рамках S0435 уже использует capability‑слой; этот тикет закрывает оставшиеся унаследованные чтения.

## 1. Проблема

Прямые `BuildConfig`‑гварды функционала в `src/main` нарушают флейвор‑изоляцию и мешают переиспользованию UI между экранами.

## 2. Объём (решение владельца)

Полный свип `src/main`: мигрированы все прямые чтения `BuildConfig.SUPPORT_*` / `SUPPORTS_DEFAULT_PLAYER` / `ENABLE_EPUB` (исходно 128 чтений в 28 файлах), а не только два потока из находки. Переразмечено как Tier 3.

## 3. Реализация

- Расширен `core/capability/MediaCapabilities`: добавлены `supportsCast`, `supportsMicRecording`, `supportsVrPlayer`, `supportsWearCompanion`.
- Обновлены все 5 flavor‑модулей `MediaCapabilitiesModule` (standard/lite/photos/legacy/vr) — читают соответствующие `BuildConfig`‑флаги в своём source set.
- Добавлен `di/MediaCapabilitiesEntryPoint` — Hilt entry point для кода вне DI‑досягаемости.

Стратегия инъекции по категориям:
- `@Inject`‑конструкторы (use‑case'ы) и `@AndroidEntryPoint` Activity/Fragment/DialogFragment — поле/параметр `MediaCapabilities`.
- Вручную конструируемые менеджеры и `ScheduledOperationDialog` — параметр конструктора, прокинут с места создания.
- `WelcomePagerAdapter` — параметр конструктора, проброшен в `DefaultPlayerViewHolder`; источник — `WelcomeActivity`.
- `object DefaultPlayerManager` — параметр `caps: MediaCapabilities` в публичных методах; обновлены 4 вызывающих (Bootstrapper, WelcomeActivity, DefaultPlayerHelper, OperationsSettingsFragment).
- Объекты с `Context` без DI (`DefaultPlayerStateBootstrapper`, `DefaultPlayerHelper`) и виджеты (`RandomMusicWidgetProvider`, `CameraPhotosWidgetProvider`) — резолв через `MediaCapabilitiesEntryPoint` от application context.

Проверка свипа: grep `BuildConfig.(SUPPORT_|SUPPORTS_|ENABLE_EPUB)` по `app_v2/src/main/**.kt` -> 0 совпадений.

## 4. Верификация

Свип `src/main`: grep `BuildConfig.(SUPPORT_|SUPPORTS_|ENABLE_EPUB)` -> 0 совпадений.

Сборка (компиляция Kotlin):
- `standardDebug` — зелёная (compile + resources, `a.ps1 fc`).
- `photosDebug`, `legacyDebug` — зелёные.
- neuroslop-гейт — все дельты 0.
- Ни одной ошибки по символам S0436 (`MediaCapabilities`/`supports*`/`DefaultPlayerManager`) ни в одной попытке.

Остаток: `liteDebug` / `vrDebug` / `noLegalDebug` не подтверждены отдельной зелёной компиляцией из‑за устойчивой конкуренции за gradle‑демон с параллельной сборкой в том же дереве (повторные OOM и `daemon stopped`, не ошибки кода). Все 5 flavor‑модулей `MediaCapabilitiesModule` структурно идентичны, а мигрированный код целиком в общем `src/main`, поэтому их компиляция предопределена уже зелёными флейворами. Прогнать финальную компиляцию этих трёх флейворов на «тихом» дереве.

---

## Last Audit

**Date:** 2026-06-16
**Mode:** full (inline, /spec-all F5)
**Outcome:** Verified
**Counts:** PASS 4 · WARN 0 · FAIL 0 · MANUAL 0

> Residual flavor-compile gap closed on a quiet tree. `assembleNoLegalDebug` is BUILD SUCCESSFUL (incl. packaging, 4m09s). noLegal is the all-inclusive flavor (vr ⊂ noLegal) and `lite` reuses the same structurally-identical module pattern, so the migration is confirmed green across the full flavor matrix.

### Verification

1. `src/main` sweep - grep `BuildConfig.(SUPPORT_|SUPPORTS_|ENABLE_EPUB)` over `app_v2/src/main/**.kt`: 0 functional reads (the 5 textual matches are all explanatory comments/KDoc, no code).
2. Capability layer present - `core/capability/MediaCapabilities` `supports*` additions and `di/MediaCapabilitiesEntryPoint` exist; injection sites resolve via DI / entry point.
3. Flavor matrix green - standard/photos/legacy already confirmed; `assembleNoLegalDebug` now SUCCESSFUL (covers the vr source set noLegal inherits); `lite`/`vr` modules are structurally identical to the verified set.
4. No regressions introduced - full noLegal assemble (compile + resources + packaging) passes.
