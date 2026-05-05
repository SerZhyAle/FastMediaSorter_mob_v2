#include "OpenXrRayDraw.h"
#include "OpenXrLog.h"

#include <array>
#include <cmath>

namespace
{
    constexpr const char *kVertexShaderSrc =
        "#version 300 es\n"
        "uniform mat4 uMvp;\n"
        "in vec3 aPos;\n"
        "void main() { gl_Position = uMvp * vec4(aPos, 1.0); }\n";

    constexpr const char *kFragmentShaderSrc =
        "#version 300 es\n"
        "precision mediump float;\n"
        "uniform vec4 uColor;\n"
        "out vec4 fragColor;\n"
        "void main() { fragColor = uColor; }\n";
}

bool xrnative::initRayResources(XrCtx &ctx)
{
    if (ctx.rayResources.ready)
        return true;

    GLuint vs = glCreateShader(GL_VERTEX_SHADER);
    if (vs == 0)
        return false;
    glShaderSource(vs, 1, &kVertexShaderSrc, nullptr);
    glCompileShader(vs);
    GLint vsOk = GL_FALSE;
    glGetShaderiv(vs, GL_COMPILE_STATUS, &vsOk);
    if (!vsOk)
    {
        char log[512] = {0};
        GLsizei len = 0;
        glGetShaderInfoLog(vs, sizeof(log) - 1, &len, log);
        nativeLogEmit(ANDROID_LOG_ERROR,
                      "initRayResources: shader compile failed: %s", log);
        glDeleteShader(vs);
        return false;
    }

    GLuint fs = glCreateShader(GL_FRAGMENT_SHADER);
    if (fs == 0)
    {
        glDeleteShader(vs);
        return false;
    }
    glShaderSource(fs, 1, &kFragmentShaderSrc, nullptr);
    glCompileShader(fs);
    GLint fsOk = GL_FALSE;
    glGetShaderiv(fs, GL_COMPILE_STATUS, &fsOk);
    if (!fsOk)
    {
        char log[512] = {0};
        GLsizei len = 0;
        glGetShaderInfoLog(fs, sizeof(log) - 1, &len, log);
        nativeLogEmit(ANDROID_LOG_ERROR,
                      "initRayResources: shader compile failed: %s", log);
        glDeleteShader(vs);
        glDeleteShader(fs);
        return false;
    }

    GLuint program = glCreateProgram();
    glAttachShader(program, vs);
    glAttachShader(program, fs);
    glLinkProgram(program);
    glDeleteShader(vs);
    glDeleteShader(fs);

    GLint linked = GL_FALSE;
    glGetProgramiv(program, GL_LINK_STATUS, &linked);
    if (!linked)
    {
        char log[512] = {0};
        GLsizei len = 0;
        glGetProgramInfoLog(program, sizeof(log) - 1, &len, log);
        nativeLogEmit(ANDROID_LOG_ERROR,
                      "initRayResources: program link failed: %s", log);
        glDeleteProgram(program);
        return false;
    }

    GLint uMvpLoc = glGetUniformLocation(program, "uMvp");
    GLint uColorLoc = glGetUniformLocation(program, "uColor");
    GLint aPosLoc = glGetAttribLocation(program, "aPos");

    GLuint vbo = 0;
    GLuint vao = 0;
    glGenBuffers(1, &vbo);
    glGenVertexArrays(1, &vao);
    glBindVertexArray(vao);
    glBindBuffer(GL_ARRAY_BUFFER, vbo);
    glBufferData(GL_ARRAY_BUFFER,
                 static_cast<GLsizeiptr>(sizeof(float) * 3 * 16),
                 nullptr,
                 GL_DYNAMIC_DRAW);
    if (aPosLoc >= 0)
    {
        glVertexAttribPointer(static_cast<GLuint>(aPosLoc), 3, GL_FLOAT, GL_FALSE, 0, nullptr);
        glEnableVertexAttribArray(static_cast<GLuint>(aPosLoc));
    }
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glBindVertexArray(0);

    ctx.rayResources.program = program;
    ctx.rayResources.vbo = vbo;
    ctx.rayResources.vao = vao;
    ctx.rayResources.uMvpLoc = uMvpLoc;
    ctx.rayResources.uColorLoc = uColorLoc;
    ctx.rayResources.aPosLoc = aPosLoc;
    ctx.rayResources.ready = true;

    nativeLogEmit(ANDROID_LOG_INFO,
                  "initRayResources: ready program=%u vbo=%u vao=%u",
                  program, vbo, vao);
    return true;
}

