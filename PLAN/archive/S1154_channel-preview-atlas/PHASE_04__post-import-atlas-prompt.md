# Phase 04 - Post-import atlas prompt

**Strategic spec:** [`../S1154_channel-preview-atlas.md`](../S1154_channel-preview-atlas.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done (code; end-to-end download device-gated -> BlockNeedUserTest)
**Depends on:** Phase 01
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-07-23
**Completed:** 2026-07-23

---

## Objective

After a successful stream-catalog import/update, offer to download the channel-preview atlas when it is not already installed - via a helper manager so `StreamsActivity` stays thin (risk §7). The offer routes through the existing `DeliverableInventory` atlas item so download progress and delete stay the real WorkManager path.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (atlas `ExtensionItem.Module` + `DeliverableInventory` wiring exist).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamAtlasPromptManager.kt` | New | ≤ 160 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt` | Modified | ≤ 1050 |
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |

> No `res/layout/*.xml` edits (reuse the existing Snackbar/dialog affordance used by `showCatalogRefreshSuggestion`) - no landscape-parity obligation.
>
> **Flavor placement.** `src/main`; inert on `lite`/`photos` (no streams screen). No `BuildConfig.*` guard.

---

## Steps

### Step 04.1 - Atlas prompt manager

**Files:** `ui/streams/helpers/StreamAtlasPromptManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `StreamAtlasPromptManager` owning the "download the preview atlas?" offer: given the current install state of the atlas `ExtensionItem` (query `DeliverableInventory`), if NOT installed and not already in-flight, show a dismissible prompt (reuse the same Snackbar-with-action affordance `StreamsActivity.showCatalogRefreshSuggestion()` uses; do NOT introduce a one-off dialog button pair). Confirm triggers `DeliverableInventory.download(item)` for the atlas set. Keep all business logic here; the Activity only forwards the `CatalogUpdated` event and hosts the anchor view (Rule 3/5).

**Verification:**

- `Glob` - `StreamAtlasPromptManager.kt` exists.
- `Grep` - `class StreamAtlasPromptManager` matches exactly once.
- `Grep` - `DeliverableInventory` referenced (offer routes through it).
- `Grep -n "Log\.d\("` - zero hits.

**Follow-up (2026-07-26, after the Phase 06 payload went live):** two defects found on the first real
end-to-end run and fixed here.

- The offer was a `LENGTH_LONG` Snackbar fired in the same frame as the "catalog updated" toast, so it
  could pass unnoticed and the once-per-session latch then suppressed it for good. It is now
  `LENGTH_INDEFINITE`, and the latch clears on any non-action dismissal, so every later catalog update
  offers the atlas again until it is installed.
- The grid reads the `url->index` map once at screen setup, so an atlas installed AFTER that read stayed
  unused until the screen was reopened. `StreamAtlasPromptManager` now calls back on
  `DownloadProgress.Installed`, and `StreamsActivity.onStart` re-reads the map when the payload exists
  but the cached map is empty (covers an install from the Extensions Manager).

Verified on emulator-5554 (Android 15): catalog import -> offer stays on screen -> Download ->
`Worker result SUCCESS for download_deliverable_CHANNEL_PREVIEW_ATLAS`, payload on disk at the exact
published sizes (11,358,632 + 134,997 B), and the video grid logs `S1154: grid atlas-preview tile
applied` for channels with no captured frame.

**Status:** `[x]` done

---

### Step 04.2 - Delegate from StreamsActivity on CatalogUpdated

**Files:** `ui/streams/StreamsActivity.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> In the existing `StreamsEvent.CatalogUpdated` collector branch, after the current handling, invoke `streamAtlasPromptManager.maybeOffer(anchorView)`. Construct the manager with the injected `DeliverableInventory` (add the field if absent). No new event type is needed - reuse `CatalogUpdated`.

**Verification:**

- `Grep` - `StreamAtlasPromptManager` referenced in `StreamsActivity.kt`.
- `Grep` - the call sits inside the `CatalogUpdated` branch (context grep).
- `.\a.ps1 fk` compiles; `StreamsActivity.kt` LOC < 1500.

**Status:** `[x]` done

---

### Step 04.3 - Prompt strings (trilingual)

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`
**Depends on:** - independent

**Prompt for developer:**

> Add `streams_atlas_prompt_message` (the offer text) and `streams_atlas_prompt_action` (the confirm action label) across EN/RU/UK via `set-android-string.ps1 -Action add` (one lockstep call per key). RU/UK prose: `..` not `...`, plain hyphen, Ё where grammatical. Strings must pass `docs/COMMUNICATION_POLICY.md` §2 (suggestion/offer formula) and §6 (tone checklist).

**Verification:**

- `Grep` - `streams_atlas_prompt_message` and `streams_atlas_prompt_action` present in all three `strings.xml`.
- Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "streams_atlas_prompt"` - exit 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (one new class); set `role`+`status` via `set.ps1`.
- [ ] Phase-boundary audit - no unresolved P0/P1. Focus: lifecycle-safety of the prompt (no leak of the Activity anchor), no duplicate offer on rapid re-import.
- [ ] **Device-gated (defer to BlockNeedUserTest):** the offer actually appears after an import and the confirm downloads the atlas end-to-end. Requires the real binary from Phase 06.

---

## Handoff Notes to Next Phase

- The offer is wired but only completes a real download once Phase 06 publishes the binary and finalizes the descriptor pins.

---

## Rollback Plan

Revert the phase commit(s). The manager and the one call site are additive - removing them restores the prior import flow.
