// OpenXR diagnostic session host. S0283 owns input, haptics, raycasting, and HUD placement.

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
#include <openxr/openxr.h>
#include <openxr/openxr_platform.h>

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
out vec4 outColor;
void main() {
    vec2 uv = v_uv;
    if (u_stereoLayout == 1) {
        uv.y = uv.y * 0.5 + (u_eyeIndex == 1 ? 0.5 : 0.0);
        uv.x += (u_eyeIndex == 0 ? -u_parallaxShift : u_parallaxShift);
    } else if (u_stereoLayout == 2) {
        uv.x = uv.x * 0.5 + (u_eyeIndex == 1 ? 0.5 : 0.0);
        uv.x += (u_eyeIndex == 0 ? -u_parallaxShift : u_parallaxShift) * 0.5;
    }
    uv.x = clamp(uv.x, 0.0, 1.0);
    outColor = texture(u_tex, uv);
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
};

State g;

// Smoothed frame rate (Hz) measured from `XrFrameState::predictedDisplayTime` deltas.
// EMA with alpha = 0.1 -> effective 10-frame window. Updated only on the render thread,
// read atomically by `xr_session_get_fps()` from any thread.
std::atomic<float> g_currentFps{0.0f};
// Previous predicted display time (nanoseconds, monotonic). 0 marks "no previous frame yet".
int64_t g_prevPredictedDisplayTimeNs{0};

bool checkGl(const char* tag) {
    GLenum e = glGetError();
    if (e != GL_NO_ERROR) { LOGE("GL error at %s: 0x%x", tag, e); return false; }
    return true;
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
            float phi = u * 2.0f * kPI;
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
            float phi = kPI + u * kPI;
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
    glDeleteShader(vs); glDeleteShader(fs);
    if (!g.program) return NativeResult::SessionCreationFailed;
    g.locViewProj     = glGetUniformLocation(g.program, "u_viewProj");
    g.locTex          = glGetUniformLocation(g.program, "u_tex");
    g.locEye          = glGetUniformLocation(g.program, "u_eyeIndex");
    g.locStereoLayout = glGetUniformLocation(g.program, "u_stereoLayout");
    g.locParallaxShift = glGetUniformLocation(g.program, "u_parallaxShift");

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
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    uint8_t pixel[4] = { 64, 64, 64, 255 };
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixel);

    glGenTextures(1, &g.hudTexture);
    glBindTexture(GL_TEXTURE_2D, g.hudTexture);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixel);

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
        float modelMat[16];
        scaleAndTranslate4x4(8.0f, 4.5f, 1.0f, 0.0f, 0.0f, -5.0f, modelMat);
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

    glEnable(GL_DEPTH_TEST); glDisable(GL_CULL_FACE);
    glUseProgram(g.program);
    glUniformMatrix4fv(g.locViewProj, 1, GL_FALSE, mvp);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, g.texture);
    glUniform1i(g.locTex, 0);
    glUniform1i(g.locEye, (GLint)eyeIdx);
    glUniform1i(g.locStereoLayout, g.stereoLayout);
    if (g.locParallaxShift >= 0) glUniform1f(g.locParallaxShift, g.parallaxShift);
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

    if (g_handInputStates[0].active && g_handInputStates[0].triggerClicked) {
        LOGD("S0283: Left select / pinch triggered -> navigating prev");
        triggerJniInputCallback(2); // 2 = Previous
    }
    if (g_handInputStates[1].active && g_handInputStates[1].triggerClicked) {
        LOGD("S0283: Right select / pinch triggered -> navigating next");
        triggerJniInputCallback(1); // 1 = Next
    }
}

} // namespace

bool xr_session_is_running() { return g.running.load(); }

NativeResult xr_session_init(JavaVM* vm, jobject_opaque activity) {
    std::lock_guard<std::mutex> lock(g.mutex);
    if (g.running.load()) return NativeResult::AlreadyRunning;
    g.vm = vm;
    g.activity = static_cast<jobject>(activity);
    NativeResult r = createInstance(vm, static_cast<jobject>(activity));
    if (r != NativeResult::Ok) return r;
    r = createEgl();
    if (r != NativeResult::Ok) return r;
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
    LOGD("xr_session_start: complete");
    return NativeResult::Ok;
}

