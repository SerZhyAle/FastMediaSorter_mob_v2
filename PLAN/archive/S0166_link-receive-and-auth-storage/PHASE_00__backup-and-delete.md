# PHASE 00 — Backup broken code and delete from source

**Status:** ✅ Done  
**Backup location:** `temp/backup_S0166_link_auth_20260511/`

---

## Files deleted

### `data/link/auth/`
- `AccountNameHintExtractor.kt` — broken account name scraping from social DOM
- `KnownAuthResources.kt` — social host registry (to be rewritten per spec §2 Шаг 0)

### `data/link/cookie/`
- `EncryptedCookieStore.kt` — broken encrypted cookie storage (to be rewritten per spec §3)
- `LinkDownloadCookieJar.kt` — broken cookie injection into OkHttp/WebView
- `LinkDownloadSessionContext.kt` — broken session context threading

### `data/link/` (extraction strategy layer — to be rewritten per spec §2 Шаги 2–2a)
- `HtmlMediaCandidate.kt` — candidate model that did not distinguish real media from og:image
- `HtmlPageExtractionStrategy.kt` — HTML strategy that treated og:image as valid content
- `InvisibleWebViewExtractionStrategy.kt` — invisible WebView that never applied stored cookies
- `CandidateSelectionPolicy.kt` — candidate ranking that picked previews as success

### `domain/usecase/link/`
- `LinkAutoDownloadCoordinator.kt` — broken coordinator (wrong session check order, missing social host gate)
- `LinkExtractionRegistry.kt` — strategy registry with broken priority order
- `UrlExtractionStrategy.kt` — strategy interface (rebuilt in Phase 02)

### `domain/repository/`
- `AuthSessionRepository.kt` — auth session interface (rebuilt in Phase 01)

### `data/repository/`
- `AuthSessionRepositoryImpl.kt` — auth session implementation (rebuilt in Phase 01)

### `ui/share/auth/`
- `WebViewAuthDialogFragment.kt` — auth dialog that appeared at wrong times
- `WebViewAuthViewModel.kt` — broken session persistence after browser login

### `ui/share/helpers/`
- `AccountSelectionManager.kt` — broken multi-account picker

### `ui/share/`
- `LinkAutoDownloadResultPresenter.kt` — presenter that reported og:image download as success

### `ui/settings/auth/`
- `AuthAccountGroupAdapter.kt`
- `AuthAccountLabels.kt`
- `AuthSessionsActivity.kt`
- `AuthSessionsListFragment.kt`
- `AuthSessionsListViewModel.kt`

### `di/`
- `LinkDownloadModule.kt` — Hilt module for all deleted components (rebuilt in Phase 05)

### `res/layout/`
- `activity_auth_sessions.xml`
- `dialog_webview_auth.xml`
- `fragment_auth_sessions_list.xml`
- `item_auth_account.xml`
- `item_auth_host_group.xml`
- `item_auth_session.xml`

### `test/` — unit tests for deleted code
- `AuthSessionsListViewModelTest.kt`
- `LinkAutoDownloadResultPresenterTest.kt`
- `LinkAutoDownloadCoordinatorTest.kt`
- `LinkExtractionRegistryTest.kt`
- `LinkDownloadTraceTest.kt`

---

## Files NOT deleted (kept — general infra)

See `INDEX.md` → "Files KEPT" table.

---

## Notes

- `ReceiveShareActivity.kt` is kept but will be gutted in Phase 03 — its current intent-handling code references deleted classes and will not compile.
- `LinkDownloadWorker.kt` is kept — its core download loop is valid; auth-aware dispatch will be rewired in Phase 03.
- Build is intentionally broken after this phase. Resume at Phase 01.
