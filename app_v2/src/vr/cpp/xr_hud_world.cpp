// World-space HUD implementation.

#include "xr_hud_world.h"
#include "xr_input.h"
#include <android/log.h>
#include <cmath>
#include <vector>

namespace fms::xr {

namespace {
constexpr const char* kLogTag = "DiagnosticXrHud";
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, kLogTag, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, kLogTag, __VA_ARGS__)

GLuint g_colorProgram{0};
GLint  g_locColorViewProj{-1};
GLint  g_locColorVec{-1};
GLuint g_lineVao{0};
GLuint g_lineVbo{0};
bool g_prevGripDown[2] = {false, false};
XrTime g_lastGripClickTime[2] = {0, 0};

XrVector3f rotate_vector(const XrQuaternionf& q, const XrVector3f& v) {
    XrVector3f q_vec{q.x, q.y, q.z};
    XrVector3f cross1{
        q_vec.y * v.z - q_vec.z * v.y,
        q_vec.z * v.x - q_vec.x * v.z,
        q_vec.x * v.y - q_vec.y * v.x
    };
    XrVector3f temp{
        cross1.x + q.w * v.x,
        cross1.y + q.w * v.y,
        cross1.z + q.w * v.z
    };
    XrVector3f cross2{
        q_vec.y * temp.z - q_vec.z * temp.y,
        q_vec.z * temp.x - q_vec.x * temp.z,
        q_vec.x * temp.y - q_vec.y * temp.x
    };
    return XrVector3f{
        v.x + 2.0f * cross2.x,
        v.y + 2.0f * cross2.y,
        v.z + 2.0f * cross2.z
    };
}

void matrix_from_pose(const XrVector3f& t, const XrQuaternionf& q, const XrVector3f& scale, float* m) {
    float xx = q.x * q.x; float xy = q.x * q.y; float xz = q.x * q.z; float xw = q.x * q.w;
    float yy = q.y * q.y; float yz = q.y * q.z; float yw = q.y * q.w;
    float zz = q.z * q.z; float zw = q.z * q.w;

    m[0]  = (1.0f - 2.0f * (yy + zz)) * scale.x;
    m[1]  = (2.0f * (xy + zw)) * scale.x;
    m[2]  = (2.0f * (xz - yw)) * scale.x;
    m[3]  = 0.0f;

    m[4]  = (2.0f * (xy - zw)) * scale.y;
    m[5]  = (1.0f - 2.0f * (xx + zz)) * scale.y;
    m[6]  = (2.0f * (yz + xw)) * scale.y;
    m[7]  = 0.0f;

    m[8]  = (2.0f * (xz + yw)) * scale.z;
    m[9]  = (2.0f * (yz - xw)) * scale.z;
    m[10] = (1.0f - 2.0f * (xx + yy)) * scale.z;
    m[11] = 0.0f;

    m[12] = t.x;
    m[13] = t.y;
    m[14] = t.z;
    m[15] = 1.0f;
}

// S0290 (owner round 3 - HUD invisibility fix 2026-05-22 16:35): the previous version of
// this helper computed B*A instead of A*B on column-major data, because it used row-major
// indexing `temp[i*4+j] = sum a[i*4+k] * b[k*4+j]`. For column-major matrices (which matrix_from_pose
// emits, and which OpenGL expects via glUniformMatrix4fv transpose=GL_FALSE), the correct
// indexing is `out[col*4+row] = sum a[k*4+row] * b[col*4+k]`. Bug masked itself: native logs
// confirmed queueHud + upload + xr_hud_render fired with correct quad.center / texture id,
// but the HUD quad never appeared in headset because the wrong MVP placed the geometry far
// outside the user FOV. xr_session.cpp::multiply4x4 already does the correct math; this is
// the same formula relocated locally so xr_hud_world stays self-contained.
void multiply_matrices(const float* a, const float* b, float* r) {
    float temp[16];
    for (int c = 0; c < 4; ++c) {
        for (int row = 0; row < 4; ++row) {
            temp[c * 4 + row] = a[0 * 4 + row] * b[c * 4 + 0] +
                                a[1 * 4 + row] * b[c * 4 + 1] +
                                a[2 * 4 + row] * b[c * 4 + 2] +
                                a[3 * 4 + row] * b[c * 4 + 3];
        }
    }
    std::memcpy(r, temp, 16 * sizeof(float));
}

GLuint compile_local_shader(GLenum type, const char* src) {
    GLuint sh = glCreateShader(type);
    glShaderSource(sh, 1, &src, nullptr);
    glCompileShader(sh);
    GLint ok = 0;
    glGetShaderiv(sh, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[512];
        glGetShaderInfoLog(sh, sizeof(log), nullptr, log);
        LOGE("HUD Local Shader compilation failed: %s", log);
        glDeleteShader(sh);
        return 0;
    }
    return sh;
}

GLuint link_local_program(GLuint vs, GLuint fs) {
    GLuint p = glCreateProgram();
    glAttachShader(p, vs);
    glAttachShader(p, fs);
    glLinkProgram(p);
    GLint ok = 0;
    glGetProgramiv(p, GL_LINK_STATUS, &ok);
    if (!ok) {
        char log[512];
        glGetProgramInfoLog(p, sizeof(log), nullptr, log);
        LOGE("HUD Local Program link failed: %s", log);
        glDeleteProgram(p);
        return 0;
    }
    return p;
}

} // namespace

