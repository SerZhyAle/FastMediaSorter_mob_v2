# Phase 03 - Delete the dead keys from every locale

**Strategic spec:** [`../S1568_unreferenced-string-keys-audit.md`](../S1568_unreferenced-string-keys-audit.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-08-12
**Completed:** 2026-08-12

---

## Objective

Delete the measured dead names from `values/strings.xml` and from every locale file that carries them, in one batch, holding back only names a human decided to keep.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] `temp/S1568/` exists for this ticket's artifacts, per CLAUDE.md Rule 1.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | net -397 lines expected |
| `app_v2/src/main/res/values-ru/strings*.xml` | Modified | net -394 lines expected |
| `app_v2/src/main/res/values-uk/strings*.xml` | Modified | net -394 lines expected |
| `app_v2/src/main/res/values-{ar,b+zh+Hans,bn,de,es,fr,hi,it,pt,ur}/strings*.xml` | Modified | net -122 lines each expected |

> No Kotlin, no layout and no flavor source file is touched: every name in the removal list has zero references anywhere under `app_v2/src`, which is the precondition Phase 02 enforces mechanically.
>
> `values/strings.xml` is far above 500 LOC, so Step 03.2 carries a backup sub-step per CLAUDE.md Rule 5. The batch tool is byte-preserving and never reserializes through `[xml]`, so the diff must contain deletions only.

---

## Steps

### Step 03.1 - Freeze the removal list and the hold-back list

**Files:** `temp/S1568/removal-candidates.txt`, `temp/S1568/hold-back.txt`
**Depends on:** - start of phase

**Prompt for developer:**

> Run the Phase 01 report over `app_v2` / `strings.xml` and write its names to `temp/S1568/removal-candidates.txt`, one per line.
> Read the list and move to `temp/S1568/hold-back.txt` every name that looks like scaffolding for a feature that is built but not yet wired - a name whose family already has live siblings, or whose text names a screen that exists.
> Each hold-back line carries a trailing `# <reason>` comment stating why the name is kept, because the file becomes the Phase 04 gate baseline verbatim.
> Do not silently drop a disputed name from both lists: a name is either removed or held back with a written reason.
> Cross-check the two lists against each other so no name appears in both, and confirm their combined length equals the report's unreferenced count.

**Why:**

Strategic §7 records that part of the dead set may be groundwork for an unreleased feature, that no mechanical signal separates it, and that disputed names must be surfaced for a decision rather than deleted silently; strategic §3.1 additionally requires every removed name to carry a visible basis for being judged dead.

**Verification:**

- `Glob` - `temp/S1568/removal-candidates.txt` and `temp/S1568/hold-back.txt` both exist.
- Line count of the two files sums to the `unreferenced=` figure printed by the Phase 01 report in the same session.
- `Grep` - every line of `temp/S1568/hold-back.txt` matches `#`, proving each hold-back carries a reason.
- No name appears in both files - a sorted intersection of the two is empty.

**Status:** `[x]` done

---

### Step 03.2 - Execute the batch removal across every locale

