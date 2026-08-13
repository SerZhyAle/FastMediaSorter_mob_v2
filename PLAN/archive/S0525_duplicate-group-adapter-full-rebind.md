# S0525 - DuplicateGroupAdapter targeted selection updates

**Status:** Archived
**Priority:** 35
**Date:** 2026-06-19
**Tier:** 2 - Light (ad-hoc)
**Origin:** parked by /spec-all during S0512 research (2026-06-19)

<!-- auto-approved by /spec-all - 2026-06-19 -->

---

## 0. Идея (исходная, raw)

Найдено при research S0512.

Symptom: `DuplicateGroupAdapter.selectedFilePaths` setter вызывает `notifyDataSetChanged()` (line 23). При любом изменении выбора (а во время drag-select - на каждый pointer-moved position) это триггерит полный rebind всего списка, вызывая flicker при 60 fps на больших наборах дубликатов. Вложенный `FileAdapter` - сырой `ListAdapter` без корректно настроенного DiffUtil для частичных обновлений.

Evidence:
- `DuplicateGroupAdapter.kt:20-24` (setter -> notifyDataSetChanged)
- `FileAdapter` inner class (line 82-123) использует `FileDiffCallback`, но изменение `selectedFilePaths` обходит DiffUtil полностью

Действие: заменить full rebind на targeted updates через DiffUtil/payloads. Влияет на производительность drag-select (S0512), но самостоятельная оптимизация.

---

## 1. Goal

Заменить полный rebind списка дубликатов на точечные обновления при изменении выбора. Сейчас сеттер `selectedFilePaths` дёргает `notifyDataSetChanged()` на внешнем адаптере на каждое изменение выбора (а при drag-select из S0512 - на каждый pointer-move), что вызывает flicker и лишнюю работу на больших наборах. Дополнительно: внешний `notifyDataSetChanged()` не обновляет вложенные строки точечно (внутренний `submitList(group.files)` идёт с тем же референсом и схлопывается в no-op), поэтому чек-боксы за пределами непосредственно нажатого фактически не перерисовываются адресно. Цель: убрать `notifyDataSetChanged()`, обновлять только те строки вложенного `FileAdapter`, чей статус выбора реально изменился, через payload `notifyItemChanged`. Видимое поведение идентично, но без мерцания и без лишней нагрузки.

---

## 2. Phases

### Phase 01 - Replace full rebind with payload-targeted selection updates

Steps:
1. In `DuplicateGroupAdapter`, remove `notifyDataSetChanged()` from the `selectedFilePaths` setter. Capture the previous value, store the new value, early-return when the value is unchanged, otherwise dispatch a targeted selection refresh to every currently bound `GroupViewHolder`.
2. Track bound group holders in a backing `MutableSet<GroupViewHolder>`: add in `onBindViewHolder`, remove in `onViewRecycled`.
3. Add a file-level `private const val PAYLOAD_SELECTION` used as the `notifyItemChanged` payload token.
4. In the inner `FileAdapter`, add `refreshSelection(old: Set<String>, new: Set<String>)` that walks `currentList` and calls `notifyItemChanged(index, PAYLOAD_SELECTION)` only for files whose membership flipped between `old` and `new`.
5. Override `onBindViewHolder(holder, position, payloads)` in `FileAdapter`: when the payload list is non-empty and all entries are `PAYLOAD_SELECTION`, update only the checkbox via `bindSelection`; otherwise delegate to the full single-arg bind.
6. Split `FileViewHolder.bind` so checkbox wiring lives in a reusable `bindSelection(file)` (null the listener, set `isChecked` from `getSelectedPaths()`, set the click listener); full `bind` calls `bindSelection` plus name/path/root-click wiring.
7. In `GroupViewHolder`, forward `refreshSelection(old, new)` to its `fileAdapter`. Keep `getSelectedPaths = { selectedFilePaths }` as the single source of truth for fresh binds (no duplicated selection state in the inner adapter).

Verification:
- `grep -n "notifyDataSetChanged" DuplicateGroupAdapter.kt` returns zero matches.
- `grep -n "PAYLOAD_SELECTION" DuplicateGroupAdapter.kt` shows one definition plus uses in `refreshSelection` and the payload `onBindViewHolder`.
- Inner `FileAdapter` declares the 3-arg `onBindViewHolder(holder, position, payloads)` override.
- `.\a.ps1 fk` (Kotlin compile) passes - DEFERRED, NO BUILD directive for this run.
- On device: drag-select across a large duplicate group flips checkboxes smoothly with no list-wide flicker - DEFERRED, manual.

