# Third-Party Brand Assets and Attributions

This document records the third-party brand marks bundled in FastMediaSorter v2, their sources, licensing terms, and conformance notes under the `ICON-EXTERNAL` contract.

## 1. YouTube & YouTube Music

- **Owner:** Google LLC / YouTube LLC
- **Source:** [YouTube Brand Resources](https://brand.youtube/) (`brand.youtube`)
- **Bundled Assets:**
  - `app_v2/src/main/res/drawable/ic_youtube.xml`: Official YouTube Play Icon (red container `#FF0000` with white triangle `#FFFFFF`).
  - `app_v2/src/main/res/drawable/ic_youtube_music.xml`: Official YouTube Music Icon (red disc `#FF0000` with white concentric ring and triangle `#FFFFFF`).
- **Usage Context:**
  - Used as fallback icons for YouTube and YouTube Music launcher gadgets when the corresponding installed apps are not available on the device (`ICON-EXTERNAL` Rule 2).
  - Preserved in official brand colors on solid/standard surfaces with unconstrained bounding boxes per YouTube Brand Guidelines.
- **Terms & Attribution:**
  - YouTube is a trademark of Google LLC.
  - The marks are used in accordance with the official YouTube Brand Guidelines solely to identify YouTube services and external links.

## 2. Google Lens & Gemini

- **Owner:** Google LLC
- **Policy:**
  - Application surfaces targeting Google Lens and Gemini use dynamic installed application icons resolved from `PackageManager` (`ICON-EXTERNAL` Rule 2) with neutral vocabulary glyph fallbacks (`ic_apps`, `ic_gesture_action_assistant`, and standard action glyphs).
  - No proprietary vector marks are bundled without dedicated third-party asset kits.
