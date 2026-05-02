#pragma once

#include "OpenXrCtx.h"

namespace xrnative
{

    bool createSessionAndSwapchains(XrCtx &ctx);
    bool createHudSwapchain(XrCtx &ctx, uint32_t requestedWidth, uint32_t requestedHeight);
    void destroyHudSwapchain(XrCtx &ctx);
    bool createPanelSwapchain(XrCtx &ctx, uint32_t requestedWidth, uint32_t requestedHeight);
    void destroyPanelSwapchain(XrCtx &ctx);

} // namespace xrnative