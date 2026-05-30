// OpenXR diagnostic session host. Owns input, haptics, raycasting, and HUD placement.

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

namespace fms::xr {

namespace {

constexpr const char* kLogTag = "S0249.XrSession";
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, kLogTag, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  kLogTag, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, kLogTag, __VA_ARGS__)
// S0290 Phase 10 / ADR-6: race-guard only against pathological double-callback from
// xrWaitFrame on Quest 3 timewarp. Main debounce = application-side rising-edge detection
// on triggerClicked (Step 10.1) plus the Meta runtime's own hysteresis on the
// pinch_ext/ready_ext boolean input. Bumped 100 ms -> 500 ms after owner feedback round 2
// (2026-05-22): user reported "still jumping" even with stick remap + edge-detection. The
// half-second cooldown guarantees one navigation per deliberate gesture even if the stick
// drifts back across the deflection threshold during a single intentional push.
constexpr XrDuration kNavigateDebounceDuration = 500000000;

constexpr float kPI = 3.14159265358979323846f;
constexpr int   kSphereLatSegments = 32;
constexpr int   kSphereLonSegments = 64;
constexpr float kSphereRadius      = 10.0f;

constexpr const char* kVertexShader = R"GLSL(#version 300 es
precision highp float;
layout(location=0) in vec3 a_pos;
layout(location=1) in vec2 a_uv;
uniform mat4 u_viewProj;
out vec2 v_uv;
void main() {
    v_uv = a_uv;
    gl_Position = u_viewProj * vec4(a_pos, 1.0);
}
)GLSL";

constexpr const char* kFragmentShader = R"GLSL(#version 300 es
precision highp float;
in vec2 v_uv;
uniform sampler2D u_tex;
uniform int u_eyeIndex; // 0 = left, 1 = right
uniform int u_stereoLayout; // 0 = Mono, 1 = Top-Bottom, 2 = Side-by-Side
uniform float u_parallaxShift;
uniform float u_zoomUv; // S0291 round 5: zoom factor applied as UV scaling for sphere/hemi projections. 1.0 = no change. FLAT projection passes 1.0 here and scales via model matrix instead.
out vec4 outColor;
void main() {
    vec2 uv = v_uv;
    if (u_zoomUv != 1.0) {
        // Centre-anchored UV scaling: zoom > 1.0 sees a smaller portion of texture (zoom in).
        uv = (uv - vec2(0.5)) / u_zoomUv + vec2(0.5);
    }
    if (u_stereoLayout == 1) {
        uv.y = uv.y * 0.5 + (u_eyeIndex == 1 ? 0.5 : 0.0);
        uv.x += (u_eyeIndex == 0 ? -u_parallaxShift : u_parallaxShift);
    } else if (u_stereoLayout == 2) {
        uv.x = uv.x * 0.5 + (u_eyeIndex == 1 ? 0.5 : 0.0);
        uv.x += (u_eyeIndex == 0 ? -u_parallaxShift : u_parallaxShift) * 0.5;
    }
    uv = clamp(uv, vec2(0.0), vec2(1.0));
    outColor = texture(u_tex, uv);
}
)GLSL";

constexpr const char* kExternalVideoFragmentShader = R"GLSL(#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision highp float;
in vec2 v_uv;
uniform samplerExternalOES u_tex;
uniform int u_eyeIndex; // 0 = left, 1 = right
uniform int u_stereoLayout; // 0 = Mono, 1 = Top-Bottom, 2 = Side-by-Side
uniform float u_parallaxShift;
uniform mat4 u_texTransform;
uniform float u_zoomUv; // S0291 round 5: zoom factor for sphere/hemi; FLAT passes 1.0.
out vec4 outColor;
void main() {
    vec2 uv = v_uv;
    if (u_zoomUv != 1.0) {
        uv = (uv - vec2(0.5)) / u_zoomUv + vec2(0.5);
    }
    if (u_stereoLayout == 1) {
        uv.y = uv.y * 0.5 + (u_eyeIndex == 1 ? 0.5 : 0.0);
        uv.x += (u_eyeIndex == 0 ? -u_parallaxShift : u_parallaxShift);
    } else if (u_stereoLayout == 2) {
        uv.x = uv.x * 0.5 + (u_eyeIndex == 1 ? 0.5 : 0.0);
        uv.x += (u_eyeIndex == 0 ? -u_parallaxShift : u_parallaxShift) * 0.5;
    }
    uv = clamp(uv, vec2(0.0), vec2(1.0));
    // S0290 (owner feedback 2026-05-22): video shown upside-down on Quest 3 because the
    // SurfaceTexture transform from MediaCodec expects GL bottom-left convention UV, but
    // our quad mesh is bitmap top-down. Convert just before the transform so stereo math
    // above stays in bitmap convention (correct for TB / SBS half-splits) and the OES
    // sampler sees the orientation it expects. See strategic spec §5.1.B.2.
    uv.y = 1.0 - uv.y;
    vec4 transformed = u_texTransform * vec4(uv, 0.0, 1.0);
    vec4 sampled = texture(u_tex, transformed.xy);
    // S0290 ADR-5 v2: OES external returns RGB in source colorspace - the spec
    // (https://registry.khronos.org/OpenGL/extensions/OES/OES_EGL_image_external.txt)
    // says "no gamma encode or decode" happens on sample. H.264 / HEVC video out of
    // MediaCodec is BT.709 gamma-encoded RGB, and the sRGB swapchain re-encodes on
    // write. Without a decode here, the chain runs gamma TWICE -> over-bright video
    // (Big Buck Bunny visibly brighter than desktop player, observed 2026-05-22).
    // Decode to linear here so the swapchain's encode round-trips correctly.
    outColor.rgb = pow(sampled.rgb, vec3(2.2));
    outColor.a = sampled.a;
}
)GLSL";

struct SwapchainImageGL { XrSwapchainImageOpenGLESKHR image; };
struct EyeSwapchain {
    XrSwapchain handle{XR_NULL_HANDLE};
    int width{0};
    int height{0};
    std::vector<SwapchainImageGL> images;
    GLuint depthRb{0};
    GLuint fbo{0};
};

struct State {
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

    JavaVM* vm{nullptr};
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
    EGLConfig  eglConfig{nullptr};
    EGLSurface eglSurface{EGL_NO_SURFACE};
    ANativeWindow* window{nullptr};

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
        0.0f, 0.0f, 0.0f, 1.0f
    };

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
    std::mutex hudMutex;

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

State g;

// Smoothed frame rate (Hz) measured from `XrFrameState::predictedDisplayTime` deltas.
// EMA with alpha = 0.1 -> effective 10-frame window. Updated only on the render thread,
// read atomically by `xr_session_get_fps()` from any thread.
std::atomic<float> g_currentFps{0.0f};
// Previous predicted display time (nanoseconds, monotonic). 0 marks "no previous frame yet".
int64_t g_prevPredictedDisplayTimeNs{0};
XrTime g_lastNavigateActionTime[2] = {0, 0};
// S0290 Phase 10 (revised 2026-05-22 by owner): navigation moved off trigger/pinch
// (those collide with Quest 3 system gestures like screenshot) to the thumbstick X axis.
// Per-hand last-known stick deflection state: -1 = pushed left, 0 = neutral, +1 = right.
// A transition 0 -> ±1 is the navigate event; we re-arm when stick returns to neutral.
// triggerClicked is preserved as the kept-as-default-mapping signal for ray interaction
// (HUD click) — only the prev/next navigation is moved to the stick.
int g_prevStickState[2] = {0, 0};
bool g_prevTriggerClicked[2] = {false, false};
// S0290 Phase 10 Step 10.3: running count of accepted navigations per hand for in-log
// self-diagnosis. The count appears in the LOGD navigation line so the operator can
// distinguish "system saw 5 distinct gestures" vs "system saw 1 gesture but I think I
// did 5". Reset alongside the time stamps in xr_session_start.
int g_navigateCounter[2] = {0, 0};

bool checkGl(const char* tag) {
    GLenum e = glGetError();
    if (e != GL_NO_ERROR) { LOGE("GL error at %s: 0x%x", tag, e); return false; }
    return true;
}

bool hasGlExtension(const char* needle) {
    const char* extensions = reinterpret_cast<const char*>(glGetString(GL_EXTENSIONS));
    return extensions && std::strstr(extensions, needle) != nullptr;
}

void configureStaticTextureFiltering() {
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    if (hasGlExtension("GL_EXT_texture_filter_anisotropic")) {
        GLfloat maxAnisotropy = 1.0f;
        glGetFloatv(GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT, &maxAnisotropy);
        const GLfloat chosen = std::fmin(maxAnisotropy, 8.0f);
        glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAX_ANISOTROPY_EXT, chosen);
        LOGD("static texture anisotropy enabled: %.1f", chosen);
    }
}

