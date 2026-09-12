// Internal header for the fms_diagnostic_xr session unit.
//
// S1270: xr_session.cpp outgrew the project's per-file line ceiling and was split into four
// translation units - xr_session.cpp (public API and lifecycle), xr_session_setup.cpp,
// xr_session_render.cpp and xr_session_jni.cpp. All four share one `State g`, which was
// translation-unit-local while everything lived in one file, so it is declared here and
// defined in exactly one unit (xr_session.cpp).
//
// This header is internal. The module's public surface stays xr_session.h.

#ifndef FMS_XR_SESSION_INTERNAL_H
#define FMS_XR_SESSION_INTERNAL_H

#include "xr_session.h"
#include "xr_input.h"
#include "xr_hud_world.h"

#include <android/log.h>
#include <android/native_window.h>
#include <jni.h>

#include <atomic>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <mutex>
#include <string>
#include <unistd.h>
#include <utility>
#include <vector>

#define XR_USE_PLATFORM_ANDROID
#define XR_USE_GRAPHICS_API_OPENGL_ES

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES3/gl3.h>
#include <GLES2/gl2ext.h>
#include <openxr/openxr.h>
#include <openxr/openxr_platform.h>

#ifndef GL_TEXTURE_MAX_ANISOTROPY_EXT
#define GL_TEXTURE_MAX_ANISOTROPY_EXT 0x84FE
#endif

#ifndef GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT
#define GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT 0x84FF
#endif

#ifndef XR_EXT_HAND_INTERACTION_EXTENSION_NAME
#define XR_EXT_HAND_INTERACTION_EXTENSION_NAME "XR_EXT_hand_interaction"
#endif

#ifndef XR_FB_HAND_TRACKING_AIM_EXTENSION_NAME
#define XR_FB_HAND_TRACKING_AIM_EXTENSION_NAME "XR_FB_hand_tracking_aim"
#endif

namespace fms::xr
{
    namespace detail
    {

        constexpr const char *kLogTag = "S0249.XrSession";
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, kLogTag, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, kLogTag, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, kLogTag, __VA_ARGS__)

        struct SwapchainImageGL
        {
            XrSwapchainImageOpenGLESKHR image;
        };
        struct EyeSwapchain
        {
            XrSwapchain handle{XR_NULL_HANDLE};
            int width{0};
            int height{0};
            std::vector<SwapchainImageGL> images;
            GLuint depthRb{0};
            GLuint fbo{0};
        };

        struct State
        {
            std::mutex mutex;
            std::atomic<bool> running{false};
            std::atomic<bool> exitRequested{false};

            XrInstance instance{XR_NULL_HANDLE};
            XrSystemId systemId{XR_NULL_SYSTEM_ID};
            XrSession session{XR_NULL_HANDLE};
            XrSpace localSpace{XR_NULL_HANDLE};
            XrSessionState sessionState{XR_SESSION_STATE_UNKNOWN};
            bool sessionRunning{false};
            std::vector<XrViewConfigurationView> viewConfigs;
            std::vector<EyeSwapchain> eyes;

            JavaVM *vm{nullptr};
            jobject activity{nullptr};

            int renderProjection{0}; // 0 = 360, 1 = 180, 2 = Flat
            int stereoLayout{1};     // 0 = Mono, 1 = Top-Bottom, 2 = Side-by-Side
            float parallaxShift{0.0f};
            // S0291 owner round 5 (2026-05-22 22:32): thumbstick-Y driven zoom factor. 1.0 = no
            // change. Higher = image appears closer/bigger (zoom IN). Stick toward user accumulates
            // zoom UP; stick away accumulates zoom DOWN. Clamped [0.3, 3.0]. For FLAT projection
            // applied as model matrix scale; for 360/180 applied as UV scaling in the fragment shader.
            float zoom{1.0f};

            std::vector<uint8_t> pendingFrameData;
            int pendingFrameWidth{0};
            int pendingFrameHeight{0};
            bool pendingFrameReady{false};
            std::mutex frameMutex;

            EGLDisplay eglDisplay{EGL_NO_DISPLAY};
            EGLContext eglContext{EGL_NO_CONTEXT};
            EGLConfig eglConfig{nullptr};
            EGLSurface eglSurface{EGL_NO_SURFACE};
            ANativeWindow *window{nullptr};

            GLuint program{0};
            GLuint texture{0};
            GLuint videoProgram{0};
            GLuint videoTexture{0};
            std::atomic<bool> videoTextureEnabled{false};
            jobject videoSurfaceTexture{nullptr};
            jobject videoSurface{nullptr};
            jclass surfaceTextureClass{nullptr};
            jmethodID surfaceTextureUpdateTexImage{nullptr};
            jmethodID surfaceTextureGetTransformMatrix{nullptr};
            jmethodID surfaceTextureRelease{nullptr};
            jclass surfaceClass{nullptr};
            jmethodID surfaceRelease{nullptr};
            float videoTextureTransform[16]{
                1.0f, 0.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 1.0f, 0.0f,
                0.0f, 0.0f, 0.0f, 1.0f};

