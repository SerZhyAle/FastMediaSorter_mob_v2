# Phase 01 - StreamsActivity gate clearance

**Strategic spec:** [`../S1328_streaminlineaudiomanager-detekt-debt.md`](../S1328_streaminlineaudiomanager-detekt-debt.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 1 / 2 done, 1 skipped
**Started:** 2026-08-03
**Completed:** 2026-08-03

---

## Objective

**Rewritten 2026-08-02.** Nothing on `StreamsActivity.kt` is live any more - the 2026-08-02 baseline
rewrite absorbed all of it, and a gated detekt run scoped to this file exits 0. The phase is no
longer clearing a red gate; it is stopping Phase 02 from turning a green one red.

Phase 02 adds two imports to `StreamsActivity.kt`. The baselined `ImportOrdering` entry
(`baseline-app_v2.xml:2997`) keys on the **entire import block, verbatim** - so the moment an import
lands, that entry stops matching and the finding resurfaces. The block genuinely is mis-ordered
(`SyntheticResourceIds` before `StreamTrackLanguage`), so it would resurface as a real failure, in
Phase 02's gate, attributed to this ticket. Step 01.1 fixes the order first.

Step 01.2 is now **optional**. The two `Wrapping` entries (`baseline-app_v2.xml:12244-12245`) key on
tokens, not on the import list, so Phase 02 does not re-key them. Keep the step as opportunistic
cleanup or skip it - either way it does not gate Phase 02. No behaviour change in this phase.

---

## Prerequisites

- [x] Pre-Implementation Blocker on `TooManyFunctions 42/40` (S1198) is resolved in INDEX.md -
      cleared 2026-08-02, the finding is baselined and the entanglement is void.
- [ ] Strategic §6 research items blocking this phase are Resolved - none exist.
- [ ] Working tree is clean or on a feature branch.
- [ ] `StreamsActivity.kt` is 1205 lines (>500), so copy it to `temp/S1328/` with a timestamped name
      before the first edit (CLAUDE.md Rule 5).
- [ ] `temp/CODE.LOCK` acquired via `scripts/utils/enter-code-lock.ps1 -Reason "S1328 phase 01"`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt` | Modified | ≤ 1205 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern).

---

## Steps

### Step 01.1 - Reorder the drifted import

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In the import block, move `import com.sza.fastmediasorter.domain.model.StreamTrackLanguage` so it
> precedes `import com.sza.fastmediasorter.domain.model.SyntheticResourceIds` (currently lines 37 and
> 38, in that wrong order). Change nothing else in the block - the total import count must stay the
> same. Do not touch `config/detekt/baseline-app_v2.xml`; its stale `ImportOrdering` entry for this
> file is keyed to an older import list and is left alone deliberately.
>
> **The ordering rule, so you can re-derive it rather than trust this step.** detekt's `formatting`
> ruleset runs with `android: true`, which is ktlint layout `*,java.**,javax.**,kotlin.**,^`: one
> group for everything else, then `java.*`, then `javax.*`, then `kotlin.*`, then aliases, with no
> blank lines between groups. `kotlinx.*` and `timber.*` are **not** `kotlin.*` - they stay in the
> first group, which is why `kotlinx.coroutines.launch` and `timber.log.Timber` correctly sit above
> `javax.inject.Inject` today. Within a group the comparison is case-**sensitive** ordinal, so an
> uppercase segment sorts above a lowercase one: `com.sza.fastmediasorter.R` genuinely belongs above
> `com.sza.fastmediasorter.core.*`, and `ui.player.PlayerActivity` above `ui.player.helpers.*`. Both
> already look wrong to an IDE "optimize imports" and to a case-insensitive sort - leave them alone.
> Only the `StreamTrackLanguage` / `SyntheticResourceIds` pair violates the real rule (`t` < `y` at
> the second character).
>
> Do not try to confirm the ordering with a PowerShell `Sort-Object -CaseSensitive`, and do not pipe
> `Group-Object` output into `[Array]::Sort(.., [StringComparer]::Ordinal)` - the first is
> culture-aware and the second silently falls back to a case-insensitive compare because the elements
> arrive `PSObject`-wrapped. Both produce a long list of false violations on this exact file. The
> only trustworthy check is a fresh detekt run.

**Note:** `ImportOrdering` is reported once for the whole import block, not once per misplaced line,
so the finding disappearing is the only proof the block is correct.

**Verification:**

- `Grep` - in `StreamsActivity.kt`, the line number of `import com.sza.fastmediasorter.domain.model.StreamTrackLanguage` is strictly less than the line number of `import com.sza.fastmediasorter.domain.model.SyntheticResourceIds`.
- `Grep` - `^import ` matches exactly 61 times in `StreamsActivity.kt` (unchanged count).
- `Grep` - `@Suppress` returns zero hits in `StreamsActivity.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-03 - Verification 3/3 PASS. `StreamTrackLanguage` now line 37, `SyntheticResourceIds` line 38; `^import ` count unchanged at 61; zero `@Suppress`. Premise re-verified against the live tree before the edit, per the debt-ticket rule: baseline still 12286 lines, `LongParameterList:StreamInlineAudioManager` still at line 3484, gate exit 0.

---

### Step 01.2 - Unwrap the two track-language index expressions - OPTIONAL

> Downgraded 2026-08-02. Both `Wrapping` findings are baselined (`baseline-app_v2.xml:12244-12245`)
> and their signatures do not depend on the import block, so Phase 02 does not re-key them. This step
> gates nothing. Run it as cleanup or skip it; if skipped, mark it `[-] skipped` rather than `[x]`.

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Around lines 913-916, two `StreamTrackLanguage.entries[..]` lookups split their index expression
> across lines, which is the source of all four live `Wrapping` findings. Hoist each index into its
> own local so the subscript sits on one line: introduce `audioIndex` from
> `binding.rowChannelAudioLanguage.getSelectedIndex().coerceAtLeast(0)` and `subtitleIndex` from
> `binding.rowChannelSubtitleLanguage.getSelectedIndex().coerceAtLeast(0)`, then index
> `StreamTrackLanguage.entries` with them. Keep the net line count of the block at 4 so no later
> finding shifts position, and keep every resulting line under 120 characters. Add no comment - the
> code states itself.

**Verification:**

- `Grep` - `val audioIndex` and `val subtitleIndex` each match exactly once in `StreamsActivity.kt`.
- `Grep` - `StreamTrackLanguage.entries\[audioIndex\]` and `StreamTrackLanguage.entries\[subtitleIndex\]` each match exactly once.
- `Grep` - `.coerceAtLeast(0)].isoCodeOrNull()` returns zero hits (the wrapped form is gone).
- `Grep` - `Timber.d("S1144:` still matches exactly once in `StreamsActivity.kt` (S1144 is `BlockNeedUserTest`; its probe must survive).
- Value equality - `StreamsActivity.kt` line count is 1205 (unchanged).

**Status:** `[-]` skipped

**Step Log:**

- 2026-08-03 - Skipped, on the step's own terms. It gates nothing: both `Wrapping` entries are baselined and key on tokens rather than the import block, so Phase 02 cannot re-key them. Unwrapping them now would turn those two baseline entries dead, and this ticket's constraint forbids touching the baseline for anything but the `LongParameterList` entry - so the cleanup would leave behind exactly the dead-entry debt `scripts/quality/audit-detekt-baseline-drift.ps1` exists to catch. Worth its own ticket, not a free rider on this one.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is resolved - 01.1 `[x] done`, 01.2 `[-] skipped` on its own optional terms.
- [x] Project compiles - `pwsh -NoProfile -File ./a.ps1 fk` exit 0, `Fast check passed`.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] `assert-detekt.ps1 -Module app_v2 -Gate -ChangedFiles StreamsActivity.kt` exit 0, `PASS (no new findings; baselines hold)`. The report file on disk was not read.
- [x] `config/detekt/baseline-app_v2.xml` unmodified by this phase - **12286** lines, unchanged.
- [x] Dev log entry added via `post-change.ps1` (`post-change: PASS (Kotlin)`).
- [x] Phase-boundary audit run - see below, no P0/P1.

## Phase-boundary audit - 2026-08-03

One import line moved, nothing else. No declaration, no call, no lifecycle, no coroutine, no listener,
no Room surface is touched, so Layers 2-4 have no trigger. Layer 1: the block now satisfies the
ktlint `android: true` ordinal rule the step re-derived, the import count is unchanged at 61, and the
compile proves no symbol was lost. Zero findings at any severity.

---

## Handoff Notes to Next Phase

`StreamsActivity.kt` carries no live finding at all, before or after this phase - everything on it is
baselined, `TooManyFunctions 42/40` included. What this phase bought Phase 02 is narrower: the import
block is now correctly ordered, so when Phase 02 adds its two imports and re-keys the `ImportOrdering`
baseline entry, the re-keyed check passes instead of surfacing a real violation.

---

## Rollback Plan

Revert the phase commit - no data migration, no user-facing surface, no behaviour change.