bool uploadStaticTexturePixels(const uint8_t* rgba, int width, int height, const char* tag) {
    glBindTexture(GL_TEXTURE_2D, g.texture);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
    glGenerateMipmap(GL_TEXTURE_2D);
    return checkGl(tag);
}

GLuint compileShader(GLenum type, const char* src) {
    GLuint s = glCreateShader(type);
    glShaderSource(s, 1, &src, nullptr);
    glCompileShader(s);
    GLint status = 0; glGetShaderiv(s, GL_COMPILE_STATUS, &status);
    if (!status) {
        char log[1024]; GLsizei n = 0; glGetShaderInfoLog(s, sizeof(log), &n, log);
        LOGE("Shader compile failed: %.*s", (int)n, log); glDeleteShader(s); return 0;
    }
    return s;
}

GLuint linkProgram(GLuint vs, GLuint fs) {
    GLuint p = glCreateProgram();
    glAttachShader(p, vs); glAttachShader(p, fs); glLinkProgram(p);
    GLint status = 0; glGetProgramiv(p, GL_LINK_STATUS, &status);
    if (!status) {
        char log[1024]; GLsizei n = 0; glGetProgramInfoLog(p, sizeof(log), &n, log);
        LOGE("Program link failed: %.*s", (int)n, log); glDeleteProgram(p); return 0;
    }
    return p;
}

JNIEnv* getAttachedEnv(bool& attached) {
    attached = false;
    if (!g.vm) return nullptr;
    JNIEnv* env = nullptr;
    jint res = g.vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (res == JNI_EDETACHED) {
        if (g.vm->AttachCurrentThread(&env, nullptr) == JNI_OK) {
            attached = true;
        }
    }
    return env;
}

void clearJniException(JNIEnv* env, const char* label) {
    if (!env || !env->ExceptionCheck()) return;
    env->ExceptionDescribe();
    env->ExceptionClear();
    LOGW("%s threw; cleared JNI exception", label);
}

bool createVideoSurfaceObjects() {
    if (!g.vm || g.videoTexture != 0 || g.videoSurfaceTexture || g.videoSurface) return true;

    glGenTextures(1, &g.videoTexture);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, g.videoTexture);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    if (!checkGl("createVideoSurfaceObjects.texture")) return false;

    bool attached = false;
    JNIEnv* env = getAttachedEnv(attached);
    if (!env) return false;

    jclass localSurfaceTextureClass = env->FindClass("android/graphics/SurfaceTexture");
    if (!localSurfaceTextureClass) {
        clearJniException(env, "FindClass(SurfaceTexture)");
        if (attached) g.vm->DetachCurrentThread();
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
    if (!localSurfaceTexture) {
        if (attached) g.vm->DetachCurrentThread();
        return false;
    }
    g.videoSurfaceTexture = env->NewGlobalRef(localSurfaceTexture);

    jclass localSurfaceClass = env->FindClass("android/view/Surface");
    if (!localSurfaceClass) {
        clearJniException(env, "FindClass(Surface)");
        env->DeleteLocalRef(localSurfaceTexture);
        if (attached) g.vm->DetachCurrentThread();
        return false;
    }
    g.surfaceClass = static_cast<jclass>(env->NewGlobalRef(localSurfaceClass));
    jmethodID surfaceCtor = env->GetMethodID(localSurfaceClass, "<init>", "(Landroid/graphics/SurfaceTexture;)V");
    g.surfaceRelease = env->GetMethodID(localSurfaceClass, "release", "()V");
    jobject localSurface = surfaceCtor ? env->NewObject(localSurfaceClass, surfaceCtor, localSurfaceTexture) : nullptr;
    clearJniException(env, "Surface.<init>");
    env->DeleteLocalRef(localSurfaceClass);
    env->DeleteLocalRef(localSurfaceTexture);
    if (!localSurface) {
        if (attached) g.vm->DetachCurrentThread();
        return false;
    }
    g.videoSurface = env->NewGlobalRef(localSurface);
    env->DeleteLocalRef(localSurface);

    if (attached) g.vm->DetachCurrentThread();
    LOGD("native video surface created");
    return g.videoSurfaceTexture && g.videoSurface;
}

void updateVideoTextureIfNeeded() {
    if (!g.videoTextureEnabled.load(std::memory_order_relaxed) || !g.videoSurfaceTexture) return;
    bool attached = false;
    JNIEnv* env = getAttachedEnv(attached);
    if (!env) return;

    env->CallVoidMethod(g.videoSurfaceTexture, g.surfaceTextureUpdateTexImage);
    if (env->ExceptionCheck()) {
        clearJniException(env, "SurfaceTexture.updateTexImage");
        if (attached) g.vm->DetachCurrentThread();
        return;
    }

    jfloatArray matrix = env->NewFloatArray(16);
    if (matrix) {
        env->CallVoidMethod(g.videoSurfaceTexture, g.surfaceTextureGetTransformMatrix, matrix);
        if (!env->ExceptionCheck()) {
            env->GetFloatArrayRegion(matrix, 0, 16, g.videoTextureTransform);
        } else {
            clearJniException(env, "SurfaceTexture.getTransformMatrix");
        }
        env->DeleteLocalRef(matrix);
    }

    if (attached) g.vm->DetachCurrentThread();
}

void releaseVideoSurfaceObjects(JNIEnv* env) {
    if (!env) return;
    if (g.videoSurface && g.surfaceRelease) {
        env->CallVoidMethod(g.videoSurface, g.surfaceRelease);
        clearJniException(env, "Surface.release");
        env->DeleteGlobalRef(g.videoSurface);
        g.videoSurface = nullptr;
    }
    if (g.videoSurfaceTexture && g.surfaceTextureRelease) {
        env->CallVoidMethod(g.videoSurfaceTexture, g.surfaceTextureRelease);
        clearJniException(env, "SurfaceTexture.release");
        env->DeleteGlobalRef(g.videoSurfaceTexture);
        g.videoSurfaceTexture = nullptr;
    }
    if (g.surfaceTextureClass) {
        env->DeleteGlobalRef(g.surfaceTextureClass);
        g.surfaceTextureClass = nullptr;
    }
    if (g.surfaceClass) {
        env->DeleteGlobalRef(g.surfaceClass);
        g.surfaceClass = nullptr;
    }
    g.surfaceTextureUpdateTexImage = nullptr;
    g.surfaceTextureGetTransformMatrix = nullptr;
    g.surfaceTextureRelease = nullptr;
    g.surfaceRelease = nullptr;
}

void buildSphereMesh(std::vector<float>& verts, std::vector<unsigned int>& indices) {
    verts.clear(); indices.clear();
    const int lat = kSphereLatSegments;
    const int lon = kSphereLonSegments;
    verts.reserve((lat + 1) * (lon + 1) * 5);
    for (int y = 0; y <= lat; ++y) {
        float v = (float)y / (float)lat;
        float theta = v * kPI;
        float sinT = std::sin(theta), cosT = std::cos(theta);
        for (int x = 0; x <= lon; ++x) {
            float u = (float)x / (float)lon;
            // S0291 owner round 3 fix (2026-05-22 21:26): owner observed "LEFT" text rendered
            // as "TFEL" (horizontally mirrored) on diagnostic_360_stereo_tb.jpg. Root cause:
            // for sphere viewed from inside, the azimuth-vs-texture-U direction was inverted —
            // as user turned LEFT, texture U DECREASED instead of INCREASED, producing a
            // mirror image. Same bug was present on bundled landscape pano but masked because
            // the lake scene has no readable text. Reverse phi direction so texture U=0
            // appears behind user and increases counter-clockwise (standard equirect viewer
            // convention) — front-of-user now at u=0.25 (was 0.75) and turning RIGHT scrolls
            // toward larger u.
            float phi = (1.0f - u) * 2.0f * kPI;
            float sinP = std::sin(phi), cosP = std::cos(phi);
            float px = -sinT * cosP * kSphereRadius;
            float py = cosT * kSphereRadius;
            float pz = sinT * sinP * kSphereRadius;
            verts.push_back(px); verts.push_back(py); verts.push_back(pz);
            verts.push_back(u);  verts.push_back(v);
        }
    }
    indices.reserve(lat * lon * 6);
    for (int y = 0; y < lat; ++y) {
        for (int x = 0; x < lon; ++x) {
            unsigned int i0 = y * (lon + 1) + x;
            unsigned int i1 = i0 + 1;
            unsigned int i2 = i0 + (lon + 1);
            unsigned int i3 = i2 + 1;
            indices.push_back(i0); indices.push_back(i2); indices.push_back(i1);
            indices.push_back(i1); indices.push_back(i2); indices.push_back(i3);
        }
    }
}

