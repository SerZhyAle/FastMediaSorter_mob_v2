# Designer Brief — FastMediaSorter Resource Icon Set

**Project:** FastMediaSorter v2 (Android media browser)
**Deliverable:** 50 SVG icons across 5 themed sets
**Date:** 2026-04-29
**Prepared by:** FastMediaSorter team

---

## 1. Project Context

FastMediaSorter is an Android media browser and sorter app. Inside the app, the user assigns a
custom "resource icon" to each media source (local folder, network share, cloud storage, etc.).
These icons appear in a resource list displayed as a grid or list of cells — each cell is
approximately 64 dp in size. The icon is tinted at runtime using the app's theme color, so it must
work as a single-color shape.

---

## 2. Total Deliverable Count

**50 SVG files** split into 5 themed sets:

| Set ID | Theme | Count |
|--------|-------|------:|
| `01` | Music | 10 |
| `02` | Video | 10 |
| `03` | Image | 10 |
| `04` | Documents | 10 |
| `05` | Other / Abstract | 20 |
| | **Total** | **50** |

---

## 3. Per-Set Theme Guidance

### Set 01 — Music (10 icons)

Treble clefs, musical notes (single and beamed), instruments (guitar, piano keys, headphones,
microphone, speaker, vinyl record, saxophone, drum). Motifs must read as "music" at a glance.

### Set 02 — Video (10 icons)

Film reels, clapperboards, cinema tickets, Hollywood star, film strip, movie projector, popcorn,
VHS cassette, play button inside a screen, camcorder.

### Set 03 — Image / Photo (10 icons)

Landscapes with house, mountain silhouette with sun, flower (simple bloom), car silhouette,
leaf / nature, camera shutter, picture frame, polaroid, beach with palm tree, hot-air balloon.

### Set 04 — Documents (10 icons)

Open book, closed book with bookmark, folder, file stack, business card, newspaper fold,
clipboard with check, briefcase, magnifying glass over document, graduation cap.

### Set 05 — Other / Abstract (20 icons)

Abstract geometric shapes: sphere (isometric), cube (isometric), diamond / gem, hexagon,
pyramid, torus ring, star (5-point), octagon, spiral, ellipse stack, crystalline prism,
molecular lattice node, infinity loop, shield, anchor, compass rose, lightning bolt in circle,
wave form, interconnected dots, ribbon / badge rosette.

---

## 4. Technical Specifications

| Parameter | Requirement |
|-----------|-------------|
| Format | SVG |
| `viewBox` | `0 0 24 24` |
| Fill color | `#000000` (single flat color — runtime tinting replaces it) |
| Stroke | Allowed only if converted to filled path (no `stroke` attributes) |
| Gradients / filters | **Not allowed** |
| Embedded raster | **Not allowed** |
| Background | Transparent (no `<rect>` fill behind the artwork) |
| Line weight | Tuned for 24 dp render — approximately 1.5–2 px stroke width when converted |
| Minimum legibility | Icon must remain recognisable at **16 dp** |

All paths must be merged / flattened before delivery. A single `<path>` element per icon is
preferred; multiple `<path>` elements are acceptable only when unavoidable (e.g. compound cutouts
with even-odd fill), provided they all share the same flat `fill="#000000"`.

---

## 5. Naming Convention

Each file must be named exactly:

```
ico_NN_NNN.svg
```

Where:

- `NN` = two-digit set id: `01`, `02`, `03`, `04`, `05`
- `NNN` = three-digit 1-based ordinal within the set: `001`, `002`, .. `010` (sets 01–04) or `001`, .. `020` (set 05)

**Examples:**

- `ico_01_001.svg` — first music icon
- `ico_02_010.svg` — tenth video icon
- `ico_05_020.svg` — twentieth abstract icon

No spaces, no uppercase, no other characters in the filename.

---

## 6. Acceptance Criteria

Before final delivery, each icon must pass the following checklist:

- [ ] Recognisable as its intended motif at 16 dp
- [ ] Visual weight consistent with sibling icons in the same set (similar bounding-box density)
- [ ] Renders correctly as a single tint color (no color information is preserved at runtime)
- [ ] `viewBox` is exactly `0 0 24 24`
- [ ] No embedded rasters, gradients, filters, or bitmap references
- [ ] File is valid, well-formed SVG (passes an XML validator)
- [ ] Named according to the `ico_NN_NNN.svg` convention

---

## 7. Out of Scope

The following assets are **not** part of this delivery and must not be included:

- Connection-type overlay icons (local storage, SMB/CIFS, SFTP, FTP, cloud provider logos)
  — these are existing app assets already in production.
- App launcher icons, notification icons, or any other UI chrome outside the resource list.
- Animated SVG or Lottie files.

---

## 8. Delivery Format

Deliver a **zip archive** with five subfolders matching set ids:

```
icons_delivery.zip
├── 01_music/
│   ├── ico_01_001.svg
│   ..
│   └── ico_01_010.svg
├── 02_video/
│   ├── ico_02_001.svg
│   ..
│   └── ico_02_010.svg
├── 03_image/
│   ├── ico_03_001.svg
│   ..
│   └── ico_03_010.svg
├── 04_docs/
│   ├── ico_04_001.svg
│   ..
│   └── ico_04_010.svg
└── 05_other/
    ├── ico_05_001.svg
    ..
    └── ico_05_020.svg
```

Include a plain-text `README.txt` inside the archive listing any deviations from this brief,
if any.

---

## Questions / Contact

Direct all questions to the project owner before starting work. Do not make assumptions about
scope — ask first.
