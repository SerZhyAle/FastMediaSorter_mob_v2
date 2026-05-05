#pragma once

#include "OpenXrCtx.h"

#include <openxr/openxr.h>

namespace xrnative
{
    bool initRayResources(XrCtx &ctx);
    void destroyRayResources(XrCtx &ctx);
    void drawControllerRays(XrCtx &ctx, const XrPosef &eyePose, const XrFovf &eyeFov);
} // namespace xrnative
