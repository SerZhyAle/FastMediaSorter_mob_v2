# Спецификация (compact bugfix): S1509 - «Удалить канал» предлагается, когда упала сеть пользователя

**Ticket:** S1509
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-08
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-08

**Захвачено во время:** аудит внешнего документа `PLAYBACK_RESILIENCE.md` (StreamsPlayer, Windows/.NET/libVLC) по запросу владельца «есть ли тут что-то полезное для нас? может мы в чем то лучше?».

**Текст:**

```
P:\WINDOWS\Streams_Player\docs\PLAYBACK_RESILIENCE.md
есть ли тут что-то полезное для нас? может мы в чем то лучше?
```

Находка аудита (их сторона, §4 приложенного документа): «Прежде чем тратить лестницу восстановлений на недостижимый хост, SP-0041 устанавливает, *что именно* недостижимо - хост канала или сама сеть, - через `StreamTransmissionProbe`, чтобы приложение не предлагало удалить канал из-за того, что у пользователя лёг собственный Wi-Fi».

Находка аудита (наша сторона): у нас терминальный диалог показывает кнопку «Удалить» безусловно, без всякой попытки различить эти два случая. То есть сценарий, который они специально закрыли отдельным тикетом, у нас открыт: человек в метро / в машине теряет связь, поток падает, приложение предлагает удалить рабочий канал из его списка. Действие деструктивное и необратимое (запись уходит из списка), а причина к каналу отношения не имеет.

- Эвиденс (наш код): `PlayerEventHandler.kt:165-181` - диалог с neutral-кнопкой `R.string.streams_remove`; `StreamsActivity.kt:983-999` - вторая, независимая копия того же диалога с `viewModel.onRemove(source)`.
- Смежное: `RecordStreamPlayOutcomeUseCase` уже умеет писать `OUTCOME_UNKNOWN` (жёлтый) для неубедительной пробы - то есть понятие «неубедительный результат» в модели данных уже есть, но на терминальный диалог оно не влияет.

**Вложения:**
- Исходный документ StreamsPlayer «Playback resilience», §4 «Recovery» - `PLAN/S1508_bugfix-stream-stepdown-no-decay-window/attachments/01__streamsplayer-playback-resilience.md`

---

## 1. Проблема / симптом

При полной потере связи на устройстве (Wi-Fi выключен, туннель, лифт, роуминг отключён) воспроизведение потока падает, и приложение предлагает пользователю удалить канал из его списка - хотя канал ни при чём.

Симптом наблюдается на двух экранах, независимо:

- Полноэкранный плеер (VIDEO / RTSP): `PlayerViewModel.onStreamPlaybackFailed` -> событие `ShowStreamUnavailable` -> `PlayerEventHandler.showStreamUnavailable` показывает диалог «Трансляция недоступна» с кнопкой «Удалить».
- Экран потоков, встроенное аудио (радио): `StreamsActivity.showStreamUnavailable` показывает вторую, независимую копию того же диалога с той же кнопкой.

Дополнительный, менее заметный ущерб: оба пути безусловно записывают каналу `OUTCOME_FAIL`, и строка в списке краснеет. После одной поездки в метро весь список выглядит мёртвым, хотя ни один канал не отказывал.

Обе последствия необратимы для пользователя разной ценой: удаление стирает запись, а красная отметка держится до следующего успешного воспроизведения и подталкивает к тому же удалению позже.

---

## 2. Корневая причина

Правило «сбой засчитывается каналу только тогда, когда у устройства была сеть» в проекте уже сформулировано и работает - но только на пути фоновых проб, и нигде больше.

- `StreamFrameSnapshotManager.kt:323` (S1469): `shouldPenaliseCaptureFailure(ok, hasNetwork) = !ok && hasNetwork` - именно нужный инвариант, с юнит-тестом `StreamFrameSnapshotPenaltyTest`.
- `StreamsActivity.kt:886` и `MainStreamsInlineAudioManager.kt:69` уже спрашивают `hasAnyNetwork()` **перед** стартом воспроизведения и показывают `streams_error_no_network` вместо попытки.

Не покрыт ровно один участок - терминальный отказ уже начатого воспроизведения:

