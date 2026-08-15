# Спецификация (compact bugfix): S1387 - `-AppOnly` глушит валидные строки приложения

**Ticket:** S1387
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-04
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-04

**Захвачено во время:** S1383

**Текст:**

search-log.ps1 -AppOnly silently drops valid app lines - false negative on a verification path.

Found 2026-08-04 while device-testing S1383 on emulator-5554. The run's captured logcat (temp/logcat_standard_20260804_140845.log, threadtime, 20665 structured lines) contains 9 `Timber.d("S1383: ..")` probe lines emitted by the app (pids 22637 / 23099 / 23535, tags DeviceProfileTileAdapter$TileViewHolder, ProfilesPageViewHolder, WelcomeActivity).

- `search-log.ps1 -Pattern "S1383:" -AppOnly` -> "No matches found."
- `search-log.ps1 -Pattern "S1383"` (same file, no -AppOnly) -> "=== 9 MATCHES ===", all app lines.

Suspected cause: the app-process filter resolves a single app PID (probably the first `Start proc` seen) and the app restarted three times during the run because the scenario ran `pm clear` twice. Any device test that clears app data therefore gets an empty probe result while the flow WAS exercised.

Why it matters beyond cosmetics: `/spec-test-device` step 8 uses exactly `-Pattern "<Sxxxx>:" -AppOnly` as its primary "code path exercised" signal, and CLAUDE.md forbids claiming a result without evidence. A false "not exercised" here pushes a correct implementation toward Partial/Broken, or silently understates coverage.

Not the same as S1353 (bugfix-search-log-logcat-parse, Verified 2026-08-02) - that one was about parsing the logcat format; this is the -AppOnly process filter on an already-parsed file.

---

## 1. Проблема / симптом

`search-log.ps1 -AppOnly` на захвате формата threadtime всегда возвращает «No matches found.», независимо от содержимого файла.

Эвиденс 2026-08-04: файл `temp/logcat_standard_20260804_140845.log` (threadtime, 20665 разобранных строк) содержит девять строк приложения с тегами `DeviceProfileTileAdapter$TileViewHolder`, `ProfilesPageViewHolder`, `WelcomeActivity`. Запрос `-Pattern "S1383:" -AppOnly` дал ноль совпадений; тот же запрос без `-AppOnly` дал девять.

Опасность не в самом фильтре, а в форме отказа: ноль совпадений неотличим от «поток не выполнялся». `/spec-test-device` использует ровно эту команду как основной признак «код отработал на устройстве», поэтому корректно реализованный тикет может быть переведён в Partial или Broken по несуществующей улике.

---

## 2. Корневая причина

Формат threadtime не содержит поля пакета - у строки есть только дата, PID, TID, уровень, тег и сообщение. Парсер это знает и осознанно оставляет `Pkg` пустым, а комментарий рядом объясняет решение: лучше «отказать закрыто», чем угадывать и молча пропускать системные строки.

Фильтр же реализован как `$_.Pkg -match "fastmediasorter"`. При пустом `Pkg` предикат ложен для каждой строки, поэтому «отказ закрыто» вырождается в тихий ноль без единого слова о том, что фильтр вообще неприменим.

Исходная гипотеза при захвате (фильтр берёт один PID и не переживает перезапуск приложения) не подтвердилась: PID тут вовсе не участвует.

---

## 3. Исправление

Отказ перестаёт быть тихим, а фильтр получает способ работать там, где поле пакета отсутствует.

### Step 1 - Различать «формат не умеет» и «совпадений нет»

**Файлы:** `scripts/utils/search-log.ps1`

**Prompt for developer:**

> Решение о ветке принимать по возможностям формата, а не по числу результатов: если среди разобранных строк есть хотя бы одна с непустым `Pkg`, оставить существующую фильтрацию по пакету, даже когда она даёт ноль. Иначе - восстановить PID процессов приложения из самого лога по строкам `Start proc <pid>:com.sza.fastmediasorter..` и отфильтровать по ним, сообщив об этом одной строкой. Если и PID восстановить не из чего - предупредить заметно и вернуть результаты без фильтрации, а не пустой список.

**Why:**

Ноль совпадений и «фильтр неприменим» - разные ответы, а сегодня они выглядят одинаково (§1), из-за чего верификация может принять исправный код за неработающий. Ветвление по числу результатов вместо возможностей формата дало бы вторую ошибку: на формате с полем пакета честный ноль превратился бы в вывод всего лога.

**Verification:**

- `search-log.ps1 -LogFile "temp/logcat_standard_20260804_140845.log" -Pattern "S1383:" -AppOnly` - девять совпадений, exit 0.
- `Grep` - `Start proc` присутствует в `scripts/utils/search-log.ps1`.

**Status:** `[x]` done

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1353 (bugfix-search-log-logcat-parse, Verified) - другой дефект того же скрипта, не дубликат. S1383 - тикет, на котором находка всплыла.

---

## 4. Проверка

- Регрессионный стенд - захват, на котором дефект и всплыл: `temp/logcat_standard_20260804_140845.log`, девять строк `S1383:`.
- До правки: `-Pattern "S1383:" -AppOnly` - ноль совпадений. После: девять, exit 0.
- Контроль отсутствия ложного расширения: тот же файл с заведомо отсутствующим шаблоном по-прежнему даёт ноль совпадений, а не весь лог.
- `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1` в составе `.\a.ps1 fg` - exit 0.

---

## Last Audit

**Date:** 2026-08-04
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 5 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 0

### Manual / on-device

- [x] На регрессионном захвате `-Pattern "S1383:" -AppOnly` даёт девять совпадений вместо нуля - expected 9 | actual 9, exit 0
- [x] Восстановлены PID всех трёх процессов приложения (22637, 23099, 23535), то есть перезапуск во время прогона фильтр переживает
- [x] Ложного расширения нет: заведомо отсутствующий шаблон по-прежнему даёт «No matches found.», а не весь лог
- [x] Ветвление принимается по возможностям формата, а не по числу результатов - `Grep` подтверждает предикат `Pkg -ne ""`
- [x] `post-change: PASS` (ChangeType Script), включая гейт синхронизации шпаргалки скриптов
