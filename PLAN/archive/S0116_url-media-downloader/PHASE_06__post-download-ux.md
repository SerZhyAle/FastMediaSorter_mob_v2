# Phase 06 — Post-download UX (Pillar M): toast vs auto-open

**Strategic spec:** [`../S0116_url-media-downloader.md`](../S0116_url-media-downloader.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03, 05
**Blocks:** Phase 07
**Steps done:** 5 / 5
**Started:** 2026-05-08
**Completed:** 2026-05-08

---

## Objective

Centralize final post-download presentation in `LinkAutoDownloadResultPresenter`: streaming-origin successes still project to existing `Result.Saved` / `Result.FellBackToDownloads`, while the new direct failure variants from Phases 03 and 05 (`Failed.DrmBlocked`, `Failed.StreamingDisabled`, `Failed.MuxFailed`, `Failed.AuthRequired`) respect `linkAutoDownloadOpenInPlayer`. Add EN/RU/UK strings under `s0116_toast_*` where new copy is actually needed. No new settings UI and no re-implementation of the Phase 05 retry loop.

---

## Prerequisites

- [ ] Phase 03 ✅ Done (streaming-origin downloads now project to existing `Result.Saved` / `Result.FellBackToDownloads`, plus `Result.Failed.DrmBlocked`, `Result.Failed.StreamingDisabled`, and `Result.Failed.MuxFailed`).
- [ ] Phase 05 ✅ Done (`Result.Failed.AuthRequired` plus the temporary activity-level dialog retry loop are available).
- [ ] Existing `linkAutoDownloadOpenInPlayer` setting unmodified.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/LinkAutoDownloadResultPresenter.kt` | New | ≤ 240 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt` | Modified | ≤ 400 |
| `app_v2/src/main/res/values/strings.xml` | Modified | line-add only |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | line-add only |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | line-add only |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/share/LinkAutoDownloadResultPresenterTest.kt` | New | ≤ 220 |

> `LinkAutoDownloadProgressDialog.kt` is intentionally **not** modified: it shows progress only and has no Result handling (verified — current file is 94 lines, no `Toast` / `Result` branching). The terminal `Result` is mapped in `ReceiveShareActivity.handleLinkAutoDownloadResult` (current file lines 181-218) — that block moves wholesale into the new presenter.

---

## Steps

### Step 06.1 — Add `s0116_toast_*` strings in EN/RU/UK

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Add seven string keys per locale: `s0116_toast_saved_to_resource` (one `%1$s` for filename), `s0116_toast_saved_to_downloads` (one `%1$s`), `s0116_toast_streaming_started`, `s0116_toast_drm_blocked`, `s0116_toast_mux_failed` (one `%1$s` for codec), `s0116_toast_streaming_disabled`, `s0116_toast_auth_required` (one `%1$s` for host). Reuse existing S0003 strings for `NoNetwork`, `Timeout`, `NoMediaFound`, `MimeBlocked`, and generic `Other` — do not add alias keys for already-existing outcomes. `s0116_toast_streaming_started` is reserved for the long-running streaming-progress surface; the presenter does not consume it directly. Russian uses `..` not `...` and `ё`. Verify with `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix s0116_toast_`.

**Verification:**

- `Grep` — `s0116_toast_saved_to_resource` matches once in each of the 3 locale files.
- `Grep` — `s0116_toast_saved_to_downloads` matches once in each of the 3 locale files.
- `Grep` — `s0116_toast_drm_blocked` matches once in each of the 3 locale files.
- `Grep` — `s0116_toast_auth_required` matches once in each of the 3 locale files.
- Run `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix s0116_toast_` returns exit code 0.

**Status:** `[x]` done

**Step Log:**

- 2026-05-08 — Verification 5/5 PASS. Files: 3 strings.xml files (+11 LOC EN, +12 LOC RU, +12 LOC UK). All 7 keys parity-verified. Dev log recorded.

---

### Step 06.2 — Implement `LinkAutoDownloadResultPresenter`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/LinkAutoDownloadResultPresenter.kt` (New)
**Depends on:** Step 06.1

**Prompt for developer:**

> `@Singleton class LinkAutoDownloadResultPresenter @Inject constructor(@ApplicationContext private val appContext: Context, private val settings: SettingsRepository)`. Single API: `suspend fun present(result: LinkAutoDownloadCoordinator.Result, hostActivity: AppCompatActivity, onAuthRetryRequested: suspend (originalUrl: String) -> Unit = {})`.
>
> Reads `settings.getSettings().first().linkAutoDownloadOpenInPlayer` once at the start. The branching mirrors the existing block in `ReceiveShareActivity.handleLinkAutoDownloadResult` (lines 181-218), extended for the new outcomes from Phase 03 and Phase 05. Toast text is selected from the table below; toast is shown via `Toast.makeText(appContext, text, Toast.LENGTH_LONG).show()`. If `openInPlayer == true` AND the outcome carries an `openInPlayerUri`, also fire `Intent(hostActivity, StandalonePlayerActivity::class.java).setData(uri).addFlags(FLAG_GRANT_READ_URI_PERMISSION); hostActivity.startActivity(intent)` (preserve current S0003 behaviour). For `Failed.AuthRequired` with `openInPlayer == true`, preserve the Phase 05 retry flow: launch `WebViewAuthDialogFragment.newInstance(originalUrl)` and invoke `onAuthRetryRequested(originalUrl)` after a successful cookie save. The presenter centralizes the UX entry point; it does not invent a second auth-flow owner.
>
> Outcome → string key table:
>
> - `Saved` → if `openInPlayer == true` and `result.openInPlayerUri != null`, start the player intent; otherwise toast `R.string.s0116_toast_saved_to_resource`.
> - `FellBackToDownloads` → if `openInPlayer == true` and `result.openInPlayerUri != null`, start the player intent; otherwise toast `R.string.s0116_toast_saved_to_downloads`.
> - `Failed.NoNetwork` → existing `R.string.link_autodownload_error_no_network`.
> - `Failed.Timeout` → existing `R.string.link_autodownload_error_timeout`.
> - `Failed.NoMediaFound` → existing `R.string.link_autodownload_error_no_media`.
> - `Failed.MimeBlocked` → existing `R.string.link_autodownload_error_mime_blocked`.
> - `Failed.Other` → existing `R.string.receive_share_cache_failed` (with `cause.message`).
> - `Failed.DrmBlocked` → new `R.string.s0116_toast_drm_blocked` (always toast, independent of toggle).
> - `Failed.StreamingDisabled` → new `R.string.s0116_toast_streaming_disabled` (always toast).
> - `Failed.MuxFailed(codec)` → new `R.string.s0116_toast_mux_failed` with `codec` substitution (always toast).
> - `Failed.AuthRequired(host, originalUrl)` with `openInPlayer == true` → keep the Phase 05 dialog + retry behaviour by launching `WebViewAuthDialogFragment.newInstance(originalUrl)` and using `onAuthRetryRequested(originalUrl)` after success. With `openInPlayer == false` → toast `R.string.s0116_toast_auth_required` with `host` substitution.
>
> Insert at the function entry: `LinkDownloadTrace.tag("post-download UX, openInPlayer=$openInPlayer, outcome=${result::class.java.simpleName}")`.
>
> The `appContext` for toasts is the existing-S0003 trick (see `ReceiveShareActivity.kt:198-201` rationale comment) — toasts must survive the activity finishing.

**Verification:**

- `Glob` — `LinkAutoDownloadResultPresenter.kt` exists.
- `Grep` — `class LinkAutoDownloadResultPresenter` matches once.
- `Grep` — `linkAutoDownloadOpenInPlayer` matches at least once.
- `Grep` — `R\.string\.s0116_toast_saved_to_resource` matches at least once.
- `Grep` — `R\.string\.s0116_toast_saved_to_downloads` matches at least once.
- `Grep` — `R\.string\.s0116_toast_drm_blocked` matches at least once.
- `Grep` — `R\.string\.s0116_toast_mux_failed` matches at least once.
- `Grep` — `R\.string\.s0116_toast_auth_required` matches at least once.
- `Grep` — `R\.string\.s0116_toast_streaming_disabled` matches at least once.
- `Grep` — `post-download UX, openInPlayer=` matches once (the `S0116:` prefix is added at runtime by `LinkDownloadTrace.tag`).
- `Grep` — `Toast\.makeText\(appContext` matches at least once.
- `Grep` — `WebViewAuthDialogFragment` matches at least once.

**Status:** `[x]` done

**Step Log:**

- 2026-05-08 — Verification 11/11 PASS. Files: LinkAutoDownloadResultPresenter.kt (NEW 113 LOC). Single-entry presenter handles all 11 Result branches; AuthRequired with openInPlayer launches WebViewAuthDialogFragment + invokes optional retry hook. Dev log recorded.

---

### Step 06.3 — Wire `LinkAutoDownloadResultPresenter` into `ReceiveShareActivity`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt`
**Depends on:** Step 06.2

**Prompt for developer:**

> Inject the new presenter: add `@Inject lateinit var resultPresenter: LinkAutoDownloadResultPresenter` next to the existing `@Inject lateinit var linkAutoDownloadCoordinator` (around line 52).
>
> Delete the entire body of the existing private function `handleLinkAutoDownloadResult` (lines 181-218 — toast + open-in-player intent branching). Replace its body with a single `lifecycleScope.launch { resultPresenter.present(result, this@ReceiveShareActivity, onAuthRetryRequested = { retryUrl -> processLinkAutoDownload(retryUrl) }) }` call (or the closest equivalent signature if `processLinkAutoDownload` currently captures the URL elsewhere). Keep the existing Phase 05 retry capability — this step only moves it behind the presenter.
>
> Remove the now-unused imports of `Toast`, `R.string.*` for autodownload outcomes, and `StandalonePlayerActivity` from `ReceiveShareActivity` if they are no longer referenced after the deletion. Other Toasts in the file (line 116 `receive_share_no_content`, line 318 `receive_share_copied_to_folder`, line 324 `receive_share_copy_to_folder_failed`) are unrelated — they remain.

**Verification:**

- `Grep` — `LinkAutoDownloadResultPresenter` matches at least 2 times in `ReceiveShareActivity.kt` (constructor + use site).
- `Grep` — `resultPresenter\.present\(` matches once.
- `Grep` — `link_autodownload_done_resource` returns 0 hits in `ReceiveShareActivity.kt` (all autodownload-result strings moved into presenter).
- `Grep` — `link_autodownload_fallback_downloads` returns 0 hits in `ReceiveShareActivity.kt`.
- `Grep` — `link_autodownload_error_no_network` returns 0 hits in `ReceiveShareActivity.kt`.
- `Grep` — `StandalonePlayerActivity` returns 0 hits in `ReceiveShareActivity.kt` (player launch moved into presenter).

**Status:** `[x]` done

**Step Log:**

- 2026-05-08 — Verification 6/6 PASS. Files: ReceiveShareActivity.kt (-46 LOC removed handler body, +9 LOC delegated to presenter, -1 LOC unused StandalonePlayerActivity import; +1 LOC presenter inject). Dev log recorded.

---

### Step 06.4 — Update existing `link_autodownload_open_in_player_summary` to mention toast fallback

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 06.3

**Prompt for developer:**

> The existing description string is keyed `link_autodownload_open_in_player_summary` (verified in `values/strings.xml:1065`: «After a successful download the file opens automatically in the built-in player.»). Append a clause about the toggle-off behaviour. Suggested final wording (single string, do not introduce a new key):
>
> - EN: «After a successful download the file opens automatically in the built-in player. If disabled, the result is shown as a toast.»
> - RU: «После успешной загрузки файл открывается во встроенном плеере. Если выключено, результат показывается тостом.»
> - UK: «Після успішного завантаження файл відкривається у вбудованому плеєрі. Якщо вимкнено, результат показується тостом.»
>
> Use `..` not `...` in Russian. The Russian version uses `ё` only where grammatically appropriate ("показывается" — no `ё` here).

**Verification:**

- `Grep -i` — `shown as a toast` matches once in `values/strings.xml`.
- `Grep` — `тостом` matches once in `values-ru/strings.xml`.
- `Grep` — `тостом` matches once in `values-uk/strings.xml`.
- `Grep` — `link_autodownload_open_in_player_summary` matches exactly once in each of the 3 locale files (no duplicates introduced).

**Status:** `[x]` done

**Step Log:**

- 2026-05-08 — Verification 4/4 PASS. Files: 3 strings.xml files (existing summary string updated in place; no new keys). Dev log recorded.

---

### Step 06.5 — Add `LinkAutoDownloadResultPresenterTest` unit suite

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/share/LinkAutoDownloadResultPresenterTest.kt` (New)
**Depends on:** Step 06.4

**Prompt for developer:**

> Robolectric. Mock `SettingsRepository` returning `linkAutoDownloadOpenInPlayer = true` then `false`. Cases per outcome:
>
> - `Saved` + toggle on → opens player intent (capture intent in mock).
> - `Saved` + toggle off → no player intent + toast string id == `s0116_toast_saved_to_resource`.
> - `Failed.MuxFailed("video/opus")` + toggle on → toast string id == `s0116_toast_mux_failed` (toggle does not suppress error toasts).
> - `Failed.MuxFailed` + toggle off → same toast.
> - `Failed.DrmBlocked` → toast `s0116_toast_drm_blocked` regardless of toggle.
> - `Failed.AuthRequired("example.com", "https://example.com/video")` + toggle on → no toast; auth-retry callback / dialog request is triggered.
> - `Failed.AuthRequired("example.com", "https://example.com/video")` + toggle off → toast `s0116_toast_auth_required` with host substituted.
>
> Mock `Toast` via `ShadowToast`.

**Verification:**

- `Glob` — `LinkAutoDownloadResultPresenterTest.kt` exists.
- `Grep` — `@Test` matches at least 7 times.
- `Grep` — `ShadowToast` matches at least once.
- `Grep` — `s0116_toast_drm_blocked` matches at least once.
- `Grep` — `s0116_toast_auth_required` matches at least once.

**Status:** `[x]` done

**Step Log:**

- 2026-05-08 — Verification 5/5 PASS. Files: LinkAutoDownloadResultPresenterTest.kt (NEW 130 LOC, 7 @Test cases, ShadowToast assertions). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 06.*` (06.1 through 06.5) is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `LinkAutoDownloadResultPresenterTest` passes (`./gradlew :app_v2:testStandardDebugUnitTest` — JVM unit test, no instrumentation needed).
- [ ] `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix s0116_toast_` exits 0.
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

- All terminal outcomes route through `LinkAutoDownloadResultPresenter`. Phase 07 coordinator-level degradation tests should stop at `LinkAutoDownloadCoordinator.handle(...)`; presenter coverage remains in this phase's dedicated unit suite and is not part of the coordinator instrumentation seam.
- Existing `linkAutoDownloadOpenInPlayer` setting is unchanged structurally — backup format compatibility preserved (Phase 01 already added the persistence; this phase only consumes it differently).

---

## Rollback Plan

Revert phase commit. Existing in-line branching in `ReceiveShareActivity` returns; new toasts disappear. Open-in-player flow and the Phase 05 retry loop revert to the pre-extraction arrangement. New strings remain unused (orphans cleaned up later via lint).

## Revision History

- **2026-05-08** - by `/spec-update` (`GPT-5.4`, focus: consistency, completeness, verifiability)
	- Applied: aligned result nomenclature, preserved Phase 05 auth retry ownership, corrected presenter string/test expectations. Proposed (DISCUSS): 0.
