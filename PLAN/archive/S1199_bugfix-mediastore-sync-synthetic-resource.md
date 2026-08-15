# Стратегическая спецификация: S1199 - Refresh в Избранном логирует ложную ошибку MediaStore-синхронизации

**Ticket:** S1199
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-26
**Tier:** 1 - Trivial (ad-hoc)
**Roadmap entry:** Ad-hoc - обнаружено при разборе лога `fastmediasorter_20260726_031343.log`

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-26

**Захвачено во время:** анализа лога устройства

**Текст:**

```
ERROR: BrowseRefreshManager.reload: MediaStore sync failed
java.lang.IllegalArgumentException: Invalid directory: Favorites
	at com.sza.fastmediasorter.domain.usecase.SyncMediaStoreUseCase$invoke$2.invokeSuspend(SyncMediaStoreUseCase.kt:62)
	at com.sza.fastmediasorter.ui.browse.managers.BrowseRefreshManager$launchReload$1.invokeSuspend(BrowseRefreshManager.kt:95)
```

---

## 1. Проблема

Pull-to-refresh в синтетическом ресурсе «Избранное» пишет в постоянный лог `E/App` со стек-трейсом, хотя ничего не сломано.

Цепочка:

- `BrowseResourceLoadManager` создаёт для Избранного `MediaResource(id = SyntheticResourceIds.FAVORITES, path = "Favorites", type = ResourceType.LOCAL)` - тип `LOCAL`, но путь является меткой, а не путём файловой системы.
- `BrowseRefreshManager.launchReload` вызывает `SyncMediaStoreUseCase` для всех ресурсов типа `LOCAL`.
- Оба guard'а use case'а промахиваются: проверка на `LOCAL` проходит, проверка на префикс `virtual://` не срабатывает для метки `"Favorites"`.
- `File("Favorites")` не существует - возвращается `Result.failure(IllegalArgumentException)`.

Тот же дефект по конструкции применим к `SyntheticResourceIds.STREAM`.

Последствия:

- Ложный `E/App` со стек-трейсом в каждом refresh в Избранном - шум в удалённой диагностике, маскирующий реальные ошибки.
- Лишние 500 мс окна `setIgnoringFileChanges` на пустой операции.
- Функционально список Избранного грузится корректно - краха нет.

## 2. Цель

Синтетические ресурсы не должны попадать в MediaStore-синхронизацию: пропуск с `Result.success(0)` и информационным логом вместо ошибки.

## 3. Решение

- В `SyncMediaStoreUseCase` заменить точечный guard по префиксу `virtual://` на общий признак «не путь файловой системы»: `resource.id < 0 || !resource.path.startsWith("/")`.
- Прежний `virtual://` кейс становится подмножеством нового условия; отдельная проверка больше не нужна.
- Guard стоит в use case, а не в вызывающем менеджере - защита действует для всех вызывающих, а не только для `BrowseRefreshManager`.
- Уровень лога `Timber.i` - пропуск это штатный путь, а не ошибка.

## 4. Затронутые файлы

- `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SyncMediaStoreUseCase.kt`
- `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/SyncMediaStoreUseCaseTest.kt`

## 5. Проверка

- Юнит-тесты `SyncMediaStoreUseCaseTest`: добавлены `synthetic favorites resource is skipped with zero count` и `non-absolute path is skipped with zero count`; существующий `missing directory yields failure` не должен сломаться (абсолютный путь, положительный id).
- Команда: `:app_v2:testStandardDebugUnitTest --tests "com.sza.fastmediasorter.domain.usecase.SyncMediaStoreUseCaseTest"` - ожидается `BUILD SUCCESSFUL`.
