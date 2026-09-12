// S1270: OpenXR instance, EGL, session, swapchain and GL-asset creation, extracted from
// xr_session.cpp. The GLSL sources live here because createGlAssets is their only reader.

#include "xr_session_internal.h"

namespace fms::xr
{
    namespace detail
    {

        constexpr const char *kVertexShader = R"GLSL(#version 300 es
precision highp float;
layout(location=0) in vec3 a_pos;
layout(location=1) in vec2 a_uv;
uniform mat4 u_viewProj;
out vec2 v_uv;
void main() {
    v_uv = a_uv;
    gl_Position = u_viewProj * vec4(a_pos, 1.0);
}
)GLSL";

        constexpr const char *kFragmentShader = R"GLSL(#version 300 es
precision highp float;
in vec2 v_uv;
uniform sampler2D u_tex;
uniform int u_eyeIndex; // 0 = left, 1 = right
uniform int u_stereoLayout; // 0 = Mono, 1 = Top-Bottom, 2 = Side-by-Side
uniform float u_parallaxShift;
uniform float u_zoomUv; // S0291 round 5: zoom factor applied as UV scaling for sphere/hemi projections. 1.0 = no change. FLAT projection passes 1.0 here and scales via model matrix instead.
out vec4 outColor;
void main() {
    vec2 uv = v_uv;
    if (u_zoomUv != 1.0) {
        // Centre-anchored UV scaling: zoom > 1.0 sees a smaller portion of texture (zoom in).
        uv = (uv - vec2(0.5)) / u_zoomUv + vec2(0.5);
    }
    if (u_stereoLayout == 1) {
        uv.y = uv.y * 0.5 + (u_eyeIndex == 1 ? 0.5 : 0.0);
        uv.x += (u_eyeIndex == 0 ? -u_parallaxShift : u_parallaxShift);
    } else if (u_stereoLayout == 2) {
        uv.x = uv.x * 0.5 + (u_eyeIndex == 1 ? 0.5 : 0.0);
        uv.x += (u_eyeIndex == 0 ? -u_parallaxShift : u_parallaxShift) * 0.5;
    }
    uv = clamp(uv, vec2(0.0), vec2(1.0));
    outColor = texture(u_tex, uv);
}
)GLSL";

        constexpr const char *kExternalVideoFragmentShader = R"GLSL(#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision highp float;
