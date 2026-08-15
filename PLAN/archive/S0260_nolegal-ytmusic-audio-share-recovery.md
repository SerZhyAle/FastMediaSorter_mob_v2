# Стратегическая спецификация: S0260 — noLegal: восстановление YTMusic audio-share

**Ticket:** S0260
**Status:** BlockNeedUserTest
**Priority:** 80
**Date:** 2026-05-19
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc — consolidation after S0190/S0198 archival, 2026-05-19
**Tactical plan:** [`PLAN/S0260_nolegal-ytmusic-audio-share-recovery/INDEX.md`](S0260_nolegal-ytmusic-audio-share-recovery/INDEX.md)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

После серии правок по YouTube share-flow исторический объём S0190 и S0198 больше не совпадает с реальным остатком работ. Shorts и обычные watch-URL уже проходят по рабочему пути, но `music.youtube.com/watch?v=...` всё ещё не даёт стабильный аудио-результат: вместо трека пользователь может получить неподходящий артефакт или сорванный download.

Старые спеки смешали несколько гипотез одновременно: write-timeout на CDN, универсальную PoToken-проблему, speculative NewPipe PoTokenProvider и остаточный YTMusic audio-only gap. В результате активный остаток плохо локализован и мешает понять, что именно ещё нужно довести до конца.

---

## 2. Цели

1. Share `music.youtube.com/watch?v=<id>` в `noLegal` стабильно сохраняет аудио-файл в Downloads.
2. YTMusic share больше не сохраняет thumbnail JPEG, preview-артефакт или другой нецелевой файл вместо трека.
3. Выбранный путь извлечения и скачивания для YTMusic становится однозначным и диагностируемым.
4. Исправление не ломает уже рабочие сценарии: YouTube Shorts, обычные YouTube watch-URL и non-YouTube hosts.

**Non-goals:**

- Полный redesign всего YouTube extraction pipeline.
- Реализация универсального `PoTokenProvider` как обязательной части первой итерации.
- Изменение public/store builds — scope только `noLegal`.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Развязать остаточный YTMusic bug от исторических speculative-гипотез, чтобы дальнейшая работа шла по одному узкому тикету.
2. Предпочесть решение, которое использует уже существующий noLegal download path, если оно подтверждается логами и device-тестом.
3. Возвращаться к теме NewPipe PoTokenProvider только если свежая диагностика покажет, что без этого YTMusic уже принципиально не чинится.

### 3.2 Жёсткие ограничения

- **Flavor:** `noLegal` только.
- **API level:** minSdk 26.
- **Wear OS:** не затрагивается.
- **Производительность:** нельзя вводить новый тяжёлый runtime или вторую параллельную extraction-цепочку без явной необходимости.
- **Совместимость данных:** без изменений пользовательских хранилищ и миграций.
- **Локализация:** новые user-visible строки не планируются; если появятся — EN/RU/UK обязательно.
- **Доступность:** user-facing поведение share-flow не должно деградировать по сравнению с текущим working path.

---

## 4. Контекст текущей архитектуры

В `noLegal` уже существует рабочий путь для YouTube share-download: URL нормализуется к канонической форме, extraction идёт через bundled downloader stack, а throttled CDN-сценарии обходятся через встроенный downloader вместо линейного чтения. Этот слой уже закрыл предыдущие крупные отказы, из-за которых были открыты S0187 и S0190.

Остаточная проблема уже не выглядит как общий failure всей YouTube-поддержки. Она локализована в YTMusic audio-only сценарии и требует отдельного решения, потому что исторические спеки одновременно описывают и уже закрытые проблемы, и гипотезы, которые пока не доказаны на текущем коде.

---

## 5. Предлагаемый подход

Остаток нужно вести как отдельную спецификацию, сосредоточенную только на YTMusic audio-share.

### 5.1 Основные столпы / модули

- **Отдельный YTMusic functional slice** — вход `music.youtube.com` должен иметь собственный ожидаемый результат: только playable audio artifact.
- **Свежая диагностика текущего пути** — решение опирается не на старые гипотезы, а на новый device/log round из актуального билда.
- **Один явный download contract** — extraction, format choice и финальная запись должны быть согласованы так, чтобы YTMusic не сваливался в thumbnail/preview output.

