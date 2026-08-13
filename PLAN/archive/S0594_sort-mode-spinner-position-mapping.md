# Strategic Specification: S0594 - Sort-mode selector position/value mapping inconsistency

**Ticket:** S0594
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-21
**Tier:** 3 - Standard

<!-- auto-approved by /spec-all - 2026-06-21 -->

---

## 0. Raw capture (auto-parked by /spec-dev during S0567)

Discovered while migrating the Playback settings Sort-Mode selector to `SettingsDropdownRow` (S0567 Phase 03).

Symptom: the Sort-Mode selector's visible option list and the persisted `SortMode` value are mapped by raw list position, but the legacy adapter's 8 hardcoded English labels ("Name (A-Z)", ..) do not line up with `SortMode.entries[0..7]` order, which is `RANDOM, NAME_ASC, NAME_DESC, DATE_ASC, DATE_DESC, SIZE_ASC, SIZE_DESC, MANUAL`. So selecting visible position 0 persists `RANDOM`, etc. - the displayed label can disagree with the saved sort mode.

Evidence:
- `app_v2/src/main/res/layout/fragment_settings_playback.xml` - Sort-Mode field (now `SettingsDropdownRow id=spinnerSortMode`).
- `PlaybackSettingsFragment.kt` - selection persisted as `SortMode.entries[position]`; visible entries built from `getSortModeName` of the first 8 ordinals.
- Pre-existing: the S0567 migration preserved the exact prior mapping (no regression introduced); this ticket is to correct the mapping itself.

Scope note: this is a behavioural/data-correctness bug, independent of the S0567 visual-unification work. Not fixed under S0567 to avoid changing persisted-setting semantics inside a UI-refactor ticket.

---

## Goal (RU)

Сделать маппинг видимых пунктов селектора сортировки в Playback-настройках явным: каждый видимый пункт привязан к конкретному значению `SortMode`, а не к сырому индексу `SortMode.entries`. Это убирает хрупкую неявную связку (любое переупорядочивание enum молча меняет состав и порядок видимых пунктов) и приводит фрагмент к уже принятому в проекте паттерну `BrowseSortDialogManager.DIALOG_SORT_ORDER`. Видимое поведение (8 универсальных режимов, тот же порядок и подписи) сохраняется без изменений.

---

## 1. Problem and analysis

Post-S0567, the Playback Sort-Mode selector is internally consistent (display, write-back and read-back all derive from `SortMode.entries` order, so the shown label currently matches the saved value). The defect is structural, not a live label/value mismatch:

- Visible entries: `SortMode.entries.take(SORT_MODE_VISIBLE_COUNT).map { getSortModeName(it) }`.
- Write-back: `SortMode.entries[position]`.
- Read-back: `setSelection(ordinal)` guarded by `ordinal < SORT_MODE_VISIBLE_COUNT`.

All three couple the visible set, its order, and the persisted value implicitly to the enum declaration order. A future reorder of `SortMode` (e.g. inserting a value, or moving `MANUAL`) silently changes which 8 modes appear and in what order, and any partial edit to one of the three sites reintroduces exactly the label/value divergence described in §0. The persistence layer must stay free to reorder the enum (the comment on `BrowseSortDialogManager.DIALOG_SORT_ORDER` states this invariant).

Goal-2 audit (read-back paths that map a stored `SortMode` to a selector position):
- `BrowseSortDialogManager` - already explicit via `DIALOG_SORT_ORDER` (reference pattern). No risk.
- `BrowseSortMenuManager` - display and write-back both index the same per-show captured `getRelevantSortModes(..)` array; internally consistent. No risk.
- `PlaybackSettingsFragment` - the only site coupling positions to `SortMode.entries`/`ordinal`. This is the fix scope.

## 2. Goals

1. Map each visible option to an explicit `SortMode` value (not by raw index), so the persisted value cannot drift from the displayed label under enum reordering.
2. Confirm read-back paths are free of the same off-by-order risk (done in §1 audit; only the Playback fragment needs the change).

## 3. Design decision (resolves prior open question)

