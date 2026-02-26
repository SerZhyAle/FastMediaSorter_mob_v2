# Technical Specification: Dynamic Background Extension Effect

**Last Updated**: 2026-02-25

---

## 1. Objective

Eliminate "black bars" (letterbox/pillarbox) when an image doesn't match the viewport aspect ratio.

**Core idea**: each pixel on the image edge emits a single colored line that extends outward to fill the empty space. The result is a "scanline bleed" — the image content bleeds into the background through parallel stripes.

---

## 2. Visual Description

```
[ LINE FROM PIXEL 0 ][    IMAGE    ][ LINE FROM PIXEL 0 ]
[ LINE FROM PIXEL 1 ][    IMAGE    ][ LINE FROM PIXEL 1 ]
[ LINE FROM PIXEL 2 ][    IMAGE    ][ LINE FROM PIXEL 2 ]
...                  [    IMAGE    ]  ...
[ LINE FROM PIXEL N ][    IMAGE    ][ LINE FROM PIXEL N ]
```

For **pillarbox** (image narrower than viewport → bars on left/right):
- Each **row** of displayed image pixels maps to one **horizontal line** colored from the corresponding edge pixel.

For **letterbox** (image wider than viewport → bars on top/bottom):
- Each **column** of displayed image pixels maps to one **vertical line** colored from the corresponding edge pixel.

No modes. No variance analysis. Always streaked lines from every pixel.

---

## 3. Algorithm

### 3.1. Precondition Check

Compute displayed image bounds inside the viewport:
- `img_left`, `img_top`, `img_right`, `img_bottom` — pixel coordinates within the viewport.

If image fills the entire viewport → skip, nothing to draw.

### 3.2. Scale Mapping

Map each viewport coordinate back to a source image pixel:

```
img_x = clamp( Floor(vp_x * ImageWidth  / DisplayedImageWidth),  0, ImageWidth  - 1 )
img_y = clamp( Floor(vp_y * ImageHeight / DisplayedImageHeight), 0, ImageHeight - 1 )
```

### 3.3. Pillarbox Rendering (Left and Right Bars)

For every `vp_y` from `0` to `ViewportHeight - 1`:
1. Map to image row: `img_y` via scale mapping above.
2. **Left bar**: `color = image.getPixel(0, img_y)` → draw horizontal line at `vp_y` from `x=0` to `x=img_left - 1`.
3. **Right bar**: `color = image.getPixel(ImageWidth - 1, img_y)` → draw horizontal line at `vp_y` from `x=img_right` to `x=ViewportWidth - 1`.

### 3.4. Letterbox Rendering (Top and Bottom Bars)

For every `vp_x` from `0` to `ViewportWidth - 1`:
1. Map to image column: `img_x` via scale mapping above.
2. **Top bar**: `color = image.getPixel(img_x, 0)` → draw vertical line at `vp_x` from `y=0` to `y=img_top - 1`.
3. **Bottom bar**: `color = image.getPixel(img_x, ImageHeight - 1)` → draw vertical line at `vp_x` from `y=img_bottom` to `y=ViewportHeight - 1`.

---

## 4. Optional: Blur Pass

After rendering all lines, apply a **Gaussian blur** to the filled bar areas only (not the image itself).

- Kernel radius: 8–20px (configurable via settings).
- Purpose: smooth harsh color transitions between adjacent lines.
- Implementation: `RenderEffect.createBlurEffect()` (API 31+), fallback `RenderScript` for older APIs.

Raw streaked look without blur is a valid default.

---

## 5. Performance Requirements

| Requirement | Detail |
|---|---|
| **Threading** | All pixel reads and bitmap writes on `Dispatchers.IO`. Post result bitmap to UI thread. |
| **Debounce** | 100ms delay after image settle. Cancel previous job with `Job.cancel()` before launching new one. |
| **Bitmap reuse** | Reuse background `Bitmap` if viewport dimensions unchanged. Recycle old bitmap explicitly before reassign. |
| **Bulk pixel access** | Use `Bitmap.getPixels(int[], ...)` to extract full edge row/column at once — never `getPixel()` in a loop. |

---

## 6. Android Implementation Notes

**Drawing approach** (choose one):

- **Per-line**: `paint.strokeWidth = 1f`; call `canvas.drawLine(x0, vp_y, x1, vp_y, paint)` per row (pillarbox), or `canvas.drawLine(vp_x, y0, vp_x, y1, paint)` per column (letterbox).
- **Scaled bitmap** (preferred): extract edge pixels into a `1 × ViewportHeight` Bitmap (pillarbox) or `ViewportWidth × 1` Bitmap (letterbox), then `canvas.drawBitmap(src, null, barRect, null)` — single hardware-accelerated draw call.

**Layer setup**: place a `View` or `ImageView` behind the main image view in Z-order. Set the generated `Bitmap` via `setImageBitmap()` after background computation.

---

## 7. Out of Scope

- Sampling / variance analysis — not applicable.
- Uniform fill (solid color average) — removed.
- Color averaging or blending from multiple pixels per line.

---

## 8. Acceptance Criteria

- [ ] Portrait image on landscape screen: horizontal colored streaks on left and right, matching image left/right edge pixel colors row by row.
- [ ] Landscape image on portrait screen: vertical colored streaks on top and bottom, matching image top/bottom edge pixel colors column by column.
- [ ] No black bars remain.
- [ ] Background updates asynchronously; main image display is never blocked.
- [ ] No memory leaks: previous bitmap recycled before new one is assigned.
- [ ] Optional blur is toggle-able without functional regression.
