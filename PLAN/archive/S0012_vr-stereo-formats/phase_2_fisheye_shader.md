# Phase 2 — Fisheye Undistortion Shader

**File:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrStereoRenderer.kt`
**Status:** [x] done

## Context

`VR180_FISHEYE_SBS` routes to `EQUIRECT_2` layer with SBS UV rects (left=[0,0,0.5,1], right=[0.5,0,0.5,1]).
The standard UV-crop shader passes pixels through unchanged, so the fisheye-projected frame reaches the
equirectangular compositor raw — the viewer sees circular lens distortion instead of a flat half-sphere.

Fix: add a second GL shader program that performs inverse equidistant fisheye projection.
For each output pixel in equirect space, compute the corresponding input sample position in the fisheye frame.

**Projection math (equidistant fisheye, FOV = 180°):**
```
output equirect UV (u_eq, v_eq) → spherical direction:
  θ (azimuth)  = (u_eq − 0.5) × π        range: [−π/2, +π/2]
  φ (elevation)= (0.5 − v_eq) × π        range: [+π/2, −π/2]  (Y flipped: v=0 is top)

direction vector:
  d = (sin(θ)·cos(φ),  sin(φ),  cos(θ)·cos(φ))

polar angle from optical axis:
  ρ = acos(clamp(d.z, −1, 1))

equidistant fisheye radius (normalized, 1 = lens edge at ρ=π/2):
  r = ρ / (π/2)

pixels outside ρ > π/2 are outside the lens circle → output black

azimuth in lens plane:
  α = atan(d.y, d.x)

