<sub class="doc-stamp">26.11.13 16:01</sub>

# Third-party licenses

This document lists licenses of third-party assets bundled with FastMediaSorter v2.

Source-code dependencies are managed through Gradle and their licenses are tracked separately by `scripts/docs/generate-oss-notices.ps1`, which derives the notice list from `app_v2/build.gradle.kts` and `wear/build.gradle.kts` and renders it into `docs/OPEN_SOURCE.md` and its RU/UK counterparts. Entries here cover **bundled binary assets** (images, fonts, sample media, audio) where the redistribution requirement is on us, not the build system.

---

## VR diagnostic image - 360° monoscopic sphere fallback

**Asset:** `app_v2/src/vr/res/drawable-nodpi/vr_diagnostic_360_mono.jpg`
**Source:** [Poly Haven - Lakeside HDRI](https://polyhaven.com/a/lakeside) - 8K tonemapped JPG export, equirectangular 8192×4096.
**Author:** Greg Zaal (Poly Haven).
**License:** CC0 1.0 Universal (public domain dedication). No attribution required for usage; attribution is included here as a courtesy and as documentation of provenance.
**Used in flavors:** `vr`, `noLegal`. Not bundled in `standard` / `lite` / `photos` / `legacy`.

### License text

```
Creative Commons Legal Code

CC0 1.0 Universal

The person who associated a work with this deed has dedicated the work to the
public domain by waiving all of his or her rights to the work worldwide under
copyright law, including all related and neighboring rights, to the extent
allowed by law.

You can copy, modify, distribute and perform the work, even for commercial
purposes, all without asking permission.

Full text: https://creativecommons.org/publicdomain/zero/1.0/legalcode
```

## Watch face clock fonts (S3557)

The watch face package (`watchface/src/main/res/font/`) bundles digits-and-colon subsets of nineteen Google Fonts families so its time can repeat the typeface chosen for the phone launcher clock. Full licence texts ship inside the package as `res/raw/third_party_notices.txt`.

- `fms_clock_condensed.ttf` - Roboto Condensed Bold, Copyright 2011 The Roboto Project Authors, SIL Open Font License 1.1.
- `fms_clock_serif.ttf` - Noto Serif Bold, Copyright 2022 The Noto Project Authors, SIL Open Font License 1.1.
- `fms_clock_mono.ttf` - Roboto Mono Bold, Copyright 2015 The Roboto Mono Project Authors, SIL Open Font License 1.1.
- `fms_clock_casual.ttf` - Coming Soon, Apache License 2.0.
- `fms_clock_bebas.ttf` - Bebas Neue, Copyright © 2010 by Dharma Type, SIL Open Font License 1.1 (S4562).
- `fms_clock_audiowide.ttf` - Audiowide, Copyright (c) 2012, Brian J. Bonislawsky DBA Astigmatic (AOETI) (astigma@astigmatic.com), with Reserved Font Names "Audiowide", SIL Open Font License 1.1 (S4562).
- `fms_clock_lobster.ttf` - Lobster, Copyright 2010 The Lobster Project Authors (https://github.com/impallari/The-Lobster-Font), with Reserved Font Name "Lobster", SIL Open Font License 1.1 (S4562).
- `fms_clock_pacifico.ttf` - Pacifico, Copyright 2018 The Pacifico Project Authors (https://github.com/googlefonts/Pacifico), SIL Open Font License 1.1 (S4562).
- `fms_clock_abril.ttf` - Abril Fatface, Copyright (c) 2011, TypeTogether (www.type-together.com), SIL Open Font License 1.1 (S4562).
- `fms_clock_righteous.ttf` - Righteous, Copyright (c) 2011 by Brian J. Bonislawsky DBA Astigmatic (AOETI), SIL Open Font License 1.1 (S4562).
- `fms_clock_pixel.ttf` - Press Start 2P, Copyright 2012 The Press Start 2P Project Authors (cody@zone38.net), with Reserved Font Name "Press Start 2P", SIL Open Font License 1.1 (S4562).
- `fms_clock_sharetech.ttf` - Share Tech Mono, Copyright (c) 2012, Carrois Type Design, Ralph du Carrois (post@carrois.com www.carrois.com), with Reserved Font Name 'Share', SIL Open Font License 1.1 (S4562).
- `fms_clock_stencil.ttf` - Black Ops One, Copyright 2022 The Black-Ops Project Authors (https://github.com/googlefonts/googlefonts-project-template), SIL Open Font License 1.1 (S4562).
- `fms_clock_monoton.ttf` - Monoton, Copyright (c) 2011 by vernon adams (vern@newtypography.co.uk), SIL Open Font License 1.1 (S4562).
- `fms_clock_bungee.ttf` - Bungee, Copyright 2023 The Bungee Project Authors (https://github.com/djrrb/Bungee), SIL Open Font License 1.1 (S4562).
- `fms_clock_terminal.ttf` - VT323, Copyright 2011, The VT323 Project Authors (peter.hull@oikoi.com), SIL Open Font License 1.1 (S4562).
- `fms_clock_blackletter.ttf` - UnifrakturMaguntia, Copyright (c) 2010, j. 'mach' wust, with Reserved Font Name UnifrakturMaguntia, SIL Open Font License 1.1 (S4562).
- `fms_clock_bangers.ttf` - Bangers, Copyright 2010 The Bangers Project Authors (https://github.com/googlefonts/bangers), SIL Open Font License 1.1 (S4562).
- `fms_clock_rubikmono.ttf` - Rubik Mono One, Copyright 2015 The Rubik Project Authors (mail@hubertfischer.com), SIL Open Font License 1.1 (S4562).

## Launcher clock fonts (S4562)

The phone launcher clock (`app_v2/src/launcherEnabled/res/font/`) bundles the same typefaces as the watch face so both show one look: digits, colon, space and the AM/PM letters, each subset renamed "FMS Clock NN" so no Reserved Font Name is carried. Built by `scripts/fonts/build-clock-fonts.py`; licences and copyright lines as listed for the watch face above.

- `fms_dial_condensed.ttf`, `fms_dial_serif.ttf`, `fms_dial_monospace.ttf` - Roboto Condensed Bold, Noto Serif Bold, Roboto Mono Bold, SIL Open Font License 1.1.
- `fms_dial_casual.ttf` - Coming Soon, Apache License 2.0.
- `fms_dial_bebas.ttf` - Bebas Neue, SIL Open Font License 1.1.
- `fms_dial_audiowide.ttf` - Audiowide, SIL Open Font License 1.1.
- `fms_dial_lobster.ttf` - Lobster, SIL Open Font License 1.1.
- `fms_dial_pacifico.ttf` - Pacifico, SIL Open Font License 1.1.
- `fms_dial_abril.ttf` - Abril Fatface, SIL Open Font License 1.1.
- `fms_dial_righteous.ttf` - Righteous, SIL Open Font License 1.1.
- `fms_dial_pixel.ttf` - Press Start 2P, SIL Open Font License 1.1.
- `fms_dial_sharetech.ttf` - Share Tech Mono, SIL Open Font License 1.1.
- `fms_dial_stencil.ttf` - Black Ops One, SIL Open Font License 1.1.
- `fms_dial_monoton.ttf` - Monoton, SIL Open Font License 1.1.
- `fms_dial_bungee.ttf` - Bungee, SIL Open Font License 1.1.
- `fms_dial_terminal.ttf` - VT323, SIL Open Font License 1.1.
- `fms_dial_blackletter.ttf` - UnifrakturMaguntia, SIL Open Font License 1.1.
- `fms_dial_bangers.ttf` - Bangers, SIL Open Font License 1.1.
- `fms_dial_rubikmono.ttf` - Rubik Mono One, SIL Open Font License 1.1.

## Family-mark lockup typeface (S4784)

The product name in the generated lockups (store and social cards, TV banner, splash wordmark) is drawn as outlines of Manrope ExtraBold, the face the FastMediaSorter family names for its lockups. The font file is a generator input under `assets/identity/font/` and is not packaged; only its outlines reach the pictures.

- Manrope ExtraBold (version 4.505, instanced at weight 800), Copyright 2018 The Manrope Project Authors, SIL Open Font License 1.1 (`assets/identity/font/OFL.txt`).
