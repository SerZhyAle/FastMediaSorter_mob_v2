# Phase 04 - QR share domain + event

**Strategic spec:** [`../S1039_share-resource-fmscfg.md`](../S1039_share-resource-fmscfg.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 03
**Blocks:** Phase 05
**Steps done:** 0 / 4
**Started:** -
**Completed:** -

---

## Objective

Produce the QR payload in the domain layer and route it to the display screen: `exportQrPayload` on the use case, a `ShowCompanionQr` event, a ViewModel method, and the event-handler launch. No dialog/menu change yet (Phase 05).

---

## Prerequisites

- [ ] Phase 01 ✅ Done (`serializeCompressed`).
- [ ] Phase 03 ✅ Done (`CompanionQrShareActivity.createIntent`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/companion/ExportCompanionConfigUseCase.kt` | Modified | ≤ 175 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainViewModel.kt` | Modified | ≤ +18 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainEventHandler.kt` | Modified | ≤ +8 |

---

## Steps

### Step 04.1 - Extract DTO builder, add `exportQrPayload`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/companion/ExportCompanionConfigUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Refactor to remove duplication before adding the QR path (avoids the S1029 duplicate-domain-logic smell). Extract the credential fetch + `CompanionConfigDto` construction (current body of `invoke`, the path-parse through the `CompanionConfigDto(..)` build) into `private suspend fun buildConfigDto(resource: MediaResource, includePassword: Boolean): CompanionConfigDto`, throwing `IllegalArgumentException` / `IllegalStateException` on the existing failure conditions instead of returning `Result.failure`. Keep `invoke(resource, includePassword): Result<File>` behavior identical: wrap `buildConfigDto` + `writeConfig(resource.name, serializer.serialize(dto))` in the existing `withContext(ioDispatcher)` + try/catch -> Result. Add `suspend fun exportQrPayload(resource: MediaResource, includePassword: Boolean): Result<CompanionQrExport>` that wraps `buildConfigDto` + `serializer.serializeCompressed(dto)` the same way and returns `CompanionQrExport(payload = compressed, passwordIncluded = !dto.password.isNullOrBlank())`. Add `data class CompanionQrExport(val payload: String, val passwordIncluded: Boolean)` (nested in the use case). Do not change `buildRoot`, `writeConfig`, or `isoTimestamp`.

**Verification:**

- `Grep` - `suspend fun exportQrPayload` matches exactly once.
- `Grep` - `private suspend fun buildConfigDto` matches exactly once.
- `Grep` - `serializeCompressed` referenced in the use case.
- `Grep` - `data class CompanionQrExport` present.
- `Grep` - `serializer.serialize(` still referenced (file path unchanged).

**Status:** `[ ]` not done

---

### Step 04.2 - Add the `ShowCompanionQr` event

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainViewModel.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> In the `sealed class MainEvent` (near the existing `ShareCompanionConfigFile`), add `data class ShowCompanionQr(val payload: String, val resourceName: String, val passwordIncluded: Boolean) : MainEvent()`.

**Verification:**

- `Grep` - `data class ShowCompanionQr` matches exactly once in `MainViewModel.kt`.

**Status:** `[ ]` not done

---

### Step 04.3 - Add `shareSftpResourceConfigAsQr`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainViewModel.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Next to `shareSftpResourceConfig`, add `fun shareSftpResourceConfigAsQr(resource: MediaResource, includePassword: Boolean)` that launches on `viewModelScope`, calls `exportCompanionConfigUseCase.exportQrPayload(resource, includePassword)`, and folds: onSuccess -> `sendEvent(MainEvent.ShowCompanionQr(export.payload, resource.name, export.passwordIncluded))`; onFailure -> `Timber.e(e, "SFTP QR export failed")` then `sendEvent(MainEvent.ShowResourceMessage(R.string.sftp_share_export_failed))`. Reuse the existing `R.string.sftp_share_export_failed` (no new string).

**Verification:**

- `Grep` - `fun shareSftpResourceConfigAsQr` matches exactly once.
- `Grep` - `exportQrPayload` referenced in `MainViewModel.kt`.

**Status:** `[ ]` not done

---

### Step 04.4 - Handle `ShowCompanionQr` in the event dispatcher

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainEventHandler.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> In `handle(event: MainEvent)` `when`, add `is MainEvent.ShowCompanionQr -> activity.startActivity(CompanionQrShareActivity.createIntent(activity, event.payload, event.resourceName, event.passwordIncluded))`. Import `com.sza.fastmediasorter.ui.companionimport.qr.CompanionQrShareActivity`. The `when` must stay exhaustive without an `else`.

**Verification:**

- `Grep` - `is MainEvent.ShowCompanionQr ->` present in `MainEventHandler.kt`.
- `Grep` - `CompanionQrShareActivity` imported in `MainEventHandler.kt`.
- Project compiles - run `/build` (proves the `when` is exhaustive).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

`MainViewModel.shareSftpResourceConfigAsQr(resource, includePassword)` exists but is not yet called - Phase 05 routes the dialog's "Show QR" choice to it. The event/handler round-trip to `CompanionQrShareActivity` is complete.

---

## Rollback Plan

Revert the phase commit - the added event/method/handler case are self-contained; the use-case refactor is behavior-preserving (revert restores the inline DTO build).