void buildHemisphereMesh(std::vector<float>& verts, std::vector<unsigned int>& indices) {
    verts.clear(); indices.clear();
    const int lat = kSphereLatSegments;
    const int lon = kSphereLonSegments;
    verts.reserve((lat + 1) * (lon + 1) * 5);
    for (int y = 0; y <= lat; ++y) {
        float v = (float)y / (float)lat;
        float theta = v * kPI;
        float sinT = std::sin(theta), cosT = std::cos(theta);
        for (int x = 0; x <= lon; ++x) {
            float u = (float)x / (float)lon;
            // Same U-axis mirror fix as sphere (see comment above). For the 180° forward
            // hemisphere, phi spans pi..2pi originally — reverse so the texture's natural
            // left-to-right reads correctly when viewed from inside the half-shell.
            float phi = kPI + (1.0f - u) * kPI;
            float sinP = std::sin(phi), cosP = std::cos(phi);
            float px = -sinT * cosP * kSphereRadius;
            float py = cosT * kSphereRadius;
            float pz = sinT * sinP * kSphereRadius;
            verts.push_back(px); verts.push_back(py); verts.push_back(pz);
            verts.push_back(u);  verts.push_back(v);
        }
    }
    indices.reserve(lat * lon * 6);
    for (int y = 0; y < lat; ++y) {
        for (int x = 0; x < lon; ++x) {
            unsigned int i0 = y * (lon + 1) + x;
            unsigned int i1 = i0 + 1;
            unsigned int i2 = i0 + (lon + 1);
            unsigned int i3 = i2 + 1;
            indices.push_back(i0); indices.push_back(i2); indices.push_back(i1);
            indices.push_back(i1); indices.push_back(i2); indices.push_back(i3);
        }
    }
}

void buildQuadMesh(std::vector<float>& verts, std::vector<unsigned int>& indices) {
    verts = {
        -0.5f,  0.5f, 0.0f,  0.0f, 0.0f,
        -0.5f, -0.5f, 0.0f,  0.0f, 1.0f,
         0.5f, -0.5f, 0.0f,  1.0f, 1.0f,
         0.5f,  0.5f, 0.0f,  1.0f, 0.0f
    };
    indices = {
        0, 1, 2,
        0, 2, 3
    };
}
void perspectiveFromFov(const XrFovf& fov, float nearZ, float farZ, float* m) {
    const float tanL = std::tan(fov.angleLeft);
    const float tanR = std::tan(fov.angleRight);
    const float tanU = std::tan(fov.angleUp);
    const float tanD = std::tan(fov.angleDown);
    const float w = tanR - tanL;
    const float h = tanU - tanD;
    std::memset(m, 0, sizeof(float) * 16);
    m[0]  = 2.0f / w;
    m[5]  = 2.0f / h;
    m[8]  = (tanR + tanL) / w;
    m[9]  = (tanU + tanD) / h;
    m[10] = -(farZ + nearZ) / (farZ - nearZ);
    m[11] = -1.0f;
    m[14] = -(2.0f * farZ * nearZ) / (farZ - nearZ);
}

// Keep R^T in column-major order; otherwise the world rotates with the head.
void viewFromPose(const XrPosef& pose, float* m) {
    const float x = pose.orientation.x, y = pose.orientation.y, z = pose.orientation.z, w = pose.orientation.w;
    const float xx = x*x, yy = y*y, zz = z*z;
    const float xy = x*y, xz = x*z, yz = y*z;
    const float wx = w*x, wy = w*y, wz = w*z;
    float r[9] = {
        1 - 2*(yy+zz),     2*(xy+wz),       2*(xz-wy),
        2*(xy-wz),         1 - 2*(xx+zz),   2*(yz+wx),
        2*(xz+wy),         2*(yz-wx),       1 - 2*(xx+yy)
    };
    const float tx = pose.position.x, ty = pose.position.y, tz = pose.position.z;
    m[0] = r[0]; m[1] = r[3]; m[2] = r[6]; m[3] = 0;
    m[4] = r[1]; m[5] = r[4]; m[6] = r[7]; m[7] = 0;
    m[8] = r[2]; m[9] = r[5]; m[10] = r[8]; m[11] = 0;
    m[12] = -(r[0]*tx + r[1]*ty + r[2]*tz);
    m[13] = -(r[3]*tx + r[4]*ty + r[5]*tz);
    m[14] = -(r[6]*tx + r[7]*ty + r[8]*tz);
    m[15] = 1;
}

void multiply4x4(const float* a, const float* b, float* out) {
    for (int c = 0; c < 4; ++c) for (int r = 0; r < 4; ++r) {
        out[c*4+r] = a[0*4+r]*b[c*4+0] + a[1*4+r]*b[c*4+1] + a[2*4+r]*b[c*4+2] + a[3*4+r]*b[c*4+3];
    }
}

bool hasInstanceExtension(const std::vector<XrExtensionProperties>& props, const char* target) {
    for (const auto& prop : props) {
        if (std::strcmp(prop.extensionName, target) == 0) return true;
    }
    return false;
}

void logInstanceExtensionSupport() {
    uint32_t count = 0;
    XrResult r = xrEnumerateInstanceExtensionProperties(nullptr, 0, &count, nullptr);
    if (XR_FAILED(r)) {
        LOGW("xrEnumerateInstanceExtensionProperties(count)=%d", (int)r);
        return;
    }
    std::vector<XrExtensionProperties> props(count);
    for (auto& prop : props) {
        prop.type = XR_TYPE_EXTENSION_PROPERTIES;
        prop.next = nullptr;
    }
    r = xrEnumerateInstanceExtensionProperties(nullptr, count, &count, props.data());
    if (XR_FAILED(r)) {
        LOGW("xrEnumerateInstanceExtensionProperties(list)=%d", (int)r);
        return;
    }
    LOGD(
        "instance extensions: count=%u android_create=%d opengles_enable=%d",
        count,
        hasInstanceExtension(props, XR_KHR_ANDROID_CREATE_INSTANCE_EXTENSION_NAME) ? 1 : 0,
        hasInstanceExtension(props, XR_KHR_OPENGL_ES_ENABLE_EXTENSION_NAME) ? 1 : 0
    );
}

NativeResult createInstance(JavaVM* vm, jobject activity) {
    LOGD("createInstance: begin vm=%p activity=%p", (void*)vm, activity);
    logInstanceExtensionSupport();

    PFN_xrInitializeLoaderKHR initializeLoader = nullptr;
    XrResult r = xrGetInstanceProcAddr(
        XR_NULL_HANDLE,
        "xrInitializeLoaderKHR",
        reinterpret_cast<PFN_xrVoidFunction*>(&initializeLoader)
    );
    if (XR_FAILED(r) || initializeLoader == nullptr) {
        LOGE(
            "xrGetInstanceProcAddr(xrInitializeLoaderKHR)=%d ptr=%p",
            (int)r,
            reinterpret_cast<void*>(initializeLoader)
        );
        return NativeResult::InstanceCreationFailed;
    }
    XrLoaderInitInfoAndroidKHR loaderInfo{XR_TYPE_LOADER_INIT_INFO_ANDROID_KHR};
    loaderInfo.applicationVM = vm;
    loaderInfo.applicationContext = activity;
    r = initializeLoader(reinterpret_cast<const XrLoaderInitInfoBaseHeaderKHR*>(&loaderInfo));
    if (XR_FAILED(r)) {
        LOGE("xrInitializeLoaderKHR=%d", (int)r);
        return NativeResult::InstanceCreationFailed;
    }
    LOGD("xrInitializeLoaderKHR ok");

    std::vector<const char*> exts = {
        XR_KHR_ANDROID_CREATE_INSTANCE_EXTENSION_NAME,
        XR_KHR_OPENGL_ES_ENABLE_EXTENSION_NAME,
        XR_EXT_HAND_TRACKING_EXTENSION_NAME,
        XR_EXT_HAND_INTERACTION_EXTENSION_NAME,
        XR_FB_HAND_TRACKING_AIM_EXTENSION_NAME
    };
    XrInstanceCreateInfoAndroidKHR androidInfo{XR_TYPE_INSTANCE_CREATE_INFO_ANDROID_KHR};
    androidInfo.applicationVM = vm;
    androidInfo.applicationActivity = activity;

    XrInstanceCreateInfo info{XR_TYPE_INSTANCE_CREATE_INFO};
    info.next = &androidInfo;
    info.enabledExtensionCount = (uint32_t)exts.size();
    info.enabledExtensionNames = exts.data();
    std::snprintf(info.applicationInfo.applicationName, sizeof(info.applicationInfo.applicationName), "FastMediaSorter-Diag");
    info.applicationInfo.applicationVersion = 1;
    std::snprintf(info.applicationInfo.engineName, sizeof(info.applicationInfo.engineName), "FastMediaSorter");
    info.applicationInfo.engineVersion = 1;
    info.applicationInfo.apiVersion = XR_CURRENT_API_VERSION;
    LOGD("xrCreateInstance: enabling %zu extensions", exts.size());
    r = xrCreateInstance(&info, &g.instance);
    if (XR_FAILED(r)) { LOGE("xrCreateInstance=%d", (int)r); return NativeResult::InstanceCreationFailed; }

    XrSystemGetInfo sysInfo{XR_TYPE_SYSTEM_GET_INFO};
    sysInfo.formFactor = XR_FORM_FACTOR_HEAD_MOUNTED_DISPLAY;
    r = xrGetSystem(g.instance, &sysInfo, &g.systemId);
    if (XR_FAILED(r) || g.systemId == XR_NULL_SYSTEM_ID) {
        LOGW("xrGetSystem=%d", (int)r); return NativeResult::SystemNotFound;
    }
    LOGD("instance ok, systemId=%llu", (unsigned long long)g.systemId);
    return NativeResult::Ok;
}

