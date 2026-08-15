# S1122 - Lite flavor Streams scope drift (owner expects audio radio, code hides Streams)

**Ticket:** S1122
**Status:** Archived
**Priority:** 35
**Date:** 2026-07-19
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захват находки (verbatim, из аудита S1118 2026-07-19)

Обнаружено при review-mode драйве S1118 (radio-stream-buffer-tolerance). Расхождение spec/owner-intent vs code vs doc по объёму функции Трансляций во флейворе **lite**.

**Owner-intent (S1118):**
- `PLAN/S1118_radio-stream-buffer-tolerance.md:44` (§3.2 жёсткое ограничение) и `:54` (§3.3 owner sign-off, подтверждено владельцем 2026-07-19) заявляют scope: `Standard/Legacy/noLegal/VR/Lite (в Lite - только progressive-audio радио, без RTSP/видео)`.

**Code (текущий build.gradle.kts):**
- `app_v2/build.gradle.kts:413` - lite: `SUPPORT_STREAMS = false` (комментарий: `S0575: Streams feature UI hidden in lite (streamingDisabled pipeline unchanged)`).
- `app_v2/build.gradle.kts:439` - photos: `SUPPORT_STREAMS = false`.
- Остальные (standard/noLegal/legacy/vr) - `true`.
- `CapabilityAvailability.kt:47`: `isStreamsAvailable(): Boolean = BuildConfig.SUPPORT_STREAMS` - единственный булев гейт, 8 call-sites (menu item, settings fragment, panel route, main-screen streams row). **Нет** тонкой ветки «audio-only Streams».
- Следствие: в Lite весь surface Трансляций (пункт меню, экран настроек, панель-роут, `StreamsActivity`, оба inline-audio менеджера) отсутствует целиком - как в Photos, а не «только аудио».

**Doc (stale):**
- `docs/ARCHITECTURE.md:224` («Internet Streams Subsystem») до сих пор пишет: `lite - progressive-audio only (HLS/DASH/RTSP show unsupported message)` - неверно относительно текущего `build.gradle.kts`.

**Происхождение:** S0575 (`streams-toggle-welcome-entrypoints`, Archived 2026-06-26) скрыл entry-points Трансляций в lite. Похоже, doc и owner-ожидание не были синхронизированы с этим изменением.

## 1. Проблема

Три источника не согласованы по объёму Трансляций в Lite:
- Владелец (в S1118) ожидает: Lite = progressive-audio радио.
- Код: Lite = Трансляций нет вовсе (`SUPPORT_STREAMS=false`).
- Doc: Lite = progressive-audio только.

Нужно продуктовое решение, какой из вариантов верен, и привести остальные два в соответствие.

## 2. Варианты (продуктовое решение владельца)

1. **Принять «в Lite Трансляций нет».** Скорректировать §3.2/§3.3 S1118 (убрать Lite из scope радио-устойчивости) и `docs/ARCHITECTURE.md:224` (убрать строку про lite progressive-audio). Никаких изменений кода.
2. **Вернуть audio-only Трансляций в Lite.** Ввести partial-доступность (напр. отдельный флаг `SUPPORT_STREAMS_AUDIO` или тонкую ветку в `isStreamsAvailable`), показывающую только progressive-audio радио без RTSP/видео. Новая capability-нарезка - существенная работа, отдельный tactical.

## 3. Заметки

- Не блокирует S1118: устойчивость радио живёт в общей `src/main`-реализации и реально доходит до Standard/Legacy/noLegal/VR независимо от исхода этого тикета. Lite-scope - ортогональный вопрос.
- Дубликатов в каталоге не найдено (поиск `lite/SUPPORT_STREAMS/progressive audio/streams flavor` 2026-07-19).

## 4. Решение (owner, 2026-07-19)

**Выбран вариант 1:** в Lite Трансляций нет вовсе (как в Photos). Кода не трогаем - S0575 намеренно скрыл Трансляций в lite, Lite остаётся лёгким.

**Объём фикса (doc-only, для `/spec-dev`):**
1. `PLAN/S1118_radio-stream-buffer-tolerance.md` §3.2 и §3.3 - убрать Lite из flavor-scope радио-устойчивости (реальный охват: Standard/Legacy/noLegal/VR). Строка «Flavor drift» §3.2 уже это фиксирует - привести §3.3 owner-scope в соответствие.
2. `docs/ARCHITECTURE.md:224` - заменить строку «lite - progressive-audio only (HLS/DASH/RTSP show unsupported message)» на «lite - Streams недоступны (`SUPPORT_STREAMS=false`, S0575)».
3. Прогнать document-registry `validate.ps1` (затрагивается запись `architecture`).

### 3.3 Owner inputs (Approval gate)

- **Flavor scope decision:** Lite не получает Трансляций (подтверждено владельцем 2026-07-19 через `/spec-quiz`). Кода не трогаем; фикс - только документация.
- **Related tickets:** S1118 (носитель устаревшего §3.2/§3.3 scope), S0575 (Archived - источник скрытия Streams в lite), S1120 (смежный doc-drift того же аудита).

### Quiz decisions (2026-07-19)
- Объём Трансляций во флейворе Lite → **Вариант 1: принять, Lite без Трансляций** (совпадает с осознанным решением S0575; ноль кода; Lite остаётся лёгким; тикет становится простым doc-fix).

## Last Audit

**Дата:** 2026-07-19 (`/spec-all` Simple path, doc-only). **Вердикт:** Verified.

Три источника приведены к реальности (Lite Трансляций не получает), кода не трогали:
- `PLAN/S1118_radio-stream-buffer-tolerance.md:45` (§3.2) - flavor scope `Standard/Legacy/noLegal/VR`; Lite/Photos исключены (`SUPPORT_STREAMS=false`). Строка «Flavor drift» заменена на «Flavor scope resolved (S1122)».
- `PLAN/S1118_radio-stream-buffer-tolerance.md:56` (§3.3) - owner-scope выровнен, помечен как уточнённый S1122.
- `docs/ARCHITECTURE.md:224` - `lite - progressive-audio only ..` -> `lite/photos - feature absent (SUPPORT_STREAMS=false, lite hidden by S0575)`. **Заодно исправлена смежная неточность:** vr добавлен в список streams-capable флейворов (у vr `SUPPORT_STREAMS=true`, строка его опускала).
- Verify: grep - все строки читают охват `Standard/Legacy/noLegal/VR`, ни одна не заявляет Lite-Streams. Document-registry `validate.ps1` PASS (23 записи, `architecture`); `generate.ps1 -Check` current.

Пользовательской способности не добавляет (документация) - записи в `docs/ALL_FEATURES.jsonl` не требует.
