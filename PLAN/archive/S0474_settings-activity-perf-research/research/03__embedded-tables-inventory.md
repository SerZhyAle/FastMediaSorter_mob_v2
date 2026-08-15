# 03 - Каталог встроенных «таблиц» (кандидаты на вынос)

**Ticket:** S0474
**§6 item:** 3
**Метод:** поиск RecyclerView/динамических контейнеров в layouts настроек + чтение адаптеров.

Пользователь упомянул «встроенные таблицы». Найденные встроенные списочные/табличные структуры прямо на страницах настроек:

## T1. Destinations (список назначений)

- Где: `rvDestinations`, `OperationsSettingsFragment`, `fragment_settings_destinations.xml`.
- Что: `RecyclerView` + `DestinationsAdapter` (inner-class). До 10 записей: папка-назначение, цветовой индикатор, кнопки вверх/вниз/удалить.
- Данные: `viewModel.destinations` (StateFlow из Room).
- Адаптив: ландшафт - `GridLayoutManager(destinations_column_count)`, портрет - 1 колонка.
- Замечание: Destinations - центральная концепция приложения; discoverability критична. Вынос за кнопку - спорное UX-решение (см. §6 open).

## T2. Scheduled Operations (запланированные операции)

- Где: `rvScheduledOps`, `OperationsSettingsFragment`.
- Что: `RecyclerView` + `ScheduledOperationsAdapter` (98 LOC). Источник → назначение + расписание.
- Данные: `ScheduledOperationsViewModel` (`activityViewModels()`).
- Гейт: скрыт при `ENABLE_SCHEDULED_OPERATIONS == false` (lite/photos) или выключенном мастер-тоггле. Включён только в noLegal и vr.

## T3. Send Commands (динамическая группа)

- Где: `containerSendCommands`, `PlaybackSettingsFragment.setupSendCommandsGroup()` (~стр. 272-322).
- Что: программно наполняемый `LinearLayout` из `SettingsToggleRow` по каждому зарегистрированному `ShareTarget` (Telegram, WhatsApp, Instagram, Google Lens, Print, ..). Кол-во строк runtime-зависимо.
- Данные: `ShareTargetRegistry`; см. P3 (PackageManager на main thread).

## Уже вынесенные (прецеденты)

- Permissions Management → `PermissionsManagementFragment` (полноэкранная замена content).
- Auth Sessions → `AuthSessionsActivity` (отдельная Activity).
- Downloadable Extensions → `ExtensionsManagerFragment` (полноэкранный оверлей).

## Вывод

Кандидаты на вынос в отдельный экран/диалог: T1 (Destinations), T2 (Scheduled). Оба живут в переросшем `OperationsSettingsFragment` (1165 LOC) и тянут inflate item-layouts + наблюдение Flow при открытии Operations-таба. Вынос разгружает таб и сокращает фрагмент, но для T1 требует подтверждения UX (прятать центральную концепцию за кнопку). T3 - не вынос, а перенос инициализации в фон.
