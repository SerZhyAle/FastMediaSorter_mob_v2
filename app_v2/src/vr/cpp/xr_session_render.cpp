// S1270: GL helpers, mesh and matrix math, per-eye render, event and action polling, and the
// frame loop, extracted from xr_session.cpp.

#include "xr_session_internal.h"

namespace fms::xr
{
    namespace detail
    {

        // S0290 Phase 10 / ADR-6: race-guard only against pathological double-callback from
        // xrWaitFrame on Quest 3 timewarp. Main debounce = application-side rising-edge detection
        // on triggerClicked (Step 10.1) plus the Meta runtime's own hysteresis on the
        // pinch_ext/ready_ext boolean input. Bumped 100 ms -> 500 ms after owner feedback round 2
        // (2026-05-22): user reported "still jumping" even with stick remap + edge-detection. The
        // half-second cooldown guarantees one navigation per deliberate gesture even if the stick
        // drifts back across the deflection threshold during a single intentional push.
        constexpr XrDuration kNavigateDebounceDuration = 500000000;

        constexpr float kPI = 3.14159265358979323846f;
        constexpr int kSphereLatSegments = 32;
        constexpr int kSphereLonSegments = 64;
        constexpr float kSphereRadius = 10.0f;

        bool checkGl(const char *tag)
        {
            GLenum e = glGetError();
            if (e != GL_NO_ERROR)
            {
                LOGE("GL error at %s: 0x%x", tag, e);
                return false;
            }
            return true;
        }

        bool hasGlExtension(const char *needle)
        {
            const char *extensions = reinterpret_cast<const char *>(glGetString(GL_EXTENSIONS));
            return extensions && std::strstr(extensions, needle) != nullptr;
        }

        void configureStaticTextureFiltering()
        {
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            if (hasGlExtension("GL_EXT_texture_filter_anisotropic"))
            {
                GLfloat maxAnisotropy = 1.0f;
                glGetFloatv(GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT, &maxAnisotropy);
                const GLfloat chosen = std::fmin(maxAnisotropy, 8.0f);
                glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAX_ANISOTROPY_EXT, chosen);
                LOGD("static texture anisotropy enabled: %.1f", chosen);
            }
        }

        bool uploadStaticTexturePixels(const uint8_t *rgba, int width, int height, const char *tag)
        {
            glBindTexture(GL_TEXTURE_2D, g.texture);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
            glGenerateMipmap(GL_TEXTURE_2D);
            return checkGl(tag);
        }

        GLuint compileShader(GLenum type, const char *src)
        {
            GLuint s = glCreateShader(type);
            glShaderSource(s, 1, &src, nullptr);
            glCompileShader(s);
            GLint status = 0;
            glGetShaderiv(s, GL_COMPILE_STATUS, &status);
            if (!status)
            {
                char log[1024];
                GLsizei n = 0;
                glGetShaderInfoLog(s, sizeof(log), &n, log);
                LOGE("Shader compile failed: %.*s", (int)n, log);
                glDeleteShader(s);
                return 0;
            }
            return s;
        }

        GLuint linkProgram(GLuint vs, GLuint fs)
        {
            GLuint p = glCreateProgram();
            glAttachShader(p, vs);
            glAttachShader(p, fs);
            glLinkProgram(p);
            GLint status = 0;
            glGetProgramiv(p, GL_LINK_STATUS, &status);
            if (!status)
            {
                char log[1024];
                GLsizei n = 0;
                glGetProgramInfoLog(p, sizeof(log), &n, log);
                LOGE("Program link failed: %.*s", (int)n, log);
                glDeleteProgram(p);
                return 0;
            }
            return p;
        }