NativeResult createEgl() {
    g.eglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (g.eglDisplay == EGL_NO_DISPLAY) { LOGE("eglGetDisplay failed"); return NativeResult::SessionCreationFailed; }
    EGLint major = 0, minor = 0;
    if (!eglInitialize(g.eglDisplay, &major, &minor)) { LOGE("eglInitialize failed"); return NativeResult::SessionCreationFailed; }
    const EGLint cfgAttribs[] = {
        EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8,
        EGL_DEPTH_SIZE, 24, EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT_KHR,
        EGL_SURFACE_TYPE, EGL_PBUFFER_BIT | EGL_WINDOW_BIT, EGL_NONE
    };
    EGLint numConfigs = 0;
    if (!eglChooseConfig(g.eglDisplay, cfgAttribs, &g.eglConfig, 1, &numConfigs) || numConfigs == 0) {
        LOGE("eglChooseConfig failed"); return NativeResult::SessionCreationFailed;
    }
    const EGLint ctxAttribs[] = { EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE };
    g.eglContext = eglCreateContext(g.eglDisplay, g.eglConfig, EGL_NO_CONTEXT, ctxAttribs);
    if (g.eglContext == EGL_NO_CONTEXT) { LOGE("eglCreateContext failed"); return NativeResult::SessionCreationFailed; }
    LOGD("EGL ready: %d.%d", major, minor);
    return NativeResult::Ok;
}

NativeResult bindEglSurface() {
    if (!g.window) {
        const EGLint pbAttribs[] = { EGL_WIDTH, 16, EGL_HEIGHT, 16, EGL_NONE };
        g.eglSurface = eglCreatePbufferSurface(g.eglDisplay, g.eglConfig, pbAttribs);
    } else {
        g.eglSurface = eglCreateWindowSurface(g.eglDisplay, g.eglConfig, g.window, nullptr);
    }
    if (g.eglSurface == EGL_NO_SURFACE) { LOGE("eglCreate*Surface failed (0x%x)", eglGetError()); return NativeResult::SessionCreationFailed; }
    if (!eglMakeCurrent(g.eglDisplay, g.eglSurface, g.eglSurface, g.eglContext)) {
        LOGE("eglMakeCurrent failed"); return NativeResult::SessionCreationFailed;
    }
    LOGD("EGL surface bound (window=%p)", (void*)g.window);
    return NativeResult::Ok;
}

NativeResult createSessionAndSpaces() {
    PFN_xrGetOpenGLESGraphicsRequirementsKHR pfnGetReq = nullptr;
    xrGetInstanceProcAddr(g.instance, "xrGetOpenGLESGraphicsRequirementsKHR", (PFN_xrVoidFunction*)&pfnGetReq);
    if (pfnGetReq) {
        XrGraphicsRequirementsOpenGLESKHR req{XR_TYPE_GRAPHICS_REQUIREMENTS_OPENGL_ES_KHR};
        pfnGetReq(g.instance, g.systemId, &req);
    }

    XrGraphicsBindingOpenGLESAndroidKHR binding{XR_TYPE_GRAPHICS_BINDING_OPENGL_ES_ANDROID_KHR};
    binding.display = g.eglDisplay;
    binding.config  = g.eglConfig;
    binding.context = g.eglContext;

    XrSessionCreateInfo sci{XR_TYPE_SESSION_CREATE_INFO};
    sci.next = &binding;
    sci.systemId = g.systemId;
    XrResult r = xrCreateSession(g.instance, &sci, &g.session);
    if (XR_FAILED(r)) { LOGE("xrCreateSession=%d", (int)r); return NativeResult::SessionCreationFailed; }



    XrReferenceSpaceCreateInfo rsci{XR_TYPE_REFERENCE_SPACE_CREATE_INFO};
    rsci.referenceSpaceType = XR_REFERENCE_SPACE_TYPE_LOCAL;
    rsci.poseInReferenceSpace.orientation.w = 1.0f;
    r = xrCreateReferenceSpace(g.session, &rsci, &g.localSpace);
    if (XR_FAILED(r)) { LOGE("xrCreateReferenceSpace=%d", (int)r); return NativeResult::SessionCreationFailed; }

    uint32_t viewCount = 0;
    xrEnumerateViewConfigurationViews(g.instance, g.systemId, XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO, 0, &viewCount, nullptr);
    g.viewConfigs.assign(viewCount, {XR_TYPE_VIEW_CONFIGURATION_VIEW});
    xrEnumerateViewConfigurationViews(g.instance, g.systemId, XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO, viewCount, &viewCount, g.viewConfigs.data());
    LOGD("View config: %u views", viewCount);
    return NativeResult::Ok;
}

NativeResult createSwapchains() {
    uint32_t fmtCount = 0;
    xrEnumerateSwapchainFormats(g.session, 0, &fmtCount, nullptr);
    std::vector<int64_t> fmts(fmtCount);
    xrEnumerateSwapchainFormats(g.session, fmtCount, &fmtCount, fmts.data());
    int64_t chosen = GL_RGBA8;
    for (auto f : fmts) { if (f == GL_SRGB8_ALPHA8) { chosen = GL_SRGB8_ALPHA8; break; } }

    g.eyes.resize(g.viewConfigs.size());
    for (size_t i = 0; i < g.viewConfigs.size(); ++i) {
        const auto& vc = g.viewConfigs[i];
        EyeSwapchain& eye = g.eyes[i];
        eye.width  = (int)vc.recommendedImageRectWidth;
        eye.height = (int)vc.recommendedImageRectHeight;

        XrSwapchainCreateInfo sci{XR_TYPE_SWAPCHAIN_CREATE_INFO};
        sci.usageFlags = XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT | XR_SWAPCHAIN_USAGE_SAMPLED_BIT;
        sci.format = chosen; sci.sampleCount = 1; sci.width = vc.recommendedImageRectWidth; sci.height = vc.recommendedImageRectHeight;
        sci.faceCount = 1; sci.arraySize = 1; sci.mipCount = 1;
        XrResult r = xrCreateSwapchain(g.session, &sci, &eye.handle);
        if (XR_FAILED(r)) { LOGE("xrCreateSwapchain[%zu]=%d", i, (int)r); return NativeResult::SwapchainCreationFailed; }

        uint32_t imageCount = 0;
        xrEnumerateSwapchainImages(eye.handle, 0, &imageCount, nullptr);
        eye.images.assign(imageCount, {{XR_TYPE_SWAPCHAIN_IMAGE_OPENGL_ES_KHR}});
        xrEnumerateSwapchainImages(eye.handle, imageCount, &imageCount, (XrSwapchainImageBaseHeader*)eye.images.data());

        glGenRenderbuffers(1, &eye.depthRb);
        glBindRenderbuffer(GL_RENDERBUFFER, eye.depthRb);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, eye.width, eye.height);
        glGenFramebuffers(1, &eye.fbo);
        LOGD("Eye %zu swapchain: %dx%d, images=%u, fmt=0x%llx", i, eye.width, eye.height, imageCount, (long long)chosen);
    }
    return NativeResult::Ok;
}

