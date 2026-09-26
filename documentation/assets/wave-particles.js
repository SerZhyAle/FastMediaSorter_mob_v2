/*
 * WAVE-PARTICLES reference implementation - contract version 0.12, rung 2 of section 7.
 * Canonical in the contracts catalog, animated-backdrop/reference/wave-particles.js. A served copy conforms
 * when it is byte-identical to this file.
 *
 * Dependency-free ES5, embeddable as-is:
 *   <canvas data-wave-particles data-palette="GREEN" data-wave-size="parent"></canvas>
 *   <script src="wave-particles.js"></script>
 * or programmatically: WaveParticles.create(canvas, { random: WaveParticles.seededRandom(7) }).start().
 *
 * Canvas attributes read by autoInit():
 *   data-palette     DYNAMIC (default) | GREEN | PINK | BLUE          (rule 6)
 *   data-profile     full (default) | reduced                          (rule 13)
 *   data-wave-size   window (default) | parent
 *   data-wave-theme  dark (default) | light | html | system           (rule 7)
 *                    html = light while <html data-theme="light">; system = prefers-color-scheme
 *   data-wave-wash   CSS custom property on <html> holding the host background, e.g. --bg (section 3.4 note)
 *   data-intensity, data-speed, data-density                          (rule 15; omit to leave them at 1)
 */