1. `PlayerViewModel.kt:292` пишет `recordStreamPlayOutcomeUseCase(source.id, ok = false)` без проверки сети, затем шлёт `ShowStreamUnavailable`.
2. `StreamsActivity.kt:985` пишет `viewModel.recordStreamOutcome(source.id, ok = false)` без проверки сети.
3. Диалог существует в двух независимых копиях (`PlayerEventHandler.kt:165-181`, `StreamsActivity.kt:983-999`), поэтому одна проверка, добавленная в одном месте, не защищает второе.

То есть причина не в отсутствии знания о сети - оно доступно через уже внедрённый `NetworkContextAnalyzer` в обоих хостах - а в том, что у правила нет единственного дома, и терминальный диалог его не спрашивает.

---

## 3. Исправление

Один предикат, один диалог, два хоста.

1. **Домен владеет правилом.** `RecordStreamPlayOutcomeUseCase` получает `recordPlayFailure(id, hasNetwork)`: при наличии сети пишет `OUTCOME_FAIL` (красный, как сейчас), при её отсутствии - `OUTCOME_UNKNOWN` (жёлтый, значение уже существует и уже отрисовывается адаптерами). Предикат `isChannelAttributableFailure(hasNetwork)` объявляется там же, и `shouldPenaliseCaptureFailure` из `StreamFrameSnapshotManager` начинает делегировать ему, чтобы у правила остался один дом, а не два согласованных вручную.
2. **Один диалог вместо двух копий.** Новый `ui/streams/StreamUnavailableDialog` по образцу `StreamRemoveConfirmation` (S1424, тот же довод: деструктивное действие задаёт один вопрос, а не копию вопроса). Параметр `offline` переключает текст и убирает neutral-кнопку «Удалить»; в offline-варианте остаются «Повторить» и «Отмена».
3. **Оба хоста спрашивают сеть в момент отказа.** `PlayerViewModel.onStreamPlaybackFailed` определяет наличие сети, передаёт его в `recordPlayFailure` и в событие `ShowStreamUnavailable(source, offline)`; `StreamsActivity.showStreamUnavailable` делает то же через уже существующий `viewModel.hasNetworkForStream()`.

4. **У отказа остаётся один вход.** Прежний `invoke(id, ok)` писал `OUTCOME_FAIL` в false-ветке безусловно, и после пунктов 1-3 ни один вызывающий не передавал туда `false` - метод оставался ловушкой, воспроизводящей ровно этот дефект для следующего, кто возьмёт очевидное API. Он сужен до `recordPlaySuccess(id)`, а три места успеха (`StreamsViewModel.recordStreamPlaySuccess`, `onSuccess` встроенного аудио в `StreamsActivity`, `PlayerViewModel.recordStreamPlayOk`) переведены на него. Записать отказ теперь можно только через `recordPlayFailure(id, hasNetwork)`, то есть не ответив на вопрос о сети - нельзя.

Вне области: автоматический повтор при возвращении сети, различение «сеть есть, но хост недостижим» пробой конкретного хоста (аналог их `StreamTransmissionProbe`). Здесь закрывается только ложное обвинение канала при полном отсутствии связи.

### 3.3 Owner inputs (Approval gate)

- **UI placement contract:** оба терминальных диалога сохраняют текущее место и способ вызова; меняется только текст и состав кнопок при отсутствии сети - neutral-кнопка «Удалить» не показывается, positive «Повторить» и negative «Отмена» остаются на своих местах.
- **Accessibility:** новый диалог строится через `MaterialAlertDialogBuilder` + `DialogKeyboardDelegate.applyTo`, как `StreamRemoveConfirmation`, поэтому Enter/D-pad подтверждают positive-кнопку так же, как на существующих диалогах потоков.
- **Communication policy:** offline-текст пишется по формуле §2.8 «Network / resource / access error» - объяснение плюс исполнимый шаг («проверьте подключение»), без кода ошибки и без предложения удалить.
- **Localization:** два новых ключа заводятся сразу в EN/RU/UK через `set-android-string.ps1 -Action add`, паритет проверяется `check_strings_localized.ps1`.
- **Validation level:** юнит-тест на предикат исхода плюс проверка на устройстве в авиарежиме на обоих экранах.
- **Owner sign-off:** требуется - меняется поведение деструктивной кнопки и цвет отметки канала.
- **Related tickets:** S1469 (тот же инвариант на пути проб), S1424 (прецедент единственного диалога удаления), S0593 / S0700 (значения `OUTCOME_FAIL` / `OUTCOME_UNKNOWN`).