        void buildSphereMesh(std::vector<float> &verts, std::vector<unsigned int> &indices)
        {
            verts.clear();
            indices.clear();
            const int lat = kSphereLatSegments;
            const int lon = kSphereLonSegments;
            verts.reserve((lat + 1) * (lon + 1) * 5);
            for (int y = 0; y <= lat; ++y)
            {
                float v = (float)y / (float)lat;
                float theta = v * kPI;
                float sinT = std::sin(theta), cosT = std::cos(theta);
                for (int x = 0; x <= lon; ++x)
                {
                    float u = (float)x / (float)lon;
                    // S0291 owner round 3 fix (2026-05-22 21:26): owner observed "LEFT" text rendered
                    // as "TFEL" (horizontally mirrored) on diagnostic_360_stereo_tb.jpg. Root cause:
                    // for sphere viewed from inside, the azimuth-vs-texture-U direction was inverted -
                    // as user turned LEFT, texture U DECREASED instead of INCREASED, producing a
                    // mirror image. Same bug was present on bundled landscape pano but masked because
                    // the lake scene has no readable text. Reverse phi direction so texture U=0
                    // appears behind user and increases counter-clockwise (standard equirect viewer
                    // convention) - front-of-user now at u=0.25 (was 0.75) and turning RIGHT scrolls
                    // toward larger u.
                    float phi = (1.0f - u) * 2.0f * kPI;
                    float sinP = std::sin(phi), cosP = std::cos(phi);
                    float px = -sinT * cosP * kSphereRadius;
                    float py = cosT * kSphereRadius;
                    float pz = sinT * sinP * kSphereRadius;
                    verts.push_back(px);
                    verts.push_back(py);
                    verts.push_back(pz);
                    verts.push_back(u);
                    verts.push_back(v);
                }
            }
            indices.reserve(lat * lon * 6);
            for (int y = 0; y < lat; ++y)
            {
                for (int x = 0; x < lon; ++x)
                {
                    unsigned int i0 = y * (lon + 1) + x;
                    unsigned int i1 = i0 + 1;
                    unsigned int i2 = i0 + (lon + 1);
                    unsigned int i3 = i2 + 1;
                    indices.push_back(i0);
                    indices.push_back(i2);
                    indices.push_back(i1);
                    indices.push_back(i1);
                    indices.push_back(i2);
                    indices.push_back(i3);
                }
            }
        }

        void buildHemisphereMesh(std::vector<float> &verts, std::vector<unsigned int> &indices)
        {
            verts.clear();
            indices.clear();
            const int lat = kSphereLatSegments;
            const int lon = kSphereLonSegments;
            verts.reserve((lat + 1) * (lon + 1) * 5);
            for (int y = 0; y <= lat; ++y)
            {
                float v = (float)y / (float)lat;
                float theta = v * kPI;
                float sinT = std::sin(theta), cosT = std::cos(theta);
                for (int x = 0; x <= lon; ++x)
                {
                    float u = (float)x / (float)lon;
                    // Same U-axis mirror fix as sphere (see comment above). For the 180° forward
                    // hemisphere, phi spans pi..2pi originally - reverse so the texture's natural
                    // left-to-right reads correctly when viewed from inside the half-shell.
                    float phi = kPI + (1.0f - u) * kPI;
                    float sinP = std::sin(phi), cosP = std::cos(phi);
                    float px = -sinT * cosP * kSphereRadius;
                    float py = cosT * kSphereRadius;
                    float pz = sinT * sinP * kSphereRadius;
                    verts.push_back(px);
                    verts.push_back(py);
                    verts.push_back(pz);
                    verts.push_back(u);
                    verts.push_back(v);
                }
            }
            indices.reserve(lat * lon * 6);
            for (int y = 0; y < lat; ++y)
            {
                for (int x = 0; x < lon; ++x)
                {
                    unsigned int i0 = y * (lon + 1) + x;
                    unsigned int i1 = i0 + 1;
                    unsigned int i2 = i0 + (lon + 1);
                    unsigned int i3 = i2 + 1;
                    indices.push_back(i0);
                    indices.push_back(i2);
                    indices.push_back(i1);
                    indices.push_back(i1);
                    indices.push_back(i2);
                    indices.push_back(i3);
                }
            }
        }

