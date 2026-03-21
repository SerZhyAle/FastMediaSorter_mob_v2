# Задача 4: Кнопка «Добавить виджет» в редактировании ресурса

**Статус:** Черновик
**Дата:** 2026-03-21
**Файлы:**
- `ui/addresource/AddResourceActivity.kt` (экран редактирования)
- `widget/ResourceLaunchWidgetProvider.kt` (виджет, который будет прикреплён)

---

## Суть

В экране редактирования ресурса добавить кнопку, которая программно прикрепляет виджет **ResourceLaunch** для данного ресурса прямо на главный экран Android. Без ручного долгого нажатия на домашнем экране.

---

## Размещение кнопки

Экран редактирования — `AddResourceActivity`. Там есть секция со статистикой ресурса. Рядом со статистикой (или под ней) добавить кнопку/ссылку:

```
[ Статистика: 342 файла, 1.2 ГБ ]
[ Добавить на главный экран  ↗ ]   ← новая кнопка
```

Стиль: текстовая кнопка или outlined button, не акцентная — это вспомогательное действие.

---

## Поведение

### Шаг 1: Проверка поддержки
```kotlin
if (!AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported) {
    // Launcher не поддерживает pinning (редко, но бывает)
    showToast("Ваш лаунчер не поддерживает добавление виджетов")
    return
}
```

### Шаг 2: Проверка заблокированного экрана
Android API (`requestPinAppWidget`) молча фейлится если экран заблокирован — пользователь ничего не увидит.
Перед вызовом проверить:
```kotlin
val km = context.getSystemService(KeyguardManager::class.java)
if (km.isKeyguardLocked) {
    showDialog("Разблокируйте экран и попробуйте снова")
    return
}
```

### Шаг 3: Проверка дубликата
Перебрать все установленные виджеты ResourceLaunch и проверить SharedPreferences:
```kotlin
val manager = AppWidgetManager.getInstance(context)
val provider = ComponentName(context, ResourceLaunchWidgetProvider::class.java)
val existingIds = manager.getAppWidgetIds(provider)
val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
val alreadyExists = existingIds.any { id ->
    prefs.getLong("resource_id_$id", -1L) == currentResourceId
}
if (alreadyExists) {
    showToast("Виджет для этого ресурса уже добавлен")
    return
}
```

### Шаг 4: Прикрепление виджета
```kotlin
val extras = Bundle().apply {
    putLong("resource_id", currentResource.id)
    putString("resource_name", currentResource.name)
    putString("resource_path", currentResource.path)
    putString("resource_type", currentResource.type.name)
}
val remoteViews = RemoteViews(context.packageName, R.layout.widget_resource_launch)
// предварительно настроить RemoteViews с данными ресурса

val successCallback = PendingIntent.getBroadcast(
    context, 0,
    Intent(ACTION_WIDGET_PINNED),
    PendingIntent.FLAG_IMMUTABLE
)

manager.requestPinAppWidget(
    ComponentName(context, ResourceLaunchWidgetProvider::class.java),
    extras,
    successCallback
)
```

**Проблема:** `requestPinAppWidget` стандартно открывает диалог конфигурации виджета (наш `ResourceLaunchWidgetConfigActivity`). Чтобы виджет уже был настроен (пропустить диалог выбора ресурса), нужно:
- Либо передать `Bundle` с данными и обработать его в `ResourceLaunchWidgetProvider.onUpdate` / колбэке
- Либо создать специальный `PinWidgetCallback BroadcastReceiver`, который после подтверждения пользователем сразу записывает конфигурацию в SharedPreferences и обновляет виджет

**Предпочтительный подход:** BroadcastReceiver-колбэк:
1. Пользователь нажимает "Добавить" в редакторе
2. Система показывает превью виджета + кнопку "Добавить" (стандартный UI Android)
3. Пользователь подтверждает — срабатывает `successCallback`
4. В колбэке записываем конфигурацию ресурса в SharedPreferences и вызываем `updateAppWidget`
5. Виджет на экране уже показывает правильный ресурс (без лишнего шага выбора)

---

## API и ограничения

- `AppWidgetManager.requestPinAppWidget()` — **API 26+** (minSdk уже 26, legacy = 23 — там недоступно!)
- Для `legacy` флейвора (`minSdk 23`) нужен guard: `if (Build.VERSION.SDK_INT >= 26)`
- Пользователь всё равно должен **подтвердить** добавление — система показывает свой диалог. Это требование Android, обойти нельзя.

---

## Открытые вопросы

- Для `legacy` (minSdk 23): показывать кнопку серой/скрытой, или вообще не показывать?
- Нужно ли добавить аналогичную кнопку в экран просмотра ресурса (не только редактирования)?
