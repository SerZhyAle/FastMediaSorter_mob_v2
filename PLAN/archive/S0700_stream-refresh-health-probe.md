# Стратегическая спецификация: S0700 - Проба статуса трансляций по кнопке обновить (зелёный/жёлтый)

**Ticket:** S0700
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-25
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-06-25
**Tactical spec:** `PLAN/S0700_stream-refresh-health-probe/` (будет создан через `/spec-tech`)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 0. Захваченный материал (inbox)

> Сырой захват идеи на лету. Вербатим-текст пользователя и вложения. Распределяется по §1/§3.1/§6 при доработке через `/spec` или `/spec-update`; секцию можно удалить, когда материал перенесён.

**Захвачено:** 2026-06-25

**Текст:**

КОгда трансяции в режиме сетки то иконками трансляций становятся favicons. Я хочу чтобы когда пользователь нажимает кнопку обновить:
- в списке - программа изучила все трансляции которые сейчас видит пользователь и по-одной попыталась её "прослушать", "просмотреть" - если ок - зеёлный статус, нет - желтый
то есть это как бы обноыление статуса
- в сетке е - программа изучила все трансляции которые сейчас видит пользователь и по-одной попыталась её "прослушать", "просмотреть" - если ок - зеёлный статус, нет - желтый
то есть это как бы обноыление статуса НО для видеотрансляций первый захваченный кадр поставила в качестве миниатюры

этот процесс должен обрываться, если пользователь что-то нажал. вышел из трансляций или выбрал какую то или прокрутил список или установил фильтр - неважно

**Вложения:**

Вложений нет.

---

## 1. Проблема

<2–4 предложения. Что сломано или чего не хватает? Эффект на пользователя. Область - модуль/feature-path без имён классов.>

---

## 2. Цели

<Нумерованный список наблюдаемых улучшений.>

**Non-goals:**

- <что явно вне объёма>

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

<Нумерованный список желаемого, но необязательного к первой итерации.>

### 3.2 Жёсткие ограничения

- **Flavor:** <затронутые варианты сборки>
- **API level:** <минимальный уровень Android или «без API-специфики»>
- **Wear OS:** <затрагивается или нет>
- **Производительность:** <бюджет CPU/память/батарея, если критично>
- **Совместимость данных:** <форма миграции без номера версии Room>
- **Локализация:** EN/RU/UK - всегда обязательно, или уточнение.
- **Доступность:** <TalkBack, touch target, не-цветовое отличие - если фича визуальная>

### 3.3 Owner inputs (Approval gate)

<Заполняется при переходе Draft → Approved (через /spec или /spec-update). В скелете оставить пустым, кроме обязательного поля ниже.>

- **Related tickets:** <Sxxxx-зависимости / зависящие, либо «none»>

---

## 4. Контекст текущей архитектуры

<1–2 абзаца. Какие слои/компоненты отвечают за затронутую область. Почему сейчас нельзя решить проблему из §1. Без перечисления классов.>

---

## 5. Предлагаемый подход

<Архитектурный уровень: какие роли появятся, откуда читают / куда пишут. Имена классов, файлов, методов - запрещены.>

### 5.1 Основные столпы / модули

<Крупные логические блоки.>

### 5.2 Потоки данных и событий

<Высокоуровневая схема. «UI → слой применения → кэш → ..». Без имён методов.>

### 5.3 Точки расширяемости

<Что должно остаться открытым к расширению.>

---

## 6. Открытые вопросы / Research items

<Если вопросов нет - «Открытых вопросов нет.»>

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| <описание> | Низкая / Средняя / Высокая | <что сломается> | <как предотвратить> |

---

## 8. Влияние на пользователя (docs/FEATURES)

<По умолчанию: «Без изменений в docs/FEATURES.» Если фича новая - одно предложение для FEATURES + _RU + _UK.>

---

## 9. Архитектурные решения (ADR)

<Если нет - «ADR нет - решение по устоявшимся паттернам проекта.»>

---

## 10. Связи с другими спеками

