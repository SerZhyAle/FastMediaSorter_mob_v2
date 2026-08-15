# S1234 - The brand waves-and-particles animation behind the welcome pages

**Status:** Archived
**Priority:** 75
**Date:** 2026-07-27
**Tier:** 3 - Moderate (ad-hoc)

## 0. Raw capture

Owner, 2026-07-27:

> "/spec-draft на фоне у welcome экранов наша брендовая анимация"

Owner, 2026-07-29, clarifying what "brand animation" means and how far it should go:

> "брендовая анимация это частицы и линии которые мы используем по умолчанию при проигрывании аудио и на сайте"

> "я хочу ее распространить на страницы welcome и лаунчера"

## 1. What already exists

The animation is real, shipped, and reusable - this ticket is a placement job, not new artwork.

- `ui/player/helpers/AudioWaveParticleView.kt` - a plain `View` in `src/main` that draws 5-12 sine paths and 15-55 drifting particles onto an off-screen bitmap with a motion-blur trail. It is **fully procedural**: it takes no audio input, no visualizer session, no permission. Its whole contract is `startAnimation()` / `pauseAnimation()` / `stopAndReset()`.
- It already has a low-RAM path that cuts object counts on weak devices, and its comments state the speed constant is matched to the HTML canvas version on the site - so phone and site already agree.
- The audio player selects it through `AudioEmptyStateController` as `MODE_CANVAS_WAVES`, one of five backdrop modes.

**The launcher half of the owner's request is already built.** `src/launcherEnabled/.../LauncherWallpaperManager.kt` renders the same view as `LauncherWallpaper.Branded`, pausing it off-foreground. That work is **S1101**, currently `BlockNeedUserTest` - it needs the owner's device test, not new development. This ticket therefore covers welcome only.

## 2. Problem

Welcome is the first screen a new user ever sees, and it is the one surface where the brand is not present. The audio player has the animation, the launcher desktop has it, the website has it; onboarding shows flat colour.

## 3. Goals

1. The waves-and-particles animation plays behind the welcome pages.
2. It survives page swipes as one continuous animation, rather than restarting per page.
3. It stops when welcome is not in the foreground.
4. Every welcome page stays legible over it.

**Non-goals:**

- The launcher desktop - already delivered by **S1101**.
- Any change to the animation itself (colours, speed, density) - it must stay identical to the audio player and the site.
- Making it configurable. If the owner wants an off switch, that is a separate ticket.

### 3.3 Owner inputs (Approval gate)

- **Legibility:** semi-transparent panels behind the text, not a full-screen scrim and not a reduced-alpha animation (owner, 2026-07-29: "выполни как легче всего, текстовые блоки можно обвести полупрозрачным блоками").
- **Coverage:** every page (owner, 2026-07-29: "по всем страницам").
- **Per-page colour:** moves from the page background into the panels - "фон текста и блоков под текстом будет узнаваемо разными", so the existing `welcome_page_N_background` palette becomes the panel tint and the animation takes over the page itself.
- **Flavors:** all - welcome is not flavor-gated.
- **Localization:** none - no new user-visible strings.
- **Related tickets:** S1101, S1235, S1237.

## 4. Architecture context

`activity_welcome.xml` is a `ConstraintLayout` holding a `ViewPager2` plus the bottom navigation row, and it carries the screen's `android:background` colour. The page layouts (`page_welcome*.xml`) declare **no root background** of their own, so they are transparent and whatever the activity puts behind them shows through.

That gives a single clean insertion point: one `AudioWaveParticleView` as the first child of the activity root, behind the pager. One instance, one lifecycle, continuous across swipes - the alternative, adding it to each page layout, would mean 14 files (7 pages x portrait/landscape), an instance per page, and a restart on every swipe.

`activity_welcome.xml` exists in `layout/`, `layout-sw480dp/` and `layout-sw720dp/` - all three must be edited together. Note there is no `-land` variant of the activity itself; the pages carry the landscape variants, and per the project's qualifier precedence `sw` beats `-land`, so the three files above are the complete set.

## 5. Proposed approach

- Add the animation layer as the first child of the activity root in all three `activity_welcome.xml` variants, `match_parent`, non-clickable, non-focusable, `contentDescription="@null"` so it never enters the D-pad chain or is announced.
- Drive it from the welcome activity's lifecycle: start on resume, pause on stop - mirroring what `LauncherWallpaperManager` already does, so the two surfaces behave identically.
- Leave the activity's existing background colour in place underneath; the animation draws over it.

## 6. Owner decisions (2026-07-29)

