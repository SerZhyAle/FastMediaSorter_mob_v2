# Phase 02 — Dialog: ETA and Overall-Percent Display

**Strategic spec:** [`../S0074_copy-move-dialog-progress.md`](../S0074_copy-move-dialog-progress.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 5 / 5
**Started:** 2026-05-04
**Completed:** 2026-05-04

---

## Objective

Extend the progress dialog to display overall transfer percentage and ETA alongside the existing file counter and speed; throttle UI updates to 3 seconds.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`FileOperationProgress.Starting` has `totalOperationBytes`, `Processing` has `completedOperationBytes`).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/dialog_file_operation_progress.xml` | Modified | ≤ 100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/FileOperationProgressDialog.kt` | Modified | ≤ 260 |
| `app_v2/src/main/res/values/strings.xml` | Modified | existing + 2 strings |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | existing + 2 strings |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | existing + 2 strings |

---

## Steps

### Step 2.1 — Add `tvOverallPercent` and `tvEta` to layout

**Files:** `app_v2/src/main/res/layout/dialog_file_operation_progress.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> In `dialog_file_operation_progress.xml`, after the existing horizontal `LinearLayout` that holds `tvProgressText` (file counter) and `tvSpeed`, add a second horizontal `LinearLayout` with the same width/height (`match_parent` / `wrap_content`, `horizontal` orientation):
>
> - Left child: `TextView` with id `tvOverallPercent`, `layout_width="0dp"`, `layout_weight="1"`, same `textSize` as `tvProgressText` (`@dimen/text_size_small`), initial text `""`, `contentDescription="@string/transfer_overall_progress_desc"`.
> - Right child: `TextView` with id `tvEta`, `layout_width="wrap_content"`, same `textSize`, initial text `""`, `contentDescription="@string/transfer_eta_desc"`.
>
> Keep layout padding and margins consistent with the existing row above.

**Verification:**

- `Grep` — `tvOverallPercent` in `dialog_file_operation_progress.xml` returns exactly 1 match.
- `Grep` — `tvEta` in `dialog_file_operation_progress.xml` returns exactly 1 match.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 2/2 PASS. tvOverallPercent=1 match, tvEta=1 match.

---

### Step 2.2 — Add string resources (EN / RU / UK)

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** Step 2.1

**Prompt for developer:**

> Add the following two string resources to each locale file. Place them near the other `dialog_file_operation_progress_*` strings for discoverability.
>
> **values/strings.xml (EN):**
> ```xml
> <string name="transfer_overall_progress_desc">Overall transfer progress</string>
> <string name="transfer_eta_desc">Estimated time remaining</string>
> ```
>
> **values-ru/strings.xml (RU):**
> ```xml
> <string name="transfer_overall_progress_desc">Общий прогресс передачи</string>
> <string name="transfer_eta_desc">Оставшееся время</string>
> ```
>
> **values-uk/strings.xml (UK):**
> ```xml
> <string name="transfer_overall_progress_desc">Загальний прогрес передачі</string>
> <string name="transfer_eta_desc">Час, що залишився</string>
> ```

**Verification:**

- `Grep` — `transfer_overall_progress_desc` in `values/strings.xml` returns 1 match.
- `Grep` — `transfer_overall_progress_desc` in `values-ru/strings.xml` returns 1 match.
- `Grep` — `transfer_overall_progress_desc` in `values-uk/strings.xml` returns 1 match.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 3/3 PASS. transfer_overall_progress_desc present in EN/RU/UK.

---

### Step 2.3 — Wire new views and store `totalOperationBytes` in dialog

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/FileOperationProgressDialog.kt`
**Depends on:** Step 2.2

**Prompt for developer:**

> In `FileOperationProgressDialog`:
>
> 1. Add two `lateinit var` fields: `tvOverallPercent: TextView` and `tvEta: TextView`.
> 2. In `onCreate`, bind them: `tvOverallPercent = view.findViewById(R.id.tvOverallPercent)` and `tvEta = view.findViewById(R.id.tvEta)`.
> 3. Add a private field `private var totalOperationBytes: Long = 0L`.
> 4. In `updateProgress`, handle the `FileOperationProgress.Starting` branch: store `progress.totalOperationBytes` into `totalOperationBytes`.
> 5. Change `UPDATE_INTERVAL_MS` from `500L` to `3000L`.
>
> Do not yet implement ETA calculation — that is Step 2.4. Leave `tvOverallPercent` and `tvEta` set to `""` for now to keep the dialog compilable.

**Verification:**

- `Grep` — `tvOverallPercent` in `FileOperationProgressDialog.kt` returns ≥ 2 matches (declaration + bind).
- `Grep` — `tvEta` in `FileOperationProgressDialog.kt` returns ≥ 2 matches.
- `Grep` — `3000L` in `FileOperationProgressDialog.kt` returns exactly 1 match (the `UPDATE_INTERVAL_MS` constant).
- `Grep` — `totalOperationBytes` in `FileOperationProgressDialog.kt` returns ≥ 2 matches.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 4/4 PASS. tvOverallPercent≥2, tvEta≥2, 3000L=1, totalOperationBytes≥2.

---

### Step 2.4 — Implement ETA calculation and update `applyProgressToUI`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/FileOperationProgressDialog.kt`
**Depends on:** Step 2.3

