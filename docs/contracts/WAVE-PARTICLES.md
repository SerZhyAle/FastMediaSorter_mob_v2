# Pointer - `WAVE-PARTICLES`

| | |
| --- | --- |
| **Id** | `WAVE-PARTICLES` |
| **Version** | 0.10, draft. Owner: this product |
| **Home** | `animated-backdrop/README.md` in the shared contracts catalog |
| **Role here** | owner and producer - phone, launcher wallpaper, watch, and the website hero |

## What this repository must do to stay conformant

- Every surface runs the contract's algorithm with its section 3 constants and section 4 frame, and
  nothing brighter.
- A change to speed, geometry or colour is agreed in the catalog before it lands in any surface; a
  constant that differs is a dated registry exception, not a comment.
- The deviations currently open are recorded as exceptions in the catalog's registry.
- Rung 1 of section 7 is mechanical here: `WaveParticlesContractConstantsTest` in the `app_v2` unit
  tests pairs every section 3 constant of the phone and watch renderers with the reference
  implementation and fails on a drift. A constant the contract does not define is marked
  `CHOSEN HERE` beside it.

## Where it lives here

- Phone and launcher: `app_v2/.../ui/player/helpers/AudioWaveParticleView.kt`.
- Watch: `wear/.../ui/common/WaveParticleBackground.kt`.
- Website: `documentation/assets/wave-particles.js`, byte-identical to the catalog's
  `reference/wave-particles.js` (rung 2), served by every page in place of the former inline copies.
