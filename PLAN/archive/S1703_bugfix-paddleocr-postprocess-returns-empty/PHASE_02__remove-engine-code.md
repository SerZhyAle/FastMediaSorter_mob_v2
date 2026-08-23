# Phase 02 - Remove the engine

**Strategic spec:** [`../S1703_bugfix-paddleocr-postprocess-returns-empty.md`](../S1703_bugfix-paddleocr-postprocess-returns-empty.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-08-17
**Completed:** 2026-08-17

---

## Objective

The engine, its binding and its selection branch leave the tree, callers first so it compiles at every step.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done - a stored `PADDLE` already normalises.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/ocr/OfflineOcrEngineProvider.kt` | Modified | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/ocr/PaddleOcrEngine.kt` | Deleted | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/ocr/PaddleOcrEngineContributor.kt` | Deleted | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/PaddleOcrModule.kt` | Deleted | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PaddleOcrModelManager.kt` | Deleted | - |

> The exact file set is what the measurement finds at implementation time; the ones above are what the
> catalog listed on 2026-08-17. Delete what is genuinely unreferenced after the selection branch is gone -
> Rule 20 - and no more.

---

## Steps

### Step 02.1 - Drop the selection branch

**Files:** `.../domain/ocr/OfflineOcrEngineProvider.kt`

**Depends on:** - start of phase

**Prompt for developer:**

> The provider chooses an engine from the setting and falls back. Remove the branch that can return the
> Paddle engine, so the provider offers the default one and its fallback only. Keep the fallback machinery
> itself - the ticket withdraws one engine, not the ability to have a second one later.

**Why:**

Strategic §3 withdraws the engine while §3 also states a second engine may return under its own ticket, so
the seam stays and only its one implementation goes.

**Verification:**

- `Grep` - no Paddle reference remains in the provider.
- `Grep` - the fallback path still exists.
- `.\a.ps1 fk` and `.\a.ps1 fkn` - exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-17 - Step 02.1 refuted by reading: OfflineOcrEngineProvider has no Paddle branch at all - it selects through a contributor set, so withdrawing the contributor is the whole change and the provider file is untouched. Step 02.2: the engine lived entirely in the noLegal source set; PaddleOcrModule, PaddleOcrEngine, PaddleOcrEngineContributor and PaddleOcrModelManager deleted with their two tests, grep across app_v2/src returns zero references, a.ps1 fkn exit 0 (full flavor recompile, Hilt graph intact).

---

### Step 02.2 - Delete the engine, its contributor, its module and its model manager

**Files:** the four files listed above

**Depends on:** Step 02.1

**Prompt for developer:**

> Delete them, then grep the tree for what still names them and remove those references too - a Hilt module
> deleted while something still injects its binding fails at run time, not at compile time, so check the
> graph rather than trusting the compiler. `.\a.ps1 fk` proves compilation; the DI graph needs its own look.

**Why:**

Rule 20 requires the orphaned classes to go with the capability, and the Hilt graph is the one place where a
dangling reference survives compilation - which is why it is called out here rather than left to the build.

**Verification:**

- `Glob` - none of the four files exists.
- `Grep` - `PaddleOcrEngine`, `PaddleOcrEngineContributor`, `PaddleOcrModule`, `PaddleOcrModelManager` return
  zero hits across `app_v2/src`.
- `.\a.ps1 fk`, `.\a.ps1 fkn` - exit 0.
- `.\a.ps1 fu` - passes; a broken Hilt binding usually shows here first.

**Status:** `[x]` done

**Step Log:**

- 2026-08-17 - Step 02.1 refuted by reading: OfflineOcrEngineProvider has no Paddle branch at all - it selects through a contributor set, so withdrawing the contributor is the whole change and the provider file is untouched. Step 02.2: the engine lived entirely in the noLegal source set; PaddleOcrModule, PaddleOcrEngine, PaddleOcrEngineContributor and PaddleOcrModelManager deleted with their two tests, grep across app_v2/src returns zero references, a.ps1 fkn exit 0 (full flavor recompile, Hilt graph intact).

---

### Step 02.3 - Retire the capability flags that only guarded it

**Files:** `.../core/capability/CapabilityAvailability.kt` and whatever else the grep finds

**Depends on:** Step 02.2

**Prompt for developer:**

> Some capability flags and settings-search gates exist only to hide or show the Paddle choice. Remove those
> that have no other reader, and leave the ones that also guard OCR as a whole - the distinction is what the
> flag is named after, not where it sits.

**Why:**

Rule 20 again, and a flag that guards nothing is worse than no flag: the next reader takes it for a real
capability axis.

**Verification:**

- `Grep` - every remaining Paddle mention in `src/main` is intentional and explained.
- `.\a.ps1 fk`, `.\a.ps1 fkn` - exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-17 - Retired CAP_OCR_ENGINE_SELECTION and isOcrEngineSelectionAvailable(). The flag's one surviving reader was itself Paddle-derived: the 'noLegal OCR' badge in the language picker, whose own KDoc stated the labels exist because noLegal bundles PaddleOCR and whose code set (ru, uk, bg, be, mk) is the Cyrillic model coverage. With the engine withdrawn the badge advertises a capability that no longer exists, so the whole chain went: LanguageCapability.NO_LEGAL_OCR, noLegalOcrCodes, the noLegalOcrLabelsEnabled plumbing through LanguageAdapter/LanguageViewHolder, the language_capability_nolegal_ocr string across 13 locales, and NoLegalCapabilityModule.provideOcrEngineSelection. The now-unused CapabilityAvailability injection was dropped from SearchableLanguagePickerDialog (Rule 20). Verification: grep 0 hits across app_v2/src and wear/src for every retired symbol and string, a.ps1 fk exit 0, a.ps1 fkn exit 0 (noLegal recompile proves the trimmed Hilt multibinding still assembles).

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] `.\a.ps1 fk` and `.\a.ps1 fkn` exit 0.
- [x] `.\a.ps1 fu` passes.
- [x] Dev log entry added for every file in Files Touched.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

No code selects or implements the engine. Its payload is still described and still downloadable.

---

## Rollback Plan

Restore the deleted files and the provider branch; nothing else changed shape.
