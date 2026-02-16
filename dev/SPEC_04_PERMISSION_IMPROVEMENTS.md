# Спецификация #4: Улучшения запроса и управления правами

## Описание задачи
Реализовать три улучшения системы управления разрешениями:
1. Предупреждение об отсутствии прав перед созданием локального ресурса
2. Изменение кнопок запроса прав в настройках, если права уже предоставлены
3. Переименование кнопок в "Управление правами" с переходом в системные настройки

## Требования

### Функциональные требования

#### 1. Предупреждение перед созданием локального ресурса
- При попытке создать новый локальный ресурс проверять наличие медиа-разрешений
- Если разрешения отсутствуют, показывать диалог с предупреждением
- Предлагать запросить разрешения прямо из диалога

#### 2. Адаптивные кнопки в настройках
- Если разрешения НЕ предоставлены: кнопка "Запросить разрешения" (активна)
- Если разрешения предоставлены: кнопка "Управление правами" (активна, но другой функционал)

#### 3. Переход в системные настройки
- При клике на "Управление правами" открывать системную страницу настроек приложения
- Пользователь может отозвать разрешения

## Пошаговая реализация

### Часть 1: Предупреждение при создании локального ресурса

#### Шаг 1.1: Найти код создания локального ресурса
1. Найти Activity/Fragment, отвечающий за создание нового ресурса
2. Вероятные файлы: `AddSourceActivity.kt`, `CreateResourceDialog.kt` или аналогичные

#### Шаг 1.2: Создать утилиту проверки разрешений
1. Создать файл `PermissionChecker.kt`:
   ```kotlin
   object PermissionChecker {
       
       fun hasMediaPermissions(context: Context): Boolean {
           val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
               arrayOf(
                   Manifest.permission.READ_MEDIA_IMAGES,
                   Manifest.permission.READ_MEDIA_VIDEO,
                   Manifest.permission.READ_MEDIA_AUDIO
               )
           } else {
               arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
           }
           
           return permissions.all { permission ->
               ContextCompat.checkSelfPermission(context, permission) == 
                   PackageManager.PERMISSION_GRANTED
           }
       }
   }
   ```

#### Шаг 1.3: Добавить проверку перед созданием ресурса
1. В методе создания локального ресурса добавить проверку:
   ```kotlin
   private fun createLocalResource() {
       if (!PermissionChecker.hasMediaPermissions(this)) {
           showPermissionRequiredDialog()
           return
       }
       // Продолжить создание ресурса
       proceedWithResourceCreation()
   }
   
   private fun showPermissionRequiredDialog() {
       AlertDialog.Builder(this)
           .setTitle(R.string.permissions_required_title)
           .setMessage(R.string.permissions_required_for_local_resource)
           .setPositiveButton(R.string.grant_permissions) { _, _ ->
               requestPermissions()
           }
           .setNegativeButton(R.string.cancel, null)
           .show()
   }
   ```

#### Шаг 1.4: Добавить строки
```xml
<string name="permissions_required_title">Требуются разрешения</string>
<string name="permissions_required_for_local_resource">Для создания локального ресурса необходим доступ к медиа-файлам. Предоставьте разрешения для продолжения.</string>
```

### Часть 2: Адаптивные кнопки в настройках

#### Шаг 2.1: Найти экран настроек
1. Найти файл настроек, вероятно `SettingsFragment.kt` или `SettingsActivity.kt`
2. Найти layout файл с кнопками разрешений

#### Шаг 2.2: Модифицировать логику кнопок
1. В коде настроек добавить метод обновления UI кнопок:
   ```kotlin
   private fun updatePermissionButtonsState() {
       val hasPermissions = PermissionChecker.hasMediaPermissions(requireContext())
       
       binding.permissionButton.apply {
           if (hasPermissions) {
               text = getString(R.string.manage_permissions)
               setOnClickListener { openAppSettings() }
           } else {
               text = getString(R.string.request_permissions)
               setOnClickListener { requestMediaPermissions() }
           }
           isEnabled = true
       }
   }
   ```

#### Шаг 2.3: Реализовать открытие системных настроек
```kotlin
private fun openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", requireContext().packageName, null)
    }
    startActivity(intent)
}
```

#### Шаг 2.4: Обновлять состояние при возврате
```kotlin
override fun onResume() {
    super.onResume()
    updatePermissionButtonsState()
}
```

#### Шаг 2.5: Добавить строки
```xml
<string name="manage_permissions">Управление правами</string>
<string name="request_permissions">Запросить разрешения</string>
```

### Часть 3: Интеграция и тестирование

#### Шаг 3.1: Тестирование создания ресурса
1. Отозвать все медиа-разрешения в системных настройках
2. Попытаться создать новый локальный ресурс
3. Проверить:
   - Появляется диалог с предупреждением
   - Можно запросить разрешения из диалога
   - После предоставления разрешений создание продолжается

#### Шаг 3.2: Тестирование кнопок в настройках
1. **Без разрешений:**
   - Открыть настройки
   - Проверить, что кнопка называется "Запросить разрешения"
   - Нажать кнопку, предоставить разрешения
   
2. **С разрешениями:**
   - Вернуться в настройки
   - Проверить, что кнопка теперь "Управление правами"
   - Нажать кнопку
   - Проверить, что открылись системные настройки приложения
   - Отозвать разрешения
   - Вернуться в приложение
   - Проверить, что кнопка снова "Запросить разрешения"

## Критерии приемки
- ✅ При создании локального ресурса без разрешений показывается предупреждение
- ✅ Из предупреждения можно запросить разрешения
- ✅ В настройках кнопка адаптируется в зависимости от состояния разрешений
- ✅ Кнопка "Управление правами" открывает системные настройки приложения
- ✅ Состояние кнопок обновляется при возврате из системных настроек
- ✅ Функционал работает на Android 10-14+

## Файлы для изменения
- Создать: `app_v2/src/main/java/.../utils/PermissionChecker.kt`
- Изменить: Activity/Fragment создания ресурса
- Изменить: `SettingsFragment.kt` / `SettingsActivity.kt`
- Изменить: `res/values/strings.xml` (+ переводы)

## Зависимости
- Связано со Спецификацией #3

## Примечания
- Убедиться, что проверка разрешений работает на всех версиях Android
- Рассмотреть возможность использования единого PermissionManager для всего приложения
