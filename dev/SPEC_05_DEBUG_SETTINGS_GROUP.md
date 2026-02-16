# Спецификация #5: Группировка Debug настроек

## Описание задачи
Вынести все debug-специфичные настройки в отдельную группу на странице "Основные настройки", которая будет скрыта в release версии приложения.

## Требования

### Функциональные требования
- Все debug-настройки должны быть сгруппированы в отдельной секции
- Секция должна быть видна только в debug сборках
- В release версии эта группа не должна отображаться
- Секция должна иметь ясный заголовок "DEBUG" или "Отладка"

### Технические требования
- Использовать BuildConfig для определения типа сборки
- Группировка должна быть реализована через PreferenceCategory (если используется PreferenceFragment)
- Код не должен содержать debug-логики в release

## Пошаговая реализация

### Шаг 1: Идентификация debug настроек
1. Открыть файл настроек (вероятно `res/xml/preferences.xml` или аналогичный)
2. Найти все настройки, которые являются debug-специфичными, например:
   - Логирование
   - Отладочные экраны
   - Тестовые функции
   - Developer опции
   - Экспериментальные функции

### Шаг 2: Создание debug ресурсов
1. Создать отдельный preference XML для debug настроек:
   `res/xml/preferences_debug.xml`:
   ```xml
   <?xml version="1.0" encoding="utf-8"?>
   <PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">
       
       <PreferenceCategory
           android:key="debug_category"
           android:title="@string/debug_settings_title">
           
           <!-- Debug настройки здесь -->
           <SwitchPreferenceCompat
               android:key="enable_logging"
               android:title="@string/enable_logging_title"
               android:defaultValue="false" />
           
           <SwitchPreferenceCompat
               android:key="show_debug_info"
               android:title="@string/show_debug_info_title"
               android:defaultValue="false" />
           
           <!-- Другие debug настройки -->
           
       </PreferenceCategory>
       
   </PreferenceScreen>
   ```

### Шаг 3: Добавление строковых ресурсов
1. В `res/values/strings.xml`:
   ```xml
   <string name="debug_settings_title">🔧 ОТЛАДКА</string>
   <string name="enable_logging_title">Включить логирование</string>
   <string name="show_debug_info_title">Показывать отладочную информацию</string>
   ```

### Шаг 4: Условная загрузка preferences
Вариант А: Программное добавление (рекомендуется)

1. В `SettingsFragment.kt`:
   ```kotlin
   override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
       setPreferencesFromResource(R.xml.preferences, rootKey)
       
       // Добавить debug настройки только в debug сборке
       if (BuildConfig.DEBUG) {
           addDebugPreferences()
       }
   }
   
   private fun addDebugPreferences() {
       // Создать категорию
       val debugCategory = PreferenceCategory(requireContext()).apply {
           key = "debug_category"
           title = getString(R.string.debug_settings_title)
           // Можно установить специальный цвет для категории
           isIconSpaceReserved = false
       }
       
       // Добавить в preferenceScreen
       preferenceScreen.addPreference(debugCategory)
       
       // Добавить debug preferences
       addPreferencesFromResource(R.xml.preferences_debug)
       
       // Переместить debug preferences в debug категорию
       moveDebugPreferencesToCategory(debugCategory)
   }
   
   private fun moveDebugPreferencesToCategory(category: PreferenceCategory) {
       // Список ключей debug настроек
       val debugKeys = listOf(
           "enable_logging",
           "show_debug_info",
           // другие debug ключи
       )
       
       debugKeys.forEach { key ->
           findPreference<Preference>(key)?.let { pref ->
               preferenceScreen.removePreference(pref)
               category.addPreference(pref)
           }
       }
   }
   ```

Вариант Б: Использование отдельных XML файлов

1. Создать `res/xml/preferences.xml` (для release):
   ```xml
   <?xml version="1.0" encoding="utf-8"?>
   <PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">
       <!-- Обычные настройки -->
   </PreferenceScreen>
   ```

2. Создать `res/xml-debug/preferences.xml` (будет использоваться только в debug):
   ```xml
   <?xml version="1.0" encoding="utf-8"?>
   <PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">
       <!-- Обычные настройки -->
       
       <PreferenceCategory
           android:key="debug_category"
           android:title="@string/debug_settings_title">
           <!-- Debug настройки -->
       </PreferenceCategory>
   </PreferenceScreen>
   ```

### Шаг 5: Визуальное выделение debug секции
1. Добавить стиль для debug категории в `res/values/styles.xml`:
   ```xml
   <style name="DebugPreferenceCategory" parent="PreferenceCategory">
       <item name="android:textColor">#FF6B6B</item>
       <item name="android:textStyle">bold</item>
   </style>
   ```

2. Применить стиль программно или через XML

### Шаг 6: Тестирование

#### Тестирование debug сборки
1. Собрать debug версию: `./gradlew assembleDebug`
2. Установить на устройство
3. Открыть настройки
4. Проверить:
   - Секция "DEBUG" / "Отладка" присутствует
   - Все debug настройки находятся в этой секции
   - Секция визуально выделена (если применено)

#### Тестирование release сборки
1. Собрать release версию: `./gradlew assembleRelease`
2. Установить на другое устройство или удалить debug версию
3. Открыть настройки
4. Проверить:
   - Секция "DEBUG" полностью отсутствует
   - Debug настройки не отображаются
   - Нет пустых мест в UI

### Шаг 7: Проверка ProGuard
1. Убедиться, что debug код удаляется в release:
   - Открыть `app/build/outputs/mapping/release/usage.txt`
   - Проверить, что debug preferences не включены в release APK

## Критерии приемки
- ✅ В debug версии все debug настройки сгруппированы в отдельной секции
- ✅ Секция имеет четкий заголовок "DEBUG" или "Отладка"
- ✅ В release версии debug секция полностью отсутствует
- ✅ Debug секция визуально выделена (опционально)
- ✅ Отсутствие debug кода в release APK (проверить через ProGuard/R8)

## Файлы для изменения
- `app_v2/src/main/res/xml/preferences.xml`
- Создать: `app_v2/src/main/res/xml/preferences_debug.xml` (или `res/xml-debug/preferences.xml`)
- `app_v2/src/main/java/.../SettingsFragment.kt`
- `app_v2/src/main/res/values/strings.xml`

## Зависимости
- Текущая реализация Settings screen
- BuildConfig.DEBUG

## Примечания
- Рассмотреть добавление счетчика кликов для активации скрытых debug настроек в release (easter egg)
- Можно добавить предупреждение в debug категорию: "Эти настройки только для разработчиков"
