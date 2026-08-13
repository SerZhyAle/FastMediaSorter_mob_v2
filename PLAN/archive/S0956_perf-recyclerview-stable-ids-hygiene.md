# S0956 - RecyclerView adapters lack stable IDs (drag-reorder identity, DiffUtil)

**Status:** Archived
**Priority:** 40
**Date:** 2026-07-05
**Tier:** 3 - Moderate (ad-hoc)

<!-- parked by S0905 audit sweep (Layer 6) - 2026-07-05 -->

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-05, из S0905 Layer 6 static perf sweep.

Symptom: no content adapter in `app_v2/src/main` declares stable IDs (`setHasStableIds(true)` + `getItemId()`). Only unrelated `RemoteViewsService` widget adapters implement `getItemId`. Drag-reorder-capable adapters (`MediaFileAdapter.moveItem`, `ResourceAdapter` via `AdapterDragController`) would let `DefaultItemAnimator`/DiffUtil track identity across `notifyItemMoved` more reliably with stable IDs. `setHasFixedSize`/shared `RecycledViewPool` are also unused module-wide.

Evidence:
- Absence confirmed via Grep for `setHasStableIds`/`override fun getItemId` across `app_v2/src/main` (only `widget/ScheduledTasksWidgetService.kt:105`, `widget/FavoritesWidgetService.kt:121` match, both unrelated widgets).
- Drag flow: `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/MediaFileAdapter.kt:125-127` (`moveItem`).

Severity: P2 (missing optimization; candidate for a project-wide convention/gate, not a one-off fix).

Scope note: decide whether to adopt stable IDs as a convention (which adapters, keyed on `MediaFile.path`/`MediaResource.id`) and whether to encode it as a mechanical gate.

## 1. Codebase findings (2026-07-06, autonomous research)

The static sweep confirmed the *absence* of stable IDs but did not evaluate whether they would help these specific adapters. On inspection the premise largely misfires:

- Both drag-reorder-capable content adapters are already `ListAdapter` + `DiffUtil`, with sound item identity:
  - `ui/main/ResourceAdapter` (`ListAdapter<MediaResource>`) - `areItemsTheSame = oldItem.id == newItem.id`; `MediaResource.id` is a unique `Long` (DB id).
  - `ui/browse/MediaFileAdapter` (`ListAdapter<MediaFile>`) - `MediaFileDiffCallback.areItemsTheSame = oldItem.path == newItem.path`, with rich `getChangePayload` (favorite / audio-metadata partial rebinds).
- `ListAdapter`/`AsyncListDiffer` dispatches granular `notifyItemMoved`/`notifyItemChanged` computed by `DiffUtil` and **never calls `notifyDataSetChanged()`**. Stable IDs exist mainly to let RecyclerView correlate items across a `notifyDataSetChanged()` and preserve view state; with DiffUtil already emitting move ops, `DefaultItemAnimator` animates reorders by position and identity is already tracked. So stable IDs add **no** drag-reorder or DiffUtil benefit here.
- On `MediaFileAdapter` there is no unique `Long` key - `getItemId()` would have to hash `path` (`path.hashCode().toLong()`), whose 32-bit collisions across a folder listing would raise `IllegalStateException: Two different ViewHolders ... same stable ID` and crash the list. Net-negative: real crash risk for zero gain.
- `setHasFixedSize(true)` is the only safe residual, but it belongs on the RecyclerView (layout/fragment), is valid only where the RecyclerView's own size is content-independent (full-bleed `match_parent` lists), and its benefit is marginal and unmeasurable without a macrobenchmark (device).
- A shared `RecycledViewPool` needs two+ RecyclerViews sharing view types on one screen; the browse/resource flows have no such surface, so it does not apply.

## 2. Recommendation (owner disposition)

- **Reject** `setHasStableIds(true)` on `MediaFileAdapter` and `ResourceAdapter` - it is net-negative (crash risk on the path-keyed adapter, no benefit over the existing DiffUtil identity).
- Do **not** encode a "stable IDs required" mechanical gate; it would be a false-positive convention against the ListAdapter+DiffUtil pattern the app correctly uses.
- Optional, separate, low-value: `setHasFixedSize(true)` on the full-bleed browse/resource RecyclerViews - only if a macrobenchmark later shows a measurable relayout cost; not worth a blind change now.
- Disposition is an owner call: **archive** this idea as won't-do (evidence above), or keep a narrowed marginal-opt backlog item for the `setHasFixedSize` micro-opt. Not autonomously implementable as framed.

## Related

- S0905 (audit-tail sweep, source); docs/CODE_AUDIT_PROTOCOL.md Layer 6/8.
