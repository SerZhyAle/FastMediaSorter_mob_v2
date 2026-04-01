# Детализированное техзадание: Назначение ресурса получателем (ИСПРАВЛЕНО)

> **ИСПРАВЛЕНИЕ (после code-review):** Оригинальная постановка описывала несуществующую архитектуру (`ReceiversTable`, `ReceiverEntity`, `ReceiverDao`, `ReceiversRepository`). В реальном проекте «получатели» — это флаги `isDestination: Boolean` и `destinationOrder: Int?` на модели `MediaResource`. Отдельной таблицы получателей нет. Всё ниже исправлено под реальный код.

## 1. Цель задачи
Реализовать механизм быстрого добавления текущего ресурса в список «Quick Sort» (получателей/Destinations) прямо с экрана Browse. Один клик в PopupMenu превращает просматриваемое хранилище в цель для Copy/Move.

## 2. Местоположение в UI
- **Экран**: `Browse` (список файлов) — `BrowseActivity`.
- **Точка входа**: PopupMenu `Resource Operations` (кнопка `btnResourceOps`) → пункт `В получатели`.
- **Управляется**: `ResourceOpsMenuManager.showMenu()` (НЕ `onCreateOptionsMenu`, т.к. это `PopupMenu`, не `OptionsMenu`).
- **Логика видимости** (устанавливается в `showMenu()` через `isVisible`):
  - Пункт видим, если **все** условия верны:
    1. `resource != null`
    2. `resource.isReadOnly == false`
    3. `resource.isDestination == false`

## 3. Архитектурные требования
- **Архитектура**: MVVM + Clean Architecture (данные хранятся в флагах `MediaResource`, не в отдельной таблице).
- **Изменение ресурса**: через `UpdateResourceUseCase(resource.copy(isDestination = true, destinationOrder = ..., destinationColor = ...))`
- **Порядок слота**: получается через `GetDestinationsUseCase.getNextAvailableOrder()` (возвращает `–1` если лимит достигнут).
- **Цвет**: присваивается через `DestinationColors.getColorForDestination(order)`.
- **Лимит**: определяется настройкой `settings.maxRecipients` (Settings, не хардкод).
- **DI**: Hilt. **Async**: `Dispatchers.IO`. **Logging**: `Timber`.

## 4. Domain Layer — новый UseCase

**Файл**: `domain/usecase/AddResourceAsDestinationUseCase.kt`

```kotlin
class AddResourceAsDestinationUseCase @Inject constructor(
    private val getDestinationsUseCase: GetDestinationsUseCase,
    private val updateResourceUseCase: UpdateResourceUseCase
) {
    suspend operator fun invoke(resource: MediaResource): Result<Unit> {
        if (resource.isReadOnly) return Result.failure(IllegalStateException("Resource is read-only"))
        if (resource.isDestination) return Result.failure(IllegalStateException("Already a destination"))
        val order = getDestinationsUseCase.getNextAvailableOrder()
        if (order < 0) return Result.failure(IllegalStateException("Destinations list is full"))
        val color = DestinationColors.getColorForDestination(order)
        return updateResourceUseCase(resource.copy(isDestination = true, destinationOrder = order, destinationColor = color))
    }
}
```

## 5. Presentation Layer

### BrowseEvent (в BrowseViewModel.kt)
Добавить:
```kotlin
data class ResourceAddedAsDestination(val resourceId: Long) : BrowseEvent()
```

### BrowseViewModel
- Инжектировать: `addResourceAsDestinationUseCase: AddResourceAsDestinationUseCase`
- Добавить метод:
```kotlin
fun addCurrentResourceAsDestination() {
    val resource = state.value.resource ?: return
    viewModelScope.launch {
        withContext(ioDispatcher) {
            addResourceAsDestinationUseCase(resource)
        }.onSuccess {
            sendEvent(BrowseEvent.ResourceAddedAsDestination(resource.id))
        }.onFailure { e ->
            sendEvent(BrowseEvent.ShowError(e.message ?: "Failed to add destination"))
        }
    }
}
```

### ResourceOpsMenuManager.showMenu()
- Добавить параметр `onAddToDestinations: (() -> Unit)? = null`
- Установить видимость пункта на основе `resource != null && !resource.isReadOnly && !resource.isDestination`
- Обработать клик: вызвать `onAddToDestinations?.invoke()`

### BrowseActivity
- Передать в `showMenu()`: `onAddToDestinations = { viewModel.addCurrentResourceAsDestination() }`
- Обработать событие `BrowseEvent.ResourceAddedAsDestination`:
  - Показать `Snackbar` с текстом «Ресурс добавлен в получатели» и кнопкой «Настроить»
  - Кнопка действия открывает `SettingsActivity` с `EXTRA_INITIAL_TAB = 3`

### SettingsActivity
- Добавить в `companion object`: `const val EXTRA_INITIAL_TAB = "extra_initial_tab"`
- В `setupViews()`: если `intent.getIntExtra(EXTRA_INITIAL_TAB, -1) in 0..3` — перейти на указанный таб

## 6. Пошаговый план имплементации (Checklist)

### Шаг 1: Domain
- [x] Создать `AddResourceAsDestinationUseCase.kt`

### Шаг 2: ViewModel
- [x] Добавить `ResourceAddedAsDestination` в `BrowseEvent`
- [x] Инжектировать `addResourceAsDestinationUseCase` в `BrowseViewModel`
- [x] Добавить `addCurrentResourceAsDestination()` в `BrowseViewModel`

### Шаг 3: UI — Menu
- [x] Добавить `action_add_to_receivers` в `menu_resource_ops.xml`
- [x] Обновить `ResourceOpsMenuManager.showMenu()` — видимость + обработчик клика
- [x] Передать `onAddToDestinations` из `BrowseActivity`
- [x] Обработать `BrowseEvent.ResourceAddedAsDestination` → Snackbar

### Шаг 4: Settings
- [x] Добавить `EXTRA_INITIAL_TAB` в `SettingsActivity.companion`
- [x] Обработать `EXTRA_INITIAL_TAB` в `setupViews()`

### Шаг 5: Локализация
- [x] `menu_add_receiver` / `msg_added_as_receiver` / `btn_edit_receiver` в strings.xml (+RU, +UK)

### Шаг 6: Changelog
- [x] Выполнить `add_to_dev_log.ps1` после всех изменений

## 7. Риски
- **Лимит**: `maxRecipients` берётся из Settings, не из `AddResourceUseCase.MAX_DESTINATIONS`. Используем `GetDestinationsUseCase.getNextAvailableOrder()` — он учитывает лимит корректно.
- **Консистентность**: удаление ресурса автоматически убирает его из Destinations, т.к. флаги хранятся на самом `MediaResource`.
