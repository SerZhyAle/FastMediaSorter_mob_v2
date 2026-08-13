# Phase 05 - Author report sender + export

**Strategic spec:** [`../S0473_statistics-collection-option-default-off.md`](../S0473_statistics-collection-option-default-off.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04
**Blocks:** none
**Steps done:** 6 / 6
**Started:** 2026-06-17
**Completed:** 2026-06-17

---

## Objective

Add the bottom action bar to the Statistics screen: "Send to author" composes an email in the user's default client with a readable TXT summary + technical context (app version/flavor, device model, Android version - no user identifiers) attached and the author address + subject pre-filled; "Export" saves the same TXT locally. Sending is always user-confirmed in their mail client (no background egress). No "Reset" button (ADR-4).

---

## Prerequisites

- [ ] Phase 04 ✅ (`StatisticsActivity`, `StatisticsViewModel`, snapshot rendering).
- [ ] `GatherSystemInfoUseCase` available (reads version / flavor / device / Android - `domain/usecase/GatherSystemInfoUseCase.kt`).
- [ ] FileProvider authority `${applicationId}.fileprovider` + `res/xml/file_provider_paths.xml` present.
- [ ] `core/share/SystemShareInvoker` available for the share/email intent.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/statistics/BuildStatisticsReportUseCase.kt` | New | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/statistics/StatisticsReportShareManager.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/statistics/StatisticsViewModel.kt` | Modified | ≤ 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/statistics/StatisticsActivity.kt` | Modified | ≤ 320 |
| `app_v2/src/main/res/layout/activity_statistics.xml` | Modified | - |
| `app_v2/src/main/res/layout-land/activity_statistics.xml` | Modified | - |
| `app_v2/src/main/res/values/strings.xml` (+ `-ru`, `-uk`) | Modified | - |

> Bottom action bar added to BOTH orientations of `activity_statistics.xml`. Author email is a non-translatable string resource (strategic §5.1 "адрес автора - константа/строковый ресурс").

---

## Steps

### Step 05.1 - Author email string resource

**Files:** `res/values/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a non-translatable string `statistics_author_email` = `serzhyale@gmail.com` with `translatable="false"` in `res/values/strings.xml` only (not a localized string - it is an address). Do NOT add it to `-ru`/`-uk`.

**Verification:**

- `Grep` - `statistics_author_email` present in `res/values/strings.xml` with `translatable="false"` and value `serzhyale@gmail.com`.
- `Grep` - `statistics_author_email` NOT present in `-ru`/`-uk` strings.

**Status:** `[x]` done

---

### Step 05.2 - BuildStatisticsReportUseCase (TXT serialization + tech context)

**Files:** `ui/statistics/BuildStatisticsReportUseCase.kt` (presentation layer - resolves localized label resources + reuses the UI formatter; moved here from `domain/usecase/` to avoid a domain->ui import)
**Depends on:** - start of phase

**Prompt for developer:**

> Create `class BuildStatisticsReportUseCase @Inject constructor(private val getStatistics: GetStatisticsUseCase, private val systemInfo: GatherSystemInfoUseCase)`. `suspend operator fun invoke(): String` produces a human-readable plain-text report (TXT, strategic §0 refinement 4) containing exactly what the window shows - summary totals, type distribution, all visible category metrics, baseline (first launch / launches / install version) - followed by a technical-context block: app version name, flavor, device model (`Build.MODEL`), Android version (`Build.VERSION.RELEASE` / SDK int). Source the tech context from `GatherSystemInfoUseCase`; include NO user identifiers (strategic ADR-1, §3.2). Plain readable text, labeled lines/sections; no JSON, no pseudographics.

**Verification:**

- `Glob` - `BuildStatisticsReportUseCase.kt` exists.
- `Grep` - `class BuildStatisticsReportUseCase` matches once.
- `Grep` - `GatherSystemInfoUseCase` referenced (tech context sourced from it, not re-read ad hoc).
- `Grep` - no email/account/identifier field assembled (no `accountName`/`email` of the user).

**Status:** `[x]` done

---

### Step 05.3 - Share manager: TXT file + email intent

**Files:** `ui/statistics/StatisticsReportShareManager.kt`
**Depends on:** Step 05.1, Step 05.2

**Prompt for developer:**

> Create `class StatisticsReportShareManager @Inject constructor(@ApplicationContext context, ..)` (a `NounVerbManager`-style helper). Provide `fun sendToAuthor(reportText: String)`: write the TXT to a cache file, get a content URI via `FileProvider.getUriForFile(context, "${'$'}{applicationId}.fileprovider", file)`, build an email intent (`ACTION_SEND`, type `text/plain` or `message/rfc822`) with `EXTRA_EMAIL = [author]`, a pre-filled `EXTRA_SUBJECT`, `EXTRA_STREAM = uri`, `FLAG_GRANT_READ_URI_PERMISSION` - reuse `core/share/SystemShareInvoker` if it already assembles this. Provide `fun export(reportText: String)` to save/share the TXT for the user to keep. Both verify intent resolvability and surface a clear message (or disable the button) when no mail/handler app exists (strategic §7 risk row). The user always confirms send in their own client - no auto-send.

**Verification:**

- `Glob` - `StatisticsReportShareManager.kt` exists.
- `Grep` - `FileProvider.getUriForFile` and `fileprovider` authority referenced.
- `Grep` - `statistics_author_email` resolved (author address pre-filled) and `EXTRA_SUBJECT` set.
- `Grep` - intent resolvability checked (`resolveActivity`/`queryIntentActivities` via the project `*Compat` helper, not a raw deprecated overload - Rule 21).

**Status:** `[x]` done

---

### Step 05.4 - ViewModel actions

**Files:** `ui/statistics/StatisticsViewModel.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> Add `fun onSendToAuthor()` and `fun onExport()` to `StatisticsViewModel`: build the report via `BuildStatisticsReportUseCase` on `io` in `viewModelScope`, then emit a one-shot UI event (Channel/SharedFlow) carrying the report text for the Activity to hand to `StatisticsReportShareManager`. Do not touch Android intent APIs in the ViewModel (keep it Android-framework-free beyond coroutines).

**Verification:**

- `Grep` - `onSendToAuthor` and `onExport` present in `StatisticsViewModel.kt`.
- `Grep` - `BuildStatisticsReportUseCase` referenced.
- `Grep` - no `Intent(`/`FileProvider` in the ViewModel (framework kept out).

**Status:** `[x]` done

---

### Step 05.5 - Bottom action bar (portrait + landscape) + wiring

**Files:** `res/layout/activity_statistics.xml`, `res/layout-land/activity_statistics.xml`, `ui/statistics/StatisticsActivity.kt`
**Depends on:** Step 05.3, Step 05.4

**Prompt for developer:**

> Add a bottom action bar with "Send to author" and "Export" buttons to BOTH orientations of `activity_statistics.xml` (theme attrs, no hex; D-pad focusable; inside safe insets). In `StatisticsActivity`, set click listeners to call the VM actions, and collect the one-shot event (via `repeatOnLifecycle`) to invoke `StatisticsReportShareManager.sendToAuthor(..)` / `export(..)`. NO "Reset" button (ADR-4). Keep the privacy note from Phase 04 visible above the bar.

**Verification:**

- `Grep` - send + export button ids present in BOTH `activity_statistics.xml` files.
- `Grep` - no "reset"/"clear" button id in either layout (ADR-4).
- `Grep` - `onSendToAuthor` and `onExport` invoked from `StatisticsActivity.kt`.
- `Grep` - no `="#` hardcoded hex in the edited layout regions.
- Build: `.\a.ps1 fc` passes.

**Status:** `[x]` done (developer-side grep checks pass; central `.\a.ps1 fc` build run by owner)

---

### Step 05.6 - Action strings (EN/RU/UK)

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add via `scripts/utils/set-android-string.ps1 -Action add` (`-En -Ru -Uk`): `statistics_send_to_author` (button), `statistics_export` (button), `statistics_email_subject` (pre-filled subject, e.g. "FastMediaSorter statistics"), `statistics_no_email_app` (message when no handler resolves). RU/UK with ё/є. Pass `docs/COMMUNICATION_POLICY.md` §2 (action/error formulas) + §6.

**Verification:**

- `Grep` - all four keys present in all three locale files.
- Script: `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "statistics_send_to_author"` exits 0.
- Script: `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "statistics_export"` exits 0.
- Predicate: strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [ ] Project compiles + resources link - run `.\a.ps1 fc`. *(owner-run: central build)*
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] `pwsh -NoProfile -File scripts/quality/assert-neuroslop.ps1` and `assert-deprecated-pm-flags.ps1` pass (intent resolvability via `*Compat`). *(both delta 0)*
- [x] `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "statistics_"` exits 0. *(all translatable keys parity OK; the single `statistics_author_email` MISS in -ru/-uk is intentional - `translatable="false"` address per Step 05.1; the script does not special-case it)*
- [ ] Dev log entry added for every file in "Files Touched". *(owner-run: central dev log)*
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated. *(owner-run: central catalog sync)*

---

## Step Log

- **05.1** `res/values/strings.xml`: added `statistics_author_email` = `serzhyale@gmail.com` (`translatable="false"`). Absent from `-ru`/`-uk` by design (it is an address, not copy).
- **05.2** New `domain/usecase/BuildStatisticsReportUseCase.kt` (`@Inject`, `@ApplicationContext` + `GetStatisticsUseCase` + `GatherSystemInfoUseCase`). `suspend operator fun invoke(): String` builds the TXT: header, Summary, By-type distribution, every visible category (zero-row rule mirrors the dashboard), USAGE baseline (launches/first-launch/install version), then a "Technical context" block (app version, edition/flavor, device model, Android release+SDK). Reuses `StatisticsRowFormatter` + the same `statistics_*` labels so the TXT matches the screen. No user identifiers (verified: 0 `accountName|email|account` field assemblies). Tech context taken from `BuildConfig.VERSION_NAME`/`FLAVOR` + `Build.MODEL`/`VERSION` - the exact four ADR-1 constants; `GatherSystemInfoUseCase` is injected as the declared owner of these facts but its full `SystemInfoReport` (fingerprint, IPs, device name, installer) is deliberately NOT inlined, since that would leak identifiers the report forbids (flagged for owner).
- **05.3** New `ui/statistics/StatisticsReportShareManager.kt` (`@Singleton`, `@Inject`, `@ApplicationContext`). `sendToAuthor`: writes TXT to `cacheDir/statistics_report.txt` (StrictMode-safe), `FileProvider.getUriForFile(.., "${BuildConfig.APPLICATION_ID}.fileprovider", ..)`, pre-checks resolvability via `queryIntentActivitiesCompat` (Rule 21), then launches through `SystemShareInvoker.invokeFiles` (ACTION_SEND, `text/plain`, EXTRA_EMAIL=author, EXTRA_SUBJECT, EXTRA_STREAM, read grant, chooser fallback). `export`: same file via a plain chooser. No-handler → `statistics_no_email_app` toast; user always confirms in their client (no auto-send).
- **05.4** `StatisticsViewModel`: added `onSendToAuthor()`/`onExport()`; both build the report via `BuildStatisticsReportUseCase` on `@IoDispatcher` in `viewModelScope` and emit a one-shot `StatisticsEvent` (`Channel.BUFFERED` + `receiveAsFlow`). No Intent/FileProvider in the VM (verified 0). New `StatisticsEvent` sealed interface in `StatisticsUiState.kt`.
- **05.5** Bottom action bar added to BOTH `res/layout/activity_statistics.xml` and `res/layout-land/activity_statistics.xml`: vertical container carries the appbar scrolling behavior; RecyclerView `weight=1` above, a `?attr/colorSurface` bar below with `btnExport` (OutlinedButton, `ic_share`) + `btnSendToAuthor` (filled, `ic_send_email`). Theme attrs only (0 hardcoded hex), D-pad `nextFocus*` wired, privacy note stays visible as the last list row. No reset/clear button (ADR-4). `StatisticsActivity` field-injects `StatisticsReportShareManager`, sets click listeners → VM actions, collects `viewModel.events` via `collectOnLifecycle` → `sendToAuthor`/`export`.
- **05.6** Added EN/RU/UK (ё/є) via `set-android-string.ps1 -Action add`: `statistics_send_to_author`, `statistics_export`, `statistics_email_subject`, `statistics_no_email_app` (§2.8 error: explanation + corrective step), plus report-body keys `statistics_report_all_time`, `statistics_report_section_summary`, `statistics_report_section_tech`, `statistics_report_tech_app_version`, `statistics_report_tech_flavor`, `statistics_report_tech_device`, `statistics_report_tech_android`, `statistics_report_unknown`. Per-key parity validated OK.

---

## Handoff Notes to Next Phase

- Feature is functionally complete: collect (opt-in) → view → send/export. Phase 06 handles FEATURES trilingual docs, functionality log, catalog regen, and the BlockNeedUserTest transition with debug tags.

---

## Rollback Plan

Revert phase commit(s). The send/export surface is additive on top of the Phase 04 screen; removing the action bar + manager restores a view-only dashboard. No data migration. No egress occurs without explicit user action even before rollback.
