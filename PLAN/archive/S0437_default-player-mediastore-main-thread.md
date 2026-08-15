# S0437 - Синхронная выборка MediaStore в UI‑потоке при подготовке интента регистрации

**Ticket:** S0437
**Status:** Archived
**Priority:** 45
**Date:** 2026-06-15
**Tier:** 2 - Easy (ad-hoc)

> Draft-инбокс. Захват находки из работы над S0435. Без ресёрча и аппрува.

## 0. Захват находки

Источник: исследование по S0435 (2026-06-15).

Симптом: при подготовке интента регистрации приложения по умолчанию поиск примерного файла выполняет синхронный cursor‑запрос к MediaStore. Вызов идёт из обработчика нажатия кнопки, то есть на UI‑потоке - это дисковый I/O на главном потоке (риск StrictMode и фриза), и проявится сильнее при добавлении кнопок регистрации в настройки (S0435).

Доказательства (на момент находки):
- Хелпер регистрации выполняет cursor‑запрос MediaStore синхронно в методе поиска примерного файла.
- Вызывается из click‑listener экрана приветствия (и будет вызываться из настроек).

Объём (предварительно): вынести выборку примерного файла вне UI‑потока (корутина/фон) перед запуском интента; сохранить fallback‑поведение.

## 1. Проблема

Синхронный MediaStore‑запрос на главном потоке при назначении программы по умолчанию может вызывать фриз UI и нарушения StrictMode в debug‑сборках.

## 2. Реализация

Файл: `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/DefaultPlayerHelper.kt`.

- `resolveOpenWithIntent()` (MediaStore cursor‑запрос в `findSampleFile()` плюс `PackageManager`‑резолв в `foreignDefaultExists()`) вынесен на `Dispatchers.IO`.
- Все три точки входа (`openChooserOrFallbackFromActivity`, `openChooserOrFallbackForResult`, приватный `openChooserOrFallback` для фрагментов) теперь запускают резолв в `lifecycleScope.launch { withContext(Dispatchers.IO) { .. } }`; продолжение (`startActivity` / `launcher.launch` / `Toast` / fallback в настройки) выполняется на главном потоке.
- Активити‑оверлоады берут scope через `activity as LifecycleOwner` - оба вызывающих (`WelcomeActivity` через `BaseActivity`, `FragmentActivity` в `WelcomeEnableAllManager`) являются `LifecycleOwner`.
- После suspend‑точки добавлены guard'ы жизненного цикла (`fragment.isAdded`, `activity.isFinishing/isDestroyed`).
- Fallback‑поведение (системный лист «Открыть с помощью», переход на экран приложений по умолчанию) сохранено.
