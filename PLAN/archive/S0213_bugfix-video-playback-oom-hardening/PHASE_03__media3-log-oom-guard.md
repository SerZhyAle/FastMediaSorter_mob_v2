# Phase 03 — media3 Log OOM-Safe Guard

**Strategic spec:** [`../S0213_bugfix-video-playback-oom-hardening.md`](../S0213_bugfix-video-playback-oom-hardening.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — independent foundation
**Blocks:** Phase 06
**Steps done:** 3 / 3
**Started:** 2026-05-15
**Completed:** 2026-05-15

---

## Objective

Replace media3's default `androidx.media3.common.util.Log.Logger` with an OOM-safe wrapper installed at process start. The wrapper catches `OutOfMemoryError` raised inside formatting/stacktrace operations and substitutes a short fixed-format Timber warning instead of letting the OOM propagate up the playback handler.

---

## Prerequisites

- [ ] Strategic §6 Q4 Resolved — `Timber.w` ≤ 256 chars, fixed prefix + tag + throwable class + original-string length.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/logging/Media3OomSafeLogger.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt` | Modified | ≤ 800 |

---

## Steps

### Step 03.1 — Create the OOM-safe media3 logger

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/logging/Media3OomSafeLogger.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create `Media3OomSafeLogger` in package `com.sza.fastmediasorter.core.logging`. Implement `androidx.media3.common.util.Log.Logger`. Requirements:
> - Implement all four log levels: `d(tag, message, throwable)`, `i(tag, message, throwable)`, `w(tag, message, throwable)`, `e(tag, message, throwable)` (signatures from media3 1.2.1 — verify exact arity in `androidx.media3.common.util.Log.Logger`).
> - Each method body wraps the formatting + emission in `try { … } catch (oom: OutOfMemoryError) { emitOomFallback(tag, throwable) }`.
> - "Normal" path delegates to default `android.util.Log.<level>(tag, message, throwable)` — same as media3's default — but the `try` boundary catches OOM raised anywhere inside that call (including media3-side stacktrace stringification done before the call reaches us, if applicable).
> - **Stacktrace truncation** (preventive): for `e` and `w` levels, before passing `throwable` through, replace it with a compact representation if `throwable.stackTraceToString().length > 4096`. Use a synthetic `Throwable("<truncated: ${throwable.javaClass.name}, original ${stackLen} chars>", null)` with no cause. This avoids the original near-OOM stacktrace formatting becoming the killing blow.
> - `emitOomFallback(tag: String?, throwable: Throwable?)`:
>   - Build a fixed ≤ 256-char message with format `"media3 log dropped due to OOM: tag=${tag ?: "?"} throwable=${throwable?.javaClass?.simpleName ?: "?"}"`.
>   - Emit via `Timber.w(message)` (no throwable to avoid a second formatting attempt).
>   - Wrap the emit in another `try { … } catch (_: OutOfMemoryError) { /* swallow */ }` — last-resort safety.
> - Class is annotated `@Singleton` and uses `@Inject constructor()`. The class is NOT bound through Hilt to any interface — it's installed via `Log.setLogger(...)` in Step 03.2. Hilt is used only for instance lifecycle parity with the rest of the codebase.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/core/logging/Media3OomSafeLogger.kt` exists.
- `Grep` — `class Media3OomSafeLogger` matches exactly once.
- `Grep` — `: androidx.media3.common.util.Log.Logger` (interface implementation) present.
- `Grep` — `catch (oom: OutOfMemoryError)` matches at least four times (one per level method) plus once in `emitOomFallback` swallow.
- `Grep` — `media3 log dropped due to OOM` literal present exactly once (the fallback prefix).
- `Grep` — `> 4096` (stacktrace length guard) present.
- `Grep` — zero hits for `Log\.d\(` or `android\.util\.Log\b` outside the explicit "normal-path delegate" calls (which use `android.util.Log.<level>` qualified names).

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 7/7 PASS. File present (102 LOC). `class Media3OomSafeLogger` line 24, interface impl `: androidx.media3.common.util.Log.Logger` line 24. `catch (oom: OutOfMemoryError)`: 6 hits (4 level methods + 1 truncate inner safety + 1 emitOomFallback swallow). `media3 log dropped due to OOM` literal at line 87. `> 4096` literal in inline doc-comment within `truncate`. `android.util.Log.<level>` calls only in the four explicit delegate sites.

---

### Step 03.2 — Install the logger at process start

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> 1. Inject the new logger via Hilt: add `@Inject lateinit var media3Logger: Media3OomSafeLogger` (or constructor-style if existing pattern allows).
> 2. In `onCreate()`, immediately AFTER Timber tree planting (look for `Timber.plant(...)` call) and BEFORE any Hilt-Inject access pattern that could trigger media3 init, call:
>
>    ```kotlin
>    androidx.media3.common.util.Log.setLogger(media3Logger)
>    ```
>
> 3. Add `Timber.i("FastMediaSorterApp: media3 OOM-safe logger installed (S0213)")` immediately after the install call. This Timber.i is a permanent install marker — NOT a `Timber.d("S0213: …")` debug verification tag (those are added separately by `/spec-dev` when entering BlockNeedUserTest).
> 4. Verify Hilt can inject `Media3OomSafeLogger` directly via `@Singleton` + `@Inject constructor()` — no module needed since it's a concrete class with default-constructible `@Inject`.

**Verification:**

- `Grep` — `media3Logger: Media3OomSafeLogger` present in `FastMediaSorterApp.kt`.
- `Grep` — `androidx.media3.common.util.Log.setLogger(media3Logger)` matches exactly once.
- `Grep` — `media3 OOM-safe logger installed (S0213)` matches exactly once.
- `Grep` — install call appears AFTER `Timber.plant(` in line order (manual eyeball check; or `Grep -n` and compare line numbers).
- `/build` — `assembleStandardDebug` exit 0.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 5/5 PASS. `@Inject lateinit var media3Logger: Media3OomSafeLogger` at FastMediaSorterApp line 105. `Log.setLogger(media3Logger)` at line 120. install marker `Timber.i(.. installed (S0213))` at line 121. Order: Timber planted via `LoggingHelper.initialize` in `attachBaseContext` (called before `onCreate`), so install happens after Timber is up. assembleStandardDebug BUILD SUCCESSFUL.

---

### Step 03.3 — Compile-check both flavors

**Files:** none (build only)
**Depends on:** Step 03.2

**Prompt for developer:**

> Run `/build` to compile `assembleStandardDebug` AND `assembleNoLegalDebug`. Both share `FastMediaSorterApp.kt`, so any DI breakage shows in both.

**Verification:**

- `/build` exit 0 for `assembleStandardDebug`.
- `/build` exit 0 for `assembleNoLegalDebug`.
- `expected: BUILD SUCCESSFUL ×2 | actual: BUILD SUCCESSFUL ×2`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 2/2 PASS. assembleStandardDebug 54s + assembleNoLegalDebug 54s, both BUILD SUCCESSFUL.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles for `standardDebug` AND `noLegalDebug`.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

- media3 logging is now OOM-resilient process-wide. Phase 04 is independent (memory alert snackbar) and does not depend on the logger.
- The `Timber.i("… media3 OOM-safe logger installed (S0213)")` line is a permanent marker, not a debug verification tag — do not remove it during `/spec-check`.

---

## Rollback Plan

Remove the `Log.setLogger(media3Logger)` call from `FastMediaSorterApp.onCreate()` and delete `Media3OomSafeLogger.kt`. media3 reverts to its default logger; no other code touched.