**Files:** `app_v2/src/main/res/values*/strings*.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

> Back up `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml` and `values-uk/strings.xml` to `temp/S1568/` with a timestamp suffix, per CLAUDE.md Rule 5.
> Take `CODE.LOCK` with `pwsh -NoProfile -File scripts/utils/enter-code-lock.ps1 -Reason "//spec-dev S1568 phase 03: string removal"` - note the doubled leading slash required by CLAUDE.md Rule 27 when the call is issued from the Bash tool.
> Run `remove -KeyList temp/S1568/removal-candidates.txt -DryRun` first and read the summary; proceed only if refused is 0.
> Run the same command without `-DryRun`, then release the lock immediately with `pwsh -NoProfile -File scripts/utils/exit-code-lock.ps1` - do not hold it across the builds in the Phase Done Criteria.
> Never hand-edit a strings file to finish the job: a name deleted from `values/` by hand stays behind in up to twelve translated locales, and no gate in the repository notices that orphan today.

**Why:**

Strategic §3.2 requires a removal to clear the name from every locale in one action, and strategic §6.2 requires the work to run now, in parallel with S1420's locale seeding, with the file conflict resolved by taking the code lock for a short step rather than by postponing the ticket.

**Verification:**

- The un-dry run exits 0, and its summary reports removed equal to the line count of `temp/S1568/removal-candidates.txt`, refused 0, absent 0.
- Re-run the Phase 01 report - `unreferenced=` now equals the line count of `temp/S1568/hold-back.txt`.
- `Grep` - a sample of five removed names returns zero hits across `app_v2/src/main/res/values*/strings*.xml`, proving no locale orphan survives.
- `Grep` - `sync_interval_hours` returns zero hits across the same set if it was not held back, proving the `<plurals>` path executed.
- Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1` - expected exit code 0.
- Diff each strict-locale file against the backup taken in this step: zero lines added, and removed lines equal to the key count plus the extra lines of any multi-line element. The originally planned predicate compared against `git diff` and is not usable, because S1420 is editing the same files in parallel, so a diff against HEAD carries its insertions and proves nothing about this step. Comparing against this step's own backup isolates exactly this change.
- `pwsh -NoProfile -File scripts/utils/lock-status.ps1 -Name Code` reports the lock free after the step.

**Status:** `[x]` done

---

## Step Log

- 2026-08-12 - Step 03.1 DONE. Report written to `temp/S1568/removal-candidates-raw.txt` (397 names). Classified by family rather than by eye: for every declared name, count live and dead siblings sharing its first two underscore segments. 208 of the 397 sit in families that still ship, i.e. leftovers of a live surface - the lowest-risk class. 23 families are fully dead with 2+ members, and only those needed judgement.
- 2026-08-12 - The judgement, family by family. The bulk are Android layout **attribute literals** accidentally extracted as string resources - `dialog_filter_*_boxBackgroundMode`, `bottom_sheet_*_textAlignment`, `page_welcome_*_lineSpacingMultiplier`, `layout_gravity_*`, `button_text_alignment_*` - which name no user-visible text at all; these are the S1550 overlap and carry zero risk. `support_images` / `support_epub` and siblings looked alive at first glance because those exact tokens appear in `SettingsRepositoryImpl` as `booleanPreferencesKey("support_images")`, but a DataStore preference key is a string literal, not a resource reference; the live sibling is `support_epub_description`, referenced from `fragment_settings_documents.xml`, so the bare names are superseded labels. Deleted.
- 2026-08-12 - **Seven names held back**, all `passthrough_capture_*`. Not a judgement call on the text but on the code: `BrowsePassthroughCaptureProvider` is a 10-line contract in `src/main`, bound through `@BindsOptionalOf` in `BrowsePassthroughOptionalModule`, injected as `Optional<..>` in `BrowseActivity`, and implemented in **no source set at all** - so the Optional is always empty and nothing is able to reference these seven user-facing messages yet. That is scaffolding awaiting wiring, which strategic §7 requires to be surfaced for a decision rather than deleted silently. Cross-checks: 390 + 7 = 397, and the two lists do not intersect.
- 2026-08-12 - Step 03.2 DONE. Backed up the three strict-locale files to `temp/S1568/*-strings.xml.20260812-001358.bak`. Took `CODE.LOCK`, ran the batch dry (`would remove=390 refused=0 absent=0`), then for real (`removed=390 refused=0 absent=0`, exit 0), and released the lock in the same step - S1420 waited seconds, not the ticket.
- 2026-08-12 - Evidence after the removal. Declared count 3234 -> 2844, exactly -390. The audit now reports `unreferenced=7`, which is the hold-back list and nothing else. Five sampled removed names, including the `<plurals>`, appear in **zero** of the 13 locale strings files, so no orphan survives anywhere. Both sampled hold-backs are still declared. `check_strings_localized.ps1` exits 0 with all 4463 keys present in en/ru/uk. Every `values*/strings*.xml` still parses as XML. Against this step's own backups: **0 lines added, 393 removed** per strict locale - 389 single-line `<string>` elements plus the 4-line `<plurals>` block, which is exactly 390 keys and proves the byte-preserving path reflowed nothing.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles, for the flavors the tooling can build. `a.ps1 fc` (standard: `mergeStandardDebugResources` + `processStandardDebugResources` + `compileStandardDebugKotlin`) exit 0 in 19 s; `a.ps1 fkn` (noLegal, which adds the `screenCapture` and `vr` source sets) exit 0 in 1 m 21 s; `a.ps1 dq` packaged a standard debug APK, exit 0. Resource *merging* is the step a dangling `@string/` dies in, and it ran clean.
- [~] **Deferred, with the reason recorded:** `lite`, `photos`, `legacy` and `vr` were not built, because `a.ps1` has no debug target for them - `vr`/`nl` are release builders and there is no per-flavor debug entry point, while `/spec-dev` forbids invoking `gradlew` directly. The tooling gap is parked as **S1589**. The residual risk is argued, not assumed: the Phase 01 scan reads every file under `app_v2/src`, including the `lite`, `photos`, `legacy`, `vr` and every `*Disabled` source set, so no deleted name is referenced by anything those four flavors compile. A deleted string can only break a build where something references it.
- [x] `.\a.ps1 fu` passes - BUILD SUCCESSFUL in 4 m 12 s, exit 0, `assert-test-suite-complete` reporting 488 reports for 488 `*Test.kt` files (ratio 1). Strategic §3.3's second half satisfied.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added via `scripts/post-change.ps1` - `post-change: PASS (Xml)`.
- [x] `dev/CATALOG/<module>.jsonl` regeneration not required - no Kotlin changed.
- [x] Phase-boundary audit run - no P0/P1 findings. See Audit note below.

