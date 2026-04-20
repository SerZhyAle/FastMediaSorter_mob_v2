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
#include <algorithm>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>

#define LOG_TAG "OpenXrNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// ─── Debug helpers ────────────────────────────────────────────────────────────

/** Human-readable XrSessionState name — used in every state log so grep is easy. */
static constexpr const char *xrSessionStateName(XrSessionState s)
{
    switch (s)
    {
    case XR_SESSION_STATE_UNKNOWN:
        return "UNKNOWN";
    case XR_SESSION_STATE_IDLE:
        return "IDLE";
    case XR_SESSION_STATE_READY:
        return "READY";
    case XR_SESSION_STATE_SYNCHRONIZED:
        return "SYNCHRONIZED";
    case XR_SESSION_STATE_VISIBLE:
        return "VISIBLE";
    case XR_SESSION_STATE_FOCUSED:
        return "FOCUSED";
    case XR_SESSION_STATE_STOPPING:
        return "STOPPING";
    case XR_SESSION_STATE_LOSS_PENDING:
        return "LOSS_PENDING";
    case XR_SESSION_STATE_EXITING:
        return "EXITING";
    default:
        return "?UNKNOWN_STATE";
    }
}

/** Human-readable XrStructureType name for event debug. */
static const char *xrEventTypeName(XrStructureType t)
{
    switch (t)
    {
    case XR_TYPE_EVENT_DATA_SESSION_STATE_CHANGED:
        return "SESSION_STATE_CHANGED";
    case XR_TYPE_EVENT_DATA_INSTANCE_LOSS_PENDING:
        return "INSTANCE_LOSS_PENDING";
    case XR_TYPE_EVENT_DATA_INTERACTION_PROFILE_CHANGED:
        return "INTERACTION_PROFILE_CHANGED";
    case XR_TYPE_EVENT_DATA_REFERENCE_SPACE_CHANGE_PENDING:
        return "REFERENCE_SPACE_CHANGE_PENDING";
    default:
        return "OTHER";
    }
}

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

        struct StereoSnapshot
        {
            std::atomic<bool> requested{false};
            // ready is atomic to allow the render thread to set it without holding
            // g_ctxMutex, while JNI polling reads it under the mutex. A release
            // store on the render side and acquire load on the JNI side guarantee
            // that all pixel writes are visible before ready is observed as true.
            std::atomic<bool> ready{false};
            uint32_t width = 0;
            uint32_t height = 0;
            std::vector<jint> eyePixels[kViewCount];
        } stereoSnapshot;
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
        LOGD("enumerateAndCreateInstance: entry  vm=%p activity=%p", g_ctx.vm, activity);

        // Pull in Android loader init extension; without it the loader on Quest refuses to create an instance.
        PFN_xrInitializeLoaderKHR initLoader = nullptr;
        XrResult loaderLookup = xrGetInstanceProcAddr(
            XR_NULL_HANDLE,
            "xrInitializeLoaderKHR",
            reinterpret_cast<PFN_xrVoidFunction *>(&initLoader));
        LOGD("xrGetInstanceProcAddr(xrInitializeLoaderKHR): result=%d ptr=%p",
             static_cast<int>(loaderLookup), reinterpret_cast<void *>(initLoader));

        if (XR_SUCCEEDED(loaderLookup) && initLoader)
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
            LOGI("xrInitializeLoaderKHR: OK");
        }
        else
        {
            LOGW("xrInitializeLoaderKHR not available (result=%d); continuing best-effort",
                 static_cast<int>(loaderLookup));
        }

        // Enumerate runtime-supported extensions, pick the ones we need.
        uint32_t extCount = 0;
        XR_CHECK(xrEnumerateInstanceExtensionProperties(nullptr, 0, &extCount, nullptr),
                 "xrEnumerateInstanceExtensionProperties(count)");
        std::vector<XrExtensionProperties> props(extCount, {XR_TYPE_EXTENSION_PROPERTIES});
        XR_CHECK(xrEnumerateInstanceExtensionProperties(nullptr, extCount, &extCount, props.data()),
                 "xrEnumerateInstanceExtensionProperties(data)");

        LOGI("Runtime extensions available: %u (key extensions checked below)", extCount);

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

        LOGI("Extension check: GraphicsES=%d AndroidCreate=%d Equirect2=%d Cylinder=%d",
             hasExt(kGraphicsExt),
             hasExt(kAndroidCreateExt),
             hasExt(kEquirect2Ext),
             hasExt(kCylinderExt));

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
        LOGI("Enabled extensions (%zu): GraphicsES Equirect2=%d Cylinder=%d",
             enabledExts.size(),
             static_cast<int>(g_ctx.supportsEquirect2),
             static_cast<int>(g_ctx.supportsCylinder));

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
        LOGD("xrCreateInstance: apiVersion=0x%llx app='%s' engine='%s'",
             static_cast<unsigned long long>(ci.applicationInfo.apiVersion),
             ci.applicationInfo.applicationName,
             ci.applicationInfo.engineName);

        XR_CHECK(xrCreateInstance(&ci, &g_ctx.instance), "xrCreateInstance");
        LOGI("XR instance created: handle=0x%llx  extensions=%zu  Equirect2=%d Cylinder=%d",
             static_cast<unsigned long long>(reinterpret_cast<uintptr_t>(g_ctx.instance)),
             enabledExts.size(),
             static_cast<int>(g_ctx.supportsEquirect2),
             static_cast<int>(g_ctx.supportsCylinder));
        return true;
    }

    bool createSessionAndSwapchains()
    {
        LOGD("createSessionAndSwapchains: entry  instance=0x%llx",
             static_cast<unsigned long long>(reinterpret_cast<uintptr_t>(g_ctx.instance)));

        // Get HMD system.
        XrSystemGetInfo sysInfo{XR_TYPE_SYSTEM_GET_INFO};
        sysInfo.formFactor = kFormFactor;
        XR_CHECK(xrGetSystem(g_ctx.instance, &sysInfo, &g_ctx.systemId), "xrGetSystem");
        LOGI("xrGetSystem: systemId=0x%llx",
             static_cast<unsigned long long>(g_ctx.systemId));

        // Log system properties so we know exactly what headset we're on.
        {
            XrSystemProperties sysProps{XR_TYPE_SYSTEM_PROPERTIES};
            if (XR_SUCCEEDED(xrGetSystemProperties(g_ctx.instance, g_ctx.systemId, &sysProps)))
            {
                LOGI("System: name='%s'  vendorId=%u",
                     sysProps.systemName, sysProps.vendorId);
                LOGI("  gfx maxSwapchainW=%u maxSwapchainH=%u maxLayers=%u",
                     sysProps.graphicsProperties.maxSwapchainImageWidth,
                     sysProps.graphicsProperties.maxSwapchainImageHeight,
                     sysProps.graphicsProperties.maxLayerCount);
                LOGI("  tracking: orientation=%d position=%d",
                     static_cast<int>(sysProps.trackingProperties.orientationTracking),
                     static_cast<int>(sysProps.trackingProperties.positionTracking));
            }
            else
            {
                LOGW("xrGetSystemProperties failed — continuing without system info");
            }
        }

        // MANDATORY per OpenXR spec §7.1: call xrGetGraphicsRequirementsOpenGLESKHR
        // BEFORE xrCreateSession. Skipping this causes XR_ERROR_GRAPHICS_REQUIREMENTS_CHECK_MISSING (-50).
        //
        // The PFN_ typedef may not exist in all header versions, so we define
        // the function-pointer type locally to stay portable across OpenXR SDK releases.
        {
            typedef XrResult(XRAPI_PTR * PFN_GetGfxReqsGLES)(
                XrInstance, XrSystemId, XrGraphicsRequirementsOpenGLESKHR *);

            PFN_GetGfxReqsGLES pfnGetGfxReqs = nullptr;
            XrResult lookupResult = xrGetInstanceProcAddr(
                g_ctx.instance,
                "xrGetGraphicsRequirementsOpenGLESKHR",
                reinterpret_cast<PFN_xrVoidFunction *>(&pfnGetGfxReqs));
            LOGD("xrGetInstanceProcAddr(xrGetGraphicsRequirementsOpenGLESKHR): result=%d ptr=%p",
                 static_cast<int>(lookupResult), static_cast<void *>(reinterpret_cast<void *>(pfnGetGfxReqs)));

            if (XR_FAILED(lookupResult) || pfnGetGfxReqs == nullptr)
            {
                LOGE("xrGetGraphicsRequirementsOpenGLESKHR function not found (result=%d) — cannot create session",
                     static_cast<int>(lookupResult));
                return false;
            }

            XrGraphicsRequirementsOpenGLESKHR gfxReqs{XR_TYPE_GRAPHICS_REQUIREMENTS_OPENGL_ES_KHR};
            XrResult reqResult = pfnGetGfxReqs(g_ctx.instance, g_ctx.systemId, &gfxReqs);
            if (XR_FAILED(reqResult))
            {
                LOGE("xrGetGraphicsRequirementsOpenGLESKHR failed: %d — cannot create session",
                     static_cast<int>(reqResult));
                return false;
            }
            LOGI("OpenGL ES version range: min=%u.%u  max=%u.%u",
                 XR_VERSION_MAJOR(gfxReqs.minApiVersionSupported),
                 XR_VERSION_MINOR(gfxReqs.minApiVersionSupported),
                 XR_VERSION_MAJOR(gfxReqs.maxApiVersionSupported),
                 XR_VERSION_MINOR(gfxReqs.maxApiVersionSupported));
        }

        // GL ES graphics binding — shares the Kotlin-side EGL context so the swapchain
        // GL textures live in the same context our renderer draws with.
        LOGD("EGL binding: display=%p config=%p context=%p",
             static_cast<void *>(g_ctx.eglDisplay),
             static_cast<void *>(g_ctx.eglConfig),
             static_cast<void *>(g_ctx.eglContext));

        XrGraphicsBindingOpenGLESAndroidKHR gfx{XR_TYPE_GRAPHICS_BINDING_OPENGL_ES_ANDROID_KHR};
        gfx.display = g_ctx.eglDisplay;
        gfx.config = g_ctx.eglConfig;
        gfx.context = g_ctx.eglContext;

        XrSessionCreateInfo sci{XR_TYPE_SESSION_CREATE_INFO};
        sci.next = &gfx;
        sci.systemId = g_ctx.systemId;
        LOGD("xrCreateSession: systemId=0x%llx  EGL display=%p context=%p",
             static_cast<unsigned long long>(g_ctx.systemId),
             static_cast<void *>(g_ctx.eglDisplay),
             static_cast<void *>(g_ctx.eglContext));

        XR_CHECK(xrCreateSession(g_ctx.instance, &sci, &g_ctx.session), "xrCreateSession");
        LOGI("xrCreateSession: SUCCESS  session=0x%llx",
             static_cast<unsigned long long>(reinterpret_cast<uintptr_t>(g_ctx.session)));

        // LOCAL reference space — anchored at session start; enough for seated viewing.
        XrReferenceSpaceCreateInfo rsci{XR_TYPE_REFERENCE_SPACE_CREATE_INFO};
        rsci.referenceSpaceType = kSpaceType;
        rsci.poseInReferenceSpace.orientation.w = 1.0f;
        XR_CHECK(xrCreateReferenceSpace(g_ctx.session, &rsci, &g_ctx.appSpace),
                 "xrCreateReferenceSpace");
        LOGI("xrCreateReferenceSpace: appSpace=0x%llx  type=LOCAL",
             static_cast<unsigned long long>(reinterpret_cast<uintptr_t>(g_ctx.appSpace)));

        // View configuration views — gives us recommended swapchain dimensions per eye.
        uint32_t viewCount = 0;
        XR_CHECK(xrEnumerateViewConfigurationViews(
                     g_ctx.instance, g_ctx.systemId, kViewConfig, 0, &viewCount, nullptr),
                 "xrEnumerateViewConfigurationViews(count)");
        LOGI("ViewConfigurationViews count=%u (expected %u)", viewCount, kViewCount);
        if (viewCount != kViewCount)
        {
            LOGE("Expected %u views, runtime reports %u", kViewCount, viewCount);
            return false;
        }
        std::vector<XrViewConfigurationView> views(viewCount, {XR_TYPE_VIEW_CONFIGURATION_VIEW});
        XR_CHECK(xrEnumerateViewConfigurationViews(
                     g_ctx.instance, g_ctx.systemId, kViewConfig, viewCount, &viewCount, views.data()),
                 "xrEnumerateViewConfigurationViews(data)");
        for (uint32_t i = 0; i < viewCount; ++i)
        {
            LOGI("  view[%u]: recommended=%ux%u  sampleCount=%u  max=%ux%u",
                 i,
                 views[i].recommendedImageRectWidth, views[i].recommendedImageRectHeight,
                 views[i].recommendedSwapchainSampleCount,
                 views[i].maxImageRectWidth, views[i].maxImageRectHeight);
        }

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
            LOGD("xrCreateSwapchain eye=%u  %ux%u  samples=%u  format=0x%x",
                 eye, sc.width, sc.height, sc.sampleCount, static_cast<unsigned>(sc.format));

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
                else
                {
                    LOGD("FBO ok eye=%u idx=%u fbo=%u texId=%u", eye, i, fbo, xrImages[i].image);
                }
                g_ctx.eyes[eye].images[i].fbo = fbo;
            }
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            LOGI("Eye %u swapchain: %ux%u, %u images  handle=0x%llx",
                 eye, sc.width, sc.height, imgCount,
                 static_cast<unsigned long long>(reinterpret_cast<uintptr_t>(g_ctx.eyes[eye].handle)));
        }

        LOGI("createSessionAndSwapchains: complete — session ready for runtime events");
        return true;
    }

    void handleSessionStateChange(XrEventDataSessionStateChanged *e)
    {
        const XrSessionState prevState = g_ctx.sessionState;
        g_ctx.sessionState = e->state;
        LOGI("Session state: %s(%d) -> %s(%d)",
             xrSessionStateName(prevState), static_cast<int>(prevState),
             xrSessionStateName(e->state), static_cast<int>(e->state));

        switch (e->state)
        {
        case XR_SESSION_STATE_READY:
        {
            XrSessionBeginInfo bi{XR_TYPE_SESSION_BEGIN_INFO};
            bi.primaryViewConfigurationType = kViewConfig;
            XrResult r = xrBeginSession(g_ctx.session, &bi);
            if (XR_SUCCEEDED(r))
            {
                g_ctx.sessionRunning = true;
                LOGI("xrBeginSession: OK — render loop will start next frame");
            }
            else
            {
                LOGE("xrBeginSession FAILED: %d — session will not render", static_cast<int>(r));
            }
            break;
        }
        case XR_SESSION_STATE_STOPPING:
        {
            LOGI("xrEndSession: calling — sessionRunning was %d", static_cast<int>(g_ctx.sessionRunning));
            XrResult r = xrEndSession(g_ctx.session);
            if (XR_FAILED(r))
            {
                LOGE("xrEndSession FAILED: %d", static_cast<int>(r));
            }
            g_ctx.sessionRunning = false;
            break;
        }
        case XR_SESSION_STATE_EXITING:
        case XR_SESSION_STATE_LOSS_PENDING:
            LOGI("Session %s — requesting exit", xrSessionStateName(e->state));
            g_ctx.exitRequested = true;
            break;
        default:
            LOGD("Session state %s — no action needed", xrSessionStateName(e->state));
            break;
        }
    }

    bool pollEvents()
    {
        XrEventDataBuffer evt{XR_TYPE_EVENT_DATA_BUFFER};
        while (xrPollEvent(g_ctx.instance, &evt) == XR_SUCCESS)
        {
            LOGD("pollEvents: received event type=%d (%s)",
                 static_cast<int>(evt.type), xrEventTypeName(evt.type));
            switch (evt.type)
            {
            case XR_TYPE_EVENT_DATA_SESSION_STATE_CHANGED:
                handleSessionStateChange(
                    reinterpret_cast<XrEventDataSessionStateChanged *>(&evt));
                break;
            case XR_TYPE_EVENT_DATA_INSTANCE_LOSS_PENDING:
                LOGW("pollEvents: INSTANCE_LOSS_PENDING — stopping render loop");
                g_ctx.exitRequested = true;
                return false;
            default:
                LOGD("pollEvents: unhandled event type=%d", static_cast<int>(evt.type));
                break;
            }
            evt = {XR_TYPE_EVENT_DATA_BUFFER};
        }
        if (g_ctx.exitRequested.load())
        {
            LOGI("pollEvents: exitRequested — render loop will terminate");
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
        // Static frame counter — never wraps in practice but uint64 just in case.
        static uint64_t s_frameCount = 0;
        ++s_frameCount;

        XrFrameWaitInfo waitInfo{XR_TYPE_FRAME_WAIT_INFO};
        XrFrameState frameState{XR_TYPE_FRAME_STATE};
        XrResult waitResult = xrWaitFrame(g_ctx.session, &waitInfo, &frameState);
        if (XR_FAILED(waitResult))
        {
            LOGE("xrWaitFrame FAILED: %d (frame #%llu)",
                 static_cast<int>(waitResult),
                 static_cast<unsigned long long>(s_frameCount));
            return;
        }

        XrFrameBeginInfo beginInfo{XR_TYPE_FRAME_BEGIN_INFO};
        XrResult beginResult = xrBeginFrame(g_ctx.session, &beginInfo);
        if (XR_FAILED(beginResult))
        {
            LOGE("xrBeginFrame FAILED: %d (frame #%llu)",
                 static_cast<int>(beginResult),
                 static_cast<unsigned long long>(s_frameCount));
            return;
        }

        // Throttled per-frame diagnostics — log every 300 frames to keep logcat readable.
        if (s_frameCount % 300 == 1)
        {
            LOGI("renderFrame #%llu: shouldRender=%d  state=%s  layerType=%d  "
                 "exitReq=%d  sessionRunning=%d",
                 static_cast<unsigned long long>(s_frameCount),
                 static_cast<int>(frameState.shouldRender),
                 xrSessionStateName(g_ctx.sessionState),
                 static_cast<int>(g_ctx.layerConfig.type),
                 static_cast<int>(g_ctx.exitRequested.load()),
                 static_cast<int>(g_ctx.sessionRunning));
        }

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

                    if (g_ctx.stereoSnapshot.requested.load())
                    {
                        const uint32_t pixelCount = chain.width * chain.height;
                        std::vector<uint8_t> rgba(pixelCount * 4u);
                        glReadPixels(0,
                                     0,
                                     static_cast<GLsizei>(chain.width),
                                     static_cast<GLsizei>(chain.height),
                                     GL_RGBA,
                                     GL_UNSIGNED_BYTE,
                                     rgba.data());

                        auto &dst = g_ctx.stereoSnapshot.eyePixels[eye];
                        dst.resize(pixelCount);
                        for (uint32_t y = 0; y < chain.height; ++y)
                        {
                            const uint32_t srcRow = chain.height - 1u - y;
                            const uint32_t dstRowOffset = y * chain.width;
                            const uint32_t srcRowOffset = srcRow * chain.width * 4u;
                            for (uint32_t x = 0; x < chain.width; ++x)
                            {
                                const uint32_t srcIndex = srcRowOffset + (x * 4u);
                                const uint8_t r = rgba[srcIndex + 0u];
                                const uint8_t g = rgba[srcIndex + 1u];
                                const uint8_t b = rgba[srcIndex + 2u];
                                const uint8_t a = rgba[srcIndex + 3u];
                                dst[dstRowOffset + x] =
                                    (static_cast<jint>(a) << 24) |
                                    (static_cast<jint>(r) << 16) |
                                    (static_cast<jint>(g) << 8) |
                                    static_cast<jint>(b);
                            }
                        }
                        g_ctx.stereoSnapshot.width = chain.width;
                        g_ctx.stereoSnapshot.height = chain.height;
                    }

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
                if (frameValid && g_ctx.stereoSnapshot.requested.load())
                {
                    // Release store: guarantees all pixel writes above are visible
                    // to any thread that observes ready via an acquire load.
                    g_ctx.stereoSnapshot.ready.store(true, std::memory_order_release);
                    g_ctx.stereoSnapshot.requested.store(false, std::memory_order_relaxed);
                }
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

        // Log every 300 frames: how many layers we're submitting and whether frame was valid.
        if (s_frameCount % 300 == 1)
        {
            LOGI("xrEndFrame #%llu: layerCount=%u frameValid=%d",
                 static_cast<unsigned long long>(s_frameCount),
                 endInfo.layerCount,
                 static_cast<int>(frameValid));
        }

        XrResult endResult = xrEndFrame(g_ctx.session, &endInfo);
        if (XR_FAILED(endResult))
        {
            LOGE("xrEndFrame FAILED: %d  layerCount=%u  frame=#%llu",
                 static_cast<int>(endResult),
                 endInfo.layerCount,
                 static_cast<unsigned long long>(s_frameCount));
        }
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
        g_ctx.stereoSnapshot.requested.store(false);
        g_ctx.stereoSnapshot.ready.store(false);
        g_ctx.stereoSnapshot.width = 0;
        g_ctx.stereoSnapshot.height = 0;
        for (auto &eyePixels : g_ctx.stereoSnapshot.eyePixels)
        {
            eyePixels.clear();
        }
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
    LOGI("nativeInitialize: ENTRY — locking g_ctxMutex");
    std::lock_guard<std::mutex> lock(g_ctxMutex);

    env->GetJavaVM(&g_ctx.vm);
    LOGD("nativeInitialize: JavaVM=%p", static_cast<void *>(g_ctx.vm));

    // Snapshot the EGL context the Kotlin GL thread already set up.
    g_ctx.eglDisplay = eglGetCurrentDisplay();
    g_ctx.eglContext = eglGetCurrentContext();
    LOGI("nativeInitialize: EGL display=%p context=%p",
         static_cast<void *>(g_ctx.eglDisplay),
         static_cast<void *>(g_ctx.eglContext));

    if (g_ctx.eglDisplay == EGL_NO_DISPLAY || g_ctx.eglContext == EGL_NO_CONTEXT)
    {
        LOGE("No current EGL context — nativeInitialize must be called from GL thread");
        return JNI_FALSE;
    }

    EGLint cfgId = 0;
    eglQueryContext(g_ctx.eglDisplay, g_ctx.eglContext, EGL_CONFIG_ID, &cfgId);
    LOGD("nativeInitialize: EGL_CONFIG_ID=%d", cfgId);
    EGLint cfgAttr[] = {EGL_CONFIG_ID, cfgId, EGL_NONE};
    EGLint numCfg = 0;
    eglChooseConfig(g_ctx.eglDisplay, cfgAttr, &g_ctx.eglConfig, 1, &numCfg);
    LOGD("nativeInitialize: eglChooseConfig numCfg=%d config=%p",
         numCfg, static_cast<void *>(g_ctx.eglConfig));
    if (numCfg == 0 || g_ctx.eglConfig == nullptr)
    {
        LOGW("nativeInitialize: eglChooseConfig returned no config — session binding may fail");
    }

    // Cache render callback (Kotlin object with an `onRenderEye(int,int,int,int)` method).
    jclass cbCls = env->GetObjectClass(callback);
    g_ctx.onRenderEyeMethod = env->GetMethodID(cbCls, "onRenderEye", "(IIII)V");
    if (!g_ctx.onRenderEyeMethod)
    {
        LOGE("Callback missing onRenderEye(IIII)V");
        return JNI_FALSE;
    }
    LOGD("nativeInitialize: onRenderEye method cached  cls=%p method=%p",
         static_cast<void *>(cbCls), static_cast<void *>(g_ctx.onRenderEyeMethod));
    g_ctx.callbackRef = env->NewGlobalRef(callback);
    LOGD("nativeInitialize: callbackRef=%p", static_cast<void *>(g_ctx.callbackRef));

    LOGI("nativeInitialize: calling enumerateAndCreateInstance");
    if (!enumerateAndCreateInstance(env, activity))
    {
        LOGE("nativeInitialize: enumerateAndCreateInstance FAILED");
        releaseCallback(env);
        return JNI_FALSE;
    }
    LOGI("nativeInitialize: calling createSessionAndSwapchains");
    if (!createSessionAndSwapchains())
    {
        LOGE("nativeInitialize: createSessionAndSwapchains FAILED — destroying partial state");
        destroyAll();
        releaseCallback(env);
        return JNI_FALSE;
    }
    LOGI("nativeInitialize: SUCCESS — OpenXR fully initialised");
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_nativeRunFrame(JNIEnv *env, jclass)
{
    std::lock_guard<std::mutex> lock(g_ctxMutex);
    if (g_ctx.instance == XR_NULL_HANDLE)
    {
        // Only log this once — if the session is gone we'll spam otherwise.
        static bool s_loggedNoInstance = false;
        if (!s_loggedNoInstance)
        {
            LOGW("nativeRunFrame: instance is NULL — skipping (will not log again)");
            s_loggedNoInstance = true;
        }
        return;
    }
    if (!pollEvents())
        return;
    if (g_ctx.sessionRunning)
    {
        renderFrame(env);
    }
    else
    {
        // Throttled — log every 120 calls so we know we're in the idle-wait loop.
        static uint64_t s_idleCount = 0;
        if (++s_idleCount % 120 == 1)
        {
            LOGD("nativeRunFrame: sessionRunning=false  state=%s  exitReq=%d  #%llu idle calls",
                 xrSessionStateName(g_ctx.sessionState),
                 static_cast<int>(g_ctx.exitRequested.load()),
                 static_cast<unsigned long long>(s_idleCount));
        }
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

extern "C" JNIEXPORT jboolean JNICALL
Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_nativeRequestStereoSnapshot(JNIEnv *, jclass)
{
    std::lock_guard<std::mutex> lock(g_ctxMutex);
    if (!g_ctx.sessionRunning || g_ctx.instance == XR_NULL_HANDLE)
        return JNI_FALSE;
    if (g_ctx.stereoSnapshot.requested.load() || g_ctx.stereoSnapshot.ready.load())
        return JNI_FALSE;

    g_ctx.stereoSnapshot.width = 0;
    g_ctx.stereoSnapshot.height = 0;
    for (auto &eyePixels : g_ctx.stereoSnapshot.eyePixels)
    {
        eyePixels.clear();
    }
    g_ctx.stereoSnapshot.ready.store(false);
    g_ctx.stereoSnapshot.requested.store(true);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_nativeIsStereoSnapshotReady(JNIEnv *, jclass)
{
    std::lock_guard<std::mutex> lock(g_ctxMutex);
    // Acquire load pairs with the release store in the render thread, ensuring
    // pixel data written before ready=true is visible here.
    return g_ctx.stereoSnapshot.ready.load(std::memory_order_acquire) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_nativeConsumeStereoSnapshotPixels(JNIEnv *env, jclass, jint eye)
{
    std::lock_guard<std::mutex> lock(g_ctxMutex);
    if (!g_ctx.stereoSnapshot.ready.load(std::memory_order_acquire) || eye < 0 || eye >= static_cast<jint>(kViewCount))
        return nullptr;

    const auto &pixels = g_ctx.stereoSnapshot.eyePixels[eye];
    if (pixels.empty())
        return nullptr;

    jintArray result = env->NewIntArray(static_cast<jsize>(pixels.size()));
    if (result == nullptr)
        return nullptr;
    env->SetIntArrayRegion(result, 0, static_cast<jsize>(pixels.size()), pixels.data());
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_sza_fastmediasorter_vr_openxr_OpenXrNative_nativeReleaseStereoSnapshot(JNIEnv *, jclass)
{
    std::lock_guard<std::mutex> lock(g_ctxMutex);
    g_ctx.stereoSnapshot.requested.store(false);
    g_ctx.stereoSnapshot.ready.store(false);
    g_ctx.stereoSnapshot.width = 0;
    g_ctx.stereoSnapshot.height = 0;
    for (auto &eyePixels : g_ctx.stereoSnapshot.eyePixels)
    {
        eyePixels.clear();
    }
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
