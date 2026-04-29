# S0017 — Tactical Spec: SMB Scanner Metadata Exception Observability

**Ticket:** S0017  
**Status:** Tactical  
**Strategic spec:** `PLAN/S0017_bugfix-smb-scanner-metadata-exception.md`  
**Created:** 2026-04-28

## Goal (Russian)

Сделать ошибки извлечения метаданных при SMB-сканировании видимыми в production-логах (W-уровень),
добавить счётчик в поток сканирования и отобразить ненавязчивое предупреждение в UI,
если хотя бы один файл не получил метаданные. Устранить дублирующийся системный шум `getFrameAtTime`.

## Phases

| # | File | Status |
|---|------|--------|
| 01 | [PHASE_01__log-level-null-frame.md](PHASE_01__log-level-null-frame.md) | [x] |
| 02 | [PHASE_02__error-counter-smb-scanner.md](PHASE_02__error-counter-smb-scanner.md) | [x] |
| 03 | [PHASE_03__thread-error-count-ui.md](PHASE_03__thread-error-count-ui.md) | [x] |
| 04 | [PHASE_04__ui-strings.md](PHASE_04__ui-strings.md) | [x] |

## Key files

| File | Role |
|------|------|
| `app_v2/.../data/network/SmbMediaScanner.kt` | Metadata extraction; error tracking |
| `app_v2/.../data/network/glide/NetworkVideoFrameDecoder.kt` | `getFrameAtTime` null handling |
| `app_v2/.../domain/usecase/ScanProgressCallback.kt` | New `onMetadataErrors` method |
| `app_v2/.../ui/browse/loading/BrowseLoadingManager.kt` | Propagates error count to ViewModel |
| `app_v2/.../ui/browse/BrowseEvent.kt` | New `ShowMetadataWarning` event |
| `app_v2/.../ui/browse/BrowseViewModel.kt` | Fires the event |
| `app_v2/src/main/res/values/strings.xml` + `-ru` + `-uk` | Localized warning string |

## Acceptance criteria (strategic §11 recap)

1. Logcat: failed metadata extraction → level W (not V).
2. `ScanProgressCallback.onMetadataErrors(N)` fires with N > 0 when errors occurred.
3. UI shows `BrowseEvent.ShowMetadataWarning(count)` snackbar when count > 0.
4. Files with failed metadata remain in the list and are playable/sortable.
5. `getFrameAtTime` not called redundantly after first null return.
