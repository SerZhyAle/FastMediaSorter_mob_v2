# S1046 - companion-fmscfg-doc-sync

**Status:** Archived

## 0. Raw capture (verbatim)

> Doc-долг по companion/.fmscfg - 8 тикетов кода, но docs/ARCHITECTURE.md и operations-index их не упоминают. Скоуп /doc-update.

Source: side-finding surfaced during S1039 research (`.fmscfg`/companion QR share). Parked via `/spec-draft`, not investigated.

## 1. Symptom / evidence

- Companion / `.fmscfg` is a substantial, shipped subsystem (cross-repo frozen contract) with ~8 delivered tickets: S0421 (windows-sftp-folder-share-companion), S0422, S0984, S0988 (qr-scan-companion-import), S0994, S1013 (lan-mdns-discovery), S1016 (per-root readOnly), S1039 (QR share) - plus the serializer/DTO/import layer in code.
- `docs/ARCHITECTURE.md` and `dev/PROJECT_OPERATIONS_INDEX.md` (Feature-to-Path Map) do not mention the companion subsystem, its config contract, transports (`FMSCFG1:` gzip + plain JSON), or its QR import/export path. A future agent researching companion work has no doc entry point and must rediscover it from code every time.

## 2. Scope (to research / update)

- Add a companion / `.fmscfg` section to `docs/ARCHITECTURE.md`: subsystem purpose, the frozen contract (schemaVersion 2, forward-compat), transports, serializer/DTO classes, import (QR scan) + export (file + QR) paths.
- Add companion feature -> path mapping to `dev/PROJECT_OPERATIONS_INDEX.md`.
- Verify against the live contract doc/spec set (S0421 is `BlockExternal` - producer side lives in the external Windows repo; keep the client-side description authoritative here).

## 3. Open questions

- Where the canonical contract description should live (single source) vs. what is duplicated in `docs/ARCHITECTURE.md` - avoid drift with the cross-repo frozen contract.

## 3a. Research so far (2026-07-16, `/spec-next` -> `/spec-all`)

> **Superseded the same day - see `## Last Audit`.** This section recorded a deliberate stop after a partial pass; the operator said continue, the subsystem was then read and the section written. Kept because its two findings (the name collision, the producer/consumer asymmetry) are what shaped the final text - but its "still open" list below is closed, and its "not enough context budget" framing no longer describes the outcome.

