# Спецификация (compact bugfix): S1097 - Недавние программы не появляются в панели после кнопки «Пуск»

**Ticket:** S1097
**Status:** Archived
**Priority:** 90
**Date:** 2026-07-18
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-18

**Захвачено во время:**  тестирования (контекст «по тестированию»); связанный тикет - S0404

**Текст:**

по тестированию
S0404
лаунчер
чнизу не появляются программы недавного запуска (они должны в панели после кнопки "Пуск" копиться

---

## 1. Проблема / симптом

<Что наблюдается, где (flavor/устройство/экран), эвиденс - лог-строки, stack trace, repro. Без имён классов на этапе захвата.>

Симптом (из захвата): в нижней панели лаунчера после кнопки «Пуск» не накапливаются/не показываются недавно запущенные программы. Ожидание: недавние приложения должны появляться в этой панели по мере запуска.

---

## 2. Корневая причина

Технический pipeline «недавних» исправен: запуск из Start-меню («Все приложения») идёт через `viewModel.run(App())` → `ExecuteLauncherCommandUseCase` пишет `journal.record(command)`; настройка `launcherTaskbarShowRecents` по умолчанию `true`; `recentIcons` подключён к таскбару.

Расхождение - продуктовое, а не дефект кода: `LauncherJournalRepository.recentApps` намеренно оставлял только записи вида `app:` (сторонние приложения), отсеивая внутренние функции, ресурсы, каналы и системные экраны. Стол по умолчанию засеян преимущественно внутренними элементами, поэтому при обычном тестировании журнал наполняется, но строка «недавних» остаётся пустой - в неё нечему попадать.

Owner-решение 2026-07-18: «недавние» = ВСЁ запущенное из лаунчера (модель Windows-таскбара), а не только сторонние приложения.

---

## 3. Исправление

Расширить «недавние» до всех видов команд:

- `LauncherJournalRepository.recentApps(limit): Flow<List<String>>` → `recentCommands(limit): Flow<List<LauncherCellCommand>>`: декодировать все записи журнала (не только `app:`), дедуп по `encode()`, newest-first, `take(limit)`.
- Новый `QueryRecentLauncherCommandsUseCase` (замена `QueryRecentLauncherAppsUseCase`): каждую команду резолвить в визуал через существующий `ResolveLauncherCommandLabelUseCase` (тот же путь, что у пинов - один источник иконок/подписей). Недоступные сейчас цели (удалённое приложение/ресурс/канал) отсеиваются: «недавнее» - это быстрый повторный запуск, а не «надгробие» как ячейка стола.
- `LauncherHomeViewModel.recentIcons`: `id` иконки = `command.encode()` (как у пина), а не голое имя пакета.
- `LauncherTaskbarManager` recents-адаптер: тап декодирует команду и повторяет её (`LauncherCellCommand.decode(icon.id)`), а не оборачивает id в `App()`.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0404 (родительский эпик; поведение итерации-1)
- **Owner decision 2026-07-18:** недавние = все запущенные команды (приложения + внутренние функции + ресурсы + каналы + системные экраны).

---

## 4. Проверка

- Компиляция: `.\a.ps1 fk` (standard) - BUILD SUCCESSFUL.
- On-device (BlockNeedUserTest): запустить со стола несколько разных элементов (внутреннюю функцию, ресурс, при наличии - стороннее приложение из «Все приложения»); вернуться на стол → каждый появляется в строке «недавних» после кнопки Пуск, newest-first, без дублей; тап по недавнему повторяет запуск именно этой команды; удалённая/недоступная цель в строке не показывается.

---

## Last Audit

Device run 2026-07-24 (`/spec-test-device`, emulator-5554 Pixel 9 / Android 15, standard-debug 2.60.7220.314). Evidence: `temp/S1097/mobile_test_scenario_20260724_0031.md`, probe `S1097: recents strip -> N item(s)` monotonic 1->2->3.

### Manual / on-device

- [x] Recents accumulate items of any command kind launched from the desktop (internal feature / resource, not apps-only) - verified on-device 2026-07-24
- [x] Newest-first ordering in the recents strip - verified on-device 2026-07-24
- [x] No duplicate when the same command is relaunched (count held at 3) - verified on-device 2026-07-24
- [x] Tapping a recent entry reruns exactly that command (recents gear reopened Android Settings) - verified on-device 2026-07-24
- [ ] Removed/unavailable target dropped from recents - not device-proven; no third-party app on the test image to uninstall. Code-covered by `QueryRecentLauncherCommandsUseCase.resolve` (drops unlaunchable App and null-visual commands).

## Revision History

- **2026-07-24** - by `/spec-test-device` (`claude-opus-4-8[1m]`, device: emulator-5554 Android 15)
  - Scenario: temp/S1097/mobile_test_scenario_20260724_0031.md · PASS/FAIL/SKIPPED 4/0/1 · Errors in log: 0