NativeResult xr_session_upload_texture(const uint8_t* rgba, int width, int height) {
    std::lock_guard<std::mutex> lock(g.mutex);
    if (g.texture == 0 || !rgba || width <= 0 || height <= 0) return NativeResult::NotRunning;
    glBindTexture(GL_TEXTURE_2D, g.texture);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
    if (!checkGl("upload_texture")) return NativeResult::FramePresentFailed;
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

void xr_session_set_render_config(int projection, int layout) {
    std::lock_guard<std::mutex> lock(g.mutex);
    g.renderProjection = projection;
    g.stereoLayout = layout;
    LOGD("set_render_config: projection=%d, layout=%d", projection, layout);
}

void xr_session_set_parallax_shift(float value) {
    std::lock_guard<std::mutex> lock(g.mutex);
    float clamped = value < 0.0f ? 0.0f : (value > 1.0f ? 1.0f : value);
    g.parallaxShift = (clamped - 0.5f) * 0.04f;
}

void xr_session_queue_hud(const uint8_t* rgba, int width, int height) {
    if (!rgba || width <= 0 || height <= 0) return;
    std::lock_guard<std::mutex> lock(g.hudMutex);
    size_t size = width * height * 4;
    g.pendingHudData.assign(rgba, rgba + size);
    g.pendingHudWidth = width;
    g.pendingHudHeight = height;
    g.pendingHudReady = true;
    LOGD("queue_hud: size=%dx%d queued", width, height);
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
                glBindTexture(GL_TEXTURE_2D, g.texture);
                glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, g.pendingFrameWidth, g.pendingFrameHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, g.pendingFrameData.data());
                g.pendingFrameReady = false;
            }
        }
        {
            std::lock_guard<std::mutex> lock(g.hudMutex);
            if (g.pendingHudReady) {
                glBindTexture(GL_TEXTURE_2D, g.hudTexture);
                glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, g.pendingHudWidth, g.pendingHudHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, g.pendingHudData.data());
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
    for (auto& eye : g.eyes) {
        if (eye.fbo) glDeleteFramebuffers(1, &eye.fbo);
        if (eye.depthRb) glDeleteRenderbuffers(1, &eye.depthRb);
        if (eye.handle != XR_NULL_HANDLE) xrDestroySwapchain(eye.handle);
    }
    g.eyes.clear();
    if (g.texture)    { glDeleteTextures(1, &g.texture);    g.texture = 0; }
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

    xr_input_shutdown();
    xr_hud_shutdown();
    if (g.localSpace != XR_NULL_HANDLE)      { xrDestroySpace(g.localSpace);      g.localSpace = XR_NULL_HANDLE; }
    if (g.session != XR_NULL_HANDLE)         { xrDestroySession(g.session);       g.session = XR_NULL_HANDLE; }
    if (g.instance != XR_NULL_HANDLE)        { xrDestroyInstance(g.instance);     g.instance = XR_NULL_HANDLE; }
    if (g.eglSurface != EGL_NO_SURFACE && g.eglDisplay != EGL_NO_DISPLAY) {
        eglMakeCurrent(g.eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        eglDestroySurface(g.eglDisplay, g.eglSurface); g.eglSurface = EGL_NO_SURFACE;
    }
    if (g.eglContext != EGL_NO_CONTEXT) { eglDestroyContext(g.eglDisplay, g.eglContext); g.eglContext = EGL_NO_CONTEXT; }
    if (g.eglDisplay != EGL_NO_DISPLAY) { eglTerminate(g.eglDisplay); g.eglDisplay = EGL_NO_DISPLAY; }
    if (g.window) { ANativeWindow_release(g.window); g.window = nullptr; }
    g.systemId = XR_NULL_SYSTEM_ID;
    g.viewConfigs.clear();
    g.sessionRunning = false;
    g.sessionState = XR_SESSION_STATE_UNKNOWN;
    g.running.store(false);
    
    if (g.vm && g.activity) {
        JNIEnv* env = nullptr;
        if (g.vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_OK && env) {
            env->DeleteGlobalRef(g.activity);
        }
        g.activity = nullptr;
        g.vm = nullptr;
    }
    LOGD("session shutdown complete");
}

} // namespace fms::xr
