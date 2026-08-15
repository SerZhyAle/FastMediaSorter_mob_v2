# Phase 04 - Catalog refresh policy behavior (on-open)

**Strategic spec:** [`../S0659_streams-settings-expansion.md`](../S0659_streams-settings-expansion.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none
**Steps done:** 3 / 3
**Started:** -
**Completed:** -

---

## Objective

Apply `streamsCatalogRefreshPolicy` when the Streams screen opens: `MANUAL` = nothing; `ON_OPEN` (default) = a throttled, dismissible refresh suggestion (snackbar with action); `PERIODIC_WIFI` = throttled auto-refresh restricted to Wi-Fi. No WorkManager / background job - opportunistic-on-open only, honoring strategic §3.2 "no heavy background work by default".

---

## Prerequisites

- [ ] Phase 01 ✅ Done - `streamsCatalogRefreshPolicy` readable from `settings`.
- [ ] Phase 02 ✅ Done - `StreamsSessionStore.lastCatalogRefreshAt` available for throttling.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsViewModel.kt` | Modified | ≤ 360 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt` | Modified | ≤ 500 |

---

## Steps

### Step 04.1 - ViewModel: policy evaluation + suggest event + refresh-timestamp

**Files:** `ui/streams/StreamsViewModel.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `StreamsEvent.SuggestCatalogRefresh` to the sealed `StreamsEvent`. Add `fun onScreenOpened()` invoked once per screen open: read `settings.value.streamsCatalogRefreshPolicy` and `sessionStore.read().lastCatalogRefreshAt`. Branch:
> - `MANUAL` -> return.
> - `ON_OPEN` -> if `now - lastCatalogRefreshAt > ON_OPEN_THROTTLE_MS` (6h), `_events.send(SuggestCatalogRefresh)`.
> - `PERIODIC_WIFI` -> if on Wi-Fi/unmetered AND `now - lastCatalogRefreshAt > PERIODIC_THROTTLE_MS` (24h), call `onImportCatalog()` directly.
> In `onImportCatalog()`, on a `Success`/`Empty` result, persist `sessionStore.writeCatalogRefreshAt(now)` so the throttle advances. Reuse the existing Wi-Fi/unmetered detection in `core/network` (the same check that backs `searchAudioCoversOnlyOnWifi` / `thumbnailPreloadWifiOnly`); inject that collaborator rather than touching `ConnectivityManager` directly in the ViewModel. Use an injected time source or `System.currentTimeMillis()` for `now`.

**Verification:**

- `Grep` - `SuggestCatalogRefresh` present in `StreamsViewModel.kt`.
- `Grep` - `fun onScreenOpened` present.
- `Grep` - `writeCatalogRefreshAt` invoked from `onImportCatalog` path.
- `Grep` - `PERIODIC_WIFI` branch references the injected connectivity collaborator (not a raw `getSystemService`).

**Status:** `[x]` done

**Step Log:** 2026-06-24 - injected `NetworkContextAnalyzer` (the `@Singleton hasWifi()` synchronous check); added `StreamsEvent.SuggestCatalogRefresh`, `onScreenOpened()` (ViewModel-guarded one-shot, MANUAL/ON_OPEN-6h/PERIODIC_WIFI-24h), and `writeCatalogRefreshAt(now)` on Success+Empty.

---

### Step 04.2 - Add the suggestion snackbar strings (EN/RU/UK)

**Files:** `app_v2/src/main/res/values/strings.xml` (+ `values-ru`, `values-uk`)
**Depends on:** Step 04.1

**Prompt for developer:**

> Add the two snackbar strings BEFORE the Activity references them (keeps each step build-clean): `streams_catalog_refresh_suggestion` ("Update the channel catalog?") and `streams_catalog_refresh_action` ("Update"). Add via `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key <key> -En ... -Ru ... -Uk ...` (trilingual lockstep). Phrasing per `docs/COMMUNICATION_POLICY.md` §2/§6 (offer, not command; no technical terms).

**Verification:**

- `Grep` - `streams_catalog_refresh_suggestion` present in `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "streams_catalog_refresh"` exits 0.

**Status:** `[x]` done

**Step Log:** 2026-06-24 - added `streams_catalog_refresh_suggestion` ("Update the channel list?") + `streams_catalog_refresh_action` ("Update") trilingual; parity check exit 0.

---

### Step 04.3 - Activity: trigger policy once + render the suggestion snackbar

**Files:** `ui/streams/StreamsActivity.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> In `setupViews()` (after the managers are wired), call `viewModel.onScreenOpened()` exactly once per Activity creation (guard with a `savedInstanceState == null` check or a one-shot flag so a config-change recreation does not re-trigger). In `observeData()`'s event collector, handle `StreamsEvent.SuggestCatalogRefresh` by showing a dismissible `Snackbar` (or the project's standard snackbar wrapper) anchored to the list, with an action button that calls `viewModel.onImportCatalog()`. Use `R.string.streams_catalog_refresh_suggestion` + `R.string.streams_catalog_refresh_action` (added in Step 04.2).

**Verification:**

- `Grep` - `onScreenOpened()` invoked in `StreamsActivity.kt`.
- `Grep` - `SuggestCatalogRefresh ->` handled in the event `when`.
- `Grep` - `streams_catalog_refresh_suggestion` referenced.

**Status:** `[x]` done

**Step Log:** 2026-06-24 - `viewModel.onScreenOpened()` called once at end of `setupViews()` (idempotent in VM); `SuggestCatalogRefresh ->` shows a dismissible Snackbar on `rvStreams` with an Update action calling `onImportCatalog()`.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` is `[x] done`.
- [ ] Project compiles - run `/build` standard debug.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

The `streamsCatalogRefreshPolicy` setting now drives on-open behavior. Phase 05 adds the dropdown that lets the user choose the policy and the remaining settings strings.

---

## Rollback Plan

Revert phase commit(s) - default `ON_OPEN` only adds a dismissible suggestion; reverting returns to today's manual-only refresh. No persistence schema change beyond the additive session timestamp.