fisheye lens UV (within one eye's half of the SBS frame):
  u_lens = 0.5 + 0.5 · r · cos(α)
  v_lens = 0.5 + 0.5 · r · sin(α)
```

Eye selection: pass `uFisheyeUOffset` uniform (0.0 for left eye, 0.5 for right eye).
Final sample UV: `u_src = uFisheyeUOffset + u_lens × 0.5`, `v_src = v_lens`.

## Steps

### 2-1 Add fisheye shader source strings

In `VrStereoRenderer`, add two new private `val`s alongside the existing shader sources inside `initGl()`:

```kotlin
val fisheyeVertexSrc = """
    attribute vec4 aPosition;
    attribute vec2 aTexCoord;
    varying vec2 vTexCoord;
    void main() {
        gl_Position = aPosition;
        vTexCoord = aTexCoord;
    }
""".trimIndent()

val fisheyeFragSrc = """
    #extension GL_OES_EGL_image_external : require
    precision mediump float;
    varying vec2 vTexCoord;
    uniform samplerExternalOES uTexture;
    uniform float uFisheyeUOffset;
    const float PI = 3.14159265359;
    void main() {
        float theta = (vTexCoord.x - 0.5) * PI;
        float phi   = (0.5 - vTexCoord.y) * PI;
        float dx = sin(theta) * cos(phi);
        float dy = sin(phi);
        float dz = cos(theta) * cos(phi);
        float rho = acos(clamp(dz, -1.0, 1.0));
        if (rho > PI * 0.5) {
            gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
            return;
        }
        float r = rho / (PI * 0.5);
        float az = atan(dy, dx);
        float uLens = 0.5 + 0.5 * r * cos(az);
        float vLens = 0.5 + 0.5 * r * sin(az);
        float uSrc = uFisheyeUOffset + uLens * 0.5;
        gl_FragColor = texture2D(uTexture, vec2(uSrc, vLens));
    }
""".trimIndent()
```

### 2-2 Add member fields for fisheye program

Add alongside the existing shader program fields:

```kotlin
private var fisheyeProgram: Int = 0
private var fAPositionLoc: Int = -1
private var fATexCoordLoc: Int = -1
private var fUTextureLoc: Int = -1
private var fUFisheyeUOffsetLoc: Int = -1
```

### 2-3 Compile and link fisheye program in `initGl()`

After the standard program link succeeds, add:

```kotlin
val fisheyeVert = compileShader(GLES20.GL_VERTEX_SHADER, fisheyeVertexSrc)
val fisheyeFrag = compileShader(GLES20.GL_FRAGMENT_SHADER, fisheyeFragSrc)
if (fisheyeVert != 0 && fisheyeFrag != 0) {
    fisheyeProgram = GLES20.glCreateProgram()
    GLES20.glAttachShader(fisheyeProgram, fisheyeVert)
    GLES20.glAttachShader(fisheyeProgram, fisheyeFrag)
    GLES20.glLinkProgram(fisheyeProgram)
    val fisheyeLinkStatus = IntArray(1)
    GLES20.glGetProgramiv(fisheyeProgram, GLES20.GL_LINK_STATUS, fisheyeLinkStatus, 0)
    if (fisheyeLinkStatus[0] == 0) {
        Timber.e("VrStereoRenderer: fisheye program link failed: %s",
            GLES20.glGetProgramInfoLog(fisheyeProgram))
        GLES20.glDeleteProgram(fisheyeProgram)
        fisheyeProgram = 0
    } else {
        fAPositionLoc       = GLES20.glGetAttribLocation(fisheyeProgram, "aPosition")
        fATexCoordLoc       = GLES20.glGetAttribLocation(fisheyeProgram, "aTexCoord")
        fUTextureLoc        = GLES20.glGetUniformLocation(fisheyeProgram, "uTexture")
        fUFisheyeUOffsetLoc = GLES20.glGetUniformLocation(fisheyeProgram, "uFisheyeUOffset")
        Timber.i("VrStereoRenderer: fisheye GL program initialized  program=%d", fisheyeProgram)
    }
    GLES20.glDeleteShader(fisheyeVert)
    GLES20.glDeleteShader(fisheyeFrag)
}
```

### 2-4 Dispatch to fisheye render in `renderEye()`

In `renderEye()`, replace the single `renderQuad()` call with a conditional dispatch:

```kotlin
if (context.stereoMode == StereoMode.VR180_FISHEYE_SBS) {
    val uOffset = if (context.eye == VrEye.LEFT) 0f else 0.5f
    renderFisheyeQuad(
        oesTextureId = oesTextureId,
        fisheyeUOffset = uOffset,
        viewport = Viewport(0, 0, context.targetWidthPx, context.targetHeightPx),
        targetWidthPx = context.targetWidthPx,
        targetHeightPx = context.targetHeightPx,
    )
} else {
    val plan = planner.buildRenderPlan(context, descriptor)
    renderQuad(
        oesTextureId = oesTextureId,
        uOffset = plan.uv.uOffset,
        vOffset = plan.uv.vOffset,
        uScale = plan.uv.uScale,
        vScale = plan.uv.vScale,
        viewport = plan.viewport,
        targetWidthPx = context.targetWidthPx,
        targetHeightPx = context.targetHeightPx,
        swapchainImageIndex = context.swapchainImageIndex,
    )
}
```

### 2-5 Add `renderFisheyeQuad()` private method

```kotlin
private fun renderFisheyeQuad(
    oesTextureId: Int,
    fisheyeUOffset: Float,
    viewport: VrRenderPlanner.Viewport,
    targetWidthPx: Int,
    targetHeightPx: Int,
) {
    if (!isGlInitialized || fisheyeProgram == 0) {
        Timber.w("VrStereoRenderer: fisheye program not ready, skipping")
        return
    }

    GLES20.glViewport(viewport.x, viewport.y, viewport.width, viewport.height)
    GLES20.glUseProgram(fisheyeProgram)

    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
    GLES20.glUniform1i(fUTextureLoc, 0)
    GLES20.glUniform1f(fUFisheyeUOffsetLoc, fisheyeUOffset)

    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo)

    GLES20.glEnableVertexAttribArray(fAPositionLoc)
    GLES20.glVertexAttribPointer(fAPositionLoc, 2, GLES20.GL_FLOAT, false, STRIDE, 0)

    GLES20.glEnableVertexAttribArray(fATexCoordLoc)
    GLES20.glVertexAttribPointer(fATexCoordLoc, 2, GLES20.GL_FLOAT, false, STRIDE, 2 * FLOAT_BYTES)

    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

    GLES20.glDisableVertexAttribArray(fAPositionLoc)
    GLES20.glDisableVertexAttribArray(fATexCoordLoc)
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
    GLES20.glUseProgram(0)
}
```

### 2-6 Release fisheye program in `release()`

After the existing `glDeleteProgram(shaderProgram)` block, add:

```kotlin
if (fisheyeProgram != 0) {
    GLES20.glDeleteProgram(fisheyeProgram)
    fisheyeProgram = 0
}
```

## Verification

- `initGl()` logs `VrStereoRenderer: fisheye GL program initialized` at info level — confirms shader compiled.
- `renderEye()` with `context.stereoMode = VR180_FISHEYE_SBS, eye = LEFT` calls `renderFisheyeQuad(fisheyeUOffset=0f)`.
- `renderEye()` with `context.stereoMode = VR180_FISHEYE_SBS, eye = RIGHT` calls `renderFisheyeQuad(fisheyeUOffset=0.5f)`.
- `renderEye()` with any non-fisheye stereo mode calls `renderQuad()` (existing path unchanged).
- `release()` with an initialized fisheye program sets `fisheyeProgram = 0` after deletion.
- File stays ≤ 500 LOC — no backup required.
