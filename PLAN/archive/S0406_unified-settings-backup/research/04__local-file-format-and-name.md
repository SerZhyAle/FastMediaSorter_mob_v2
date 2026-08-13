# Research 04 - Формат и имя локального файла

**Strategic item:** §6.4
**Status:** Resolved

## Что выяснено (из кода)

- Текущий локальный экспорт пишет XML `FastMediaSorter_export.xml` в Downloads (MediaStore на Android 10+, прямой доступ ниже). Импорт ищет по `LIKE 'FastMediaSorter_export%.xml'`, берёт самый свежий, либо принимает явный SAF URI.
- Облачный путь пишет `backup_YYMMDD-HHmm.json`.
- Парсер импорта читает корневой тег `FastMediaSorterBackup` и атрибут `version`.

## Решение

- Новый локальный файл: JSON `FastMediaSorter_backup.json` (структура - единый `BackupPayload`).
- Импорт авто-детектит формат по содержимому: первый непробельный символ `{` → JSON (единый applier); `<` или тег `FastMediaSorterBackup` → legacy XML (старый парсер, back-compat).
- Поиск файла при авто-импорте: сначала `FastMediaSorter_backup%.json`, при отсутствии - legacy `FastMediaSorter_export%.xml`. Явный SAF URI имеет приоритет и детектится по содержимому.
- Имя стабильно (без таймстампа) для предсказуемого авто-поиска; MediaStore-перезапись по `DISPLAY_NAME` уже реализована.

## Влияние на план

- Export пишет JSON под новым именем.
- Import: ветка детекта формата + сохранённый legacy-XML-парсер.
- Старые `.xml` файлы по-прежнему импортируются (критерий §11.6).