void xrnative::destroyRayResources(XrCtx &ctx)
{
    if (ctx.rayResources.vao != 0)
    {
        glDeleteVertexArrays(1, &ctx.rayResources.vao);
        ctx.rayResources.vao = 0;
    }
    if (ctx.rayResources.vbo != 0)
    {
        glDeleteBuffers(1, &ctx.rayResources.vbo);
        ctx.rayResources.vbo = 0;
    }
    if (ctx.rayResources.program != 0)
    {
        glDeleteProgram(ctx.rayResources.program);
        ctx.rayResources.program = 0;
    }
    ctx.rayResources.uMvpLoc = -1;
    ctx.rayResources.uColorLoc = -1;
    ctx.rayResources.aPosLoc = -1;
    ctx.rayResources.ready = false;
}

namespace
{
    void buildProjFromFov(const XrFovf &fov, float zNear, float zFar, float out[16])
    {
        const float tanLeft = std::tan(fov.angleLeft);
        const float tanRight = std::tan(fov.angleRight);
        const float tanUp = std::tan(fov.angleUp);
        const float tanDown = std::tan(fov.angleDown);
        const float wInv = 1.0f / (tanRight - tanLeft);
        const float hInv = 1.0f / (tanUp - tanDown);
        const float dInv = 1.0f / (zFar - zNear);
        for (int i = 0; i < 16; ++i)
            out[i] = 0.0f;
        out[0] = 2.0f * wInv;
        out[5] = 2.0f * hInv;
        out[8] = (tanRight + tanLeft) * wInv;
        out[9] = (tanUp + tanDown) * hInv;
        out[10] = -(zFar + zNear) * dInv;
        out[11] = -1.0f;
        out[14] = -(2.0f * zFar * zNear) * dInv;
    }

    void buildViewFromPose(const XrPosef &pose, float out[16])
    {
        const float qx = pose.orientation.x;
        const float qy = pose.orientation.y;
        const float qz = pose.orientation.z;
        const float qw = pose.orientation.w;
        const float r00 = 1.0f - 2.0f * (qy * qy + qz * qz);
        const float r01 = 2.0f * (qx * qy - qz * qw);
        const float r02 = 2.0f * (qx * qz + qy * qw);
        const float r10 = 2.0f * (qx * qy + qz * qw);
        const float r11 = 1.0f - 2.0f * (qx * qx + qz * qz);
        const float r12 = 2.0f * (qy * qz - qx * qw);
        const float r20 = 2.0f * (qx * qz - qy * qw);
        const float r21 = 2.0f * (qy * qz + qx * qw);
        const float r22 = 1.0f - 2.0f * (qx * qx + qy * qy);
        const float tx = -(r00 * pose.position.x + r10 * pose.position.y + r20 * pose.position.z);
        const float ty = -(r01 * pose.position.x + r11 * pose.position.y + r21 * pose.position.z);
        const float tz = -(r02 * pose.position.x + r12 * pose.position.y + r22 * pose.position.z);
        out[0] = r00; out[1] = r01; out[2] = r02; out[3] = 0.0f;
        out[4] = r10; out[5] = r11; out[6] = r12; out[7] = 0.0f;
        out[8] = r20; out[9] = r21; out[10] = r22; out[11] = 0.0f;
        out[12] = tx; out[13] = ty; out[14] = tz; out[15] = 1.0f;
    }

