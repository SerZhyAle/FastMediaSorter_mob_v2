# Specification: VR Panel — Default Large Landscape Window + Size Persistence

**Status:** Draft
**Date:** 2026-04-21
**Tier:** 2 — Low-Medium (contained, UI-level, no media pipeline changes)
**Goal:** При запуске приложения на Quest 3 (или любом VR-хедсете) окно по умолчанию открывается
большим, в горизонтальной ориентации. Это поведение применяется **ко всем флейворам** (standard,
lite, photos, legacy, vr). Кроме того, приложение запоминает последний размер окна, установленный
пользователем вручную, и восстанавливает его при следующем запуске.

---

## 1. Problem Statement

На Meta Quest 3 в режиме панели (passthrough / panel mode) HorizonOS запускает приложение
в минимальном окне по умолчанию (~небольшой квадрат). Пользователь вынужден вручную растягивать
окно при каждом запуске. Автоматического запоминания и восстановления размера нет ни в одном
флейворе. Это создаёт плохой UX при каждодневном использовании.

---

## 2. Goals

1. При первом запуске на VR-хедсете окно открывается с заранее определённым большим размером
   (пресет `DEFAULT_VR_PANEL`).
2. При последующих запусках восстанавливается **последний размер**, сохранённый пользователем.
3. Обнаружение хедсета происходит без компиляции — через runtime-проверку фич устройства.
4. Логика применяется ко всем флейворам (`standard`, `lite`, `photos`, `legacy`, `vr`).
5. На телефонах/планшетах никаких изменений нет (проверка предотвращает случайное применение).

Non-goals: изменение логики immersive / OpenXR рендеринга; кнопка для ручного сброса размера
в UI настроек (можно добавить позже); поддержка нескольких мониторов / virtual displays.

---

## 3. Flavor & API Level Scope

### 3.1 Flavor Impact

| Flavor | Affected? | Примечание |
|--------|:---------:|-----------|
| `standard` | ✅ | Основной сценарий |
| `lite`     | ✅ | Работает на Quest так же, как standard |
| `photos`   | ✅ | Фото-библиотека в панели Quest |
| `legacy`   | ✅ | Без изменений в поведении на телефонах |
| `vr`       | ✅ | `MainActivity` уже работает как panel host |

Код размещается в **`main` source set** — без `BuildConfig`-гарда. Гардом служит
runtime-детектор `XrDeviceDetector.isXrHeadset(context)`.

### 3.2 Android API Level Forks

| API | Использование | Min API |
|-----|---------------|---------|
| `WindowManager.getCurrentWindowMetrics()` | Чтение текущего размера окна | 30 (Q) |
| `PackageManager.hasSystemFeature(android.hardware.vr.headtracking)` | Детект хедсета | 26 (O) |
| `window.attributes.width / height` | Запрос нужного размера окна | 26 (O) |
| `getWindowManager().defaultDisplay.getRealSize()` | Fallback для API < 30 | 26 (O) |

Проект `minSdk = 26` → все API доступны без условий.

---

## 4. Механизм обнаружения VR-хедсета

### 4.1 Уровни детекции (приоритет по убыванию надёжности)

```
1. PackageManager.hasSystemFeature("android.hardware.vr.headtracking")
   → наиболее надёжный; декларирован в /etc/permissions на всех HorizonOS устройствах.

2. Build.MANUFACTURER == "Oculus" || "Meta" (case-insensitive)
   → fallback если feature не задекларирована (кастомные прошивки, sideload).

3. PackageManager.hasSystemFeature(PackageManager.FEATURE_VR_MODE_HIGH_PERFORMANCE)
   → устаревший, но полезен на Quest 1/2.
```

Если хотя бы одно условие истинно — устройство считается XR-хедсетом.

### 4.2 Новый объект `XrDeviceDetector`

```
core/xr/XrDeviceDetector.kt   (main source set, нет BuildConfig-гарда)
```

```kotlin
object XrDeviceDetector {
    /** Returns true if the device is a VR/XR headset (Quest, etc.). */
    fun isXrHeadset(context: Context): Boolean {
        val pm = context.packageManager
        if (pm.hasSystemFeature("android.hardware.vr.headtracking")) return true
        if (pm.hasSystemFeature(PackageManager.FEATURE_VR_MODE_HIGH_PERFORMANCE)) return true
        val mfr = Build.MANUFACTURER.lowercase(Locale.ROOT)
        return mfr == "oculus" || mfr == "meta"
    }
}
```