Partial pass. The gap in §1 is confirmed and one constraint surfaced that changes the shape of the fix. Not enough context budget in that session to write the ARCHITECTURE section itself - the deliverable here is *authority* (a future agent's entry point), and an entry point written from a thin reading of the subsystem would be confident fiction, which is worse than the current silence.

**Confirmed:**

- `docs/ARCHITECTURE.md` -> **zero** matches for `fmscfg` / `companion`.
- `dev/PROJECT_OPERATIONS_INDEX.md` -> one match, and it is not this subsystem: line 8, `- Wear companion: wear/`.
- No dedicated contract doc exists: `ls docs/ | grep -i 'fmscfg|companion|contract'` -> nothing.

**New constraint - the name "companion" is already taken.** Every developer-doc hit for "companion" today means the **Wear OS** companion, not the Windows one:

- `docs/DOCS_MAP.md:46` - a section titled "Wear OS Companion".
- `docs/DEV_OPS.md:294` - the `SUPPORT_WEAR_COMPANION` flavor-matrix row.
- `dev/PROJECT_OPERATIONS_INDEX.md:8` - `Wear companion: wear/`.

So a section merely titled "Companion" in `ARCHITECTURE.md` would collide with an existing, different subsystem and make the map worse, not better. Whatever is written must name the Windows/`.fmscfg` side unambiguously (e.g. "Desktop companion (`.fmscfg`)") and the operations-index entry must not read as a sibling of `Wear companion`. Settle this naming before writing prose.

**Still open for the next pass:**

- The §3 single-source question is untouched. Note that S0421 (the producer side) is `BlockExternal` and lives in the external Windows repo (`P:\windows\fms_companion`), so this repo can only be authoritative for the **consumer** half. That asymmetry is probably the answer to §3, but it was not verified against the live contract text.
- The subsystem itself (serializer / DTO / import-export paths, `FMSCFG1:` gzip vs plain JSON transports) has not been read. Do that before writing.
- Check the user-facing docs that already mention a companion (`docs/FAQ*.md`, `docs/FEATURES*.md`, `docs/DOWNLOADS_EN.md`) - some of those hits may be the Windows one, and if so they are existing prose to stay consistent with rather than contradict.

## 4. Notes

- Doc-only scope; route via `/doc-update` when picked up. No code change expected.

---

## Last Audit

**Дата:** 2026-07-16
**Вердикт:** Verified. Doc-only, кода не тронуто.

**Изменённые файлы:**

- `docs/ARCHITECTURE.md` - новый раздел «Desktop Companion Config (`.fmscfg`) Subsystem» (после «Internet Streams Subsystem», по его образцу: границы слоёв, поток данных, точки входа, флейворы).
- `dev/PROJECT_OPERATIONS_INDEX.md` - запись в §9 Feature-to-Path Map.

**§3 (открытый вопрос) закрыт из кода.** Канонический источник контракта - **companion repo `docs/CONFIG_FORMAT.md`**; это записано прямо в коде дважды: `CompanionConfigParser` KDoc («per the contract (companion `docs/CONFIG_FORMAT.md`)») и `CompanionConfigDto` KDoc («Authoritative contract: companion repo `docs/CONFIG_FORMAT.md`. The canonical test vector is frozen on both ends»). Продюсер (S0421) `BlockExternal` и живёт во внешнем репозитории, поэтому здешний репозиторий авторитетен **только за потребительскую половину**. Отсюда решение: раздел **не дублирует список полей** (он бы дрейфовал), а указывает на внешний канон и описывает инварианты, которыми владеет клиент. Это записано в самом разделе как правило, а не только здесь.

**Найдено по ходу и учтено:**

- **Коллизия имён.** Слово «companion» во всех девелоперских доках означало Wear OS (`DOCS_MAP.md:46`, `SUPPORT_WEAR_COMPANION` в `DEV_OPS.md:294`, `Wear companion: wear/` в operations-index:8). Раздел, названный просто «Companion», сделал бы карту хуже. Отсюда «Desktop Companion Config (`.fmscfg`)» и явная оговорка «NOT the Wear companion» в обоих доках.
- **Флейворы - едва не опубликовал неверное.** Первая редакция раздела гласила «all flavors»: в пакете `companion` нет ни `BuildConfig`, ни обращения к `CapabilityAvailability`, и это правда. Но подсистема импортирует **SFTP**-ресурсы, а сетевая группа гейтуется `SUPPORT_LOCAL_NETWORK` (`RemoteSourceAvailabilityGate` -> `MediaCapabilities.supportsLocalNetworkSources`): true в standard/photos/legacy/vr/noLegal, **false в `lite`**. Формулировка исправлена до проверки: код без гейта, полезность ограничена сетевой группой. Ровно тот класс ошибки, что уже стоил правки в S1066 и S1061.
- **`FMSCFG1:` - маркер транспортного конверта, а не версия схемы** (`CompanionConfigParser` companion object это оговаривает явно). Легко спутать; в разделе выделено.

**Проверки:**

- `grep -c -i fmscfg docs/ARCHITECTURE.md` -> 4 (было 0). Раздел на строке 215.
- `grep -i fmscfg dev/PROJECT_OPERATIONS_INDEX.md` -> запись §9 на месте (было: единственное совпадение «Wear companion», то есть другая подсистема).
- `assert-settings-doc-sync.ps1` -> exit 0 (настроек не трогали).
- `check-rule-prompt-drift.ps1` -> exit 1, 18 находок. **Все досуществующие, ни одна не относится к этому изменению**: проверено адресно - grep по `companion|fmscfg|exit.contract` в отчёте пуст; `MissingDocumentedScript` не про новый гейт (`assert-exit-contract.ps1` на диске, 5817 байт); `MissingNoProfile` указывает на `PROJECT_OPERATIONS_INDEX.md:78` - строка досуществующая, мой дифф добавил 0 строк с `pwsh`. Долг парковать не стал: 18 находок в 4 категориях - отдельная работа, и это вывод инструмента-аудита, а не сломанный гейт.

**Прочитанное (основание для утверждений раздела):** `CompanionConfigParser.kt`, `CompanionConfigDto.kt`, `CompanionConfigSerializer.kt` (шапка), `ExportCompanionConfigUseCase.kt` (шапка), `AndroidManifest.xml` (4 intent-filter импорта + `exported=false` у QR-share), `RemoteSourceAvailabilityGate.kt`, `build.gradle.kts` (флаги), места вызова в `MainViewModel`/`MainEventHandler`.
