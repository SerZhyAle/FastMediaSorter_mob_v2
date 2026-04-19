// OpenXR native bridge for FastMediaSorter VR flavor.
//
// Owns one XR instance/system/session/swapchain pair, drives the event+render loop,
// and callbacks into Kotlin per-eye so VrStereoRenderer can draw into the provided FBO.
//
// All OpenXR calls live here; Kotlin just owns lifecycle + renderer + per-frame callback.

#include <jni.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>

#include <EGL/egl.h>
#include <GLES3/gl3.h>

#include <openxr/openxr.h>
#include <openxr/openxr_platform.h>

#include <atomic>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>

#define LOG_TAG "OpenXrNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace
{

    constexpr XrViewConfigurationType kViewConfig = XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO;
    constexpr XrFormFactor kFormFactor = XR_FORM_FACTOR_HEAD_MOUNTED_DISPLAY;
    constexpr XrReferenceSpaceType kSpaceType = XR_REFERENCE_SPACE_TYPE_LOCAL;
    constexpr uint32_t kViewCount = 2;

    enum class LayerType : int32_t
    {
        Projection = 0,
        QuadCinema = 1,
        Equirect2 = 2,
        Cylinder = 3,
    };

    struct LayerConfig
    {
        LayerType type = LayerType::QuadCinema;
        float widthMeters = 4.0f;
        float heightMeters = 2.25f;
        float distanceMeters = 4.0f;
        float radiusMeters = 1.0f;
        float centralHorizontalAngle = 6.2831855f;
        float upperVerticalAngle = 1.5707964f;
        float lowerVerticalAngle = -1.5707964f;
    };

    struct SwapchainImage
    {
        uint32_t imageId = 0; // GL texture ID from XrSwapchainImageOpenGLESKHR
        uint32_t fbo = 0;     // FBO with imageId bound as color attachment 0
    };

    struct EyeSwapchain
    {
        XrSwapchain handle = XR_NULL_HANDLE;
        uint32_t width = 0;
        uint32_t height = 0;
        std::vector<SwapchainImage> images;
    };

    struct XrCtx
    {
        // JavaVM for thread-attachment when calling back into Kotlin.
        JavaVM *vm = nullptr;

        // Global ref to the Kotlin callback instance; invoked per eye.
        jobject callbackRef = nullptr;
        jmethodID onRenderEyeMethod = nullptr;

        XrInstance instance = XR_NULL_HANDLE;
        XrSystemId systemId = XR_NULL_SYSTEM_ID;
        XrSession session = XR_NULL_HANDLE;
        XrSpace appSpace = XR_NULL_HANDLE;

        XrSessionState sessionState = XR_SESSION_STATE_UNKNOWN;
        bool sessionRunning = false;
        std::atomic<bool> exitRequested{false};

        // Shared EGL (created by Kotlin GL thread before initialize()).
        EGLDisplay eglDisplay = EGL_NO_DISPLAY;
        EGLContext eglContext = EGL_NO_CONTEXT;
        EGLConfig eglConfig = nullptr;

        bool supportsEquirect2 = false;
        bool supportsCylinder = false;
        bool warnedMissingEquirect2 = false;
        bool warnedMissingCylinder = false;
        LayerConfig layerConfig{};

        EyeSwapchain eyes[kViewCount];
    };

    // Single process-wide context. The vr flavor only ever runs one XR session.
    XrCtx g_ctx{};
    std::mutex g_ctxMutex;

#define XR_CHECK(expr, tag)                                   \
    do                                                        \
    {                                                         \
        XrResult _r = (expr);                                 \
        if (XR_FAILED(_r))                                    \
        {                                                     \
            LOGE("%s failed: %d", tag, static_cast<int>(_r)); \
            return false;                                     \
        }                                                     \
    } while (0)

    bool enumerateAndCreateInstance(JNIEnv *env, jobject activity)
    {
        // Pull in Android loader init extension; without it the loader on Quest refuses to create an instance.
        PFN_xrInitializeLoaderKHR initLoader = nullptr;
        if (XR_SUCCEEDED(xrGetInstanceProcAddr(
                XR_NULL_HANDLE,
                "xrInitializeLoaderKHR",
                reinterpret_cast<PFN_xrVoidFunction *>(&initLoader))) &&
            initLoader)
        {
            XrLoaderInitInfoAndroidKHR loaderInit{XR_TYPE_LOADER_INIT_INFO_ANDROID_KHR};
            loaderInit.applicationVM = g_ctx.vm;
            loaderInit.applicationContext = activity;
            XrResult r = initLoader(reinterpret_cast<XrLoaderInitInfoBaseHeaderKHR *>(&loaderInit));
            if (XR_FAILED(r))
            {
                LOGE("xrInitializeLoaderKHR failed: %d", static_cast<int>(r));
                return false;
            }
        }
        else
        {
            LOGW("xrInitializeLoaderKHR not available; continuing best-effort");
        }

        // Enumerate runtime-supported extensions, pick the ones we need.
        uint32_t extCount = 0;
        XR_CHECK(xrEnumerateInstanceExtensionProperties(nullptr, 0, &extCount, nullptr),
                 "xrEnumerateInstanceExtensionProperties(count)");
        std::vector<XrExtensionProperties> props(extCount, {XR_TYPE_EXTENSION_PROPERTIES});
        XR_CHECK(xrEnumerateInstanceExtensionProperties(nullptr, extCount, &extCount, props.data()),
                 "xrEnumerateInstanceExtensionProperties(data)");

        auto hasExt = [&](const char *name)
        {
            for (const auto &p : props)
            {
                if (std::string(p.extensionName) == name)
                    return true;
            }
            return false;
        };

        std::vector<const char *> enabledExts;
        const char *kGraphicsExt = XR_KHR_OPENGL_ES_ENABLE_EXTENSION_NAME;
        const char *kAndroidCreateExt = XR_KHR_ANDROID_CREATE_INSTANCE_EXTENSION_NAME;
        const char *kEquirect2Ext = XR_KHR_COMPOSITION_LAYER_EQUIRECT2_EXTENSION_NAME;
        const char *kCylinderExt = XR_KHR_COMPOSITION_LAYER_CYLINDER_EXTENSION_NAME;
        if (!hasExt(kGraphicsExt))
        {
            LOGE("Required extension missing: %s", kGraphicsExt);
            return false;
        }
        enabledExts.push_back(kGraphicsExt);
        if (hasExt(kAndroidCreateExt))
        {
            enabledExts.push_back(kAndroidCreateExt);
        }
        g_ctx.supportsEquirect2 = hasExt(kEquirect2Ext);
        g_ctx.supportsCylinder = hasExt(kCylinderExt);
        if (g_ctx.supportsEquirect2)
        {
            enabledExts.push_back(kEquirect2Ext);
        }
        if (g_ctx.supportsCylinder)
        {
            enabledExts.push_back(kCylinderExt);
        }

        // Android create info must be in the chain so the runtime has VM + Activity.
        XrInstanceCreateInfoAndroidKHR androidCreate{XR_TYPE_INSTANCE_CREATE_INFO_ANDROID_KHR};
        androidCreate.applicationVM = g_ctx.vm;
        androidCreate.applicationActivity = activity;

        XrInstanceCreateInfo ci{XR_TYPE_INSTANCE_CREATE_INFO};
        ci.next = &androidCreate;
        ci.enabledExtensionCount = static_cast<uint32_t>(enabledExts.size());
        ci.enabledExtensionNames = enabledExts.data();
        ci.applicationInfo.apiVersion = XR_CURRENT_API_VERSION;
        std::strncpy(ci.applicationInfo.applicationName,
                     "FastMediaSorter-VR",
                     XR_MAX_APPLICATION_NAME_SIZE - 1);
        std::strncpy(ci.applicationInfo.engineName,
                     "FMS-OpenXR",
                     XR_MAX_ENGINE_NAME_SIZE - 1);

        XR_CHECK(xrCreateInstance(&ci, &g_ctx.instance), "xrCreateInstance");
        LOGI("XR instance created (extensions=%zu)", enabledExts.size());
        return true;
    }

    bool createSessionAndSwapchains()
    {
        // Get HMD system.
        XrSystemGetInfo sysInfo{XR_TYPE_SYSTEM_GET_INFO};
        sysInfo.formFactor = kFormFactor;
        XR_CHECK(xrGetSystem(g_ctx.instance, &sysInfo, &g_ctx.systemId), "xrGetSystem");

        // GL ES graphics binding — shares the Kotlin-side EGL context so the swapchain
        // GL textures live in the same context our renderer draws with.
        XrGraphicsBindingOpenGLESAndroidKHR gfx{XR_TYPE_GRAPHICS_BINDING_OPENGL_ES_ANDROID_KHR};
        gfx.display = g_ctx.eglDisplay;
        gfx.config = g_ctx.eglConfig;
        gfx.context = g_ctx.eglContext;

        XrSessionCreateInfo sci{XR_TYPE_SESSION_CREATE_INFO};
        sci.next = &gfx;
        sci.systemId = g_ctx.systemId;
        XR_CHECK(xrCreateSession(g_ctx.instance, &sci, &g_ctx.session), "xrCreateSession");

        // LOCAL reference space — anchored at session start; enough for seated viewing.
        XrReferenceSpaceCreateInfo rsci{XR_TYPE_REFERENCE_SPACE_CREATE_INFO};
        rsci.referenceSpaceType = kSpaceType;
        rsci.poseInReferenceSpace.orientation.w = 1.0f;
        XR_CHECK(xrCreateReferenceSpace(g_ctx.session, &rsci, &g_ctx.appSpace),
                 "xrCreateReferenceSpace");

        // View configuration views — gives us recommended swapchain dimensions per eye.
        uint32_t viewCount = 0;
        XR_CHECK(xrEnumerateViewConfigurationViews(
                     g_ctx.instance, g_ctx.systemId, kViewConfig, 0, &viewCount, nullptr),
                 "xrEnumerateViewConfigurationViews(count)");
        if (viewCount != kViewCount)
        {
            LOGE("Expected %u views, runtime reports %u", kViewCount, viewCount);
            return false;
        }
        std::vector<XrViewConfigurationView> views(viewCount, {XR_TYPE_VIEW_CONFIGURATION_VIEW});
        XR_CHECK(xrEnumerateViewConfigurationViews(
                     g_ctx.instance, g_ctx.systemId, kViewConfig, viewCount, &viewCount, views.data()),
                 "xrEnumerateViewConfigurationViews(data)");

        // Create one swapchain per eye.
        for (uint32_t eye = 0; eye < kViewCount; ++eye)
        {
            const auto &v = views[eye];

            XrSwapchainCreateInfo sc{XR_TYPE_SWAPCHAIN_CREATE_INFO};
            sc.usageFlags = XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT | XR_SWAPCHAIN_USAGE_SAMPLED_BIT;
            sc.format = GL_SRGB8_ALPHA8;
            sc.sampleCount = v.recommendedSwapchainSampleCount;
            sc.width = v.recommendedImageRectWidth;
            sc.height = v.recommendedImageRectHeight;
            sc.faceCount = 1;
            sc.arraySize = 1;
            sc.mipCount = 1;

            XR_CHECK(xrCreateSwapchain(g_ctx.session, &sc, &g_ctx.eyes[eye].handle), "xrCreateSwapchain");
            g_ctx.eyes[eye].width = sc.width;
            g_ctx.eyes[eye].height = sc.height;

            uint32_t imgCount = 0;
            XR_CHECK(xrEnumerateSwapchainImages(g_ctx.eyes[eye].handle, 0, &imgCount, nullptr),
                     "xrEnumerateSwapchainImages(count)");
            std::vector<XrSwapchainImageOpenGLESKHR> xrImages(
                imgCount, {XR_TYPE_SWAPCHAIN_IMAGE_OPENGL_ES_KHR});
            XR_CHECK(xrEnumerateSwapchainImages(
                         g_ctx.eyes[eye].handle,
                         imgCount,
                         &imgCount,
                         reinterpret_cast<XrSwapchainImageBaseHeader *>(xrImages.data())),
                     "xrEnumerateSwapchainImages(data)");

            g_ctx.eyes[eye].images.resize(imgCount);
            for (uint32_t i = 0; i < imgCount; ++i)
            {
                g_ctx.eyes[eye].images[i].imageId = xrImages[i].image;
                // Build a matching FBO so the Kotlin renderer can bind by ID.
                GLuint fbo = 0;
                glGenFramebuffers(1, &fbo);
                glBindFramebuffer(GL_FRAMEBUFFER, fbo);
                glFramebufferTexture2D(GL_FRAMEBUFFER,
                                       GL_COLOR_ATTACHMENT0,
                                       GL_TEXTURE_2D,
                                       xrImages[i].image,
                                       0);
                GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
                if (status != GL_FRAMEBUFFER_COMPLETE)
                {
                    LOGE("FBO incomplete eye=%u idx=%u status=0x%x", eye, i, status);
                }
                g_ctx.eyes[eye].images[i].fbo = fbo;
            }
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            LOGI("Eye %u swapchain: %ux%u, %u images", eye, sc.width, sc.height, imgCount);
        }

        return true;
    }

    void handleSessionStateChange(XrEventDataSessionStateChanged *e)
    {
        g_ctx.sessionState = e->state;
        LOGI("Session state -> %d", static_cast<int>(e->state));

        switch (e->state)
        {
        case XR_SESSION_STATE_READY:
        {
            XrSessionBeginInfo bi{XR_TYPE_SESSION_BEGIN_INFO};
            bi.primaryViewConfigurationType = kViewConfig;
            XrResult r = xrBeginSession(g_ctx.session, &bi);
            if (XR_SUCCEEDED(r))
                g_ctx.sessionRunning = true;
            break;
        }
        case XR_SESSION_STATE_STOPPING:
        {
            xrEndSession(g_ctx.session);
            g_ctx.sessionRunning = false;
            break;
        }
        case XR_SESSION_STATE_EXITING:
        case XR_SESSION_STATE_LOSS_PENDING:
            g_ctx.exitRequested = true;
            break;
        default:
            break;
        }
    }

    bool pollEvents()
    {
        XrEventDataBuffer evt{XR_TYPE_EVENT_DATA_BUFFER};
        while (xrPollEvent(g_ctx.instance, &evt) == XR_SUCCESS)
        {
            switch (evt.type)
            {
            case XR_TYPE_EVENT_DATA_SESSION_STATE_CHANGED:
                handleSessionStateChange(
                    reinterpret_cast<XrEventDataSessionStateChanged *>(&evt));
                break;
            case XR_TYPE_EVENT_DATA_INSTANCE_LOSS_PENDING:
                g_ctx.exitRequested = true;
                return false;
            default:
                break;
            }
            evt = {XR_TYPE_EVENT_DATA_BUFFER};
        }
        return !g_ctx.exitRequested.load();
    }

    void invokeRenderCallback(JNIEnv *env, int eye, int fbo, int width, int height)
    {
        if (!g_ctx.callbackRef || !g_ctx.onRenderEyeMethod)
            return;
        env->CallVoidMethod(g_ctx.callbackRef, g_ctx.onRenderEyeMethod,
                            static_cast<jint>(eye),
                            static_cast<jint>(fbo),
                            static_cast<jint>(width),
                            static_cast<jint>(height));
        if (env->ExceptionCheck())
        {
            env->ExceptionDescribe();
            env->ExceptionClear();
        }
    }

    XrSwapchainSubImage buildFullFrameSubImage(const EyeSwapchain &chain)
    {
        XrSwapchainSubImage subImage{};
        subImage.swapchain = chain.handle;
        subImage.imageRect = {
            {0, 0},
            {static_cast<int32_t>(chain.width), static_cast<int32_t>(chain.height)}};
        subImage.imageArrayIndex = 0;
        return subImage;
    }

    XrEyeVisibility eyeVisibilityForIndex(uint32_t eye)
    {
        return eye == 0 ? XR_EYE_VISIBILITY_LEFT : XR_EYE_VISIBILITY_RIGHT;
    }

    void renderFrame(JNIEnv *env)
    {
        XrFrameWaitInfo waitInfo{XR_TYPE_FRAME_WAIT_INFO};
        XrFrameState frameState{XR_TYPE_FRAME_STATE};
        if (XR_FAILED(xrWaitFrame(g_ctx.session, &waitInfo, &frameState)))
            return;

        XrFrameBeginInfo beginInfo{XR_TYPE_FRAME_BEGIN_INFO};
        xrBeginFrame(g_ctx.session, &beginInfo);

        const LayerConfig layerConfig = g_ctx.layerConfig;
        std::vector<XrCompositionLayerProjectionView> projViews(kViewCount);
        bool projectionViewsValid = (layerConfig.type != LayerType::Projection);
        bool frameValid = false;

        if (frameState.shouldRender == XR_TRUE)
        {
            uint32_t viewCount = 0;
            std::vector<XrView> views(kViewCount, {XR_TYPE_VIEW});
            if (layerConfig.type == LayerType::Projection)
            {
                XrViewState viewState{XR_TYPE_VIEW_STATE};
                XrViewLocateInfo locate{XR_TYPE_VIEW_LOCATE_INFO};
                locate.viewConfigurationType = kViewConfig;
                locate.displayTime = frameState.predictedDisplayTime;
                locate.space = g_ctx.appSpace;
                projectionViewsValid =
                    XR_SUCCEEDED(xrLocateViews(g_ctx.session, &locate, &viewState,
                                               kViewCount, &viewCount, views.data())) &&
                    (viewState.viewStateFlags & XR_VIEW_STATE_POSITION_VALID_BIT) &&
                    (viewState.viewStateFlags & XR_VIEW_STATE_ORIENTATION_VALID_BIT);
            }

            if (projectionViewsValid)
            {
                uint32_t renderedEyes = 0;
                for (uint32_t eye = 0; eye < kViewCount; ++eye)
                {
                    auto &chain = g_ctx.eyes[eye];

                    XrSwapchainImageAcquireInfo acq{XR_TYPE_SWAPCHAIN_IMAGE_ACQUIRE_INFO};
                    uint32_t imageIdx = 0;
                    if (XR_FAILED(xrAcquireSwapchainImage(chain.handle, &acq, &imageIdx)))
                        break;

                    XrSwapchainImageWaitInfo wait{XR_TYPE_SWAPCHAIN_IMAGE_WAIT_INFO};
                    wait.timeout = XR_INFINITE_DURATION;
                    xrWaitSwapchainImage(chain.handle, &wait);

                    glBindFramebuffer(GL_FRAMEBUFFER, chain.images[imageIdx].fbo);
                    glViewport(0, 0, static_cast<GLsizei>(chain.width),
                               static_cast<GLsizei>(chain.height));
                    invokeRenderCallback(env, static_cast<int>(eye),
                                         static_cast<int>(chain.images[imageIdx].fbo),
                                         static_cast<int>(chain.width),
                                         static_cast<int>(chain.height));

                    XrSwapchainImageReleaseInfo rel{XR_TYPE_SWAPCHAIN_IMAGE_RELEASE_INFO};
                    xrReleaseSwapchainImage(chain.handle, &rel);

                    renderedEyes++;

                    if (layerConfig.type == LayerType::Projection)
                    {
                        projViews[eye] = {XR_TYPE_COMPOSITION_LAYER_PROJECTION_VIEW};
                        projViews[eye].pose = views[eye].pose;
                        projViews[eye].fov = views[eye].fov;
                        projViews[eye].subImage = buildFullFrameSubImage(chain);
                    }
                }
                frameValid = (renderedEyes == kViewCount);
            }
        }

        XrCompositionLayerProjection projectionLayer{XR_TYPE_COMPOSITION_LAYER_PROJECTION};
        std::vector<XrCompositionLayerQuad> quadLayers;
        std::vector<XrCompositionLayerEquirect2KHR> equirectLayers;
        std::vector<XrCompositionLayerCylinderKHR> cylinderLayers;
        std::vector<const XrCompositionLayerBaseHeader *> layers;
        if (frameValid)
        {
            if (layerConfig.type == LayerType::Projection)
            {
                projectionLayer.space = g_ctx.appSpace;
                projectionLayer.viewCount = kViewCount;
                projectionLayer.views = projViews.data();
                layers.push_back(reinterpret_cast<const XrCompositionLayerBaseHeader *>(&projectionLayer));
            }
            else if (layerConfig.type == LayerType::QuadCinema)
            {
                quadLayers.resize(kViewCount);
                for (uint32_t eye = 0; eye < kViewCount; ++eye)
                {
                    quadLayers[eye] = {XR_TYPE_COMPOSITION_LAYER_QUAD};
                    quadLayers[eye].space = g_ctx.appSpace;
                    quadLayers[eye].eyeVisibility = eyeVisibilityForIndex(eye);
                    quadLayers[eye].subImage = buildFullFrameSubImage(g_ctx.eyes[eye]);
                    quadLayers[eye].pose.orientation.w = 1.0f;
                    quadLayers[eye].pose.position.z = -layerConfig.distanceMeters;
                    quadLayers[eye].size = {layerConfig.widthMeters, layerConfig.heightMeters};
                }
                for (auto &layer : quadLayers)
                {
                    layers.push_back(reinterpret_cast<const XrCompositionLayerBaseHeader *>(&layer));
                }
            }
            else if (layerConfig.type == LayerType::Equirect2 && g_ctx.supportsEquirect2)
            {
                equirectLayers.resize(kViewCount);
                for (uint32_t eye = 0; eye < kViewCount; ++eye)
                {
                    equirectLayers[eye] = {XR_TYPE_COMPOSITION_LAYER_EQUIRECT2_KHR};
                    equirectLayers[eye].space = g_ctx.appSpace;
                    equirectLayers[eye].eyeVisibility = eyeVisibilityForIndex(eye);
                    equirectLayers[eye].subImage = buildFullFrameSubImage(g_ctx.eyes[eye]);
                    equirectLayers[eye].pose.orientation.w = 1.0f;
                    equirectLayers[eye].radius = layerConfig.radiusMeters;
                    equirectLayers[eye].centralHorizontalAngle = layerConfig.centralHorizontalAngle;
                    equirectLayers[eye].upperVerticalAngle = layerConfig.upperVerticalAngle;
                    equirectLayers[eye].lowerVerticalAngle = layerConfig.lowerVerticalAngle;
                }
                for (auto &layer : equirectLayers)
                {
                    layers.push_back(reinterpret_cast<const XrCompositionLayerBaseHeader *>(&layer));
                }
            }
            else if (layerConfig.type == LayerType::Cylinder && g_ctx.supportsCylinder)
            {
                const float aspectRatio = layerConfig.heightMeters > 0.0f
                                              ? (layerConfig.widthMeters / layerConfig.heightMeters)
                                              : 1.0f;
                cylinderLayers.resize(kViewCount);
                for (uint32_t eye = 0; eye < kViewCount; ++eye)
                {
                    cylinderLayers[eye] = {XR_TYPE_COMPOSITION_LAYER_CYLINDER_KHR};
                    cylinderLayers[eye].space = g_ctx.appSpace;
                    cylinderLayers[eye].eyeVisibility = eyeVisibilityForIndex(eye);
                    cylinderLayers[eye].subImage = buildFullFrameSubImage(g_ctx.eyes[eye]);
                    cylinderLayers[eye].pose.orientation.w = 1.0f;
                    cylinderLayers[eye].pose.position.z = -layerConfig.distanceMeters;
                    cylinderLayers[eye].radius = layerConfig.radiusMeters;
                    cylinderLayers[eye].centralAngle = layerConfig.centralHorizontalAngle;
                    cylinderLayers[eye].aspectRatio = aspectRatio;
                }
                for (auto &layer : cylinderLayers)
                {
                    layers.push_back(reinterpret_cast<const XrCompositionLayerBaseHeader *>(&layer));
                }
            }
            else
            {
                if (layerConfig.type == LayerType::Equirect2 && !g_ctx.warnedMissingEquirect2)
                {
                    LOGW("Equirect2 layer requested but XR_KHR_composition_layer_equirect2 is unavailable; falling back to cinema quad");
                    g_ctx.warnedMissingEquirect2 = true;
                }
                if (layerConfig.type == LayerType::Cylinder && !g_ctx.warnedMissingCylinder)
                {
                    LOGW("Cylinder layer requested but XR_KHR_composition_layer_cylinder is unavailable; falling back to cinema quad");
                    g_ctx.warnedMissingCylinder = true;
                }

                quadLayers.resize(kViewCount);
                for (uint32_t eye = 0; eye < kViewCount; ++eye)
                {
                    quadLayers[eye] = {XR_TYPE_COMPOSITION_LAYER_QUAD};
                    quadLayers[eye].space = g_ctx.appSpace;
                    quadLayers[eye].eyeVisibility = eyeVisibilityForIndex(eye);
                    quadLayers[eye].subImage = buildFullFrameSubImage(g_ctx.eyes[eye]);
                    quadLayers[eye].pose.orientation.w = 1.0f;
                    quadLayers[eye].pose.position.z = -4.0f;
                    quadLayers[eye].size = {4.0f, 2.25f};
                    layers.push_back(reinterpret_cast<const XrCompositionLayerBaseHeader *>(&quadLayers[eye]));
                }
            }
        }

        XrFrameEndInfo endInfo{XR_TYPE_FRAME_END_INFO};
        endInfo.displayTime = frameState.predictedDisplayTime;
        endInfo.environmentBlendMode = XR_ENVIRONMENT_BLEND_MODE_OPAQUE;
        endInfo.layerCount = static_cast<uint32_t>(layers.size());
        endInfo.layers = layers.data();
        xrEndFrame(g_ctx.session, &endInfo);
    }

    void releaseCallback(JNIEnv *env)
    {
        if (g_ctx.callbackRef)
        {
            env->DeleteGlobalRef(g_ctx.callbackRef);
            g_ctx.callbackRef = nullptr;
        }
        g_ctx.onRenderEyeMethod = nullptr;
    }

    void destroyAll()
    {
        for (auto &eye : g_ctx.eyes)
        {
            for (auto &img : eye.images)
            {
                if (img.fbo)
                {
                    GLuint f = img.fbo;
                    glDeleteFramebuffers(1, &f);
                    img.fbo = 0;
                }
            }
            eye.images.clear();
            if (eye.handle)
            {
                xrDestroySwapchain(eye.handle);
                eye.handle = XR_NULL_HANDLE;
            }
        }
        if (g_ctx.appSpace != XR_NULL_HANDLE)
        {
            xrDestroySpace(g_ctx.appSpace);
            g_ctx.appSpace = XR_NULL_HANDLE;
        }
        if (g_ctx.session != XR_NULL_HANDLE)
        {
            xrDestroySession(g_ctx.session);
            g_ctx.session = XR_NULL_HANDLE;
        }
        if (g_ctx.instance != XR_NULL_HANDLE)
        {
            xrDestroyInstance(g_ctx.instance);
            g_ctx.instance = XR_NULL_HANDLE;
        }
        g_ctx.supportsEquirect2 = false;
        g_ctx.supportsCylinder = false;
        g_ctx.warnedMissingEquirect2 = false;
        g_ctx.warnedMissingCylinder = false;
        g_ctx.layerConfig = LayerConfig{};
        g_ctx.sessionRunning = false;
        g_ctx.exitRequested = false;
        g_ctx.sessionState = XR_SESSION_STATE_UNKNOWN;
    }

} // namespace