Этот же объект **уже нужен** в `PlayerEntryCoordinator` для определения `DeviceClass.HEADSET`
(см. §8 Архитектурные замечания) — вынос в отдельный util избавляет от дублирования логики.

---

## 5. Механизм окна (Panel Window Sizing)

### 5.1 Почему `WindowManager.LayoutParams`, а не manifest `<layout>`

| Вариант | Плюсы | Минусы |
|---------|-------|--------|
| `<layout android:defaultWidth android:defaultHeight>` в manifest | Работает без кода | Статический; нельзя восстановить сохранённый размер; применяется только при первом создании Activity |
| `window.attributes` в `onCreate` / `onWindowFocusChanged` | Полный контроль; можно читать сохранённый размер | Нужен код; HorizonOS может проигнорировать запрос если окно заблокировано снаружи |
| Meta Spatial Panel API (`com.oculus.intent.extra.PANEL_SIZE`) | Точный размер в метрах | Только vr flavor; недоступен в standard/lite/photos |

Выбран **комбинированный подход**:
- В manifest (vr AndroidManifest.xml) — статический `<layout>` как fallback для первого запуска.
- В `onCreate` `MainActivity` — runtime-корректировка через `WindowManager.LayoutParams`
  с чтением сохранённого размера.

### 5.2 Пресет `DEFAULT_VR_PANEL`

| Параметр | Значение |
|----------|----------|
| Ширина | 1920 px (в единицах экранного пространства Quest) |
| Высота | 1080 px |
| Ориентация | Landscape (принудительно через `requestedOrientation`) |
| Минимальный размер для сохранения | 800 × 400 px |

> **Почему 1920×1080?** Quest 3 рендерит панели в виртуальном пространстве с физически
> видимым разрешением ~1920–2560 виртуальных пикселей по ширине. Значение 1920 даёт большой,
> но не чрезмерный "кинотеатральный" размер, который не перекрывает всё поле зрения.
> Если пользователь уменьшит окно — новый размер сохраняется.

### 5.3 Manifest-фрагмент (vr source set, fallback для первого запуска)

```xml
<!-- app_v2/src/vr/AndroidManifest.xml -->
<activity android:name="com.sza.fastmediasorter.ui.main.MainActivity">
    <!-- Default large landscape panel for Quest panel mode.
         HorizonOS respects <layout> on initial window creation only;
         MainActivity.onCreate() overrides this with the persisted size if available. -->
    <layout
        android:defaultWidth="1920dp"
        android:defaultHeight="1080dp"
        android:gravity="center"
        android:minWidth="400dp"
        android:minHeight="300dp" />
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="com.oculus.intent.category.2D" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```

Флейворы standard/lite/photos/legacy не имеют vr AndroidManifest.xml — для них размер
управляется только runtime-кодом в `MainActivity`.

---

## 6. Сохранение и восстановление размера

### 6.1 Новый класс `VrPanelSizePreference`

```
core/xr/VrPanelSizePreference.kt   (main source set)
```

Тонкая обёртка над `SharedPreferences` — без Room, без DI, намеренно простая:

```kotlin
object VrPanelSizePreference {
    private const val PREFS = "vr_panel_size"
    private const val KEY_W = "panel_w_px"
    private const val KEY_H = "panel_h_px"

    // Default first-launch size (pixels in Quest virtual display)
    const val DEFAULT_W = 1920
    const val DEFAULT_H = 1080
    private const val MIN_W  = 800
    private const val MIN_H  = 400

    fun save(context: Context, widthPx: Int, heightPx: Int) {
        // Guard: ignore tiny windows (minimised, snapped, or system-resized)
        if (widthPx < MIN_W || heightPx < MIN_H) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putInt(KEY_W, widthPx); putInt(KEY_H, heightPx)
        }
    }

    fun load(context: Context): Pair<Int, Int> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return p.getInt(KEY_W, DEFAULT_W) to p.getInt(KEY_H, DEFAULT_H)
    }

    /** Returns true if user has previously persisted a custom size. */
    fun hasUserSize(context: Context): Boolean {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return p.contains(KEY_W)
    }
}
```

