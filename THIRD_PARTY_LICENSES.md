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

The watch face package (`watchface/src/main/res/font/`) bundles digits-and-colon subsets of four Google Fonts families so its time can repeat the typeface chosen for the phone launcher clock. Full licence texts ship inside the package as `res/raw/third_party_notices.txt`.

- `fms_clock_condensed.ttf` - Roboto Condensed Bold, Copyright 2011 The Roboto Project Authors, SIL Open Font License 1.1.
- `fms_clock_serif.ttf` - Noto Serif Bold, Copyright 2022 The Noto Project Authors, SIL Open Font License 1.1.
- `fms_clock_mono.ttf` - Roboto Mono Bold, Copyright 2015 The Roboto Mono Project Authors, SIL Open Font License 1.1.
- `fms_clock_casual.ttf` - Coming Soon, Apache License 2.0.