(function (root) {
    'use strict';

    // WAVE-PARTICLES section 3.1
    var REFERENCE_FRAME_MS = 1000 / 60;
    var TIME_STEP = 0.002;
    var RAMP_FRAMES = 36;
    // Not a contract value: after a long main-thread stall one tick would otherwise jump the picture by
    // seconds. The phone implementation caps the same way.
    var MAX_FRAMES_PER_TICK = 4;

    // WAVE-PARTICLES section 3.2
    var PROFILES = {
        full: { lines: [5, 12], particles: [15, 55] },
        reduced: { lines: [3, 6], particles: [6, 20] }
    };
    var SAMPLE_STEP = 20;
    var SAMPLE_STEP_JITTER = [0.8, 1.2];
    var STROKE_WIDTH = [3, 6];
    var AMPLITUDE_FRACTION = [0.28, 0.48];
    var SPEED_MULTIPLIER = [0.5, 1.5];
    var PARTICLE_RADIUS = [1, 6];
    var PARTICLE_SPEED_BASE = 0.12;
    var PARTICLE_SPEED_RANGE = 0.42;
    var COUNTER_DRIFT_PROBABILITY = 0.18;
    var COUNTER_DRIFT_FACTOR = -0.35;
    var PARTICLE_JITTER = 0.28;

    // WAVE-PARTICLES section 3.3
    var PALETTES = {
        DYNAMIC: function (u) {
            return { lineBase: u(0, 360), lineStep: u(8, 20), particleBase: u(0, 360), spread: 108 };
        },
        GREEN: function (u) { return banded(u, 95, 130, 115); },
        PINK: function (u) { return banded(u, 305, 335, 320); },
        BLUE: function (u) { return banded(u, 200, 230, 215); }
    };

    function banded(u, lineLow, lineHigh, particleCentre) {
        return {
            lineBase: u(lineLow, lineHigh),
            lineStep: u(2, 6),
            particleBase: particleCentre + (u(0, 1) - 0.5) * 30,
            spread: 30
        };
    }

    // WAVE-PARTICLES section 3.4
    var WASH_DARK = 'rgb(10, 10, 10)';
    var WASH_LIGHT = 'rgb(245, 245, 245)';
    var WASH_ALPHA = 38 / 255;
    var LINE_SATURATION = 0.80;
    var LINE_LIGHTNESS = { dark: 0.65, light: 0.35 };
    var PARTICLE_SATURATION = 0.90;
    var PARTICLE_LIGHTNESS = { dark: 0.70, light: 0.30 };
    var LINE_OPACITY_BASE = 0.28;
    var LINE_OPACITY_GAIN = 0.16;
    var PARTICLE_OPACITY_BASE = 0.38;
    var PARTICLE_OPACITY_GAIN = 0.32;
    var OPACITY_SCALE = 0.70;
    var CENTRE_DRIFT_RATE = 0.45;
    var CENTRE_DRIFT_FRACTION = 0.02;
    var LANE_FRACTION = 0.038;
    var SPAN_EXTRA_STEPS = 6;
    var SPATIAL_FREQUENCY = 0.0105;
    var LANE_PHASE_STEP = 0.8;
    var ENVELOPE_BASE = 0.40;
    var ENVELOPE_GAIN = 0.60;
    var ENVELOPE_RATE = 0.4;
    var ENVELOPE_LANE_STEP = 0.2;
    var RAMP_GAIN_START = 0.35;

    // WAVE-PARTICLES section 3.5
    var BOUNDS = { intensity: [0, 1], speed: [0.25, 2], density: [0, 1] };

    function clamp(value, range) {
        if (typeof value !== 'number' || isNaN(value)) return 1;
        return Math.min(range[1], Math.max(range[0], value));
    }

    function hueOf(value) {
        return ((value % 360) + 360) % 360;
    }

    function hsla(hue, saturation, lightness, alpha) {
        return 'hsla(' + hue.toFixed(2) + ', ' + (saturation * 100).toFixed(1) + '%, ' +
            (lightness * 100).toFixed(1) + '%, ' + alpha.toFixed(4) + ')';
    }

    function uniformFrom(random) {
        return function (low, high) { return low + random() * (high - low); };
    }

    function integerFrom(random) {
        return function (range) { return range[0] + Math.floor(random() * (range[1] - range[0] + 1)); };
    }

    function rollSession(random, profileName, paletteName) {
        var u = uniformFrom(random);
        var n = integerFrom(random);
        var profile = PROFILES[profileName] || PROFILES.full;
        var theta = u(0, 360) * Math.PI / 180;
        var session = {
            dx: Math.cos(theta),
            dy: Math.sin(theta),
            nx: -Math.sin(theta),
            ny: Math.cos(theta),
            lineCount: n(profile.lines),
            step: SAMPLE_STEP * u(SAMPLE_STEP_JITTER[0], SAMPLE_STEP_JITTER[1]),
            stroke: u(STROKE_WIDTH[0], STROKE_WIDTH[1]),
            amplitude: u(AMPLITUDE_FRACTION[0], AMPLITUDE_FRACTION[1]),
            particleCount: n(profile.particles),
            speed: u(SPEED_MULTIPLIER[0], SPEED_MULTIPLIER[1]),
            palette: (PALETTES[paletteName] || PALETTES.DYNAMIC)(u),
            particles: [],
            t: 0,
            f: 0
        };
        for (var i = 0; i < session.particleCount; i++) {
            session.particles.push(rollParticle(u, session));
        }
        return session;
    }

    function rollParticle(u, session) {
        var along = (PARTICLE_SPEED_BASE + u(0, PARTICLE_SPEED_RANGE)) * session.speed;
        if (u(0, 1) < COUNTER_DRIFT_PROBABILITY) along *= COUNTER_DRIFT_FACTOR;
        var jitterX = (u(0, 1) - 0.5) * PARTICLE_JITTER * session.speed;
        var jitterY = (u(0, 1) - 0.5) * PARTICLE_JITTER * session.speed;
        return {
            x: 0,
            y: 0,
            radius: u(PARTICLE_RADIUS[0], PARTICLE_RADIUS[1]),
            vx: session.dx * along + jitterX,
            vy: session.dy * along + jitterY,
            hue: hueOf(session.palette.particleBase + (u(0, 1) - 0.5) * session.palette.spread)
        };
    }

    function seedPositions(u, session, width, height) {
        for (var i = 0; i < session.particles.length; i++) {
            session.particles[i].x = u(0, width);
            session.particles[i].y = u(0, height);
        }
    }

    function rampGain(f) {
        var p = f / RAMP_FRAMES;
        return RAMP_GAIN_START + (1 - RAMP_GAIN_START) * p * (2 - p);
    }

    // WAVE-PARTICLES section 4, one frame covering k reference frames.
    function drawFrame(ctx, session, k, surface) {
        var w = surface.width;
        var h = surface.height;
        session.t += TIME_STEP * surface.speed * k;
        session.f = Math.min(session.f + k, RAMP_FRAMES);
        var g = rampGain(session.f);

        ctx.globalAlpha = 1 - Math.pow(1 - WASH_ALPHA, k);
        ctx.fillStyle = surface.wash;
        ctx.fillRect(0, 0, w, h);
        ctx.globalAlpha = 1;

        var t = session.t;
        var small = Math.min(w, h);
        var drift = Math.sin(CENTRE_DRIFT_RATE * t) * CENTRE_DRIFT_FRACTION * small;
        var cx = w / 2 + session.dx * drift;
        var cy = h / 2 + session.dy * drift;
        var lane = LANE_FRACTION * small;
        var span = Math.sqrt(w * w + h * h) + SPAN_EXTRA_STEPS * session.step;
        var lineAlpha = (LINE_OPACITY_BASE + LINE_OPACITY_GAIN * g) * OPACITY_SCALE;
        var lineLightness = surface.light ? LINE_LIGHTNESS.light : LINE_LIGHTNESS.dark;

        ctx.lineWidth = session.stroke;
        ctx.lineCap = 'round';
        ctx.lineJoin = 'round';
        for (var j = 0; j < session.lineCount; j++) {
            var band = (j - (session.lineCount - 1) / 2) * lane;
            var envelope = ENVELOPE_BASE + ENVELOPE_GAIN * Math.abs(Math.sin(ENVELOPE_RATE * t + ENVELOPE_LANE_STEP * j));
            var amp = h * session.amplitude * g * envelope;
            ctx.beginPath();
            var first = true;
            for (var s = -span / 2; s <= span / 2; s += session.step) {
                var offset = band + Math.sin(SPATIAL_FREQUENCY * s + t + LANE_PHASE_STEP * j) * amp;
                var px = cx + session.dx * s + session.nx * offset;
                var py = cy + session.dy * s + session.ny * offset;
                if (first) { ctx.moveTo(px, py); first = false; } else { ctx.lineTo(px, py); }
            }
            ctx.strokeStyle = hsla(hueOf(session.palette.lineBase + j * session.palette.lineStep),
                LINE_SATURATION, lineLightness, lineAlpha);
            ctx.stroke();
        }

        var particleAlpha = (PARTICLE_OPACITY_BASE + PARTICLE_OPACITY_GAIN * g) * OPACITY_SCALE;
        var particleLightness = surface.light ? PARTICLE_LIGHTNESS.light : PARTICLE_LIGHTNESS.dark;
        var visible = Math.min(session.particles.length, Math.round(session.particles.length * surface.density));
        for (var i = 0; i < session.particles.length; i++) {
            var p = session.particles[i];
            p.x += p.vx * k;
            p.y += p.vy * k;
            // Section 4 as amended in 0.12: reflect the overshoot and turn inward, so a particle that a
            // multi-frame tick carried past the edge is inside again after this step.
            if (p.x < 0) { p.x = Math.min(-p.x, w); p.vx = Math.abs(p.vx); }
            if (p.x > w) { p.x = Math.max(2 * w - p.x, 0); p.vx = -Math.abs(p.vx); }
            if (p.y < 0) { p.y = Math.min(-p.y, h); p.vy = Math.abs(p.vy); }
            if (p.y > h) { p.y = Math.max(2 * h - p.y, 0); p.vy = -Math.abs(p.vy); }
            if (i >= visible) continue;
            ctx.beginPath();
            ctx.arc(p.x, p.y, p.radius, 0, Math.PI * 2);
            ctx.fillStyle = hsla(p.hue, PARTICLE_SATURATION, particleLightness, particleAlpha);
            ctx.fill();
        }
    }

    function mediaQuery(query) {
        return (root.matchMedia && root.matchMedia(query)) || null;
    }

    function onMediaChange(list, handler) {
        if (!list) return;
        if (list.addEventListener) list.addEventListener('change', handler);
        else if (list.addListener) list.addListener(handler);
    }

    /**
     * options: random (function returning [0, 1), default Math.random), palette, profile,
     * isLight (function -> boolean), wash (function -> CSS colour or empty for the contract wash),
     * size (function -> {width, height}; default the canvas's own client size),
     * intensity, speed, density (rule 15), reducedMotion (function -> boolean).
     */
    function create(canvas, options) {
        options = options || {};
        var ctx = canvas.getContext('2d');
        var random = options.random || Math.random;
        var u = uniformFrom(random);
        var paletteName = options.palette && PALETTES[options.palette] ? options.palette : 'DYNAMIC';
        var profileName = options.profile && PROFILES[options.profile] ? options.profile : 'full';
        var surface = {
            width: 0,
            height: 0,
            light: false,
            wash: WASH_DARK,
            speed: clamp(options.speed === undefined ? 1 : options.speed, BOUNDS.speed),
            density: clamp(options.density === undefined ? 1 : options.density, BOUNDS.density)
        };
        if (options.intensity !== undefined) {
            canvas.style.opacity = String(clamp(options.intensity, BOUNDS.intensity));
        }
        var session = null;
        var running = false;
        var frameId = 0;
        var lastTime = null;

        function readSurface() {
            surface.light = !!(options.isLight && options.isLight());
            var hostWash = options.wash ? options.wash() : '';
            surface.wash = hostWash || (surface.light ? WASH_LIGHT : WASH_DARK);
        }

        function measure() {
            if (options.size) return options.size();
            return { width: canvas.clientWidth, height: canvas.clientHeight };
        }

        function fillWash() {
            ctx.globalAlpha = 1;
            ctx.fillStyle = surface.wash;
            ctx.fillRect(0, 0, surface.width, surface.height);
        }

        // Rule 12: carry the old buffer stretched, re-seed positions, keep every other roll and the ramp.
        function resize() {
            var size = measure();
            var width = Math.max(0, Math.round(size.width));
            var height = Math.max(0, Math.round(size.height));
            if (width === surface.width && height === surface.height) return false;
            var carried = null;
            if (surface.width > 0 && surface.height > 0 && width > 0 && height > 0 && root.document) {
                carried = root.document.createElement('canvas');
                carried.width = surface.width;
                carried.height = surface.height;
                carried.getContext('2d').drawImage(canvas, 0, 0);
            }
            canvas.width = width;
            canvas.height = height;
            surface.width = width;
            surface.height = height;
            if (width === 0 || height === 0) return true;
            readSurface();
            fillWash();
            if (carried) ctx.drawImage(carried, 0, 0, width, height);
            if (session) seedPositions(u, session, width, height);
            return true;
        }

        function freshSession() {
            session = rollSession(random, profileName, paletteName);
            seedPositions(u, session, surface.width, surface.height);
            readSurface();
            fillWash();
        }

        function motionAllowed() {
            return !(options.reducedMotion && options.reducedMotion());
        }

        // Rule 9: the frame the animation reaches after RAMP_FRAMES frames, from a fully washed buffer.
        function stillFrame() {
            if (!session) session = rollSession(random, profileName, paletteName);
            session.t = 0;
            session.f = 0;
            seedPositions(u, session, surface.width, surface.height);
            readSurface();
            fillWash();
            for (var i = 0; i < RAMP_FRAMES; i++) drawFrame(ctx, session, 1, surface);
        }

        function tick(now) {
            frameId = 0;
            if (!running) return;
            var k = lastTime === null ? 1 : (now - lastTime) / REFERENCE_FRAME_MS;
            lastTime = now;
            if (k > MAX_FRAMES_PER_TICK) k = MAX_FRAMES_PER_TICK;
            if (k > 0 && surface.width > 0 && surface.height > 0) {
                var wasLight = surface.light;
                var wasWash = surface.wash;
                readSurface();
                if (surface.light !== wasLight || surface.wash !== wasWash) fillWash();
                drawFrame(ctx, session, k, surface);
            }
            frameId = root.requestAnimationFrame(tick);
        }

        function schedule() {
            if (!frameId && running && root.requestAnimationFrame) {
                lastTime = null;
                frameId = root.requestAnimationFrame(tick);
            }
        }

        function halt() {
            if (frameId && root.cancelAnimationFrame) root.cancelAnimationFrame(frameId);
            frameId = 0;
            lastTime = null;
        }

        var api = {
            /** Rule 3: a start from rest rolls a new session; a start while paused resumes. */
            start: function () {
                resize();
                if (!motionAllowed()) {
                    running = false;
                    halt();
                    stillFrame();
                    return api;
                }
                if (!session) freshSession();
                running = true;
                schedule();
                return api;
            },
            /** Rule 10: no frames and no clock until resume(); the session and the buffer are kept. */
            pause: function () {
                running = false;
                halt();
                return api;
            },
            resume: function () {
                if (!session) return api.start();
                if (!motionAllowed()) return api;
                running = true;
                schedule();
                return api;
            },
            /** Back to rest: the next start() rolls a new session. */
            stop: function () {
                api.pause();
                session = null;
                return api;
            },
            resize: function () {
                var changed = resize();
                if (changed && !motionAllowed() && session && surface.width > 0) stillFrame();
                return api;
            },
            /** A held still frame is re-rendered in the new colours; a running one switches on its next tick. */
            refresh: function () {
                if (!motionAllowed() && session) stillFrame();
                return api;
            },
            /** Advance exactly k reference frames without a display clock - for seeded comparison (rung 3). */
            step: function (k) {
                if (!surface.width) resize();
                if (!session) freshSession();
                readSurface();
                drawFrame(ctx, session, k === undefined ? 1 : k, surface);
                return api;
            },
            stillFrame: function () {
                if (!surface.width) resize();
                stillFrame();
                return api;
            },
            isRunning: function () { return running; },
            /** A copy of the rolled session and the clock, for tests and inspection. */
            snapshot: function () {
                return session ? JSON.parse(JSON.stringify(session)) : null;
            }
        };
        return api;
    }

    // Mulberry32: a small deterministic source for rung 3 comparisons. Any uniform generator conforms.
    function seededRandom(seed) {
        var state = seed >>> 0;
        return function () {
            state = (state + 0x6D2B79F5) >>> 0;
            var r = Math.imul(state ^ (state >>> 15), 1 | state);
            r = (r + Math.imul(r ^ (r >>> 7), 61 | r)) ^ r;
            return ((r ^ (r >>> 14)) >>> 0) / 4294967296;
        };
    }

    function numberAttribute(canvas, name) {
        var raw = canvas.getAttribute(name);
        return raw === null || raw === '' ? undefined : parseFloat(raw);
    }

    function themeReader(mode) {
        var doc = root.document;
        if (mode === 'light') return function () { return true; };
        if (mode === 'html') {
            return function () { return doc.documentElement.getAttribute('data-theme') === 'light'; };
        }
        if (mode === 'system') {
            var scheme = mediaQuery('(prefers-color-scheme: light)');
            return function () { return !!(scheme && scheme.matches); };
        }
        return function () { return false; };
    }

    function attach(canvas) {
        if (canvas.waveParticles) return canvas.waveParticles;
        var doc = root.document;
        var themeMode = canvas.getAttribute('data-wave-theme') || 'dark';
        var washVar = canvas.getAttribute('data-wave-wash');
        var sizeMode = canvas.getAttribute('data-wave-size') || 'window';
        var reduced = mediaQuery('(prefers-reduced-motion: reduce)');
        var sizeTarget = sizeMode === 'parent' ? canvas.parentElement : null;
        var instance = create(canvas, {
            palette: canvas.getAttribute('data-palette') || 'DYNAMIC',
            profile: canvas.getAttribute('data-profile') || 'full',
            intensity: numberAttribute(canvas, 'data-intensity'),
            speed: numberAttribute(canvas, 'data-speed'),
            density: numberAttribute(canvas, 'data-density'),
            isLight: themeReader(themeMode),
            wash: washVar ? function () {
                return root.getComputedStyle(doc.documentElement).getPropertyValue(washVar).trim();
            } : null,
            size: function () {
                if (sizeTarget) return { width: sizeTarget.offsetWidth, height: sizeTarget.offsetHeight };
                return { width: root.innerWidth, height: root.innerHeight };
            },
            reducedMotion: function () { return !!(reduced && reduced.matches); }
        });
        canvas.waveParticles = instance;

        if (sizeTarget && root.ResizeObserver) {
            new root.ResizeObserver(function () { instance.resize(); }).observe(sizeTarget);
        } else {
            root.addEventListener('resize', function () { instance.resize(); });
        }
        doc.addEventListener('visibilitychange', function () {
            if (doc.hidden) instance.pause(); else instance.resume();
        });
        onMediaChange(reduced, function () {
            if (reduced.matches) instance.pause(); else instance.resume();
        });
        if (themeMode === 'html' && root.MutationObserver) {
            new root.MutationObserver(function () { instance.refresh(); })
                .observe(doc.documentElement, { attributes: true, attributeFilter: ['data-theme'] });
        } else if (themeMode === 'system') {
            onMediaChange(mediaQuery('(prefers-color-scheme: light)'), function () { instance.refresh(); });
        }
        if (!doc.hidden) instance.start();
        return instance;
    }

    function autoInit() {
        var doc = root.document;
        if (!doc) return [];
        var canvases = doc.querySelectorAll('canvas[data-wave-particles]');
        var started = [];
        for (var i = 0; i < canvases.length; i++) started.push(attach(canvases[i]));
        return started;
    }

    var WaveParticles = {
        version: '0.12',
        create: create,
        attach: attach,
        autoInit: autoInit,
        seededRandom: seededRandom,
        constants: {
            REFERENCE_FRAME_MS: REFERENCE_FRAME_MS,
            TIME_STEP: TIME_STEP,
            RAMP_FRAMES: RAMP_FRAMES,
            WASH_ALPHA: WASH_ALPHA,
            PROFILES: PROFILES,
            PALETTES: ['DYNAMIC', 'GREEN', 'PINK', 'BLUE'],
            BOUNDS: BOUNDS
        }
    };

    if (typeof module === 'object' && module.exports) {
        module.exports = WaveParticles;
    } else {
        root.WaveParticles = WaveParticles;
        if (root.document) {
            if (root.document.readyState === 'loading') {
                root.document.addEventListener('DOMContentLoaded', autoInit);
            } else {
                autoInit();
            }
        }
    }
}(typeof window !== 'undefined' ? window : this));
