# Спецификация #3: Запрос прав на медиа в Welcome Flow

## Описание задачи
Добавить запрос прав доступа к медиа-файлам в процесс прохождения экранов приветствия (Welcome Activity), чтобы пользователь получал соответствующие запросы во время первого запуска приложения.

## Требования

### Функциональные требования
- Пользователь должен получить запрос на доступ к медиа во время прохождения Welcome Activity
- Запрос должен появляться на логичном этапе (после объяснения функционала приложения)
- Если пользователь отказывается, приложение должно предупредить о последствиях
- Возможность повторно запросить права, если они были отклонены

### Технические требования
- Использовать современный API разрешений (ActivityResultContracts)
- Поддерживать разные версии Android (API 21+)
- Корректно обрабатывать разрешения для Android 13+ (READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, READ_MEDIA_AUDIO)
- Корректно обрабатывать разрешения для Android 10-12 (READ_EXTERNAL_STORAGE)

## Пошаговая реализация

### Шаг 1: Определить нужные разрешения
1. Определить список разрешений в зависимости от версии Android:
   ```kotlin
   private fun getRequiredMediaPermissions(): Array<String> {
       return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
           arrayOf(
               Manifest.permission.READ_MEDIA_IMAGES,
               Manifest.permission.READ_MEDIA_VIDEO,
               Manifest.permission.READ_MEDIA_AUDIO
           )
       } else {
           arrayOf(
               Manifest.permission.READ_EXTERNAL_STORAGE
           )
       }
   }
   ```

### Шаг 2: Добавить страницу объяснения в Welcome Activity
1. Создать новый layout для страницы объяснения прав:
   `res/layout/fragment_welcome_permissions.xml`
   ```xml
   <?xml version="1.0" encoding="utf-8"?>
   <androidx.constraintlayout.widget.ConstraintLayout>
       <ImageView
           android:id="@+id/permissionIcon"
           android:src="@drawable/ic_permissions"
           ... />
       
       <TextView
           android:id="@+id/permissionTitle"
           android:text="@string/welcome_permissions_title"
           ... />
       
       <TextView
           android:id="@+id/permissionDescription"
           android:text="@string/welcome_permissions_description"
           ... />
       
       <Button
           android:id="@+id/grantPermissionsButton"
           android:text="@string/grant_permissions"
           ... />
   </androidx.constraintlayout.widget.ConstraintLayout>
   ```

### Шаг 3: Добавить строковые ресурсы
1. В `res/values/strings.xml`:
   ```xml
   <string name="welcome_permissions_title">Доступ к медиа-файлам</string>
   <string name="welcome_permissions_description">Для работы приложения требуется доступ к вашим фото, видео и аудио файлам. Мы не передаем ваши файлы третьим лицам.</string>
   <string name="grant_permissions">Предоставить доступ</string>
   <string name="permissions_denied_warning">Без доступа к медиа-файлам основной функционал приложения будет недоступен</string>
   ```

2. В `res/values-ru/strings.xml` и `res/values-uk/strings.xml` добавить переводы

### Шаг 4: Создать фрагмент для страницы разрешений
1. Создать `WelcomePermissionsFragment.kt`:
   ```kotlin
   class WelcomePermissionsFragment : Fragment() {
       
       private val permissionLauncher = registerForActivityResult(
           ActivityResultContracts.RequestMultiplePermissions()
       ) { permissions ->
           val allGranted = permissions.all { it.value }
           if (allGranted) {
               // Разрешения предоставлены, переход к следующему экрану
               moveToNextPage()
           } else {
               // Показать предупреждение
               showPermissionDeniedDialog()
           }
       }
       
       override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
           super.onViewCreated(view, savedInstanceState)
           
           binding.grantPermissionsButton.setOnClickListener {
               requestMediaPermissions()
           }
       }
       
       private fun requestMediaPermissions() {
           val permissions = getRequiredMediaPermissions()
           permissionLauncher.launch(permissions)
       }
       
       private fun showPermissionDeniedDialog() {
           AlertDialog.Builder(requireContext())
               .setTitle(R.string.permissions_denied_title)
               .setMessage(R.string.permissions_denied_warning)
               .setPositiveButton(R.string.retry) { _, _ ->
                   requestMediaPermissions()
               }
               .setNegativeButton(R.string.continue_anyway) { _, _ ->
                   moveToNextPage()
               }
               .show()
       }
   }
   ```

### Шаг 5: Интегрировать в Welcome Activity
1. Добавить новую страницу в ViewPager адаптер
2. Разместить её после страницы с описанием функциональности
3. Убедиться, что запрос происходит до завершения Welcome Flow

### Шаг 6: Сохранить состояние предоставления разрешений
1. После успешного предоставления разрешений сохранить это в SharedPreferences:
   ```kotlin
   private fun savePermissionsGranted() {
       context?.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
           ?.edit()
           ?.putBoolean("media_permissions_granted", true)
           ?.apply()
   }
   ```

### Шаг 7: Тестирование
1. Удалить приложение полностью с устройства
2. Установить и запустить заново
3. Проверить, что запрос разрешений появляется во время Welcome Flow
4. Протестировать сценарии:
   - Предоставление всех разрешений
   - Отказ от разрешений
   - Частичное предоставление (если применимо)
5. Проверить на разных версиях Android (особенно API 29, 30, 33+)

## Критерии приемки
- ✅ Запрос разрешений появляется во время Welcome Activity
- ✅ Запрос корректно работает на Android 10, 11, 12, 13+
- ✅ При отказе показывается предупреждение о последствиях
- ✅ Есть возможность повторно запросить разрешения
- ✅ После предоставления разрешений Welcome Flow продолжается нормально

## Зависимости
- Текущая реализация Welcome Activity
- Система управления разрешениями в приложении

## Связанные задачи
- Спецификация #4: Улучшение запроса разрешений

## Примечания
- Учесть поведение на устройствах с разными версиями Android
- Рассмотреть добавление ссылки на настройки приложения, если пользователь выбрал "Never ask again"
