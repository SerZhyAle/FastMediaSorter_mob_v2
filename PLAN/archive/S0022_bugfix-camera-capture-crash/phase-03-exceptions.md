# Phase 03 — Обвязка исключений + Snackbar

**Файл:** `BrowseCameraCaptureManager.kt`

## Суть изменения

Заменить `Toast` на `Snackbar` для всех путей ошибок. Каждый класс исключения → отдельная локализованная строка. Убрать хардкоженные английские строки.

## Маппинг исключений → строки

| Исключение | Строка |
|-----------|--------|
| `ActivityNotFoundException` | `camera_capture_error_no_camera_app` |
| `SecurityException` | `camera_capture_error_permission_denied` |
| `IOException` | `camera_capture_error_io` |
| `handlers == 0` в launch() | `camera_capture_error_no_camera_app` |
| `createTemp == null` | `camera_capture_error_temp_file` |
| save failed | `camera_capture_error_save_generic` |
| process death (null context) | `camera_capture_error_session_expired` |

## Snackbar anchor

Использовать `activity.window.decorView.rootView` как View для Snackbar. Это стандартный паттерн в Manager-классах без binding.

## Чеклист

- [x] Все Toast заменены на Snackbar
- [x] Нет хардкоженных английских строк ошибок
- [x] SecurityException обрабатывается отдельно
- [x] IOException обрабатывается отдельно