<Список связей или «Связей нет.»>

---

## 11. Критерии готовности (strategic-level)

<Нумерованный список. Наблюдаемые результаты, не архитектурные утверждения.>

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0700` - создаст `PLAN/S0700_stream-refresh-health-probe/` с фазами.

---

## Last Audit

### CORRECTION: native crash on real device - capture DISABLED - 2026-07-04 (Samsung SM-S731B, Android 16 / API36, noLegal debug 2.60.7040.307)

**Verdict: the try/catch fix below is correct for the EMULATOR (Java) path but does NOT fix the real device - that crash is a NATIVE process kill Java cannot catch. `CAPTURE_ENABLED` flipped to `false`; grid uses the favicon atlas (S0785) until capture is reworked.**

Owner re-tested on the Samsung and it still crashed "in the same place". Four Timber session exports (`logs/fastmediasorter_20260704_0310-0312*.log`) show the real signature: entering GRID logs the full `S0700: snapshot request enqueued` burst for all pinned channels and then the log **ends mid-burst** - no exception, no `favicon fallback` line, no `onDestroy`, no `fastmediasorter_crash_*.log`. The process is killed natively during the first capture's decoder/Surface setup, BEFORE `onImageAvailable` ever fires. So:

- The emulator manifestation (Java `UnsupportedOperationException` at `acquireLatestImage`, caught by the try/catch below) and the Samsung manifestation (native abort during codec/Surface setup on Exynos + API36) are DIFFERENT failure points of the same fundamentally-incompatible ImageReader capture path.
- A Java `try/catch` on the reader callback cannot catch a native SIGABRT/SIGSEGV that happens before any Java callback runs. The hardening below is kept (it correctly neutralises the emulator path) but it is NOT sufficient for the device.

Action: `CAPTURE_ENABLED = false` (the stopgap this ticket's own status note pre-authorised on re-crash). Real grid capture is off on every flavor; grid tiles fall back to the favicon atlas (now good-looking after S0785). No live-thumbnail capture runs, so nothing can native-crash.

Path to actually restore live thumbnails (owner rejected a permanent favicon-only fallback): rework capture off the offscreen-`ImageReader`-Surface approach. The 2026-06-27 S21 test used a `TextureView` + implied PixelCopy path and did NOT native-crash (it only released too early -> black frame); that direction, plus a bounded first-frame wait before release, is the candidate. `MediaMetadataRetriever.getFrameAtTime` is a poor fit for live HLS. This is a real rework, tracked as the re-enable gate on `CAPTURE_ENABLED`.

### Real logcat + root cause found - 2026-07-04 (emulator Pixel-6 / Android 13 API33, standard debug)

**Verdict: ROOT CAUSE IDENTIFIED (deterministic codec format mismatch, NOT the timing race of experiment #3); crash fixed + verified on emulator - capture stays enabled.**

The real `adb logcat` the prior notes demanded was captured. The GRID crash is a clean Java `UnsupportedOperationException: The producer output buffer format 0x23 doesn't match the ImageReader's configured buffer format 0x1`, thrown by `ImageReader.acquireLatestImage()` on the reader's own `StreamFrameReader` `HandlerThread`. `0x23` = `YUV_420_888` (what this device's decoder renders into the surface); `0x1` = `RGBA_8888` (how the offscreen `ImageReader` is configured). It is deterministic per codec, not a layout/Surface timing race - `POST_LAYOUT_SETTLE_DELAY_MS` (experiment #3) was addressing the wrong cause.

Because the throw lands on the ImageReader `HandlerThread` (not `capture()`'s main-thread coroutine, whose `try/catch` cannot reach it), it was uncaught -> process kill. This is a plausible unifier for the Samsung native-kill too (a decoder that outputs a format the RGBA reader rejects), though that variant died natively with no Java trace so it is not yet proven identical.

Fix (`StreamFrameSnapshotManager`): the entire reader `OnImageAvailableListener` body is now wrapped in `catch (t: Throwable)` (the reader thread can never take down the process again), and the `acquire` sites catch `UnsupportedOperationException` explicitly - the unusable frame is dropped and `capture()` falls back to the channel favicon FOR THAT CODEC ONLY. This is NOT the rejected global favicon-only fallback: a decoder that renders RGBA still yields a live thumbnail. `CAPTURE_ENABLED` stays `true`.

Verified on the emulator (whose software codec is YUV-only): entering GRID fired 16 format-mismatch captures, every one caught and logged (`I .. producer frame format unsupported by RGBA reader - favicon fallback` and `W ..$capture: callback failed on reader thread - favicon fallback`), `0 FATAL EXCEPTION`, the process stayed in `StreamsActivity` (no `CrashActivity`). Full `post-change.ps1` gate suite PASS (detekt scoped clean).

Device gate: confirm on a real device with an RGBA-capable HW decoder (Galaxy S21+) that live thumbnails still render there - the emulator can only exercise the fallback path. Optional follow-up (owner's call, since a global fallback was rejected): make capture universal by decoding `YUV_420_888` and converting to RGB (or an `ImageFormat.PRIVATE` + GPU path), so thumbnails render even on YUV-only decoders instead of falling back.

### Owner pushback + second experiment - 2026-07-03 20:30

Owner reports live-updating grid video thumbnails worked on-device before today's changes and rejects a permanent favicon-only fallback (the 19:20 stopgap below) as a first resort. Re-examined whether S0900's specific diff (Phase 1: a `pending`-cleared guard in `drainOne` that skips a capture only after `cancelAll()` runs, i.e. only after leaving GRID) could explain a regression in the "enter GRID and stay" crash scenario - it cannot: that guard is inert while the grid stays open (`pending` is only cleared by `cancelAll()`, called from leaving GRID / `onStop`), so behavior for the crashing scenario is unchanged before/after S0900's commit. No code-level explanation found for "worked before S0900" specifically; still open whether the owner's "worked" memory is from a different build, a different device, or the OS itself updating to Android 16 recently.

Re-enabled capture (`CAPTURE_ENABLED = true`) and added a new, previously-untested variable: `POST_LAYOUT_SETTLE_DELAY_MS = 500L` before the `ImageReader`/`ExoPlayer` setup in `capture()`. Theory: both prior crashes happened within ~50ms of the GRID's initial `RecyclerView` layout/bind burst (19 tiles inflated in one frame) - the delay pushes the risky Surface/decoder setup safely past that burst, testing a timing/Surface-contention race instead of a hard per-attempt incompatibility (which the concurrency-count experiments already ruled out as the cause). `standard debug` Kotlin compile PASS, full `post-change.ps1` gate suite PASS.

This is experiment #3, unconfirmed. If it crashes again on retest, stop guessing blind - fall back to the favicon-only stopgap (flip `CAPTURE_ENABLED = false`) and get a real native trace (tombstone/`adb logcat -b crash -b main` at the moment of the crash) before any further attempt.

---

### Re-test after mitigation - 2026-07-03 19:05 (same Samsung SM-S731B, Android 16 / SDK 36, noLegal debug, App version 260703190 - includes the MAX_CONCURRENT_CAPTURES=1 mitigation below)

**Verdict: MITIGATION FAILED - crash reproduces identically with only 1 concurrent capture. Root cause is NOT capture concurrency.**

User re-tested and supplied 4 more Timber logs (`fastmediasorter_20260703_1905{15,27,33,41}.log`). 3 of 4 sessions crash with the exact same signature as before the fix: enter GRID -> `S0700: snapshot request enqueued` burst for all 19 pinned channels (still duplicated twice, same as before) -> log ends mid-burst, no further line, no exception. Confirmed `MAX_CONCURRENT_CAPTURES = 1` in the running build (source checked, matches). The 4th session (`190541.log`) went to Settings instead of GRID and survived, consistent with earlier evidence.

Since dropping concurrency from 2 to 1 had zero effect, the crash is not resource contention from multiple simultaneous decoders - it is triggered by starting the *first* real capture (ExoPlayer + offscreen `ImageReader` targeting a live HLS stream) at all, on this device/OS. Leading suspects, unconfirmed without a native trace: a Codec2/Surface-producer incompatibility between a hardware decoder and an `ImageReader`-backed `Surface` for this pixel format/size on this SoC (Samsung Exynos s5e9945) + this very-new OS (Android 16 / API 36), or a native allocation failure in the decoder's buffer setup. `Debug.getNativeHeapFreeSize()` readings before the crash (~4MB free of a 31MB reserved arena) are not strong evidence either way - that metric reflects the malloc arena's current reservation, not a hard ceiling, since the allocator grows on demand.

Do not attempt another blind concurrency/timing tweak without a real native crash signature (tombstone or full `adb logcat -b crash -b main` captured at the moment of the crash) - two mitigations have now failed to reproduce-fix blind. See owner discussion for the chosen next step (temporary favicon-only fallback in GRID vs. gathering device-level crash evidence first).

---

### User-reported crash - 2026-07-03 (Samsung SM-S731B, Android 16 / SDK 36, noLegal debug)

**Verdict: CONFIRMED CRASH in GRID mode, no root-cause stacktrace available - mitigated, needs re-test**

User supplied 4 Timber session-export logs (`fastmediasorter_20260703_18{0537,0544,0600,0706}.log`) covering 4 process restarts inside ~90 s, reporting "падает в окне трансляций когда они сеткой" (crashes in the streams window when in grid mode).

- **Two sessions crashed identically.** `180537.log` opens with `PREVIOUS SESSION ENDED WITH A CRASH` (banner from `FastMediaSorterApp.kt`, gated on `LoggingHelper.hasPreviousCrash()`). User opens `StreamsActivity` (grid restored as last mode) - the grid immediately enqueues `S0700: snapshot request enqueued` for all 19 pinned channels, duplicated twice back-to-back (two independent triggers - `StreamGridAdapter.bind()` and a second capture-request wave - both firing at initial layout; deduped internally by the `pending` set so it does not double real concurrency, just the log line). The log file ends mid-burst with no further line - no `onDestroy`, no exception. `180544.log` repeats the exact same shape after the restart.
- **One session survived by leaving GRID immediately.** `180600.log`: user taps a channel straight into fullscreen playback instead of lingering in grid. The same enqueue burst fires, but since the grid is left immediately, S0900's post-permit guard fires 12x (`S0900: capture skipped, grid left`) before any of the queued captures start a real ExoPlayer - session continues through playback, rotation, and a clean `onDestroy`.
- **No Java stacktrace anywhere.** `LoggingHelper.installCrashHandler()` writes a dedicated `fastmediasorter_crash_*.log` file synchronously on any uncaught Java exception; no such file exists among the exported logs. The crash is therefore a native-level process kill (decoder/codec resource exhaustion is the leading hypothesis, given it lines up with GRID's initial burst spinning up `MAX_CONCURRENT_CAPTURES` concurrent muted ExoPlayer/MediaCodec sessions decoding live HLS the instant the grid is shown and not immediately left) - not something Timber or a Java handler can capture.
- **Mitigation applied (no confirmed root cause):** `StreamFrameSnapshotManager.MAX_CONCURRENT_CAPTURES` dropped from 2 to 1, halving peak simultaneous live-decoder sessions during the grid's initial capture sweep. `standard debug` Kotlin compile PASS after the change.

Recommendation: do NOT flip to Verified from this alone - the native crash signature is still unconfirmed (no tombstone/logcat capture available, only Timber's own log). Re-test entering GRID and staying in it (not navigating away) with several live video channels; if it recurs, capture full `adb logcat` (not just Timber) at the moment of the crash to get the actual native fault before iterating further.

---

### Manual device test - 2026-06-27 (Galaxy S21+ SM-G996U1, Android 15 / SDK 35, standard debug)

**Verdict: INCONCLUSIVE (list-mode green/amber probe PASS; grid first-frame thumbnail still does NOT populate on real HW decode - fails earlier than the emulator timeout, confirming the spec's fallback hypothesis)**

Test channel: "Mux Test HLS" (`https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8`), reachable HLS stream.

- **List-mode status probe PASS (expected: Refresh probes each visible stream, green when reachable / amber when not; actual: confirmed).** Before refresh the status icon `ivPlayStatus` had contentDescription "Ещё не проверялось" (Not yet checked, amber dot). Tapping Refresh logged `23:01:35.112 D/StreamsActivity S0700: health probe started over 1 visible streams` + a Toast; after completion the icon flipped to "Проверено онлайн" (Checked - online, green checkmark). The reachable Mux stream correctly resolved to green. Evidence: `01_list_green.png`.
- **Grid-mode HW decode runs but first-frame thumbnail stays black (the headline grid feature; NOT confirmed).** Switching to grid (`action_stream_display_toggle`) shows the tile with a black thumbnail. Refresh in grid mode does spin up the real capture pipeline on HW: logcat shows the Qualcomm hardware decoder `c2.qti.avc.decoder` allocated and configured `1920x1080 @ 30fps`, `sw codec: no`, reaching `state->set(RUNNING)`, decoding into the offscreen `app:id/textureCapture` TextureView (518x291, the grid thumbnail size). So the emulator software-decoder timeout does NOT occur here - HW decode is available and runs.
- **Root cause shifts from "timeout" to "released too early".** Each capture is `ExoPlayerImpl Init -> qti.avc.decoder RUNNING -> ExoPlayerImpl Release -> decoder RELEASED` within ~400-485 ms (e.g. 23:03:15.639 Init -> 23:03:16.124 Release). That window is too short for the first decoded HLS frame (after segment fetch over the network) to land on the SurfaceTexture before teardown - there is no `onFrameAvailable` for `textureCapture` in the logs. The thumbnail therefore stays black across two Refresh attempts in grid mode (`02_grid_after_refresh.png`, `03_grid_retry_still_black.png`). This validates the spec status-note prediction: real devices also fail, so the offscreen-TextureView-in-RecyclerView approach should be reworked to a manual SurfaceTexture + PixelCopy capture decoupled from the View hierarchy (and given a first-frame wait/seek before release).
- **No crash, no ExoPlayer playback error.** Player released cleanly each cycle (release() in finally path holds); the stream itself plays (decoder ran), so the failure is purely in getting the captured frame onto the thumbnail.

Recommendation: do NOT flip to Verified. The list-mode probe is shippable; the grid first-frame thumbnail needs the manual SurfaceTexture + PixelCopy rework (add a bounded wait for the first frame / short seek before release). The P1 race in the Audit note below should land with that rework.

Evidence: `temp/S0700_devtest/` (01_list_green.png, 02_grid_after_refresh.png, 03_grid_retry_still_black.png, logcat_full.txt, logcat_grid_capture.txt).

---

## Audit note - 2026-06-27 (release-safety, NOT yet fixed)

Static audit of `StreamFrameSnapshotManager` found a P1 concurrency race to fix during (or before) the device-test session:

- `request()` launches the outer drain coroutine on `lifecycleScope` (~line 70) but only the INNER capture job is added to `inFlight` (~line 99). `cancelAll()` (~line 78) cancels only `inFlight`, so an outer coroutine sitting between `queue.poll()` and the inner `scope.launch` is not cancelled. After a fast scroll + `onStop` it can still start a capture and fire `onOutcome` on the main thread for a url whose `pending` guard was cleared -> a stale green/amber probe result is written for an item the user already scrolled past.
- Fix direction: track the outer jobs too, or give the manager its own child `CoroutineScope` that `cancelAll()` cancels wholesale.
- Also (P2, protocol): add `player?.setVideoTextureView(null)` before `player?.release()` in `capture()` (~line 169) per the CODE_AUDIT_PROTOCOL release contract.

Non-crashing; Streams is gated by the `enableStreams` toggle. The timeout path itself is leak-free (release() in finally, verified). Does not block the HW-decode device test, but should land before S0700 is Verified.
