# Phase 01 - Loader post-attach `findLibrary` verification + WARN diagnostics

File: `app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/DeliveredNativeLibraryLoader.kt`

Goal: after warm-loading the delivered `.so`, prove each one resolves by soname into the delivered `setDir`. If not, the injection did not take effect on this device/OS - do not mark the set attached; throw `DeliveredNativeLibraryIncompatibleException` (already caught upstream by `RecognitionBackend` for graceful degradation) and log the wrongly-resolved path.

## Steps

1. Add a private helper `resolvesIntoDelivered(setDir: File, payloadFileName: String): Boolean`:
   - Derive the short lib name: strip leading `lib` and trailing `.so` from `payloadFileName` (e.g. `libjpeg.so` -> `jpeg`). If the name does not match `lib*.so`, return `true` (not a name-resolved engine lib; nothing to assert).
   - Get the classloader once: `val cl = javaClass.classLoader as? BaseDexClassLoader ?: return false`.
   - `val resolved = cl.findLibrary(shortName)` (public on `BaseDexClassLoader`; returns absolute path or null).
   - Return `resolved != null && File(resolved).parentFile?.absolutePath == setDir.absolutePath`.

2. In `load()`, after the warm-load `for` loop and before `loadedSets.add(set)`, iterate `soFiles` again and call the helper. On the first `.so` that does not resolve into `setDir`:
   - `Timber.w("DeliveredNativeLibraryLoader: set %s attached to filesDir but %s resolves by name to %s, not the delivered copy - native search path injection ineffective on this device (API %d)", set, payloadFile.fileName, <resolvedPathOrNull>, Build.VERSION.SDK_INT)`.
   - `throw DeliveredNativeLibraryIncompatibleException(set, "name resolution for ${payloadFile.fileName} did not reach delivered dir")`.
   - Do NOT call `invalidateCorruptSet` - the payload is byte-correct (already ADR-3 verified); this is a device-capability failure, same class as the existing `UnsatisfiedLinkError` branch.

3. Keep the permanent WARN free of any `Sxxxx` marker (CLAUDE.md Rule 2 / ticket-log gate). Import `android.os.Build` if not present.

## Verification

- Grep: `DeliveredNativeLibraryLoader.kt` contains `findLibrary(` and a `Timber.w(` referencing `injection ineffective`, and the new `throw DeliveredNativeLibraryIncompatibleException` sits before `loadedSets.add(set)`.
- Grep: no `"S0923` substring in the permanent WARN.
- Compile: `.\a.ps1 fk` (standard) green.
- Reasoning predicate: on a device where injection works, `findLibrary` returns `setDir/lib*.so` -> helper true -> no throw, unchanged behaviour. On the crashing device, helper false -> throw -> `RecognitionBackend` returns null (no crash) instead of reaching `TessBaseAPI()`.
