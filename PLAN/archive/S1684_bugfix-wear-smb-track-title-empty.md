# Спецификация (compact bugfix): S1684 - Название трека не подтягивается при воспроизведении по SMB на часах

**Ticket:** S1684
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-15
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-15

**Текст:**

у меня там играл трек через SMB - не подтянулось название вообще

**Захвачено во время:** S1683 (спека на управление плеером часов)

---

## 1. Проблема / симптом

Owner report, 2026-08-15: playing a track from an SMB source on the watch, the player showed no track name at all.

Split out of S1683 deliberately. S1683 is about controls that exist but are not visible, and about paging; this is a metadata defect on a different axis - the name is a value the screen already knows how to draw and simply did not receive. Fixing one does not fix the other.

What is known, and what is not:

- **Not reproduced yet.** The device session that produced S1683 played a **local** file (`/sdcard/Music/Samsung/Over_the_Horizon.mp3`), and there the name rendered correctly - the screenshot in S1683's attachment shows `Over_the_Horizon.mp3` on screen. So the failure is specific to the network path, not to the player screen as such.
- The audio player draws `uiState.mediaFile?.name` with the literal fallback `"Unknown"`. The owner reports no name **at all** rather than the word "Unknown", which is worth checking first: if the screen really showed nothing, the file object exists and carries an empty name; if it showed "Unknown", there is no file object at that moment. Those are two different defects and the fix differs.
- The local path gets its name from MediaStore's `_display_name`. The SMB path has no MediaStore row, so whatever populates the name for network files is a separate code path, and that is where to look.

Diagnostics for the reproduction: configure one SMB source on the paired phone, push it to the watch (delivery now works after S1681), open a track from it on the watch, and capture the watch log around the browse and player view models plus a screenshot of the player.

---

## 2. Корневая причина

Установлено по коду 2026-08-15. Из двух версий, которые §1 назвал равновероятными, верна вторая: объекта файла в состоянии экрана нет вовсе.

`AudioPlayerViewModel.loadMediaFile` разветвляется на локальный и сетевой пути. Локальная ветка получает файл из репозитория и кладёт его в состояние. Сетевая ветка берёт из `SelectedMediaManager` тот же объект файла, но передаёт из него дальше только имя - как параметр загрузчика, где оно используется исключительно для имени временного файла в кэше. В состояние экрана на сетевом пути не попадает ничего.

Экран же рисует `uiState.mediaFile?.name ?: "Unknown"`. Значит владелец видел слово `Unknown`, а не пустоту - и прочитал его как «название не подтянулось вообще», что по сути верно.

Дефект не в SMB и не в сетевом коде. Он в ветвлении плеера, поэтому воспроизводится на любом сетевом источнике, а не только на SMB.

Два следствия, найденные тем же разбором и расширяющие объём:

- **Видеоплеер содержит ровно тот же пропуск** в своей `loadMediaFile`. Починка только аудио оставила бы половину дефекта.
- **Пустое имя уезжает на телефон.** `publishPlaybackState` в видеоплеере отправляет `mediaFile?.name ?: ""`, то есть карточка удалённого управления на телефоне для сетевого файла тоже была пустой. Это уже не экран часов, и в §1 этого не было.

Показательно, что KDoc `SelectedMediaManager` описывает его назначение прямо: переносить данные файла между экраном списка и плеером **именно для сетевых источников, о которых MediaStore ответить не может**. Механизм был построен для этого случая и в этом случае не использовался.

---

## 3. Исправление

Сетевая ветка публикует объект файла в состояние экрана перед началом загрузки - тем же одним действием, что и соседняя локальная ветка. Правка внесена в оба плеера:

- `wear/src/main/java/com/sza/fastmediasorter/wear/ui/player/audio/AudioPlayerViewModel.kt`
- `wear/src/main/java/com/sza/fastmediasorter/wear/ui/player/video/VideoPlayerViewModel.kt`

Публикация идёт до скачивания, а не после, чтобы название было видно во время загрузки - на часах по сети это заметное время.

Отдельно не чинится и вынесено за объём: состояние «избранное» на сетевом пути тоже не запрашивается (локальная ветка вызывает проверку, сетевая нет), но для этого нужен идентификатор источника, которого в сетевой ветке нет под рукой. Это отдельная задача, не однострочная.

Проверка компиляцией: `:wear:compileDebugKotlin` - `BUILD SUCCESSFUL in 8s`, exit 0.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1683 (управление плеером и листание на часах - соседний тикет по тому же экрану, другая причина), S1681 (доставка телефон-часы; починена, без неё SMB-источник на часах вообще не появлялся), S1556 (wear-browse-ignores-source-basepath, Draft - тоже про сетевые источники на часах, проверить на пересечение до начала работы).

---

## 4. Проверка

Сценарий на устройстве, нужен достижимый сетевой источник с аудиофайлом:

1. Открыть источник на часах, выбрать трек, дождаться начала воспроизведения.
2. Ожидается: на экране плеера видно имя файла. Не ожидается: слово `Unknown`.
3. В логе часов при этом появляется `S1684: network audio title published: <имя>`.
4. Повторить с видеофайлом - ожидается `S1684: network video title published: <имя>`, и на телефоне в карточке удалённого управления видно то же имя, а не пустое место.

Локальный файл этот дефект не воспроизводит и доказательством не является: локальная ветка была исправной с самого начала.

## 5. Результат проверки на устройстве (2026-08-15)

Владелец в тот же день открыл доступ к SMB-шаре `mark common` (192.168.1.100), и сценарий пройден целиком на его Galaxy Watch 7.

- Аудио: трек `04. Влади - Из Москвы (Рай На Острове).mp3` играет с шары, на экране плеера видно имя файла, слова `Unknown` нет. В логе часов - `S1684: network audio title published: 04. Влади - Из Москвы (Рай На Острове).mp3`. Снимок: `temp/S1684/audio-smb-title-visible.png`.
- Видео: `1775996896467233.mp4` воспроизводится, в логе - `S1684: network video title published: 1775996896467233.mp4`. Снимок: `temp/S1684/video-smb-playing.png`.
- Отладочные метки удалены сразу после проверки, компиляция модуля часов после удаления - `BUILD SUCCESSFUL`, exit 0.

Побочно подтвердилось на той же шаре: кириллица в именах файлов по SMB читается верно. Значит кракозябра, заведённая как S1688, относится именно к разбору листинга FTP, а не к сетевому коду вообще.

Первоначальная причина задержки (ниже) снята: на стенде утром 2026-08-15 достижимого SMB-хоста не было, из пяти настроенных источников отвечал только FTP, а через FTP проверка не проходила из-за независимого дефекта S1687.
