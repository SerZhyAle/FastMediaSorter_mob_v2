# S0297 — Decision brief for owner

**Parent spec:** `PLAN/S0297_nolegal-vr-capability-research.md`
**Backing research:** `PLAN/S0297_nolegal-vr-capability-research/RESEARCH.md`
**Created:** 2026-05-25

Однопроходный документ для owner-input. Каждое решение даёт 2..3 опции с короткими pros/cons и явной рекомендацией. Пометить выбор галочкой (✓) рядом с опцией, после чего этот файл превращается в protocol-of-record, и стратегическая спека `S0297` переходит из `Draft` в `Approved`.

---

## Решение D-1 — порядок first-wave (что делаем первым)

Контекст: research показал, что приоритет в стратегической спеке был «A1 + A2 первой волной». После web-research картина изменилась — у A1 есть upstream-блокер (YouTube VR180 broken), B3 значительно дешевле и независим, A2 даёт единственную realible дифференциацию.

- ✅ **D-1.a (recommended) — chosen 2026-05-25:** **B3 first, A2 second.** B3 — 1..2 дня Kotlin без зависимостей, мгновенная UX-польза в Browse, не блокируется ничем. A2 — после Verified S0296. A1 + B1.3 + B2 — second wave.
- ☐ D-1.b: A2 first, B3 second. Если differentiation важнее лёгкости. Минус: A2 заблокирован S0296 → first-wave не стартует пока S0296 не Verified.
- ☐ D-1.c: A1 first (как изначально планировалось). Минус: 100% fail на YouTube VR180 сегодня, нужна A1.2 (`SpatialMetadataInjector`) обязательно.

---

## Решение D-2 — A1 variant (если A1 в first wave)

Контекст: research показал, что A1.1 (просто preset для extractor_args) не выживает upstream-breakage и не даёт immerse-player сигнал о projection. A1.2 добавляет `SpatialMetadataInjector` (~300 LOC MP4 box-walker по Google spatial-media v2 RFC).

- ✅ **D-2.a (recommended) — chosen 2026-05-25:** **A1.2** — extractor preset + metadata injector. Файл получает `st3d`/`sv3d` boxes → immerse-player может auto-detect projection из metadata, не из format_id.
- ☐ D-2.b: A1.1 — только preset. Минус: файлы без projection-metadata, immerse-player падает в guesswork по filename.
- ☐ D-2.c: Отложить A1 совсем, пока upstream YouTube VR180 не починят и `SpatialMetadataInjector` не появится как shared primitive для B1.3.

---

## Решение D-3 — B1 scope (adult-VR catalog)

Контекст: research показал, что нет TMDB-эквивалента; community-интеграции (xbvr, stash-vr) реализуют consumer-side DeoVR/HereSphere протокола, не aggregator-side API. Прямая интеграция (B1.1) — structurally unbounded maintenance.

- ✅ **D-3.a (recommended) — chosen 2026-05-25:** **B1.3 only — локальные `.hsp` sidecar.** Сканирует папки с уже скачанными VR-файлами, пишет `.hsp` с тегами/projection/жанрами, открывает в `VrLibraryActivity`. Совместим с HereSphere/DeoVR import. Maintenance Low.
- ☐ D-3.b: B1.3 + B1.2 (`.hsp` + curated URL-list). Owner ведёт private JSON-список scene URLs, app рендерит как library + извлекает через yt-dlp. Maintenance Medium.
- ☐ D-3.c: Hold B1 entirely. Owner не нуждается в library-mode → C-кандидат до dedicated user-pull.

---

## Решение D-4 — B2 stack (codec pack, если B2 в second wave)

Контекст: research показал, что Media3 FFmpeg extension audio-only, FFmpegKit заархивирован, libVLC AAR (LGPL-2.1+, ~30 МБ) — единственный production-viable вариант. Все adjacent VR-приложения уже на libVLC.

- ✅ **D-4.a (recommended) — chosen 2026-05-25:** **B2.1 — libVLC AAR как parallel Player в noLegal-flavor.** ~30 МБ binary cost, full HEVC 10-bit / AV1 / DTS / AC4 coverage, production-validated.
- ☐ D-4.b: B2.2 — hybrid (Media3 FFmpeg ext для audio + libVLC для video). Меньше surface для libVLC-ветки, но две extensions для поддержки.
- ☐ D-4.c: Hold B2 entirely. Только если owner не сталкивался с unsupported codec на Quest 3 → MediaCodec покрывает median case.

---

## Решение D-5 — C-кандидаты (хранить или удалить)

Контекст: C-список содержит 6 направлений с явной defer-причиной (passthrough capture, Widevine bypass, Whisper transcription, experimental OpenXR loaders, custom controller mappings, locally hosted yt-dlp catalog). Они могут оставаться как «revisit in 6 months» либо быть удалены целиком.

- ✅ **D-5.a (recommended) — chosen 2026-05-25:** **Оставить C-список как есть** — короткие defer-rationales в `RESEARCH.md` уже минимальны (по строке на пункт). Не мешают. Любой C-пункт можно потом перевести в A/B через `/spec-update`.
- ☐ D-5.b: Удалить C-список полностью. Минус: при будущем «а что если» придётся переоткрывать research.
- ☐ D-5.c: Удалить часть C — конкретные пункты (Whisper, experimental loaders) удалить полностью; passthrough capture / Widevine / custom controllers — оставить.

