# Phase 05 - Streams entry on the Welcome onboarding page

**Strategic spec:** [`../S0575_streams-toggle-welcome-entrypoints.md`](../S0575_streams-toggle-welcome-entrypoints.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none
**Steps done:** 4 / 4
**Started:** 2026-06-21
**Completed:** 2026-06-21

## Step Log

- 2026-06-21 - Steps 05.1-05.4 Verification PASS. rowStreams+groupStreamsProgress mirrored in BOTH layout/ and layout-land/ (parity). Controller bindStreamsRow = commit-ON-immediately + optional page-scoped catalog import (not install-then-persist); EnableAll best-effort streams path. 3 welcome strings in strings_setup.xml (EN/RU/UK parity exit 0). `.\a.ps1 fc` -> BUILD SUCCESSFUL (PageWelcomeFunctionalityBinding regen; Hilt resolves ImportStreamCatalogUseCase). Dev logs batched at Phase 07.

---

## Objective

Add a "Streams" row to the Welcome "What should the app do?" page, immediately after the Translation row. Toggling it ON commits `enableStreams=true` immediately and OFFERS an optional catalog download (inline progress); a declined or failed download is a non-event and never disables the feature or blocks onboarding (strategic §6 "Quiz decisions").

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`enableStreams`, `isStreamsAvailable()`).
- [ ] `ImportStreamCatalogUseCase` exists (S0570).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/page_welcome_functionality.xml` | Modified | n/a |
| `app_v2/src/main/res/layout-land/page_welcome_functionality.xml` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/helpers/WelcomeFunctionalityController.kt` | Modified | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/helpers/WelcomeEnableAllManager.kt` | Modified | ≤ 220 |
| `app_v2/src/main/res/values/strings.xml` (+ `values-ru`, `values-uk`) | Modified | n/a |

> Landscape parity (MANDATORY): `page_welcome_functionality.xml` HAS a `res/layout-land/` counterpart - the new row + progress group must be added to BOTH files with identical ids.

---

## Steps

### Step 05.1 - Add the Streams row to both orientations

**Files:** `res/layout/page_welcome_functionality.xml`, `res/layout-land/page_welcome_functionality.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> In BOTH the portrait and landscape layouts, add a `SettingsToggleRow` with id `@+id/rowStreams` immediately after `rowTranslation`, plus an inline progress group mirroring `groupTranslationProgress` (container id `@+id/groupStreamsProgress`, a `ProgressBar` `@+id/progressStreams`, a status `TextView` `@+id/tvStreamsStatus`). Use `?attr/`/`@string/` resources only - no hardcoded colors. Row label `@string/welcome_func_streams`.

**Verification:**

- `Grep` - `@+id/rowStreams` matches in BOTH `layout/` and `layout-land/` `page_welcome_functionality.xml`.
- `Grep` - `@+id/groupStreamsProgress` matches in BOTH files.

**Status:** `[x]` done

---

### Step 05.2 - Bind the Streams row

**Files:** `ui/welcome/helpers/WelcomeFunctionalityController.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Inject `ImportStreamCatalogUseCase`. Add `bindStreamsRow(binding, owner, settings)` and call it from `bindRows()` immediately after `bindTranslationRow(...)`. Behaviour (NOT the translation install-then-persist pattern): hide the row when `!capabilityAvailability.isStreamsAvailable()`. On toggle ON - `persist { it.copy(enableStreams = true) }` immediately, then start an OPTIONAL catalog import: show `groupStreamsProgress` with an indeterminate bar, run `importStreamCatalogUseCase()` in a retained `Job` (cancellable on rebind/toggle-OFF) collected under `repeatOnLifecycle(STARTED)`, and on completion show a done/failed status line. A `Failure`/`Empty` result leaves `enableStreams=true` (the user can add sources manually) - it must NOT revert the toggle. On toggle OFF - cancel the job, hide the group, `persist { it.copy(enableStreams = false) }`. Never block page navigation.

**Verification:**

- `Grep` - `fun bindStreamsRow(` matches once.
- `Grep` - `bindStreamsRow(` is called inside `bindRows(`.
- `Grep` - `it.copy(enableStreams = true)` and `it.copy(enableStreams = false)` both present.
- `Grep` - `ImportStreamCatalogUseCase` appears in the constructor parameter list.

**Status:** `[x]` done

---

### Step 05.3 - Include Streams in the "Enable all" path

**Files:** `ui/welcome/helpers/WelcomeEnableAllManager.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> Where the manager enables the optional capabilities, add a Streams branch gated on `capabilityAvailability.isStreamsAvailable()`: persist `enableStreams=true` and trigger the optional catalog import (best-effort, failure ignored, consistent with the per-row behaviour). Mirror the structure of the existing OCR/translation enable-all branch but without the install-then-persist gating.

**Verification:**

- `Grep` - `enableStreams` matches in `WelcomeEnableAllManager.kt`.

**Status:** `[x]` done

---

### Step 05.4 - Add the Welcome Streams strings (trilingual)

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`
**Depends on:** Step 05.1

**Prompt for developer:**

> Add `welcome_func_streams` (row label) and any status strings the row needs (e.g. `welcome_streams_catalog_done`, `welcome_streams_catalog_failed`) in EN/RU/UK via `scripts/utils/set-android-string.ps1 -Action add`. Copy follows `docs/COMMUNICATION_POLICY.md` §2 (onboarding choice + status) and passes the §6 tone checklist; the failed-status line must read as non-alarming (failure is expected/optional), e.g. "Catalog not downloaded - you can add streams manually".

**Verification:**

- `Grep` - `welcome_func_streams` present in all three `strings.xml`.
- Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "welcome_func_streams"` - exits 0 (and `-KeyPrefix "welcome_streams"` if those keys were added).
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] On a `standard` build the Welcome page shows a Streams row after Translation in both portrait and landscape; enabling it persists the flag and shows catalog progress; killing the network leaves the flag ON.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

- Welcome and Extensions both reach the catalog only through `ImportStreamCatalogUseCase`; no duplicate import path exists.

---

## Rollback Plan

Revert the phase commit(s) - additive row + binding; no persistence/migration changed.