NativeResult createGlAssets() {
    GLuint vs = compileShader(GL_VERTEX_SHADER, kVertexShader);
    GLuint fs = compileShader(GL_FRAGMENT_SHADER, kFragmentShader);
    if (!vs || !fs) return NativeResult::SessionCreationFailed;
    g.program = linkProgram(vs, fs);
    GLuint videoFs = compileShader(GL_FRAGMENT_SHADER, kExternalVideoFragmentShader);
    if (videoFs) {
        g.videoProgram = linkProgram(vs, videoFs);
        glDeleteShader(videoFs);
    }
    glDeleteShader(vs); glDeleteShader(fs);
    if (!g.program) return NativeResult::SessionCreationFailed;
    if (!g.videoProgram) return NativeResult::SessionCreationFailed;
    g.locViewProj     = glGetUniformLocation(g.program, "u_viewProj");
    g.locTex          = glGetUniformLocation(g.program, "u_tex");
    g.locEye          = glGetUniformLocation(g.program, "u_eyeIndex");
    g.locStereoLayout = glGetUniformLocation(g.program, "u_stereoLayout");
    g.locParallaxShift = glGetUniformLocation(g.program, "u_parallaxShift");
    g.locZoomUv = glGetUniformLocation(g.program, "u_zoomUv");
    g.videoLocViewProj = glGetUniformLocation(g.videoProgram, "u_viewProj");
    g.videoLocTex = glGetUniformLocation(g.videoProgram, "u_tex");
    g.videoLocEye = glGetUniformLocation(g.videoProgram, "u_eyeIndex");
    g.videoLocStereoLayout = glGetUniformLocation(g.videoProgram, "u_stereoLayout");
    g.videoLocParallaxShift = glGetUniformLocation(g.videoProgram, "u_parallaxShift");
    g.videoLocTexTransform = glGetUniformLocation(g.videoProgram, "u_texTransform");
    g.videoLocZoomUv = glGetUniformLocation(g.videoProgram, "u_zoomUv");

    std::vector<float> verts; std::vector<unsigned int> indices;

    buildSphereMesh(verts, indices);
    g.indexCount = (GLsizei)indices.size();
    glGenVertexArrays(1, &g.vao); glBindVertexArray(g.vao);
    glGenBuffers(1, &g.vbo); glBindBuffer(GL_ARRAY_BUFFER, g.vbo);
    glBufferData(GL_ARRAY_BUFFER, verts.size() * sizeof(float), verts.data(), GL_STATIC_DRAW);
    glGenBuffers(1, &g.ibo); glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, g.ibo);
    glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices.size() * sizeof(unsigned int), indices.data(), GL_STATIC_DRAW);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 5 * sizeof(float), (void*)0);
    glEnableVertexAttribArray(1);
    glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, 5 * sizeof(float), (void*)(3 * sizeof(float)));
    glBindVertexArray(0);

    buildHemisphereMesh(verts, indices);
    g.hemiIndexCount = (GLsizei)indices.size();
    glGenVertexArrays(1, &g.hemiVao); glBindVertexArray(g.hemiVao);
    glGenBuffers(1, &g.hemiVbo); glBindBuffer(GL_ARRAY_BUFFER, g.hemiVbo);
    glBufferData(GL_ARRAY_BUFFER, verts.size() * sizeof(float), verts.data(), GL_STATIC_DRAW);
    glGenBuffers(1, &g.hemiIbo); glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, g.hemiIbo);
    glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices.size() * sizeof(unsigned int), indices.data(), GL_STATIC_DRAW);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 5 * sizeof(float), (void*)0);
    glEnableVertexAttribArray(1);
    glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, 5 * sizeof(float), (void*)(3 * sizeof(float)));
    glBindVertexArray(0);

    buildQuadMesh(verts, indices);
    g.quadIndexCount = (GLsizei)indices.size();
    glGenVertexArrays(1, &g.quadVao); glBindVertexArray(g.quadVao);
    glGenBuffers(1, &g.quadVbo); glBindBuffer(GL_ARRAY_BUFFER, g.quadVbo);
    glBufferData(GL_ARRAY_BUFFER, verts.size() * sizeof(float), verts.data(), GL_STATIC_DRAW);
    glGenBuffers(1, &g.quadIbo); glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, g.quadIbo);
    glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices.size() * sizeof(unsigned int), indices.data(), GL_STATIC_DRAW);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 5 * sizeof(float), (void*)0);
    glEnableVertexAttribArray(1);
    glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, 5 * sizeof(float), (void*)(3 * sizeof(float)));
    glBindVertexArray(0);

    glGenTextures(1, &g.texture);
    glBindTexture(GL_TEXTURE_2D, g.texture);
    configureStaticTextureFiltering();
    uint8_t pixel[4] = { 64, 64, 64, 255 };
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
    glGenerateMipmap(GL_TEXTURE_2D);

    glGenTextures(1, &g.hudTexture);
    glBindTexture(GL_TEXTURE_2D, g.hudTexture);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixel);

    if (!createVideoSurfaceObjects()) return NativeResult::SessionCreationFailed;

    return checkGl("createGlAssets") ? NativeResult::Ok : NativeResult::SessionCreationFailed;
}

void scaleAndTranslate4x4(float sx, float sy, float sz, float tx, float ty, float tz, float* out) {
    std::memset(out, 0, sizeof(float) * 16);
    out[0] = sx;
    out[5] = sy;
    out[10] = sz;
    out[12] = tx;
    out[13] = ty;
    out[14] = tz;
    out[15] = 1.0f;
}

void triggerJniInputCallback(int eventType) {
    if (!g.vm || !g.activity) return;
    JNIEnv* env = nullptr;
    bool attached = false;
    jint res = g.vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (res == JNI_EDETACHED) {
        if (g.vm->AttachCurrentThread(&env, nullptr) == JNI_OK) {
            attached = true;
        }
    }
    if (env && g.activity) {
        jclass clazz = env->GetObjectClass(g.activity);
        if (clazz) {
            jmethodID method = env->GetMethodID(clazz, "onNativeInputEvent", "(I)V");
            if (method) {
                env->CallVoidMethod(g.activity, method, eventType);
            }
            env->DeleteLocalRef(clazz);
        }
    }
    if (attached) {
        g.vm->DetachCurrentThread();
    }
}

void triggerJniRayInteraction(float uvX, float uvY, bool isHover, bool isClick) {
    if (!g.vm || !g.activity) return;
    JNIEnv* env = nullptr;
    bool attached = false;
    jint res = g.vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (res == JNI_EDETACHED) {
        if (g.vm->AttachCurrentThread(&env, nullptr) == JNI_OK) {
            attached = true;
        }
    }
    if (env && g.activity) {
        jclass clazz = env->GetObjectClass(g.activity);
        if (clazz) {
            jmethodID method = env->GetMethodID(clazz, "onNativeRayInteraction", "(FFZZ)V");
            if (method) {
                env->CallVoidMethod(g.activity, method, uvX, uvY, isHover ? JNI_TRUE : JNI_FALSE, isClick ? JNI_TRUE : JNI_FALSE);
            }
            env->DeleteLocalRef(clazz);
        }
    }
    if (attached) {
        g.vm->DetachCurrentThread();
    }
}



void pollEvents() {
    XrEventDataBuffer ev{XR_TYPE_EVENT_DATA_BUFFER};
    while (xrPollEvent(g.instance, &ev) == XR_SUCCESS) {
        switch (ev.type) {
            case XR_TYPE_EVENT_DATA_SESSION_STATE_CHANGED: {
                auto* st = reinterpret_cast<XrEventDataSessionStateChanged*>(&ev);
                g.sessionState = st->state;
                LOGD("session state -> %d", (int)st->state);
                if (st->state == XR_SESSION_STATE_READY) {
                    XrSessionBeginInfo bi{XR_TYPE_SESSION_BEGIN_INFO};
                    bi.primaryViewConfigurationType = XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO;
                    xrBeginSession(g.session, &bi); g.sessionRunning = true;
                } else if (st->state == XR_SESSION_STATE_STOPPING) {
                    xrEndSession(g.session); g.sessionRunning = false;
                } else if (st->state == XR_SESSION_STATE_EXITING || st->state == XR_SESSION_STATE_LOSS_PENDING) {
                    g.exitRequested.store(true);
                }
                break;
            }
            case XR_TYPE_EVENT_DATA_INSTANCE_LOSS_PENDING:
                g.exitRequested.store(true); break;
            default: break;
        }
        ev = {XR_TYPE_EVENT_DATA_BUFFER};
    }
}

