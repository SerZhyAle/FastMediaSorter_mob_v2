# Phase 03 — System Print Fallback

**Strategic spec:** [`../S0145_bugfix-print-rejected-context-not-activity.md`](../S0145_bugfix-print-rejected-context-not-activity.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01 (diagnostics) — no research dependency; may be implemented immediately
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-05-10
**Completed:** 2026-05-10

---

## Objective

When the direct print dispatch throws (any firmware where `PrintManager`/`PrintHelper` reject the call), hand the already-prepared local file to the system via a "Share" chooser instead of showing a dead-end "print unavailable" notice — so the user can still route it to a print target. Independent of which protocol the file came from.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (the catch branches that will trigger the fallback are the ones Phase 01 instrumented).
- [ ] A `FileProvider` authority usable for sharing cache/temp files already exists in the app (confirm in `AndroidManifest.xml`; reuse — do not add a new authority).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerPrintFallbackManager.kt` | New | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DocumentPrintManager.kt` | Modified | ≤ 440 |

> No layout files touched — landscape parity not applicable.
> `DocumentPrintManager.kt` projected < 440 lines — under the 500-line backup threshold.

---

## Steps

### Step 03.1 — Add `PlayerPrintFallbackManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerPrintFallbackManager.kt` (New)
**Depends on:** — start of phase

**Prompt for developer:**

> Create `PlayerPrintFallbackManager` in `ui/player/helpers/`, constructed with the player Activity (same pattern as `DocumentPrintManager`). Give it one public method that takes the prepared local `File`, its display name, and its MIME type, and launches a system chooser: build an `ACTION_SEND` intent with a `FileProvider` content URI for that file (`FLAG_GRANT_READ_URI_PERMISSION`), set the MIME type, wrap in `Intent.createChooser` with a title string, and `startActivity`. Wrap in try/catch — if even the chooser fails (no app to handle it), return `false` so the caller falls back to the existing error notice. MIME type: PDF → `application/pdf`, text → `text/plain`, image → the file's image MIME (`image/*` if unknown). Add a `Timber.w("S0145: print fallback — sharing <name> as <mime>")` line at entry.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerPrintFallbackManager.kt` exists.
- `Grep` — `class PlayerPrintFallbackManager` matches exactly once in that file.
- `Grep` — `Intent.createChooser` and `ACTION_SEND` both present in that file.
- `Grep -n "Log\.d\("` over that file returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-05-10 — Verification 4/4 PASS (file exists; `class PlayerPrintFallbackManager` ×1; `Intent.createChooser` + `ACTION_SEND` present; `Log.d(` ×0). Created `PlayerPrintFallbackManager.kt` (57 LOC) — `shareForPrint(file, displayName, mimeType): Boolean` builds `ACTION_SEND` + FileProvider URI (`${packageName}.fileprovider`) + `FLAG_GRANT_READ_URI_PERMISSION` + `EXTRA_TITLE/SUBJECT`, wraps in `Intent.createChooser` with `R.string.print_share_chooser_title`, `startActivity`; try/catch → `false`; `Timber.w("S0145: print fallback — …")` at entry. Dev log recorded.

---

### Step 03.2 — Add chooser-title and fallback-notice strings (EN/RU/UK)

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** — start of phase (Step 03.1 references the title key)

**Prompt for developer:**

> Add two string keys to all three `strings.xml` files: `print_share_chooser_title` (the title of the system "send to…" chooser shown as a print fallback) and `print_fallback_to_share` (a short, friendly notice shown to the user when the direct print dialog could not be opened and the share chooser is offered instead). Wording must follow `docs/COMMUNICATION_POLICY.md` §2 (formula for the relevant message type — informational + next-step CTA) and pass the §6 tone checklist; `..` not `...`; use `ё`/`Ё` in Russian where correct.

**Verification:**

- `Grep` — `print_share_chooser_title` present in all three of `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`.
- `Grep` — `print_fallback_to_share` present in all three files.
- Run `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "print_"` — exit code 0.
- Strings pass `docs/COMMUNICATION_POLICY.md` §6 checklist.

**Status:** `[x] done`

**Step Log:**

- 2026-05-10 — Verification 4/4 PASS (both keys present in EN/RU/UK; `check_strings_localized.ps1 -KeyPrefix "print_"` exit 0; tone-checklist OK — informational + next-step CTA, no raw exception, no dead-end, friendly neutral). Added `print_share_chooser_title` ("Send to print" / "Отправить на печать" / "Надіслати на друк") and `print_fallback_to_share` (states the print dialog is unavailable and points at the share menu). Author style: `..` not `...`; no `ё` needed in these RU/UK values. Dev log recorded (3 files).

---

### Step 03.3 — Invoke the fallback from `DocumentPrintManager` catch branches

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DocumentPrintManager.kt`
**Depends on:** Step 03.1, Step 03.2

**Prompt for developer:**

> In `DocumentPrintManager`, hold a `PlayerPrintFallbackManager` instance (construct it lazily or in the constructor). In each `catch` branch in `dispatchPrint` and `printImage` that currently shows `error_print_unavailable`: instead, first call the fallback manager with the prepared file / name / MIME; if it returns `true`, show the `print_fallback_to_share` notice (snackbar) instead of the error; if it returns `false`, keep the current `error_print_unavailable` snackbar. Keep the WARN log of the original exception (ADR-1 — diagnostics stay). For the text path, the prepared file passed to the fallback is the same local source file.

**Verification:**

- `Grep` — `PlayerPrintFallbackManager` referenced in `DocumentPrintManager.kt`.
- `Grep` — `print_fallback_to_share` referenced in `DocumentPrintManager.kt`.
- `Grep` — `error_print_unavailable` still referenced in `DocumentPrintManager.kt` (kept as the last resort).
- `Grep -n "Log\.d\("` over `DocumentPrintManager.kt` returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-05-10 — Verification 4/4 PASS (`PlayerPrintFallbackManager` ×1 field; `print_fallback_to_share` ×1; `error_print_unavailable` ×2 — fallback's else branch + `printText` WebView catch; `Log.d(` ×0). Added `private val printFallbackManager` + `private fun showPrintFailedSnackbar(sourceFile, sourceName, mimeType)` (share-then-error). Replaced `error_print_unavailable` snackbar in `dispatchPrint` (`pm == null` + both catches) and `printImage` catch with `showPrintFailedSnackbar(...)`. Threaded `sourceName`/`mimeType` through `printPdf` (`application/pdf`), `printImage` (`image/*`), `printText` (`text/plain`), and `dispatchPrint` (new params `sourceFile`, `sourceName`, `mimeType`); `printCurrentFile` passes `mediaFile.name`. Original-exception WARN/ERROR logs kept. `printText` WebView-unavailable catch left on `error_print_unavailable` (out of prompt scope — distinct failure). Dev log recorded. File 418 LOC (< 500, no backup).

---

### Step 03.4 — Confirm the S0145 flow-entry tag still marks the entry

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DocumentPrintManager.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> Verify the `Timber.d("S0145: …")` tag still sits at the entry of `printCurrentFile` after the catch-branch changes. Exactly one such tag in `DocumentPrintManager.kt`; the fallback manager keeps its own `Timber.w("S0145: print fallback …")` line (a WARN trace, not the entry tag).

**Verification:**

- `Grep` — `Timber.d("S0145:` matches exactly once in `DocumentPrintManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-10 — Verification 1/1 PASS (`Timber.d("S0145: printCurrentFile` ×1, unchanged from Phase 01 — the catch-branch restructuring did not move the entry tag). `PlayerPrintFallbackManager` keeps its own `Timber.w("S0145: print fallback …")` WARN trace (not the entry tag).

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles — `.\build-debug.PS1` exit code 0 (`BUILD SUCCESSFUL in 35s`; only the pre-existing `WelcomeActivity` deprecation warning).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "print_"` — exit code 0.
- [x] Dev log entry added for every file in "Files Touched" (`PlayerPrintFallbackManager.kt`, `DocumentPrintManager.kt`, `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`).
- [x] New class added → `scan.ps1` (998 files) + `render.ps1` for `app_v2`; `set.ps1` filled `role` + `status=new` for `PlayerPrintFallbackManager`.

---

## Handoff Notes to Next Phase

There are now up to three outcomes of a print command: system print dialog opens (direct path, or after Phase 02), the share chooser opens (fallback), or the `error_print_unavailable` notice (nothing handled it). Phase 04 refines the wording of the two notices so the difference is clear and the dead-end one suggests a next step.

---

## Rollback Plan

Revert the phase commit and delete `PlayerPrintFallbackManager.kt`; remove the two new string keys from the three `strings.xml` files. No data migration or persistent state involved.