in vec2 v_uv;
uniform samplerExternalOES u_tex;
uniform int u_eyeIndex; // 0 = left, 1 = right
uniform int u_stereoLayout; // 0 = Mono, 1 = Top-Bottom, 2 = Side-by-Side
uniform float u_parallaxShift;
uniform mat4 u_texTransform;
uniform float u_zoomUv; // S0291 round 5: zoom factor for sphere/hemi; FLAT passes 1.0.
out vec4 outColor;
void main() {
    vec2 uv = v_uv;
    if (u_zoomUv != 1.0) {
        uv = (uv - vec2(0.5)) / u_zoomUv + vec2(0.5);
    }
    if (u_stereoLayout == 1) {
        uv.y = uv.y * 0.5 + (u_eyeIndex == 1 ? 0.5 : 0.0);
        uv.x += (u_eyeIndex == 0 ? -u_parallaxShift : u_parallaxShift);
    } else if (u_stereoLayout == 2) {
        uv.x = uv.x * 0.5 + (u_eyeIndex == 1 ? 0.5 : 0.0);
        uv.x += (u_eyeIndex == 0 ? -u_parallaxShift : u_parallaxShift) * 0.5;
    }
    uv = clamp(uv, vec2(0.0), vec2(1.0));
    // S0290 (owner feedback 2026-05-22): video shown upside-down on Quest 3 because the
    // SurfaceTexture transform from MediaCodec expects GL bottom-left convention UV, but
    // our quad mesh is bitmap top-down. Convert just before the transform so stereo math
    // above stays in bitmap convention (correct for TB / SBS half-splits) and the OES
    // sampler sees the orientation it expects. See strategic spec §5.1.B.2.
    uv.y = 1.0 - uv.y;
    vec4 transformed = u_texTransform * vec4(uv, 0.0, 1.0);
    vec4 sampled = texture(u_tex, transformed.xy);
    // S0290 ADR-5 v2: OES external returns RGB in source colorspace - the spec
    // (https://registry.khronos.org/OpenGL/extensions/OES/OES_EGL_image_external.txt)
    // says "no gamma encode or decode" happens on sample. H.264 / HEVC video out of
    // MediaCodec is BT.709 gamma-encoded RGB, and the sRGB swapchain re-encodes on
    // write. Without a decode here, the chain runs gamma TWICE -> over-bright video
    // (Big Buck Bunny visibly brighter than desktop player, observed 2026-05-22).
    // Decode to linear here so the swapchain's encode round-trips correctly.
    outColor.rgb = pow(sampled.rgb, vec3(2.2));
    outColor.a = sampled.a;
}
)GLSL";

        bool hasInstanceExtension(const std::vector<XrExtensionProperties> &props, const char *target)
        {
            for (const auto &prop : props)
            {
                if (std::strcmp(prop.extensionName, target) == 0)
                    return true;
            }
            return false;
        }

        void logInstanceExtensionSupport()
        {
            uint32_t count = 0;
            XrResult r = xrEnumerateInstanceExtensionProperties(nullptr, 0, &count, nullptr);
            if (XR_FAILED(r))
            {
                LOGW("xrEnumerateInstanceExtensionProperties(count)=%d", (int)r);
                return;
            }
            std::vector<XrExtensionProperties> props(count);
            for (auto &prop : props)
            {
                prop.type = XR_TYPE_EXTENSION_PROPERTIES;
                prop.next = nullptr;
            }
            r = xrEnumerateInstanceExtensionProperties(nullptr, count, &count, props.data());
            if (XR_FAILED(r))
            {
                LOGW("xrEnumerateInstanceExtensionProperties(list)=%d", (int)r);
                return;
            }
            LOGD(
                "instance extensions: count=%u android_create=%d opengles_enable=%d",
                count,
                hasInstanceExtension(props, XR_KHR_ANDROID_CREATE_INSTANCE_EXTENSION_NAME) ? 1 : 0,
                hasInstanceExtension(props, XR_KHR_OPENGL_ES_ENABLE_EXTENSION_NAME) ? 1 : 0);
        }

        NativeResult createInstance(JavaVM *vm, jobject activity)
        {
            LOGD("createInstance: begin vm=%p activity=%p", (void *)vm, activity);

            // S0607: initialize the OpenXR loader exactly once per process, with the process-stable
            // Application context (not the per-entry Activity). The loader keeps its own JNI ref to
            // whatever Context it is given; a finish()-ed Activity ref is what aborted the next
            // xrEnumerate* under CheckJNI (S0291). With the Application context that ref never goes
            // stale, so the XrInstance below can be destroyed and recreated on every entry.
            if (!g_loaderInitialized)
            {
                PFN_xrInitializeLoaderKHR initializeLoader = nullptr;
                XrResult lr = xrGetInstanceProcAddr(
                    XR_NULL_HANDLE,
                    "xrInitializeLoaderKHR",
                    reinterpret_cast<PFN_xrVoidFunction *>(&initializeLoader));
                if (XR_FAILED(lr) || initializeLoader == nullptr)
                {
                    LOGE(
                        "xrGetInstanceProcAddr(xrInitializeLoaderKHR)=%d ptr=%p",
                        (int)lr,
                        reinterpret_cast<void *>(initializeLoader));
                    return NativeResult::InstanceCreationFailed;
                }
                // Build a process-lifetime GLOBAL ref to the Application context and hand THAT to
                // the loader. The Quest loader lazily NewGlobalRef's this context (during the first
                // xrEnumerate*), so a local ref deleted after the call crashed CheckJNI. We never
                // delete g_appContextGlobal and never detach the long-lived render thread.
                if (g_appContextGlobal == nullptr)
                {
                    bool loaderEnvAttached = false;
                    JNIEnv *loaderEnv = getAttachedEnv(loaderEnvAttached);
                    if (loaderEnv)
                    {
                        jobject appLocal = getApplicationContextLocal(loaderEnv, activity);
                        if (appLocal)
                        {
                            g_appContextGlobal = loaderEnv->NewGlobalRef(appLocal);
                            loaderEnv->DeleteLocalRef(appLocal);
                        }
                    }
                }
                XrLoaderInitInfoAndroidKHR loaderInfo{XR_TYPE_LOADER_INIT_INFO_ANDROID_KHR};
                loaderInfo.applicationVM = vm;
                loaderInfo.applicationContext = g_appContextGlobal ? g_appContextGlobal : activity;
                lr = initializeLoader(reinterpret_cast<const XrLoaderInitInfoBaseHeaderKHR *>(&loaderInfo));
                if (XR_FAILED(lr))
                {
                    LOGE("xrInitializeLoaderKHR=%d", (int)lr);
                    return NativeResult::InstanceCreationFailed;
                }
                LOGD("xrInitializeLoaderKHR ok (process-stable Application context)");

                // xrEnumerateInstanceExtensionProperties needs the loader initialized first.
                // Diagnostic logging only; runs once right after loader init, before any Activity
                // teardown could make a ref stale.
                logInstanceExtensionSupport();
                g_loaderInitialized = true;
            }

            XrResult r;

            std::vector<const char *> exts = {
                XR_KHR_ANDROID_CREATE_INSTANCE_EXTENSION_NAME,
                XR_KHR_OPENGL_ES_ENABLE_EXTENSION_NAME,
                XR_EXT_HAND_TRACKING_EXTENSION_NAME,
                XR_EXT_HAND_INTERACTION_EXTENSION_NAME,
                XR_FB_HAND_TRACKING_AIM_EXTENSION_NAME};
            XrInstanceCreateInfoAndroidKHR androidInfo{XR_TYPE_INSTANCE_CREATE_INFO_ANDROID_KHR};
            androidInfo.applicationVM = vm;
            androidInfo.applicationActivity = activity;

            XrInstanceCreateInfo info{XR_TYPE_INSTANCE_CREATE_INFO};
            info.next = &androidInfo;
            info.enabledExtensionCount = (uint32_t)exts.size();
            info.enabledExtensionNames = exts.data();
            std::snprintf(info.applicationInfo.applicationName, sizeof(info.applicationInfo.applicationName), "FastMediaSorter-Diag");
            info.applicationInfo.applicationVersion = 1;
            std::snprintf(info.applicationInfo.engineName, sizeof(info.applicationInfo.engineName), "FastMediaSorter");
            info.applicationInfo.engineVersion = 1;
            info.applicationInfo.apiVersion = XR_CURRENT_API_VERSION;
            LOGD("xrCreateInstance: enabling %zu extensions", exts.size());
            r = xrCreateInstance(&info, &g.instance);
            if (XR_FAILED(r))
            {
                LOGE("xrCreateInstance=%d", (int)r);
                return NativeResult::InstanceCreationFailed;
            }

            XrSystemGetInfo sysInfo{XR_TYPE_SYSTEM_GET_INFO};
            sysInfo.formFactor = XR_FORM_FACTOR_HEAD_MOUNTED_DISPLAY;
            r = xrGetSystem(g.instance, &sysInfo, &g.systemId);
            if (XR_FAILED(r) || g.systemId == XR_NULL_SYSTEM_ID)
            {
                LOGW("xrGetSystem=%d", (int)r);
                return NativeResult::SystemNotFound;
            }
            LOGD("instance ok, systemId=%llu", (unsigned long long)g.systemId);
            return NativeResult::Ok;
        }

        NativeResult createEgl()
        {
            g.eglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
            if (g.eglDisplay == EGL_NO_DISPLAY)
            {
                LOGE("eglGetDisplay failed");
                return NativeResult::SessionCreationFailed;
            }
            EGLint major = 0, minor = 0;
            if (!eglInitialize(g.eglDisplay, &major, &minor))
            {
                LOGE("eglInitialize failed");
                return NativeResult::SessionCreationFailed;
            }
            const EGLint cfgAttribs[] = {
                EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8,
                EGL_DEPTH_SIZE, 24, EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT_KHR,
                EGL_SURFACE_TYPE, EGL_PBUFFER_BIT | EGL_WINDOW_BIT, EGL_NONE};
            EGLint numConfigs = 0;
            if (!eglChooseConfig(g.eglDisplay, cfgAttribs, &g.eglConfig, 1, &numConfigs) || numConfigs == 0)
            {
                LOGE("eglChooseConfig failed");
                return NativeResult::SessionCreationFailed;
            }
            const EGLint ctxAttribs[] = {EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE};
            g.eglContext = eglCreateContext(g.eglDisplay, g.eglConfig, EGL_NO_CONTEXT, ctxAttribs);
            if (g.eglContext == EGL_NO_CONTEXT)
            {
                LOGE("eglCreateContext failed");
                return NativeResult::SessionCreationFailed;
            }
            LOGD("EGL ready: %d.%d", major, minor);
            return NativeResult::Ok;
        }

        NativeResult bindEglSurface()
        {
            if (!g.window)
            {
                const EGLint pbAttribs[] = {EGL_WIDTH, 16, EGL_HEIGHT, 16, EGL_NONE};
                g.eglSurface = eglCreatePbufferSurface(g.eglDisplay, g.eglConfig, pbAttribs);
            }
            else
            {
                g.eglSurface = eglCreateWindowSurface(g.eglDisplay, g.eglConfig, g.window, nullptr);
            }
            if (g.eglSurface == EGL_NO_SURFACE)
            {
                LOGE("eglCreate*Surface failed (0x%x)", eglGetError());
                return NativeResult::SessionCreationFailed;
            }
            if (!eglMakeCurrent(g.eglDisplay, g.eglSurface, g.eglSurface, g.eglContext))
            {
                LOGE("eglMakeCurrent failed");
                return NativeResult::SessionCreationFailed;
            }
            LOGD("EGL surface bound (window=%p)", (void *)g.window);
            return NativeResult::Ok;
        }

        NativeResult createSessionAndSpaces()
        {
            PFN_xrGetOpenGLESGraphicsRequirementsKHR pfnGetReq = nullptr;
            xrGetInstanceProcAddr(g.instance, "xrGetOpenGLESGraphicsRequirementsKHR", (PFN_xrVoidFunction *)&pfnGetReq);
            if (pfnGetReq)
            {
                XrGraphicsRequirementsOpenGLESKHR req{XR_TYPE_GRAPHICS_REQUIREMENTS_OPENGL_ES_KHR};
                pfnGetReq(g.instance, g.systemId, &req);
            }

            XrGraphicsBindingOpenGLESAndroidKHR binding{XR_TYPE_GRAPHICS_BINDING_OPENGL_ES_ANDROID_KHR};
            binding.display = g.eglDisplay;
            binding.config = g.eglConfig;
            binding.context = g.eglContext;

            XrSessionCreateInfo sci{XR_TYPE_SESSION_CREATE_INFO};
            sci.next = &binding;
            sci.systemId = g.systemId;
            XrResult r = xrCreateSession(g.instance, &sci, &g.session);
            if (XR_FAILED(r))
            {
                LOGE("xrCreateSession=%d", (int)r);
                return NativeResult::SessionCreationFailed;
            }

            XrReferenceSpaceCreateInfo rsci{XR_TYPE_REFERENCE_SPACE_CREATE_INFO};
            rsci.referenceSpaceType = XR_REFERENCE_SPACE_TYPE_LOCAL;
            rsci.poseInReferenceSpace.orientation.w = 1.0f;
            r = xrCreateReferenceSpace(g.session, &rsci, &g.localSpace);
            if (XR_FAILED(r))
            {
                LOGE("xrCreateReferenceSpace=%d", (int)r);
                return NativeResult::SessionCreationFailed;
            }

            uint32_t viewCount = 0;
            xrEnumerateViewConfigurationViews(g.instance, g.systemId, XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO, 0, &viewCount, nullptr);
            g.viewConfigs.assign(viewCount, {XR_TYPE_VIEW_CONFIGURATION_VIEW});
            xrEnumerateViewConfigurationViews(g.instance, g.systemId, XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO, viewCount, &viewCount, g.viewConfigs.data());
            LOGD("View config: %u views", viewCount);
            return NativeResult::Ok;
        }

        NativeResult createSwapchains()
        {
            uint32_t fmtCount = 0;
            xrEnumerateSwapchainFormats(g.session, 0, &fmtCount, nullptr);
            std::vector<int64_t> fmts(fmtCount);
            xrEnumerateSwapchainFormats(g.session, fmtCount, &fmtCount, fmts.data());
            int64_t chosen = GL_RGBA8;
            for (auto f : fmts)
            {
                if (f == GL_SRGB8_ALPHA8)
                {
                    chosen = GL_SRGB8_ALPHA8;
                    break;
                }
            }

            g.eyes.resize(g.viewConfigs.size());
            for (size_t i = 0; i < g.viewConfigs.size(); ++i)
            {
                const auto &vc = g.viewConfigs[i];
                EyeSwapchain &eye = g.eyes[i];
                eye.width = (int)vc.recommendedImageRectWidth;
                eye.height = (int)vc.recommendedImageRectHeight;

                XrSwapchainCreateInfo sci{XR_TYPE_SWAPCHAIN_CREATE_INFO};
                sci.usageFlags = XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT | XR_SWAPCHAIN_USAGE_SAMPLED_BIT;
                sci.format = chosen;
                sci.sampleCount = 1;
                sci.width = vc.recommendedImageRectWidth;
                sci.height = vc.recommendedImageRectHeight;
                sci.faceCount = 1;
                sci.arraySize = 1;
                sci.mipCount = 1;
                XrResult r = xrCreateSwapchain(g.session, &sci, &eye.handle);
                if (XR_FAILED(r))
                {
                    LOGE("xrCreateSwapchain[%zu]=%d", i, (int)r);
                    return NativeResult::SwapchainCreationFailed;
                }

                uint32_t imageCount = 0;
                xrEnumerateSwapchainImages(eye.handle, 0, &imageCount, nullptr);
                eye.images.assign(imageCount, {{XR_TYPE_SWAPCHAIN_IMAGE_OPENGL_ES_KHR}});
                xrEnumerateSwapchainImages(eye.handle, imageCount, &imageCount, (XrSwapchainImageBaseHeader *)eye.images.data());

                glGenRenderbuffers(1, &eye.depthRb);
                glBindRenderbuffer(GL_RENDERBUFFER, eye.depthRb);
                glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, eye.width, eye.height);
                glGenFramebuffers(1, &eye.fbo);
                LOGD("Eye %zu swapchain: %dx%d, images=%u, fmt=0x%llx", i, eye.width, eye.height, imageCount, (long long)chosen);
            }
            return NativeResult::Ok;
        }

        NativeResult createGlAssets()
        {
            GLuint vs = compileShader(GL_VERTEX_SHADER, kVertexShader);
            GLuint fs = compileShader(GL_FRAGMENT_SHADER, kFragmentShader);
            if (!vs || !fs)
                return NativeResult::SessionCreationFailed;
            g.program = linkProgram(vs, fs);
            GLuint videoFs = compileShader(GL_FRAGMENT_SHADER, kExternalVideoFragmentShader);
            if (videoFs)
            {
                g.videoProgram = linkProgram(vs, videoFs);
                glDeleteShader(videoFs);
            }
            glDeleteShader(vs);
            glDeleteShader(fs);
            if (!g.program)
                return NativeResult::SessionCreationFailed;
            if (!g.videoProgram)
                return NativeResult::SessionCreationFailed;
            g.locViewProj = glGetUniformLocation(g.program, "u_viewProj");
            g.locTex = glGetUniformLocation(g.program, "u_tex");
            g.locEye = glGetUniformLocation(g.program, "u_eyeIndex");
            g.locStereoLayout = glGetUniformLocation(g.program, "u_stereoLayout");
            g.locParallaxShift = glGetUniformLocation(g.program, "u_parallaxShift");
            g.locZoomUv = glGetUniformLocation(g.program, "u_zoomUv");
            g.videoLocViewProj = glGetUniformLocation(g.videoProgram, "u_viewProj");
            g.videoLocTex = glGetUniformLocation(g.videoProgram, "u_tex");
            g.videoLocEye = glGetUniformLocation(g.videoProgram, "u_eyeIndex");
            g.videoLocStereoLayout = glGetUniformLocation(g.videoProgram, "u_stereoLayout");
            g.videoLocParallaxShift = glGetUniformLocation(g.videoProgram, "u_parallaxShift");
            g.videoLocTexTransform = glGetUniformLocation(g.videoProgram, "u_texTransform");
            g.videoLocZoomUv = glGetUniformLocation(g.videoProgram, "u_zoomUv");

            std::vector<float> verts;
            std::vector<unsigned int> indices;

            buildSphereMesh(verts, indices);
            g.indexCount = (GLsizei)indices.size();
            glGenVertexArrays(1, &g.vao);
            glBindVertexArray(g.vao);
            glGenBuffers(1, &g.vbo);
            glBindBuffer(GL_ARRAY_BUFFER, g.vbo);
            glBufferData(GL_ARRAY_BUFFER, verts.size() * sizeof(float), verts.data(), GL_STATIC_DRAW);
            glGenBuffers(1, &g.ibo);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, g.ibo);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices.size() * sizeof(unsigned int), indices.data(), GL_STATIC_DRAW);
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 5 * sizeof(float), (void *)0);
            glEnableVertexAttribArray(1);
            glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, 5 * sizeof(float), (void *)(3 * sizeof(float)));
            glBindVertexArray(0);

            buildHemisphereMesh(verts, indices);
            g.hemiIndexCount = (GLsizei)indices.size();
            glGenVertexArrays(1, &g.hemiVao);
            glBindVertexArray(g.hemiVao);
            glGenBuffers(1, &g.hemiVbo);
            glBindBuffer(GL_ARRAY_BUFFER, g.hemiVbo);
            glBufferData(GL_ARRAY_BUFFER, verts.size() * sizeof(float), verts.data(), GL_STATIC_DRAW);
            glGenBuffers(1, &g.hemiIbo);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, g.hemiIbo);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices.size() * sizeof(unsigned int), indices.data(), GL_STATIC_DRAW);
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 5 * sizeof(float), (void *)0);
            glEnableVertexAttribArray(1);
            glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, 5 * sizeof(float), (void *)(3 * sizeof(float)));
            glBindVertexArray(0);

            buildQuadMesh(verts, indices);
            g.quadIndexCount = (GLsizei)indices.size();
            glGenVertexArrays(1, &g.quadVao);
            glBindVertexArray(g.quadVao);
            glGenBuffers(1, &g.quadVbo);
            glBindBuffer(GL_ARRAY_BUFFER, g.quadVbo);
            glBufferData(GL_ARRAY_BUFFER, verts.size() * sizeof(float), verts.data(), GL_STATIC_DRAW);
            glGenBuffers(1, &g.quadIbo);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, g.quadIbo);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices.size() * sizeof(unsigned int), indices.data(), GL_STATIC_DRAW);
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 5 * sizeof(float), (void *)0);
            glEnableVertexAttribArray(1);
            glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, 5 * sizeof(float), (void *)(3 * sizeof(float)));
            glBindVertexArray(0);

            glGenTextures(1, &g.texture);
            glBindTexture(GL_TEXTURE_2D, g.texture);
            configureStaticTextureFiltering();
            uint8_t pixel[4] = {64, 64, 64, 255};
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
            glGenerateMipmap(GL_TEXTURE_2D);

            glGenTextures(1, &g.hudTexture);
            glBindTexture(GL_TEXTURE_2D, g.hudTexture);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
            // S0961: the texture holds the grey placeholder again from this point on, so the
            // content-uploaded gate must disarm here, at the placeholder's birthplace.
            g.hudContentUploaded = false;

            // S0986: subtitle quad texture - same grey 1x1 placeholder + gate as the HUD.
            glGenTextures(1, &g.subtitleTexture);
            glBindTexture(GL_TEXTURE_2D, g.subtitleTexture);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
            g.subtitleContentUploaded = false;
            g.subtitleVisible = false;

            if (!createVideoSurfaceObjects())
                return NativeResult::SessionCreationFailed;

            return checkGl("createGlAssets") ? NativeResult::Ok : NativeResult::SessionCreationFailed;
        }

    } // namespace detail
} // namespace fms::xr
