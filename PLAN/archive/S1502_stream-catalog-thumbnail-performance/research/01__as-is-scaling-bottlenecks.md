# S1502 research 01 - what breaks first at 15k+ channels

**Question (strategic §6.1):** with a catalog of 15,000+ channels and thumbnails, what fails first on old hardware, and why?

**Method:** read-only code trace of the streams data path. The three top findings were re-verified by hand afterwards, because the first pass derived them from control flow rather than from a runtime measurement.

**Scale is real, not hypothetical.** `delivery/stream-catalog/streams.csv` currently carries 19,860 lines. The growth traces to S1476, whose own problem statement puts the pre-expansion catalog at 3,916 rows - so the working set grew roughly fivefold. Hardware floor for the `legacy` flavor: minSdk 23, 128 MB heap (`dev/TECH_REQUIREMENTS.md` lines 299-313); `docs/FLAVOR_MATRIX.md` line 29 confirms `SUPPORT_STREAMS` is live in `legacy`.

---

## Verified by hand

**1. The whole catalog is filtered and sorted on the main thread, once per typed character.**

`StreamsViewModel.kt:150-159` builds the UI state with
`combine(observeStreamSources(), _filter) { .. }.onEach { .. }.launchIn(viewModelScope)`.
There is no `flowOn` and no `withContext` anywhere in the file, so the transform runs in the
collector's context - `viewModelScope`, which is `Dispatchers.Main.immediate`. The transform calls
`applyFilter`, which walks every row. Each keystroke re-enters it.

**2. Per-row string allocation inside that main-thread pass.**

`StreamsViewModel.kt:602-605`. The *query* is hoisted and lowercased once - the KDoc at 592-596 says
so and it is true. The *row fields* are not: `source.title.lowercase()`, `source.topic?.lowercase()`
and `source.language?.lowercase()` allocate per row, per keystroke, whenever the earlier disjunct does
not short-circuit. At ~20k rows that is tens of thousands of transient Strings per character, against
a 128 MB heap.

**3. Ordering work on top of the filter.**

`StreamsViewModel.kt:588-589` partitions the matched set into pinned/unpinned and sorts the unpinned
tail. Same thread, same pass.

---

## Reported by the trace, not re-verified by hand

Listed separately on purpose - these are credible and evidenced, but I confirmed only the three above
myself, and a spec should not present the two classes of confidence as one.

- **A single-row write re-runs the entire chain.** `RecordStreamPlayOutcomeUseCase.kt:38-40` ->
  `StreamSourceRepository.kt:77-78` -> `StreamSourceDao.kt:104-108` updates one row; Room then
  invalidates `observeAll()` (`StreamSourceDao.kt:18-19`), which re-emits the full table into
  `StreamsViewModel.kt:150`. Every grid capture outcome and every health-probe result that flips a
  row's status therefore pays for a full re-filter, re-sort and re-diff of ~20k rows.
- **No paging anywhere.** `StreamSourceDao.kt:18-19` / `StreamSourceRepository.kt:21` /
  `ObserveStreamSourcesUseCase.kt:11` return the whole table; `androidx.paging` is absent from the
  module.
- **The pinned/unpinned split happens three times per emission**, in `StreamsViewModel.kt:588`,
  `StreamsSectionsManager.kt:48,56` and `StreamsActivity.kt:812`.
- **DiffUtil over ~20k items** on a sort-mode flip approaches worst-case edit distance;
  `areContentsTheSame` compares a 17-field data class (`StreamSourceAdapter.kt:369-379`,
  `StreamGridAdapter.kt:417-427`). The comparison itself is off-main, but `dispatchUpdatesTo` is not.
- **Three full url-to-index maps live in memory at once** (logo, favicon, preview atlas stores), each
  up to ~20k entries, parsed through whole-document `org.json` (`StreamLogoAtlasStore.kt:47-74`).
- **Disk-cap enforcement rescans the frame directory on every capture save**
  (`StreamFramePersistentStore.kt:70-88`).

## Already bounded - do not "fix" these

- Grid frame capture is hard-limited to one at a time and to the visible range
  (`StreamFrameSnapshotManager.kt:76,296`, `StreamGridModeManager.kt:238-247`).
- The health-probe sweep is likewise visible-range only (`StreamsActivity.kt:791,811-825`).
- Per-tile bitmap decode is already random-access with an LRU (`StreamTilePackReader.kt:45-47`),
  which is what S1154 and S1445 delivered. Neither scales with catalog size.

## What no one has established

1. No on-device trace exists. Everything above is a cost *argument*, not a measured frame time on
   real API-23 hardware, and the ticket's acceptance cannot be settled without one.
2. No test exercises anything near 20k rows; all fixtures are small in-memory lists, so keystroke
   latency at real N is unmeasured in CI as well as on device.
3. Whether the owner's installed database already holds ~19.8k rows, or he is describing the state
   after running a catalog refresh, is not answerable from code.

## Out of scope, parked

`gridAdapter` / `pinnedAdapter` / `pinnedGridAdapter` never receive the full repaint that `adapter`
gets when artwork coordinates load (`StreamsActivity.kt:599,640,647,664`). A correctness gap
independent of catalog size - parked as **S1503**.

## Status

Resolved for the AS-IS question. Feeds strategic §1, §4 and §6.