### 6.2 Точки интеграции в `MainActivity`

#### Шаг A — Восстановление при запуске (`onCreate`, до `super.onCreate`)

```kotlin
// MainActivity.kt  — before super.onCreate() so the window param is applied early
if (XrDeviceDetector.isXrHeadset(this)) {
    val (w, h) = VrPanelSizePreference.load(this)
    val lp = window.attributes
    lp.width  = w
    lp.height = h
    window.attributes = lp
    // Force landscape — Quest panel starts portrait by default if not set
    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
}
```

#### Шаг B — Сохранение при изменении размера (`onWindowFocusChanged`)

HorizonOS delivers a new window size to the Activity via a configuration change
(width/height change), which triggers `onConfigurationChanged`. Hook that, **not**
`onWindowFocusChanged`, so the save is triggered only once per actual resize event.

```kotlin
override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    if (!XrDeviceDetector.isXrHeadset(this)) return

    // Read the actual current window size via WindowMetrics (API 30+)
    val bounds = windowManager.currentWindowMetrics.bounds   // Rect
    val w = bounds.width()
    val h = bounds.height()
    Timber.d("VR panel resize detected: ${w}×${h}")
    VrPanelSizePreference.save(this, w, h)
}
```

> На API < 30 (т.е. никогда в этом проекте, так как minSdk=26 → через несколько лет это
> условие не возникнет) — использовать `windowManager.defaultDisplay.getRealSize(Point())`.
> Для простоты в spec мы считаем API 30+ достаточным (все Quest 3 = Android 12 = API 32).

---

## 7. Обработка всех флейворов

### 7.1 Почему не нужен `BuildConfig.SUPPORT_VR_PLAYER`

На телефонах/планшетах `XrDeviceDetector.isXrHeadset()` вернёт `false` и весь код
не выполнится. Дополнительного BuildConfig-гарда не требуется.

### 7.2 Почему `main` source set, а не `vr` source set

- Флейворы standard/lite/photos/legacy могут быть установлены на Quest 3 через Google Play
  (Quest поддерживает APK из обычного Play, если manifest не блокирует).
- `BuildConfig.SUPPORT_VR_PLAYER` = false в non-vr флейворах, но `isXrHeadset()` работает
  независимо от этого флага.
- Код в `main` source set компилируется во все флейворы и активируется только на хедсетах.

### 7.3 Матрица поведения

| Флейвор | Устройство | Результат |
|---------|-----------|----------|
| any | Quest 3 (первый запуск) | Окно 1920×1080 landscape |
| any | Quest 3 (повторный запуск) | Окно = сохранённый размер |
| any | Телефон / планшет | Без изменений |
| `vr` | Quest 3 (первый запуск) | `<layout>` manifest → 1920×1080dp + runtime override |

---

## 8. Архитектурные замечания (ADR)

### ADR-W1: SharedPreferences вместо Room для размера окна

**Контекст:** Размер окна нужно читать в `onCreate` **до** `super.onCreate()` — Room
инициализируется позже через Hilt. SharedPreferences доступны немедленно.

**Решение:** Отдельный `"vr_panel_size"` SharedPreferences-файл с двумя Int-значениями.
Никакой миграции схемы не требуется.

### ADR-W2: `onConfigurationChanged` для отслеживания изменений размера

**Контекст:** Quest 3 доставляет изменение размера окна через `onConfigurationChanged`
(width/height), а не через `onWindowSizeChanged` API Jetpack WindowManager (который требует
Compose / WindowSizeClass).

**Решение:** Переопределить `onConfigurationChanged` в `MainActivity`. Список
`android:configChanges` в manifest уже содержит `screenSize|screenLayout` — дополнительного
объявления не требуется.

### ADR-W3: `XrDeviceDetector` в `core/xr/` — замена дублированной логики

**Контекст:** `PlayerEntryCoordinator` получает `DeviceClass` снаружи, но нигде в code base
нет единого места определения «это хедсет?».