## Phase-boundary audit (2026-08-12)

Layer 1 plus a resource-integrity pass. No Kotlin, no lifecycle, no Room surface.

- The irreversible step is done and every claim about it rests on a command that ran: 390 removed, declared 3234 -> 2844, unreferenced 397 -> 7, parity gate exit 0, all 13 locale files still parse, and a backup-relative diff showing 0 insertions.
- Blast radius checked from the other direction too: `a.ps1 fc` merged and processed standard resources, which is the step a dangling `@string/` reference dies in, and it passed. `fkn` covered the noLegal graph including the `screenCapture` and `vr` source sets. The full debug APK packaged.
- Held-back names verified present after the run, so the hold-back list is not merely documented but effective.
- The four unbuilt flavors are recorded above as a deferred item with a source-level argument and a parked ticket (S1589), not silently dropped.
- One unrelated gate, `assert-memory-budget`, was found red while running `a.ps1 fg` and was fixed inline as a trivial out-of-scope item per CLAUDE.md §3.1: the agent-memory index was 365 B over its ceiling. Fixed by shortening hook text, explicitly NOT by dropping pointers - three pointers removed in a first attempt were restored, because an index line is what makes a memory findable. `a.ps1 fg` now reports all gates green.
- No P0/P1.

---

## Handoff Notes to Next Phase

`temp/S1568/hold-back.txt` is the input to the Phase 04 gate baseline and must survive until that phase copies it into `scripts/quality/`. `temp/` is gitignored, so the hold-back reasons are not durable until Phase 04 lands - do not treat Phase 03 as closeable on its own.

The post-cleanup unreferenced count, printed by the Phase 01 report at the end of Step 03.2, is the number Phase 04 ratchets against.

---

## Rollback Plan

Restore the three timestamped strings backups from `temp/S1568/` and `git checkout` the ten translated locale files. The change is deletion-only inside resource files, with no schema, no migration and no persisted user data behind it, so a revert is complete on its own.
