# Стратегическая спецификация: S0169 — AudioMetadataLoader: излишние W-предупреждения для SMB аудио файлов

**Ticket:** S0169
**Status:** Verified
**Priority:** 15
**Date:** 2026-05-11
**Tier:** 1 — Trivial (logging classification)
**Roadmap entry:** Ad-hoc — лог `logs/fastmediasorter_20260511_220728.log`, строки 7743, 7985, 8163, 8744, 8819

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк.

---

## 1. Проблема

При воспроизведении аудио с SMB-ресурса `AudioMetadataLoader` пытается извлечь метаданные
через `Media3 MetadataRetriever`, читая первые 65536 байт файла. Для SMB-файлов это всегда
завершается с:

```
W  AudioMetadataLoader: Media3 MetadataRetriever failed on 65536 bytes:
   UnrecognizedInputFormatException
```

Воспроизведение при этом работает корректно — ExoPlayer читает ID3-теги самостоятельно
через потоковый доступ. `Media3 MetadataRetriever` — дополнительный, необязательный путь
получения метаданных, и его неудача на неполных данных является **ожидаемым поведением**
для сетевых источников.

В результате каждый SMB аудио файл порождает W-уровень лог, загрязняющий Logcat.

**Пример (5 файлов подряд, 22:11:16–22:11:27):**
- `01 - Pete Tong - Right Here, Right Now.mp3`
- `01-PUSHKING COMMUNITY-Moments Of Blue.mp3`
- `01. Miss Jane - It's A Fine Day (ATB Radio Edit).mp3`
- `02 - Pete Tong - Pjanoo.mp3`
- `02-DAVE MENIKETTI-Loan Me A Dime.mp3`

---

## 2. Цели

1. При недостижимости метаданных через `Media3 MetadataRetriever` на частичных сетевых
   заголовках (SMB / FTP / SFTP) ожидаемый `UnrecognizedInputFormatException` логируется
   на уровне **D** вместо **W**.
2. Текущий путь обогащения метаданных для сетевого аудио сохраняется — попытка
   `Media3 MetadataRetriever` не отключается.
3. Поведение метаданных и воспроизведения для пользователя не меняется.

**Non-goals:**
- Добавление поддержки `Media3 MetadataRetriever` для SMB/FTP (требует собственного
  `DataSource.Factory` для MetadataRetriever — несоразмерно сложно для задачи).
- Изменение способа отображения тегов / обложек.

---

## 3. Ограничения

- **Flavor:** standard, legacy — все с поддержкой аудио.
- **API level:** без специфики.
- **Wear OS:** не затрагивается.
- **Изменений UI-строк нет.**

---

## 4. Контекст текущей архитектуры

`AudioMetadataLoader` читает первые 65536 байт сетевого аудиофайла, пишет их во временный
файл и затем пытается разобрать этот partial header через `Media3 MetadataRetriever`.

Для текущей архитектуры это и есть основной путь обогащения метаданных для SMB / SFTP / FTP
в списке файлов и в диалоге подробной информации. Отдельного fallback-парсера тегов внутри
самого `AudioMetadataLoader` сейчас нет.

На partial header сетевого файла `MetadataRetriever` иногда закономерно завершается с
`UnrecognizedInputFormatException`: 65536 байт достаточно не для всех контейнеров и наборов
тегов. Это ожидаемое best-effort поведение, а не пользовательская ошибка.

---

## 5. Предлагаемый подход

### Реализованный вариант: W → D для ожидаемого partial-header failure

Сохраняем текущую попытку `Media3 MetadataRetriever`, но при поимке
`UnrecognizedInputFormatException` для SMB / FTP / SFTP partial header логируем на уровне D.

Неожиданные сбои, а также ошибки на local / content источниках, остаются на уровне W.
Это убирает шум в Logcat без отключения рабочего пути извлечения метаданных.

---

## 6. Риски

| Риск | Оценка |
|---|---|
| Будущие сетевые протоколы могут не попасть в downgrade-предикат | Low — текущий код покрывает все реально поддержанные partial-read протоколы |
| Для части ожидаемых partial-header сбоёв может использоваться другой exception type | Low — текущий лог показывает именно `UnrecognizedInputFormatException`; остальные типы останутся на W и будут заметны |

---

## 7. Открытые вопросы

1. Нужно ли со временем расширить downgrade-предикат на другие ожидаемые exception types,
   если они появятся в логах partial-read сценариев?
2. Если в будущем появится отдельный direct ID3/Vorbis parser внутри `AudioMetadataLoader`,
   можно будет вернуться к варианту полного skip для части network URI.

---

## Last Audit

**Date:** 2026-05-14
**Mode:** full
**Flags:** —
**Outcome:** Verified
**Counts:** PASS 3 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 0

§5 implemented in `AudioMetadataLoader.kt`: `UnrecognizedInputFormatException` is detected by `simpleName` match (line 290) and the corresponding catch path logs at D-level via `Timber.d("AudioMetadataLoader: Media3 MetadataRetriever expected miss on ${bytes.size} bytes: $failureName")` (line 530). The Media3 MetadataRetriever attempt is preserved; only the expected partial-header miss is downgraded from W to D. Unexpected failures remain at W. No `Timber.d("S0169:` tags (status leaving Implemented → Verified — grep confirmed zero).
