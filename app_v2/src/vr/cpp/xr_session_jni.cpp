// S1270: JNI helpers, the video SurfaceTexture/Surface objects and the native -> Kotlin
// callbacks, extracted from xr_session.cpp.

#include "xr_session_internal.h"

namespace fms::xr
{
    namespace detail
    {

        JNIEnv *getAttachedEnv(bool &attached)
        {
            attached = false;
            if (!g.vm)
                return nullptr;
            JNIEnv *env = nullptr;
            jint res = g.vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
            if (res == JNI_EDETACHED)
            {
                if (g.vm->AttachCurrentThread(&env, nullptr) == JNI_OK)
                {
                    attached = true;
                }
            }
            return env;
        }

        void clearJniException(JNIEnv *env, const char *label)
        {
            if (!env || !env->ExceptionCheck())
                return;
            env->ExceptionDescribe();
            env->ExceptionClear();
            LOGW("%s threw; cleared JNI exception", label);
        }

        // S0607: fetch the process-stable Application context from the Activity
        // (Context.getApplicationContext()). Used to initialize the OpenXR loader once with a ref
        // that outlives every per-entry Activity. Returns a local ref the caller must delete, or
        // null on any JNI failure (the caller then falls back to the Activity).
        jobject getApplicationContextLocal(JNIEnv *env, jobject activity)
        {
            if (!env || !activity)
                return nullptr;
            jclass cls = env->GetObjectClass(activity);
            if (!cls)
            {
                clearJniException(env, "GetObjectClass(activity)");
                return nullptr;
            }
            jmethodID mid = env->GetMethodID(cls, "getApplicationContext", "()Landroid/content/Context;");
            env->DeleteLocalRef(cls);
            if (!mid)
            {
                clearJniException(env, "GetMethodID(getApplicationContext)");
                return nullptr;
            }
            jobject ctx = env->CallObjectMethod(activity, mid);
            if (env->ExceptionCheck())
            {
                clearJniException(env, "Activity.getApplicationContext");
                return nullptr;
            }
            return ctx;
        }

        bool createVideoSurfaceObjects()
        {
            if (!g.vm || g.videoTexture != 0 || g.videoSurfaceTexture || g.videoSurface)
                return true;

            glGenTextures(1, &g.videoTexture);
            glBindTexture(GL_TEXTURE_EXTERNAL_OES, g.videoTexture);
            glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            if (!checkGl("createVideoSurfaceObjects.texture"))
                return false;

            bool attached = false;
            JNIEnv *env = getAttachedEnv(attached);
            if (!env)
                return false;

            jclass localSurfaceTextureClass = env->FindClass("android/graphics/SurfaceTexture");
            if (!localSurfaceTextureClass)
            {
                clearJniException(env, "FindClass(SurfaceTexture)");
                if (attached)
                    g.vm->DetachCurrentThread();
                return false;
            }
            g.surfaceTextureClass = static_cast<jclass>(env->NewGlobalRef(localSurfaceTextureClass));
            jmethodID surfaceTextureCtor = env->GetMethodID(localSurfaceTextureClass, "<init>", "(I)V");
            g.surfaceTextureUpdateTexImage = env->GetMethodID(localSurfaceTextureClass, "updateTexImage", "()V");
            g.surfaceTextureGetTransformMatrix = env->GetMethodID(localSurfaceTextureClass, "getTransformMatrix", "([F)V");
            g.surfaceTextureRelease = env->GetMethodID(localSurfaceTextureClass, "release", "()V");
            jobject localSurfaceTexture = surfaceTextureCtor
                                              ? env->NewObject(localSurfaceTextureClass, surfaceTextureCtor, static_cast<jint>(g.videoTexture))
                                              : nullptr;
            clearJniException(env, "SurfaceTexture.<init>");
            env->DeleteLocalRef(localSurfaceTextureClass);
            if (!localSurfaceTexture)
            {
                if (attached)
                    g.vm->DetachCurrentThread();
                return false;
            }
            g.videoSurfaceTexture = env->NewGlobalRef(localSurfaceTexture);

            jclass localSurfaceClass = env->FindClass("android/view/Surface");
            if (!localSurfaceClass)
            {
                clearJniException(env, "FindClass(Surface)");
                env->DeleteLocalRef(localSurfaceTexture);
                if (attached)
                    g.vm->DetachCurrentThread();
                return false;
            }
            g.surfaceClass = static_cast<jclass>(env->NewGlobalRef(localSurfaceClass));
            jmethodID surfaceCtor = env->GetMethodID(localSurfaceClass, "<init>", "(Landroid/graphics/SurfaceTexture;)V");
            g.surfaceRelease = env->GetMethodID(localSurfaceClass, "release", "()V");
            jobject localSurface = surfaceCtor ? env->NewObject(localSurfaceClass, surfaceCtor, localSurfaceTexture) : nullptr;
            clearJniException(env, "Surface.<init>");
            env->DeleteLocalRef(localSurfaceClass);
            env->DeleteLocalRef(localSurfaceTexture);
            if (!localSurface)
            {
                if (attached)
                    g.vm->DetachCurrentThread();
                return false;
            }
            g.videoSurface = env->NewGlobalRef(localSurface);
            env->DeleteLocalRef(localSurface);

            if (attached)
                g.vm->DetachCurrentThread();
            LOGD("native video surface created");
            return g.videoSurfaceTexture && g.videoSurface;
        }

