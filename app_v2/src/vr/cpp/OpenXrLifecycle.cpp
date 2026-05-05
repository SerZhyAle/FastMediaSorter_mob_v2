#include "OpenXrLifecycle.h"

#include "OpenXrHandTracking.h"
#include "OpenXrInput.h"
#include "OpenXrLog.h"
#include "OpenXrRayDraw.h"
#include "OpenXrSwapchain.h"

#include <cstring>
#include <string>
#include <vector>

constexpr XrViewConfigurationType kViewConfig = XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO;

using namespace xrnative;

#define LOGI(...) nativeLogEmit(ANDROID_LOG_INFO, __VA_ARGS__)
#define LOGW(...) nativeLogEmit(ANDROID_LOG_WARN, __VA_ARGS__)
#define LOGE(...) nativeLogEmit(ANDROID_LOG_ERROR, __VA_ARGS__)
#define LOGD(...) nativeLogEmit(ANDROID_LOG_DEBUG, __VA_ARGS__)

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

bool xrnative::enumerateAndCreateInstance(XrCtx &ctx, JNIEnv *env, jobject activity)
{
    LOGD("enumerateAndCreateInstance: entry  vm=%p activity=%p", ctx.vm, activity);

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
        loaderInit.applicationVM = ctx.vm;
        loaderInit.applicationContext = activity;
        XrResult result = initLoader(reinterpret_cast<XrLoaderInitInfoBaseHeaderKHR *>(&loaderInit));
        if (XR_FAILED(result))
        {
            LOGE("xrInitializeLoaderKHR failed: %d", static_cast<int>(result));
            return false;
        }
        LOGI("xrInitializeLoaderKHR: OK");
    }
    else
    {
        LOGW("xrInitializeLoaderKHR not available (result=%d); continuing best-effort",
             static_cast<int>(loaderLookup));
    }

    uint32_t extCount = 0;
    XR_CHECK(xrEnumerateInstanceExtensionProperties(nullptr, 0, &extCount, nullptr),
             "xrEnumerateInstanceExtensionProperties(count)");
    std::vector<XrExtensionProperties> props(extCount, {XR_TYPE_EXTENSION_PROPERTIES});
    XR_CHECK(xrEnumerateInstanceExtensionProperties(nullptr, extCount, &extCount, props.data()),
             "xrEnumerateInstanceExtensionProperties(data)");

    LOGI("Runtime extensions available: %u (key extensions checked below)", extCount);

    auto hasExt = [&](const char *name)
    {
        for (const auto &prop : props)
        {
            if (std::string(prop.extensionName) == name)
                return true;
        }
        return false;
    };

    std::vector<const char *> enabledExts;
    const char *kGraphicsExt = XR_KHR_OPENGL_ES_ENABLE_EXTENSION_NAME;
    const char *kAndroidCreateExt = XR_KHR_ANDROID_CREATE_INSTANCE_EXTENSION_NAME;
    const char *kEquirect2Ext = XR_KHR_COMPOSITION_LAYER_EQUIRECT2_EXTENSION_NAME;
    const char *kCylinderExt = XR_KHR_COMPOSITION_LAYER_CYLINDER_EXTENSION_NAME;
    const char *kHandTrackingExt = XR_EXT_HAND_TRACKING_EXTENSION_NAME;
    const char *kHandAimMetaExt = XR_META_HAND_TRACKING_AIM_EXTENSION_NAME;
    const char *kHandAimFbExt = XR_FB_HAND_TRACKING_AIM_EXTENSION_NAME;
    const char *kMicrogesturesExt = XR_META_HAND_TRACKING_MICROGESTURES_EXTENSION_NAME;

    LOGI("Extension check: GraphicsES=%d AndroidCreate=%d Equirect2=%d Cylinder=%d "
         "HandTracking=%d HandAim(META=%d FB=%d) Microgestures=%d",
         hasExt(kGraphicsExt),
         hasExt(kAndroidCreateExt),
         hasExt(kEquirect2Ext),
         hasExt(kCylinderExt),
         hasExt(kHandTrackingExt),
         hasExt(kHandAimMetaExt),
         hasExt(kHandAimFbExt),
         hasExt(kMicrogesturesExt));

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
    ctx.supportsEquirect2 = hasExt(kEquirect2Ext);
    ctx.supportsCylinder = hasExt(kCylinderExt);
    if (ctx.supportsEquirect2)
    {
        enabledExts.push_back(kEquirect2Ext);
    }
    if (ctx.supportsCylinder)
    {
        enabledExts.push_back(kCylinderExt);
    }
    ctx.supportsHandTracking = hasExt(kHandTrackingExt);
    if (ctx.supportsHandTracking)
    {
        enabledExts.push_back(kHandTrackingExt);
        if (hasExt(kHandAimMetaExt))
        {
            enabledExts.push_back(kHandAimMetaExt);
            ctx.supportsHandAim = true;
        }
        else if (hasExt(kHandAimFbExt))
        {
            enabledExts.push_back(kHandAimFbExt);
            ctx.supportsHandAim = true;
        }
        // Microgestures remain gated on META aim because recent Quest runtimes
        // reject the FB alias as a dependency even though hand aim itself still works.
        if (hasExt(kMicrogesturesExt) && hasExt(kHandAimMetaExt))
        {
            enabledExts.push_back(kMicrogesturesExt);
            ctx.supportsMicrogestures = true;
        }
    }
    LOGI("Enabled extensions (%zu): GraphicsES Equirect2=%d Cylinder=%d "
         "HandTracking=%d HandAim=%d Microgestures=%d",
         enabledExts.size(),
         static_cast<int>(ctx.supportsEquirect2),
         static_cast<int>(ctx.supportsCylinder),
         static_cast<int>(ctx.supportsHandTracking),
         static_cast<int>(ctx.supportsHandAim),
         static_cast<int>(ctx.supportsMicrogestures));

    XrInstanceCreateInfoAndroidKHR androidCreate{XR_TYPE_INSTANCE_CREATE_INFO_ANDROID_KHR};
    androidCreate.applicationVM = ctx.vm;
    androidCreate.applicationActivity = activity;

    XrInstanceCreateInfo createInfo{XR_TYPE_INSTANCE_CREATE_INFO};
    createInfo.next = &androidCreate;
    createInfo.enabledExtensionCount = static_cast<uint32_t>(enabledExts.size());
    createInfo.enabledExtensionNames = enabledExts.data();
    createInfo.applicationInfo.apiVersion = XR_MAKE_VERSION(1, 1, 0);
    std::strncpy(createInfo.applicationInfo.applicationName,
                 "FastMediaSorter-VR",
                 XR_MAX_APPLICATION_NAME_SIZE - 1);
    std::strncpy(createInfo.applicationInfo.engineName,
                 "FMS-OpenXR",
                 XR_MAX_ENGINE_NAME_SIZE - 1);
    LOGD("xrCreateInstance: apiVersion=0x%llx app='%s' engine='%s'",
         static_cast<unsigned long long>(createInfo.applicationInfo.apiVersion),
         createInfo.applicationInfo.applicationName,
         createInfo.applicationInfo.engineName);

    XrResult createResult = xrCreateInstance(&createInfo, &ctx.instance);
    if (createResult == XR_ERROR_EXTENSION_DEPENDENCY_NOT_ENABLED)
    {
        LOGE("xrCreateInstance: XR_ERROR_EXTENSION_DEPENDENCY_NOT_ENABLED — retrying with minimal extension set");
        for (const char *name : enabledExts)
        {
            LOGW("  was-enabled: %s", name);
        }
        enabledExts.clear();
        enabledExts.push_back(kGraphicsExt);
        if (hasExt(kAndroidCreateExt))
        {
            enabledExts.push_back(kAndroidCreateExt);
        }
        ctx.supportsEquirect2 = false;
        ctx.supportsCylinder = false;
        ctx.supportsHandTracking = false;
        ctx.supportsHandAim = false;
        ctx.supportsMicrogestures = false;
        createInfo.enabledExtensionCount = static_cast<uint32_t>(enabledExts.size());
        createInfo.enabledExtensionNames = enabledExts.data();
        LOGI("xrCreateInstance: retry with %zu extensions (graphics-only)", enabledExts.size());
        createResult = xrCreateInstance(&createInfo, &ctx.instance);
    }
    if (XR_FAILED(createResult))
    {
        LOGE("xrCreateInstance failed: %d", static_cast<int>(createResult));
        return false;
    }
    LOGI("XR instance created: handle=0x%llx  extensions=%zu  Equirect2=%d Cylinder=%d",
         static_cast<unsigned long long>(reinterpret_cast<uintptr_t>(ctx.instance)),
         enabledExts.size(),
         static_cast<int>(ctx.supportsEquirect2),
         static_cast<int>(ctx.supportsCylinder));
    return true;
}