---

## 4. Проверка

- Юнит: `recordPlayFailure` пишет `FAIL` при `hasNetwork = true` и `UNKNOWN` при `false`; `StreamFrameSnapshotPenaltyTest` остаётся зелёным после делегирования.
- На устройстве, авиарежим включён во время воспроизведения: на полноэкранном плеере и на экране потоков диалог показывает offline-текст, кнопки «Удалить» нет, отметка канала не краснеет.
- На устройстве, сеть есть, а URL заведомо мёртвый: диалог показывает прежний текст, кнопка «Удалить» на месте, отметка канала краснеет.

---

## Phase 01 - Один предикат вины канала

**Status:** ✅ Done
**Depends on:** none - foundation phase
**Steps done:** 3 / 3

### Objective

Домен получает единственное определение «сбой засчитывается каналу», и путь проб начинает читать его оттуда же.

### Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/streams/RecordStreamPlayOutcomeUseCase.kt` | Modified | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamFrameSnapshotManager.kt` | Modified | ≤ 330 |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/streams/RecordStreamPlayOutcomeUseCaseTest.kt` | New | ≤ 120 |

### Steps

#### Step 01.1 - Add the failure-attribution predicate and writer

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/streams/RecordStreamPlayOutcomeUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `suspend fun recordPlayFailure(id: String, hasNetwork: Boolean)` writing `OUTCOME_FAIL` when `hasNetwork` is true and `OUTCOME_UNKNOWN` when it is false. Add `fun isChannelAttributableFailure(hasNetwork: Boolean): Boolean = hasNetwork` to the companion object. Do not change `invoke` or `recordProbe` behaviour.

**Why:**

Both terminal-failure call sites currently write `OUTCOME_FAIL` unconditionally, so an outage reddens every channel the user tried; the domain needs one writer that can express "the failure said nothing about this channel" before either UI host can stop lying about it.

**Verification:**

- `Grep` - `fun recordPlayFailure` matches exactly once in that file.
- `Grep` - `fun isChannelAttributableFailure` matches exactly once in that file.
- `Grep` - `OUTCOME_UNKNOWN` appears in the `recordPlayFailure` body.

**Status:** `[x]` done

---

#### Step 01.2 - Point the capture penalty at the same predicate

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamFrameSnapshotManager.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Rewrite `shouldPenaliseCaptureFailure(ok, hasNetwork)` to return `!ok && RecordStreamPlayOutcomeUseCase.isChannelAttributableFailure(hasNetwork)`. Keep the function `internal` and keep its name, so `StreamFrameSnapshotPenaltyTest` keeps compiling. Update its KDoc to state that the attribution rule now lives in the use case.

**Why:**

S1469 already wrote this rule once for the probe path, and leaving a second hand-copied instance of it in the UI layer is what let the terminal dialog drift away from the probe path in the first place.

**Verification:**

- `Grep` - `isChannelAttributableFailure` present in `StreamFrameSnapshotManager.kt`.
- `Grep` - `internal fun shouldPenaliseCaptureFailure` still matches exactly once.

**Status:** `[x]` done

---

#### Step 01.3 - Unit-test the writer

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/streams/RecordStreamPlayOutcomeUseCaseTest.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add a test class covering `recordPlayFailure`: with `hasNetwork = true` the repository receives `OUTCOME_FAIL`, with `hasNetwork = false` it receives `OUTCOME_UNKNOWN`, and neither case calls `markPlayed` or records a stats event. Use a fake `StreamSourceRepository` and a fake `StatsSink` rather than a mocking framework if the surrounding tests do.

**Why:**

The whole fix rests on which of two strings reaches the row bullet, and that choice is invisible on screen until a user is already offline, so it needs a test that fails loudly rather than an on-device observation nobody repeats.

**Verification:**

- `Glob` - the test file exists.
- `.\a.ps1 fu` - the new test class passes.

**Status:** `[x]` done

---

### Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] `.\a.ps1 fk` passes.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

### Handoff Notes to Next Phase

The domain now answers "was this the channel's fault?"; Phase 02 makes both terminal dialogs ask it.

---

## Phase 02 - Один диалог на оба хоста

