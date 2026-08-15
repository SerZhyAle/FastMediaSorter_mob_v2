# Phase 03 - Streams catalog item in the Downloadable Extensions screen

**Strategic spec:** [`../S0575_streams-toggle-welcome-entrypoints.md`](../S0575_streams-toggle-welcome-entrypoints.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none
**Steps done:** 5 / 5
**Started:** 2026-06-21
**Completed:** 2026-06-21

## Step Log

- 2026-06-21 - Steps 03.1-03.5 Verification PASS. Strings added EN/RU/UK (Cyrillic verified via Grep, no mojibake; parity gate exit 0). `.\a.ps1 fc` -> BUILD SUCCESSFUL (new ExtensionItem.Catalog, exhaustive when across download/uninstall/sectionTitleRes, CatalogImportResult->DownloadProgress map). Dev logs batched at Phase 07.

---

## Objective

Add a downloadable "Streams catalog" item to the Extensions screen as a new `ExtensionItem.Catalog` variant in its own `ExtensionSection.STREAMS`, whose download action runs `ImportStreamCatalogUseCase`. The catalog is not a `DeliverableSet`, so it must NOT be routed through `DeliverableDownloadRunner`.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`CapabilityAvailability.isStreamsAvailable()` exists).
- [ ] `ImportStreamCatalogUseCase` exists (S0570) - confirmed present, returns `CatalogImportResult`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/delivery/DeliverableInventory.kt` | Modified | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/DeliverableInventoryImpl.kt` | Modified | ≤ 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/delivery/ExtensionsManagerFragment.kt` | Modified | ≤ 340 |
| `app_v2/src/main/res/values/strings.xml` (+ `values-ru`, `values-uk`) | Modified | n/a |

---

## Steps

### Step 03.1 - Add `ExtensionSection.STREAMS` and `ExtensionItem.Catalog`

**Files:** `domain/delivery/DeliverableInventory.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Append `STREAMS` to the `ExtensionSection` enum. Add a new `ExtensionItem` subtype `data class Catalog(...)` carrying the abstract members only (`id`, `displayNameRes`, `descriptionRes`, `sizeLabel`, `section`, `statusFlow`) - no `DeliverableSet`, because the stream catalog is fetched directly, not via the deliverable runner. Keep `Module` and `LanguageData` unchanged.

**Verification:**

- `Grep` - `enum class ExtensionSection` line contains `STREAMS`.
- `Grep` - `data class Catalog(` matches once in `DeliverableInventory.kt`.

**Status:** `[x]` done

---

### Step 03.2 - Emit the catalog item in the inventory

**Files:** `data/delivery/DeliverableInventoryImpl.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Inject `ImportStreamCatalogUseCase` into the constructor. In `getExtensions()`, after the existing sections, add `if (capabilityAvailability.isStreamsAvailable()) { add(ExtensionItem.Catalog(id = "stream_catalog", displayNameRes = R.string.ext_streams_title, descriptionRes = R.string.ext_streams_desc, sizeLabel = <small pinned estimate via formatBytes>, section = ExtensionSection.STREAMS, statusFlow = statusFlowFor(<that id>))) }`. The status flow uses the existing `statusFlowFor(item.id)` `MutableStateFlow` pattern (default `NotInstalled`), so no repository query is needed.

**Verification:**

- `Grep` - `ExtensionItem.Catalog(` matches once in `DeliverableInventoryImpl.kt`.
- `Grep` - `import ...domain.usecase.streams.ImportStreamCatalogUseCase` present and `ImportStreamCatalogUseCase` appears in the constructor parameter list.

**Status:** `[x]` done

---

### Step 03.3 - Route catalog download/uninstall

**Files:** `data/delivery/DeliverableInventoryImpl.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> In `download(item)`, add an `is ExtensionItem.Catalog ->` branch that returns a `Flow<DownloadProgress>` emitting `Queued`, then runs `importStreamCatalogUseCase()` and maps its `CatalogImportResult` to a terminal `DownloadProgress`: `Success -> Installed`, `Empty -> Failed("empty")`, `Failure(reason) -> Failed(reason)`. Keep the existing per-id status-flow plumbing so the row reflects progress. In `uninstall(item)`, add an `is ExtensionItem.Catalog ->` branch: set its status flow to `NotInstalled` (catalog rows are re-importable; do not delete user-added manual sources here - strategic §6 "off keeps data").

**Verification:**

- `Grep` - `is ExtensionItem.Catalog ->` matches at least twice in `DeliverableInventoryImpl.kt` (download + uninstall when-branches).
- `Grep` - `DownloadProgress.Installed` referenced inside the catalog mapping.

**Status:** `[x]` done

---

### Step 03.4 - Render the STREAMS section header

**Files:** `ui/delivery/ExtensionsManagerFragment.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> In the row builder that inserts a section header when `item.section` changes, map `ExtensionSection.STREAMS` to the new `R.string.ext_section_streams` header. Follow the exact pattern used for the OCR / TRANSLATION / MEDIA_PLAYBACK headers; do not special-case rendering of the `Catalog` row otherwise (it renders like any item via its abstract members).

**Verification:**

- `Grep` - `ExtensionSection.STREAMS` matches in `ExtensionsManagerFragment.kt`.
- `Grep` - `ext_section_streams` matches in `ExtensionsManagerFragment.kt`.

**Status:** `[x]` done

---

### Step 03.5 - Add the Streams extension strings (trilingual)

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`
**Depends on:** Step 03.2, Step 03.4

**Prompt for developer:**

> Add `ext_streams_title`, `ext_streams_desc`, and `ext_section_streams` in EN/RU/UK in one lockstep call: `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key <key> -En "..." -Ru "..." -Uk "..."` per key. Copy must follow `docs/COMMUNICATION_POLICY.md` §2 (informative label/description) and pass the §6 tone checklist (concise, no marketing, Ё/ё in RU). Section header e.g. EN "Streams", description names "download the catalog of available stream sources".

**Verification:**

- `Grep` - `ext_streams_title`, `ext_streams_desc`, `ext_section_streams` each present in all three `strings.xml` files.
- Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "ext_streams"` - exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] On a `standard` build the Extensions screen shows a "Streams" section with the catalog row; tapping it triggers a catalog import that ends Installed or Failed.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regen deferred to Phase 07 (new `ExtensionItem.Catalog`).

---

## Handoff Notes to Next Phase

- The Streams catalog download path goes through `ImportStreamCatalogUseCase`, never `DeliverableDownloadRunner`; reuse this for the Welcome row (Phase 05) rather than a second copy.

---

## Rollback Plan

Revert the phase commit(s) - the new enum value and item variant are additive; no persistence or migration touched.
