# Спецификация #1: Иконки для разных версий приложения

## Описание задачи
Необходимо создать уникальные иконки для различных вариантов сборки приложения (debug, lite версии), сохранив текущую иконку только для основной (production) версии.

## Требования

### Функциональные требования
- Основная версия приложения должна сохранить текущую иконку
- Debug версия должна иметь отличительную иконку с маленькой буквой "D"
- Lite версия должна иметь свою уникальную иконку
- Другие варианты сборки (если есть) должны иметь отличительные иконки

### Технические требования
- Иконки должны быть в формате PNG
- Необходимо создать иконки для всех требуемых разрешений (mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi)
- Иконки должны соответствовать руководству Material Design

## Пошаговая реализация

### Шаг 1: Подготовка ресурсов
1. Создать варианты иконок:
   - `ic_launcher_debug.png` - для debug версии (с буквой "D")
   - `ic_launcher_lite.png` - для lite версии
   - Другие варианты по необходимости

2. Создать адаптивные иконки (если используются):
   - `ic_launcher_debug_foreground.xml`
   - `ic_launcher_debug_background.xml`
   - Аналогично для lite версии

### Шаг 2: Организация ресурсов
1. Разместить иконки в соответствующих директориях для каждого build variant:
   ```
   app_v2/src/debug/res/mipmap-mdpi/ic_launcher.png
   app_v2/src/debug/res/mipmap-hdpi/ic_launcher.png
   app_v2/src/debug/res/mipmap-xhdpi/ic_launcher.png
   app_v2/src/debug/res/mipmap-xxhdpi/ic_launcher.png
   app_v2/src/debug/res/mipmap-xxxhdpi/ic_launcher.png
   
   app_v2/src/lite/res/mipmap-*/ic_launcher.png
   ```

### Шаг 3: Конфигурация в build.gradle
1. Открыть файл `app_v2/build.gradle.kts`
2. Убедиться, что build variants правильно настроены:
   ```kotlin
   android {
       buildTypes {
           debug {
               applicationIdSuffix = ".debug"
               // Иконка будет браться из src/debug/res/
           }
       }
       
       flavorDimensions += "version"
       productFlavors {
           create("lite") {
               dimension = "version"
               applicationIdSuffix = ".lite"
               // Иконка будет браться из src/lite/res/
           }
       }
   }
   ```

### Шаг 4: Создание XML дескрипторов иконок
1. Для каждого варианта создать соответствующий `ic_launcher.xml` в `src/{variant}/res/mipmap-anydpi-v26/`:
   ```xml
   <?xml version="1.0" encoding="utf-8"?>
   <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
       <background android:drawable="@drawable/ic_launcher_background"/>
       <foreground android:drawable="@drawable/ic_launcher_foreground"/>
   </adaptive-icon>
   ```

### Шаг 5: Тестирование
1. Собрать debug версию: `./gradlew assembleDebug`
2. Собрать lite версию: `./gradlew assembleLite`
3. Установить на устройство и проверить, что иконки отображаются корректно
4. Убедиться, что на домашнем экране все версии имеют разные иконки

## Критерии приемки
- ✅ Debug версия имеет иконку с буквой "D"
- ✅ Lite версия имеет свою уникальную иконку
- ✅ Production версия сохранила оригинальную иконку
- ✅ Все иконки корректно отображаются на разных версиях Android (от API 21 до текущей)
- ✅ Иконки корректно отображаются на всех разрешениях экранов

## Зависимости
- Дизайнер должен предоставить варианты иконок
- Необходимо знать все существующие build variants проекта

## Примечания
- Убедиться, что иконки визуально согласуются с основной иконкой приложения
- Различия должны быть очевидными, но не слишком радикальными
- Рассмотреть возможность добавления цветовых акцентов (например, debug - красный оттенок, lite - синий)