HUDWorldState g_hudState;

void xr_hud_init() {
    LOGD("xr_hud_init: establishing 3D HUD world geometry");
    // S0291 owner round 8 (2026-05-22 22:53): keep width 0.3m but slim height 1.5x
    // from 0.169m -> 0.113m per owner request ("та же ширина, но высота в 1.5 раза
    // меньше"). The banner bitmap stays 1024x128 (8:1 aspect), so the quad now
    // letterboxes the texture top/bottom; this matches the visible text height the
    // owner sees in screenshots.
    // S0964: an explicit override (panel mode) wins over the banner defaults and must
    // survive re-entry - xr_hud_init re-runs on every session start.
    if (g_hudState.overrideWidth > 0.0f && g_hudState.overrideHeight > 0.0f) {
        g_hudState.quad.width = g_hudState.overrideWidth;
        g_hudState.quad.height = g_hudState.overrideHeight;
    } else {
        g_hudState.quad.width = 0.3f;
        g_hudState.quad.height = 0.113f;
    }
    
    // S0290 (owner round 3 2026-05-22): HUD centered in front of head so screenshots
    // show the filename + stereo type banner clearly. Was {0, -0.2, -1.5} (below center).
    // S1228 (owner, in-headset 2026-07-27): that centring still holds for the diagnostic banner,
    // but the player panel must not sit on the film - it now asks for a negative offset via
    // xr_hud_set_quad_size. Default stays 0.0 so the banner placement is unchanged.
    g_hudState.quad.center = {0.0f, g_hudState.overrideVerticalOffset, -1.5f};
    g_hudState.quad.rot = {0.0f, 0.0f, 0.0f, 1.0f};
    
    g_hudState.targetCenter = g_hudState.quad.center;
    g_hudState.targetRot = g_hudState.quad.rot;
    
    g_hudState.visible = true;
    
    g_hudState.smoothedUv[0] = {0.5f, 0.5f};
    g_hudState.smoothedUv[1] = {0.5f, 0.5f};
    g_hudState.hasIntersection[0] = false;
    g_hudState.hasIntersection[1] = false;
}