**Решение:** `XrDeviceDetector.kt` в `core/xr/`. Существующий код в `MainActivity` (строка
1226) и `VrPlayerActivity` может мигрировать на этот детектор в рамках этой же задачи или
отдельным рефактором.

---

## 9. Тестирование

### 9.1 Unit tests (`VrPanelSizePreferenceTest`)

| Сценарий | Ожидание |
|----------|----------|
| `load()` без сохранения | Возвращает DEFAULT_W × DEFAULT_H |
| `save(1280, 800)` → `load()` | Возвращает 1280 × 800 |
| `save(100, 50)` (< MIN) → `load()` | Не сохраняет; возвращает DEFAULT |
| `hasUserSize()` без сохранения | false |
| `hasUserSize()` после `save()` | true |

### 9.2 Unit tests (`XrDeviceDetectorTest`)

| Сценарий | Ожидание |
|----------|----------|
| `hasSystemFeature("android.hardware.vr.headtracking") = true` | `isXrHeadset()` = true |
| Нет фич, MANUFACTURER = "Meta" | `isXrHeadset()` = true |
| Нет фич, MANUFACTURER = "Samsung" | `isXrHeadset()` = false |

### 9.3 Manual QA на Quest 3

| Шаг | Ожидание |
|-----|----------|
| Первый запуск (standard) | Окно ~1920×1080, горизонтальная ориентация |
| Ручное уменьшение окна | Новый размер сохраняется |
| Перезапуск | Окно восстанавливается в сохранённый размер |
| То же для lite, photos, legacy, vr | Аналогичное поведение |
| Запуск на телефоне (standard) | Нет изменений, requestedOrientation не трогается |

---

## 10. Затронутые файлы

| Файл | Действие |
|------|---------|
| `app_v2/src/main/java/.../core/xr/XrDeviceDetector.kt` | **НОВЫЙ** — детектор хедсета |
| `app_v2/src/main/java/.../core/xr/VrPanelSizePreference.kt` | **НОВЫЙ** — чтение/запись размера |
| `app_v2/src/main/java/.../ui/main/MainActivity.kt` | Изменение: вызов детектора в `onCreate`; `onConfigurationChanged` |
| `app_v2/src/vr/AndroidManifest.xml` | Изменение: добавить `<layout>` для `MainActivity` |
| `app_v2/src/test/.../core/xr/XrDeviceDetectorTest.kt` | **НОВЫЙ** — unit tests |
| `app_v2/src/test/.../core/xr/VrPanelSizePreferenceTest.kt` | **НОВЫЙ** — unit tests |

---

## 11. Рискообразующие факторы

| Риск | Вероятность | Митигация |
|------|:-----------:|-----------|
| HorizonOS игнорирует `window.attributes.width/height` до `setContentView` | Средняя | Применять также в `onWindowFocusChanged(hasFocus=true)` как дублирующий вызов |
| Quest доставляет несколько `onConfigurationChanged` при первом запуске (системные resize) | Средняя | MIN_W/MIN_H guard предотвращает сохранение мелких промежуточных размеров |
| На standard/lite/photos флейворах `<layout>` тега нет (нет vr AndroidManifest.xml) | Низкая — это норма | Runtime override в `MainActivity` достаточен |
| Конфликт с `requestedOrientation` в `PlayerActivity` при переходе в плеер | Низкая | Плеер уже принудительно landscape; `MainActivity` сбрасывает orientation при onResume |

---

## 12. Объём работы

| Фаза | Оценка |
|------|--------|
| `XrDeviceDetector.kt` + `VrPanelSizePreference.kt` | ~60 строк |
| `MainActivity` изменения | ~25 строк |
| `vr/AndroidManifest.xml` изменение | ~8 строк |
| Unit tests | ~80 строк |
| **Итого** | **~173 строки** |

Tier 2 — задача для одной итерации без блокировки других направлений.

---

## 13. Связанные спецификации

- [spec_vr-master.md](spec_vr-master.md) — мастер-документ VR edition
- [spec_vr-panel-and-immersive.md](spec_vr-panel-and-immersive.md) — двухрежимная архитектура
- [TASK_VR_3D_IMMERSIVE_INVESTIGATION_2026-04-21.md](../dev/TASK_VR_3D_IMMERSIVE_INVESTIGATION_2026-04-21.md)