---

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0512
- **UI surface:** n/a - no layout, string, or visible-element change; only the redraw path for the existing duplicate-file checkboxes changes (targeted, flicker-free)

---

## Last Audit

### Manual (device: emulator-5556, standard-debug v2.60.6191.257) - 2026-06-19

- **Verdict: PASS**
- **Setup:** seeded `/sdcard/Download/FastMediaSorter_Test/DupBig` with 10 byte-identical PNG copies (same md5), registered as a Local folder resource (read off-disk, MediaStore index not required), ran Find Duplicates -> Start Scan. Scan found 1 group / 10 files / wasted 16.51 KB; group expanded to dup_01..dup_10. Initial auto-selection: dup_01 unchecked, dup_02..dup_10 checked (9 selected).
- **Probe fires per real selection change (expected: yes | actual: yes):** drag-select starting on the unchecked dup_01 emitted, back-to-back, `S0512: Duplicates within-group drag-select start at position=0` then `S0525: targeted selection refresh old=9 new=10 holders=1`. Selection went 9 -> 10 and exactly one probe fired for the one membership flip.
- **Targeted (not full-list) refresh (expected: per-row payload path | actual: confirmed):** the probe reports `holders=1`, i.e. the change is pushed into the bound group holder's nested FileAdapter via payload `notifyItemChanged`, not a list-wide rebind.
- **Every dragged-over checkbox updates (expected: yes | actual: yes):** dup_01 checkbox visibly flipped unchecked -> checked (before: 22_before_drag2.png; after: 23_after_drag3_dup01_flipped.png). Other rows already selected stayed selected.
- **No list-wide flicker (expected: none | actual: none):** before/after screenshots show only the targeted row changing; no global redraw artifact. 0 crashes / FATAL in logcat for the whole session.
- **Negative control - no spurious refresh:** a drag that began on an already-selected row and never reached the only unchecked row produced `S0512` (gesture start) but `S0525` count = 0. The setter's `old == value` early-return correctly suppresses a refresh when the selection set does not change, confirming refresh is driven by real membership flips rather than every pointer tick.
- **S0525 log lines captured (count per drag):**
  - drag 1 (full-range swipe): S0525 x1 - `old=9 new=10 holders=1` (dup_01 flip)
  - drag 2 (swipe over already-selected rows, did not reach unchecked row): S0525 x0 (correct no-op, early-return)
  - drag 3 (drag anchored on unchecked dup_01): S0525 x1 - `old=9 new=10 holders=1`
- **Evidence:** temp/S0525_devtest/ (screenshots 01..23, logcat_drag1.txt, logcat_drag2.txt, logcat_drag3_from_dup01.txt, logcat_full_session.txt).
- **Note:** the within-group drag-select is union-only (adds to the selection, never deselects) and auto-select leaves only the first file unchecked, so a single full-range drag can flip at most one checkbox in this group. The targeted-refresh and probe behaviour is nonetheless fully exercised and verified.

---

## Revision History

- **2026-06-19** - by `/spec-test-device` (device: emulator-5556, Android emulator)
  - Manual device test: PASS. Real duplicate group produced (10 byte-identical files in a registered Local folder resource), Find Duplicates -> Start Scan returned 1 group, group expanded, drag-select exercised. Probe `S0525: targeted selection refresh` fires per real selection change with `holders=1` (targeted payload path, not full-list rebind); dragged-over checkbox (dup_01) visibly flipped; no list-wide flicker; 0 crashes. Negative control confirms early-return suppresses refresh when selection is unchanged. Status kept BlockNeedUserTest (owner sign-off; no catalog status flip in this run).
- **2026-06-19** - by `/spec-test-device` (device: emulator-5554, Android emulator)
  - Scenario: temp/S0525_mobile_test_scenario_20260619_1011.md · PASS/FAIL/INCONCLUSIVE 3/0/3 · Errors in log: 0
  - Build+install of the tagged standard-debug APK PASSed; app launches stably (no crash). The drag-select no-flicker acceptance criterion is not automatable on the emulator (MediaStore not indexed, no large byte-identical duplicate group, continuous drag gesture not driveable, visual judgment) - remains a manual on-device check. Status kept BlockNeedUserTest.
