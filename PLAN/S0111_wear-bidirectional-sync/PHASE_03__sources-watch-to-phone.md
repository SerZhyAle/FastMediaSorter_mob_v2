# Phase 03 — Sources Watch → Phone

**Strategic spec:** [`../S0111_wear-bidirectional-sync.md`](../S0111_wear-bidirectional-sync.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 07
**Steps done:** 9 / 9
**Started:** 2026-05-08
**Completed:** 2026-05-08

---

## Objective

Watch can push its locally configured network sources to the phone with a single "Send to phone" action. Phone's `PhoneWearListenerService` receives the message, deduplicates, and surfaces a badge on `WearSyncSettingsFragment` for the user to accept or dismiss the import. The import UX type (badge vs. notification) must be decided via Blocker 4 before this phase starts.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] INDEX.md Blocker 4 (import UX decision) is checked.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/WearSourcesExportPayload.kt` | New | ≤ 25 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/domain/usecase/ExportSourcesUseCase.kt` | New | ≤ 80 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/ui/network/viewmodel/NetworkSourcesViewModel.kt` | Modified | ≤ 190 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/ui/network/NetworkSourcesScreen.kt` | Modified | ≤ 390 |
| `wear/src/main/res/values/strings.xml` | Modified | — |
| `wear/src/main/res/values-ru/strings.xml` | Modified | — |
| `wear/src/main/res/values-uk/strings.xml` | Modified | — |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/WearSourcesExportPayload.kt` | New | ≤ 25 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ImportWatchSourcesUseCase.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/service/PhoneWearListenerService.kt` | Modified | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/WearSyncViewModel.kt` | Modified | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/WearSyncSettingsFragment.kt` | Modified | ≤ 270 |
| `app_v2/src/main/res/values/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | — |

---

## Steps

### Step 3.1 — Create `WearSourcesExportPayload` on watch side

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/WearSourcesExportPayload.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create the listed file in package `com.sza.fastmediasorter.wear.domain.model`. Declare `data class WearSourcesExportPayload(val sources: List<WearNetworkSourcePayload>, val watchName: String)`. `WearNetworkSourcePayload` is already defined in `wear/…/domain/model/WearSyncPayload.kt` — import from there.

**Verification:**

- `Glob` — `wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/WearSourcesExportPayload.kt` exists.
- `Grep` — `data class WearSourcesExportPayload` matches.
- `Grep` — `WearNetworkSourcePayload` imported.

**Status:** `[x] done`

**Step Log:**
- 2026-05-08 — Verification 3/3 PASS. Files: wear/.../domain/model/WearSourcesExportPayload.kt (+8 LOC). Dev log recorded.

---

### Step 3.2 — Create `WearSourcesExportPayload` mirror on phone side

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/WearSourcesExportPayload.kt`
**Depends on:** — start of phase (parallel with 3.1)

**Prompt for developer:**

> Create the listed file in package `com.sza.fastmediasorter.domain.model`. Declare `data class WearSourcesExportPayload(val sources: List<WearNetworkSourcePayload>, val watchName: String)`. `WearNetworkSourcePayload` is already defined in `app_v2/…/domain/model/WearSyncPayload.kt`.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/WearSourcesExportPayload.kt` exists.
- `Grep` — `data class WearSourcesExportPayload` matches.

**Status:** `[x] done`

**Step Log:**
- 2026-05-08 — Verification 2/2 PASS. Files: app_v2/.../domain/model/WearSourcesExportPayload.kt (+8 LOC). Dev log recorded.

---

### Step 3.3 — Create `ExportSourcesUseCase` on watch

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/domain/usecase/ExportSourcesUseCase.kt`
**Depends on:** Step 3.1

**Prompt for developer:**

> Create the listed file. Declare `class ExportSourcesUseCase @Inject constructor(private val networkSourceRepository: NetworkSourceRepository, private val context: Context, private val gson: Gson)`.
>
> Implement `suspend operator fun invoke(): Result<Int>` using `runCatching`:
> 1. Load all sources via `networkSourceRepository.getAllSources()`.
> 2. Map each `NetworkSource` to `WearNetworkSourcePayload` (use fields: id, type.name, name, server, port, username, password, shareName, basePath, domain, sshPrivateKey).
> 3. Build `WearSourcesExportPayload(sources = payloads, watchName = android.os.Build.MODEL)`.
> 4. Build `WearEventEnvelope(eventType = WearDataLayerPaths.EVENT_SOURCES_EXPORT, sentAt = System.currentTimeMillis(), data = gson.toJson(payload).toByteArray())`.
> 5. Get connected nodes via `Wearable.getNodeClient(context).connectedNodes.await()`; if empty, error("No phone connected").
> 6. For each node: `Wearable.getMessageClient(context).sendMessage(node.id, WearDataLayerPaths.SOURCES_EXPORT, envelopeBytes).await()`.
> 7. Return `Result.success(payloads.size)`.
>
> Add `Timber.d("S0111: ExportSourcesUseCase — exporting ${payloads.size} sources to phone")` before sending.

**Verification:**

- `Glob` — `wear/src/main/java/com/sza/fastmediasorter/wear/domain/usecase/ExportSourcesUseCase.kt` exists.
- `Grep` — `class ExportSourcesUseCase` matches.
- `Grep` — `Timber.d("S0111:` present.
- `Grep` — `WearDataLayerPaths.SOURCES_EXPORT` present.
- `Grep` — `Log\.d\(` returns zero hits in that file.

**Status:** `[x] done`

**Step Log:**
- 2026-05-08 — Verification 5/5 PASS. Files: wear/.../domain/usecase/ExportSourcesUseCase.kt (+63 LOC). Dev log recorded.

---

### Step 3.4 — Add export action to `NetworkSourcesViewModel`

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/ui/network/viewmodel/NetworkSourcesViewModel.kt`
**Depends on:** Step 3.3

**Prompt for developer:**

> Inject `ExportSourcesUseCase` into `NetworkSourcesViewModel`. Add a new sealed class `ExportState { Idle, Exporting, Success(count: Int), Error(message: String) }` and a corresponding `_exportState: MutableStateFlow<ExportState>` with public `val exportState`.
>
> Add `fun exportToPhone()`: set state `Exporting`, launch coroutine, call `exportSourcesUseCase()`, on success set `Success(count)`, on failure set `Error`.

**Verification:**

- `Grep` — `fun exportToPhone` present in `NetworkSourcesViewModel.kt`.
- `Grep` — `ExportState` present.
- `Grep` — `ExportSourcesUseCase` injected.
- `Grep` — `Log\.d\(` returns zero hits in that file.

**Status:** `[x] done`

**Step Log:**
- 2026-05-08 — Verification 4/4 PASS. Files: wear/.../ui/network/viewmodel/NetworkSourcesViewModel.kt (+24 LOC). Dev log recorded.

---

### Step 3.5 — Add "Send to phone" chip to `NetworkSourcesScreen`

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/ui/network/NetworkSourcesScreen.kt`
**Depends on:** Step 3.4

**Prompt for developer:**

> In `NetworkSourcesScreen`, read `exportState` from the ViewModel. Add a "Send to phone" `Chip` to the existing `SourcesListContent` composable (place it after the existing "Sync from phone" chip). When tapped: call `viewModel.exportToPhone()`. Show a circular progress indicator when `ExportState.Exporting`. Show a success toast-style text when `ExportState.Success`. Use `stringResource(R.string.wear_export_to_phone)` (added in Step 3.7).

**Verification:**

- `Grep` — `exportToPhone` call present in `NetworkSourcesScreen.kt`.
- `Grep` — `ExportState` imported.
- `Grep` — `wear_export_to_phone` string key referenced.

**Status:** `[x] done`

**Step Log:**
- 2026-05-08 — Verification 3/3 PASS. Files: wear/.../ui/network/NetworkSourcesScreen.kt (+42 LOC, 394 total — 4 over spec table budget, within 1500 hard limit). Dev log recorded.

---

### Step 3.6 — Create `ImportWatchSourcesUseCase` on phone

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ImportWatchSourcesUseCase.kt`
**Depends on:** Step 3.2

**Prompt for developer:**

> Create the listed file. Declare `class ImportWatchSourcesUseCase @Inject constructor(private val resourceRepository: ResourceRepository, private val credentialsRepository: NetworkCredentialsRepository, private val gson: Gson)`.
>
> Implement `suspend operator fun invoke(payload: WearSourcesExportPayload): Result<ImportWatchResult>` where `data class ImportWatchResult(val added: Int, val skipped: Int)`.
>
> For each source in `payload.sources`:
> - Check for duplicate: query `resourceRepository` for a resource where `server == source.server && port == source.port && type.name == source.type`. If found, increment `skipped` and continue.
> - Otherwise create a new `ResourceEntity` (or domain model) from the payload fields and call `resourceRepository.addResource(...)`. Increment `added`.
>
> Add `Timber.d("S0111: ImportWatchSourcesUseCase — added=${added} skipped=${skipped}")` before returning.
>
> Note: inspect `ResourceRepository` interface to confirm the `addResource` method name and parameter type before coding. Do not guess the signature.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ImportWatchSourcesUseCase.kt` exists.
- `Grep` — `class ImportWatchSourcesUseCase` matches.
- `Grep` — `Timber.d("S0111:` present.
- `Grep` — `data class ImportWatchResult` present in same file.

**Status:** `[x] done`

**Step Log:**
- 2026-05-08 — Verification 4/4 PASS. Files: app_v2/.../domain/usecase/ImportWatchSourcesUseCase.kt (+67 LOC). Dev log recorded.

---

### Step 3.7 — Add localized strings for sources export

**Files:** `wear/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`, `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** Steps 3.5, 3.8

**Prompt for developer:**

> Add to **watch** strings (all three locales):
> - `wear_export_to_phone` — "Send to phone" / "Отправить на телефон" / "Надіслати на телефон"
> - `wear_export_success` — "Sources sent" / "Источники отправлены" / "Джерела надіслані"
>
> Add to **phone** strings (all three locales):
> - `wear_import_pending_title` — "Watch sources received" / "Получены источники часов" / "Отримано джерела годинника"
> - `wear_import_pending_desc` — "%1$d sources from %2$s" / "%1$d источников от %2$s" / "%1$d джерел від %2$s"
> - `wear_import_accept` — "Import" / "Импортировать" / "Імпортувати"
> - `wear_import_dismiss` — "Dismiss" / "Отклонить" / "Відхилити"

**Verification:**

- `Grep` — `wear_export_to_phone` present in `wear/src/main/res/values/strings.xml`.
- `Grep` — `wear_export_to_phone` present in `wear/src/main/res/values-ru/strings.xml`.
- `Grep` — `wear_export_to_phone` present in `wear/src/main/res/values-uk/strings.xml`.
- `Grep` — `wear_import_pending_title` present in `app_v2/src/main/res/values/strings.xml`.
- `Grep` — `wear_import_pending_title` present in `app_v2/src/main/res/values-ru/strings.xml`.
- `Grep` — `wear_import_pending_title` present in `app_v2/src/main/res/values-uk/strings.xml`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-08 — Verification 6/6 PASS. Files: wear/values/{,ru/,uk/}strings.xml (+2 keys each), app_v2/values/{,ru/,uk/}strings.xml (+4 keys each). Dev log recorded.

---

### Step 3.8 — Wire import handler in `PhoneWearListenerService`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/service/PhoneWearListenerService.kt`
**Depends on:** Step 3.6

**Prompt for developer:**

> Inject `ImportWatchSourcesUseCase` and `Gson` into `PhoneWearListenerService`. Replace the stub `handleSourcesExport(data: ByteArray)` with a real implementation:
> 1. Deserialize `data` → `WearEventEnvelope` → inner `data` bytes → `WearSourcesExportPayload` via Gson.
> 2. Emit `WearSyncEvents.watchSourcesReceivedFlow.emit(payload)` (add this new `MutableSharedFlow<WearSourcesExportPayload>` to `WearSyncEvents` object).
>
> Do not call `ImportWatchSourcesUseCase` here — the ViewModel calls it after user confirmation.

**Verification:**

- `Grep` — `handleSourcesExport` is non-stub (does not contain "not yet implemented") in `PhoneWearListenerService.kt`.
- `Grep` — `watchSourcesReceivedFlow` present in `WearSyncEvents` object.
- `Grep` — `WearSourcesExportPayload` imported in `PhoneWearListenerService.kt`.
- `Grep` — `Log\.d\(` returns zero hits in that file.

**Status:** `[x] done`

**Step Log:**
- 2026-05-08 — Verification 4/4 PASS. Files: app_v2/.../service/PhoneWearListenerService.kt (+20 LOC). Dev log recorded.

---

### Step 3.9 — Add pending import card to `WearSyncSettingsFragment`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/WearSyncViewModel.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/WearSyncSettingsFragment.kt`
**Depends on:** Steps 3.7, 3.8

**Prompt for developer:**

> In `WearSyncViewModel`: observe `WearSyncEvents.watchSourcesReceivedFlow`; store the pending payload in `_pendingWatchSources: MutableStateFlow<WearSourcesExportPayload?>`; expose as `val pendingWatchSources`. Add `fun acceptWatchImport()`: call `importWatchSourcesUseCase(pendingWatchSources.value!!)` in a coroutine, clear `_pendingWatchSources` on completion. Add `fun dismissWatchImport()`: clear `_pendingWatchSources`.
>
> In `WearSyncScreen` composable: observe `pendingWatchSources`. When non-null, show a `Card` with the import summary (`wear_import_pending_title`, `wear_import_pending_desc` with count and watch name), and two buttons: "Import" → `viewModel.acceptWatchImport()`, "Dismiss" → `viewModel.dismissWatchImport()`.

**Verification:**

- `Grep` — `pendingWatchSources` present in `WearSyncViewModel.kt`.
- `Grep` — `fun acceptWatchImport` present.
- `Grep` — `fun dismissWatchImport` present.
- `Grep` — `wear_import_pending_title` referenced in `WearSyncSettingsFragment.kt`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-08 — Verification 4/4 PASS. Files: WearSyncViewModel.kt (+29 LOC, 138 total), WearSyncSettingsFragment.kt (+37 LOC, 237 total). Dev log recorded.

---

## Phase Done Criteria

- [x] Every Step 03.* above is `[x] done`.
- [x] Project compiles — both BUILD SUCCESSFUL.
- [x] `Grep` for `TODO(phase-03)` returns zero hits in .kt files.
- [x] String locale audit: wear_export EN/RU/UK ✓, wear_import 4/4 keys OK in EN/RU/UK.
- [x] Dev log entry added for every file in "Files Touched".
- [x] Catalogs regenerated — app_v2: 956 records, wear: 60 records.

---

## Handoff Notes to Next Phase

- Watch `NetworkSourcesScreen` exposes "Send to phone" action.
- Phone stores received sources in `pendingWatchSources` until user accepts or dismisses.
- `ImportWatchSourcesUseCase` deduplicates by server+port+type.

---

## Rollback Plan

Revert phase commit(s). No DB schema change. Phone resource list unchanged if user never accepted import.