// ─── JNI entry points ─────────────────────────────────────────────────────────

extern "C" JNIEXPORT jboolean JNICALL
Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_nativeInitialize(
    JNIEnv *env, jclass, jobject activity, jobject callback)
{
    std::lock_guard<std::mutex> lock(g_ctxMutex);

    env->GetJavaVM(&g_ctx.vm);

    // Snapshot the EGL context the Kotlin GL thread already set up.
    g_ctx.eglDisplay = eglGetCurrentDisplay();
    g_ctx.eglContext = eglGetCurrentContext();
    if (g_ctx.eglDisplay == EGL_NO_DISPLAY || g_ctx.eglContext == EGL_NO_CONTEXT)
    {
        LOGE("No current EGL context — nativeInitialize must be called from GL thread");
        return JNI_FALSE;
    }
    EGLint cfgId = 0;
    eglQueryContext(g_ctx.eglDisplay, g_ctx.eglContext, EGL_CONFIG_ID, &cfgId);
    EGLint cfgAttr[] = {EGL_CONFIG_ID, cfgId, EGL_NONE};
    EGLint numCfg = 0;
    eglChooseConfig(g_ctx.eglDisplay, cfgAttr, &g_ctx.eglConfig, 1, &numCfg);

    // Cache render callback (Kotlin object with an `onRenderEye(int,int,int,int)` method).
    jclass cbCls = env->GetObjectClass(callback);
    g_ctx.onRenderEyeMethod = env->GetMethodID(cbCls, "onRenderEye", "(IIII)V");
    if (!g_ctx.onRenderEyeMethod)
    {
        LOGE("Callback missing onRenderEye(IIII)V");
        return JNI_FALSE;
    }
    g_ctx.callbackRef = env->NewGlobalRef(callback);

    if (!enumerateAndCreateInstance(env, activity))
    {
        releaseCallback(env);
        return JNI_FALSE;
    }
    if (!createSessionAndSwapchains())
    {
        destroyAll();
        releaseCallback(env);
        return JNI_FALSE;
    }
    LOGI("OpenXR initialized successfully");
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_nativeRunFrame(JNIEnv *env, jclass)
{
    std::lock_guard<std::mutex> lock(g_ctxMutex);
    if (g_ctx.instance == XR_NULL_HANDLE)
        return;
    if (!pollEvents())
        return;
    if (g_ctx.sessionRunning)
    {
        renderFrame(env);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_nativeConfigureLayer(
    JNIEnv *, jclass,
    jint layerType,
    jfloat widthMeters,
    jfloat heightMeters,
    jfloat distanceMeters,
    jfloat radiusMeters,
    jfloat centralHorizontalAngleRadians,
    jfloat upperVerticalAngleRadians,
    jfloat lowerVerticalAngleRadians)
{
    std::lock_guard<std::mutex> lock(g_ctxMutex);

    LayerConfig config{};
    switch (layerType)
    {
    case 1:
        config.type = LayerType::QuadCinema;
        break;
    case 2:
        config.type = LayerType::Equirect2;
        break;
    case 3:
        config.type = LayerType::Cylinder;
        break;
    case 0:
    default:
        config.type = LayerType::Projection;
        break;
    }

    config.widthMeters = widthMeters;
    config.heightMeters = heightMeters;
    config.distanceMeters = distanceMeters;
    config.radiusMeters = radiusMeters;
    config.centralHorizontalAngle = centralHorizontalAngleRadians;
    config.upperVerticalAngle = upperVerticalAngleRadians;
    config.lowerVerticalAngle = lowerVerticalAngleRadians;
    g_ctx.layerConfig = config;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_nativeShouldContinue(JNIEnv *, jclass)
{
    return g_ctx.exitRequested.load() ? JNI_FALSE : JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_nativeRequestExit(JNIEnv *, jclass)
{
    g_ctx.exitRequested = true;
}

extern "C" JNIEXPORT void JNICALL
Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_nativeRelease(JNIEnv *env, jclass)
{
    std::lock_guard<std::mutex> lock(g_ctxMutex);
    destroyAll();
    releaseCallback(env);
    LOGI("OpenXR released");
}

extern "C" JNIEXPORT jint JNICALL
Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_nativeGetEyeWidth(JNIEnv *, jclass, jint eye)
{
    if (eye < 0 || eye >= static_cast<jint>(kViewCount))
        return 0;
    return static_cast<jint>(g_ctx.eyes[eye].width);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_nativeGetEyeHeight(JNIEnv *, jclass, jint eye)
{
    if (eye < 0 || eye >= static_cast<jint>(kViewCount))
        return 0;
    return static_cast<jint>(g_ctx.eyes[eye].height);
}
