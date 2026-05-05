# Стратегическая спецификация: S0058 — Захват кадра через Passthrough Camera API на Quest 3

**Ticket:** S0058
**Status:** Draft
**Priority:** 55
**Date:** 2026-05-03
**Tier:** 4 — Hard
**Roadmap entry:** Ad-hoc — обсуждение 2026-05-03 по итогам анализа лога Quest 3 (`logs/fastmediasorter_20260503_031502.log`); владелец выбрал вариант C (Passthrough Camera) среди A/B/C/D в чате.
**Tactical spec:** `PLAN/S0058_vr-passthrough-camera-capture/` (будет создан через `/spec-tech` после закрытия research-items §6).

> **Scope:** STRATEGIC + RESEARCH-FIRST. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов. Тактическая фаза — только после ответа на §6.1–§6.5.

---

## 1. Проблема

В сборке VR на Quest 3 в логе сессии 2026-05-03 (`logs/fastmediasorter_20260503_031502.log:2948,3963,4206,4906,8405`) повторяется warning:

```
W/App: CameraCapture: no handlers, command hidden action=android.media.action.IMAGE_CAPTURE
W/App: CameraCapture: no handlers, command hidden action=android.media.action.VIDEO_CAPTURE
```

Текущая реализация (`BrowseCameraCaptureManager`) опирается на штатный Android-контракт `Intent.ACTION_IMAGE_CAPTURE`/`ACTION_VIDEO_CAPTURE` и `PackageManager.queryIntentActivities` — на Horizon OS Quest 3 нет приложения, регистрирующегося на эти actions, поэтому кнопка скрывается, а в логе остаётся warning. **При этом физически захват кадра на Quest 3 возможен** — через системный жест Meta-кнопка + trigger, через MediaProjection API (содержимое экрана) или через Passthrough Camera API (Horizon OS v74+, 2025) — сырой feed с фронтальных passthrough-камер.

**Решение владельца (2026-05-03):** реализовать через Passthrough Camera API — снять кадр того, что пользователь видит снаружи, и сохранить в выбранный пользователем ресурс приложения. Альтернативные пути (system gesture, MediaProjection) — out-of-scope этой спеки.

**Что сейчас делает код в этой ветке:**

- `BrowseCameraCaptureManager` пробует `queryIntentActivities` → пусто → скрывает UI-кнопку → пишет warning.
- В VR-сборке кнопка фактически недоступна; пользователь не имеет способа сохранить кадр Passthrough в нашу галерею.

---

## 2. Цели

1. На VR-сборке (только Quest 3 / Horizon OS v74+) кнопка «снять кадр» доступна и при нажатии создаёт фотографию с frontal passthrough-камер.
2. Полученный кадр сохраняется в локальный ресурс приложения, выбранный пользователем (по умолчанию — Last Used Resource или системный «Pictures/FastMediaSorter» — определяется в §6).
3. После сохранения файл сразу появляется в текущем browse-списке (если активный ресурс совпадает с целевым) либо доступен для просмотра при следующем входе в ресурс.
4. На устройствах без поддержки Passthrough Camera API (старые Horizon OS, не-Quest VR-headsets) кнопка корректно скрывается без warning-логов.
5. Захват не нарушает текущий immersive-сеанс плеера (если он активен) и не требует выхода из VR.

**Non-goals:**

- Не реализовывать Variant B (MediaProjection) — захват содержимого экрана плеера.
- Не реализовывать Variant A (просто скрыть кнопку и подсказать system gesture).
- Не интегрировать system gesture-захват Meta — файл уходит в системную Meta-галерею, не в наш ресурс.
- Не делать видеозахват в первой итерации — только single-frame (фото). Видео — отдельная follow-up спека если потребуется.
- Не реализовывать realtime-preview камер до спуска затвора — статический snapshot одного кадра по нажатию.
- Не затрагивать non-VR флейворы — у них поведение остаётся как сейчас (queryIntentActivities → handler есть → штатный системный intent).

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. На Quest 3 captured-кадр должен быть «то, что пользователь сейчас видит снаружи» — Passthrough, а не содержимое плеера.
2. Кнопка не должна требовать дополнительного диалога подтверждения — нажатие сразу создаёт файл (с быстрым flash-индикатором / haptic как UX-feedback).
3. Сохранённый файл должен иметь читаемое имя с timestamp (например, `passthrough_2026-05-03_18-22-07.jpg`).

