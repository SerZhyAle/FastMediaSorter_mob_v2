# Phase 03 — dynamic-extractor

**Strategic spec:** [`../S0140_extend-market-url-coverage.md`](../S0140_extend-market-url-coverage.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⛔ Blocked
**Depends on:** Phase 02 + research §6.1/§6.2/§6.3
**Blocks:** Phase 04 step 04.3
**Steps done:** 3 / 4
**Started:** 2026-05-10
**Completed:** -

---

## Objective

Add the dynamic-page extractor and login-wall heuristic only after the smoke-test and false-positive baselines are measured.

---

## Prerequisites

- [x] Phase 02 is ✅ Done.
- [x] Strategic §6.1 / §6.2 / §6.3 resolved 2026-05-18 — research items closed via web-research + code audit. Smoke-test matrix is now a BlockNeedUserTest regression artefact, not a pre-impl gate.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `temp/S0140_smoketest/*` | New | <= 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/InvisibleWebViewExtractionStrategy.kt` | New | <= 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkExtractionRegistry.kt` | Modified | <= 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | <= 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified | <= 760 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupData.kt` | Modified | <= 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupMapper.kt` | Modified | <= 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/HtmlPageExtractionStrategy.kt` | Modified | <= 420 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/LinkAutoDownloadProgressDialog.kt` | Modified | <= 180 |
| `app_v2/src/main/res/values/strings_s0140.xml` | Modified | <= 80 |
| `app_v2/src/main/res/values-ru/strings_s0140.xml` | Modified | <= 80 |
| `app_v2/src/main/res/values-uk/strings_s0140.xml` | Modified | <= 80 |

---

## Steps

### Step 03.1 — Capture observability baseline (post-implementation regression artefact)

**Files:** `temp/S0140_smoketest/summary.md`, `app_v2/src/main/java/com/sza/fastmediasorter/data/link/InvisibleWebViewExtractionStrategy.kt` (Timber event sanity check)
**Depends on:** Steps 03.2..03.4 implemented; runs during BlockNeedUserTest device round

**Prompt for developer:**

> Record one regression-baseline pass after the dynamic extractor and login-wall heuristic are landed. Run the cancellable analyzing-page flow on a representative public-URL sweep (10..15 SPA URLs + 20..30 neutral URLs for FPR), then capture the structured Timber `dynamic-extractor result=<found|empty|blocked|timeout> host=<sha256-8> ms=<n>` events to `temp/S0140_smoketest/summary.md` without naming the platforms in code or in the file. The strategic §6.1/§6.2/§6.3 resolutions explicitly downgraded this step from pre-impl gate to BlockNeedUserTest regression artefact (cap values 22s / 4s already shipped in `HARD_TIMEOUT_MS` / `DOM_SETTLE_MS`; `MIN_LOGIN_WALL_SIGNALS=2` already shipped).

**Verification:**

- `Glob` — `temp/S0140_smoketest/summary.md` exists.
- `Grep` — `dynamic-extractor result=` appears in the summary at least once.
- `Grep` — login-wall heuristic FPR sample size ≥ 20 neutral URLs recorded in the same summary.
- No platform-name string literal added to `temp/S0140_smoketest/*` or to `src/main/`.

**Status:** `[ ]` not done

---

### Step 03.2 — Add the invisible WebView extraction strategy and registry order

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/InvisibleWebViewExtractionStrategy.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkExtractionRegistry.kt`
**Depends on:** — start of phase (research baseline §6.1..§6.3 resolved 2026-05-18)

**Prompt for developer:**

> Implement a generic, headless WebView strategy that injects saved cookies, intercepts media-like requests, honours the hard caps from the strategic spec, and register it after the structured-data / static HTML stages in `LinkExtractionRegistry`.

**Verification:**

- `Glob` — `InvisibleWebViewExtractionStrategy.kt` exists.
- `Grep` — `override val id = "dynamic"` or equivalent neutral id exists once.
- `Grep` — `dynamic` is present in `LinkExtractionRegistry.CANONICAL_ORDER` after `html`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification PASS. `InvisibleWebViewExtractionStrategy.kt` landed with neutral `dynamic` id and the registry order now places it after `html`.

---

### Step 03.3 — Surface cancellable page-analysis progress

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/LinkAutoDownloadProgressDialog.kt`, `app_v2/src/main/res/values/strings_s0140.xml`, `app_v2/src/main/res/values-ru/strings_s0140.xml`, `app_v2/src/main/res/values-uk/strings_s0140.xml`
**Depends on:** Step 03.2

**Prompt for developer:**

> Add a dedicated progress state and user-visible copy for dynamic page analysis. Keep the dialog cancellable and pass the new strings through `docs/COMMUNICATION_POLICY.md` before marking the step done.

**Verification:**

- `Grep` — `Analyzing page..` exists in EN/RU/UK resources.
- `Grep` — the progress dialog switches on the new progress state.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification PASS. `Analyzing page..` copy added in EN/RU/UK and the progress dialog maps the new state.

---

### Step 03.4 — Add the login-wall heuristic and opt-out setting

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/HtmlPageExtractionStrategy.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupData.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupMapper.kt`, `app_v2/src/main/res/values/strings_s0140.xml`, `app_v2/src/main/res/values-ru/strings_s0140.xml`, `app_v2/src/main/res/values-uk/strings_s0140.xml`
**Depends on:** — start of phase (research baseline §6.3 resolved 2026-05-18)

**Prompt for developer:**

> Add the soft login-wall heuristic, gate it by a persisted opt-out setting with backup-safe defaults, and reuse the existing `AuthRequired` presenter path so the user can jump into the WebView auth flow when the heuristic trips.

**Verification:**

- `Grep` — a persisted boolean setting for the heuristic exists in settings + backup mapping.
- `Grep` — `BlockedReason.AuthRequired` can be returned from the HTML path on heuristic hits.
- `Grep` — the user-visible strings exist in EN/RU/UK and pass the communication-policy checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification PASS. HTML extraction can now raise `BlockedReason.AuthRequired` on heuristic hits and the persisted toggle flows through settings + backup mapping.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Research blockers in `INDEX.md` are checked.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if new public API files were added.

---

## Handoff Notes to Next Phase

Runtime code is landed. Closure is still blocked until the public smoke-test/FPR baselines exist and the phase is revalidated through `/build`.

---

## Rollback Plan

Revert Phase 03 commit(s) only after removing any temporary dynamic-extractor traces or test artefacts.

---

## Revision History

- **2026-05-18** — by `/spec-update` (sonnet-4.5, focus: completeness + consistency)
  - Applied: 4. Proposed (DISCUSS): 0.
  - Step 03.1 переписан: pre-impl smoke-test → post-impl regression artefact в BlockNeedUserTest device-round. Prerequisites: research §6.1/§6.2/§6.3 отмечены `[x]` Resolved. Depends-on графы 03.2 и 03.4 распутаны (теперь не зависят от 03.1). Phase Status header `⛔ Blocked` оставлен без изменений — переход выполняется через `/spec-check` или `update.ps1` на стороне оператора после закрытия Step 03.1.