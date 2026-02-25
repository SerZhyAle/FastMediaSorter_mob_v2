# Technical Specification: Dynamic Background Extension Effect

## 1. Objective
To mitigate the "black bars" effect (letterboxing or pillarboxing) when displaying images that do not match the screen/view aspect ratio. The system should dynamically fill the empty space by extending the colors from the edges of the image, creating an immersive "Ambilight-like" or "bleed" effect.

## 2. General Logic Flow
The feature triggers whenever an image is displayed or resized. It calculates the aspect ratio difference between the **Source Image** and the **Target Viewport**.
1.  **Match**: If ratios match, do nothing.
2.  **Pillarbox (Vertical bars)**: If Image is narrower than Viewport -> Fill Left and Right empty areas.
3.  **Letterbox (Horizontal bars)**: If Image is wider than Viewport -> Fill Top and Bottom empty areas.

## 3. Detailed Algorithm

### 3.1. Initialization & Scaling
Before processing, determine scaling factors to map Viewport coordinates back to Source Image pixel coordinates.
*   `ScaleFactor_W`: Viewport Width / Image Width
*   `ScaleFactor_H`: Viewport Height / Image Height

### 3.2. Sampling Strategy
Instead of processing every pixel, use a "Sampling Step" to improve performance. Define a `StepSize` (e.g., 100 pixels) for initial analysis.

For a specific edge (e.g., Left Edge):
1.  Iterate along the edge (Y-axis 0 to Height).
2.  Sample pixels from column 0 of the Source Image.
3.  Store these colors in a collection `SampledColors`.

### 3.3. Uniformity Analysis (Variance Check)
Determine if the edge is visually simple (e.g., a solid blue sky) or complex (e.g., patterned wallpaper).

1.  **Trim Noise**: Sort the R, G, and B components of `SampledColors` independently. Remove the top 7% and bottom 7% of values to discard outliers (noise/hot pixels).
2.  **Calculate Deviation**:
    *   `Diff_R = Max(R) - Min(R)`
    *   `Diff_G = Max(G) - Min(G)`
    *   `Diff_B = Max(B) - Min(B)`
    *   `TotalDeviation = Diff_R + Diff_G + Diff_B`
3.  **Threshold Check**: Compare `TotalDeviation` against a defined `Threshold` (e.g., 4% of maximum possible color distance).

### 3.4. Rendering Modes

#### Mode A: Uniform Fill (Low Deviation)
If `TotalDeviation < Threshold`, the edge is considered uniform.
1.  Calculate the **Average Color** (Arithmetic Mean) of all R, G, and B values in the sample.
2.  Fill the entire empty rectangular area adjacent to that edge with this single Average Color.
    *   *Implementation Note*: This avoids visual banding on solid backgrounds.

#### Mode B: Perspective Extension (High Deviation)
If `TotalDeviation >= Threshold`, the edge is complex.
1.  Iterate through the empty area perpendicular to the edge (e.g., Y = 0 to Viewport Height for vertical bars).
2.  For every `Y` coordinate on the screen:
    *   Map `Y` to the corresponding `Image_Y` coordinate: `Floor(Y / ScaleFactor_H)`.
    *   Sample the pixel color from the Source Image edge at `Image_Y`.
    *   Draw a **Line** 1 pixel high (or `BrushWidth` height) extending from the image edge outward to the viewport edge.
3.  This creates a "streaked" or "stretched" look matching the image content.

## 4. Implementation Details per Side

### Left/Right Bars (Image < Viewport Aspect Ratio)
*   **Left Bar**:
    *   **Source**: Pixel column `0` (Leftmost).
    *   **Draw**: Horizontal lines from `X=0` to `X=(ViewportWidth - ImageDisplayedWidth) / 2`.
*   **Right Bar**:
    *   **Source**: Pixel column `ImageWidth - 1` (Rightmost).
    *   **Draw**: Horizontal lines from `X=ViewportWidth` back to the image edge.

### Top/Bottom Bars (Image > Viewport Aspect Ratio)
*   **Top Bar**:
    *   **Source**: Pixel row `0` (Topmost).
    *   **Draw**: Vertical lines from `Y=0` to `Y=(ViewportHeight - ImageDisplayedHeight) / 2`.
*   **Bottom Bar**:
    *   **Source**: Pixel row `ImageHeight - 1` (Bottommost).
    *   **Draw**: Vertical lines from `Y=ViewportHeight` back to the image edge.

## 5. Performance Optimization Requirements
1.  **Background Threading**: This calculation must not block the UI thread. It should update the background asynchronously after the main image is displayed.
2.  **Debouncing**: If the user is resizing the window or scrubbing through a slideshow quickly, delay the background generation (e.g., by 50-200ms). Only draw if the image has settled.
3.  **Resource Management**: Ensure previous background bitmaps/textures are explicitly disposed of before creating new ones to prevent memory leaks (OOM errors).
4.  **Coordinate Precision**: When mapping viewport pixels to image pixels for Mode B, use `Floor` rounding to ensure colors align perfectly with the source image pixels, preventing a 1-pixel visual disconnect.

## 6. Android Specifics
*   Use `Bitmap.getPixel` or lock the bitmap hardware buffer for fast pixel access.
*   The "Drawing" is likely best implemented as a custom `Drawable` or a `Canvas` operation on a View behind the main Image View.
*   For Mode B, `Canvas.drawLine` or creating a scaled-up Bitmap (1 pixel wide stretched to Fill Width) works effectively.
