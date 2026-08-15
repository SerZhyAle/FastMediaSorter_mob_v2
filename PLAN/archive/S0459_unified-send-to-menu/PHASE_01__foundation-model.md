# Phase 01 - Foundation: type-applicability, content payload, handler contract, icon resolver

**Strategic spec:** [`../S0459_unified-send-to-menu.md`](../S0459_unified-send-to-menu.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, 03, 04
**Steps done:** 4 / 4
**Started:** 2026-06-16
**Completed:** 2026-06-16

---

## Objective

Extend the S0452 `ShareTarget` model with additive type-applicability, and introduce the shared abstractions every receiver and the menu will consume: a content payload value object, a receiver handler contract, and an icon resolver. No registrations, no UI.

---

## Prerequisites

- [ ] Working tree clean or on a feature branch.
- [ ] S0452 infra present (`ShareTarget`, `ShareTargetRegistry`, `ShareTargetAvailabilityResolver`, `IsShareTargetEnabledUseCase`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/share/ShareTarget.kt` | Modified | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/share/ShareableContent.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/share/ShareTargetHandler.kt` | New | ≤ 50 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/share/ShareTargetIconResolver.kt` | New | ≤ 120 |
| `app_v2/src/test/java/com/sza/fastmediasorter/core/share/ShareTargetApplicabilityTest.kt` | New | ≤ 80 |

---

## Steps

### Step 01.1 - Add additive type-applicability to ShareTarget

**Files:** `core/share/ShareTarget.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one field to the `ShareTarget` data class: `val applicableTypes: Set<MediaType> = emptySet()` (import `domain.model.MediaType`). KDoc it: `emptySet()` means "applies to any media type" (the additive-safe default that preserves un-gated behaviour). Add a pure top-level/extension function `fun ShareTarget.appliesTo(type: MediaType): Boolean = applicableTypes.isEmpty() || type in applicableTypes`. Do not touch `ShareTargetAvailabilityResolver` - applicability is content-driven, availability stays device-driven. Per research 02.

**Verification:**

- `Grep` - `val applicableTypes: Set<MediaType> = emptySet()` present in `ShareTarget.kt`.
- `Grep` - `fun ShareTarget.appliesTo(` present exactly once.
- `Grep -n "Log\.d\("` in `ShareTarget.kt` returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-06-16 - Verification 2/2 PASS (`applicableTypes` field line 30, `appliesTo` ext line 37, zero `Log.d`). Files: core/share/ShareTarget.kt. Dev log recorded.

---

### Step 01.2 - Introduce ShareableContent payload

**Files:** `core/share/ShareableContent.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create a `ShareableContent` data class describing what a surface hands to a receiver, decoupled from any Activity: `uris: List<Uri>`, `mime: String`, `mediaType: MediaType`, `text: String? = null` (used by text-only receivers like Keep-text), `displayName: String? = null`. Add a `single()` convenience for first-file receivers (returns content scoped to the first uri). No Android UI imports beyond `android.net.Uri`. This is the uniform input every `ShareTargetHandler` consumes.

**Verification:**

- `Glob` - `core/share/ShareableContent.kt` exists.
- `Grep` - `data class ShareableContent` matches exactly once.
- `Grep` - `val mediaType: MediaType` present.

**Status:** `[x] done`

**Step Log:**

- 2026-06-16 - Verification 3/3 PASS (`data class ShareableContent` line 11, `mediaType` line 14, file exists). Files: core/share/ShareableContent.kt. Dev log recorded.

---

### Step 01.3 - Define ShareTargetHandler contract

**Files:** `core/share/ShareTargetHandler.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Create an interface `ShareTargetHandler` with `val targetId: String` and `fun send(activity: Activity, content: ShareableContent): Boolean` (returns whether an Activity was launched). This is the per-receiver send behaviour, bound later by Hilt `@IntoMap`/`@IntoSet` keyed by `targetId`. No implementation here - contract only. KDoc: implementations wrap existing invokers (`SystemShareInvoker`, print, etc.) or new send code; they must not read global UI state, only the passed `content`.

**Verification:**

- `Glob` - `core/share/ShareTargetHandler.kt` exists.
- `Grep` - `interface ShareTargetHandler` matches exactly once.
- `Grep` - `fun send(activity: Activity, content: ShareableContent): Boolean` present.

**Status:** `[x] done`

**Step Log:**

- 2026-06-16 - Verification 3/3 PASS (`interface ShareTargetHandler` line 14, `send` signature line 19, file exists). Files: core/share/ShareTargetHandler.kt. Dev log recorded.

---

### Step 01.4 - Add ShareTargetIconResolver + applicability unit test

**Files:** `core/share/ShareTargetIconResolver.kt`, `src/test/.../ShareTargetApplicabilityTest.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `@Singleton class ShareTargetIconResolver @Inject constructor(@ApplicationContext context)` exposing `fun resolveIcon(target: ShareTarget): Drawable?`: for a package-backed target (non-empty `packages`, first installed via `PackageManager`) return the installed app icon; otherwise return null so the caller falls back to the target's neutral `?attr`-tinted `iconRes` glyph. Catch `NameNotFoundException` and log at `Timber.i` (expected absence, not an error - per CLAUDE.md Rule 19, no broad catch). Then add `ShareTargetApplicabilityTest` asserting `appliesTo` is true for empty set on every `MediaType`, true only for members of a non-empty set, false otherwise.

**Verification:**

- `Glob` - `core/share/ShareTargetIconResolver.kt` and `src/test/.../ShareTargetApplicabilityTest.kt` exist.
- `Grep` - `class ShareTargetIconResolver` matches exactly once.
- `Grep` - `fun resolveIcon(` present; `Timber.i` present (no bare `catch (e: Exception)`).
- `Grep` - `appliesTo` referenced in the test file.

**Status:** `[x] done`

**Step Log:**

- 2026-06-16 - Verification 4/4 PASS (`ShareTargetIconResolver` line 19, `resolveIcon` line 23, `Timber.i` line 30, no broad catch; test references `appliesTo`). Files: core/share/ShareTargetIconResolver.kt, test/.../ShareTargetApplicabilityTest.kt. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` BUILD SUCCESSFUL (2m05s; only pre-existing deprecation warnings, none in S0459 files). Required killing two concurrent Android Studio Gradle/Kotlin daemons that were corrupting the kapt incremental cache.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] `ShareTargetRegistryTest` still compiles unchanged - additive field carries a default and the test uses named-arg construction without `applicableTypes`, so construction is source-compatible.
- [x] Dev log entry added for every file in "Files Touched".
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (public API changed).

---

## Handoff Notes to Next Phase

`ShareableContent` + `ShareTargetHandler` are the uniform send seam. `applicableTypes`/`appliesTo` are the third menu gate. `ShareTargetIconResolver` returns null for logical targets (caller uses the neutral glyph). No receiver is registered yet - the registry stays empty, settings group stays hidden.

---

## Rollback Plan

Revert phase commit(s) - pure additive model/util layer, no migration, no user-facing surface, registry still empty.