1. **Legibility: translucent panels behind the text.** Explicitly the cheapest option - no scrim layer, no alpha change to the animation, no dark-theme-only restriction. The animation stays exactly as the audio player and the site render it.
2. **Coverage: all pages.** No brand-moment-only variant.
3. **The per-page palette survives, relocated.** `WelcomeActivity.pageBackgrounds` currently swaps `binding.root`'s background through `welcome_page_1..N_background` as the pager moves. Those colours become the tint of the translucent panels instead, so each page stays recognisable while the animation shows through around them.

Implication for the implementation: the root no longer carries the per-page colour, so `applyPageBackground` must retarget from `binding.root` to the panels rather than simply being deleted.

## 6.1 Implementation state (2026-07-29)

- `res/layout{,-sw480dp,-sw720dp}/activity_welcome.xml` - `AudioWaveParticleView` as the first child, non-focusable, `contentDescription="@null"`.
- `ui/welcome/WelcomeActivity.kt` - a `DefaultLifecycleObserver` starts the backdrop on `onStart` and pauses it on `onStop`; `applyPageBackground` and the `pageBackgrounds` field are gone, so the root no longer repaints per page. The observer rather than two lifecycle overrides because this class already sits exactly on detekt's 40-function ceiling - the pair of overrides pushed it over, the observer keeps it under.
- `ui/welcome/WelcomePagePalette.kt` - new home of the seven page colours, shared so the activity and the adapter cannot drift.
- `ui/welcome/WelcomePagerAdapter.kt` - `applyContentPanel` tints `layoutContent` with the page colour at 82 % over `bg_welcome_content_panel`, once for every view type rather than seven times.
- `res/drawable/bg_welcome_content_panel.xml`, `res/values/dimens.xml` - the rounded translucent panel and its corner.
- `res/layout{,-land}/page_welcome_default_player.xml` - the only page that lacked a content container; portrait got the id on its existing column, landscape gained a `FrameLayout` root so the id could sit on a real container.

Two traps found on device, both now fixed:

- **View binding refuses a layout whose root id disagrees between configurations.** Putting `layoutContent` on the landscape root failed the build with "Configurations for page_welcome_default_player.xml must agree on the root element's ID"; the id has to live on an inner view, so the landscape variant gained a `FrameLayout` root.
- **A rotation "bug" that was really the test device.** The backdrop went flat black after every rotation and stayed black. Two attempted fixes changed nothing, because the cause was not in the app: emulator-5554/5556 ship with `animator_duration_scale = 0`, so every `ValueAnimator` completes instantly. The backdrop had been rendering exactly one frozen frame all along; `onSizeChanged` then cleared the buffer on rotation and no animator was left to repaint it. With the three animation scales set to 1.0 the backdrop animates correctly through rotation **with no code change at all**, which is what the shipped implementation relies on.

  Both attempted fixes were reverted rather than kept "just in case": neither made the animations-off case work either (a zero-duration `ValueAnimator` restarted from the host produces no update callback), and the stronger one re-randomised the visuals on every rotation - a real regression traded for an imaginary one. The residual defect, blank backdrop after rotation **only** when the user has disabled system animations, is parked as **S1277**.

  Lesson for anyone verifying this ticket: run `adb shell settings get global animator_duration_scale` before judging the animation. A zero there makes a working animation look broken.

## 6.2 Bottom-nav readability fix (2026-08-03, owner device report)

- The backdrop paints its own black canvas in both themes, so every bottom-nav control that coloured itself from theme attributes lost its contrast: `btnPrevious` (`?attr/colorOnSurface`, plus `alpha 0.7`) and `btnEnableAll` (`?attr/colorPrimary`) rendered near-black on black in the light theme. Only the pages sit on a translucent panel; the nav bar sits on the raw animation.
- Both now use fixed light values - `@color/white` label, `@color/white_50_alpha` stroke - because the surface behind them is black regardless of theme, and `btnPrevious` moved from the text style to the outlined one so it reads as a button.
- `margin_small` start/end margins on `btnPrevious`/`btnNext`/`btnFinish`: `welcome_top_nav_padding` is 2dp below sw480dp, which left the newly visible outline flush with the screen edge.
- Applied to all three `activity_welcome.xml` variants. Verified on emulator-5554 (light theme): page 1 shows a legible outlined "Enable all", page 2 a legible outlined "Previous", neither clipped.

## 7. Risks

- **Legibility** - the highest one; see §6.1. Onboarding is the worst place to make text hard to read.
- **First-launch cost** - welcome runs on an unknown, possibly cold device. Mitigated by the view's existing low-RAM path, but worth measuring rather than assuming.
- **Battery** - much smaller than the launcher case (welcome is short-lived), provided §5's stop-on-stop is honoured.

## 8. User impact

New capability - one sentence for `docs/FEATURES*` once delivered: the brand animation now plays behind onboarding.

## 9. Related