bool renderEye(size_t eyeIdx, const XrView& view, XrCompositionLayerProjectionView& outLayer) {
    EyeSwapchain& eye = g.eyes[eyeIdx];
    uint32_t imgIdx = 0;
    XrSwapchainImageAcquireInfo ai{XR_TYPE_SWAPCHAIN_IMAGE_ACQUIRE_INFO};
    if (XR_FAILED(xrAcquireSwapchainImage(eye.handle, &ai, &imgIdx))) return false;
    XrSwapchainImageWaitInfo wi{XR_TYPE_SWAPCHAIN_IMAGE_WAIT_INFO}; wi.timeout = XR_INFINITE_DURATION;
    if (XR_FAILED(xrWaitSwapchainImage(eye.handle, &wi))) return false;

    glBindFramebuffer(GL_FRAMEBUFFER, eye.fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, eye.images[imgIdx].image.image, 0);
    glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, eye.depthRb);
    glViewport(0, 0, eye.width, eye.height);
    glClearColor(0.05f, 0.05f, 0.08f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

    float proj[16]; perspectiveFromFov(view.fov, 0.05f, 100.0f, proj);
    float viewMat[16]; viewFromPose(view.pose, viewMat);

    float mvp[16];
    if (g.renderProjection == 2) {
        // S0291 round 5: zoom for FLAT projection applied as proportional scale of the quad
        // (width AND height multiplied by g.zoom). Quad stays at z=-5 so depth cues match the
        // rest of the scene; only apparent size changes. This is the natural interpretation of
        // "image приближается / удаляется" for a movie screen.
        const float flatScale = g.zoom;
        float modelMat[16];
        scaleAndTranslate4x4(8.0f * flatScale, 4.5f * flatScale, 1.0f, 0.0f, 0.0f, -5.0f, modelMat);
        float temp[16];
        multiply4x4(viewMat, modelMat, temp);
        multiply4x4(proj, temp, mvp);
    } else {
        multiply4x4(proj, viewMat, mvp);
    }

    GLuint activeVao = g.vao;
    GLsizei activeIndexCount = g.indexCount;
    if (g.renderProjection == 1) {
        activeVao = g.hemiVao;
        activeIndexCount = g.hemiIndexCount;
    } else if (g.renderProjection == 2) {
        activeVao = g.quadVao;
        activeIndexCount = g.quadIndexCount;
    }

    const bool useVideoTexture =
        g.videoTextureEnabled.load(std::memory_order_relaxed) &&
        g.videoTexture != 0 &&
        g.videoProgram != 0;

    GLuint program = useVideoTexture ? g.videoProgram : g.program;
    GLint locViewProj = useVideoTexture ? g.videoLocViewProj : g.locViewProj;
    GLint locTex = useVideoTexture ? g.videoLocTex : g.locTex;
    GLint locEye = useVideoTexture ? g.videoLocEye : g.locEye;
    GLint locStereoLayout = useVideoTexture ? g.videoLocStereoLayout : g.locStereoLayout;
    GLint locParallaxShift = useVideoTexture ? g.videoLocParallaxShift : g.locParallaxShift;
    GLint locZoomUv = useVideoTexture ? g.videoLocZoomUv : g.locZoomUv;

    glEnable(GL_DEPTH_TEST); glDisable(GL_CULL_FACE);
    glUseProgram(program);
    glUniformMatrix4fv(locViewProj, 1, GL_FALSE, mvp);
    glActiveTexture(GL_TEXTURE0);
    if (useVideoTexture) {
        glBindTexture(GL_TEXTURE_EXTERNAL_OES, g.videoTexture);
        if (g.videoLocTexTransform >= 0) {
            glUniformMatrix4fv(g.videoLocTexTransform, 1, GL_FALSE, g.videoTextureTransform);
        }
    } else {
        glBindTexture(GL_TEXTURE_2D, g.texture);
    }
    glUniform1i(locTex, 0);
    glUniform1i(locEye, (GLint)eyeIdx);
    glUniform1i(locStereoLayout, g.stereoLayout);
    if (locParallaxShift >= 0) glUniform1f(locParallaxShift, g.parallaxShift);
    // S0291 round 5: zoom applies as UV scaling for 360 / 180 sphere/hemi projections only.
    // FLAT projection scales via model matrix instead (built above) and the shader gets 1.0
    // so the FLAT quad's UV range stays exact.
    const float effectiveZoomUv = (g.renderProjection == 2) ? 1.0f : g.zoom;
    if (locZoomUv >= 0) glUniform1f(locZoomUv, effectiveZoomUv);
    glBindVertexArray(activeVao);
    glDrawElements(GL_TRIANGLES, activeIndexCount, GL_UNSIGNED_INT, nullptr);
    glBindVertexArray(0);

    // Render World Space HUD Quad, pointer rays, and low-latency cursor dots (Phase 02)
    xr_hud_render(proj, viewMat, eyeIdx, g.program, g.quadVao, g.hudTexture, g.locViewProj, g.locTex, g.locEye, g.locStereoLayout);

    glBindFramebuffer(GL_FRAMEBUFFER, 0);

    XrSwapchainImageReleaseInfo ri{XR_TYPE_SWAPCHAIN_IMAGE_RELEASE_INFO};
    xrReleaseSwapchainImage(eye.handle, &ri);

    outLayer = {XR_TYPE_COMPOSITION_LAYER_PROJECTION_VIEW};
    outLayer.pose = view.pose; outLayer.fov = view.fov;
    outLayer.subImage.swapchain = eye.handle;
    outLayer.subImage.imageRect.offset = {0, 0};
    outLayer.subImage.imageRect.extent = {eye.width, eye.height};
    return true;
}

void pollActions(XrTime predictedTime) {
    if (!g.sessionRunning) return;

    xr_input_poll(g.localSpace, predictedTime);

    // S0290 Phase 10 (owner feedback round 2 2026-05-22): tighten thresholds to combat
    // over-sensitivity reports — user sees "still jumping" even with edge-detection because
    // a 0.6 deflect is easy to hit incidentally on Quest 3 sticks. Bumped to 0.85 (firm push
    // required) and re-arm to ±0.40 (clear release required). Race-guard duration upgraded
    // to a navigation-cooldown of 500 ms (kNavigateDebounceDuration) so a quick re-flick
    // cannot fire a second navigation within half a second of the previous one. Triggers
    // remain free for Quest 3 system gestures (screenshot, recenter).
    constexpr float kStickDeflectThreshold = 0.85f;
    constexpr float kStickReturnThreshold = 0.40f;
    for (int hand = 0; hand < 2; ++hand) {
        const float x = g_handInputStates[hand].thumbstickX;
        int newState = g_prevStickState[hand];
        if (g_prevStickState[hand] == 0) {
            if (x >  kStickDeflectThreshold) newState = +1;
            else if (x < -kStickDeflectThreshold) newState = -1;
        } else {
            if (std::abs(x) < kStickReturnThreshold) newState = 0;
        }
        if (newState != g_prevStickState[hand] && newState != 0) {
            const bool raceGuardOk =
                g_lastNavigateActionTime[hand] == 0 ||
                predictedTime - g_lastNavigateActionTime[hand] >= kNavigateDebounceDuration;
            if (raceGuardOk) {
                g_lastNavigateActionTime[hand] = predictedTime;
                if (newState > 0) {
                    LOGD("hand=%d thumbstick deflect right -> navigating next (count=%d, x=%.2f)",
                         hand, ++g_navigateCounter[hand], x);
                    triggerJniInputCallback(1); // 1 = Next
                } else {
                    LOGD("hand=%d thumbstick deflect left -> navigating prev (count=%d, x=%.2f)",
                         hand, ++g_navigateCounter[hand], x);
                    triggerJniInputCallback(2); // 2 = Previous
                }
            }
        }
        g_prevStickState[hand] = newState;
        // Track trigger rising edge purely to keep the field reset in sync; no nav action.
        g_prevTriggerClicked[hand] = g_handInputStates[hand].triggerClicked;
    }

    // S0291 owner round 5 (2026-05-22 22:32): thumbstick-Y drives zoom on the immersive
    // image. Stick pulled toward the user (y < 0 in OpenXR thumbstick convention) zooms IN
    // (image appears closer / larger). Stick pushed away (y > 0) zooms OUT. Either hand
    // contributes. Deadzone 0.2 keeps neutral; outside deadzone we accumulate at a rate
    // proportional to deflection and clamp to a sane range [0.3, 3.0].
    constexpr float kZoomDeadzone = 0.2f;
    constexpr float kZoomPerFrame = 0.012f; // ~1.2% size change at full deflection per frame; smooth at 72 Hz.
    constexpr float kZoomMin = 0.3f;
    constexpr float kZoomMax = 3.0f;
    float zoomDelta = 0.0f;
    for (int hand = 0; hand < 2; ++hand) {
        const float y = g_handInputStates[hand].thumbstickY;
        if (std::abs(y) > kZoomDeadzone) {
            zoomDelta += -y * kZoomPerFrame;
        }
    }
    if (zoomDelta != 0.0f) {
        std::lock_guard<std::mutex> lock(g.mutex);
        float nz = g.zoom + zoomDelta;
        if (nz < kZoomMin) nz = kZoomMin;
        if (nz > kZoomMax) nz = kZoomMax;
        g.zoom = nz;
    }
}

} // namespace

bool xr_session_is_running() { return g.running.load(); }

bool xr_session_is_initialized() {
    std::lock_guard<std::mutex> lock(g.mutex);
    // S0291 owner round 4 (2026-05-22 21:51): "initialized" now means "session is active",
    // not "instance exists". g.instance and g.eglContext are intentionally process-scoped
    // (see xr_session_init / xr_session_shutdown comments) and persist across exit/re-enter.
    // A fresh session is detected by g.session being XR_NULL_HANDLE.
    return g.session != XR_NULL_HANDLE;
}

jobject_opaque g_activity_jobject() {
    std::lock_guard<std::mutex> lock(g.mutex);
    return static_cast<jobject_opaque>(g.activity);
}

void xr_session_clear_activity_jobject() {
    std::lock_guard<std::mutex> lock(g.mutex);
    g.activity = nullptr;
}