---

## Решение D-6 — связь с S0156 epic

Контекст: `S0156` (`BlockByOtherTask`) — родительский noLegal epic, фиксирует политику документации (`docs/FEATURES_noLegal.md`) и flavor isolation. Все будущие noLegal-VR impl-тикеты по-хорошему должны ссылаться на S0156 как родителя, но S0156 заблокирован.

- ✅ **D-6.a (recommended) — chosen 2026-05-25:** **Impl-тикеты после S0297 ссылаются на оба:** S0156 (документация / flavor isolation policy) + S0297 (capability rationale). Пробуждать S0156 не нужно — он уже выполнил роль политики.
- ☐ D-6.b: Разблокировать S0156 (статус `BlockByOtherTask` → `Approved`) перед началом impl-тикетов. Минус: S0156 был заблокирован по причине, надо понимать почему.
- ☐ D-6.c: Полностью оторвать noLegal-VR от S0156 — impl-тикеты ссылаются только на S0297. Минус: дублирование policy decisions.

---

## Решение D-7 — extraction shared primitive

Контекст: research выявил, что `SpatialMetadataInjector` (из A1.2) — это shared primitive, нужный для:

- A1.2 (yt-dlp VR extraction post-processing).
- B1.3 (`.hsp` sidecar generation требует projection-type).
- Future auto-detect-stereo-format из S0240 §10.3.

Если A1 и B1.3 обе попадают в first/second-wave — стоит вынести primitive в shared `noLegal/util`-модуль вместо дублирования.

- ✅ **D-7.a (recommended) — chosen 2026-05-25:** **Extract `SpatialMetadataInjector` в shared module** на этапе implementation A1.2 (поскольку A1.2 — первый использующий). B1.3 потом просто инжектится через тот же helper.
- ☐ D-7.b: Не выносить — пусть каждое использование живёт в своём impl-тикете. Минус: ~300 LOC дублирования.
- ☐ D-7.c: Решение отложить до момента, когда оба impl-тикета (A1 и B1) попадут в активную работу.

---

## После owner-decision

Когда owner проставил галочки и сохранил файл, mechanical next steps:

1. **Зафиксировать решения в Last Audit родительской спеки** (`PLAN/S0297_nolegal-vr-capability-research.md`).
2. **Перевести S0297 status** `Draft → Approved` через `update.ps1 -Id S0297 -Status Approved`.
3. **Аллоцировать impl-тикеты** в порядке выбранного D-1 решения. Для каждого:
   - `next-id.ps1` → новый `Sxxxx`.
   - `insert.ps1` с `-Status Draft -Priority N -Tier 3`.
   - `/spec` для написания стратегической спеки с явной ссылкой на S0297 (и опционально S0156 по решению D-6) в `Roadmap entry`.
4. **Sub-зависимости отметить явно:**
   - Если D-2.a выбран — A1-тикет ссылается на shared primitive из D-7.a.
   - A2-тикет имеет `Depends on: S0296 Verified`.
   - A1-тикет имеет `Depends on: S0296 Verified` + соответствующий yt-dlp issue tracker (если YouTube VR180 ещё broken upstream — A1 ждёт upstream-fix или сразу делает A1.2 без YouTube).
5. **B3 не имеет блокеров** — может стартовать сразу после Approved.

---

## Recommended composite default (если owner хочет «just do the recommended»)

Если ставить все галочки на (recommended), получается:

| Решение | Выбор |
|---------|-------|
| D-1 | B3 first → A2 second → A1 third (gated на S0296) → B1.3 → B2.1 (second wave) |
| D-2 | A1.2 (extraction + injector) |
| D-3 | B1.3 only (локальные `.hsp` sidecar) |
| D-4 | B2.1 (libVLC AAR) |
| D-5 | Оставить C-список как «revisit in 6 months» |
| D-6 | Impl-тикеты ссылаются на S0156 + S0297, S0156 не разблокируется |
| D-7 | Extract `SpatialMetadataInjector` в shared module на этапе A1.2 |

Эта композиция — минимум технического риска, максимум переиспользования существующего noLegal-стека, durable maintenance posture по всем направлениям.

---

## Open considerations (не требуют owner-decision сейчас)

Эти пункты выйдут на сцену **во время** implementation, не на decision-stage:

- Тестовое покрытие A1: нужен набор 5..10 публичных VR-URL для verify (Vimeo 360, Reddit VR, тестовые tube-сайты). Скрипт provisioning может быть аналогом `setup_test_vr.ps1` (используется в S0290/S0291).
- A2 thermal budget на Quest 3: первый impl-тикет должен включить on-device thermal logging за 90-минутный playback session.
- B3 detection-cache strategy: SHA-1 prefix vs `(path, size, mtime)` — рассматривается в impl-тикете, не в decision.
- libVLC AAR 16 KB-page-alignment check: тот же ритуал, что для Paddle-Lite в S0288 — `readelf -lW` в impl-тикете.
