# 🧪 Testing in Android Studio with Virtual Device

## ✅ Unit Tests Successfully Set Up!

Тесты готовы к запуску. Добавлена полная инфраструктура для автоматизированного тестирования.

## 📁 Созданные тесты

### Unit Tests (без эмулятора):
- ✅ `ExampleUnitTest` - базовые примеры
- ✅ `MediaUtilsTest` - тестирование утилит для медиафайлов
- ✅ `PreferenceManagerTest` - тестирование настроек

### Instrumented Tests (требуют эмулятор):
- ✅ `MainActivityTest` - тестирование главного экрана
- ✅ `SettingsActivityTest` - тестирование настроек
- ✅ `LanguageTest` - тестирование переключения языков

## 🚀 Запуск в Android Studio

### Способ 1: Через интерфейс

#### Unit Tests (быстрые, без эмулятора):
1. Открыть файл теста (например `ExampleUnitTest.kt`)
2. Кликнуть зелёный треугольник ▶️ рядом с классом или методом
3. Выбрать `Run 'ExampleUnitTest'`

#### Instrumented Tests (UI тесты, нужен эмулятор):
1. Запустить эмулятор (см. ниже)
2. Открыть файл теста (например `SettingsActivityTest.kt`)
3. Кликнуть зелёный треугольник ▶️
4. Выбрать `Run 'SettingsActivityTest'`

### Способ 2: Через Gradle панель

1. Открыть `View` → `Tool Windows` → `Gradle`
2. Развернуть `app` → `Tasks` → `verification`
3. Двойной клик на:
   - `testDebugUnitTest` - unit тесты
   - `connectedDebugAndroidTest` - UI тесты (требует эмулятор)

## 📱 Настройка эмулятора

### Создание Virtual Device:

1. `Tools` → `Device Manager`
2. Нажать `Create Device` (`+`)
3. Выбрать `Phone` → `Pixel 5` → `Next`
4. Выбрать System Image:
   - **Release Name**: `S` (API Level 31)
   - **ABI**: `x86_64`
   - Нажать `Download` если нужно
5. `Next` → `Finish`

### Запуск эмулятора:

1. `Tools` → `Device Manager`
2. Нажать ▶️ `Play` рядом с устройством
3. Подождать загрузки (1-2 минуты)

## 💻 Команды терминала

```powershell
# Unit тесты (быстро, без эмулятора)
.\gradlew testDebugUnitTest

# UI тесты (требуется запущенный эмулятор)
.\gradlew connectedDebugAndroidTest

# Собрать приложение
.\gradlew assembleDebug

# Очистка
.\gradlew clean
```

## 📊 Просмотр результатов

### После запуска тестов:

**Unit Tests отчёт:**
```
app\build\reports\tests\testDebugUnitTest\index.html
```

**Instrumented Tests отчёт:**
```
app\build\reports\androidTests\connected\index.html
```

Открыть в браузере для детального просмотра.

## 🎯 Пример: Запуск первого теста

1. **Открыть** `app/src/test/java/com/sza/fastmediasorter/ExampleUnitTest.kt`
2. **Кликнуть** зелёный ▶️ рядом с `class ExampleUnitTest`
3. **Выбрать** `Run 'ExampleUnitTest'`
4. **Результат** появится в нижней панели:
   ```
   ✓ addition_isCorrect (0.001s)
   ✓ string_concatenation_isCorrect (0.000s)
   
   2 tests passed
   ```

## 📝 Добавление своих тестов

### Unit Test:

```kotlin
// app/src/test/java/com/sza/fastmediasorter/MyTest.kt
package com.sza.fastmediasorter

import org.junit.Test
import org.junit.Assert.*

class MyTest {
    @Test
    fun myTest() {
        val result = 2 + 2
        assertEquals(4, result)
    }
}
```

### UI Test:

```kotlin
// app/src/androidTest/java/com/sza/fastmediasorter/MyUITest.kt
package com.sza.fastmediasorter

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MyUITest {
    @get:Rule
    val activityRule = ActivityTestRule(MainActivity::class.java)

    @Test
    fun checkView() {
        onView(withId(R.id.myView))
            .check(matches(isDisplayed()))
    }
}
```

## ⚙️ Конфигурация

### Добавлено в `build.gradle.kts`:

```kotlin
dependencies {
    // Unit Tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.7.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    
    // Instrumented Tests
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test:rules:1.5.0")
}

testOptions {
    unitTests.isReturnDefaultValues = true
    animationsDisabled = true
}
```

### JVM Target: Java 11

```kotlin
compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlinOptions {
    jvmTarget = "11"
}
```

## 🐛 Troubleshooting

### "No tests found"
**Решение**: Sync Project (`File` → `Sync Project with Gradle Files`)

### "No connected devices"
**Решение**: Запустить эмулятор через `Device Manager`

### Эмулятор тормозит
**Решение**: 
- Увеличить RAM в AVD настройках (2GB → 4GB)
- Включить Hardware Acceleration (HAXM/WHPX)

### Тесты падают случайно
**Решение**: 
- Отключить анимации в эмуляторе:
  1. Settings → Developer options
  2. Window animation scale → Off
  3. Transition animation scale → Off
  4. Animator duration scale → Off

## 📚 Дополнительно

- **Полная документация**: См. `TESTING.md`
- **Android Testing Guide**: https://developer.android.com/training/testing
- **Espresso Cheat Sheet**: https://developer.android.com/training/testing/espresso/cheat-sheet

---

✅ **Готово к использованию!** Просто запустите любой тест через Android Studio.
