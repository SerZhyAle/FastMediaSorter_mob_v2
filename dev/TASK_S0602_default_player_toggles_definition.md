# TASK DEFINITION (Постановка задачи): S0602 - default-player-toggles-nonfunctional-unsupported-flavors

**Билет:** S0602  
**Название:** default-player-toggles-nonfunctional-unsupported-flavors  
**Дата:** 2026-06-21  

---

## 1. Цель
Исправить проблему, при которой на сборках (flavors) без поддержки дефолтного плеера (`MediaCapabilities.supportsDefaultPlayer == false`, например, на flavor `lite`) переключатели `rowPrimaryMediaPlayer` и `rowAcceptSharedFiles` во вкладке Operations остаются видимыми/активными в поиске настроек или на самом экране настроек (хотя их слушатели изменений не регистрируются). 

Необходимо:
1. Скрыть `rowPrimaryMediaPlayer` и `rowAcceptSharedFiles` на сборках без поддержки дефолтного плеера.
2. Сбросить соответствующие настройки (`isPrimaryMediaPlayer` и `acceptSharedFiles`) в `false` в базе данных/сессиях на неподдерживаемых сборках.
3. Исключить эти переключатели и кнопки регистрации дефолтного плеера (`btnSettingsDefaultPlayerImages`, `btnSettingsDefaultPlayerAudio`, `btnSettingsDefaultPlayerVideo`, `btnSettingsDefaultPlayerDocs`) из результатов поиска настроек (`SettingsSearchRegistry`), если сборка или конкретные медиа-возможности их не поддерживают.

## 2. Область изменений (Scope)
1. **Экран настроек (`OperationsSettingsFragment.kt`):**
   - В методе `applyFlavorRestrictions()` добавить скрытие `rowPrimaryMediaPlayer` и `rowAcceptSharedFiles`, если `supportsDefaultPlayer` равен `false`.
   - Там же при отсутствии поддержки сбрасывать настройки `isPrimaryMediaPlayer` и `acceptSharedFiles` в `false` с сохранением в ViewModel (аналогично поведению OCR/перевода).
2. **Поиск по настройкам (`SettingsSearchRegistry.kt`):**
   - Внедрить `MediaCapabilities` через Hilt-конструктор.
   - Отфильтровать выдачу `entries`, скрывая ключи настроек дефолтного плеера в зависимости от `MediaCapabilities`.

## 3. Критерии приемки
- Созданы файлы описания задачи (`TASK_S0602..._definition.md`) и дизайна (`TASK_S0602..._design.md`).
- Создан и одобрен `implementation_plan.md` в артефактах.
- Изменения внесены в `OperationsSettingsFragment.kt` и `SettingsSearchRegistry.kt`.
- Все изменения сделаны без запуска сборки (NO BUILD).
- Изменения задокументированы в `dev/CHANGELOG.md` и progress log.