NativeResult xr_session_init(JavaVM* vm, jobject_opaque activity) {
    std::lock_guard<std::mutex> lock(g.mutex);
    if (g.running.load()) {
        LOGW("xr_session_init: already running; rejecting");
        return NativeResult::AlreadyRunning;
    }
    g.vm = vm;
    g.activity = static_cast<jobject>(activity);
    // S0291 owner round 4 (2026-05-22 21:51): re-entry crashed with CheckJNI abort inside
    // libopenxr_loader.so::xrEnumerateInstanceExtensionProperties (logcat 21:45 line 430-445
    // stack trace). Quest 3's OpenXR loader holds an internal JNI global ref to the Activity
    // it received during xrInitializeLoaderKHR; that ref is NOT released when we call
    // xrDestroyInstance. After ANY shutdown, the loader's cached ref races with ART CheckJNI
    // and the next xrEnumerate* call (re-init) aborts the whole process. Mitigation: keep
    // the XrInstance + EGL context alive for the WHOLE process lifetime, only destroy the
    // per-session pieces (session handle, swapchains, GL objects, hud, input) in shutdown.
    // This matches the standard OpenXR pattern — instance is process-scoped, session is
    // immersive-scoped. Logged transitions so we can verify the path in the next test.
    LOGD("xr_session_init: g.instance=%p g.eglContext=%p (will reuse if non-null)",
         (void*)g.instance, (void*)g.eglContext);
    NativeResult r;
    if (g.instance == XR_NULL_HANDLE) {
        LOGD("xr_session_init: creating XrInstance (cold start)");
        r = createInstance(vm, static_cast<jobject>(activity));
        if (r != NativeResult::Ok) {
            LOGE("xr_session_init: createInstance failed -> %d", (int)r);
            return r;
        }
    } else {
        LOGD("xr_session_init: reusing existing XrInstance %p", (void*)g.instance);
    }
    if (g.eglContext == EGL_NO_CONTEXT) {
        LOGD("xr_session_init: creating EGL context (cold start)");
        r = createEgl();
        if (r != NativeResult::Ok) {
            LOGE("xr_session_init: createEgl failed -> %d", (int)r);
            return r;
        }
    } else {
        LOGD("xr_session_init: reusing existing EGL context %p", (void*)g.eglContext);
    }
    return NativeResult::Ok;
}

NativeResult xr_session_attach_surface(ANativeWindow* window) {
    std::lock_guard<std::mutex> lock(g.mutex);
    g.window = window;
    NativeResult r = bindEglSurface();
    return r;
}

NativeResult xr_session_start() {
    std::lock_guard<std::mutex> lock(g.mutex);
    if (g.session != XR_NULL_HANDLE) return NativeResult::AlreadyRunning;
    NativeResult r = createSessionAndSpaces(); if (r != NativeResult::Ok) return r;
    r = createSwapchains();                    if (r != NativeResult::Ok) return r;
    r = createGlAssets();                      if (r != NativeResult::Ok) return r;
    xr_input_init(g.instance, g.session);
    xr_hud_init();
    g.running.store(true);
    g.exitRequested.store(false);
    // Reset FPS accumulator so a previous session's value does not bleed into the new one.
    g_currentFps.store(0.0f, std::memory_order_relaxed);
    g_prevPredictedDisplayTimeNs = 0;
    g_lastNavigateActionTime[0] = 0;
    g_lastNavigateActionTime[1] = 0;
    // S0290 Phase 10: clear per-hand edge-detection snapshot and per-hand counters so a
    // freshly started session does not inherit stale state from the previous one.
    g_prevStickState[0] = 0;
    g_prevStickState[1] = 0;
    g_prevTriggerClicked[0] = false;
    g_prevTriggerClicked[1] = false;
    // S0291 round 5: reset zoom on each new session so re-enter starts at 1.0 (no carry-over).
    g.zoom = 1.0f;
    g_navigateCounter[0] = 0;
    g_navigateCounter[1] = 0;
    LOGD("xr_session_start: complete");
    return NativeResult::Ok;
}

NativeResult xr_session_upload_texture(const uint8_t* rgba, int width, int height) {
    std::lock_guard<std::mutex> lock(g.mutex);
    if (g.texture == 0 || !rgba || width <= 0 || height <= 0) return NativeResult::NotRunning;
    if (!uploadStaticTexturePixels(rgba, width, height, "upload_texture")) return NativeResult::FramePresentFailed;
    LOGD("texture uploaded: %dx%d", width, height);
    return NativeResult::Ok;
}

NativeResult xr_session_queue_frame(const uint8_t* rgba, int width, int height) {
    if (!rgba || width <= 0 || height <= 0) return NativeResult::UnexpectedRuntimeError;
    std::lock_guard<std::mutex> lock(g.frameMutex);
    size_t size = width * height * 4;
    g.pendingFrameData.assign(rgba, rgba + size);
    g.pendingFrameWidth = width;
    g.pendingFrameHeight = height;
    g.pendingFrameReady = true;
    return NativeResult::Ok;
}

jobject_opaque xr_session_get_video_surface() {
    return static_cast<jobject_opaque>(g.videoSurface);
}

void xr_session_set_video_surface_enabled(bool enabled) {
    g.videoTextureEnabled.store(enabled, std::memory_order_relaxed);
}

void xr_session_set_render_config(int projection, int layout) {
    std::lock_guard<std::mutex> lock(g.mutex);
    g.renderProjection = projection;
    g.stereoLayout = layout;
    // S0291 owner round 8 (2026-05-22 22:53): reset zoom to 1.0 on every media
    // switch so each new slide starts at default framing ("при переключении на
    // следующую картинку/видео приближение/удаление сбрасывается к норме").
    g.zoom = 1.0f;
    LOGD("set_render_config: projection=%d, layout=%d, zoom reset to 1.0", projection, layout);
}

void xr_session_set_parallax_shift(float value) {
    std::lock_guard<std::mutex> lock(g.mutex);
    float clamped = value < 0.0f ? 0.0f : (value > 1.0f ? 1.0f : value);
    g.parallaxShift = (clamped - 0.5f) * 0.04f;
}

void xr_session_queue_hud(const uint8_t* rgba, int width, int height) {
    if (!rgba || width <= 0 || height <= 0) {
        LOGW("xr_session_queue_hud REJECTED: rgba=%p w=%d h=%d", rgba, width, height);
        return;
    }
    std::lock_guard<std::mutex> lock(g.hudMutex);
    size_t size = width * height * 4;
    g.pendingHudData.assign(rgba, rgba + size);
    g.pendingHudWidth = width;
    g.pendingHudHeight = height;
    g.pendingHudReady = true;
    LOGD("xr_session_queue_hud STORED %dx%d (%zu bytes); first pixel RGBA=%d,%d,%d,%d",
         width, height, size, (int)rgba[0], (int)rgba[1], (int)rgba[2], (int)rgba[3]);
}