void xrnative::handleSessionStateChange(XrCtx &ctx, XrEventDataSessionStateChanged *event)
{
    const XrSessionState prevState = ctx.sessionState;
    ctx.sessionState = event->state;
    LOGI("Session state: %s(%d) -> %s(%d)",
         xrSessionStateName(prevState), static_cast<int>(prevState),
         xrSessionStateName(event->state), static_cast<int>(event->state));

    switch (event->state)
    {
    case XR_SESSION_STATE_READY:
    {
        XrSessionBeginInfo beginInfo{XR_TYPE_SESSION_BEGIN_INFO};
        beginInfo.primaryViewConfigurationType = kViewConfig;
        XrResult result = xrBeginSession(ctx.session, &beginInfo);
        if (XR_SUCCEEDED(result))
        {
            ctx.sessionRunning = true;
            LOGI("xrBeginSession: OK — render loop will start next frame");
        }
        else
        {
            LOGE("xrBeginSession FAILED: %d — session will not render", static_cast<int>(result));
        }
        break;
    }
    case XR_SESSION_STATE_STOPPING:
    {
        LOGI("xrEndSession: calling — sessionRunning was %d", static_cast<int>(ctx.sessionRunning));
        XrResult result = xrEndSession(ctx.session);
        if (XR_FAILED(result))
        {
            LOGE("xrEndSession FAILED: %d", static_cast<int>(result));
        }
        ctx.sessionRunning = false;
        break;
    }
    case XR_SESSION_STATE_EXITING:
    case XR_SESSION_STATE_LOSS_PENDING:
        LOGI("Session %s — requesting exit", xrSessionStateName(event->state));
        ctx.exitRequested = true;
        break;
    default:
        LOGD("Session state %s — no action needed", xrSessionStateName(event->state));
        break;
    }
}