// S0964: requested from the JNI bridge (any thread). Floats are written whole; the render
// thread picks the new size up next frame - no lock needed for this tear-safe pair.
void xr_hud_set_quad_size(float widthMeters, float heightMeters, float verticalOffsetMeters) {
    if (widthMeters <= 0.0f || heightMeters <= 0.0f) {
        LOGE("xr_hud_set_quad_size REJECTED: w=%.3f h=%.3f", widthMeters, heightMeters);
        return;
    }
    g_hudState.overrideWidth = widthMeters;
    g_hudState.overrideHeight = heightMeters;
    g_hudState.overrideVerticalOffset = verticalOffsetMeters;
    g_hudState.quad.width = widthMeters;
    g_hudState.quad.height = heightMeters;
    LOGD("xr_hud_set_quad_size: %.3fx%.3f m dy=%.3f", widthMeters, heightMeters, verticalOffsetMeters);
}

// S1232: requested from the JNI bridge (any thread). A single bool is written whole; the render
// thread picks it up next frame - same tear-safe contract as xr_hud_set_quad_size.
void xr_hud_set_visible(bool visible) {
    g_hudState.visible = visible;
    if (!visible) {
        // Drop the hover state with the quad. Leaving it set would strand the last cursor
        // position, so the strip would come back already hovering a widget nobody is pointing at.
        g_hudState.hasIntersection[0] = false;
        g_hudState.hasIntersection[1] = false;
        g_hudState.dragging = false;
    }
    LOGD("xr_hud_set_visible: %d", visible ? 1 : 0);
}

void xr_hud_update(const XrPosef& headPose, float deltaTime) {
    (void)deltaTime;
    if (!g_hudState.visible) return;
    
    // S0290 (owner round 3): HUD centered in front of head (y=0) instead of below (y=-0.15)
    // so the filename banner is visible directly on the user's screenshots without head tilt.
    // S1228: the offset is now per-mode (see xr_hud_set_quad_size). It rides inside the rotated
    // vector so the strip stays below the gaze ray when the user looks up or down, rather than
    // hanging at a fixed world height.
    XrVector3f ergonomicOffset = rotate_vector(
        headPose.orientation, {0.0f, g_hudState.overrideVerticalOffset, -1.5f});
    
    XrVector3f idealCenter{
        headPose.position.x + ergonomicOffset.x,
        headPose.position.y + ergonomicOffset.y,
        headPose.position.z + ergonomicOffset.z
    };
    
    XrQuaternionf idealRot = headPose.orientation;

    if (g_hudState.dragging) return;

    g_hudState.quad.center = idealCenter;
    g_hudState.quad.rot = idealRot;
    g_hudState.recenterRequested = false;
}

void xr_hud_process_rays(const XrSpace localSpace, XrTime predictedTime) {
    (void)localSpace;
    // S1232: a hidden quad is not merely unpainted - it must not react. Without this gate the
    // ray still intersects it, so a grip-drag would silently reposition a panel nobody can see
    // and the summoning trigger pull would land on whatever widget the ray happened to cross.
    if (!g_hudState.visible) return;
    bool anyGripDown = false;
    for (int i = 0; i < 2; i++) {
        if (!g_handInputStates[i].active) {
            g_hudState.hasIntersection[i] = false;
            continue;
        }

        Ray ray;
        ray.pos = g_handInputStates[i].gripPoseActive
            ? g_handInputStates[i].gripPose.position
            : g_handInputStates[i].pointerPose.position;
        ray.dir = rotate_vector(g_handInputStates[i].pointerPose.orientation, {0.0f, 0.0f, -1.0f});
        const bool gripDown = g_handInputStates[i].gripDown;
        if (gripDown && !g_prevGripDown[i]) {
            if (g_lastGripClickTime[i] > 0 && predictedTime - g_lastGripClickTime[i] < 500000000) {
                g_hudState.recenterRequested = true;
            }
            g_lastGripClickTime[i] = predictedTime;
        }
        g_prevGripDown[i] = gripDown;
        // S1240: a grip held as a media-navigation modifier is not a drag. Without this the same
        // hold would step to the next file and haul the panel along with it.
        if (gripDown && !g_handInputStates[i].gripIsModifier) {
            anyGripDown = true;
            g_hudState.quad.center = {
                ray.pos.x + ray.dir.x * 1.5f,
                ray.pos.y + ray.dir.y * 1.5f,
                ray.pos.z + ray.dir.z * 1.5f
            };
            g_hudState.targetCenter = g_hudState.quad.center;
            g_hudState.quad.rot = g_handInputStates[i].pointerPose.orientation;
            g_hudState.targetRot = g_hudState.quad.rot;
        }

        XrVector2f uv;
        if (ray_quad_intersect(ray, g_hudState.quad, uv)) {
            g_hudState.hasIntersection[i] = true;
            g_hudState.smoothedUv[i] = filter_uv_jitter(uv, g_hudState.smoothedUv[i], 0.25f);
        } else {
            g_hudState.hasIntersection[i] = false;
        }
    }
    g_hudState.dragging = anyGripDown;
}

