## Дизайн решения

### 1. Контекст

- `BaseActivity` уже вызывает `enableEdgeToEdge()` для большинства Activity.
- Часть экранов обрабатывает inset'ы вручную, часть full-screen экранов использует `WindowCompat.setDecorFitsSystemWindows(window, false)`.
- В `values-v35` уже есть API 35 override для `bottomSheetDialogTheme`, но он включает только `enableEdgeToEdge=true`.
- В проекте зафиксированы:
- `com.google.android.material:material:1.13.0`;
- `androidx.appcompat:appcompat:1.7.0`;
- `androidx.activity:*:1.8.2`.

### 2. Наблюдение

- По официальной документации `BottomSheetDialog` включает edge-to-edge ветку автоматически, когда одновременно выполнены два условия:
- `enableEdgeToEdge=true`;
- `navigationBarColor` прозрачен или полупрозрачен.
- У нас второе условие в API 35 теме для BottomSheet не задано.
- AndroidX Activity новых версий содержит дополнительные исправления edge-to-edge для API 35 по сравнению с `1.8.2`.
- AppCompat `1.7.1` является ближайшим стабильным патч-апдейтом относительно текущего `1.7.0`.

### 3. Решение

- В API 35 light/night темах BottomSheet:
- оставить `enableEdgeToEdge=true`;
- добавить прозрачный `android:navigationBarColor`;
- включить автоматические `padding*SystemWindowInsets` по нижнему/боковым краям.
- Обновить зависимости:
- `androidx.appcompat:appcompat` -> `1.7.1`;
- `androidx.activity:activity-compose` -> `1.10.1`;
- `androidx.activity:activity-ktx` -> `1.10.1`.

### 3.1 Ограничение по toolchain

- Попытка поднять `androidx.activity` до `1.12.4` не подходит проекту:
- эта ветка требует `compileSdk 36`;
- проект зафиксирован на `compileSdk 35`.
- Поэтому выбран ближайший стабильный апдейт без требования поднятия compileSdk: `1.10.1`.

### 4. Почему это минимально-рискованно

- Не меняем layout XML экранов и не трогаем существующую Activity-логику.
- BottomSheet получает официально рекомендованный theme-based edge-to-edge путь.
- Dependency update ограничен стабильными версиями и направлен именно на системные/UI библиотеки.

### 5. Проверка

- Целевая валидация: `.\a.ps1 fc`.
- Дополнительно: review diff по темам и зависимостям на предмет нежелательных UI-изменений.
