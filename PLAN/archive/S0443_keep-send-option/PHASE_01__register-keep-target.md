# Phase 01 - Register the Keep share-target

**Strategic spec:** [`../S0443_keep-send-option.md`](../S0443_keep-send-option.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none - foundation phase (S0452 registry seam already exists)
**Blocks:** Phase 02, Phase 03
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

---

## Objective

Register a single `ShareTarget` for Google Keep via Hilt multibinding so the foundation surfaces it: the settings group renders the Keep toggle automatically, and the effective-state seam `IsShareTargetEnabledUseCase("keep", ...)` becomes meaningful. No gating of command surfaces yet (Phases 02-03), no settings-UI or storage work (foundation owns it).

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] S0452 foundation present: `core/share/ShareTarget.kt`, `ShareTargetRegistry.kt`, `core/share/di/ShareTargetModule.kt`, `domain/usecase/IsShareTargetEnabledUseCase.kt`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/share/di/KeepShareTargetModule.kt` | New | ≤ 80 |
| `app_v2/src/test/java/com/sza/fastmediasorter/core/share/KeepShareTargetTest.kt` | New | ≤ 120 |

---

## Steps

### Step 01.1 - Contribute the Keep `ShareTarget` via `@IntoSet`

**Files:** `core/share/di/KeepShareTargetModule.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create a new Hilt module `KeepShareTargetModule` (`@Module @InstallIn(SingletonComponent::class)`) that contributes one `ShareTarget` via `@Provides @IntoSet`. Build the target with: `id = KEEP_TARGET_ID` (a `const val KEEP_TARGET_ID = "keep"` declared in this module's `companion object` / file - this is the single source of the id literal reused by every gate), `titleRes = R.string.text_editor_action_send_keep` (the existing "Send to Keep" label), `iconRes = R.drawable.ic_text_send_keep`, `availability = ShareTargetAvailability.PACKAGE_INSTALLED`, `packages = listOf("com.google.android.keep", "com.google.android.keep.notes")` (the known Keep package ids), `defaultEnabled = ShareTargetDefault.ON_IF_GOOGLE`. Do not add a settings row, storage field, or `<queries>` entry - all already provided by the S0452 foundation and the manifest. Keep the module free of behaviour; it only declares.

**Verification:**

- `Glob` - `core/share/di/KeepShareTargetModule.kt` exists.
- `Grep` - `@IntoSet` present in the file.
- `Grep` - `KEEP_TARGET_ID = "keep"` present (const id literal).
- `Grep` - both `com.google.android.keep` and `com.google.android.keep.notes` present.
- `Grep` - `ON_IF_GOOGLE` and `PACKAGE_INSTALLED` present.
- `Grep -n "Log\.d\("` - zero hits in the file.

**Status:** `[ ]` not done

---

### Step 01.2 - Unit-test the Keep target declaration

**Files:** `core/share/KeepShareTargetTest.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `KeepShareTargetTest` that constructs the Keep `ShareTarget` the same way the module does (or invokes the module's `@Provides` function directly) and asserts: `id == "keep"`; `availability == PACKAGE_INSTALLED`; `packages` contains both Keep package ids; `defaultEnabled == ON_IF_GOOGLE`; `titleRes == R.string.text_editor_action_send_keep`. Pure JVM test, no Android framework / no Hilt graph. This pins the declaration so a future edit cannot silently change the id (which every gate keys on) or the default rule.

**Verification:**

- `Glob` - `core/share/KeepShareTargetTest.kt` exists.
- `Grep` - `@Test` matches >= 1.
- `Grep` - `"keep"` asserted in the test.
- Build: `.\a.ps1 fk` compiles the module + test source (full `--tests` run may be blocked by the pre-existing broken unit-test set tracked under S0455; if so, record the per-class report path or the compile-only PASS).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - Hilt graph resolves the non-empty `Set<ShareTarget>` (`.\a.ps1 fc` or `assembleStandardDebug`).
- [ ] The Keep toggle appears in the settings group at runtime (registry no longer empty - `PlaybackSettingsFragment` renders it). Visual confirmation deferred to device test (Phase 04 / `/spec-test-device`).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

- `KEEP_TARGET_ID` (`"keep"`) is the registry key every gate in Phases 02-03 references - never inline the literal again.
- `IsShareTargetEnabledUseCase("keep", settings)` is now meaningful: returns the user choice if set, else `ON_IF_GOOGLE` resolved by the resolver.
- The settings toggle is already wired by the foundation; Phases 02-03 only add the consumer-side gates.

---

## Rollback Plan

Revert phase commit(s) - new files only. Registry returns to empty, settings toggle disappears, no data migration.