void xr_hud_render(const float* proj, const float* viewMat, size_t eyeIdx, GLuint shaderProgram, GLuint quadVao, GLuint hudTex, GLint locViewProj, GLint locTex, GLint locEye, GLint locStereo) {
    static int s_renderCallCount = 0;
    if (!g_hudState.visible || hudTex == 0) {
        if ((s_renderCallCount++ % 90) == 0) {
            LOGE("xr_hud_render EARLY RETURN: visible=%d hudTex=%u eye=%zu",
                 g_hudState.visible ? 1 : 0, hudTex, eyeIdx);
        }
        return;
    }
    if ((s_renderCallCount++ % 180) == 0) {
        LOGD("xr_hud_render ok: eye=%zu hudTex=%u quad.center=(%.2f,%.2f,%.2f) wh=%.2fx%.2f",
             eyeIdx, hudTex,
             g_hudState.quad.center.x, g_hudState.quad.center.y, g_hudState.quad.center.z,
             g_hudState.quad.width, g_hudState.quad.height);
    }

    float modelMat[16];
    matrix_from_pose(g_hudState.quad.center, g_hudState.quad.rot, {g_hudState.quad.width, g_hudState.quad.height, 1.0f}, modelMat);
    
    float mvMat[16];
    multiply_matrices(viewMat, modelMat, mvMat);
    float mvpMat[16];
    multiply_matrices(proj, mvMat, mvpMat);

    glDisable(GL_DEPTH_TEST);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    glUseProgram(shaderProgram);
    glUniformMatrix4fv(locViewProj, 1, GL_FALSE, mvpMat);
    
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, hudTex);
    glUniform1i(locTex, 0);
    glUniform1i(locEye, (GLint)eyeIdx);
    glUniform1i(locStereo, 0); // HUD is Mono texture
    GLint locParallax = glGetUniformLocation(shaderProgram, "u_parallaxShift");
    if (locParallax >= 0) glUniform1f(locParallax, 0.0f);

    glBindVertexArray(quadVao);
    glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, nullptr);
    glBindVertexArray(0);

    if (g_colorProgram == 0) {
        const char* vsSrc = R"GLSL(#version 300 es
            precision highp float;
            layout(location=0) in vec3 a_pos;
            uniform mat4 u_viewProj;
            void main() {
                gl_Position = u_viewProj * vec4(a_pos, 1.0);
            }
        )GLSL";
        
        const char* fsSrc = R"GLSL(#version 300 es
            precision highp float;
            uniform vec4 u_color;
            out vec4 outColor;
            void main() {
                outColor = u_color;
            }
        )GLSL";
        
        GLuint vs = compile_local_shader(GL_VERTEX_SHADER, vsSrc);
        GLuint fs = compile_local_shader(GL_FRAGMENT_SHADER, fsSrc);
        g_colorProgram = link_local_program(vs, fs);
        glDeleteShader(vs); glDeleteShader(fs);
        
        g_locColorViewProj = glGetUniformLocation(g_colorProgram, "u_viewProj");
        g_locColorVec = glGetUniformLocation(g_colorProgram, "u_color");
        
        glGenVertexArrays(1, &g_lineVao);
        glGenBuffers(1, &g_lineVbo);
        glBindVertexArray(g_lineVao);
        glBindBuffer(GL_ARRAY_BUFFER, g_lineVbo);
        glBufferData(GL_ARRAY_BUFFER, 6 * sizeof(float), nullptr, GL_DYNAMIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 3 * sizeof(float), (void*)0);
        glBindVertexArray(0);
    }

    if (g_colorProgram != 0) {
        glUseProgram(g_colorProgram);
        float vpMat[16];
        multiply_matrices(proj, viewMat, vpMat);
        glUniformMatrix4fv(g_locColorViewProj, 1, GL_FALSE, vpMat);

        for (int i = 0; i < 2; i++) {
            if (!g_hudState.hasIntersection[i]) continue;

            XrVector3f right  = rotate_vector(g_hudState.quad.rot, {1.0f, 0.0f, 0.0f});
            XrVector3f up     = rotate_vector(g_hudState.quad.rot, {0.0f, 1.0f, 0.0f});
            
            float lx = (g_hudState.smoothedUv[i].x - 0.5f) * g_hudState.quad.width;
            float ly = (g_hudState.smoothedUv[i].y - 0.5f) * g_hudState.quad.height;

            XrVector3f hitPos{
                g_hudState.quad.center.x + right.x * lx + up.x * ly,
                g_hudState.quad.center.y + right.y * lx + up.y * ly,
                g_hudState.quad.center.z + right.z * lx + up.z * ly
            };

            XrVector3f origin = g_handInputStates[i].gripPoseActive
                ? g_handInputStates[i].gripPose.position
                : g_handInputStates[i].pointerPose.position;

            float lineVerts[6] = {
                origin.x, origin.y, origin.z,
                hitPos.x, hitPos.y, hitPos.z
            };
            
            glBindVertexArray(g_lineVao);
            glBindBuffer(GL_ARRAY_BUFFER, g_lineVbo);
            glBufferSubData(GL_ARRAY_BUFFER, 0, 6 * sizeof(float), lineVerts);
            
            if (i == 0) {
                glUniform4f(g_locColorVec, 0.2f, 0.9f, 0.3f, 0.4f); // semi-transparent green
            } else {
                glUniform4f(g_locColorVec, 0.2f, 0.6f, 0.9f, 0.4f); // semi-transparent blue-cyan
            }
            glLineWidth(3.0f);
            glDrawArrays(GL_LINES, 0, 2);
            glBindVertexArray(0);

            XrVector3f normal = rotate_vector(g_hudState.quad.rot, {0.0f, 0.0f, 1.0f});
            XrVector3f dotPos{
                hitPos.x + normal.x * 0.005f,
                hitPos.y + normal.y * 0.005f,
                hitPos.z + normal.z * 0.005f
            };

            float dotModel[16];
            matrix_from_pose(dotPos, g_hudState.quad.rot, {0.018f, 0.018f, 1.0f}, dotModel);
            
            float dotMvp[16];
            float dotMv[16];
            multiply_matrices(viewMat, dotModel, dotMv);
            multiply_matrices(proj, dotMv, dotMvp);

            glUniformMatrix4fv(g_locColorViewProj, 1, GL_FALSE, dotMvp);
            if (i == 0) {
                glUniform4f(g_locColorVec, 0.1f, 1.0f, 0.2f, 0.9f); // Green dot
            } else {
                glUniform4f(g_locColorVec, 0.1f, 0.7f, 1.0f, 0.9f); // Blue-cyan dot
            }

            glBindVertexArray(quadVao);
            glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, nullptr);
            glBindVertexArray(0);
        }
    }

    glDisable(GL_BLEND);
    glEnable(GL_DEPTH_TEST);
}