bool xrnative::pollEvents(XrCtx &ctx)
{
    XrEventDataBuffer event{XR_TYPE_EVENT_DATA_BUFFER};
    while (xrPollEvent(ctx.instance, &event) == XR_SUCCESS)
    {
        LOGD("pollEvents: received event type=%d (%s)",
             static_cast<int>(event.type), xrEventTypeName(event.type));
        switch (event.type)
        {
        case XR_TYPE_EVENT_DATA_SESSION_STATE_CHANGED:
            handleSessionStateChange(
                ctx,
                reinterpret_cast<XrEventDataSessionStateChanged *>(&event));
            break;
        case XR_TYPE_EVENT_DATA_INSTANCE_LOSS_PENDING:
            LOGW("pollEvents: INSTANCE_LOSS_PENDING — stopping render loop");
            ctx.exitRequested = true;
            return false;
        case XR_TYPE_EVENT_DATA_INTERACTION_PROFILE_CHANGED:
        {
            LOGD("pollEvents: interaction profile changed — querying active profiles");
            const char *topLevelPaths[] = {"/user/hand/left", "/user/hand/right"};
            for (const auto *path : topLevelPaths)
            {
                XrPath topLevel = XR_NULL_PATH;
                xrStringToPath(ctx.instance, path, &topLevel);
                XrInteractionProfileState state{XR_TYPE_INTERACTION_PROFILE_STATE};
                XrResult result = xrGetCurrentInteractionProfile(ctx.session, topLevel, &state);
                if (XR_SUCCEEDED(result) && state.interactionProfile != XR_NULL_PATH)
                {
                    uint32_t len = 0;
                    char profileName[256] = {};
                    xrPathToString(ctx.instance, state.interactionProfile,
                                   sizeof(profileName), &len, profileName);
                    LOGD("pollEvents: active profile for %s = %s", path, profileName);
                }
            }
            break;
        }
        default:
            LOGD("pollEvents: unhandled event type=%d", static_cast<int>(event.type));
            break;
        }
        event = {XR_TYPE_EVENT_DATA_BUFFER};
    }
    if (ctx.exitRequested.load())
    {
        LOGI("pollEvents: exitRequested — render loop will terminate");
    }
    return !ctx.exitRequested.load();
}

void xrnative::releaseCallback(XrCtx &ctx, JNIEnv *env)
{
    if (ctx.callbackRef)
    {
        env->DeleteGlobalRef(ctx.callbackRef);
        ctx.callbackRef = nullptr;
    }
    ctx.onRenderEyeMethod = nullptr;
    releaseInputCallback(ctx, env);
}

void xrnative::destroyAll(XrCtx &ctx)
{
    destroyRayResources(ctx);
    destroyHandTracking(ctx);
    destroyInputHandles(ctx);
    destroyHudSwapchain(ctx);
    destroyPanelSwapchain(ctx);
    for (auto &eye : ctx.eyes)
    {
        for (auto &img : eye.images)
        {
            if (img.fbo)
            {
                GLuint fbo = img.fbo;
                glDeleteFramebuffers(1, &fbo);
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
    if (ctx.appSpace != XR_NULL_HANDLE)
    {
        xrDestroySpace(ctx.appSpace);
        ctx.appSpace = XR_NULL_HANDLE;
    }
    if (ctx.viewSpace != XR_NULL_HANDLE)
    {
        xrDestroySpace(ctx.viewSpace);
        ctx.viewSpace = XR_NULL_HANDLE;
    }
    if (ctx.session != XR_NULL_HANDLE)
    {
        xrDestroySession(ctx.session);
        ctx.session = XR_NULL_HANDLE;
    }
    if (ctx.instance != XR_NULL_HANDLE)
    {
        xrDestroyInstance(ctx.instance);
        ctx.instance = XR_NULL_HANDLE;
    }
    ctx.supportsEquirect2 = false;
    ctx.supportsCylinder = false;
    ctx.warnedMissingEquirect2 = false;
    ctx.warnedMissingCylinder = false;
    ctx.supportsHandTracking = false;
    ctx.supportsHandAim = false;
    ctx.supportsMicrogestures = false;
    ctx.layerConfig = LayerConfig{};
    ctx.stereoSnapshot.requested.store(false);
    ctx.stereoSnapshot.ready.store(false);
    ctx.stereoSnapshot.width = 0;
    ctx.stereoSnapshot.height = 0;
    for (auto &eyePixels : ctx.stereoSnapshot.eyePixels)
    {
        eyePixels.clear();
    }
    ctx.sessionRunning = false;
    ctx.exitRequested = false;
    ctx.sessionState = XR_SESSION_STATE_UNKNOWN;
}