        void buildQuadMesh(std::vector<float> &verts, std::vector<unsigned int> &indices)
        {
            verts = {
                -0.5f, 0.5f, 0.0f, 0.0f, 0.0f,
                -0.5f, -0.5f, 0.0f, 0.0f, 1.0f,
                0.5f, -0.5f, 0.0f, 1.0f, 1.0f,
                0.5f, 0.5f, 0.0f, 1.0f, 0.0f};
            indices = {
                0, 1, 2,
                0, 2, 3};
        }
        void perspectiveFromFov(const XrFovf &fov, float nearZ, float farZ, float *m)
        {
            const float tanL = std::tan(fov.angleLeft);
            const float tanR = std::tan(fov.angleRight);
            const float tanU = std::tan(fov.angleUp);
            const float tanD = std::tan(fov.angleDown);
            const float w = tanR - tanL;
            const float h = tanU - tanD;
            std::memset(m, 0, sizeof(float) * 16);
            m[0] = 2.0f / w;
            m[5] = 2.0f / h;
            m[8] = (tanR + tanL) / w;
            m[9] = (tanU + tanD) / h;
            m[10] = -(farZ + nearZ) / (farZ - nearZ);
            m[11] = -1.0f;
            m[14] = -(2.0f * farZ * nearZ) / (farZ - nearZ);
        }

        // Keep R^T in column-major order; otherwise the world rotates with the head.
        void viewFromPose(const XrPosef &pose, float *m)
        {
            const float x = pose.orientation.x, y = pose.orientation.y, z = pose.orientation.z, w = pose.orientation.w;
            const float xx = x * x, yy = y * y, zz = z * z;
            const float xy = x * y, xz = x * z, yz = y * z;
            const float wx = w * x, wy = w * y, wz = w * z;
            float r[9] = {
                1 - 2 * (yy + zz), 2 * (xy + wz), 2 * (xz - wy),
                2 * (xy - wz), 1 - 2 * (xx + zz), 2 * (yz + wx),
                2 * (xz + wy), 2 * (yz - wx), 1 - 2 * (xx + yy)};
            const float tx = pose.position.x, ty = pose.position.y, tz = pose.position.z;
            m[0] = r[0];
            m[1] = r[3];
            m[2] = r[6];
            m[3] = 0;
            m[4] = r[1];
            m[5] = r[4];
            m[6] = r[7];
            m[7] = 0;
            m[8] = r[2];
            m[9] = r[5];
            m[10] = r[8];
            m[11] = 0;
            m[12] = -(r[0] * tx + r[1] * ty + r[2] * tz);
            m[13] = -(r[3] * tx + r[4] * ty + r[5] * tz);
            m[14] = -(r[6] * tx + r[7] * ty + r[8] * tz);
            m[15] = 1;
        }

        void multiply4x4(const float *a, const float *b, float *out)
        {
            for (int c = 0; c < 4; ++c)
                for (int r = 0; r < 4; ++r)
                {
                    out[c * 4 + r] = a[0 * 4 + r] * b[c * 4 + 0] + a[1 * 4 + r] * b[c * 4 + 1] + a[2 * 4 + r] * b[c * 4 + 2] + a[3 * 4 + r] * b[c * 4 + 3];
                }
        }

        void scaleAndTranslate4x4(float sx, float sy, float sz, float tx, float ty, float tz, float *out)
        {
            std::memset(out, 0, sizeof(float) * 16);
            out[0] = sx;
            out[5] = sy;
            out[10] = sz;
            out[12] = tx;
            out[13] = ty;
            out[14] = tz;
            out[15] = 1.0f;
        }

        // Event types accepted by DiagnosticXrActivity.onNativeInputEvent - keep both sides in step.
        constexpr int kInputEventNext = 1;
        constexpr int kInputEventPrev = 2;
        constexpr int kInputEventHudSummon = 3;
        constexpr int kInputEventSeekForward = 4;
        constexpr int kInputEventSeekBack = 5;
        constexpr int kInputEventMenuToggle = 6; // S1271: left menu button - settings panel
        // S1133: browse-mode grid steps, accepted by ImmersiveBrowseActivity.onNativeInputEvent.
        constexpr int kInputEventGridLeft = 7;
        constexpr int kInputEventGridRight = 8;
        constexpr int kInputEventGridUp = 9;
        constexpr int kInputEventGridDown = 10;

