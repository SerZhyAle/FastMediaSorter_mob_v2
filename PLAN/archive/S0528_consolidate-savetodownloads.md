# Стратегическая спецификация: S0528 - Консолидация записи в Downloads

**Ticket:** S0528
**Status:** Archived
**Priority:** 45
**Date:** 2026-06-19
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-06-19
**Tactical plan:** `PLAN/S0528_consolidate-savetodownloads/INDEX.md`

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 0. Захваченный материал (inbox)

> Сырой захват идеи на лету. Распределяется по §1/§3.1/§6 при доработке через `/spec` или `/spec-update`.

**Захвачено:** 2026-06-19

**Захвачено во время:** S0522 (research)

**Текст:**

Логика записи в Downloads через MediaStore почти дословно продублирована в нескольких местах с расходящейся семантикой перезаписи (renameTo против MediaStore IS_PENDING). Уже существует общий слой записи (MediaStoreLocalDestinationWriter), но не все потоки на него переведены. Свести все записи в Downloads на единый слой, устранив дублирование и расхождения. В S0522 явно вынесено в отдельный тикет (non-goal).

Evidence: ui/player/helpers/SaveVideoFrameManager.kt:227 (saveToDownloads) и data/link/LinkDownloadWriter.kt:147 (saveToDownloads) - почти идентичные реализации; общий слой data/transfer/local/MediaStoreLocalDestinationWriter.kt не используется обоими.

**Вложения:**

Вложений нет.

---

## 1. Проблема

Логика сохранения файла в системную папку Downloads почти дословно скопирована в двух потоках: захват кадра видео в плеере и загрузка файла по ссылке. Оба потока несут собственную копию записи через MediaStore вместо общего слоя локальной записи, который уже существует и используется соседними потоками (fallback из S0522, сохранение GIF-кадра из S0527).

Копии разошлись по семантике коллизии имён на устаревшем пути (API 26-28): захват кадра перезаписывает существующий файл, а загрузка по ссылке создаёт файл с уникальным именем. На Android Q+ обе копии перезаписывают. То есть один и тот же поток ведёт себя по-разному в зависимости от уровня API, а два потока - по-разному между собой. Дублирование грозит дальнейшим расхождением и двойной поддержкой.

---

## 2. Цели

1. Оба потока записи в Downloads (захват кадра видео, загрузка по ссылке) идут через единый общий слой локальной записи.
2. Поведение при коллизии имени унифицировано: существующий файл с тем же именем перезаписывается, одинаково на всех уровнях API.
3. Дублированные приватные реализации записи в Downloads удалены.
4. Сохранён fallback-контракт из S0522 (поведение запасного пути не меняется).

**Non-goals:**

- Путь сохранения GIF-кадра при смене скорости - принадлежит S0527.
- Изменение политики запасного пути, введённой S0522.
- Добавление режима уникального имени в общий слой (выбрана перезапись).
- Изменение формата имени файла, MIME, целевой папки.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Минимальная видимая разница для пользователя: на Q+ поведение остаётся прежним (перезапись), что покрывает подавляющее большинство устройств.

### 3.2 Жёсткие ограничения

- **Flavor:** потоки живут в видеоплеере и в загрузке по ссылке - затрагиваются варианты сборки, включающие эти функции; поведение должно остаться единым во всех таких флейворах.
- **API level:** должно работать на minSdk 26 (standard) и 23 (legacy); путь до-Q обслуживается файловым стоком общего слоя.
- **Wear OS:** не затрагивается.
- **Производительность:** без изменений - запись одного файла, тот же объём I/O.
- **Совместимость данных:** на Q+ расположение и семантика записи не меняются; на до-Q загрузка по ссылке при коллизии теперь перезаписывает (раньше создавала уникальное имя) - принято владельцем.
- **Локализация:** внутренний рефактор, новых строк не ожидается; при любом изменении видимой строки - EN/RU/UK обязательно.
- **Доступность:** не применимо - изменений UI нет.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0522 (ввёл общий fallback через слой локальной записи), S0527 (тот же слой MediaStore)
- **Семантика коллизии:** перезаписать существующий файл с тем же именем (overwrite), одинаково на всех уровнях API.
- **Изменение поведения данных:** на до-Q (API 26-28) загрузка по ссылке при коллизии переходит с уникального имени на перезапись - принято.
- **Очерёдность:** исполнять сразу, не дожидаясь верификации S0522/S0527 (риск переделки принят владельцем).

