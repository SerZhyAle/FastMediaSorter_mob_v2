# Спецификация (compact bugfix): S0980 - Галочка «открывать загруженные файлы» не открывает файлы из Instagram

**Ticket:** S0980
**Status:** Archived
**Priority:** 90
**Date:** 2026-07-10
**Tier:** 3 - Moderate (ad-hoc)

<!-- auto-approved by /spec-all - 2026-07-10 -->

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-10

**Текст:**

ещё драфт чтобы починить галочку "открывать загруженные файлы. файлы загруженные из инстаграм не открываются

---

## 1. Проблема / симптом

Настройка «Открывать скачанное в плеере» (`linkAutoDownloadOpenInPlayer`, строка `link_autodownload_open_in_player_label`) должна открывать только что скачанный по ссылке файл во встроенном плеере. При включённой галочке файлы, скачанные из Instagram, не открываются - показывается только тост «сохранено».

Область: link-download пайплайн (`data/link`, `domain/usecase/link`, `ui/share`). Standard flavor - весь затронутый код в `src/main`.

---

## 2. Корневая причина

Пост-загрузочное открытие в `LinkAutoDownloadResultPresenter.present()` срабатывает только когда `result.openInPlayerUri != null`. Этот URI приходит из `LinkDownloadWriter.WriteResult.destinationUri`, а тот заполняется по-разному:

1. **Сохранение в настроенный ресурс (успех)** - `LinkDownloadWriter` (ветка `FileOperationResult.Success`) возвращает `WriteResult.Saved(.., destinationUri = null)`, хотя реальный путь записанного файла доступен в `FileOperationResult.Success.copiedFilePaths`. `null` пробрасывается в `Result.Saved.openInPlayerUri`, и презентер уходит в `else -> toast(..)`, минуя `launchPlayer`. Галочка - no-op для любой загрузки, попавшей в настроенный ресурс (типовой случай для Instagram, когда задан ресурс назначения).
2. **Fallback в Downloads** - возвращает настоящий MediaStore URI, поэтому этот путь открывается. Отсюда впечатление «из других источников открывается, из Instagram - нет».
3. **Батч (карусель Instagram)** - `Result.BatchCompleted` в презентере никогда не открывает файл: при полном успехе только тост, при частичном - сводка. `BatchSummary.firstSavedUri` уже вычисляется (для content-intent уведомления), но презентером для открытия не используется. Плюс из-за пункта 1 `firstSavedUri` обнуляется, если элементы батча сохранялись в ресурс.

Комментарии S0257 в `LinkAutoDownloadCoordinator` утверждают, что `openInPlayerUri` «unconditionally populated from `destinationUri`» - но writer нарушает этот контракт для ветки `Saved`.

---

## 3. Исправление

Заполнить контракт open-in-player на всех успешных путях сохранения.

**Фаза 01 - writer возвращает реальный URI сохранённого файла.**

- Файл: `app_v2/src/main/java/com/sza/fastmediasorter/data/link/LinkDownloadWriter.kt`, ветка `is FileOperationResult.Success` (около строки 117-119).
- Для **локального** ресурса (`!resource.type.isNetworkResource`) взять реальный путь из `result.copiedFilePaths.firstOrNull()` и вернуть `destinationUri = Uri.fromFile(File(path))`.
- Для **сетевого/облачного** ресурса локального URI нет - оставить `destinationUri = null` (поведение «тост», без попытки открыть удалённый файл в локальном плеере).
- Пустой `copiedFilePaths` -> `null` (безопасная деградация к тосту, без падения).
- Verification: `LinkDownloadWriter.WriteResult.Saved.destinationUri` не `null` для локального ресурса; `assembleStandardDebug` компилируется.

**Фаза 02 - презентер открывает первый файл успешного батча.**

- Файл: `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/LinkAutoDownloadResultPresenter.kt`, ветка `is Result.BatchCompleted`.
- При `failureCount == 0`: если `openInPlayer && summary.firstSavedUri != null` -> `launchPlayer(summary.firstSavedUri)`, иначе прежний тост. Ветка с ошибками (`showBatchSummary`) без изменений.
- Verification: `assembleStandardDebug` компилируется; сборка проходит detekt-гейт.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0223, S0257 (тот же контракт open-in-player; не блокируют).

---

## 4. Проверка

On-device (эмулятор, standard debug):

1. Настроить локальный ресурс назначения и включить «Открывать скачанное в плеере».
2. Скачать по ссылке медиафайл, попадающий в ресурс (прямой .mp4 URL достаточно - Instagram-специфика не требуется для воспроизведения дефекта).
3. Ожидаемо: файл открывается во встроенном плеере, а не только тост.
4. Сравнить с выключенной галочкой: должен показываться тост, плеер не открывается.

Батч: скачать многофайловую ссылку (карусель) при включённой галочке - открывается первый сохранённый файл.

---

## Last Audit

### Manual / on-device

- [!] Local resource + open-in-player ON -> download opens in player (not toast) - INCONCLUSIVE on-device 2026-07-10; `present()` is suppressed for success (MainActivity.kt:299), so the `S0980:` probe and setting-gated auto-open cannot fire via the share flow. See temp/S0980/mobile_test_scenario_20260710_1140.md
- [!] Open-in-player OFF -> toast only - INCONCLUSIVE on-device 2026-07-10; same suppression makes ON vs OFF unobservable in the foreground flow.
- [!] Batch/carousel opens first saved file - INCONCLUSIVE on-device 2026-07-10; presenter batch-open branch runs only inside the suppressed `present()`.
- [!] Logcat `S0980:` probes show branch + uri - INCONCLUSIVE on-device 2026-07-10; 0 probes possible: they live only in `present()`, which never runs for a successful worker-produced result (`notificationShown` hard-coded true, LinkDownloadWorker.kt:139).

Note: the shippable fix is Phase 01 (writer populates `destinationUri`, LinkDownloadWriter.kt:119-125), which feeds the result-notification content-intent (the real open channel). That path was not exercised end-to-end (link-download destination picker unreached on this emulator; no download triggered). Build v2.60.7101.516-DEBUG installed and verified on emulator-5554 (Android 13).

## Revision History

- **2026-07-10** - by `/spec-test-device` (`claude-opus-4-8[1m]`, device: emulator-5554 Android 13)
  - Scenario: temp/S0980/mobile_test_scenario_20260710_1140.md · PASS/FAIL/INCONCLUSIVE 0/0/4 · Errors in log: 0
  - Structural finding: `S0980:` probes + presenter auto-open are unreachable for success results (present() suppressed in MainActivity); contract not exercisable on-device as written.
