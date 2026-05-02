#pragma once

#include "OpenXrCtx.h"

namespace xrnative
{

    bool enumerateAndCreateInstance(XrCtx &ctx, JNIEnv *env, jobject activity);
    void handleSessionStateChange(XrCtx &ctx, XrEventDataSessionStateChanged *event);
    bool pollEvents(XrCtx &ctx);
    void releaseCallback(XrCtx &ctx, JNIEnv *env);
    void destroyAll(XrCtx &ctx);

} // namespace xrnative