#include "OpenXrSwapchain.h"

#include "OpenXrHandTracking.h"
#include "OpenXrInput.h"
#include "OpenXrLog.h"
#include "OpenXrRayDraw.h"

#include <dlfcn.h>

#include <string>
#include <vector>

constexpr XrViewConfigurationType kViewConfig = XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO;
constexpr XrFormFactor kFormFactor = XR_FORM_FACTOR_HEAD_MOUNTED_DISPLAY;
constexpr XrReferenceSpaceType kSpaceType = XR_REFERENCE_SPACE_TYPE_LOCAL;

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

bool xrnative::createSessionAndSwapchains(XrCtx &ctx)
{
    LOGD("createSessionAndSwapchains: entry  instance=0x%llx",
         static_cast<unsigned long long>(reinterpret_cast<uintptr_t>(ctx.instance)));

    XrSystemGetInfo sysInfo{XR_TYPE_SYSTEM_GET_INFO};
    sysInfo.formFactor = kFormFactor;
    XR_CHECK(xrGetSystem(ctx.instance, &sysInfo, &ctx.systemId), "xrGetSystem");
    LOGI("xrGetSystem: systemId=0x%llx", static_cast<unsigned long long>(ctx.systemId));

    {
        XrSystemProperties sysProps{XR_TYPE_SYSTEM_PROPERTIES};
        if (XR_SUCCEEDED(xrGetSystemProperties(ctx.instance, ctx.systemId, &sysProps)))
        {
            LOGI("System: name='%s'  vendorId=%u", sysProps.systemName, sysProps.vendorId);
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

    {
        typedef XrResult(XRAPI_PTR * PFN_GetGfxReqsGLES)(
            XrInstance, XrSystemId, XrGraphicsRequirementsOpenGLESKHR *);

        PFN_GetGfxReqsGLES pfnGetGfxReqs = nullptr;

        XrResult lookupResult = xrGetInstanceProcAddr(
            ctx.instance,
            "xrGetOpenGLESGraphicsRequirementsKHR",
            reinterpret_cast<PFN_xrVoidFunction *>(&pfnGetGfxReqs));
        LOGD("xrGetInstanceProcAddr(xrGetOpenGLESGraphicsRequirementsKHR): result=%d ptr=%p",
             static_cast<int>(lookupResult), reinterpret_cast<void *>(pfnGetGfxReqs));

        if (pfnGetGfxReqs == nullptr)
        {
            lookupResult = xrGetInstanceProcAddr(
                XR_NULL_HANDLE,
                "xrGetOpenGLESGraphicsRequirementsKHR",
                reinterpret_cast<PFN_xrVoidFunction *>(&pfnGetGfxReqs));
            LOGD("xrGetInstanceProcAddr(XR_NULL_HANDLE, xrGetOpenGLESGraphicsRequirementsKHR): result=%d ptr=%p",
                 static_cast<int>(lookupResult), reinterpret_cast<void *>(pfnGetGfxReqs));
        }

        if (pfnGetGfxReqs == nullptr)
        {
            static const char *const kLoaderLibs[] = {
                "libopenxr_loader.so",
                "libopenxr.so",
                nullptr};
            for (int loaderIndex = 0; kLoaderLibs[loaderIndex] != nullptr && pfnGetGfxReqs == nullptr; ++loaderIndex)
            {
                void *loaderLib = dlopen(kLoaderLibs[loaderIndex], RTLD_NOW | RTLD_NOLOAD);
                if (loaderLib)
                {
                    pfnGetGfxReqs = reinterpret_cast<PFN_GetGfxReqsGLES>(
                        dlsym(loaderLib, "xrGetOpenGLESGraphicsRequirementsKHR"));
                    LOGD("dlsym(%s, xrGetOpenGLESGraphicsRequirementsKHR): ptr=%p",
                         kLoaderLibs[loaderIndex], reinterpret_cast<void *>(pfnGetGfxReqs));
                    dlclose(loaderLib);
                }
            }
        }

        if (pfnGetGfxReqs != nullptr)
        {
            XrGraphicsRequirementsOpenGLESKHR gfxReqs{XR_TYPE_GRAPHICS_REQUIREMENTS_OPENGL_ES_KHR};
            XrResult reqResult = pfnGetGfxReqs(ctx.instance, ctx.systemId, &gfxReqs);
            if (XR_FAILED(reqResult))
            {
                LOGW("xrGetOpenGLESGraphicsRequirementsKHR returned %d — proceeding",
                     static_cast<int>(reqResult));
            }
            else
            {
                LOGI("OpenGL ES version range: min=%u.%u  max=%u.%u",
                     XR_VERSION_MAJOR(gfxReqs.minApiVersionSupported),
                     XR_VERSION_MINOR(gfxReqs.minApiVersionSupported),
                     XR_VERSION_MAJOR(gfxReqs.maxApiVersionSupported),
                     XR_VERSION_MINOR(gfxReqs.maxApiVersionSupported));
            }
        }
        else
        {
            LOGW("xrGetOpenGLESGraphicsRequirementsKHR unavailable via all methods (instance/null/dlsym) — xrCreateSession will likely fail with XR_ERROR_GRAPHICS_REQUIREMENTS_CALL_MISSING (-50)");
        }
    }

    LOGD("EGL binding: display=%p config=%p context=%p",
         static_cast<void *>(ctx.eglDisplay),
         static_cast<void *>(ctx.eglConfig),
         static_cast<void *>(ctx.eglContext));

    XrGraphicsBindingOpenGLESAndroidKHR gfx{XR_TYPE_GRAPHICS_BINDING_OPENGL_ES_ANDROID_KHR};
    gfx.display = ctx.eglDisplay;
    gfx.config = ctx.eglConfig;
    gfx.context = ctx.eglContext;

    XrSessionCreateInfo createInfo{XR_TYPE_SESSION_CREATE_INFO};
    createInfo.next = &gfx;
    createInfo.systemId = ctx.systemId;
    LOGD("xrCreateSession: systemId=0x%llx  EGL display=%p context=%p",
         static_cast<unsigned long long>(ctx.systemId),
         static_cast<void *>(ctx.eglDisplay),
         static_cast<void *>(ctx.eglContext));

    XR_CHECK(xrCreateSession(ctx.instance, &createInfo, &ctx.session), "xrCreateSession");
    LOGI("xrCreateSession: SUCCESS  session=0x%llx",
         static_cast<unsigned long long>(reinterpret_cast<uintptr_t>(ctx.session)));

    XrReferenceSpaceCreateInfo spaceInfo{XR_TYPE_REFERENCE_SPACE_CREATE_INFO};
    spaceInfo.referenceSpaceType = kSpaceType;
    spaceInfo.poseInReferenceSpace.orientation.w = 1.0f;
    XR_CHECK(xrCreateReferenceSpace(ctx.session, &spaceInfo, &ctx.appSpace),
             "xrCreateReferenceSpace");
    LOGI("xrCreateReferenceSpace: appSpace=0x%llx  type=LOCAL",
         static_cast<unsigned long long>(reinterpret_cast<uintptr_t>(ctx.appSpace)));

    {
        XrReferenceSpaceCreateInfo viewSpaceInfo{XR_TYPE_REFERENCE_SPACE_CREATE_INFO};
        viewSpaceInfo.referenceSpaceType = XR_REFERENCE_SPACE_TYPE_VIEW;
        viewSpaceInfo.poseInReferenceSpace.orientation.w = 1.0f;
        XrResult viewSpaceResult = xrCreateReferenceSpace(ctx.session, &viewSpaceInfo, &ctx.viewSpace);
        if (XR_FAILED(viewSpaceResult))
        {
            LOGW("xrCreateReferenceSpace(VIEW) failed: %d — HUD layer will be disabled",
                 static_cast<int>(viewSpaceResult));
            ctx.viewSpace = XR_NULL_HANDLE;
        }
        else
        {
            LOGI("xrCreateReferenceSpace: viewSpace=0x%llx  type=VIEW",
                 static_cast<unsigned long long>(reinterpret_cast<uintptr_t>(ctx.viewSpace)));
        }
    }

    uint32_t viewCount = 0;
    XR_CHECK(xrEnumerateViewConfigurationViews(
                 ctx.instance, ctx.systemId, kViewConfig, 0, &viewCount, nullptr),
             "xrEnumerateViewConfigurationViews(count)");
    LOGI("ViewConfigurationViews count=%u (expected %u)", viewCount, kViewCount);
    if (viewCount != kViewCount)
    {
        LOGE("Expected %u views, runtime reports %u", kViewCount, viewCount);
        return false;
    }
    std::vector<XrViewConfigurationView> views(viewCount, {XR_TYPE_VIEW_CONFIGURATION_VIEW});
    XR_CHECK(xrEnumerateViewConfigurationViews(
                 ctx.instance, ctx.systemId, kViewConfig, viewCount, &viewCount, views.data()),
             "xrEnumerateViewConfigurationViews(data)");
    for (uint32_t eye = 0; eye < viewCount; ++eye)
    {
        LOGI("  view[%u]: recommended=%ux%u  sampleCount=%u  max=%ux%u",
             eye,
             views[eye].recommendedImageRectWidth, views[eye].recommendedImageRectHeight,
             views[eye].recommendedSwapchainSampleCount,
             views[eye].maxImageRectWidth, views[eye].maxImageRectHeight);
    }

    for (uint32_t eye = 0; eye < kViewCount; ++eye)
    {
        const auto &view = views[eye];

        XrSwapchainCreateInfo swapchainInfo{XR_TYPE_SWAPCHAIN_CREATE_INFO};
        swapchainInfo.usageFlags = XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT | XR_SWAPCHAIN_USAGE_SAMPLED_BIT;
        swapchainInfo.format = GL_SRGB8_ALPHA8;
        swapchainInfo.sampleCount = view.recommendedSwapchainSampleCount;
        swapchainInfo.width = view.recommendedImageRectWidth;
        swapchainInfo.height = view.recommendedImageRectHeight;
        swapchainInfo.faceCount = 1;
        swapchainInfo.arraySize = 1;
        swapchainInfo.mipCount = 1;
        LOGD("xrCreateSwapchain eye=%u  %ux%u  samples=%u  format=0x%x",
             eye, swapchainInfo.width, swapchainInfo.height, swapchainInfo.sampleCount, static_cast<unsigned>(swapchainInfo.format));

        XR_CHECK(xrCreateSwapchain(ctx.session, &swapchainInfo, &ctx.eyes[eye].handle), "xrCreateSwapchain");
        ctx.eyes[eye].width = swapchainInfo.width;
        ctx.eyes[eye].height = swapchainInfo.height;

        uint32_t imgCount = 0;
        XR_CHECK(xrEnumerateSwapchainImages(ctx.eyes[eye].handle, 0, &imgCount, nullptr),
                 "xrEnumerateSwapchainImages(count)");
        std::vector<XrSwapchainImageOpenGLESKHR> xrImages(
            imgCount, {XR_TYPE_SWAPCHAIN_IMAGE_OPENGL_ES_KHR});
        XR_CHECK(xrEnumerateSwapchainImages(
                     ctx.eyes[eye].handle,
                     imgCount,
                     &imgCount,
                     reinterpret_cast<XrSwapchainImageBaseHeader *>(xrImages.data())),
                 "xrEnumerateSwapchainImages(data)");

        ctx.eyes[eye].images.resize(imgCount);
        for (uint32_t imageIndex = 0; imageIndex < imgCount; ++imageIndex)
        {
            ctx.eyes[eye].images[imageIndex].imageId = xrImages[imageIndex].image;
            GLuint fbo = 0;
            glGenFramebuffers(1, &fbo);
            glBindFramebuffer(GL_FRAMEBUFFER, fbo);
            glFramebufferTexture2D(GL_FRAMEBUFFER,
                                   GL_COLOR_ATTACHMENT0,
                                   GL_TEXTURE_2D,
                                   xrImages[imageIndex].image,
                                   0);
            GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
            if (status != GL_FRAMEBUFFER_COMPLETE)
            {
                LOGE("FBO incomplete eye=%u idx=%u status=0x%x", eye, imageIndex, status);
            }
            else
            {
                LOGD("FBO ok eye=%u idx=%u fbo=%u texId=%u", eye, imageIndex, fbo, xrImages[imageIndex].image);
            }
            ctx.eyes[eye].images[imageIndex].fbo = fbo;
        }
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        LOGI("Eye %u swapchain: %ux%u, %u images  handle=0x%llx",
             eye, swapchainInfo.width, swapchainInfo.height, imgCount,
             static_cast<unsigned long long>(reinterpret_cast<uintptr_t>(ctx.eyes[eye].handle)));
    }

    if (!setupActionSet(ctx))
    {
        LOGW("createSessionAndSwapchains: setupActionSet failed — controllers will not respond; continuing");
    }

    if (!initHandTracking(ctx))
    {
        LOGI("createSessionAndSwapchains: hand tracking disabled — continuing with controllers only");
    }

    {
        const uint32_t hudW = ctx.hudSwapchainWidth != 0 ? ctx.hudSwapchainWidth : 1024u;
        const uint32_t hudH = ctx.hudSwapchainHeight != 0 ? ctx.hudSwapchainHeight : 256u;
        if (!createHudSwapchain(ctx, hudW, hudH))
        {
            LOGW("createSessionAndSwapchains: HUD swapchain creation failed — HUD disabled");
        }
    }

    if (!initRayResources(ctx))
    {
        LOGW("createSessionAndSwapchains: ray resources init failed — controller ray will not render");
    }

    LOGI("createSessionAndSwapchains: complete — session ready for runtime events");
    return true;
}

bool xrnative::createHudSwapchain(XrCtx &ctx, uint32_t requestedWidth, uint32_t requestedHeight)
{
    if (ctx.session == XR_NULL_HANDLE)
    {
        LOGW("createHudSwapchain: session is null — skipped");
        return false;
    }
    if (ctx.hudSwapchain != XR_NULL_HANDLE)
    {
        if (ctx.hudSwapchainWidth == requestedWidth && ctx.hudSwapchainHeight == requestedHeight)
        {
            return true;
        }
        destroyHudSwapchain(ctx);
    }

    XrSwapchainCreateInfo swapchainInfo{XR_TYPE_SWAPCHAIN_CREATE_INFO};
    swapchainInfo.usageFlags = XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT | XR_SWAPCHAIN_USAGE_SAMPLED_BIT;
    swapchainInfo.format = 0x8058;
    swapchainInfo.sampleCount = 1;
    swapchainInfo.width = requestedWidth;
    swapchainInfo.height = requestedHeight;
    swapchainInfo.faceCount = 1;
    swapchainInfo.arraySize = 1;
    swapchainInfo.mipCount = 1;

    XrResult result = xrCreateSwapchain(ctx.session, &swapchainInfo, &ctx.hudSwapchain);
    if (XR_FAILED(result))
    {
        LOGE("HUD swapchain xrCreateSwapchain failed: %d", static_cast<int>(result));
        ctx.hudSwapchain = XR_NULL_HANDLE;
        return false;
    }
    ctx.hudSwapchainWidth = requestedWidth;
    ctx.hudSwapchainHeight = requestedHeight;
    ctx.hudPendingPixels.assign(static_cast<size_t>(requestedWidth) * requestedHeight * 4u, 0u);
    ctx.hudBitmapPending.store(false);

    uint32_t imgCount = 0;
    result = xrEnumerateSwapchainImages(ctx.hudSwapchain, 0, &imgCount, nullptr);
    if (XR_FAILED(result) || imgCount == 0)
    {
        LOGE("HUD xrEnumerateSwapchainImages(count) failed: %d count=%u",
             static_cast<int>(result), imgCount);
        destroyHudSwapchain(ctx);
        return false;
    }
    std::vector<XrSwapchainImageOpenGLESKHR> xrImages(
        imgCount, {XR_TYPE_SWAPCHAIN_IMAGE_OPENGL_ES_KHR});
    result = xrEnumerateSwapchainImages(
        ctx.hudSwapchain,
        imgCount,
        &imgCount,
        reinterpret_cast<XrSwapchainImageBaseHeader *>(xrImages.data()));
    if (XR_FAILED(result))
    {
        LOGE("HUD xrEnumerateSwapchainImages(data) failed: %d", static_cast<int>(result));
        destroyHudSwapchain(ctx);
        return false;
    }

    ctx.hudSwapchainImages.resize(imgCount);
    for (uint32_t imageIndex = 0; imageIndex < imgCount; ++imageIndex)
    {
        ctx.hudSwapchainImages[imageIndex].imageId = xrImages[imageIndex].image;
        GLuint fbo = 0;
        glGenFramebuffers(1, &fbo);
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER,
                               GL_COLOR_ATTACHMENT0,
                               GL_TEXTURE_2D,
                               xrImages[imageIndex].image,
                               0);
        GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE)
        {
            LOGW("HUD FBO incomplete idx=%u status=0x%x", imageIndex, status);
        }
        ctx.hudSwapchainImages[imageIndex].fbo = fbo;
    }
    glBindFramebuffer(GL_FRAMEBUFFER, 0);

    LOGI("HUD swapchain: %ux%u, %u images  handle=0x%llx",
         requestedWidth, requestedHeight, imgCount,
         static_cast<unsigned long long>(reinterpret_cast<uintptr_t>(ctx.hudSwapchain)));
    return true;
}