            GLuint vao{0};
            GLuint vbo{0};
            GLuint ibo{0};
            GLsizei indexCount{0};

            GLuint hemiVao{0};
            GLuint hemiVbo{0};
            GLuint hemiIbo{0};
            GLsizei hemiIndexCount{0};

            GLuint quadVao{0};
            GLuint quadVbo{0};
            GLuint quadIbo{0};
            GLsizei quadIndexCount{0};

            GLuint hudTexture{0};
            std::vector<uint8_t> pendingHudData;
            int pendingHudWidth{0};
            int pendingHudHeight{0};
            bool pendingHudReady{false};
            // S0961: hudTexture is born as an opaque grey 1x1 placeholder (createGlAssets). If any
            // step of the generate -> queue -> upload chain fails on-device, rendering the quad
            // exposes that placeholder as an empty grey rectangle in the middle of the view. Track
            // whether real HUD content ever reached the texture and keep the quad hidden until then
            // (render call site passes hudTex=0). Written and read on the render thread only.
            bool hudContentUploaded{false};
            std::mutex hudMutex;

            // S0986: subtitle-cue quad pipeline - mirrors the HUD pending-buffer/mutex/upload-gate
            // pattern with its OWN mutex (different cadence: per cue event, not per HUD state change).
            // subtitleContentUploaded gates the grey placeholder like hudContentUploaded; subtitleVisible
            // is toggled by cue presence (empty cue hides). Reads/writes on the render thread only.
            GLuint subtitleTexture{0};
            std::vector<uint8_t> pendingSubtitleData;
            int pendingSubtitleWidth{0};
            int pendingSubtitleHeight{0};
            bool pendingSubtitleReady{false};
            bool pendingSubtitleHide{false};
            bool subtitleContentUploaded{false};
            bool subtitleVisible{false};
            std::mutex subtitleMutex;

            GLint locViewProj{-1};
            GLint locTex{-1};
            GLint locEye{-1};
            GLint locStereoLayout{-1};
            GLint locParallaxShift{-1};
            GLint locZoomUv{-1};
            GLint videoLocViewProj{-1};
            GLint videoLocTex{-1};
            GLint videoLocEye{-1};
            GLint videoLocStereoLayout{-1};
            GLint videoLocParallaxShift{-1};
            GLint videoLocTexTransform{-1};
            GLint videoLocZoomUv{-1};
        };

        // Defined once in xr_session.cpp; every unit below sees the same object.
        extern State g;

        extern bool g_loaderInitialized;
        extern jobject g_appContextGlobal;
        extern std::atomic<float> g_currentFps;
        extern int64_t g_prevPredictedDisplayTimeNs;
        extern XrTime g_lastNavigateActionTime[2];
        extern int g_prevStickState[2];
        extern bool g_prevTriggerClicked[2];
        extern int g_navigateCounter[2];
        extern std::atomic<int> g_inputMode;
        extern int g_prevStickStateY[2];

        // Defined in xr_session_render.cpp.
        bool checkGl(const char *tag);
        bool hasGlExtension(const char *needle);
        void configureStaticTextureFiltering();
        bool uploadStaticTexturePixels(const uint8_t *rgba, int width, int height, const char *tag);
        GLuint compileShader(GLenum type, const char *src);
        GLuint linkProgram(GLuint vs, GLuint fs);
        void buildSphereMesh(std::vector<float> &verts, std::vector<unsigned int> &indices);
        void buildHemisphereMesh(std::vector<float> &verts, std::vector<unsigned int> &indices);
        void buildQuadMesh(std::vector<float> &verts, std::vector<unsigned int> &indices);
        void perspectiveFromFov(const XrFovf &fov, float nearZ, float farZ, float *m);
        void viewFromPose(const XrPosef &pose, float *m);
        void multiply4x4(const float *a, const float *b, float *out);
        void scaleAndTranslate4x4(float sx, float sy, float sz, float tx, float ty, float tz, float *out);
        void pollEvents();
        bool renderEye(size_t eyeIdx, const XrView &view, XrCompositionLayerProjectionView &outLayer);
        void pollActions(XrTime predictedTime);

        // Defined in xr_session_setup.cpp.
        NativeResult createInstance(JavaVM *vm, jobject activity);
        NativeResult createEgl();
        NativeResult bindEglSurface();
        NativeResult createSessionAndSpaces();
        NativeResult createSwapchains();
        NativeResult createGlAssets();

        // Defined in xr_session_jni.cpp.
        JNIEnv *getAttachedEnv(bool &attached);
        void clearJniException(JNIEnv *env, const char *label);
        jobject getApplicationContextLocal(JNIEnv *env, jobject activity);
        bool createVideoSurfaceObjects();
        void updateVideoTextureIfNeeded();
        void releaseVideoSurfaceObjects(JNIEnv *env);
        void triggerJniInputCallback(int eventType);
        void triggerJniRayInteraction(float uvX, float uvY, bool isHover, bool isClick);

    } // namespace detail
} // namespace fms::xr

#endif // FMS_XR_SESSION_INTERNAL_H
