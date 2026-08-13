# Strategic Specification: S0564 - Main quick-capture loses result on process death

**Ticket:** S0564
**Status:** Archived
**Priority:** 40
**Date:** 2026-06-20
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - parked by `/spec-draft` from `/spec-all S0563` (2026-06-20)

> **Scope:** компактный bugfix-спек (Simple path). Зеркалит существующий `BrowseCameraCaptureManager.saveState/restoreState` паттерн в `MainCameraCaptureManager`.

<!-- auto-approved by /spec-all - 2026-06-20 -->

---

## 0. Raw capture (symptom + evidence)

Parked during S0563 research (out of scope for that ticket).

**Symptom:** when the OS kills `MainActivity` while the in-app camera host is in the foreground (low
memory), the main-menu quick-capture path loses its pending state and abandons the captured file.

**Evidence:**

- `MainCameraCaptureManager` keeps the pending capture target in plain in-memory fields
  (`pendingDir` / `pendingBaseName`). There is no `onSaveInstanceState` / restore path.
- On return after process death these fields are null, so `handleResult` early-returns with
  `camera_capture_error_session_expired` and the captured file is left on disk in the app-private
  scratch dir, never moved to the public folder.
- `BrowseCameraCaptureManager` already solves the same problem via `saveState` / `restoreState`; the
  main-menu manager has no equivalent.

**Discovered by:** research agent during S0563 (`PLAN/S0563_camera-unified-entry-mode-switch.md`).

---

## 1. Проблема

Быстрый захват из главного overflow-меню не переживает смерть процесса. Хост камеры может пережить `MainActivity` под нехваткой памяти; при возврате pending-цель (`pendingDir`/`pendingBaseName`) утеряна, и захваченный файл молча отбрасывается с ошибкой session_expired вместо переноса в публичную папку.

---

## 2. Цели

1. Сохранять pending quick-capture цель (`pendingDir`/`pendingBaseName`) через смерть процесса, зеркаля существующий паттерн `BrowseCameraCaptureManager.saveState`/`restoreState`.
2. При восстановлении завершать сохранение файла вместо показа session_expired.

**Non-goals:**

- Очистка orphaned scratch-файлов от более ранних убитых сессий (отдельная задача; вне объёма).
- Изменение поведения захвата, путей сохранения (фото -> DCIM/Camera, видео -> Movies) или UI.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

- Минимальное точечное вмешательство - переиспользовать проверенный паттерн Browse, без новой инфраструктуры.

### 3.2 Жёсткие ограничения

- **Flavor:** все стандартные (код в `src/main`).
- **API level:** без API-специфики.
- **Wear OS:** не затрагивается.
- **Производительность:** не критично (только сериализация двух строк в Bundle).
- **Совместимость данных:** только runtime Bundle (`onSaveInstanceState`), персистентного хранилища нет.
- **Локализация:** без новых строк (переиспользуется существующий `camera_capture_error_session_expired`).
- **Доступность:** без изменений.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0563 (camera-unified-entry-mode-switch - владелец `MainCameraCaptureManager`)
- **UI:** изменений размещения/видимости/дизайна нет - чисто восстановление состояния.

---

## 4. Контекст текущей архитектуры

`MainCameraCaptureManager` (слой `ui/main/helpers`) запускает unified-камеру и держит `pendingDir`/`pendingBaseName` в памяти до возврата результата через `handleResult`. `MainActivity` владеет launcher-ом (`quickCaptureCameraLauncher`), чей callback делегирует в `handleResult`. Менеджер создаётся в `setupViews()` (выполняется внутри `super.onCreate()`), поэтому к телу `onCreate` после `super.onCreate(savedInstanceState)` он уже инициализирован, а `savedInstanceState` доступен - это окно до dispatch отложенного результата (onStart), где `BrowseActivity` делает свой restore.

---

## 5. Предлагаемый подход

Добавить `saveState(Bundle)`/`restoreState(Bundle)` в `MainCameraCaptureManager` (зеркало Browse, но проще - без resource-lookup, т.к. цель сохранения фиксированная публичная папка). Вызвать `saveState` из `MainActivity.onSaveInstanceState` и `restoreState` из `onCreate` сразу после `super.onCreate()` (до dispatch результата). После restore `handleResult` находит `pendingDir`/`pendingBaseName` и завершает перенос файла.

### 5.1 Основные столпы / модули

- `ui/main/helpers/MainCameraCaptureManager` - save/restore pending-цели.
- `ui/main/MainActivity` - проводка `onSaveInstanceState` -> saveState, `onCreate` -> restoreState.

### 5.2 Потоки данных и событий

- onSaveInstanceState -> `saveState(Bundle)` (persist dir+base).
- (process death) -> onCreate -> `restoreState(Bundle)` -> отложенный результat -> `handleResult` завершает save.

### 5.3 Точки расширяемости

- Не требуется - точечный фикс по существующему паттерну.

---

## 6. Открытые вопросы / Research items

Открытых вопросов нет (дизайн-форк §3 раннего скелета разрешён прецедентом Browse: `onSaveInstanceState`/Bundle, не отдельное хранилище).

---

## 7. Риски

- Scratch-dir (app-private `getExternalFilesDir`) переживает смерть процесса, но при крайней нехватке места ОС может его очистить - тогда restore проверяет существование dir и тихо выходит (handleResult затем покажет session_expired как и раньше). Низкий риск, корректная деградация.

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES (исправление потери результата, не новая возможность).

---

## 9. Архитектурные решения (ADR)

ADR нет - решение зеркалит устоявшийся паттерн `BrowseCameraCaptureManager.saveState`/`restoreState` (S0371).

---

## 10. Связи с другими спеками

- S0563 - владелец `MainCameraCaptureManager` (unified camera entry). Правка ортогональна mode-switch.
- S0371 - источник паттерна save/restore в `BrowseCameraCaptureManager`.

---

## 11. Критерии готовности (strategic-level)

1. Захват из главного меню, убийство процесса при открытом хосте, возврат -> файл сохраняется в публичную папку (не session_expired).
2. Отсутствие регрессий обычного (без смерти процесса) пути захвата.

---

## Phases

### Phase 01 - save/restore в MainCameraCaptureManager [DONE]

- [x] Добавить `saveState(Bundle)`/`restoreState(Bundle)` + ключи Bundle, зеркаля Browse (без resource-lookup).
  - Verification: `pendingDir`/`pendingBaseName` сериализуются/десериализуются; restore проверяет существование dir.

### Phase 02 - проводка в MainActivity [DONE]

- [x] `onSaveInstanceState` -> `cameraCaptureManager.saveState(outState)` (guard `isInitialized`).
- [x] `onCreate` после `super.onCreate()` -> `savedInstanceState?.let { cameraCaptureManager.restoreState(it) }`.
  - Verification: компиляция `a.ps1 fk` PASS.

### Phase 03 - проверка на устройстве [BlockNeedUserTest]

- [ ] Реальное устройство: захват из меню -> убить процесс при открытом хосте -> вернуться -> файл в публичной папке.
  - Verification: тег `Timber.d("S0564: ..")` в `restoreState` подтверждает восстановление; файл перенесён, нет session_expired. AVD недостаточен (камера эмулятора ломает CameraX-захват, нет валидного результата для переноса).