The Playback default-sort selector exposes a **curated subset** of `SortMode`, not the full enum: the 8 media-type-agnostic modes (`RANDOM, NAME_ASC, NAME_DESC, DATE_ASC, DATE_DESC, SIZE_ASC, SIZE_DESC, MANUAL`). Media-specific modes (artist/title/duration/dateTaken/type) are intentionally excluded from a global default and are offered only in the browse selectors. Preserve this exact set and order; bind labels to values via an explicit ordered `List<SortMode>` (parity with `DIALOG_SORT_ORDER`).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0567 (migration that introduced the current `SettingsDropdownRow` selector), S0258 (settings-row widgets).
- **Behaviour parity:** no change to visible options, their order, labels, or persisted semantics; the 8-mode subset and read/write behaviour are byte-identical to the current build.

---

## Phase 01 - Explicit visible-option -> SortMode mapping

**File:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt`

- [x] Add a `private val PLAYBACK_SORT_MODES: List<SortMode>` in the companion object listing the 8 visible modes in display order (`RANDOM, NAME_ASC, NAME_DESC, DATE_ASC, DATE_DESC, SIZE_ASC, SIZE_DESC, MANUAL`), with a WHY comment (explicit map keeps enum declaration order free for persistence; parity with `BrowseSortDialogManager.DIALOG_SORT_ORDER`).
  - Verification: `PLAYBACK_SORT_MODES` declared; contains exactly those 8 values in that order. PASS.
- [x] In `setupViews()`, build entries from `PLAYBACK_SORT_MODES.map { getSortModeName(it) }`; in the item-selected listener resolve `PLAYBACK_SORT_MODES.getOrNull(position)` and persist it (early-return on null).
  - Verification: no `SortMode.entries[position]` and no `SortMode.entries.take(..)` remain in the file. PASS (grep: 0 hits).
- [x] In `observeData()`, replace the ordinal-based selection with `binding.spinnerSortMode.setSelection(PLAYBACK_SORT_MODES.indexOf(settings.defaultSortMode))` (a mode outside the subset yields `-1` -> unselected, preserving current behaviour).
  - Verification: no reference to `.ordinal` for sort-mode selection remains; `indexOf` used. PASS (grep: 0 hits).
- [x] Remove the now-unused `SORT_MODE_VISIBLE_COUNT` constant.
  - Verification: `grep SORT_MODE_VISIBLE_COUNT` returns no hits in the module. PASS.
- [x] Compile.
  - Verification: `.\a.ps1 dq` (assembleStandardDebug) exits 0. PASS (BUILD SUCCESSFUL in 1m 26s, full compileStandardDebugKotlin).

---

## 4. Out of scope

- No change to `SortMode` enum order or members.
- No change to the browse-screen selectors (audited clean).
- No change to visible options, labels, ordering, or persisted values (pure internal hardening).

---

## Last Audit

**Date:** 2026-06-21
**Verdict:** Verified

- Goal-1 met: `PlaybackSettingsFragment` now maps visible options to explicit `SortMode` values via `PLAYBACK_SORT_MODES`; write-back uses `getOrNull(position)`, read-back uses `indexOf(value)`. No `SortMode.entries[..]`, `.take(..)`, `.ordinal`, or `SORT_MODE_VISIBLE_COUNT` remain (grep: 0 hits).
- Goal-2 met: browse read-back paths audited - `BrowseSortDialogManager.DIALOG_SORT_ORDER` already explicit; `BrowseSortMenuManager` indexes a per-show captured array. Neither needs a change.
- Behaviour parity proven by construction: `PLAYBACK_SORT_MODES` equals `SortMode.entries[0..7]` in identical order, so for the 8 visible modes `indexOf(m) == m.ordinal` and `PLAYBACK_SORT_MODES[p] == SortMode.entries[p]`; modes outside the subset yield `-1` (unselected), matching the prior `ordinal < 8 ? ordinal : -1` guard. Observable behaviour is unchanged - no on-device verification required.
- Build: `assembleStandardDebug` PASS (full `compileStandardDebugKotlin`, BUILD SUCCESSFUL in 1m 26s).
- No new user-visible capability delivered (internal hardening) - no `docs/ALL_FEATURES.jsonl` record.
