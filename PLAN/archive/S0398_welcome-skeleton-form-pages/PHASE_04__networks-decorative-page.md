# Phase 04 - Networks Decorative Page

**Strategic spec:** [`../S0398_welcome-skeleton-form-pages.md`](../S0398_welcome-skeleton-form-pages.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-06-11
**Completed:** 2026-06-11

---

## Objective

Add a decorative networks page (3 tiles: SMB (intranet), (S)FTP, Cloud) after page 0. The Cloud tile shows only when `MediaCapabilities.supportsCloud` is true. Static/informational only - S0391 later turns the tiles into group toggles (its §3.1.4).

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (page list is data-driven).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/page_welcome_networks.xml` | New | n/a |
| `app_v2/src/main/res/layout-land/page_welcome_networks.xml` | New | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomePagerAdapter.kt` | Modified | ≤ 360 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt` | Modified | ≤ 700 |
| `app_v2/src/main/res/values/strings_setup.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings_setup.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings_setup.xml` | Modified | n/a |

> New layout ships with its `layout-land` counterpart from the start (Rule 11).
> Welcome strings live in `strings_setup.xml`, not `strings.xml` (corrected from the original table).

---

## Steps

### Step 04.1 - Networks page strings

**Files:** `res/values{,-ru,-uk}/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add EN/RU/UK in one lockstep call: `welcome_networks_title`, `welcome_networks_description` (one informational line on remote sources), and the three tile labels `welcome_network_tile_smb` ("SMB (intranet)"), `welcome_network_tile_ftp` ("(S)FTP"), `welcome_network_tile_cloud` ("Cloud"). Copy passes COMMUNICATION_POLICY §6.

**Verification:**

- `Grep` - `welcome_networks_title` and `welcome_network_tile_smb` present in all three `values*/strings.xml`.
- `Bash` - `scripts/check_strings_localized.ps1 -KeyPrefix "welcome_network"` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-06-11 - Verification 2/2 PASS. Added 5 keys to `strings_setup.xml` EN/RU/UK lockstep: `welcome_networks_title`, `welcome_networks_description`, `welcome_network_tile_smb` ("SMB (intranet)"/"SMB (локальная сеть)"/…), `welcome_network_tile_ftp` ("(S)FTP"), `welcome_network_tile_cloud`. `check_strings_localized -KeyPrefix welcome_network` exit 0; §6 tone OK. Dev log recorded (EN/RU/UK).

---

### Step 04.2 - Networks page layout (portrait + land)

**Files:** `res/layout/page_welcome_networks.xml`, `res/layout-land/page_welcome_networks.xml`
**Depends on:** Step 04.1

**Prompt for developer:**

> Create the page layout in both orientations: pinned header (icon + `welcome_networks_title` + `welcome_networks_description`) over a row/grid of three tiles reusing the `item_welcome_feature_tile` visual style (tinted icon + label). Give the Cloud tile a stable id (`@id/tileNetworkCloud`) so the holder can hide it. Icons: reuse `ic_resource_smb`, an FTP/network icon, `ic_resource_cloud`. `?attr/` colours only.

**Verification:**

- `Glob` - both `page_welcome_networks.xml` files exist (default + land).
- `Grep` - `tileNetworkCloud` present in both files.
- `Grep` - `="#` returns zero hits in either file.

**Status:** `[x]` done

**Step Log:**

- 2026-06-11 - Verification 3/3 PASS. Created `page_welcome_networks.xml` (portrait + `layout-land`, lockstep ids). Centred header (`@mipmap/ic_launcher` + title + description) over a scroll-safe weighted row of three `MaterialCardView` tiles styled like `item_welcome_feature_tile`: `tileNetworkSmb` (`ic_resource_smb`), `tileNetworkFtp` (`ic_resource_ftp`), `tileNetworkCloud` (`ic_resource_cloud`). `?attr/` colours only, `="#"` hits = 0. Dev log recorded for both files.

---

### Step 04.3 - Render the page and gate the Cloud tile

**Files:** `ui/welcome/WelcomePagerAdapter.kt`, `ui/welcome/WelcomeActivity.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Add a `NetworksViewHolder` (new `VIEW_TYPE_NETWORKS`) inflating `page_welcome_networks.xml`; bind sets the Cloud tile visibility from a `WelcomePage.showCloudNetworkTile: Boolean` field. Add an `isNetworksPage` flag to `WelcomePage` and route it in `getItemViewType`. In WelcomeActivity insert the networks page into the candidate list at position 1 (immediately after page 0), with `showCloudNetworkTile = mediaCapabilities.supportsCloud`. The page is purely informational - no click handlers, no settings writes (S0391 adds toggles later).

**Verification:**

- `Grep` - `VIEW_TYPE_NETWORKS` and `NetworksViewHolder` present in WelcomePagerAdapter.kt.
- `Grep` - `showCloudNetworkTile = mediaCapabilities.supportsCloud` present in WelcomeActivity.kt.
- `Grep` - `isNetworksPage` present in WelcomePagerAdapter.kt.

**Status:** `[x]` done

**Step Log:**

- 2026-06-11 - Verification 3/3 PASS. Added `VIEW_TYPE_NETWORKS` + `NetworksViewHolder` (inflates `PageWelcomeNetworksBinding`; bind sets `tileNetworkCloud.isVisible = page.showCloudNetworkTile` + entrance animations), `WelcomePage.isNetworksPage`/`showCloudNetworkTile`, routed in `getItemViewType`/`onCreateViewHolder`/`onBindViewHolder`. WelcomeActivity inserts the networks page at index 1 with `showCloudNetworkTile = mediaCapabilities.supportsCloud`; no click handlers / settings writes. Catalog re-synced (NetworksViewHolder present); gates green. Dev log recorded for both files.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - `assembleStandardDebug` (1m44s) + `assembleLiteDebug` (2m26s) BUILD SUCCESSFUL; lite exercises `supportsCloud=false` Cloud-tile collapse.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry for every new/modified file (7 files).

---

## Handoff Notes to Next Phase

The decorative networks page renders after page 0; lite hides the Cloud tile via `supportsCloud`. S0391 will replace the static tiles with group toggles in place.

---

## Rollback Plan

Revert phase commit(s) - deletes the new layouts and the view-holder/insertion. No data surface.