---

## 4. Контекст текущей архитектуры

За запись в локальные публичные коллекции (включая Downloads) отвечает общий слой локальной записи: на Q+ - через MediaStore с флагом IS_PENDING и режимом перезаписи/отказа по флагу overwrite; на до-Q - через файловый сток. Соседние потоки (запасной путь из S0522, сохранение GIF-кадра из S0527) уже используют этот слой.

Два потока - захват кадра видео и загрузка по ссылке - появились раньше адаптации общего слоя и несут собственные копии записи. Именно поэтому проблему из §1 нельзя закрыть локальной правкой: нужно перевести оба потока на общий слой и убрать копии.

---

## 5. Предлагаемый подход

Перевести оба потока на общий слой локальной записи, запрашивая режим перезаписи, и удалить приватные копии записи в Downloads. Общий слой уже умеет перезапись на всех уровнях API, поэтому расширять его не требуется.

### 5.1 Основные столпы / модули

- Общий слой локальной записи - единственная точка записи в Downloads.
- Поток захвата кадра видео - потребитель общего слоя.
- Поток загрузки по ссылке - потребитель общего слоя.

### 5.2 Потоки данных и событий

- Поток (кадр / загрузка) → формирование имени и MIME → общий слой записи (перезапись) → публикация результата.

### 5.3 Точки расширяемости

- Режим коллизии остаётся параметром общего слоя - при будущей потребности (например, уникальное имя для загрузок) расширяется централизованно, без возврата копий в потоки.

---

## 6. Открытые вопросы / Research items

Открытых вопросов нет - семантика коллизии (перезапись) и очерёдность (исполнять сразу) решены владельцем на этапе quiz.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Изменение поведения на до-Q: загрузка по ссылке теперь перезаписывает | Низкая | На старых устройствах повторная загрузка одноимённого файла затрёт прежний | Принято владельцем; редкий путь (API 26-28), на Q+ поведение и так было перезаписью |
| Переделка из-за параллельных правок S0522/S0527 тех же путей | Средняя | Возможна доработка после device-тестов S0522/S0527 | Владелец принял риск; сохранить fallback-контракт S0522 как инвариант |
| Регресс запасного пути из S0522 при переводе на общий слой | Низкая | Падение сохранения в Downloads | Сохранить поведение fallback как критерий готовности |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES. Внутренний рефактор; на Q+ видимое поведение идентично.

---

## 9. Архитектурные решения (ADR)

ADR нет - решение по устоявшимся паттернам проекта (единый слой локальной записи).

---

## 10. Связи с другими спеками

S0522 (общий fallback-путь), S0527 (запись GIF-кадра через тот же слой). Рефакторинг должен сохранить поведение fallback из S0522.

---

## 11. Критерии готовности (strategic-level)

