#pragma once

#include "OpenXrCtx.h"

namespace xrnative
{

    bool initHandTracking(XrCtx &ctx);
    void destroyHandTracking(XrCtx &ctx);
    void syncHandTracking(XrCtx &ctx, JNIEnv *env);

} // namespace xrnative