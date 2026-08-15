# S0711 - Ранний мягкий отказ при отсутствии сети для удалённых ресурсов

**Ticket:** S0711
**Status:** Archived
**Priority:** 90
**Date:** 2026-06-26
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - request 2026-06-26

> **Scope:** COMPACT (strategic goal + inline phases). Simple-path spec per `/spec-all` complexity assessment.

---

## 0. Захваченный материал (inbox)

> Сырой захват. Сохранён дословно.

**Захвачено:** 2026-06-26

**Текст:**

Нет вайфай - отказывать в SMB соединениях мяшко, а не пытаться их открыть
нет ни вайфай ни мобильной сети - отказывать в SFT, FTP, Cloud, Тренсляциях - без попытки их достичь

**Контекст:** пользователь хочет ранний отказ до сетевой попытки, если нужный тип транспорта заведомо недоступен. Для SMB достаточно отсутствия Wi-Fi; для SFTP/FTP/Cloud/Трансляций - отсутствия и Wi-Fi, и мобильной сети.

---

## 1. Цель

Не пытаться открывать удалённый ресурс, когда нужного транспорта заведомо нет, а давать быстрый мягкий отказ вместо долгого спиннера и бесполезной ошибки соединения по таймауту.

Исследование кодовой базы показало, что для SMB, SFTP, FTP и Cloud этот ранний отказ **уже реализован**: синхронный `NetworkReachabilityGate` срабатывает до открытия сокета (SMB требует Wi-Fi/Ethernet -> `WifiRequiredException`; SFTP/FTP/Cloud требуют любой активный транспорт -> `NetworkConnectionLostException`). Это ровно соответствует целям захвата. Единственный незакрытый путь - воспроизведение Трансляций (Streams): тап по каналу бьёт прямо в ExoPlayer/`PlayerActivity` без какой-либо проверки сети и ждёт таймаут.

S0711 закрывает этот пробел: добавляет ранний сетевой precheck на пути воспроизведения Streams, оставляя уже корректные SMB/SFTP/FTP/Cloud без изменений.

---

## 2. Объём

В объёме:

- Ранний отказ на пути воспроизведения Streams (тап по плитке/строке и запуск по URL-шорткату), когда нет ни одного активного транспорта.
- Мягкое сообщение через существующий канал `StreamsEvent.Message` / Toast.

Вне объёма (non-goals):

- SMB/SFTP/FTP/Cloud - уже закрыты `NetworkReachabilityGate`; только регресс-проверка, без правок кода.
- Изменение политики SFTP/FTP по сотовой сети: они намеренно работают через cellular, отказ только при полном отсутствии сети (цель захвата: "нет ни Wi-Fi, ни мобильной").
- Эвристики качества связи: VPN-only, captive portal, USB-tether, частично-доступный интернет. `NetworkContextAnalyzer.hasAnyNetwork()` учитывает Wi-Fi/Cellular/Ethernet; расширение транспортов - отдельная задача.
- UI-precheck на стороне browse (список ресурсов): существующий гейт уже срабатывает до сокета, длинного спиннера там нет.

---

## 3. Чёрный ящик (наблюдаемое поведение)

- Тап по каналу Streams при полном отсутствии сети -> мягкое сообщение, ExoPlayer/`PlayerActivity` не запускаются.
- Повторный тап по уже играющему inline-аудио (toggle-off / стоп) работает и без сети - стоп не должен блокироваться precheck.
- Запуск Streams по URL-шорткату (`playByUrl`) проходит тот же гейт (оба пути сходятся в `StreamsActivity.onPlay()`).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0522 (fallback-save-unavailable-resource - реактивный fallback при недоступном ресурсе), S0624 (bugfix-sftp-scan-hang-network - зависание SFTP при переключении сети)
- **UI surface:** новое мягкое сообщение отказа при попытке воспроизвести Streams офлайн; доставка через существующий Toast / `StreamsEvent.Message`, без нового экрана или диалога.
- **Flavor scope:** правки ограничены Streams-UI (`StreamsActivity` / `StreamsViewModel`), которые компилируются только при `BuildConfig.SUPPORT_STREAMS = true` (standard, noLegal, legacy); guard в `src/main` не нужен.

---

## Phase 01 - Streams play-path network precheck

**Goal:** refuse stream playback fast when no network transport is active; reuse the injected `NetworkContextAnalyzer` and the existing message channel.

**Steps:**

1. Add string key `streams_error_no_network` across EN/RU/UK via `scripts/utils/set-android-string.ps1 -Action add`.
   - EN: `No network. Connect to Wi-Fi or mobile data to play streams.`
   - RU: `Нет сети. Подключитесь к Wi-Fi или мобильной сети, чтобы воспроизводить трансляции.`
   - UK: `Немає мережі. Підключіться до Wi-Fi або мобільної мережі, щоб відтворювати трансляції.`