### 5.2 Потоки данных и событий

Share URL YTMusic → canonical processing → extraction/downloader path → целевой audio result.

Если основной audio-only путь невалиден, должен быть один явный fallback с понятным пользовательским результатом, а не скрытое выпадение в нецелевой артефакт.

### 5.3 Точки расширяемости

- Отдельная future-ветка для `PoTokenProvider`, если логи докажут его обязательность.
- Возможность host-specific tuning для `music.youtube.com` без повторного изменения общего YouTube/Shorts flow.

---

## 6. Открытые вопросы / Research items

1. **Точный момент остаточного сбоя**
   - **Вопрос:** YTMusic ломается на extraction, на выборе формата, на самом download request или на финальной записи результата?
   - **Варианты:** audio-only selector mismatch; client-specific response difference; host-specific media fallback.
   - **Нужно выяснить:** свежий device-log на актуальном noLegal build с одной YTMusic ссылкой.
   - **Статус:** Open

2. **Нужен ли реально PoTokenProvider**
   - **Вопрос:** является ли residual YTMusic failure реальной PoToken/botguard зависимостью, или это отдельный yt-dlp/download-path mismatch?
   - **Варианты:** PoToken truly required; different format/client policy is enough; нужен другой fallback.
   - **Нужно выяснить:** сопоставить текущий log outcome с выбранным downloader path и client selection.
   - **Статус:** Open

3. **Fallback contract для YTMusic**
   - **Вопрос:** если strict audio-only path временно недоступен, допустим ли controlled fallback, или результат должен быть только audio и никак иначе?
   - **Варианты:** audio-only or fail; audio-only preferred with explicit fallback; owner-defined alternative.
   - **Нужно выяснить:** зафиксировать owner decision до tactical implementation.
   - **Статус:** Open

Открытые вопросы блокируют tactical decomposition без нового evidence round.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Остаток снова будет ошибочно сведен к общей PoToken-проблеме | Средняя | Повторное разрастание scope и ложные блокировки | Держать новую spec узко на YTMusic audio-share и подтверждать гипотезы логами |
| Исправление YTMusic сломает уже рабочие Shorts/watch сценарии | Средняя | Регресс действующего noLegal YouTube flow | Обязательные regression checks для Shorts и regular watch URLs |
| Новый fallback снова сохранит нецелевой артефакт | Высокая | Пользователь получает JPEG/preview вместо трека | Закрепить явный output contract и negative checks в acceptance criteria |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в `docs/FEATURES_noLegal*` до фактического Verified-результата. Новая спецификация описывает остаточный bugfix scope, а не новую capability.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Остаточный YTMusic bug выносится в отдельный тикет**

- **Решение:** заархивировать S0190 и S0198 как исторические/speculative umbrella-спеки и продолжить работу в одном новом узком тикете.
- **Альтернативы:** оставить S0190/S0198 активными; продолжить вести остаток как часть общего YouTube/PoToken scope.
- **Почему:** текущий рабочий код и последние audit-блоки показывают, что реальный остаток уже меньше и уже, чем описывают старые спеки.

**ADR-2: `PoTokenProvider` перестаёт быть обязательной гипотезой первой итерации**

- **Решение:** считать `PoTokenProvider` только одной из возможных follow-up веток, а не автоматическим блокером новой спеки.
- **Альтернативы:** сохранять `PoTokenProvider` как primary path с первого шага.
- **Почему:** текущий residual issue ещё не доказан как NewPipe-specific или PoToken-specific на актуальном кодовом пути.

---

## 10. Связи с другими спеками

- **S0156** — родительский epic noLegal capability surface.
- **S0174** — исторический yt-dlp umbrella context.
- **S0175** — NewPipe bump, остаётся историческим dependency context.
- **S0186** — pipeline cascade resilience; смежный, но не заменяет остаточный YTMusic scope.
- **S0187** — архивный исторический YouTube recovery context.
- **S0190** — архивируется как superseded umbrella-spec.
- **S0198** — архивируется как speculative PoTokenProvider branch, больше не является primary ticket для остатка.