**Status:** ✅ Done
**Depends on:** Phase 01
**Steps done:** 4 / 4

### Objective

Оба терминальных диалога заменяются одним объектом, который при отсутствии сети меняет текст и не предлагает удаление, а оба хоста записывают исход через `recordPlayFailure`.

### Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamUnavailableDialog.kt` | New | ≤ 80 |
| `app_v2/src/main/res/values/strings.xml` | Modified | +2 keys |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt` | Modified | ≤ 1255 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt` | Modified | ≤ 905 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerEventHandler.kt` | Modified | ≤ 255 |

> `StreamsActivity.kt` (1251 LOC) and `PlayerViewModel.kt` (898 LOC) are over 500 LOC - take a timestamped backup under `temp/S1509/` before editing each (CLAUDE.md Rule 5).

### Steps

#### Step 02.1 - Add the two offline strings

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru`, `values-uk`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `streams_unavailable_offline_title` and `streams_unavailable_offline_message` across EN/RU/UK in one call each via `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key <key> -En <..> -Ru <..> -Uk <..>`. The message takes the channel title as `%1$s` and follows Communication Policy §2.8 - say the device is offline and name the corrective step, never mention removing the channel. Then run `scripts/check_strings_localized.ps1 -KeyPrefix "streams_unavailable_offline"`.

**Why:**

The offline dialog must say something different from "«%1$s» is not responding. Remove it from your list?", because that sentence blames the channel for the user's own dead link and is the text that makes removal look reasonable.

**Verification:**

- `Grep` - both keys present in each of `values/`, `values-ru/`, `values-uk/strings.xml`.
- `scripts/check_strings_localized.ps1 -KeyPrefix "streams_unavailable_offline"` - exit 0.

**Status:** `[x]` done

---

#### Step 02.2 - Extract the one terminal dialog

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamUnavailableDialog.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add `object StreamUnavailableDialog` with `fun show(activity: AppCompatActivity, channelTitle: String, offline: Boolean, onRetry: () -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit)`. Return early when the activity is finishing or destroyed. When `offline` is true use the offline title/message and attach no neutral button; otherwise use `streams_unavailable_title` / `streams_unavailable_message` and attach the `streams_remove` neutral button wired to `onRemove`. Positive is `retry` -> `onRetry`, negative is `android.R.string.cancel` -> `onDismiss`, and the cancel listener also calls `onDismiss`. Apply `DialogKeyboardDelegate.applyTo` targeting the positive button, mirroring `StreamRemoveConfirmation`.

**Why:**