        void pollEvents()
        {
            XrEventDataBuffer ev{XR_TYPE_EVENT_DATA_BUFFER};
            while (xrPollEvent(g.instance, &ev) == XR_SUCCESS)
            {
                switch (ev.type)
                {
                case XR_TYPE_EVENT_DATA_SESSION_STATE_CHANGED:
                {
                    auto *st = reinterpret_cast<XrEventDataSessionStateChanged *>(&ev);
                    g.sessionState = st->state;
                    LOGD("session state -> %d", (int)st->state);
                    if (st->state == XR_SESSION_STATE_READY)
                    {
                        XrSessionBeginInfo bi{XR_TYPE_SESSION_BEGIN_INFO};
                        bi.primaryViewConfigurationType = XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO;
                        xrBeginSession(g.session, &bi);
                        g.sessionRunning = true;
                    }
                    else if (st->state == XR_SESSION_STATE_STOPPING)
                    {
                        xrEndSession(g.session);
                        g.sessionRunning = false;
                    }
                    else if (st->state == XR_SESSION_STATE_EXITING || st->state == XR_SESSION_STATE_LOSS_PENDING)
                    {
                        g.exitRequested.store(true);
                    }
                    break;
                }
                case XR_TYPE_EVENT_DATA_INSTANCE_LOSS_PENDING:
                    g.exitRequested.store(true);
                    break;
                default:
                    break;
                }
                ev = {XR_TYPE_EVENT_DATA_BUFFER};
            }
        }