---

## 11. Критерии готовности (strategic-level)

1. Share `https://music.youtube.com/watch?v=<id>` → в Downloads появляется playable audio file.
2. Для YTMusic share в результате не появляется JPEG, thumbnail, preview-file или другой нецелевой артефакт.
3. Shorts regression check остаётся зелёным.
4. Regular YouTube watch-link regression check остаётся зелёным.
5. Диагностика clearly показывает выбранный YTMusic path и позволяет отличить extraction failure от download/output failure.

---

## Last Audit

**Date:** 2026-05-21
**Mode:** full
**Flags:** -
**Outcome:** BlockNeedUserTest (no flip — insufficient YTMusic-specific evidence)
**Counts:** PASS 2 · WARN 1 · FAIL 0 · MANUAL 3 · EXEMPT 0

Log `logs/fastmediasorter_20260521_141313.log` covers the diagnostic surface for non-YTMusic paths (Threads-embedded `youtube.com/watch?v=<id>` URLs), but does NOT exercise `music.youtube.com/watch?v=<id>`. The original YT Music playlist share at 14:14:27 was abandoned (app backgrounded 5 s later); all subsequent batch downloads stem from a Threads share at 14:16:44 whose `youtube.com` embeds correctly bypass `YtMusicAudioOnlyContract` (early-return Accept on line 21: `!canonicalAudioOnly && originalHost != "music.youtube.com"`).

Suspected build/source skew: `LinkAutoDownloadCoordinator.applySessionContext` lines 105-112 carry a 2026-05-21 fix that propagates the `audioOnly=true` hint through `sessionContext.set(host, emptyList(), null, audioOnly = true)` and emits `Timber.d("S0260: ... propagate audioOnly hint without cookies sessionHost=...")`. The log contains the prior `S0260: session context skipped ... audioOnly=true` line but NOT the propagate-hint follow-up — either the test APK predates the fix, or the propagation branch silently failed.

### Action items

1. **[WARN — build/source skew]** Confirm the test APK was built after `LinkAutoDownloadCoordinator.kt` line 105-112 landed. If APK is older, re-build via `.\a.ps1 dn` (noLegal flavor) and re-run device round before judging contract behaviour.

### Manual / on-device

- [ ] §11.1 — share `https://music.youtube.com/watch?v=<id>` (single track, NOT playlist) → confirm Downloads contains a playable m4a/opus/mp3, NOT mp4. Expect Timber `S0260: LinkAutoDownloadCoordinator.applySessionContext propagate audioOnly hint without cookies sessionHost=…` and downstream `route=python-googlevideo audioOnly=true`.
- [ ] §11.2 — same as §11.1 but explicitly verify result file extension is in `AUDIO_EXTENSIONS = {mp3, m4a, aac, opus, ogg, wav, flac}` per `YtMusicAudioOnlyContract.kt`. Negative case: thumbnail / video-mp4 result → contract should `Reject(ytmusic_thumbnail_artifact|ytmusic_non_audio_artifact, fallbackAllowed=false)`.
- [ ] §11.3 — regression: share `youtube.com/shorts/<id>` → confirm video downloads as mp4, contract returns Accept (canonical Shorts is not YTMusic-host).
- [ ] §11.4 — regression: share `youtube.com/watch?v=<id>` (regular video) → confirm video downloads as mp4, contract Accept on early-return.

### Diagnostic surface (§11.5)

Verified PASS: log shows full S0260 trace surface emitting as designed — `canonical orig=… audioOnly=…`, `session context state host=… audioOnly=…`, `session context skipped host=… reason=… audioOnly=…`, `ytdlp pick bucket=… combinedSeen=… videoOnlySeen=… manifestSeen=…`, `ytdlp route=python-googlevideo url=… audioOnly=…`, `ytdlp python result file=… mime=… size=…`, `contract outcome=Accept|Reject reason=…`.