### 3.2 Жёсткие ограничения

- **Flavor:** **только VR**. В standard/lite/photos/legacy данный код не собирается — изоляция в VR source set, без новых `BuildConfig`-полей для других flavors.
- **API level:** требуется Horizon OS v74+ (Camera2 + permission `horizonos.permission.HEADSET_CAMERA`); код должен gracefully отключаться на старых версиях — без crash, без warning.
- **Permission:** новое опасное разрешение `horizonos.permission.HEADSET_CAMERA` (Meta-specific). Запрашивается при первом нажатии кнопки. Если отклонено — fallback к скрытию кнопки + однократному tooltip про настройки приложения.
- **Wear OS:** не затрагивается.
- **Производительность:** захват — единичный, не должен влиять на текущую кадровую частоту immersive-сеанса (если активен). Использование camera-сессии — open → snapshot → close немедленно, без удержания camera ресурса.
- **Совместимость данных:** сохранённый файл — JPEG, помещается в выбранный ресурс через существующий FileOperation pipeline; **схема Room не меняется**.
- **Локализация:** строки кнопки, tooltip про permission, error-сообщения — EN/RU/UK через ресурсы.
- **Доступность:** кнопка имеет contentDescription; flash-индикатор после захвата дублируется haptic (controller vibration).
- **Privacy:** Passthrough-кадр содержит реальное окружение пользователя — обработка строго локально, никакой автоматической загрузки/телеметрии. Сохранение — только в выбранный пользователем ресурс приложения.

---

## 4. Контекст текущей архитектуры

`BrowseCameraCaptureManager` (`ui/browse/managers/BrowseCameraCaptureManager.kt`) — менеджер кнопки захвата кадра в Browse-экране. Сейчас делегирует системному intent `ACTION_IMAGE_CAPTURE`. На Quest 3 handler отсутствует, кнопка скрывается, в лог — warning.

VR-сборка имеет отдельный source set (`app_v2/src/vr/`) с собственными классами для OpenXR и passthrough rendering (`VrPlayerActivity`, `OpenXrSessionManager`, `VrStereoRenderer`, etc.). Существующие menifest-permissions VR-сборки на момент написания спеки **не включают** `HEADSET_CAMERA` — это надо будет добавить (только в `app_v2/src/vr/AndroidManifest.xml`).

Pipeline сохранения файла в ресурс приложения уже существует (используется при копировании/перемещении): `FileOperationUseCase` принимает source path → destination resource → выполняет copy через `LocalFileOperationHandler` или `SmbFileOperationHandler` в зависимости от типа целевого ресурса.

Meta Passthrough Camera API (с 2025 года, Horizon OS v74+) экспонирует фронтальные passthrough-камеры через стандартный Android `Camera2` API (`CameraManager.getCameraIdList()` возвращает дополнительные id). Использование требует:
- declare `<uses-permission android:name="horizonos.permission.HEADSET_CAMERA" />`;
- runtime-запрос разрешения как dangerous-permission;
- открытие `CameraDevice` → `CameraCaptureSession` → одиночный `captureRequest` с `JPEG` outputSurface через `ImageReader`.

Документация Meta: `developers.meta.com/horizon/documentation/native/android/passthrough-camera-overview/` (точная версия SDK уточняется в research-items).

---

## 5. Предлагаемый подход

### 5.1 Основные столпы / модули

**А. VR-only Passthrough Camera Capture pathway**
Новая роль «фотограф из passthrough» в VR source set (`app_v2/src/vr/`). Получает запрос на захват, открывает Camera2-сессию по Meta passthrough id, делает один captureRequest с JPEG output, освобождает камеру. Возвращает byte[] кадра.

