# 02 - Узкие места работоспособности при открытии

**Ticket:** S0474
**§6 item:** 2
**Метод:** чтение `onCreate`/`onViewCreated`/`setupViews` затронутых классов.

Подтверждённые источники синхронной работы на main thread при открытии экрана настроек.

## P1. `OperationsSettingsFragment` - 1165 LOC

`ui/settings/fragments/OperationsSettingsFragment.kt`. Содержит inner-class `DestinationsAdapter`, wire-up `ScheduledOperationsAdapter`, 8 разделов, 3 `registerForActivityResult`. Близко к лимиту 1500 LOC (CLAUDE.md §2) - любое новое требование нарушит правило.

## P2. Синхронное чтение SharedPreferences без StrictMode-обёртки

`OperationsSettingsFragment.setupExpandableSections()` (~стр. 712): `requireContext().getSharedPreferences(PREFS_NAME, MODE_PRIVATE)` из `onViewCreated`, без `StrictModeHelper.allowDiskReads`. Для сравнения - `PlaybackSettingsFragment` и `MediaSettingsFragment` оборачивают аналогичные чтения корректно.

## P3. PackageManager на main thread при инициализации Playback-таба

`PlaybackSettingsFragment.resolveShareTargetLabel()` (~стр. 335-341) вызывает `pm.getApplicationInfoCompat(pkg)` в цикле по каждому зарегистрированному `ShareTarget` из `setupSendCommandsGroup()` → `onViewCreated`. N таргетов = N синхронных Binder-вызовов.

## P4. `offscreenPageLimit = 1` создаёт 2 фрагмента при старте

`SettingsActivity.setupViews()` (~стр. 195). Явно выставлен `1` вместо дефолта - при открытии немедленно создаётся активный таб + соседний. Если соседний - Media, срабатывает P5.

## P5. `MediaSettingsFragment` - синхронный `commitNow()` на 5-6 child-фрагментов

`attachChildFragments()` (~стр. 94-138): 5-6 `transaction.replace(...)` + `commitNow()`. Синхронно выполняет `onCreateView`+`onViewCreated` всех дочерних на UI thread; суммарный inflate ~800 строк XML (images 113 + video 159 + audio 191 + documents 84 + other 253) в одной транзакции.

## P6. XML-сканирование индекса поиска на main thread при каждом открытии

`LayoutSettingsSearchSource.collect()` парсит `resources.getXml(layoutResId)` по 9 layouts через `XmlPullParser`. Триггерится в `SettingsActivity.setupViews()` → `updateSearchResults(settingsSearchRegistry.entries)` (~стр. 479) - то есть при каждом открытии Settings, не только при открытии поиска.

## Совокупный эффект

При холодном открытии Settings на дефолтном табе возможна немедленная инициализация 7-8 фрагментов (P4+P5) + дисковые чтения (P2) + Binder-вызовы (P3) + XML-парсинг 9 layouts (P6) - всё на main thread до первого интерактивного кадра. На медленных/старых устройствах (legacy minSdk 23) это кандидат на видимый лаг или ANR.

## Замер

В `SettingsActivity` есть DEBUG-инструментовка `SystemClock.uptimeMillis()` (~стр. 174-248). Актуальных данных замеров на целевых устройствах не обнаружено - нужен baseline (см. §6 open).
