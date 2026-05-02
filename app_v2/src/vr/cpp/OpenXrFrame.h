#pragma once

#include "OpenXrCtx.h"

namespace xrnative
{

    void renderFrame(XrCtx &ctx, JNIEnv *env);
    void invokeRenderCallback(XrCtx &ctx, JNIEnv *env, int eye, int fbo, int width, int height);

} // namespace xrnative