void xrnative::destroyHudSwapchain(XrCtx &ctx)
{
    for (auto &img : ctx.hudSwapchainImages)
    {
        if (img.fbo)
        {
            GLuint fbo = img.fbo;
            glDeleteFramebuffers(1, &fbo);
            img.fbo = 0;
        }
    }
    ctx.hudSwapchainImages.clear();
    if (ctx.hudSwapchain != XR_NULL_HANDLE)
    {
        XrResult result = xrDestroySwapchain(ctx.hudSwapchain);
        if (XR_FAILED(result))
        {
            LOGW("HUD swapchain destroy failed: %d", static_cast<int>(result));
        }
        else
        {
            LOGI("HUD swapchain destroyed");
        }
        ctx.hudSwapchain = XR_NULL_HANDLE;
    }
    ctx.hudSwapchainWidth = 0;
    ctx.hudSwapchainHeight = 0;
    ctx.hudLayerVisible.store(false);
    ctx.hudPendingPixels.clear();
    ctx.hudBitmapPending.store(false);
}

bool xrnative::createPanelSwapchain(XrCtx &ctx, uint32_t requestedWidth, uint32_t requestedHeight)
{
    if (ctx.session == XR_NULL_HANDLE)
    {
        LOGW("createPanelSwapchain: session is null — skipped");
        return false;
    }
    if (ctx.panelSwapchain != XR_NULL_HANDLE)
    {
        if (ctx.panelSwapchainWidth == requestedWidth &&
            ctx.panelSwapchainHeight == requestedHeight)
            return true;
        destroyPanelSwapchain(ctx);
    }

    XrSwapchainCreateInfo swapchainInfo{XR_TYPE_SWAPCHAIN_CREATE_INFO};
    swapchainInfo.usageFlags = XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT | XR_SWAPCHAIN_USAGE_SAMPLED_BIT;
    swapchainInfo.format = GL_RGBA8;
    swapchainInfo.width = requestedWidth;
    swapchainInfo.height = requestedHeight;
    swapchainInfo.faceCount = 1;
    swapchainInfo.arraySize = 1;
    swapchainInfo.mipCount = 1;
    swapchainInfo.sampleCount = 1;
    LOGD("createPanelSwapchain: format=0x%x usageFlags=0x%x sampleCount=%u width=%u height=%u",
         static_cast<unsigned>(swapchainInfo.format), static_cast<unsigned>(swapchainInfo.usageFlags),
         swapchainInfo.sampleCount, swapchainInfo.width, swapchainInfo.height);

    XrResult result = xrCreateSwapchain(ctx.session, &swapchainInfo, &ctx.panelSwapchain);
    if (XR_FAILED(result))
    {
        LOGE("Panel swapchain xrCreateSwapchain failed: %d", static_cast<int>(result));
        ctx.panelSwapchain = XR_NULL_HANDLE;
        return false;
    }
    ctx.panelSwapchainWidth = requestedWidth;
    ctx.panelSwapchainHeight = requestedHeight;
    ctx.panelPendingPixels.assign(static_cast<size_t>(requestedWidth) * requestedHeight * 4u, 0u);

    uint32_t imgCount = 0;
    result = xrEnumerateSwapchainImages(ctx.panelSwapchain, 0, &imgCount, nullptr);
    if (XR_FAILED(result))
    {
        LOGE("Panel xrEnumerateSwapchainImages(count) failed: %d count=%u",
             static_cast<int>(result), imgCount);
        destroyPanelSwapchain(ctx);
        return false;
    }
    std::vector<XrSwapchainImageOpenGLESKHR> images(imgCount);
    for (auto &image : images)
        image.type = XR_TYPE_SWAPCHAIN_IMAGE_OPENGL_ES_KHR;
    result = xrEnumerateSwapchainImages(
        ctx.panelSwapchain, imgCount, &imgCount,
        reinterpret_cast<XrSwapchainImageBaseHeader *>(images.data()));
    if (XR_FAILED(result))
    {
        LOGE("Panel xrEnumerateSwapchainImages(data) failed: %d", static_cast<int>(result));
        destroyPanelSwapchain(ctx);
        return false;
    }
    ctx.panelSwapchainImages.resize(imgCount);
    for (uint32_t imageIndex = 0; imageIndex < imgCount; ++imageIndex)
        ctx.panelSwapchainImages[imageIndex].imageId = images[imageIndex].image;

    LOGI("createPanelSwapchain: %ux%u swapchain created imgCount=%u",
         requestedWidth, requestedHeight, imgCount);
    LOGI("nativeCreatePanelSwapchain: panel ready %ux%u imgCount=%u",
         ctx.panelSwapchainWidth, ctx.panelSwapchainHeight, imgCount);
    return true;
}

void xrnative::destroyPanelSwapchain(XrCtx &ctx)
{
    ctx.panelSwapchainImages.clear();
    if (ctx.panelSwapchain != XR_NULL_HANDLE)
    {
        XrResult result = xrDestroySwapchain(ctx.panelSwapchain);
        if (XR_FAILED(result))
            LOGW("Panel swapchain destroy failed: %d", static_cast<int>(result));
        else
            LOGI("Panel swapchain destroyed");
        ctx.panelSwapchain = XR_NULL_HANDLE;
    }
    ctx.panelSwapchainWidth = 0;
    ctx.panelSwapchainHeight = 0;
    ctx.panelLayerVisible.store(false);
    ctx.panelPendingPixels.clear();
    ctx.panelBitmapPending.store(false);
}