        void updateVideoTextureIfNeeded()
        {
            if (!g.videoTextureEnabled.load(std::memory_order_relaxed) || !g.videoSurfaceTexture)
                return;
            bool attached = false;
            JNIEnv *env = getAttachedEnv(attached);
            if (!env)
                return;

            env->CallVoidMethod(g.videoSurfaceTexture, g.surfaceTextureUpdateTexImage);
            if (env->ExceptionCheck())
            {
                clearJniException(env, "SurfaceTexture.updateTexImage");
                if (attached)
                    g.vm->DetachCurrentThread();
                return;
            }

            jfloatArray matrix = env->NewFloatArray(16);
            if (matrix)
            {
                env->CallVoidMethod(g.videoSurfaceTexture, g.surfaceTextureGetTransformMatrix, matrix);
                if (!env->ExceptionCheck())
                {
                    env->GetFloatArrayRegion(matrix, 0, 16, g.videoTextureTransform);
                }
                else
                {
                    clearJniException(env, "SurfaceTexture.getTransformMatrix");
                }
                env->DeleteLocalRef(matrix);
            }

            if (attached)
                g.vm->DetachCurrentThread();
        }

        void releaseVideoSurfaceObjects(JNIEnv *env)
        {
            if (!env)
                return;
            if (g.videoSurface && g.surfaceRelease)
            {
                env->CallVoidMethod(g.videoSurface, g.surfaceRelease);
                clearJniException(env, "Surface.release");
                env->DeleteGlobalRef(g.videoSurface);
                g.videoSurface = nullptr;
            }
            if (g.videoSurfaceTexture && g.surfaceTextureRelease)
            {
                env->CallVoidMethod(g.videoSurfaceTexture, g.surfaceTextureRelease);
                clearJniException(env, "SurfaceTexture.release");
                env->DeleteGlobalRef(g.videoSurfaceTexture);
                g.videoSurfaceTexture = nullptr;
            }
            if (g.surfaceTextureClass)
            {
                env->DeleteGlobalRef(g.surfaceTextureClass);
                g.surfaceTextureClass = nullptr;
            }
            if (g.surfaceClass)
            {
                env->DeleteGlobalRef(g.surfaceClass);
                g.surfaceClass = nullptr;
            }
            g.surfaceTextureUpdateTexImage = nullptr;
            g.surfaceTextureGetTransformMatrix = nullptr;
            g.surfaceTextureRelease = nullptr;
            g.surfaceRelease = nullptr;
        }

        void triggerJniInputCallback(int eventType)
        {
            if (!g.vm || !g.activity)
                return;
            JNIEnv *env = nullptr;
            bool attached = false;
            jint res = g.vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
            if (res == JNI_EDETACHED)
            {
                if (g.vm->AttachCurrentThread(&env, nullptr) == JNI_OK)
                {
                    attached = true;
                }
            }
            if (env && g.activity)
            {
                jclass clazz = env->GetObjectClass(g.activity);
                if (clazz)
                {
                    jmethodID method = env->GetMethodID(clazz, "onNativeInputEvent", "(I)V");
                    if (method)
                    {
                        env->CallVoidMethod(g.activity, method, eventType);
                    }
                    env->DeleteLocalRef(clazz);
                }
            }
            if (attached)
            {
                g.vm->DetachCurrentThread();
            }
        }

        void triggerJniRayInteraction(float uvX, float uvY, bool isHover, bool isClick)
        {
            if (!g.vm || !g.activity)
                return;
            JNIEnv *env = nullptr;
            bool attached = false;
            jint res = g.vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
            if (res == JNI_EDETACHED)
            {
                if (g.vm->AttachCurrentThread(&env, nullptr) == JNI_OK)
                {
                    attached = true;
                }
            }
            if (env && g.activity)
            {
                jclass clazz = env->GetObjectClass(g.activity);
                if (clazz)
                {
                    jmethodID method = env->GetMethodID(clazz, "onNativeRayInteraction", "(FFZZ)V");
                    if (method)
                    {
                        env->CallVoidMethod(g.activity, method, uvX, uvY, isHover ? JNI_TRUE : JNI_FALSE, isClick ? JNI_TRUE : JNI_FALSE);
                    }
                    env->DeleteLocalRef(clazz);
                }
            }
            if (attached)
            {
                g.vm->DetachCurrentThread();
            }
        }

    } // namespace detail
} // namespace fms::xr