        bool renderEye(size_t eyeIdx, const XrView &view, XrCompositionLayerProjectionView &outLayer)
        {
            EyeSwapchain &eye = g.eyes[eyeIdx];
            uint32_t imgIdx = 0;
            XrSwapchainImageAcquireInfo ai{XR_TYPE_SWAPCHAIN_IMAGE_ACQUIRE_INFO};
            if (XR_FAILED(xrAcquireSwapchainImage(eye.handle, &ai, &imgIdx)))
                return false;
            XrSwapchainImageWaitInfo wi{XR_TYPE_SWAPCHAIN_IMAGE_WAIT_INFO};
            wi.timeout = XR_INFINITE_DURATION;
            if (XR_FAILED(xrWaitSwapchainImage(eye.handle, &wi)))
                return false;

            glBindFramebuffer(GL_FRAMEBUFFER, eye.fbo);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, eye.images[imgIdx].image.image, 0);
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, eye.depthRb);
            glViewport(0, 0, eye.width, eye.height);
            // S1231 (owner, in-headset 2026-07-27): void black, not a tinted near-black. The
            // previous {0.05, 0.05, 0.08} reads as blue-grey against a film - the blue channel
            // being highest made it a visible haze rather than absence of light.
            glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            float proj[16];
            perspectiveFromFov(view.fov, 0.05f, 100.0f, proj);
            float viewMat[16];
            viewFromPose(view.pose, viewMat);

            float mvp[16];
            if (g.renderProjection == 2)
            {
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
            }
            else
            {
                multiply4x4(proj, viewMat, mvp);
            }

            GLuint activeVao = g.vao;
            GLsizei activeIndexCount = g.indexCount;
            if (g.renderProjection == 1)
            {
                activeVao = g.hemiVao;
                activeIndexCount = g.hemiIndexCount;
            }
            else if (g.renderProjection == 2)
            {
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

            glEnable(GL_DEPTH_TEST);
            glDisable(GL_CULL_FACE);
            glUseProgram(program);
            glUniformMatrix4fv(locViewProj, 1, GL_FALSE, mvp);
            glActiveTexture(GL_TEXTURE0);
            if (useVideoTexture)
            {
                glBindTexture(GL_TEXTURE_EXTERNAL_OES, g.videoTexture);
                if (g.videoLocTexTransform >= 0)
                {
                    glUniformMatrix4fv(g.videoLocTexTransform, 1, GL_FALSE, g.videoTextureTransform);
                }
            }
            else
            {
                glBindTexture(GL_TEXTURE_2D, g.texture);
            }
            glUniform1i(locTex, 0);
            glUniform1i(locEye, (GLint)eyeIdx);
            glUniform1i(locStereoLayout, g.stereoLayout);
            if (locParallaxShift >= 0)
                glUniform1f(locParallaxShift, g.parallaxShift);
            // S0291 round 5: zoom applies as UV scaling for 360 / 180 sphere/hemi projections only.
            // FLAT projection scales via model matrix instead (built above) and the shader gets 1.0
            // so the FLAT quad's UV range stays exact.
            const float effectiveZoomUv = (g.renderProjection == 2) ? 1.0f : g.zoom;
            if (locZoomUv >= 0)
                glUniform1f(locZoomUv, effectiveZoomUv);
            glBindVertexArray(activeVao);
            glDrawElements(GL_TRIANGLES, activeIndexCount, GL_UNSIGNED_INT, nullptr);
            glBindVertexArray(0);

            // Render World Space HUD Quad, pointer rays, and low-latency cursor dots (Phase 02).
            // S0961: pass hudTex=0 until real content reached the texture so the quad stays hidden
            // instead of exposing the grey 1x1 placeholder; reuses xr_hud_render's early-return and
            // its "EARLY RETURN .. hudTex=0" diagnostic log.
            xr_hud_render(proj, viewMat, eyeIdx, g.program, g.quadVao,
                          g.hudContentUploaded ? g.hudTexture : 0,
                          g.locViewProj, g.locTex, g.locEye, g.locStereoLayout);

            // S0986: subtitle quad - lower-third cue text, independent of the HUD. subTex=0 until a
            // real cue lands (content gate) and while the current cue is empty (visibility gate).
            xr_subtitle_render(proj, viewMat, eyeIdx, g.program, g.quadVao,
                               (g.subtitleContentUploaded && g.subtitleVisible) ? g.subtitleTexture : 0,
                               g.locViewProj, g.locTex, g.locEye, g.locStereoLayout, g.locZoomUv);

            glBindFramebuffer(GL_FRAMEBUFFER, 0);

            XrSwapchainImageReleaseInfo ri{XR_TYPE_SWAPCHAIN_IMAGE_RELEASE_INFO};
            xrReleaseSwapchainImage(eye.handle, &ri);

            outLayer = {XR_TYPE_COMPOSITION_LAYER_PROJECTION_VIEW};
            outLayer.pose = view.pose;
            outLayer.fov = view.fov;
            outLayer.subImage.swapchain = eye.handle;
            outLayer.subImage.imageRect.offset = {0, 0};
            outLayer.subImage.imageRect.extent = {eye.width, eye.height};
            return true;
        }

        void pollActions(XrTime predictedTime)
        {
            if (!g.sessionRunning)
                return;

            xr_input_poll(g.localSpace, predictedTime);

            // S1271: the left menu button toggles the settings panel. The edge detection lives in
            // xr_input_poll (menuClicked is a rising edge), so this emits exactly one managed
            // callback per press without touching exit / trigger / thumbstick / grip handling.
            if (g_handInputStates[0].menuClicked)
            {
                triggerJniInputCallback(kInputEventMenuToggle);
            }

            // S0290 Phase 10 (owner feedback round 2 2026-05-22): tighten thresholds to combat
            // over-sensitivity reports - user sees "still jumping" even with edge-detection because
            // a 0.6 deflect is easy to hit incidentally on Quest 3 sticks. Bumped to 0.85 (firm push
            // required) and re-arm to ±0.40 (clear release required). Race-guard duration upgraded
            // to a navigation-cooldown of 500 ms (kNavigateDebounceDuration) so a quick re-flick
            // cannot fire a second navigation within half a second of the previous one. Triggers
            // remain free for Quest 3 system gestures (screenshot, recenter).
            constexpr float kStickDeflectThreshold = 0.85f;
            constexpr float kStickReturnThreshold = 0.40f;
            // S1133: read the mode once so both axes of one frame agree on the semantics even if
            // the Activity switches modes mid-frame.
            const int inputMode = g_inputMode.load(std::memory_order_relaxed);
            const bool browseMode = inputMode == kXrInputModeBrowse;
            for (int hand = 0; hand < 2; ++hand)
            {
                const float x = g_handInputStates[hand].thumbstickX;
                int newState = g_prevStickState[hand];
                if (g_prevStickState[hand] == 0)
                {
                    if (x > kStickDeflectThreshold)
                        newState = +1;
                    else if (x < -kStickDeflectThreshold)
                        newState = -1;
                }
                else
                {
                    if (std::abs(x) < kStickReturnThreshold)
                        newState = 0;
                }
                if (newState != g_prevStickState[hand] && newState != 0)
                {
                    const bool raceGuardOk =
                        g_lastNavigateActionTime[hand] == 0 ||
                        predictedTime - g_lastNavigateActionTime[hand] >= kNavigateDebounceDuration;
                    if (raceGuardOk)
                    {
                        g_lastNavigateActionTime[hand] = predictedTime;
                        // S1240 decision 1: the bare axis now seeks; grip is the modifier that
                        // turns it back into media navigation. Latching gripIsModifier suppresses
                        // this hand's HUD drag for the rest of the hold - reaching for the next
                        // file must not also drag the panel across the room (spec 7.2).
                        // S1133: the grip modifier is player-only - the browser leaves the hold
                        // free to drag its panel, so a browse step never latches it.
                        const bool gripAsModifier = !browseMode && g_handInputStates[hand].gripDown;
                        if (gripAsModifier)
                        {
                            g_handInputStates[hand].gripIsModifier = true;
                        }
                        int event;
                        if (browseMode)
                        {
                            event = newState > 0 ? kInputEventGridRight : kInputEventGridLeft;
                        }
                        else
                        {
                            event = gripAsModifier
                                ? (newState > 0 ? kInputEventNext : kInputEventPrev)
                                : (newState > 0 ? kInputEventSeekForward : kInputEventSeekBack);
                        }
                        LOGD("hand=%d stick deflect %s grip=%d browse=%d -> event %d (count=%d, x=%.2f)",
                             hand, newState > 0 ? "right" : "left", gripAsModifier ? 1 : 0,
                             browseMode ? 1 : 0, event, ++g_navigateCounter[hand], x);
                        triggerJniInputCallback(event);
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
            // S1133: in browse mode the vertical axis walks grid rows instead of accumulating zoom,
            // mirroring the horizontal edge detector above. The navigate timestamp is shared with
            // that loop on purpose: a diagonal push is one gesture, and firing a column step and a
            // row step from it would move the selection two cells for one flick.
            if (browseMode)
            {
                for (int hand = 0; hand < 2; ++hand)
                {
                    const float y = g_handInputStates[hand].thumbstickY;
                    int newStateY = g_prevStickStateY[hand];
                    if (g_prevStickStateY[hand] == 0)
                    {
                        if (y > kStickDeflectThreshold)
                            newStateY = +1;
                        else if (y < -kStickDeflectThreshold)
                            newStateY = -1;
                    }
                    else
                    {
                        if (std::abs(y) < kStickReturnThreshold)
                            newStateY = 0;
                    }
                    if (newStateY != g_prevStickStateY[hand] && newStateY != 0)
                    {
                        const bool raceGuardOk =
                            g_lastNavigateActionTime[hand] == 0 ||
                            predictedTime - g_lastNavigateActionTime[hand] >= kNavigateDebounceDuration;
                        if (raceGuardOk)
                        {
                            g_lastNavigateActionTime[hand] = predictedTime;
                            // OpenXR thumbstick convention: y > 0 is pushed away from the user,
                            // which reads as "up" on a vertical grid.
                            const int event = newStateY > 0 ? kInputEventGridUp : kInputEventGridDown;
                            LOGD("hand=%d stick deflect %s browse -> event %d (y=%.2f)",
                                 hand, newStateY > 0 ? "up" : "down", event, y);
                            triggerJniInputCallback(event);
                        }
                    }
                    g_prevStickStateY[hand] = newStateY;
                }
                // The zoom accumulator below is player-only: a thumbnail grid has no zoom, and
                // leaving it live would scale the media quad behind the browser panel.
                return;
            }

            constexpr float kZoomDeadzone = 0.2f;
            constexpr float kZoomPerFrame = 0.012f; // ~1.2% size change at full deflection per frame; smooth at 72 Hz.
            constexpr float kZoomMin = 0.3f;
            constexpr float kZoomMax = 3.0f;
            float zoomDelta = 0.0f;
            for (int hand = 0; hand < 2; ++hand)
            {
                const float y = g_handInputStates[hand].thumbstickY;
                if (std::abs(y) > kZoomDeadzone)
                {
                    zoomDelta += -y * kZoomPerFrame;
                }
            }
            if (zoomDelta != 0.0f)
            {
                std::lock_guard<std::mutex> lock(g.mutex);
                float nz = g.zoom + zoomDelta;
                if (nz < kZoomMin)
                    nz = kZoomMin;
                if (nz > kZoomMax)
                    nz = kZoomMax;
                g.zoom = nz;
            }
        }

    } // namespace detail

    using namespace detail;

    NativeResult xr_session_run_frame_loop()
    {
        if (!g.running.load() || g.session == XR_NULL_HANDLE)
            return NativeResult::NotRunning;
        LOGD("frame loop entered");
        while (!g.exitRequested.load())
        {
            pollEvents();
            if (!g.sessionRunning)
            {
                // Throttle while waiting for SESSION_STATE_READY. Without sleep this branch
                // busy-spins at 100% CPU; 1 ms is enough to stay responsive to state changes
                // without burning a core during the window-ready wait.
                usleep(1000);
                continue;
            }
            XrFrameWaitInfo fwi{XR_TYPE_FRAME_WAIT_INFO};
            XrFrameState fs{XR_TYPE_FRAME_STATE};
            if (XR_FAILED(xrWaitFrame(g.session, &fwi, &fs)))
                break;
            XrFrameBeginInfo fbi{XR_TYPE_FRAME_BEGIN_INFO};
            xrBeginFrame(g.session, &fbi);

            // Smoothed FPS sampler: derive instantaneous frame rate from predictedDisplayTime delta,
            // feed an EMA. `predictedDisplayTime` is monotonic in nanoseconds (XrTime).
            {
                const int64_t now = static_cast<int64_t>(fs.predictedDisplayTime);
                if (g_prevPredictedDisplayTimeNs != 0 && now > g_prevPredictedDisplayTimeNs)
                {
                    const double dtSec = static_cast<double>(now - g_prevPredictedDisplayTimeNs) * 1e-9;
                    if (dtSec > 1e-6 && dtSec < 1.0)
                    {
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
                if (g.pendingFrameReady)
                {
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
                if (g.pendingHudReady)
                {
                    glBindTexture(GL_TEXTURE_2D, g.hudTexture);
                    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
                    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, g.pendingHudWidth, g.pendingHudHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, g.pendingHudData.data());
                    LOGD("hud upload: %dx%d to texture=%u", g.pendingHudWidth, g.pendingHudHeight, g.hudTexture);
                    g.pendingHudReady = false;
                    g.hudContentUploaded = true;
                }
            }
            {
                // S0986: subtitle upload / hide - mirrors the HUD block under its own mutex.
                std::lock_guard<std::mutex> lock(g.subtitleMutex);
                if (g.pendingSubtitleHide)
                {
                    g.subtitleVisible = false;
                    g.pendingSubtitleHide = false;
                }
                if (g.pendingSubtitleReady)
                {
                    glBindTexture(GL_TEXTURE_2D, g.subtitleTexture);
                    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
                    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, g.pendingSubtitleWidth, g.pendingSubtitleHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, g.pendingSubtitleData.data());
                    g.pendingSubtitleReady = false;
                    g.subtitleContentUploaded = true;
                    g.subtitleVisible = true;
                }
            }

            std::vector<XrCompositionLayerProjectionView> layerViews;
            XrCompositionLayerProjection layer{XR_TYPE_COMPOSITION_LAYER_PROJECTION};
            layer.space = g.localSpace;

            if (fs.shouldRender && g.viewConfigs.size() == g.eyes.size() && !g.eyes.empty())
            {
                XrViewLocateInfo vli{XR_TYPE_VIEW_LOCATE_INFO};
                vli.viewConfigurationType = XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO;
                vli.displayTime = fs.predictedDisplayTime;
                vli.space = g.localSpace;
                XrViewState vs{XR_TYPE_VIEW_STATE};
                uint32_t viewCount = 0;
                std::vector<XrView> views(g.viewConfigs.size(), {XR_TYPE_VIEW});
                if (XR_SUCCEEDED(xrLocateViews(g.session, &vli, &vs, (uint32_t)views.size(), &viewCount, views.data())) &&
                    (vs.viewStateFlags & XR_VIEW_STATE_POSITION_VALID_BIT) &&
                    (vs.viewStateFlags & XR_VIEW_STATE_ORIENTATION_VALID_BIT))
                {
                    if (viewCount > 0)
                    {
                        xr_hud_update(views[0].pose, 0.013f);
                        xr_subtitle_update(views[0].pose);
                        xr_hud_process_rays(g.localSpace, fs.predictedDisplayTime);

                        if (!g_hudState.visible)
                        {
                            // S1232: while the strip is hidden the trigger is a summon, not a
                            // ray click. triggerClicked is the rising edge (xr_input.cpp), so a
                            // held trigger summons once. Nothing is dispatched to Kotlin as a
                            // hover or click, which is what "the summoning pull is consumed"
                            // means: calling the panel back cannot also press a widget on it.
                            for (int i = 0; i < 2; i++)
                            {
                                if (g_handInputStates[i].triggerClicked)
                                {
                                    triggerJniInputCallback(kInputEventHudSummon);
                                    break;
                                }
                            }
                        }
                        else
                        {
                            // Step 03.1: Stream interaction data from C++ render loop up to JVM
                            bool anyHover = false;
                            float targetUvX = 0.5f;
                            float targetUvY = 0.5f;
                            bool anyClick = false;
                            for (int i = 0; i < 2; i++)
                            {
                                if (g_hudState.hasIntersection[i])
                                {
                                    anyHover = true;
                                    targetUvX = g_hudState.smoothedUv[i].x;
                                    targetUvY = g_hudState.smoothedUv[i].y;
                                    if (g_handInputStates[i].triggerDown)
                                    {
                                        anyClick = true;
                                    }
                                    break;
                                }
                            }
                            // S1133: a selection moved by the thumbstick lives while the ray points
                            // past the panel, and anyClick above is only ever computed inside the
                            // intersection branch - so in browse mode a trigger held off-panel is
                            // forwarded as a click and the dispatcher activates that selection.
                            // Player mode keeps the original contract: no intersection, no click.
                            if (!anyHover && g_inputMode.load(std::memory_order_relaxed) == kXrInputModeBrowse)
                            {
                                anyClick = g_handInputStates[0].triggerDown || g_handInputStates[1].triggerDown;
                            }
                            triggerJniRayInteraction(targetUvX, targetUvY, anyHover, anyClick);
                        }
                    }
                    layerViews.resize(viewCount);
                    updateVideoTextureIfNeeded();
                    for (uint32_t i = 0; i < viewCount; ++i)
                        renderEye(i, views[i], layerViews[i]);
                    layer.viewCount = (uint32_t)layerViews.size();
                    layer.views = layerViews.data();
                }
            }

            XrCompositionLayerBaseHeader *layers[1] = {(XrCompositionLayerBaseHeader *)&layer};
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

} // namespace fms::xr