void xr_hud_shutdown() {
    LOGD("xr_hud_shutdown: cleaning up local GLES color programs");
    if (g_colorProgram) {
        glDeleteProgram(g_colorProgram);
        g_colorProgram = 0;
    }
    if (g_lineVao) {
        glDeleteVertexArrays(1, &g_lineVao);
        g_lineVao = 0;
    }
    if (g_lineVbo) {
        glDeleteBuffers(1, &g_lineVbo);
        g_lineVbo = 0;
    }
}

// S0986 subtitle quad -------------------------------------------------------------------------

SubtitleQuadState g_subtitleState;

void xr_subtitle_init() {
    // 0.5 m wide, aspect 4:1 (1024x256 texture) -> 0.125 m tall; two readable lines at watch
    // distance. Lower third: 0.5 m below the gaze ray (~18 deg down) so it clears the HUD panel.
    g_subtitleState.quad.width = 0.5f;
    g_subtitleState.quad.height = 0.125f;
    g_subtitleState.quad.center = {0.0f, -0.5f, -1.5f};
    g_subtitleState.quad.rot = {0.0f, 0.0f, 0.0f, 1.0f};
    g_subtitleState.visible = false;
    LOGD("xr_subtitle_init: subtitle quad %.2fx%.2f m lower-third", g_subtitleState.quad.width, g_subtitleState.quad.height);
}