**Б. Permission flow с graceful fallback**
Перед первым захватом — стандартный Android-runtime-permission flow для `HEADSET_CAMERA`. Если отклонено пользователем «не спрашивать снова» — кнопка скрывается, при касании area вокруг неё — однократный tooltip «открой настройки приложения и разреши доступ к камере». Если устройство не Quest или Horizon OS < v74 — Camera2 не вернёт passthrough-id, кнопка скрывается тихо без warning.

**В. Адаптер выбора целевого ресурса**
Перед сохранением — короткий dialog «куда сохранить» со списком LOCAL-ресурсов пользователя (default — Last Used Resource). Если только один LOCAL-ресурс — без диалога. После выбора — файл записывается напрямую через существующий FileOperationUseCase pipeline.

**Г. UX-feedback на захват**
При спуске затвора — короткий flash-overlay (150-300 мс белый/полупрозрачный поверх Passthrough), плюс haptic pulse на оба контроллера. Через ~500 мс — toast/banner «Снимок сохранён в <resourceName>».

**Д. Замена `BrowseCameraCaptureManager` для VR**
В VR source set — переопределить (или заменить) `BrowseCameraCaptureManager` так, чтобы:
- detect Passthrough Camera availability (есть ли passthrough camera id в `CameraManager.getCameraIdList()`);
- если есть — кнопка показывается всегда; нажатие → §5.1.А;
- если нет — кнопка скрывается **без warning** (новый detection-flow заменяет старый `queryIntentActivities`-based).

### 5.2 Потоки данных и событий

```
Пользователь нажимает кнопку «снять кадр» (Browse, VR-сборка)
  ↓
[А] PassthroughCaptureRole — проверка permission
  ├─ нет permission → системный prompt → если отклонено → tooltip + finish
  └─ есть permission ↓
[А] Открыть Camera2 сессию (passthrough id)
  ├─ ошибка (Horizon OS < v74 / не Quest) → toast «не поддерживается» (один раз) + скрыть кнопку
  └─ ok ↓
[А] captureRequest(JPEG) → ImageReader.acquireLatestImage() → byte[]
  ↓
[Г] flash-overlay + haptic
  ↓
[В] Адаптер выбора ресурса
  ├─ один LOCAL-ресурс → пропустить выбор
  └─ несколько → bottom-sheet выбор
  ↓
Сохранение через существующий FileOperationUseCase pipeline
  → файл `passthrough_<timestamp>.jpg` в выбранном ресурсе
  ↓
[Г] Toast «Снимок сохранён в <resource>»; если active resource совпадает — refresh списка
```

### 5.3 Точки расширяемости

- Роль «фотограф» в §5.1.А отделена от UI — её можно повторно использовать (например, для будущего видеозахвата §2 non-goals или для programmatic-захвата по событию плеера).
- Адаптер выбора ресурса (§5.1.В) — общий паттерн с существующим Copy/Move flow; если уже есть похожий компонент (resource-picker), переиспользовать.
- Флаг availability (`hasPassthroughCamera`) — кэшируется per-process; будущий video-capture или иная подсистема этим же флагом будут пользоваться.

---

## 6. Открытые вопросы / Research items

