# Phase 01 - New lexeme list

**Strategic spec:** [`../S1627_strings-thirteen-locales-by-default.md`](../S1627_strings-thirteen-locales-by-default.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 2 / 2
**Started:** 2026-08-14
**Completed:** 2026-08-14

**Execution order note.** The two steps ran in the reverse of their numbering: the producer was written first and generated the baseline itself, from a run with no baseline to subtract. Writing the baseline by any other route would have given it a second definition of "untranslated" - the exact defect the Objective's correction records.

---

## Objective

Produce the baseline that separates today's known-untranslated residue from a genuinely new key, and a single command that emits what is left in the form the external translator already accepts.

**Plan correction, measured 2026-08-14.** This phase originally opened with `-EmitExempt` on the exporter, to freeze the units the exporter skips as the exempt baseline. Measurement refutes it: the exporter's skip set never reaches the export at all, so subtracting it from the export is a no-op. Run over `main,vr,noLegal` the exporter emitted 5 lines and skipped 91 units (90 `no-word`, 1 `escaped-markup`) - two disjoint sets. A skipped literal is therefore already exempt by construction, and the only identities that can be mistaken for new are the 5 the exporter does emit, all of them the placeholder-misread keys owned by `S1626`. The baseline is that set; the exporter needs no new switch, because its sidecar already carries `set`, `file` and `key` per line.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done - none.
- [ ] Strategic §6 research items blocking this phase are Resolved - both are.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/locale-untranslated-baseline.txt` | New | n/a - generated data |
| `scripts/utils/list-new-lexemes.ps1` | New | ≤ 240 |

---

## Steps

### Step 01.1 - Freeze the known-untranslated baseline

**Files:** `scripts/quality/locale-untranslated-baseline.txt`
**Depends on:** Step 01.2 - the producer generates this file

**Prompt for developer:**

> Generate `scripts/quality/locale-untranslated-baseline.txt` from the exporter's sidecar over `main,vr,noLegal`, one `set|file|key` identity per line, sorted. Follow the naming convention of the other ratchet baselines in that directory. Add a header comment naming the ticket, the generation command, the date, and the two tickets that shrink it: `S1626` rewrites the English phrasing of every entry so the next bulk round lands it, and `S1550` marks layout literals `translatable="false"`. Do not hand-edit entries into it.

**Why:**

Strategic §7 names "the gate treats historical residue as new" as the highest-probability risk, whose consequence is a failure on every release that nobody can act on; the frozen list is the mitigation the strategic spec commits to, and it must hold identities rather than a count so that clearing one entry cannot mask a new key arriving in the same release.

**Verification:**

- `Glob` - `scripts/quality/locale-untranslated-baseline.txt` exists.
- `Grep` - `S1627` and `S1626` each match in the file's header comment.
- Every non-comment line matches `^[^|]+\|[^|]+\|.+$`.
- The non-comment line count equals the exporter's reported line count for the same source sets.

**Status:** `[x]` done - 2026-08-14. 19 identities frozen. They are exactly `S1626`'s keys: the per-locale gaps sum to 42, the same 42 slots the `S1420` import guard rejected, which is the arithmetic that confirms the two sets are one.

---

### Step 01.2 - Add the list-new-lexemes command

**Files:** `scripts/utils/list-new-lexemes.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Write `list-new-lexemes.ps1`. It exports the whole corpus once with `locale-bulk-export.ps1 -All` into a scratch directory, reads back the sidecar, and for every best-effort locale collects the keys that locale's resource files already carry. A unit missing from at least one best-effort locale is untranslated; subtract every identity present in the baseline and report what is left - the count, the keys and the locales each one is missing from. Write the survivors as `new_lexemes_en.txt` plus a renumbered `new_lexemes_index.jsonl` in the same shape the sidecar uses, so `locale-bulk-import.ps1` consumes the result without a second format. Report baseline entries that no longer appear anywhere as stale, without failing on them. Parameters `-Module` (default `app_v2`), `-SourceSet` (default `main,vr,noLegal`), `-BaselinePath`, `-OutDir`, `-Quiet`. Exit 0 when nothing is left, exit 3 when the list is non-empty, exit 1 on unusable input. The header must list exactly those codes. `-Module` exists so `S1628` can point the same command at `wear` without a rewrite.

**Why:**

Strategic §5.1 requires the list to be the input of the next step rather than a report, and §3.1 records the owner's wording that the program produces it - a producer emitting prose instead of a translator-ready file would force a second, divergent format for the same set. Checking every best-effort locale rather than one reference locale is what makes the answer match the rule being enforced: the rule says thirteen locales, so a key present in one of the ten and missing from nine must still count as untranslated.

**Verification:**

- `Glob` - `scripts/utils/list-new-lexemes.ps1` exists.
- `Grep` - `-Module`, `-SourceSet`, `-BaselinePath` each match in its param block.
- Run it with no arguments on the current tree - exit code 0, report names zero new lexemes.
- Add a throwaway key with `set-android-string.ps1 -Action add`, re-run - exit code 3, the report names that key and lists ten missing locales, and `new_lexemes_en.txt` holds exactly one line. Remove the key with `-Action remove` afterwards and confirm exit 0 returns.
- `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1` - exit 0.

**Status:** `[x]` done - 2026-08-14. Clean tree: `new untranslated keys 0 | corpus 4429 | baselined 19 | locales checked 10`, exit 0. Seeded `s1627_probe_key`: exit 3, all ten best-effort locales named, `new_lexemes_en.txt` one line; after `-Action remove`, exit 0 again. `assert-exit-contract.ps1` exit 0.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - not applicable, this phase touches no module source; run `.\a.ps1 fg` instead and record its exit code.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `scripts/post-change.ps1 -ChangeType Script`.
- [ ] If public API changed: not applicable - no Kotlin touched.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

"Untranslated" has exactly one definition and one producer: `list-new-lexemes.ps1` exit 3 means "there is new text no locale set carries". Phases 02 and 03 both branch on that exit code rather than re-deriving the set. The exporter is unchanged, so the S1420 round trip keeps working exactly as it did.

---

## Rollback Plan

Delete the new script and the baseline file. Nothing else was touched, and nothing here is read at build time.
