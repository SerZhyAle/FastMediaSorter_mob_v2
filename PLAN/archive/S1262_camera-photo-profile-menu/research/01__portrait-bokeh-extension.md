# S1262 research 01 - Portrait effect mechanism (strategic §6.1)

**Question:** which stock platform path enables the bokeh/portrait effect and how to detect availability.

## Findings (from the working tree, 2026-07-28)

- The capture stack is CameraX 1.5.3 with `androidx.camera:camera-extensions:1.5.3` already integrated (`app_v2/build.gradle.kts` ~line 1380, added by S0753 for NIGHT).
- `CameraCaptureSessionManager` already holds an `ExtensionsManager` instance (field `extensionsManager`, init around line 174-176) and resolves extension-enabled camera selectors in a single `when` chain at rebind (lines ~667-676): HDR wins over NIGHT, plain selector otherwise.
- Availability is probed per active lens with `extensionsManager.isExtensionAvailable(baseSelector, ExtensionMode.X)` and cached in booleans (`nightExtensionAvailable`, `hdrExtensionAvailable`), then surfaced into `CameraRuntimeCapabilities` (`supportsNightMode`, `supportsHdrExtension`).

## Conclusion

- Portrait = `ExtensionMode.BOKEH` through the exact same mechanism: one more availability boolean at rebind, one more branch in the selector `when` chain, one more capability flag (`supportsBokehExtension`).
- Extension selectors are mutually exclusive by construction of that `when` chain - which matches the profile model (exactly one active profile, ADR-2).
- Availability predicate for the Portrait menu entry: `supportsBokehExtension` on the current capabilities snapshot. Hidden when false (owner decision 2026-07-28) - same pattern as the night button today.
- No new dependency, no API-level branch beyond what camera-extensions already degrades gracefully on.

**Status:** Resolved.