The remove button exists twice in two files that never consult each other, so a connectivity check added to one of them would leave the other still offering to delete a working channel - the same reason S1424 collapsed the remove confirmation into one object.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamUnavailableDialog.kt` exists.
- `Grep` - `object StreamUnavailableDialog` matches exactly once.
- `Grep` - `setNeutralButton` appears inside a branch guarded by `offline`.

**Status:** `[x]` done

---

#### Step 02.3 - Rewire the streams screen

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> In `showStreamUnavailable`, read `viewModel.hasNetworkForStream()` once into a local, replace the unconditional `viewModel.recordStreamOutcome(source.id, ok = false)` with a call that routes through `recordPlayFailure` carrying that value, and replace the inline builder with `StreamUnavailableDialog.show`. Add the matching `StreamsViewModel` method delegating to `RecordStreamPlayOutcomeUseCase.recordPlayFailure`. Retry keeps calling `inlineAudio.play(source, useBackgroundService = isBackgroundAudioEnabled())`, remove keeps calling `viewModel.onRemove(source)`, dismiss stays a no-op on this screen.

**Why:**

This screen is where the radio path lands, and it is the copy that both reddens the row and offers removal in the same breath, so leaving it on its own inline dialog would keep the reported symptom alive on one of the two reported screens.

**Verification:**

- `Grep` - `StreamUnavailableDialog.show` present in `StreamsActivity.kt`.
- `Grep` - `MaterialAlertDialogBuilder` no longer appears inside `showStreamUnavailable`.
- `Grep` - `recordStreamOutcome(source.id, ok = false)` returns zero hits in `StreamsActivity.kt`.

**Status:** `[x]` done

---

#### Step 02.4 - Rewire the fullscreen player

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerEventHandler.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Inject `NetworkContextAnalyzer` into `PlayerViewModel` by constructor. In `onStreamPlaybackFailed`, capture `hasAnyNetwork()` once, pass it to `recordPlayFailure`, and add an `offline: Boolean` field to `PlayerEvent.ShowStreamUnavailable` carrying its negation. In `PlayerEventHandler.showStreamUnavailable`, take that flag and delegate to `StreamUnavailableDialog.show`, keeping the existing semantics - retry replays the URL, remove calls `removeStreamSource` then finishes, dismiss finishes the activity.

**Why:**

The video and RTSP path reaches the same dialog through a different object graph, and the owner's report named the fullscreen player first, so the fix is not delivered until this host asks the same question the streams screen now asks.

**Verification:**

- `Grep` - `StreamUnavailableDialog.show` present in `PlayerEventHandler.kt`.
- `Grep` - `recordPlayFailure` present in `PlayerViewModel.kt`.
- `Grep` - `val offline` present in the `ShowStreamUnavailable` declaration.
- `.\a.ps1 fk` - exit 0.

**Status:** `[x]` done

---

### Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] `.\a.ps1 fc` passes.
- [ ] `scripts/post-change.ps1` closes green over the whole changed set.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

### Handoff Notes to Next Phase

Final phase - remaining gate is on-device verification in airplane mode on both screens.

---

## Rollback Plan

Revert the phase commits - no schema, no migration, no persisted format changed; `OUTCOME_UNKNOWN` already existed and already rendered.

---

## Last Audit

**Date:** 2026-08-08
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 17 - WARN 0 - FAIL 0 - MANUAL 1 - EXEMPT 1

Evidence: `.\a.ps1 fk` exit 0; `RecordStreamPlayOutcomeUseCaseTest` 6/6; `StreamFrameSnapshotPenaltyTest` 4/4 (S1469 unaffected by the delegation); `check_strings_localized.ps1 -KeyPrefix streams_unavailable_offline` exit 0; `post-change.ps1 -ScopeToFile` PASS (one advisory, a pre-existing over-length Timber line at `PlayerViewModel.kt:964`, untouched by this ticket).

Device run 2026-08-08, emulator-5554 (Android 15): both probe tags fired, one per changed flow, each taking a different branch - `hasNetwork=true` on the streams screen, `hasNetwork=false` on the player.

### Manual / on-device

- [x] Offline mid-playback, fullscreen player: title `No connection`, no Remove button, Cancel + Retry only - verified on-device 2026-08-08.
- [x] Offline failure leaves the bullet amber (`Not played yet`), not red - verified on-device 2026-08-08.
- [x] Online control unchanged: dead host with the link up still offers Remove and still goes red - verified on-device 2026-08-08.
- [ ] Offline mid-playback on the streams screen (inline radio): not reached. With no network the S0711 pre-play gate refuses before the terminal dialog can appear, so this branch needs a live radio station playing first. Both screens now share `StreamUnavailableDialog`, and that screen's `hasNetwork` plumbing was proven by its online probe, so what is unproven is the call-site wiring alone.

### Residual gaps, by design

- "Network is up but this host is unreachable" is still indistinguishable from a live channel: a genuinely dead channel on a working link keeps the removal offer (correct), and a channel behind a captive portal is still blamed (unchanged from before this ticket). The host-level probe that would separate these is the StreamsPlayer `StreamTransmissionProbe` analogue, deliberately out of scope.
- The amber state's accessibility label reads `Not played yet`, which is now sometimes false - a channel that played and then hit an outage lands there. The state is pre-existing (S0700); only its reachability is new. Cosmetic, single string.

### Parked during this ticket

- `S1536 bugfix-background-radio-streamplayed-stat` - `AudioPlaybackService.recordCurrentStreamSuccess` bypasses the use case, so background radio never records `StatsEvent.StreamPlayed`. Surfaced by narrowing the writer; out of scope here.

---

## Revision History

- **2026-08-08** - by `/spec-test-device` (`sdk_gphone64_x86_64`, device: emulator-5554, Android 15 / SDK 35)
  - Scenario: `temp/S1509/mobile_test_scenario_20260808_2309.md` - PASS/FAIL/SKIPPED 9/0/1 - errors in log: induced outage only (UnknownHostException), no crash