#include "OpenXrFrame.h"

#include "OpenXrHandTracking.h"
#include "OpenXrInput.h"
#include "OpenXrLog.h"
#include "OpenXrRayDraw.h"

#include <vector>

constexpr XrViewConfigurationType kViewConfig = XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO;

using namespace xrnative;

#define LOGI(...) nativeLogEmit(ANDROID_LOG_INFO, __VA_ARGS__)
#define LOGW(...) nativeLogEmit(ANDROID_LOG_WARN, __VA_ARGS__)
#define LOGE(...) nativeLogEmit(ANDROID_LOG_ERROR, __VA_ARGS__)
#define LOGD(...) nativeLogEmit(ANDROID_LOG_DEBUG, __VA_ARGS__)

namespace
{

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

} // namespace

void xrnative::invokeRenderCallback(XrCtx &ctx, JNIEnv *env, int eye, int fbo, int width, int height)
{
    if (!ctx.callbackRef || !ctx.onRenderEyeMethod)
        return;
    env->CallVoidMethod(ctx.callbackRef, ctx.onRenderEyeMethod,
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

void xrnative::renderFrame(XrCtx &ctx, JNIEnv *env)
{
    static uint64_t s_frameCount = 0;
    ++s_frameCount;

    syncInputActions(ctx, env);
    syncHandTracking(ctx, env);
    syncControllerAimRay(ctx, env);

    XrFrameWaitInfo waitInfo{XR_TYPE_FRAME_WAIT_INFO};
    XrFrameState frameState{XR_TYPE_FRAME_STATE};
    XrResult waitResult = xrWaitFrame(ctx.session, &waitInfo, &frameState);
    if (XR_FAILED(waitResult))
    {
        LOGE("xrWaitFrame FAILED: %d (frame #%llu)",
             static_cast<int>(waitResult),
             static_cast<unsigned long long>(s_frameCount));
        return;
    }
    ctx.lastPredictedDisplayTime = frameState.predictedDisplayTime;

    XrFrameBeginInfo beginInfo{XR_TYPE_FRAME_BEGIN_INFO};
    XrResult beginResult = xrBeginFrame(ctx.session, &beginInfo);
    if (XR_FAILED(beginResult))
    {
        LOGE("xrBeginFrame FAILED: %d (frame #%llu)",
             static_cast<int>(beginResult),
             static_cast<unsigned long long>(s_frameCount));
        return;
    }

    if (s_frameCount % 300 == 1)
    {
        LOGI("renderFrame #%llu: shouldRender=%d  state=%s  layerType=%d  "
             "exitReq=%d  sessionRunning=%d",
             static_cast<unsigned long long>(s_frameCount),
             static_cast<int>(frameState.shouldRender),
             xrSessionStateName(ctx.sessionState),
             static_cast<int>(ctx.layerConfig.type),
             static_cast<int>(ctx.exitRequested.load()),
             static_cast<int>(ctx.sessionRunning));
    }

    const LayerConfig layerConfig = ctx.layerConfig;
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
            locate.space = ctx.appSpace;
            projectionViewsValid =
                XR_SUCCEEDED(xrLocateViews(ctx.session, &locate, &viewState,
                                           kViewCount, &viewCount, views.data())) &&
                (viewState.viewStateFlags & XR_VIEW_STATE_POSITION_VALID_BIT) &&
                (viewState.viewStateFlags & XR_VIEW_STATE_ORIENTATION_VALID_BIT);
        }

        if (projectionViewsValid)
        {
            uint32_t renderedEyes = 0;
            for (uint32_t eye = 0; eye < kViewCount; ++eye)
            {
                auto &chain = ctx.eyes[eye];

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
                invokeRenderCallback(ctx, env, static_cast<int>(eye),
                                     static_cast<int>(chain.images[imageIdx].fbo),
                                     static_cast<int>(chain.width),
                                     static_cast<int>(chain.height));

                drawControllerRays(ctx, views[eye].pose, views[eye].fov);

                if (ctx.stereoSnapshot.requested.load())
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

                    auto &dst = ctx.stereoSnapshot.eyePixels[eye];
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
                    ctx.stereoSnapshot.width = chain.width;
                    ctx.stereoSnapshot.height = chain.height;
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
            if (frameValid && ctx.stereoSnapshot.requested.load())
            {
                // Release store keeps stereo snapshot pixel writes visible to JNI polling.
                ctx.stereoSnapshot.ready.store(true, std::memory_order_release);
                ctx.stereoSnapshot.requested.store(false, std::memory_order_relaxed);
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
            projectionLayer.space = ctx.appSpace;
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
                quadLayers[eye].space = ctx.appSpace;
                quadLayers[eye].eyeVisibility = eyeVisibilityForIndex(eye);
                quadLayers[eye].subImage = buildFullFrameSubImage(ctx.eyes[eye]);
                quadLayers[eye].pose.orientation.w = 1.0f;
                quadLayers[eye].pose.position.z = -layerConfig.distanceMeters;
                quadLayers[eye].size = {layerConfig.widthMeters, layerConfig.heightMeters};
            }
            for (auto &layer : quadLayers)
            {
                layers.push_back(reinterpret_cast<const XrCompositionLayerBaseHeader *>(&layer));
            }
        }
        else if (layerConfig.type == LayerType::Equirect2 && ctx.supportsEquirect2)
        {
            equirectLayers.resize(kViewCount);
            for (uint32_t eye = 0; eye < kViewCount; ++eye)
            {
                equirectLayers[eye] = {XR_TYPE_COMPOSITION_LAYER_EQUIRECT2_KHR};
                equirectLayers[eye].space = ctx.appSpace;
                equirectLayers[eye].eyeVisibility = eyeVisibilityForIndex(eye);
                equirectLayers[eye].subImage = buildFullFrameSubImage(ctx.eyes[eye]);
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
        else if (layerConfig.type == LayerType::Cylinder && ctx.supportsCylinder)
        {
            const float aspectRatio = layerConfig.heightMeters > 0.0f
                                          ? (layerConfig.widthMeters / layerConfig.heightMeters)
                                          : 1.0f;
            cylinderLayers.resize(kViewCount);
            for (uint32_t eye = 0; eye < kViewCount; ++eye)
            {
                cylinderLayers[eye] = {XR_TYPE_COMPOSITION_LAYER_CYLINDER_KHR};
                cylinderLayers[eye].space = ctx.appSpace;
                cylinderLayers[eye].eyeVisibility = eyeVisibilityForIndex(eye);
                cylinderLayers[eye].subImage = buildFullFrameSubImage(ctx.eyes[eye]);
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
            if (layerConfig.type == LayerType::Equirect2 && !ctx.warnedMissingEquirect2)
            {
                LOGW("Equirect2 layer requested but XR_KHR_composition_layer_equirect2 is unavailable; falling back to cinema quad");
                ctx.warnedMissingEquirect2 = true;
            }
            if (layerConfig.type == LayerType::Cylinder && !ctx.warnedMissingCylinder)
            {
                LOGW("Cylinder layer requested but XR_KHR_composition_layer_cylinder is unavailable; falling back to cinema quad");
                ctx.warnedMissingCylinder = true;
            }

            quadLayers.resize(kViewCount);
            for (uint32_t eye = 0; eye < kViewCount; ++eye)
            {
                quadLayers[eye] = {XR_TYPE_COMPOSITION_LAYER_QUAD};
                quadLayers[eye].space = ctx.appSpace;
                quadLayers[eye].eyeVisibility = eyeVisibilityForIndex(eye);
                quadLayers[eye].subImage = buildFullFrameSubImage(ctx.eyes[eye]);
                quadLayers[eye].pose.orientation.w = 1.0f;
                quadLayers[eye].pose.position.z = -4.0f;
                quadLayers[eye].size = {4.0f, 2.25f};
                layers.push_back(reinterpret_cast<const XrCompositionLayerBaseHeader *>(&quadLayers[eye]));
            }
        }
    }

    if (ctx.hudBitmapPending.exchange(false) &&
        ctx.hudSwapchain != XR_NULL_HANDLE &&
        !ctx.hudPendingPixels.empty())
    {
        uint32_t uploadImgIdx = 0;
        XrSwapchainImageAcquireInfo uploadAcqInfo{XR_TYPE_SWAPCHAIN_IMAGE_ACQUIRE_INFO};
        XrResult uploadR = xrAcquireSwapchainImage(
            ctx.hudSwapchain, &uploadAcqInfo, &uploadImgIdx);
        if (XR_SUCCEEDED(uploadR))
        {
            XrSwapchainImageWaitInfo uploadWait{XR_TYPE_SWAPCHAIN_IMAGE_WAIT_INFO};
            uploadWait.timeout = XR_INFINITE_DURATION;
            uploadR = xrWaitSwapchainImage(ctx.hudSwapchain, &uploadWait);
            if (XR_SUCCEEDED(uploadR) &&
                uploadImgIdx < ctx.hudSwapchainImages.size())
            {
                const uint32_t texId = ctx.hudSwapchainImages[uploadImgIdx].imageId;
                glBindTexture(GL_TEXTURE_2D, texId);
                glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
                glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0,
                                static_cast<GLsizei>(ctx.hudSwapchainWidth),
                                static_cast<GLsizei>(ctx.hudSwapchainHeight),
                                GL_RGBA, GL_UNSIGNED_BYTE,
                                ctx.hudPendingPixels.data());
                glBindTexture(GL_TEXTURE_2D, 0);
            }
            XrSwapchainImageReleaseInfo uploadRelInfo{XR_TYPE_SWAPCHAIN_IMAGE_RELEASE_INFO};
            xrReleaseSwapchainImage(ctx.hudSwapchain, &uploadRelInfo);
            if (XR_SUCCEEDED(uploadR))
            {
                ctx.hudFrameUploaded.store(true);
            }
        }
    }

    XrCompositionLayerQuad hudLayer{XR_TYPE_COMPOSITION_LAYER_QUAD};
    const bool hudActive = ctx.hudLayerVisible.load() &&
                           ctx.hudSwapchain != XR_NULL_HANDLE &&
                           ctx.viewSpace != XR_NULL_HANDLE;
    const bool hudUploadedThisInterval = ctx.hudFrameUploaded.exchange(false);
    if (hudActive && !hudUploadedThisInterval)
    {
        uint32_t hudImgIdx = 0;
        XrSwapchainImageAcquireInfo acqInfo{XR_TYPE_SWAPCHAIN_IMAGE_ACQUIRE_INFO};
        XrResult acqR = xrAcquireSwapchainImage(ctx.hudSwapchain, &acqInfo, &hudImgIdx);
        if (XR_SUCCEEDED(acqR))
        {
            XrSwapchainImageWaitInfo waitInfo{XR_TYPE_SWAPCHAIN_IMAGE_WAIT_INFO};
            waitInfo.timeout = XR_INFINITE_DURATION;
            XrResult waitR = xrWaitSwapchainImage(ctx.hudSwapchain, &waitInfo);
            if (XR_SUCCEEDED(waitR))
            {
                XrSwapchainImageReleaseInfo relInfo{XR_TYPE_SWAPCHAIN_IMAGE_RELEASE_INFO};
                xrReleaseSwapchainImage(ctx.hudSwapchain, &relInfo);
            }
        }
    }
    if (hudActive)
    {
        hudLayer.layerFlags = XR_COMPOSITION_LAYER_BLEND_TEXTURE_SOURCE_ALPHA_BIT;
        hudLayer.space = ctx.viewSpace;
        hudLayer.eyeVisibility = XR_EYE_VISIBILITY_BOTH;
        hudLayer.subImage.swapchain = ctx.hudSwapchain;
        hudLayer.subImage.imageRect.offset = {0, 0};
        hudLayer.subImage.imageRect.extent = {
            static_cast<int32_t>(ctx.hudSwapchainWidth),
            static_cast<int32_t>(ctx.hudSwapchainHeight),
        };
        hudLayer.subImage.imageArrayIndex = 0;
        hudLayer.pose.orientation = {-0.17365f, 0.0f, 0.0f, 0.98481f};
        hudLayer.pose.position = {0.0f, 0.0f, -1.5f};
        hudLayer.size = {1.0f, 0.3f};
        layers.push_back(reinterpret_cast<const XrCompositionLayerBaseHeader *>(&hudLayer));
    }

    if (ctx.panelBitmapPending.exchange(false) &&
        ctx.panelSwapchain != XR_NULL_HANDLE &&
        !ctx.panelPendingPixels.empty())
    {
        uint32_t uploadImgIdx = 0;
        XrSwapchainImageAcquireInfo uploadAcqInfo{XR_TYPE_SWAPCHAIN_IMAGE_ACQUIRE_INFO};
        XrResult uploadR = xrAcquireSwapchainImage(
            ctx.panelSwapchain, &uploadAcqInfo, &uploadImgIdx);
        if (XR_SUCCEEDED(uploadR))
        {
            XrSwapchainImageWaitInfo uploadWait{XR_TYPE_SWAPCHAIN_IMAGE_WAIT_INFO};
            uploadWait.timeout = XR_INFINITE_DURATION;
            uploadR = xrWaitSwapchainImage(ctx.panelSwapchain, &uploadWait);
            if (XR_SUCCEEDED(uploadR) &&
                uploadImgIdx < ctx.panelSwapchainImages.size())
            {
                const uint32_t texId = ctx.panelSwapchainImages[uploadImgIdx].imageId;
                glBindTexture(GL_TEXTURE_2D, texId);
                glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
                glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0,
                                static_cast<GLsizei>(ctx.panelSwapchainWidth),
                                static_cast<GLsizei>(ctx.panelSwapchainHeight),
                                GL_RGBA, GL_UNSIGNED_BYTE,
                                ctx.panelPendingPixels.data());
                glBindTexture(GL_TEXTURE_2D, 0);
            }
            XrSwapchainImageReleaseInfo uploadRelInfo{XR_TYPE_SWAPCHAIN_IMAGE_RELEASE_INFO};
            xrReleaseSwapchainImage(ctx.panelSwapchain, &uploadRelInfo);
            if (XR_SUCCEEDED(uploadR))
                ctx.panelFrameUploaded.store(true);
        }
    }

    XrCompositionLayerQuad panelLayer{XR_TYPE_COMPOSITION_LAYER_QUAD};
    const bool panelActive = ctx.panelLayerVisible.load() &&
                             ctx.panelSwapchain != XR_NULL_HANDLE &&
                             ctx.viewSpace != XR_NULL_HANDLE;
    const bool panelUploadedThisInterval = ctx.panelFrameUploaded.exchange(false);
    if (panelActive && !panelUploadedThisInterval)
    {
        uint32_t panImgIdx = 0;
        XrSwapchainImageAcquireInfo acqInfo{XR_TYPE_SWAPCHAIN_IMAGE_ACQUIRE_INFO};
        if (XR_SUCCEEDED(xrAcquireSwapchainImage(ctx.panelSwapchain, &acqInfo, &panImgIdx)))
        {
            XrSwapchainImageWaitInfo waitInfo{XR_TYPE_SWAPCHAIN_IMAGE_WAIT_INFO};
            waitInfo.timeout = XR_INFINITE_DURATION;
            if (XR_SUCCEEDED(xrWaitSwapchainImage(ctx.panelSwapchain, &waitInfo)))
            {
                XrSwapchainImageReleaseInfo relInfo{XR_TYPE_SWAPCHAIN_IMAGE_RELEASE_INFO};
                xrReleaseSwapchainImage(ctx.panelSwapchain, &relInfo);
            }
        }
    }
    if (panelActive)
    {
        panelLayer.layerFlags = XR_COMPOSITION_LAYER_BLEND_TEXTURE_SOURCE_ALPHA_BIT;
        panelLayer.space = ctx.viewSpace;
        panelLayer.eyeVisibility = XR_EYE_VISIBILITY_BOTH;
        panelLayer.subImage.swapchain = ctx.panelSwapchain;
        panelLayer.subImage.imageRect.offset = {0, 0};
        panelLayer.subImage.imageRect.extent = {
            static_cast<int32_t>(ctx.panelSwapchainWidth),
            static_cast<int32_t>(ctx.panelSwapchainHeight),
        };
        panelLayer.subImage.imageArrayIndex = 0;
        panelLayer.pose.orientation = {-0.17365f, 0.0f, 0.0f, 0.98481f};
        panelLayer.pose.position = {0.0f, -0.35f, -1.5f};
        panelLayer.size = {1.0f, 0.5f};
        layers.push_back(reinterpret_cast<const XrCompositionLayerBaseHeader *>(&panelLayer));
    }

    XrFrameEndInfo endInfo{XR_TYPE_FRAME_END_INFO};
    endInfo.displayTime = frameState.predictedDisplayTime;
    endInfo.environmentBlendMode = XR_ENVIRONMENT_BLEND_MODE_OPAQUE;
    endInfo.layerCount = static_cast<uint32_t>(layers.size());
    endInfo.layers = layers.data();

    if (s_frameCount % 300 == 1)
    {
        LOGI("xrEndFrame #%llu: layerCount=%u frameValid=%d",
             static_cast<unsigned long long>(s_frameCount),
             endInfo.layerCount,
             static_cast<int>(frameValid));
    }

    XrResult endResult = xrEndFrame(ctx.session, &endInfo);
    if (XR_FAILED(endResult))
    {
        LOGE("xrEndFrame FAILED: %d  layerCount=%u  frame=#%llu",
             static_cast<int>(endResult),
             endInfo.layerCount,
             static_cast<unsigned long long>(s_frameCount));
    }
}