1. Захват кадра видео сохраняет файл в Downloads через общий слой записи.
2. Загрузка по ссылке сохраняет файл в Downloads через общий слой записи.
3. В коде не остаётся отдельной приватной реализации записи в Downloads в этих двух потоках.
4. При коллизии имени файл перезаписывается - одинаково на Q+ и на до-Q.
5. Поведение запасного пути из S0522 не изменилось.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0528` - создаст `PLAN/S0528_consolidate-savetodownloads/` с фазами.

---

### Quiz decisions (2026-06-19)

- Семантика коллизии имени в Downloads → Перезаписать (overwrite=true): совпадает с родным режимом общего слоя и с текущим Q+ поведением обоих потоков; не требует расширения слоя.
- Очерёдность относительно S0522/S0527 (оба BlockNeedUserTest) → Исполнять сразу: владелец принял риск переделки, не дожидаясь их верификации.

---

## Last Audit

### Manual device test - 2026-06-19 (device: emulator-5556, standard debug, Android API 33 / sdk_gphone64_x86_64, binary v2.60.6191.257-DEBUG)

Verdict: PASS (5/5 sub-checks)

Evidence directory: `temp/S0528_devtest/` (s0528_s0522_log.txt, link_download_evidence.txt, final_downloads_jpg.txt, device_meta.txt, settings_pre.pb)

Pre-state: `video_snapshot_resource_id` unset and `link_auto_download` resource id unset (both -> Downloads), `video_snapshot_format=JPG`, `video_frame_copy_to_clipboard=on`.

1. Frame save with NO destination resource configured -> Downloads — PASS
   - expected: frame file written to /sdcard/Download via shared writer, no S0522 notice (NoResourceConfigured is silent)
   - actual: `S0528: video-frame save to Downloads via shared writer, file=video_sample_00h00m39s.jpg` then `frame saved to Downloads`; file present in /sdcard/Download; toast "Frame saved to Downloads"

2. Link download with no resource configured -> Downloads — PASS
   - expected: linked file written to Downloads via shared writer when no destination set
   - actual: ACTION_SEND of `https://httpbin.org/image/jpeg` -> `S0528: link download save to Downloads via shared writer, file=jpeg.jpg mime=image/jpeg`, `LinkDownloadWriter: saved 'jpeg.jpg' to Downloads`, `[S0166] real media saved via fallback ... reason=NoResourceConfigured`, `done result=FellBackToDownloads`; jpeg.jpg (35588 B) present in /sdcard/Download
   - note: app content filters reject under-sized / wrong-MIME fixtures (too-small, MimeBlocked) before the writer; a real-sized image/jpeg payload (httpbin) was required to exercise the write path

3. Same-name save is overwritten, not duplicated — PASS
   - expected: repeating a save with an existing name replaces the file; no "(1)" suffix
   - actual (frame): two saves of the 00:39 frame keep exactly one `video_sample_00h00m39s.jpg`; mtime advanced 17:37 -> 20:00 -> 20:01 -> 20:09; no dup-suffix file. MediaStore overwrite confirmed: `MediaStoreLocalDestinationWriter: deleted existing record ... rows=1` then `MediaStoreSink.commit: published ...`
   - actual (link): repeating the same URL keeps exactly one `jpeg.jpg`; `deleted existing record uri=...218` + `published ...219`; no `jpeg(1).jpg`

4. S0528 probe tags fire in logcat — PASS
   - expected: both probe tags present
   - actual: `SaveVideoFrameManager$saveToDownloads: S0528: video-frame save to Downloads via shared writer` and `LinkDownloadWriter: S0528: link download save to Downloads via shared writer` both observed

5. S0522 fallback when a CONFIGURED network resource is unreachable — PASS
   - expected: configured but unreachable network destination -> S0522 fallback notice + local Downloads fallback (S0522 contract preserved)
   - actual: snapshot destination set to SMB resource `S0483_BogusSMB` (smb://10.255.255.1/Common, unreachable). Save Frame: `SmbConnectionManager: Server unreachable (10.255.255.1:445)`, `SaveVideoFrameManager: copy failed for 'S0483_BogusSMB' ... Server is not responding`, then fallback `S0528: video-frame save to Downloads via shared writer` + `frame saved to Downloads`; toast "Frame saved to Downloads" shown. Fallback path took the `ResourceWriteFailed` branch (write attempted then failed), which sets the S0522 notifier reason; the S0522 toast (`save_fallback_resource_unavailable` = "Saved to %1$s - %2$s is unavailable") is emitted just before the saved-to-Downloads toast and was visually overlapped (Toasts are not logged) - notice emission is guaranteed by the non-NoResourceConfigured reason in code, fallback-to-Downloads behaviour confirmed visually and in log

Notes: status not flipped, no git changes, no debug tags removed. Test artifact `/sdcard/video_sample.mp4` (copied into storage root to expose the test video in "All Files") was removed after the run.

---

## Revision History

- **2026-06-19** - by `/spec-test-device` (device: emulator-5554, Android emulator)
  - Scenario: temp/S0528_mobile_test_scenario_20260619_1102.md · PASS/FAIL/INCONCLUSIVE 2/0/4 · Errors in log: 0
  - Build+install of the S0528 binary (v2.60.6191.101) PASSed; app launches stably, no crash/regression. Behavioural acceptance (both Downloads-save flows via shared writer, same-name overwrite, S0522 fallback) not automatable on the emulator (MediaStore not indexed, no link/fallback fixtures, in-player menu driving). Remains a manual on-device check. Status kept BlockNeedUserTest.
