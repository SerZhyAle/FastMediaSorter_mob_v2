# Phase 02 - Widen the manifest export to the documentation scope

**Strategic spec:** [`../S1313_settings-manifest-blind-to-dialog-settings.md`](../S1313_settings-manifest-blind-to-dialog-settings.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, 04, 05
**Steps done:** 3 / 3
**Started:** 2026-08-01
**Completed:** 2026-08-01

---

## Objective

Let `LayoutSettingsSearchSource` scan an arbitrary layout list, make `SettingsManifestExportTest` merge the search scope with the documentation scope, and regenerate `docs/settings/settings-manifest.json` so dialog-hosted settings appear in it.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/LayoutSettingsSearchSource.kt` | Modified | ≤ 300 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/settings/search/SettingsManifestExportTest.kt` | Modified | ≤ 175 |
| `docs/settings/settings-manifest.json` | Modified (generated) | n/a |

---

## Steps

### Step 02.1 - Add a layout-list overload to the scan source

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/LayoutSettingsSearchSource.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Extract the body of `override fun collect()` into `fun collect(layoutResIds: List<Int>): List<RawSettingsSearchEntry>` and make `collect()` delegate with `SettingsSearchLayoutCatalog.layoutResIds`. The overload is not part of the `SettingsSearchSource` interface - only the concrete class exposes it, so injected consumers keep seeing the search-scope-only contract. Keep the existing per-layout try/catch and the `TRANSIENT_ACTION_BUTTON_IDS` de-indexing untouched. Update the class KDoc to say the no-arg overload walks the search catalog while the list overload serves the documentation scope.

**Verification:**

- `Grep` - `fun collect(layoutResIds: List<Int>): List<RawSettingsSearchEntry>` matches exactly once.
- `Grep` - `override fun collect(): List<RawSettingsSearchEntry>` matches exactly once (implemented as `= collect(SettingsSearchLayoutCatalog.layoutResIds)`, one-line delegation rather than a separate body - same effect, simpler).
- `Grep` - `SettingsSearchLayoutCatalog.layoutResIds` matches exactly once in that file.
- `.\a.ps1 fk` exits 0.

**Status:** `[x] done`

---

### Step 02.2 - Merge the documentation scope into `buildManifest()`

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/settings/search/SettingsManifestExportTest.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `buildManifest()`, keep the existing search-scope loop that routes through `SettingsSearchTabMapping.assignmentFor`. After it, add a second loop over `SettingsDocScopeCatalog.surfaces` that calls `source.collect(listOf(surface.layoutResId))` and builds a `SettingsManifestEntry` per raw row using `surface.sectionId` and `surface.destination.name`. Reuse the existing `resolveTitle` / `resourceName` helpers and keep the existing `if (titles.values.all { it.isBlank() }) continue` skip so title-less rows stay out. Emit the search scope first so existing manifest order is preserved and the diff is append-only. Update the class KDoc: the manifest now covers both the navigable search index and documentation-only surfaces, and the two scopes come from two disjoint catalogs.

**Verification:**

- `Grep` - `SettingsDocScopeCatalog.surfaces` matches exactly once in that file.
- `Grep` - `source.collect(listOf(` matches exactly once in that file.
- `Grep` - `if (titles.values.all { it.isBlank() }) continue` matches exactly twice in that file.
- `.\a.ps1 fk` exits 0.

**Status:** `[x] done`

---

### Step 02.3 - Regenerate the committed manifest

**Files:** `docs/settings/settings-manifest.json`
**Depends on:** Step 02.2

**Prompt for developer:**

> Regenerate the manifest from the live scan by running the export test in generate mode. Do not hand-edit the JSON. Expect an append-only diff: the pre-existing entries keep their order and content, and dialog-hosted rows are appended. Record `expected: 209 entries | actual: <N>` in the step notes. The annotation gate is expected to be RED after this step - Phase 03 closes it.
>
> **Actual:** expected 209 (plan's baseline) | actual 209 pre-existing entries confirmed unchanged in
> order/content, plus 49 new documentation-scope entries (6 surfaces, corrected set per Phase 01) = 258
> total. First regeneration attempt (with the plan's original 9-surface table) produced 41 new keys
> including generic noise (`btnApply`/`btnCancel`/`btnOk`/volume-brightness-speed transport-control
> buttons from `dialog_playback_control`); after Correction 2 dropped the three noisy/incomplete
> surfaces, the clean regeneration produced exactly 49 new keys as above.

```powershell
pwsh -NoProfile -File scripts/quality/reindex-settings.ps1
```

> `reindex-settings.ps1` exits 2 when it regenerated drift, which is the expected outcome here, and exits 3 if the verify gate fails on annotations. Both are acceptable at this step; only exit 1 (infrastructure failure) is a real problem.

**Verification:**

- `Grep` - `"layout": "dialog_launcher_settings"` matches at least once in `docs/settings/settings-manifest.json`.
- `Grep` - `"sectionId": "launcher"` matches at least once in `docs/settings/settings-manifest.json`.
- `Grep` - `"key": "rowLauncherShowTray"` matches exactly once in `docs/settings/settings-manifest.json`.
- `Grep` - `"key": "rowLauncherLockDesktop"` matches exactly once in `docs/settings/settings-manifest.json`.
- Value equality - entry count parsed from the JSON is strictly greater than 209. Actual: 258.
- `Grep` - `"layout": "dialog_add_stream"` returns zero hits (per-entity editor stays out of scope). Confirmed.

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` exits 0.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] `Grep -n "Log\.d\("` returns zero hits in both modified `.kt` files.
- [x] Dev log entry added for every file in "Files Touched" (batched with Phase 06 closure entry).
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The manifest now carries dialog-hosted keys with new `sectionId` values. Two downstream artifacts are knowingly stale until Phases 03 and 04 land: `check-settings-annotations.ps1` fails on the new unannotated keys, and `render-settings-reference.ps1` silently drops the new sections because `$sectionOrder` does not list them yet.

---

## Rollback Plan

Revert the two `.kt` files and re-run `scripts/quality/reindex-settings.ps1` to restore the narrower manifest. No data migration or user-facing surface changed.