    void multiplyMat4(const float a[16], const float b[16], float out[16])
    {
        for (int col = 0; col < 4; ++col)
        {
            for (int row = 0; row < 4; ++row)
            {
                float s = 0.0f;
                for (int k = 0; k < 4; ++k)
                    s += a[k * 4 + row] * b[col * 4 + k];
                out[col * 4 + row] = s;
            }
        }
    }
}

void xrnative::drawControllerRays(XrCtx &ctx, const XrPosef &eyePose, const XrFovf &eyeFov)
{
    if (!ctx.rayResources.ready)
        return;
    if (!ctx.controllerRayEnabled.load())
        return;

    const bool aL = ctx.rayState[0].active;
    const bool aR = ctx.rayState[1].active;
    if (!aL && !aR)
        return;

    constexpr float kZNear = 0.05f;
    constexpr float kZFar = 100.0f;
    float proj[16];
    float view[16];
    float mvp[16];
    buildProjFromFov(eyeFov, kZNear, kZFar, proj);
    buildViewFromPose(eyePose, view);
    multiplyMat4(proj, view, mvp);

    std::array<float, 16 * 3> verts{};
    int v = 0;
    auto pushVert = [&](float x, float y, float z)
    {
        verts[v * 3 + 0] = x;
        verts[v * 3 + 1] = y;
        verts[v * 3 + 2] = z;
        ++v;
    };

    int lineVertCount = 0;
    for (int hand = 0; hand < 2; ++hand)
    {
        if (!ctx.rayState[hand].active)
            continue;
        pushVert(ctx.rayState[hand].originX, ctx.rayState[hand].originY, ctx.rayState[hand].originZ);
        pushVert(ctx.rayState[hand].endX, ctx.rayState[hand].endY, ctx.rayState[hand].endZ);
        lineVertCount += 2;
    }

    constexpr float kCursorHalf = 0.01f;
    int cursorOffsets[2] = {-1, -1};
    for (int hand = 0; hand < 2; ++hand)
    {
        if (!ctx.rayState[hand].active || !ctx.rayState[hand].hasCursor)
            continue;
        cursorOffsets[hand] = v;
        const float cx = ctx.rayState[hand].cursorX;
        const float cy = ctx.rayState[hand].cursorY;
        const float cz = ctx.rayState[hand].cursorZ;
        pushVert(cx - kCursorHalf, cy - kCursorHalf, cz);
        pushVert(cx + kCursorHalf, cy - kCursorHalf, cz);
        pushVert(cx - kCursorHalf, cy + kCursorHalf, cz);
        pushVert(cx + kCursorHalf, cy + kCursorHalf, cz);
    }

    glUseProgram(ctx.rayResources.program);
    glBindVertexArray(ctx.rayResources.vao);
    glBindBuffer(GL_ARRAY_BUFFER, ctx.rayResources.vbo);
    glBufferSubData(GL_ARRAY_BUFFER, 0,
                    static_cast<GLsizeiptr>(sizeof(float) * 3 * v),
                    verts.data());
    glUniformMatrix4fv(ctx.rayResources.uMvpLoc, 1, GL_FALSE, mvp);

    GLboolean prevDepthMask = GL_TRUE;
    glGetBooleanv(GL_DEPTH_WRITEMASK, &prevDepthMask);
    glDisable(GL_DEPTH_TEST);
    glDepthMask(GL_FALSE);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    if (lineVertCount > 0)
    {
        glUniform4f(ctx.rayResources.uColorLoc, 0.4f, 0.85f, 1.0f, 0.6f);
        glLineWidth(2.0f);
        glDrawArrays(GL_LINES, 0, lineVertCount);
    }

    for (int hand = 0; hand < 2; ++hand)
    {
        if (cursorOffsets[hand] < 0)
            continue;
        glUniform4f(ctx.rayResources.uColorLoc, 0.95f, 0.95f, 0.95f, 0.85f);
        glDrawArrays(GL_TRIANGLE_STRIP, cursorOffsets[hand], 4);
    }

    glDepthMask(prevDepthMask);
    glEnable(GL_DEPTH_TEST);
    glDisable(GL_BLEND);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glBindVertexArray(0);
    glUseProgram(0);
}
