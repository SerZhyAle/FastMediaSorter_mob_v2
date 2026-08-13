# Phase 08 — Docs and Catalog Cleanup

**Strategic spec:** [`../S0116_url-media-downloader.md`](../S0116_url-media-downloader.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all phases (01 — 07)
**Blocks:** none — final phase
**Steps done:** 5 / 5
**Started:** 2026-05-08
**Completed:** 2026-05-08

---

## Objective

Update user-facing feature docs in three locales, regenerate class catalog, ensure dev/CHANGELOG completeness, run final lint/string parity checks. Final phase — see INDEX.md Completion Gate.

---

## Prerequisites

- [ ] Phases 01 — 07 ✅ Done.
- [ ] All new public classes have populated `role` and `status` in `dev/CATALOG/app_v2.jsonl` (or are about to be filled here).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | line-add only |
| `docs/FEATURES_RU.md` | Modified | line-add only |
| `docs/FEATURES_UK.md` | Modified | line-add only |
| `dev/CATALOG/app_v2.jsonl` | Modified (regenerated) | n/a |
| `dev/CATALOG/app_v2.md` | Modified (regenerated) | n/a |
| `dev/CHANGELOG.md` | Modified (line-add only) | n/a |

---

## Steps

### Step 08.1 — Update `docs/FEATURES.md` §22 with the new capability bullet

**Files:** `docs/FEATURES.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Confirmed: §22 Background & System Services starts at line 350; the existing S0003 bullet is at line 360 (`- **Auto-download incoming links (S0003)**: ...`). Insert a new top-level bullet immediately after the S0003 bullet (between current lines 360 and 361):
>
> > - **Extended URL download (S0116)**: Direct media URLs, embedded media in HTML, and standard streaming manifests (HLS / DASH) are saved as standard MP4 / MP3 / JPEG. If the site requires a login, you can authenticate inside an embedded WebView — subsequent downloads from that domain reuse the saved session. Quality preferences apply automatically. Whether the result auto-opens in the built-in player or shows a toast is configured by the existing "Open downloaded file in player" toggle.
>
> Do not name any platforms. Avoid `bypass`, `unauthorised`, `restricted`, `private content` keywords.

**Verification:**

- `Grep` — `Extended URL download \(S0116\)` matches once in `docs/FEATURES.md`.
- `Grep` — `(?i)YouTube|Instagram|TikTok|Reddit|Facebook|Threads|Twitter` returns 0 hits in the new bullet's surrounding context (verified by `Grep -A 3 -B 1 "Extended URL download"`).

**Status:** `[x]` done

**Step Log:**

- 2026-05-08 — Verification 2/2 PASS. Files: docs/FEATURES.md (+1 LOC bullet at line 362, immediately after the existing S0003 bullet). Surrounding ±3 lines contain no platform names (compliance gate). Dev log recorded.

---

### Step 08.2 — Update `docs/FEATURES_RU.md` and `docs/FEATURES_UK.md` mirrors

**Files:** `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** Step 08.1

**Prompt for developer:**

> Russian (use `..` not `...`, use `ё`):
>
> > **Расширенная загрузка по ссылке:** прямые медиаURL, встроенные медиа в HTML-странице, стандартные потоковые манифесты (HLS / DASH) загружаются как обычный MP4/MP3/JPEG. Если сайт требует входа, можно авторизоваться во встроенном веб-экране — после этого загрузки с этого домена работают с сохранённой сессией. Настройки качества применяются автоматически. Открывать результат в плеере или ограничиться тостом — настраивается в «Загрузка по ссылке».
>
> Ukrainian: equivalent translation. No platform names anywhere.

**Verification:**

- `Grep` — `Расширенная загрузка по ссылке` matches once in `docs/FEATURES_RU.md`.
- `Grep` — `Розширене завантаження за посиланням` (or equivalent UK heading) matches once in `docs/FEATURES_UK.md`.
- `Grep` — `\.\.\.` (three dots) returns 0 hits inside the new RU paragraph.

**Status:** `[x]` done

**Step Log:**

- 2026-05-08 — Verification 3/3 PASS. Files: docs/FEATURES_RU.md (+1 LOC), docs/FEATURES_UK.md (+1 LOC). RU/UK paragraphs use `..` not `...`; RU uses `ё` (`сохранённой`, `тостом`). Dev log recorded.

---

### Step 08.3 — Regenerate `dev/CATALOG/app_v2.jsonl` and `dev/CATALOG/app_v2.md`

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 08.2

**Prompt for developer:**

> Run `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` then `pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2`. For each new class added in Phases 01 — 07, set `role` and `status` via `pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -ClassName <Name> -Role <role> -Status implemented`. Required for: `StreamingManifest`, `MediaQualityPreference`, `LinkDownloadTrace`, `StreamingManifestSniffer`, `StreamingDownloadStrategy`, `Media3SegmentDownloader`, `MediaMuxerRemuxer`, `ManifestDrmDetector`, `StreamingCacheCleaner`, `EncryptedCookieStore`, `LinkDownloadCookieJar`, `AuthSessionRepository`, `AuthSessionRepositoryImpl`, `WebViewAuthDialogFragment`, `WebViewAuthViewModel`, `AuthSessionsListFragment`, `AuthSessionsListViewModel`, `AuthSessionAdapter`, `LinkAutoDownloadResultPresenter`.

**Verification:**

- `Grep` — `StreamingDownloadStrategy` returns 0 hits in `dev/CATALOG/app_v2.jsonl` (lives in `streamingEnabled/` flavor source-set, which the catalog scanner does not index by design — same applies to `Media3SegmentDownloader`, `MediaMuxerRemuxer`, `ManifestDrmDetector`, `StreamingCacheCleaner`, `NoOpStreamingPipeline`, both flavor `StreamingModule.kt` files).
- `Grep` — `LinkAutoDownloadResultPresenter` matches at least once in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `WebViewAuthDialogFragment` matches at least once in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `EncryptedCookieStore` matches at least once in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-08 — Verification 4/4 PASS. Catalog regenerated: 981 files (+11 from pre-S0116 baseline of 968). Indexed: AuthSessionRepository, AuthSessionRepositoryImpl, AuthSessionAdapter, AuthSessionsActivity, AuthSessionsListFragment, AuthSessionsListViewModel, EncryptedCookieStore, LinkDownloadCookieJar, LinkAutoDownloadResultPresenter, StreamingPipeline, WebViewAuthDialogFragment, WebViewAuthViewModel. Flavor-specific streaming classes intentionally not indexed (scanner restricted to `src/main/`). Dev log recorded.

---

### Step 08.4 — Confirm `dev/CHANGELOG.md` covers every modified file

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 08.3

**Prompt for developer:**

> Audit: for each `app_v2/src/.../**/*.kt` file modified across Phases 01 — 07, ensure at least one matching entry exists in `dev/CHANGELOG.md`. If gaps exist, run `pwsh -File scripts/add_to_dev_log.ps1 "<path>" "spec-tech" "<short description>"`. Cluster by phase. Do not edit `dev/CHANGELOG.md` directly.

**Verification:**

- `Grep` — `S0116` matches at least 8 times in `dev/CHANGELOG.md` (one per phase + extras).
- `Grep` — `LinkAutoDownloadResultPresenter` matches at least once.
- `Grep` — `StreamingDownloadStrategy` matches at least once.
- `Grep` — `WebViewAuthDialogFragment` matches at least once.

**Status:** `[x]` done

**Step Log:**

- 2026-05-08 — Verification 4/4 PASS. dev/CHANGELOG.md contains 111 `S0116` mentions (every phase covered), all 3 sample class references present. Audit-only step — no edits required. Dev log not added (no file changes).

---

### Step 08.5 — Run final lint and string parity gates

**Files:** none modified — verification-only step
**Depends on:** Step 08.4

**Prompt for developer:**

> Run sequentially:
>
> - `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix s0116_toast_` (must exit 0)
> - `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix auth_sessions_` (must exit 0)
> - `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix webview_auth_` (must exit 0)
> - `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix setting_saved_authorizations` (must exit 0)
> - `/build standardDebug` (must succeed)
> - `/build liteDebug` (must succeed — confirms streaming-disabled flavor compiles)
> - `/build photosDebug` (must succeed)
>
> If any check fails, halt completion and fix root cause in the responsible phase, then re-run.

**Verification:**

- All four `check_strings_localized.ps1` invocations exit 0 (recorded in chat / build log).
- `/build` for `standardDebug`, `liteDebug`, `photosDebug` all succeed.

**Status:** `[x]` done

**Step Log:**

- 2026-05-08 — Verification 2/2 PASS. All 4 string-parity gates exit 0 (s0116_toast_=7 keys, auth_sessions_=3, webview_auth_=4, setting_saved_authorizations=2). All 3 flavor builds PASS (standardDebug, liteDebug, photosDebug). Audit-only step — no edits required.

---

## Phase Done Criteria

- [ ] Every `Step 08.*` above is `[x] done`.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated and reviewed for compliance wording.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated and roles/statuses set for every new class.
- [ ] `dev/CHANGELOG.md` covers every modified file across all phases.
- [ ] All string-locale audits pass.
- [ ] `standardDebug`, `liteDebug`, `photosDebug` all build.
- [ ] `Grep` for `TODO(phase-08)` returns zero hits.

---

## Handoff Notes to Next Phase

Final phase — see [INDEX.md Completion Gate](INDEX.md#completion-gate). Run `/spec-check S0116` to advance strategic spec status from `Implemented` → `Verified`. `/spec-check` is also responsible for grepping and removing all six `Timber.d("S0116:` debug tags before committing the Verified transition.

---

## Rollback Plan

Revert phase commit. Docs and catalog revert to pre-S0116 state; functionality remains in code (Phases 01 — 07 untouched). Re-run `/spec-tech S0116 --phase 08` to redo cleanly.

## Revision History

- **2026-05-08** - by `/spec-update` (`GPT-5.4`, focus: consistency, completeness, verifiability)
	- Applied: revision-history maintenance for tactical refinement pass. Proposed (DISCUSS): 0.
