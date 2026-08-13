# S0485 - DeliveredNativeLibraryLoader logs expected condition at Timber.e

**Status:** Archived
**Priority:** 45
**Date:** 2026-06-17

## 0. Raw capture

**Symptom:** `DeliveredNativeLibraryLoader` logs `UnsatisfiedLinkError loading <lib>` at `Timber.e` even when the native set is legitimately absent (emulator, non-arm64, no Play delivery). This is a designed graceful-degradation path (OCR/translation stays disabled), not an actionable error.

**Why it matters:** Violates the project rule "reserve `Timber.e` for real errors only" (CLAUDE.md; expected device-capability fallbacks log at `Timber.i`). Pollutes automated error-count analysis (e.g. S0484 verdict aggregator must add a specific suppression rule).

**Evidence:** `app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/DeliveredNativeLibraryLoader.kt` warm-load loop (`System.load` catch).

**Discovered during:** S0484 §6.5 research (log verdict markers).

## 1. Root cause

- The warm-load `System.load` loop runs only *after* the payload already passed SHA-256 + size integrity verification (ADR-3). So a `.so` reaching this point is byte-correct.
- A `UnsatisfiedLinkError` on a byte-correct `.so` is therefore not corruption - it is an ABI/linkage incompatibility (arm64 lib on an x86 emulator or non-arm64 device, or an unsatisfiable transitive dependency). This is an expected device-capability fallback.
- Two distinct defects, both stemming from mishandling this expected condition:
  1. It is logged at `Timber.e` - wrong level for an expected, non-actionable fallback.
  2. It is rethrown as the original `UnsatisfiedLinkError`, which extends `Error`, not `Exception`. Every caller's graceful-degradation `catch (e: Exception)` fails to catch it, so it escapes uncaught (latent crash) instead of degrading OCR/translation/DTS to "unavailable".

## 2. Decision

- The warm-load `UnsatisfiedLinkError` path signals "delivered payload is byte-correct but not loadable on this device".
- Introduce `DeliveredNativeLibraryIncompatibleException : IOException` to carry this meaning - a sibling of `DeliveredPayloadCorruptException`, but explicitly *not* corruption: consumers must degrade to "feature unavailable" and must *not* offer a reinstall (re-downloading the same arch cannot help, so the set install marker is left intact).
- Loader logs the condition once at `Timber.w` (notable - a set was delivered that will not run here - but not an actionable error) without the stacktrace, then throws the typed exception.
- Each graceful-degradation consumer adds a typed `catch` ahead of its generic `catch (e: Exception)` so the expected case degrades at `Timber.i` while genuine unexpected failures keep `Timber.e`.

## 3. Plan

- Add `DeliveredNativeLibraryIncompatibleException.kt` (data/delivery).
- `DeliveredNativeLibraryLoader.load`: warm-load catch logs at `Timber.w` and throws the typed exception (no set invalidation).
- `RecognitionBackend` (3 load sites): add typed catch logging at `Timber.i`, degrade to `null`.
- `TranslationBackend.ensureNativeLibrariesLoaded`: add typed catch logging at `Timber.i`, return `false`.
- `PlaybackRenderersFactory.attachDeliveredFfmpegDtsIfInstalled`: no change needed - its existing `catch (e: Exception)` now catches the typed exception and already degrades at `Timber.w`.

## 4. Verification

- `.\a.ps1 fk` compile.
- Code inspection: no `Timber.e` remains on the byte-correct-but-unloadable path; the typed exception is catchable as `Exception` so no path can escape uncaught.
- On-device repro is impractical (requires a set marked installed with a wrong-arch payload); behaviour is verified by inspection + build.

---

## Last Audit

**Date:** 2026-06-18
**Mode:** strategic (compact)
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 6 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

### Manual / on-device

- [ ] On-device repro impractical (needs a set marked installed with a wrong-arch payload) - verified by inspection + build per §4. Confirmed statically: `DeliveredNativeLibraryIncompatibleException : IOException`; loader warm-load catch logs `Timber.w` (no `Timber.e`) + throws typed, no set invalidation; `RecognitionBackend` 3 sites typed-catch at `Timber.i` -> null; `TranslationBackend.ensureNativeLibrariesLoaded` typed-catch at `Timber.i` -> false; 0 debug tags. Dev log completed this audit (3 missing file entries added: exception class, RecognitionBackend, TranslationBackend).
