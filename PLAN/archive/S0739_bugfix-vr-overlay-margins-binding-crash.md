# Спецификация (fix): S0739 - Краш PlayerVrLaunchManager: binding после onDestroy

**Ticket:** S0739
**Status:** Archived
**Priority:** 80
**Date:** 2026-06-26
**Tier:** 1 - Bugfix

> **Scope:** P0-краш на выходе из плеера. Отложенный Runnable обращается к binding после уничтожения Activity.

---

## 0. Источник

Краш-репорт владельца (2026-06-26 23:27:31), обнаружен во время device-тестирования плеера (S0670):

- `java.lang.IllegalStateException: Binding is only valid between onCreateView and onDestroyView`
- `BaseActivity.getBinding(BaseActivity.kt:67)` <- `PlayerActivity.getActivityBinding(PlayerActivity.kt:125)` <- `PlayerVrLaunchManager.updateOverlayMargins$lambda$12(PlayerVrLaunchManager.kt:310)` <- `Handler.handleCallback`
- standard debug v2.60.6261.106, Android 17 emulator.

## 1. Причина

`PlayerVrLaunchManager.updateOverlayMargins()` планирует работу через `activity.activityBinding.root.post { .. }`. Внутри лямбды повторно читается `activity.activityBinding` (геттер `BaseActivity.binding`, бросающий ISE при `_binding == null`). Если Activity уничтожается до выполнения отложенного Runnable (быстрый вход/выход в плеер - частый случай при device-sweep), геттер бросает ISE на главном потоке -> краш.

Соседний `safeViews` уже захватывает binding один раз при создании менеджера и потому безопасен; уязвим только этот отложенный путь.

## 2. Исправление

`PlayerVrLaunchManager.updateOverlayMargins()`: захватить `val binding = activity.activityBinding` ОДИН раз до `post { }` и использовать захваченную ссылку внутри лямбды (`binding.root`, `binding.btnTouchZonesHelp`, ..) вместо повторного вызова бросающего геттера. Чтение уже захваченных view на отсоединённой иерархии безвредно (тот же приём, что и `safeViews`).

## 3. Критерии приёмки

- [x] В отложенной лямбде нет вызова `activity.activityBinding` (геттера, способного бросить ISE); используется захваченная ссылка.
- [x] `.\a.ps1 fk` зелёный.
- [ ] На устройстве: повторный быстрый вход/выход в плеер (+ поворот) не вызывает краш "Binding is only valid..".

## 4. Связанные тикеты

- S0670 (во время его device-теста краш и всплыл).