2. In `StreamsViewModel`, expose a synchronous reachability snapshot delegating to the already-injected analyzer:
   `fun hasNetworkForStream(): Boolean = networkContextAnalyzer.hasAnyNetwork()` with KDoc referencing S0711. Keeps the network read out of the Activity.

3. In `StreamsActivity.onPlay(source)`, restructure so the audio toggle-off early-return runs first (stop allowed offline), then insert the precheck before any actual start:
   - `if (!viewModel.hasNetworkForStream()) { Toast streams_error_no_network; return }`
   - then the existing inline-audio start and `PlayerActivity` launch branches.
   This single guard covers both entry points because the shortcut path (`playByUrl` -> `StreamsEvent.PlayRequested`) also routes through `onPlay`.

**Verification:**

- `Grep -n "fun hasNetworkForStream"` in `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsViewModel.kt` -> exactly 1 match.
- `Grep -n "hasNetworkForStream"` in `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt` -> >= 1 match.
- `Grep "streams_error_no_network"` in `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml` -> 1 each.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "streams_error_no_network"` -> exit 0.
- `.\a.ps1 fk` (standard debug Kotlin compile) -> BUILD SUCCESSFUL.

---

## Phase 02 - Regression guard: SMB/SFTP/FTP/Cloud already gated (no code change)

**Goal:** lock in that goals for SMB/SFTP/FTP/Cloud are pre-satisfied by `NetworkReachabilityGate`; document so a future edit does not silently remove the gate.

**Steps:**

- No edits. Confirm the pre-existing synchronous gate calls remain in place.

**Verification:**

- `Grep -n "requireWifi(\"SMB\")"` in `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt` -> 1 match (SMB Wi-Fi gate).
- `Grep -n "requireAnyNetwork(" app_v2/src/main/java` -> matches in `SftpClient`, `FtpClient`, `GoogleDriveRestClient`, `DropboxClient`, `OneDriveRestClient`.
- `NetworkReachabilityGate.requireWifi` throws `WifiRequiredException` after `hasWifi()` check; `requireAnyNetwork` throws `NetworkConnectionLostException` after `hasAnyNetwork()` check.

---

## Phase Done Criteria

- Tapping a Streams channel with no active transport shows the soft message and starts neither ExoPlayer nor `PlayerActivity`.
- Toggling off an already-playing inline stream still works with no network.
- `streams_error_no_network` present and localized in EN/RU/UK.
- Standard debug compiles; no new lint warnings in `StreamsActivity` / `StreamsViewModel`.
- SMB/SFTP/FTP/Cloud gate calls unchanged and still present.

---

## Last Audit

**2026-06-26 - `/spec-all` device-test (emulator-5554, standard debug v2.60.6261.106) - Verified.**

- Phase 01 implemented: `StreamsViewModel.hasNetworkForStream()` delegates to `NetworkContextAnalyzer.hasAnyNetwork()`; `StreamsActivity.onPlay()` gates after the audio toggle-off return and before the start branches; string `streams_error_no_network` added EN/RU/UK (parity OK).
- On-device offline path (Wi-Fi+data disabled, `Active default network: none`): tapping a video channel logged `S0711: stream play refused - no active network transport`, showed the soft message, and did NOT open `PlayerActivity` (still on the Streams grid). Precheck fires.
- On-device online path (network restored): tapping the same channel opened `PlayerActivity` (`topResumedActivity = PlayerActivity`), no refusal - the guard does not block working playback.
- Phase 02 regression: `requireWifi("SMB")` plus `requireAnyNetwork` calls for SFTP/FTP/Cloud unchanged and present.
- Debug tag `Timber.d("S0711: ..")` removed on transition out of `BlockNeedUserTest`.

## Revision History

- **2026-06-26** - by `/spec-update` (`claude-opus-4.8`, focus: all)
  - Draft skeleton review: clean, 0 applied / 0 proposed.
- **2026-06-26** - by `/spec-all` (`claude-opus-4.8`, Simple path)
  - Research-driven rewrite to compact spec. Scope reduced: SMB/SFTP/FTP/Cloud already satisfied by `NetworkReachabilityGate`; only Streams play-path precheck is new work. Added §3.3 Owner inputs, Phase 01/02, Phase Done Criteria.
- **2026-06-26** - by `/spec-all` (`claude-opus-4.8`, finalization)
  - Implemented Phase 01, device-tested PASS (offline refusal + online playback), removed debug tag, status -> Verified.

<!-- auto-approved by /spec-all - 2026-06-26 -->
