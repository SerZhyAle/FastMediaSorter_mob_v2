---
name: brand-visual-waves-and-particles
description: The product's signature visual is "waves and particles" (AudioWaveParticleView), shared with the website and the desktop wallpaper - NOT the equalizer bars
metadata:
  type: project
---

**The brand visual across every SZA surface is "волны и частицы" - procedural sine waves plus drifting particles.** Owner correction, 2026-08-15: it runs on the website, on his desktop wallpaper and in the app, and it is what "наша фирменная визуализация" means whenever he says it.

**Why:** the repo contains two audio backdrops side by side in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/`, and picking the wrong one is easy: `AudioBreathingBarsView` (15 sine-animated pastel equalizer bars, and a RINGS mode) is **not** the brand visual, `AudioWaveParticleView` is. I proposed the bars and was corrected. The tell is inside `AudioWaveParticleView` itself: `TIME_INCREMENT` carries the comment "Must match the speed of the HTML canvas version: time += 0.003 per animation frame" - the constant is a cross-surface contract with the site, not a tunable.

**How to apply:**
- "Фирменная визуализация", "наш фон", "как на сайте" -> waves and particles. Never offer the bars as the branded option.
- Its parameters are shared with a web implementation: 5-12 wave paths, 15-55 particles, motion-blur trail via an off-screen bitmap, palette theme-aware since S1287 (do not restore a hardcoded black). Changing speed or feel means changing the site too - treat a tweak as a cross-surface decision, not a local one.
- Public lifecycle to mirror when porting: `startAnimation`, `renderFreshStaticFrame`, `pauseAnimation`, `stopAndReset`.
- Porting it to the `wear` module cannot reuse the class - it is an `app_v2` custom `View` and wear is Compose with no dependency on app_v2. Port the visual language, keep the speed constant, and cut particle/wave counts for the watch battery budget (S1683).