1. **Точное имя permission и SDK-зависимость**
   - **Вопрос:** актуальное имя permission в Horizon OS v74+ — `horizonos.permission.HEADSET_CAMERA` или другое (Meta могли переименовать)? Нужен ли Meta-specific gradle-dependency, или достаточно AOSP Camera2?
   - **Как выяснить:** [developers.meta.com/horizon/documentation/native/android/passthrough-camera-overview/](https://developers.meta.com/horizon/documentation/native/android/passthrough-camera-overview/) + Meta sample-кода на GitHub.
   - **Статус:** Open. Блокирует Phase 01.

2. **Минимальная Horizon OS версия и detection**
   - **Вопрос:** как программно отличить «Horizon OS поддерживает passthrough camera» от «не поддерживает» (без падения на старых версиях)?
   - **Дефолт:** `CameraManager.getCameraIdList()` возвращает дополнительные id на поддерживаемых устройствах; на старых — стандартный пустой/только rear-эмуляция. Делать probe через try-open + быстрый close.
   - **Статус:** Open.

3. **Поведение во время активного immersive-сеанса VR-плеера**
   - **Вопрос:** можно ли открыть Camera2-сессию параллельно с активным OpenXR rendering loop, не убивая его? Снимок происходит в Browse — но если плеер был свёрнут / в фоне с активным аудио — что происходит?
   - **Как выяснить:** Camera2 + OpenXR совместимость — empirical-test на Quest 3 в фазе implementation.
   - **Статус:** Open.

4. **Куда сохранять по умолчанию**
   - **Вопрос:** «Last Used Resource» подходит как default? Или нужна отдельная настройка «папка для passthrough-снимков»?
   - **Дефолт:** Last Used Resource (если LOCAL); если последний resource — сетевой, использовать `Pictures/FastMediaSorter/` через MediaStore. Настройка появится только если пользователь попросит.
   - **Статус:** Open. Не блокирует, решается по ходу.

5. **Имя файла и метаданные**
   - **Вопрос:** формат имени `passthrough_<timestamp>.jpg` — достаточно? Включать ли EXIF orientation / координаты headset / другие метаданные?
   - **Дефолт:** имя `passthrough_YYYY-MM-DD_HH-mm-ss.jpg`; EXIF — стандартный (orientation, datetime, software=FastMediaSorter); никаких location-метаданных.
   - **Статус:** Open. Не блокирует.

6. **Что показывать при отклонении permission «не спрашивать снова»**
   - **Вопрос:** tooltip с deeplink в системные настройки приложения — или просто скрытая кнопка без объяснений?
   - **Дефолт:** при первом отказе — tooltip; при последующих нажатиях area вокруг скрытой кнопки — toast «нужен доступ к камере, открой настройки» с кнопкой-deeplink.
   - **Статус:** Open. Не блокирует.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Meta меняет API/permission в следующей версии Horizon OS | Средняя | Регрессия после OTA-апдейта Quest | Изолировать всё в одной роли §5.1.А; runtime-detection через try-open вместо hardcoded check на версию SDK |
| Camera2-сессия конфликтует с OpenXR в immersive | Средняя | Чёрный кадр в плеере / краш XR-сессии при захвате | Захват только из Browse-экрана (вне immersive); если у плеера активна XR — отложить или отклонить захват с сообщением |
| Permission «HEADSET_CAMERA» не классифицируется как dangerous, требует system-signature | Низкая | Невозможно реализовать как стандартное user-grant | Phase 0 research §6.1; если так — закрыть спеку, открыть follow-up на MediaProjection (Variant B) |
| Захват blocking на main thread при медленной камере → ANR | Средняя | Зависание Browse при нажатии | Camera2 callback-based API уже async; основной поток не блокируется |
| Privacy/legal: пользователь снимает кадр случайно, попадают окружающие люди | Низкая | Возможные жалобы | Явный flash + haptic как «затвор сработал»; toast с подтверждением; явная иконка камеры в UI |
| Сохранение JPEG в SMB-ресурс через текущий pipeline проходит не сразу (network) | Средняя | Toast «сохранено» приходит до фактической записи на сервер | Дождаться completion от FileOperationUseCase прежде чем показать toast; для сетевых — отдельный «загружается..» state |

---

## 8. Влияние на пользователя (docs/FEATURES)

Раздел **VR Features** в `docs/FEATURES.md` + RU + UK получает новую запись:

> **Захват кадра Passthrough (Quest 3, Horizon OS v74+).** Кнопка в Browse сохраняет одиночный JPEG с фронтальных passthrough-камер в выбранный локальный ресурс приложения. Требует разрешения камеры; на устройствах без Passthrough Camera API кнопка скрывается. Снимок содержит реальное окружение пользователя — обработка только локально.

Точная формулировка и трёхъязычные переводы — в фазе документации тактической спеки.

---

## 9. Архитектурные решения (ADR)

### ADR-1: Passthrough Camera, не MediaProjection и не system gesture

- **Решение:** реализуем именно Variant C (Passthrough Camera) — снимок «того, что пользователь видит снаружи».
- **Альтернативы:** A (скрыть и подсказать system-gesture), B (MediaProjection — захват содержимого экрана плеера), D (комбо B+C).
- **Почему так:** прямое решение владельца (2026-05-03). Соответствует пользовательской модели «снять кадр» в её естественном смысле — окружающий мир, а не интерфейс приложения. MediaProjection — другая фича (могла бы появиться отдельно если потребуется).

### ADR-2: VR source set only, без условной компиляции в общем коде

- **Решение:** весь код passthrough capture живёт в `app_v2/src/vr/`. Общий source set не знает о его существовании.
- **Альтернативы:** условная инициализация через `BuildConfig.HAS_PASSTHROUGH_CAMERA` в общем коде.
- **Почему так:** строгое правило §3.2 «изоляция в VR source set, без новых BuildConfig-полей». Полное удаление кода для не-VR сборок — без рантайм-проверок.

### ADR-3: Single-frame только в первой итерации

- **Решение:** только фото; видео и burst-mode — out-of-scope.
- **Альтернативы:** сразу включить video-capture (§2 non-goals).
- **Почему так:** сильно меньшая сложность (нет MediaCodec/MediaMuxer-pipeline), быстрее до production. Видео — отдельная спека если запрос появится.

---

## 10. Связи с другими спеками

- **S0055** (diagnostic-noise-cleanup, Draft) — изначально пункт «C» там покрывал warning `CameraCapture: no handlers`; перенесён сюда. После реализации S0058 warning исчезает естественным образом (новый detection в §5.1.Д не вызывает warning при отсутствии handler).
- **S0009** (vr-immersive-hud-gl, Partial) — отдельная VR-подсистема (HUD); технически не пересекается, но обе живут в `app_v2/src/vr/`.
- **S0028** (vr-multi-window-playback, Tactical) — отдельная VR-фича; не пересекается, но возможен общий resource-picker UI.

---

## 11. Критерии готовности (strategic-level)

1. На Quest 3 (Horizon OS v74+) пользователь нажимает кнопку «снять кадр» в Browse → получает JPEG с frontal passthrough-камер в выбранном локальном ресурсе приложения.
2. На Quest 3 без granted `HEADSET_CAMERA` permission — нажатие триггерит системный permission prompt; при отказе «не спрашивать» — кнопка скрывается + tooltip-deeplink в системные настройки.
3. На устройствах без Passthrough Camera API (старый Horizon OS / не-Quest) — кнопка скрыта, в логе **нет** warning `CameraCapture: no handlers`.
4. Захват не вызывает заметного кадрового provala в активном immersive-сеансе плеера (если он есть в фоне).
5. Captured-файл валиден как JPEG, открывается в самом приложении и в системной галерее.
6. Раздел **VR Features** в `docs/FEATURES.md` + `_RU` + `_UK` содержит формулировку из §8.
7. Privacy: кадр не покидает устройство автоматически; никакой телеметрии о факте захвата.

---

## 12. Тактическая спецификация

После закрытия research-items §6.1, §6.2, §6.3 (минимально — точное permission + минимальная Horizon OS + совместимость с OpenXR) — `/spec-tech vr-passthrough-camera-capture` создаст `PLAN/S0058_vr-passthrough-camera-capture/` с фазами:

- **Phase 00 — Research closure:** owner или dev-инженер закрывают §6.1/§6.2/§6.3 (Meta docs + sample + on-device probe).
- **Phase 01 — Permission + manifest setup:** добавление permission в VR manifest, runtime-flow.
- **Phase 02 — Capture role implementation:** Camera2-based JPEG snapshot.
- **Phase 03 — UI integration:** новый detection в `BrowseCameraCaptureManager` для VR + flash-overlay/haptic.
- **Phase 04 — Save pipeline:** интеграция с FileOperationUseCase + resource picker.
- **Phase 05 — Localization + docs:** строки EN/RU/UK + FEATURES.

---

## Last Audit

_Не проводился._