NativeResult xr_session_run_frame_loop() {
    if (!g.running.load() || g.session == XR_NULL_HANDLE) return NativeResult::NotRunning;
    LOGD("frame loop entered");
    while (!g.exitRequested.load()) {
        pollEvents();
        if (!g.sessionRunning) {
            // Throttle while waiting for SESSION_STATE_READY.
            continue;
        }
        XrFrameWaitInfo fwi{XR_TYPE_FRAME_WAIT_INFO};
        XrFrameState fs{XR_TYPE_FRAME_STATE};
        if (XR_FAILED(xrWaitFrame(g.session, &fwi, &fs))) break;
        XrFrameBeginInfo fbi{XR_TYPE_FRAME_BEGIN_INFO};
        xrBeginFrame(g.session, &fbi);

        // Smoothed FPS sampler: derive instantaneous frame rate from predictedDisplayTime delta,
        // feed an EMA. `predictedDisplayTime` is monotonic in nanoseconds (XrTime).
        {
            const int64_t now = static_cast<int64_t>(fs.predictedDisplayTime);
            if (g_prevPredictedDisplayTimeNs != 0 && now > g_prevPredictedDisplayTimeNs) {
                const double dtSec = static_cast<double>(now - g_prevPredictedDisplayTimeNs) * 1e-9;
                if (dtSec > 1e-6 && dtSec < 1.0) {
                    const float instantaneousFps = static_cast<float>(1.0 / dtSec);
                    const float prev = g_currentFps.load(std::memory_order_relaxed);
                    const float alpha = 0.1f;
                    const float smoothed = (prev <= 0.0f)
                            ? instantaneousFps
                            : alpha * instantaneousFps + (1.0f - alpha) * prev;
                    g_currentFps.store(smoothed, std::memory_order_relaxed);
                }
            }
            g_prevPredictedDisplayTimeNs = now;
        }

        pollActions(fs.predictedDisplayTime);

        {
            std::lock_guard<std::mutex> lock(g.frameMutex);
            if (g.pendingFrameReady) {
                uploadStaticTexturePixels(
                    g.pendingFrameData.data(),
                    g.pendingFrameWidth,
                    g.pendingFrameHeight,
                    "pending_frame_upload");
                g.pendingFrameReady = false;
            }
        }
        {
            std::lock_guard<std::mutex> lock(g.hudMutex);
            if (g.pendingHudReady) {
                glBindTexture(GL_TEXTURE_2D, g.hudTexture);
                glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, g.pendingHudWidth, g.pendingHudHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, g.pendingHudData.data());
                LOGD("hud upload: %dx%d to texture=%u", g.pendingHudWidth, g.pendingHudHeight, g.hudTexture);
                g.pendingHudReady = false;
            }
        }

        std::vector<XrCompositionLayerProjectionView> layerViews;
        XrCompositionLayerProjection layer{XR_TYPE_COMPOSITION_LAYER_PROJECTION};
        layer.space = g.localSpace;

        if (fs.shouldRender && g.viewConfigs.size() == g.eyes.size() && !g.eyes.empty()) {
            XrViewLocateInfo vli{XR_TYPE_VIEW_LOCATE_INFO};
            vli.viewConfigurationType = XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO;
            vli.displayTime = fs.predictedDisplayTime; vli.space = g.localSpace;
            XrViewState vs{XR_TYPE_VIEW_STATE};
            uint32_t viewCount = 0;
            std::vector<XrView> views(g.viewConfigs.size(), {XR_TYPE_VIEW});
            if (XR_SUCCEEDED(xrLocateViews(g.session, &vli, &vs, (uint32_t)views.size(), &viewCount, views.data())) &&
                (vs.viewStateFlags & XR_VIEW_STATE_POSITION_VALID_BIT) &&
                (vs.viewStateFlags & XR_VIEW_STATE_ORIENTATION_VALID_BIT)) {
                if (viewCount > 0) {
                    xr_hud_update(views[0].pose, 0.013f);
                    xr_hud_process_rays(g.localSpace, fs.predictedDisplayTime);

                    // Step 03.1: Stream interaction data from C++ render loop up to JVM
                    bool anyHover = false;
                    float targetUvX = 0.5f;
                    float targetUvY = 0.5f;
                    bool anyClick = false;
                    for (int i = 0; i < 2; i++) {
                        if (g_hudState.hasIntersection[i]) {
                            anyHover = true;
                            targetUvX = g_hudState.smoothedUv[i].x;
                            targetUvY = g_hudState.smoothedUv[i].y;
                            if (g_handInputStates[i].triggerDown) {
                                anyClick = true;
                            }
                            break;
                        }
                    }
                    triggerJniRayInteraction(targetUvX, targetUvY, anyHover, anyClick);
                }
                layerViews.resize(viewCount);
                updateVideoTextureIfNeeded();
                for (uint32_t i = 0; i < viewCount; ++i) renderEye(i, views[i], layerViews[i]);
                layer.viewCount = (uint32_t)layerViews.size();
                layer.views = layerViews.data();
            }
        }

        XrCompositionLayerBaseHeader* layers[1] = { (XrCompositionLayerBaseHeader*)&layer };
        XrFrameEndInfo fei{XR_TYPE_FRAME_END_INFO};
        fei.displayTime = fs.predictedDisplayTime;
        fei.environmentBlendMode = XR_ENVIRONMENT_BLEND_MODE_OPAQUE;
        fei.layerCount = layer.viewCount > 0 ? 1 : 0;
        fei.layers = layer.viewCount > 0 ? layers : nullptr;
        xrEndFrame(g.session, &fei);
    }
    LOGD("frame loop exited");
    return NativeResult::Ok;
}

void xr_session_request_exit() {
    g.exitRequested.store(true);
}

float xr_session_get_fps() {
    return g_currentFps.load(std::memory_order_relaxed);
}

void xr_session_shutdown() {
    std::lock_guard<std::mutex> lock(g.mutex);
    LOGD("xr_session_shutdown: begin (instance=%p session=%p activity=%p)",
         (void*)g.instance, (void*)g.session, (void*)g.activity);
    // Drop running flag FIRST so any concurrent render-thread iteration observes the change
    // and exits the frame loop before we tear down GL/EGL/OpenXR resources underneath it.
    g.running.store(false);
    g.exitRequested.store(true);
    bool attached = false;
    JNIEnv* env = getAttachedEnv(attached);
    releaseVideoSurfaceObjects(env);
    if (attached && g.vm) g.vm->DetachCurrentThread();
    g.videoTextureEnabled.store(false, std::memory_order_relaxed);

    for (auto& eye : g.eyes) {
        if (eye.fbo) glDeleteFramebuffers(1, &eye.fbo);
        if (eye.depthRb) glDeleteRenderbuffers(1, &eye.depthRb);
        if (eye.handle != XR_NULL_HANDLE) xrDestroySwapchain(eye.handle);
    }
    g.eyes.clear();
    if (g.texture)    { glDeleteTextures(1, &g.texture);    g.texture = 0; }
    if (g.videoTexture) { glDeleteTextures(1, &g.videoTexture); g.videoTexture = 0; }
    if (g.hudTexture) { glDeleteTextures(1, &g.hudTexture);  g.hudTexture = 0; }
    
    if (g.vbo)        { glDeleteBuffers(1, &g.vbo);         g.vbo = 0; }
    if (g.ibo)        { glDeleteBuffers(1, &g.ibo);         g.ibo = 0; }
    if (g.vao)        { glDeleteVertexArrays(1, &g.vao);    g.vao = 0; }
    
    if (g.hemiVbo)    { glDeleteBuffers(1, &g.hemiVbo);     g.hemiVbo = 0; }
    if (g.hemiIbo)    { glDeleteBuffers(1, &g.hemiIbo);     g.hemiIbo = 0; }
    if (g.hemiVao)    { glDeleteVertexArrays(1, &g.hemiVao); g.hemiVao = 0; }
    
    if (g.quadVbo)    { glDeleteBuffers(1, &g.quadVbo);     g.quadVbo = 0; }
    if (g.quadIbo)    { glDeleteBuffers(1, &g.quadIbo);     g.quadIbo = 0; }
    if (g.quadVao)    { glDeleteVertexArrays(1, &g.quadVao); g.quadVao = 0; }
    
    if (g.program)    { glDeleteProgram(g.program);         g.program = 0; }
    if (g.videoProgram) { glDeleteProgram(g.videoProgram);  g.videoProgram = 0; }

    xr_input_shutdown();
    xr_hud_shutdown();
    if (g.localSpace != XR_NULL_HANDLE)      { xrDestroySpace(g.localSpace);      g.localSpace = XR_NULL_HANDLE; }
    if (g.session != XR_NULL_HANDLE)         { xrDestroySession(g.session);       g.session = XR_NULL_HANDLE; }
    // S0291 owner round 4 (2026-05-22 21:51): keep XrInstance, XrSystemId, view configs,
    // and the EGL context alive for the process lifetime. See xr_session_init for the
    // rationale (Quest 3 OpenXR loader holds its own JNI globalref to Activity that does
    // NOT survive re-create cleanly). We DO destroy the per-session EGLSurface because it
    // is bound to a per-Activity ANativeWindow that legitimately changes across sessions.
    LOGD("xr_session_shutdown: preserving XrInstance %p, EGL context %p across exit",
         (void*)g.instance, (void*)g.eglContext);
    if (g.eglSurface != EGL_NO_SURFACE && g.eglDisplay != EGL_NO_DISPLAY) {
        eglMakeCurrent(g.eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        eglDestroySurface(g.eglDisplay, g.eglSurface); g.eglSurface = EGL_NO_SURFACE;
    }
    if (g.window) { ANativeWindow_release(g.window); g.window = nullptr; }
    g.sessionRunning = false;
    g.sessionState = XR_SESSION_STATE_UNKNOWN;
    // g.running already false (set at function top). Reset frame buffers too so a fresh
    // session does not inherit stale pendingFrameReady from the previous one.
    {
        std::lock_guard<std::mutex> fl(g.frameMutex);
        g.pendingFrameReady = false;
        g.pendingFrameData.clear();
        g.pendingFrameWidth = 0;
        g.pendingFrameHeight = 0;
    }
    {
        std::lock_guard<std::mutex> hl(g.hudMutex);
        g.pendingHudReady = false;
        g.pendingHudData.clear();
        g.pendingHudWidth = 0;
        g.pendingHudHeight = 0;
    }

    // S0291 owner round 3 (2026-05-22 21:19): do NOT DeleteGlobalRef on g.activity at
    // shutdown. The Activity global ref must persist across the immersive enter/exit
    // boundary for the lifetime of the process — see the load-bearing rationale in
    // diagnostic_xr_runtime.cpp::nativeInitSession (CheckJNI "stale reference with
    // serial number" crash on re-entry when the ref was deleted between sessions).
    // g.activity is reused by the JNI bridge on next nativeInitSession via IsSameObject;
    // it is released only if a truly different Activity instance arrives, or implicitly
    // when the process dies. g.vm is cleared because it has no associated allocation.
    g.vm = nullptr;
    LOGD("xr_session_shutdown: complete (activity globalref intentionally retained for process lifetime)");
}

} // namespace fms::xr
