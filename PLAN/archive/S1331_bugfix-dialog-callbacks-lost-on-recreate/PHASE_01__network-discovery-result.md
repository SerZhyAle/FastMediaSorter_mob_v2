# Phase 01 - Network discovery returns its host through FragmentResult

**Strategic spec:** [`../S1331_bugfix-dialog-callbacks-lost-on-recreate.md`](../S1331_bugfix-dialog-callbacks-lost-on-recreate.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-07-31
**Completed:** 2026-07-31

---

## Objective

`NetworkDiscoveryDialog` delivers the picked host through `setFragmentResult`, and `AddResourceActivity`
receives it through a listener registered in its own `onCreate`, so a host picked after the activity is
recreated still fills the SMB server field and starts the share scan.

Highest exposure of the five conversions: the user opens this dialog and waits inside it while a subnet scan
runs, so it is the dialog most likely to be on screen when a recreation event lands, and the most likely to be
left open while the user switches away and the process is killed.

---

## Prerequisites

- [x] Working tree is clean or on a feature branch.
- [x] `temp/CODE.LOCK` acquired via `scripts/utils/enter-code-lock.ps1 -Reason "S1331 phase 01"`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/NetworkDiscoveryDialog.kt` | Modified | ≤ 210 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceActivity.kt` | Modified | ≤ 560 |

`AddResourceActivity.kt` is 529 lines, over the 500-line threshold - take a timestamped backup into
`temp/S1331/` before editing it (Rule 5). No landscape layout work: this phase changes no XML.

---

## Steps

### Step 01.1 - Back up the oversized host file

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Copy `AddResourceActivity.kt` to `temp/S1331/AddResourceActivity.<yyyyMMdd-HHmmss>.kt.bak` before any edit,
> per Rule 5 for files over 500 lines.

**Verification:**

- `Glob` - `temp/S1331/AddResourceActivity.*.kt.bak` matches at least one file.

**Status:** `[x]` done

---

### Step 01.2 - Emit the picked host as a FragmentResult

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/NetworkDiscoveryDialog.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Delete the `var onHostSelected: ((NetworkHost) -> Unit)? = null` field. Add to the companion object:
> `RESULT_KEY = "network_discovery_result"`, `RESULT_HOST_IP`, `RESULT_HOST_NAME`, `RESULT_HOST_PORTS`, and a
> private `ARG_REQUEST_KEY`. Change `newInstance()` to `newInstance(requestKey: String = RESULT_KEY)` and put
> the key into `arguments` with `bundleOf`. Add an `onCreate` override that reads the key into a
> `private var requestKey: String = RESULT_KEY` field from `arguments`, so a restored instance recovers it.
> In the `NetworkHostAdapter` click lambda replace `onHostSelected?.invoke(host)` with
> `setFragmentResult(requestKey, bundleOf(RESULT_HOST_IP to host.ip, RESULT_HOST_NAME to host.hostname,
> RESULT_HOST_PORTS to host.openPorts.toIntArray()))`, keeping the `dismiss()` that follows.
> `NetworkHost` is a plain data class in `domain/usecase/DiscoverNetworkResourcesUseCase.kt` and must stay
> that way - pass its three fields as bundle primitives rather than making the domain model `Parcelable`.
> Replace the class KDoc gap with a short note naming S1331 and why the key comes from `arguments`, matching
> the tone of the `SearchableLanguagePickerDialog` header.

**Verification:**

- `Grep` - `var onHostSelected` returns zero hits in `NetworkDiscoveryDialog.kt`.
- `Grep` - `setFragmentResult(` matches in `NetworkDiscoveryDialog.kt`.
- `Grep` - `ARG_REQUEST_KEY` matches in `NetworkDiscoveryDialog.kt`.
- `Grep` - `override fun onCreate(savedInstanceState: Bundle?)` matches in `NetworkDiscoveryDialog.kt`.
- `Grep` - `Parcelize` returns zero hits in `domain/usecase/DiscoverNetworkResourcesUseCase.kt`.

**Status:** `[x]` done

---

### Step 01.3 - Receive the host in the activity's own onCreate

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceActivity.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> In `AddResourceActivity.onCreate`, register
> `supportFragmentManager.setFragmentResultListener(NetworkDiscoveryDialog.RESULT_KEY, this) { _, bundle -> .. }`.
> The lambda reads the three payload keys, sets `binding.etSmbServer` from the IP, and calls
> `viewModel.scanShares(..)` with exactly the arguments the current inline lambda at the `btnScanNetwork`
> click handler passes. Then reduce that click handler to `NetworkDiscoveryDialog.newInstance()
> .show(supportFragmentManager, NetworkDiscoveryDialog.TAG)` and delete the `dialog.onHostSelected = { .. }`
> assignment. Registration must sit in `onCreate`, not in the click handler, so a recreated activity restores
> the listener before the restored dialog resumes.

**Verification:**

- `Grep` - `setFragmentResultListener(` matches in `AddResourceActivity.kt`.
- `Grep` - `onHostSelected` returns zero hits across `app_v2/src`.
- `Grep` - `NetworkDiscoveryDialog.RESULT_KEY` matches in `AddResourceActivity.kt`.
- `Grep` - `scanShares(` matches in `AddResourceActivity.kt`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] No `Timber.d("S1331` probe was added by this phase. The six probes were inserted once, at the final `BlockNeedUserTest` transition.
- [x] `Grep` - `Log.d(` returns zero hits in both touched files.
- [x] Dev log entry added. One entry for the ticket, not one per touched file - CLAUDE.md journaling granularity.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - `newInstance` signature changed.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Establishes the house shape for the remaining four conversions: payload keys and `ARG_REQUEST_KEY` in the
companion, key read from `arguments` in `onCreate`, listener registered in the host's `onCreate`, domain models
decomposed into bundle primitives rather than made `Parcelable`.

---

## Rollback Plan

Revert the phase commit. No data migration, no schema change, no user-facing surface changed.