void xr_subtitle_update(const XrPosef& headPose) {
    // Head-locked lower-third anchor: forward 1.5 m + down 0.5 m, always facing the user. No drag
    // and no gaze lazy-follow, so its position is fully independent of the HUD quad (S0986).
    XrVector3f offset = rotate_vector(headPose.orientation, {0.0f, -0.5f, -1.5f});
    g_subtitleState.quad.center = {
        headPose.position.x + offset.x,
        headPose.position.y + offset.y,
        headPose.position.z + offset.z
    };
    g_subtitleState.quad.rot = headPose.orientation;
}

void xr_subtitle_render(const float* proj, const float* viewMat, size_t eyeIdx, GLuint shaderProgram, GLuint quadVao, GLuint subTex, GLint locViewProj, GLint locTex, GLint locEye, GLint locStereo, GLint locZoom) {
    static int s_subRenderCallCount = 0;
    if (!g_subtitleState.visible || subTex == 0) {
        if ((s_subRenderCallCount++ % 180) == 0) {
            LOGD("xr_subtitle_render skip: visible=%d subTex=%u", g_subtitleState.visible ? 1 : 0, subTex);
        }
        return;
    }

    float modelMat[16];
    matrix_from_pose(g_subtitleState.quad.center, g_subtitleState.quad.rot, {g_subtitleState.quad.width, g_subtitleState.quad.height, 1.0f}, modelMat);
    float mvMat[16];
    multiply_matrices(viewMat, modelMat, mvMat);
    float mvpMat[16];
    multiply_matrices(proj, mvMat, mvpMat);

    glDisable(GL_DEPTH_TEST);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    glUseProgram(shaderProgram);
    glUniformMatrix4fv(locViewProj, 1, GL_FALSE, mvpMat);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, subTex);
    glUniform1i(locTex, 0);
    glUniform1i(locEye, (GLint)eyeIdx);
    glUniform1i(locStereo, 0); // subtitle is a mono texture
    // S0986: pin zoom to 1.0 so the text overlay never inherits the 360/180 video zoom (u_zoomUv).
    if (locZoom >= 0) glUniform1f(locZoom, 1.0f);
    GLint locParallax = glGetUniformLocation(shaderProgram, "u_parallaxShift");
    if (locParallax >= 0) glUniform1f(locParallax, 0.0f);

    glBindVertexArray(quadVao);
    glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, nullptr);
    glBindVertexArray(0);

    glDisable(GL_BLEND);
    glEnable(GL_DEPTH_TEST);
}

} // namespace fms::xr
