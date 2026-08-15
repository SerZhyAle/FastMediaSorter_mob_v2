# Спецификация (draft): S1663 - Инъекция MainActivity читает SharedPreferences с главного потока 258 мс

**Ticket:** S1663
**Status:** Archived
**Priority:** 50
**Date:** 2026-08-14
**Tier:** bugfix

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-14 (авто-захват по CLAUDE.md 3.1 при проверке S1650 на устройстве)

**Симптом:**

При холодном старте `MainActivity.onCreate` блокируется на 258 мс дисковым чтением: Hilt конструирует `BrowseFileTransferCoordinator`, а тот в конструкторе открывает `SharedPreferences`.

**Доказательство (verbatim из logcat, RFCR110NBQJ, SM-G996U1, Android 15, standard-debug `v2.60.8112.319`):**

```
08-14 20:55:18.056 19638 19638 D StrictMode: StrictMode policy violation; ~duration=258 ms: android.os.strictmode.DiskReadViolation
        at android.app.ContextImpl.getSharedPreferences(ContextImpl.java:628)
        at com.sza.fastmediasorter.ui.browse.transfer.BrowseFileTransferCoordinator.<init>(BrowseFileTransferCoordinator.kt:47)
        at ..DaggerFastMediaSorterApp_HiltComponents_SingletonC$SingletonCImpl$SwitchingProvider.get0(..:6384)
        at ..ActivityCImpl.injectMainActivity2(..:2767)
        at com.sza.fastmediasorter.ui.main.Hilt_MainActivity.inject(Hilt_MainActivity.java:84)
        at com.sza.fastmediasorter.core.ui.BaseActivity.onCreate(BaseActivity.kt:141)
        at com.sza.fastmediasorter.ui.main.MainActivity.onCreate(MainActivity.kt:279)
```

`pid 19638` и `tid 19638` совпадают - это главный поток. Это самое длительное нарушение во всём холодном старте: остальные восемь в том же захвате длятся 1-2 мс.

**Что выяснить:**

- Зачем `BrowseFileTransferCoordinator` открывает настройки в конструкторе и можно ли отложить открытие до первой передачи файла (`by lazy`, `dagger.Lazy`, либо перенос чтения в suspend-путь).
- Почему объект, принадлежащий экрану обзора, вообще строится при инъекции `MainActivity` - возможно, он тянется транзитивно и на главном экране не нужен.
- Отдельный вопрос той же природы: `ResourceLaunchWidgetProvider.onUpdate` в том же захвате даёт ещё два дисковых чтения на главном потоке, по 2 мс каждое.

**Границы:** не трогать прогрев Glide (S1650, закрыт) и путь открытия плеера (S1648) - это соседние тикеты того же класса, но другие места.

**Захвачено во время:** проверка S1650 на устройстве.

---

## 1. Корневая причина

`BrowseFileTransferCoordinator` - это `@Singleton`, который граф строит в момент инъекции `MainActivity`, то есть на главном потоке во время холодного старта. В конструкторе он открывал файл настроек, а первое обращение к `SharedPreferences` читает и разбирает файл синхронно.

Ключевая деталь: сам объект на старте не нужен. Все три места, где эти настройки читаются или пишутся, обслуживают учёт терминальных событий передачи файлов - «этот идентификатор работы уже обработан». До первой передачи файла такое событие возникнуть не может. То есть цена платилась всегда, а польза наступала только у тех, кто что-то передаёт.

## 2. Исправление

Поле переведено в `by lazy`: конструктор снова дёшев, а файл настроек открывается при первом обращении - оно к этому моменту заведомо не на пути запуска. Сессия, в которой пользователь ничего не передавал, теперь не платит вовсе.

Правка минимальна намеренно. `WorkManager.getInstance(context)` в соседней строке оставлен как был: в измеренном стеке его нет, а менять то, что не измерено, - способ получить второй дефект вместо одного исправленного.

## 3. Проверка

**Дата:** 2026-08-14 · **Вердикт:** Verified

Тем же способом, каким дефект был найден: холодный старт на `RFCR110NBQJ` после `force-stop` и очистки буфера, сборка standard-debug.

| | Нарушений StrictMode на главном потоке | Худшее | Кадров `BrowseFileTransferCoordinator` |
|---|---:|---:|---:|
| До (2026-08-14 20:55) | 9 | **258 мс** | есть, в самом длинном |
| После (2026-08-14 23:45) | 8 | **2 мс** | **0** |

То есть исчезло ровно одно нарушение - то самое, - и вместе с ним 258 из ~270 мс дисковой работы на главном потоке за весь холодный старт. Оставшиеся восемь длятся 0-2 мс и принадлежат другим местам (в том числе провайдеру виджета), к этому тикету отношения не имеют.

Компиляция `.\a.ps1 fk` - `Fast check passed`, exit 0. `assert-detekt -Gate` по изменённому файлу - PASS.