**Prompt for developer:**

> Add an ETA calculation mechanism and update `applyProgressToUI` as follows:
>
> **ETA state fields** (add to class body):
> ```kotlin
> private val speedSamples = ArrayDeque<Long>(10)   // last-N speed values for smoothing
> private val MAX_SPEED_SAMPLES = 10
> ```
>
> **Helper: `formatEta(etaSeconds: Long): String`** — convert seconds to a compact human-readable string:
> - < 60 s  → `"< 1 min"`
> - 60–3599 s → `"~Xm Ys"` (e.g. `"~2m 15s"`)
> - ≥ 3600 s → `"~Xh Ym"` (e.g. `"~1h 5m"`)
>
> **In `applyProgressToUI(progress: FileOperationProgress.Processing)`:**
>
> 1. Accumulate speed sample: if `progress.speedBytesPerSecond > 0`, add it to `speedSamples`; if size > `MAX_SPEED_SAMPLES`, remove the oldest.
>
> 2. Compute overall percent:
>    ```kotlin
>    val overallPercent = if (totalOperationBytes > 0L) {
>        (progress.completedOperationBytes * 100L / totalOperationBytes).toInt().coerceIn(0, 99)
>    } else null
>    ```
>    Cap at 99 until the `Completed` event is received. When `totalOperationBytes == 0L` (e.g. network files with unknown size), `overallPercent` is `null` — hide the view.
>
> 3. Compute ETA:
>    ```kotlin
>    val avgSpeed = if (speedSamples.isNotEmpty()) speedSamples.average().toLong() else 0L
>    val remainingBytes = if (totalOperationBytes > 0L) totalOperationBytes - progress.completedOperationBytes else 0L
>    val etaSeconds = if (avgSpeed > 0L && remainingBytes > 0L) remainingBytes / avgSpeed else -1L
>    ```
>
> 4. Update views:
>    - `progressBar`: set `max = 100`, `progress = overallPercent ?: (progress.currentIndex * 100 / progress.totalFiles.coerceAtLeast(1))`
>    - `tvOverallPercent`: `"$overallPercent%"` if not null, else `""`
>    - `tvEta`: `formatEta(etaSeconds)` if `etaSeconds > 0` and `overallPercent != null`, else `""`
>    - `tvProgress`: keep showing `"${progress.currentIndex + 1} / ${progress.totalFiles}"` unchanged
>    - `tvSpeed`: keep as-is (format speed only)
>
> 5. Set `ContentDescription` of the dialog root (or `tvOverallPercent`) to `"${overallPercent ?: "—"}% — ETA: ..."` for TalkBack.

**Verification:**

- `Grep` — `speedSamples` in `FileOperationProgressDialog.kt` returns ≥ 2 matches.
- `Grep` — `formatEta` in `FileOperationProgressDialog.kt` returns ≥ 2 matches (declaration + call).
- `Grep` — `completedOperationBytes` in `FileOperationProgressDialog.kt` returns ≥ 1 match.
- `Grep` — `coerceIn(0, 99)` in `FileOperationProgressDialog.kt` returns 1 match (the percent cap).
- `Grep` — `Log\.d\(` in `FileOperationProgressDialog.kt` returns 0 hits (Timber only).

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 5/5 PASS. speedSamples≥2, formatEta=2, completedOperationBytes≥1, coerceIn(0,99)=1, Log.d=0.

---

### Step 2.5 — Apply pending progress on dialog show and handle completion

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/FileOperationProgressDialog.kt`
**Depends on:** Step 2.4

**Prompt for developer:**

> Verify and, if necessary, update two edge-case behaviours:
>
> 1. **Completion bypass**: in the `FileOperationProgress.Completed` branch of `updateProgress`, before calling `dismiss()`, reset `tvOverallPercent` to `"100%"` and `tvEta` to `""` so the user briefly sees 100% if the dialog was already visible. Then dismiss immediately (no additional delay).
>
> 2. **`onStart` pending state**: `pendingProgress?.let { applyProgressToUI(it) }` already exists — confirm it still compiles correctly now that `applyProgressToUI` also reads `totalOperationBytes` (which is stored in a field, not passed as a parameter). No code change needed if it already works; add a `Timber.d` trace log only if it does not.
>
> 3. **`lastUpdateTime` reset**: ensure `lastUpdateTime` is reset to `0L` when a new `Starting` event is received, so the first update within the 3-second window is always shown immediately.

**Verification:**

- `Grep` — `"100%"` in `FileOperationProgressDialog.kt` returns 1 match (the completion branch).
- `Grep` — `lastUpdateTime = 0L` in `FileOperationProgressDialog.kt` returns 1 match (in the Starting branch).
- `Grep` — `Log\.d\(` in `FileOperationProgressDialog.kt` returns 0 hits.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 3/3 PASS. "100%"=1, lastUpdateTime=0L=1, Log.d=0.

---

## Phase Done Criteria

- [ ] Every `Step 2.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for each modified file via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

After this phase: the dialog shows overall %, speed, and ETA; updates at most every 3 seconds; TalkBack receives a content description with the current percent. Phase 03 is docs and catalog only.

---

## Rollback Plan

Revert phase commit(s) — no data migration or persistent state changed. The dialog reverts to file-count progress bar only.
