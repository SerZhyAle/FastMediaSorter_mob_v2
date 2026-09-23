# Brand visual: waves and particles - description and reproduction algorithm

> Status: **Reference** - a descriptive contract read before touching either implementation or before
> producing a new material that should carry the brand's visual identity. Not a spec; no code changes
> follow from this document alone.
> Date: 2026-08-28.
> Implementations described: [AudioWaveParticleView.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioWaveParticleView.kt)
> (Android) and [documentation/assets/wave-particles.js](../documentation/assets/wave-particles.js) (website:
> the served copy of the contract's reference implementation, embedded by every landing and documentation page
> since S3465).
> The normative definition is contract `WAVE-PARTICLES` in the shared contracts catalog; where this page and
> the contract differ, the contract wins.
> The visual it is **not**: [AudioBreathingBarsView.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioBreathingBarsView.kt),
> a sine-animated equalizer-bar view. It looks like an audio visualizer and lives in the same package; it
> carries no brand meaning and must never be offered as "the branded" option.
> Related documents: `OCR-ACCURACY` (`ocr-overlay-accuracy.md` in the shared contracts catalog) - a
> cross-surface constant contract of the same shape, for a different subsystem.

## 1. What this is

The product's signature visual is "waves and particles" - procedural sine-wave lines overlaid with
slowly drifting particles, rendered with a motion-blur trail instead of a hard-edged redraw. It appears,
independently implemented, on the app's audio background, its onboarding screen, its optional launcher
"desktop" wallpaper, and the website header. Nothing about it names a specific piece of music or a
specific device; it is the shape the brand uses whenever it needs a moving background that says "this
product" without saying anything more literal.

This document exists so the algorithm can be reproduced correctly in a context that has no source code
at all - a commissioned graphic, a static export for a store listing, an AI-image-generation prompt, a
print piece - without re-deriving it from whichever implementation happens to be open at the time, and
without accidentally copying a parameter that was never meant to travel (see §5).

## 2. Where it lives today

**Android**, all from one custom `View`, `AudioWaveParticleView`:

- Audio player background, one of five selectable "no cover art" modes
  (`AppSettings.audioEmptyStateMode = MODE_CANVAS_WAVES`, alongside a black screen, a pulsing icon, the
  equalizer bars, and a looping video) -
  [AudioEmptyStateController.kt:26-31](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioEmptyStateController.kt#L26-L31),
  wired at
  [activity_player_unified.xml:145](../app_v2/src/main/res/layout/activity_player_unified.xml#L145)
  and its `-land` counterpart.
- Welcome / onboarding screen background, view id `brandAnimation` -
  [activity_welcome.xml:7](../app_v2/src/main/res/layout/activity_welcome.xml#L7), mirrored in the
  `-sw480dp` and `-sw720dp` layout variants.
- The `launcherEnabled` flavor's in-app "desktop" replacement: one wallpaper option is literally named
  `LauncherWallpaper.Branded`, distinct from a static frame of the same view
  (`LauncherWallpaper.StaticStripes`), a photo, or a live camera feed -
  [LauncherWallpaperManager.kt](../app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherWallpaperManager.kt),
  view wired at
  [activity_launcher_home.xml:25](../app_v2/src/launcherEnabled/res/layout/activity_launcher_home.xml#L25).
  This is the "desktop wallpaper" surface referenced whenever the visual is called "our background" - it
  is an in-app launcher-replacement screen, not the OS wallpaper.

**Website**: one file, `documentation/assets/wave-particles.js`, byte-identical to the contract's
reference implementation (`animated-backdrop/reference/wave-particles.js` in the catalog). It starts
itself on every `<canvas data-wave-particles>`: the page-header hero of `index.html`, `index-ru.html`,
`index-uk.html`, `nolegal.html`, `nolegal-ru.html` and `nolegal-uk.html`, and the full-page documentation
background of `_layouts/doc.html`, `documentation/design-system/index.html` and the sample pages
`scripts/docs/generate-docs-pages.ps1` emits. Each canvas declares its palette, theme source and wash colour
by attribute. Change the catalog file first and copy it over; never edit the served copy alone.

No other maintained source exists. A static desktop-wallpaper image the owner has produced by hand from
this algorithm is not checked in anywhere; if one becomes a maintained asset, add its location here.

## 3. The algorithm, medium-agnostic

The recipe below reproduces the visual in any tool - a `<canvas>`, an `AnimatedVectorDrawable`, After
Effects, a static illustration, or a single still frame for print. It is deliberately stated without
reference to a specific API.

1. Fill the frame with a solid background colour.
2. Each animation tick, before drawing anything new, paint a low-alpha rectangle of that same background
   colour over the whole frame - not a full clear. This is the entire "motion blur": it pulls every
   previous stroke a little further toward the background instead of erasing it outright, which is why
   older detail fades smoothly instead of vanishing on the next tick.
3. Draw several sine-wave lines. Each wave samples points across the frame at a fixed horizontal step;
   the vertical offset at each sample point is `sin(position * spatialFrequency + time + phaseOffset) *
   amplitude`. Waves differ from each other only by their phase/hue offset, so several appear to move as
   one flowing field rather than as identical overlapping copies.
4. Draw a handful of small filled circles ("particles") that drift at a slow, independent velocity and
   bounce off the four edges of the frame by reversing their velocity component on contact.
5. Advance `time` by a small fixed increment and repeat from step 2.

The one detail that breaks if skipped: **the buffer must persist between frames.** A `<canvas>` element
does this on its own - nothing clears it automatically, so step 2's translucent wash is the only thing
touching pixels from the previous frame. Android's `View.onDraw` receives a fresh `Canvas` on every call,
so `AudioWaveParticleView` keeps its own off-screen `Bitmap` (`offBitmap` /
[offCanvas](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioWaveParticleView.kt#L192-L195))
that accumulates the trail across ticks and is simply blitted onto the real `Canvas` each frame
([onDraw, lines 334-336](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioWaveParticleView.kt#L334-L336)).
Rendering into a canvas that gets cleared every frame produces discrete flickering lines, not the brand
visual.

## 4. Parameters, as they exist in each live implementation

| Parameter | Android app (`AudioWaveParticleView`) | Website (`wave-particles.js`, since S3465) |
|---|---|---|
| Wave count | Random 5-12 per session (3-6 on a low-RAM device) | Random 5-12 per session (full profile) |
| Wave sampling step | ~16-24 px (±20% of 20 px), randomized per session | Same |
| Stroke width | Random 3-6 px per session | Same |
| Wave amplitude | Random 28%-48% of view height, further shaped by a per-wave envelope `0.40 + 0.60 * |sin(time*0.4 + wave*0.2)|` (always non-negative) | Same |
| Wave hue / palette | Fully random per session: base hue 0-360°, +8-20° per successive wave, saturation 80% | Contract palette `GREEN`: base hue 95-130°, +2-6° per successive wave, saturation 80% |
| Wave direction | Random flow angle per session; waves are sampled in a rotated coordinate frame | Same |
| Particle count | Random 15-55 per session (6-20 on a low-RAM device) | Random 15-55 per session |
| Particle radius | Random 1-6 px per particle | Same |
| Particle speed | Directional-biased, 0.5x-1.5x global multiplier, occasional counter-drift | Same |
| Particle hue | Random per particle, centred on a random per-session base ±54° | Centred on 100-130°, ±15° |
| Trail wash colour/alpha | Background colour at ~15% alpha (theme-aware: near-black on dark theme, near-white on light theme, S1287) | The page's own background token (`--bg`, `--doc-bg`) at ~15% alpha, re-read when the theme changes |
| Time increment | `0.002` per 1/60 s of elapsed time | Same, paced by elapsed time at any refresh rate |
| Fade-in ramp | First 36 reference frames ramp amplitude/alpha up from 35% (`STARTUP_RAMP_FRAMES`) | Same |
| Theme awareness | Yes - lightness of both waves and particles mirrors across the theme (S1287) | Yes - follows `<html data-theme>` |
| Randomization scope | Every fresh `startAnimation()` / `renderFreshStaticFrame()` re-rolls every parameter above | Every page load rolls a session; a resize keeps it and re-seeds positions only; `prefers-reduced-motion` shows the settled still frame |

Constant names and line numbers for the Android column are in
[AudioWaveParticleView.kt:53-104](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioWaveParticleView.kt#L53-L104)
(session ranges) and
[AudioWaveParticleView.kt:416-472](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioWaveParticleView.kt#L416-L472)
(`tick()`, the per-frame math). The website column is
[wave-particles.js](../documentation/assets/wave-particles.js), whose constants cite `WAVE-PARTICLES section 3`.

## 5. What is a contract versus what is free to differ per surface

**Held in common on purpose** - the actual brand identity, and what a new surface must reproduce:

- The two-layer structure: multiple offset sine-wave lines plus independently drifting particles.
- The motion-blur technique: a low-alpha same-colour wash each tick, never a hard clear.
- The intent that playback speed match across surfaces - stated explicitly in the Android source's own
  comment, unified at `0.002` per animation frame under S2206.

**Expected, deliberately, to differ per surface** - none of these travelling is not a defect:

- The profile: the low-RAM and watch surfaces use the contract's reduced counts; the website uses the full one.
- The palette: the app rolls `DYNAMIC` by default; the website declares `GREEN`.
- Dimming: each surface may be quieter than the contract, never brighter - the website hero at 0.65 opacity,
  the documentation background at 0.35 behind text.

## 6. Resolved drift (S2206, 2026-08-28)

Formerly, `AudioWaveParticleView`'s `TIME_INCREMENT` constant in `app_v2` and `WaveParticleBackground` in `wear`
carried `0.003f` while the then-inline website script incremented `time += 0.002`. Under ticket **S2206**, the canonical animation speed was unified at
`0.002` per tick across both Android modules (`app_v2` and `wear`) and the site canvas.

## 7. Canonical palette for external / arbitrary materials

The website runs the contract palette `GREEN`: every session rolls its hues inside one green band
(lines 95-130°, particles around 100-130°). The two-tone "Pine + Gold" pairing the inline site script
used to draw (green ~135-155°, gold ~40-56°) was retired from the animation by S3465, because the contract
admits only its named palettes; bringing it back is a MINOR contract amendment that adds a palette key. The
Android app re-rolls a random hue every playback session, by design - a variety feature, not a statement
about what colour the brand is.

For a poster, a wallpaper export, a store banner, or any other material meant to read as "the brand" at
a glance, use a `GREEN` session, not a randomly sampled hue from one app session. A random-session frame
is appropriate only when the material is specifically illustrating "the Android app in use" rather than
the brand mark itself.

## 8. Producing one still frame

Both the website and the app animate continuously; neither exposes a single "the" canonical frame, and a
frozen frame straight from `time = 0` looks empty because the trail (§3 step 2) has not built up yet and
the amplitude/alpha ramp has not completed. `AudioWaveParticleView` already contains the correct recipe
for a settled still, used for its own `renderFreshStaticFrame()` / `LauncherWallpaper.StaticStripes`
path
([AudioWaveParticleView.kt:278-286](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioWaveParticleView.kt#L278-L286)):

1. Roll a fresh set of session parameters (§4) - or fix them to the canonical palette of §7 for
   brand-consistent material.
2. Run the per-tick update (§3, steps 2-5) forward **36 iterations** from `time = 0` -
   `STARTUP_RAMP_FRAMES`, the same count the app uses to reach full amplitude and alpha via
   `startupGain = 0.35 + 0.65 * progress * (2 - progress)`
   ([AudioWaveParticleView.kt:426-427](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioWaveParticleView.kt#L426-L427)).
3. Export the buffer after the 36th iteration, not before - a smaller count reads as a thin, unfinished
   trail; a much larger count no longer changes the visible density (the ramp itself saturates at 36).

## 9. Keeping this document honest

This is a description of two independently maintained implementations, not a generated artifact - no
mechanical gate checks it against either source (unlike, for example, `docs/FLAVOR_MATRIX.md`, which is
regenerated from code). When either implementation's parameters change on purpose, update the relevant
row of §4 in the same change; when a change is discovered to have drifted unintentionally, treat it the
way §6 was handled here - park it, do not silently edit this document to match whichever side happened
to be read last.