- **S1101** `launcher-desktop-wallpaper-options` - already renders this animation as the default launcher desktop; in `BlockNeedUserTest` awaiting the owner's device test. Delivers the launcher half of the 2026-07-29 request.
- **S1235**, **S1237** - other open welcome-page tickets; if any of them restructures `activity_welcome.xml`, sequence them with this one.

## Last Audit

### Manual - 2026-07-29, emulator-5554 (API 37, sw800dp tablet, 1600x2560 @320dpi, standard debug)

Objective half of the acceptance note only. Criterion (2), "text stays comfortable to read", is an aesthetic call and is deliberately not ruled on here - the measurements below exist so the owner can make it.

**Precondition honoured.** `animator_duration_scale` was unset (`null`) on this AVD - the §6.1 trap. It was set to 1.0 for the run and deleted afterwards, so every reading below is of a genuinely running animation. The app has no animation switch of its own: `AppSettings.audioEmptyStateMode` governs the audio player's backdrop, not this one. The probe `S1234: welcome brand animation start` fired on entry.

**Criterion (1) - waves around a translucent, per-page-tinted panel: PASS on all six pages.**

Method: two screenshots ~700 ms apart per page. An opaque surface yields a frame delta of exactly 0, a translucent one lets the animation through. Panel tint is the modal pixel colour inside the panel, not an eyeball read.

| Page | Panel tint | Panel frame delta (mean / moved) | Body-text contrast |
|---|---|---|---|
| 1 intro | `#BAC7D0` | 3.13 / 14.5% | 7.36:1 |
| 2 profiles | `#BECAC0` | 3.28 / 15.2% | 7.56:1 |
| 3 networks | `#C8BDCA` | 4.21 / 19.9% | 5.06:1 |
| 4 functions | `#D2C8B9` | 4.71 / 21.6% | 7.57:1 |
| 5 permissions | `#C5CABE` | 1.27 / 6.4% | 5.38:1 |
| 6 default player | `#C1C4C6` | 3.97 / 18.2% | 7.28:1 |

- Every tint matches its `WelcomePagePalette` entry composited at `PANEL_ALPHA` 0.82 over the backdrop, worst case dE 0.72 - the palette relocation of §6 landed exactly as designed.
- Backdrop outside the panel moves by mean 21-27 per channel with 21-26% of pixels changing, so the waves are running rather than showing one frozen frame.
- Method control: the opaque `cardPermissions` on page 5 measured `0.000 / 0.00% / max 0` on the same frame pair where the bare backdrop strip measured 20.4, which is what makes the non-zero panel deltas trustworthy.
- This build shows six pages, so `welcome_page_7_background` never renders here - the seventh palette entry is unexercised, not broken.

**Page 5 is the one page where the effect is thin.** `cardPermissions` is an opaque surface covering the panel from y=428 downwards, so the translucent tinted band is only the header, roughly the top 15% of the panel. The waves do show through that band, but the page reads as a solid list rather than as glass. This meets the letter of criterion (1) and is still worth the owner's eye.

**Perceptual separation between pages.** CIE76 dE across the 15 pairs: min 2.79, max 15.25. Only `p2 profiles / p5 permissions` at 2.79 sits near the ~2.3 just-noticeable-difference threshold; `p1 intro / p6 default player` at 5.08 is next closest. The tints differ everywhere, and the two close pairs follow from the pastel palette at 0.82 alpha rather than from a fault in the implementation.

**Criterion (2) - readability: measured, not judged.** Body-text contrast against the panel ranges 5.06:1 (page 3) to 7.57:1 (page 4). All six clear WCAG AA for normal text (4.5:1) and four also clear AAA (7:1). The two lowest, pages 3 and 5, are secondary grey copy (`#474751`, `#47494F`) rather than the primary near-black used elsewhere. Animation bleed-through inside the panel is mean 3-4.7 of 255 in light theme. `PANEL_ALPHA` is still the knob.

**Criteria (3) and (5) - PASS.** The backdrop keeps animating across a rotation in both directions (mean delta 39.2 then 15.5, against 0 for a frozen frame) with animator scale at 1.0, as §6.1 predicts. The device is sw800dp, so this is the tablet case: the panel is inset 56 px on each side and the waves show in those margins and in the bands above and below.

**Criterion (4) - dark theme: PASS.** The page 1 panel measured `#161E68` against `#161D68` predicted for `#1A237E` at 0.82. Bleed-through inside the panel is far stronger here - mean 19.5 with 36% of pixels moving, against 3.1 in light theme - because bright waves sit against a dark panel instead of a pale one. This is the configuration most likely to move the owner's readability call.

All device state changed for the run (animator scale, `welcome_completed`, rotation, night mode) was restored. Captures and measurement scripts: `temp/S1234/`.
