package com.sza.fastmediasorter.vr.render

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.opengl.GLES20
import android.opengl.GLUtils
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.FutureTarget
import com.bumptech.glide.signature.ObjectKey
import com.sza.fastmediasorter.core.util.MemoryTier
import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.data.cloud.glide.CloudThumbnailData
import com.sza.fastmediasorter.data.network.glide.NetworkFileData
import com.sza.fastmediasorter.domain.model.StereoMode
import com.sza.fastmediasorter.ui.player.render.RenderState
import com.sza.fastmediasorter.ui.player.render.RenderTarget
import com.sza.fastmediasorter.ui.player.render.RenderPriority
import com.sza.fastmediasorter.ui.player.render.RendererMode
import com.sza.fastmediasorter.ui.player.render.StaticImageRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.roundToInt

/**
 * Static bitmap renderer for VR image playback.
 *
 * The phone UI still owns PhotoView transitions. This renderer mirrors the current still image
 * into the XR pipeline so that flat, 360°, and 180° photos can use the same layer routing model
 * as video without forcing a SurfaceTexture/ExoPlayer decode path.
 */
class VrPhotoSphereRenderer(
    context: Context,
    private val memoryTier: MemoryTier = MemoryTier.detect(context),
) : StaticImageRenderer {

    override val state: StateFlow<RenderState>
        get() = renderState

    private val appContext = context.applicationContext
    private val renderState = MutableStateFlow<RenderState>(RenderState.Idle)
    private val planner = VrRenderPlanner()

    private var currentMode: RendererMode = RendererMode.MANUAL
    private var currentStereoMode: StereoMode = StereoMode.MONO
    private var currentTarget: RenderTarget? = null

    private var shaderProgram: Int = 0
    private var aPositionLoc: Int = -1
    private var aTexCoordLoc: Int = -1
    private var uTextureLoc: Int = -1
    private var uUvOffsetLoc: Int = -1
    private var uUvScaleLoc: Int = -1
    private var quadVbo: Int = 0
    private var textureId: Int = 0
    private var isGlInitialized = false
    private var glMaxTextureSize = 0
    private var hasUploadedTexture = false

    @Volatile
    private var pendingBitmap: Bitmap? = null

    @Volatile
    private var pendingBitmapTarget: FutureTarget<Bitmap>? = null

    override suspend fun render(target: RenderTarget) {
        currentTarget = target
        updateState(RenderState.Loading(target))

        val loadedBitmap = try {
            loadBitmap(target)
        } catch (e: Exception) {
            Timber.e(e, "VrPhotoSphereRenderer: bitmap load failed for %s", target.mediaFile.name)
            updateState(RenderState.Error(target, e))
            return
        }

        replacePendingBitmap(loadedBitmap.bitmap, loadedBitmap.futureTarget)
        updateState(RenderState.Ready(target))
    }

    override suspend fun prefetch(targets: List<RenderTarget>) {
        if (targets.isEmpty()) return

        withContext(Dispatchers.IO) {
            targets.forEach { target ->
                try {
                    val request = Glide.with(appContext)
                        .downloadOnly()
                        .load(resolveGlideModel(target))
                        .signature(ObjectKey(cacheKey(target)))
                        .diskCacheStrategy(DiskCacheStrategy.DATA)
                        .override(requestedDecodeDimension(), requestedDecodeDimension())

                    if (memoryTier == MemoryTier.LOW) {
                        request.set(com.bumptech.glide.load.Option.memory("decodeFormat"), DecodeFormat.PREFER_RGB_565)
                    }

                    val future = request.submit()
                    future.get()
                    Glide.with(appContext).clear(future)
                } catch (e: Exception) {
                    Timber.w(e, "VrPhotoSphereRenderer: prefetch failed for %s", target.mediaFile.name)
                }
            }
        }
    }

    override fun setMode(mode: RendererMode) {
        currentMode = mode
    }

    override fun setStereoMode(mode: StereoMode) {
        currentStereoMode = mode
    }

    override fun onPause() = Unit

    override fun onResume() = Unit

    /** Must be called on the GL thread with the XR EGL context current. */
    fun initGl() {
        if (isGlInitialized) return

        val vertexShaderSrc = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            uniform vec2 uUvOffset;
            uniform vec2 uUvScale;
            void main() {
                gl_Position = aPosition;
                vTexCoord = uUvOffset + aTexCoord * uUvScale;
            }
        """.trimIndent()

        val fragmentShaderSrc = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D uTexture;
            void main() {
                gl_FragColor = texture2D(uTexture, vTexCoord);
            }
        """.trimIndent()

        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexShaderSrc)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderSrc)
        if (vertexShader == 0 || fragmentShader == 0) {
            Timber.e("VrPhotoSphereRenderer: shader compilation failed")
            return
        }

        shaderProgram = GLES20.glCreateProgram()
        GLES20.glAttachShader(shaderProgram, vertexShader)
        GLES20.glAttachShader(shaderProgram, fragmentShader)
        GLES20.glLinkProgram(shaderProgram)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(shaderProgram, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            Timber.e("VrPhotoSphereRenderer: program link failed: %s", GLES20.glGetProgramInfoLog(shaderProgram))
            GLES20.glDeleteProgram(shaderProgram)
            shaderProgram = 0
            return
        }

        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)

        aPositionLoc = GLES20.glGetAttribLocation(shaderProgram, "aPosition")
        aTexCoordLoc = GLES20.glGetAttribLocation(shaderProgram, "aTexCoord")
        uTextureLoc = GLES20.glGetUniformLocation(shaderProgram, "uTexture")
        uUvOffsetLoc = GLES20.glGetUniformLocation(shaderProgram, "uUvOffset")
        uUvScaleLoc = GLES20.glGetUniformLocation(shaderProgram, "uUvScale")

        val quadVertices = floatArrayOf(
            -1f, -1f, 0f, 1f,
             1f, -1f, 1f, 1f,
            -1f,  1f, 0f, 0f,
             1f,  1f, 1f, 0f,
        )
        val vertexBuffer: FloatBuffer = ByteBuffer
            .allocateDirect(quadVertices.size * FLOAT_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(quadVertices)
        vertexBuffer.position(0)

        val vboIds = IntArray(1)
        GLES20.glGenBuffers(1, vboIds, 0)
        quadVbo = vboIds[0]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo)
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER,
            quadVertices.size * FLOAT_BYTES,
            vertexBuffer,
            GLES20.GL_STATIC_DRAW,
        )
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

        val maxTextureSizeOut = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxTextureSizeOut, 0)
        glMaxTextureSize = maxTextureSizeOut[0].coerceAtLeast(DEFAULT_MAX_TEXTURE_SIZE_HINT)
        isGlInitialized = true
        Timber.d("VrPhotoSphereRenderer: GL initialized, maxTextureSize=%d, mode=%s", glMaxTextureSize, currentMode)
    }

    fun hasRenderableTexture(): Boolean = hasUploadedTexture || pendingBitmap != null

    /** Draw the current bitmap into the already-bound XR eye target. */
    fun renderEye(context: VrRenderContext, descriptor: VrLayerDescriptor) {
        if (!isGlInitialized || shaderProgram == 0) return

        uploadPendingBitmapIfNeeded()
        if (!hasUploadedTexture || textureId == 0) return

        val plan = planner.buildRenderPlan(context, descriptor)
        renderQuad(
            texture = textureId,
            uv = plan.uv,
            viewport = plan.viewport,
            targetWidthPx = context.targetWidthPx,
            targetHeightPx = context.targetHeightPx,
        )
    }

    override fun release() {
        clearPendingBitmap()
        hasUploadedTexture = false

        if (textureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
            textureId = 0
        }
        if (shaderProgram != 0) {
            GLES20.glDeleteProgram(shaderProgram)
            shaderProgram = 0
        }
        if (quadVbo != 0) {
            GLES20.glDeleteBuffers(1, intArrayOf(quadVbo), 0)
            quadVbo = 0
        }
        isGlInitialized = false
        updateState(RenderState.Idle)
    }

    private fun renderQuad(
        texture: Int,
        uv: VrRenderPlanner.UvParams,
        viewport: VrRenderPlanner.Viewport,
        targetWidthPx: Int,
        targetHeightPx: Int,
    ) {
        val needsLetterboxClear = viewport.x != 0 || viewport.y != 0 ||
            viewport.width != targetWidthPx || viewport.height != targetHeightPx
        if (needsLetterboxClear) {
            GLES20.glViewport(0, 0, targetWidthPx, targetHeightPx)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        }

        GLES20.glViewport(viewport.x, viewport.y, viewport.width, viewport.height)
        GLES20.glUseProgram(shaderProgram)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glUniform1i(uTextureLoc, 0)
        GLES20.glUniform2f(uUvOffsetLoc, uv.uOffset, uv.vOffset)
        GLES20.glUniform2f(uUvScaleLoc, uv.uScale, uv.vScale)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo)
        GLES20.glEnableVertexAttribArray(aPositionLoc)
        GLES20.glVertexAttribPointer(aPositionLoc, 2, GLES20.GL_FLOAT, false, STRIDE, 0)
        GLES20.glEnableVertexAttribArray(aTexCoordLoc)
        GLES20.glVertexAttribPointer(aTexCoordLoc, 2, GLES20.GL_FLOAT, false, STRIDE, 2 * FLOAT_BYTES)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(aPositionLoc)
        GLES20.glDisableVertexAttribArray(aTexCoordLoc)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glUseProgram(0)
    }

    private fun uploadPendingBitmapIfNeeded() {
        val bitmap = pendingBitmap ?: return
        val pendingTarget = pendingBitmapTarget

        if (textureId == 0) {
            textureId = createTexture2d()
        }

        val bitmapForUpload = scaleToGlLimit(bitmap, glMaxTextureSize)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmapForUpload, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        if (bitmapForUpload !== bitmap) {
            bitmapForUpload.recycle()
        }

        pendingBitmap = null
        pendingBitmapTarget = null
        pendingTarget?.let { Glide.with(appContext).clear(it) }
        hasUploadedTexture = true
    }

    private fun createTexture2d(): Int {
        val textureOut = IntArray(1)
        GLES20.glGenTextures(1, textureOut, 0)
        val texture = textureOut[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        return texture
    }

    private fun scaleToGlLimit(bitmap: Bitmap, maxTextureSize: Int): Bitmap {
        val safeLimit = maxTextureSize.coerceAtLeast(DEFAULT_MAX_TEXTURE_SIZE_HINT)
        if (bitmap.width <= safeLimit && bitmap.height <= safeLimit) {
            return bitmap
        }

        val dimensions = calculateUploadDimensions(bitmap.width, bitmap.height, safeLimit)
        // We downsample before texImage2D because 8K panoramas otherwise spike heap usage and
        // can exceed the headset GPU texture cap even though the decoded file itself is valid.
        return Bitmap.createScaledBitmap(bitmap, dimensions.width, dimensions.height, true)
    }

    private suspend fun loadBitmap(target: RenderTarget): LoadedBitmap = withContext(Dispatchers.IO) {
        val request = buildBitmapRequest(target)
        val futureTarget = request.submit()
        LoadedBitmap(
            bitmap = futureTarget.get(),
            futureTarget = futureTarget,
        )
    }

    private fun buildBitmapRequest(target: RenderTarget): RequestBuilder<Bitmap> {
        val request = Glide.with(appContext)
            .asBitmap()
            .load(resolveGlideModel(target))
            .signature(ObjectKey(cacheKey(target)))
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .priority(resolvePriority(target.priority))
            .override(requestedDecodeDimension(), requestedDecodeDimension())

        if (memoryTier == MemoryTier.LOW) {
            request.format(DecodeFormat.PREFER_RGB_565)
        }

        return request
    }

    private fun requestedDecodeDimension(): Int {
        val textureLimit = glMaxTextureSize.takeIf { it > 0 } ?: DEFAULT_MAX_TEXTURE_SIZE_HINT
        return when (memoryTier) {
            MemoryTier.LOW -> textureLimit.coerceAtMost(4096)
            else -> textureLimit.coerceAtMost(8192)
        }
    }

    private fun resolveGlideModel(target: RenderTarget): Any {
        val mediaFile = target.mediaFile
        val path = target.path

        return when {
            path.startsWith("cloud://", ignoreCase = true) -> {
                val provider = resolveCloudProvider(path)
                val fileId = when (provider) {
                    CloudProvider.DROPBOX -> {
                        val afterScheme = path.substringAfter("cloud://dropbox")
                        "/${afterScheme.trimStart('/')}"
                    }
                    else -> path.substringAfterLast('/')
                }
                CloudThumbnailData(
                    thumbnailUrl = mediaFile.thumbnailUrl ?: "",
                    fileId = fileId,
                    loadFullImage = true,
                    cloudProvider = provider,
                )
            }

            path.startsWith("smb://", ignoreCase = true) ||
                path.startsWith("ftp://", ignoreCase = true) ||
                path.startsWith("sftp://", ignoreCase = true) -> {
                NetworkFileData(
                    path = path,
                    loadFullImage = true,
                    highPriority = true,
                    size = mediaFile.size,
                    createdDate = mediaFile.createdDate,
                )
            }

            else -> {
                val localFile = File(path)
                if (!localFile.canRead() && !mediaFile.contentUri.isNullOrEmpty()) {
                    Uri.parse(mediaFile.contentUri)
                } else {
                    localFile
                }
            }
        }
    }

    private fun resolveCloudProvider(path: String): CloudProvider {
        val authority = path.removePrefix("cloud://").substringBefore("/").lowercase()
        return when (authority) {
            "googledrive", "google_drive" -> CloudProvider.GOOGLE_DRIVE
            "onedrive" -> CloudProvider.ONEDRIVE
            "dropbox" -> CloudProvider.DROPBOX
            else -> CloudProvider.GOOGLE_DRIVE
        }
    }

    private fun resolvePriority(priority: RenderPriority): Priority = when (priority) {
        RenderPriority.NEXT -> Priority.IMMEDIATE
        RenderPriority.PREVIOUS -> Priority.HIGH
        RenderPriority.LOOKAHEAD -> Priority.LOW
    }

    private fun cacheKey(target: RenderTarget): String {
        val mediaFile = target.mediaFile
        return listOf(
            mediaFile.path,
            mediaFile.size,
            mediaFile.lastModified,
            mediaFile.width,
            mediaFile.height,
            currentStereoMode,
        ).joinToString("|")
    }

    private fun replacePendingBitmap(bitmap: Bitmap, futureTarget: FutureTarget<Bitmap>) {
        clearPendingBitmap()
        pendingBitmap = bitmap
        pendingBitmapTarget = futureTarget
    }

    private fun clearPendingBitmap() {
        pendingBitmap = null
        pendingBitmapTarget?.let { Glide.with(appContext).clear(it) }
        pendingBitmapTarget = null
    }

    private fun updateState(newState: RenderState) {
        renderState.value = newState
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) return 0
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            Timber.e("VrPhotoSphereRenderer: shader compile error: %s", GLES20.glGetShaderInfoLog(shader))
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    internal data class LoadedBitmap(
        val bitmap: Bitmap,
        val futureTarget: FutureTarget<Bitmap>,
    )

    internal data class UploadDimensions(
        val width: Int,
        val height: Int,
    )

    companion object {
        private const val FLOAT_BYTES = 4
        private const val STRIDE = 4 * FLOAT_BYTES
        private const val DEFAULT_MAX_TEXTURE_SIZE_HINT = 4096

        internal fun calculateUploadDimensions(
            sourceWidth: Int,
            sourceHeight: Int,
            maxTextureSize: Int,
        ): UploadDimensions {
            if (sourceWidth <= maxTextureSize && sourceHeight <= maxTextureSize) {
                return UploadDimensions(sourceWidth, sourceHeight)
            }

            val scale = minOf(
                maxTextureSize.toFloat() / sourceWidth.toFloat(),
                maxTextureSize.toFloat() / sourceHeight.toFloat(),
            )
            return UploadDimensions(
                width = (sourceWidth * scale).roundToInt().coerceAtLeast(1),
                height = (sourceHeight * scale).roundToInt().coerceAtLeast(1),
            )
        }
    }
}