#version 330
#extension GL_ARB_separate_shader_objects : require

// Not in the original: the menu background, in the original's colours, shot like a film. Near-black; a fire just below the screen
// whose light (the theme's colour, warmed) falls off upwards and rises in shafts through the haze. Smoke at two depths,
// grey in the dark and lit red-orange where the fire reaches it, brightest on its undersides and along thin wisps.
// Embers at three depths: tiny far ones, bright mid ones trailing motion streaks, and blurred discs right in front of
// the lens; hot near the fire, cooling to red as they climb. A slow camera drift gives parallax. Graded with a filmic
// curve, cool shadows, a heavy vignette and film grain.

layout(std140) uniform Globals {
    ivec3 CameraBlockPos;
    float GlintAlpha;
    vec3 CameraOffset;
    float GameTime;
    vec2 ScreenSize;
    int MenuBlurRadius;
    int UseRgss;
};
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};

layout(location = 0) in vec2 params;  // x: seconds, y: 0 for the title screen, 1 for the other menus (calmer, darker)
layout(location = 1) in vec4 accent;  // the accent colour; alpha is the opacity

layout(location = 0) out vec4 fragColor;

// ---- noise ----

vec2 hash22(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy) * 2.0 - 1.0;
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

// Gradient noise with a quintic fade: smooth, no blobs; about -0.7..0.7.
float gnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    float a = dot(hash22(i), f);
    float b = dot(hash22(i + vec2(1.0, 0.0)), f - vec2(1.0, 0.0));
    float c = dot(hash22(i + vec2(0.0, 1.0)), f - vec2(0.0, 1.0));
    float d = dot(hash22(i + vec2(1.0, 1.0)), f - vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

// Five octaves, each turned and shifted so their grids never line up.
const mat2 OCTAVE = mat2(1.6, 1.2, -1.2, 1.6);

float fbm(vec2 p) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 5; i++) {
        sum += amp * gnoise(p);
        p = OCTAVE * p + vec2(1.7, 9.2);
        amp *= 0.5;
    }
    return sum;
}

// Interleaved gradient noise: an even, pattern-free dither.
float dither(vec2 frag) {
    return fract(52.9829189 * fract(dot(frag, vec2(0.06711056, 0.00583715))));
}

// ---- smoke ----

// One depth of smoke, rising at `speed` and curled by a slow noise field. Returns its soft body, the thin bright wisps
// along two of its folds (fading in and out along their length), and how lit its underside is (density here against
// just below: the edges facing the fire).
vec3 smoke(vec2 p, float t, float scale, float speed, float seed) {
    vec2 q = p * scale + vec2(seed, -t * speed);
    vec2 w = vec2(fbm(q * 0.7 + vec2(0.0, t * 0.05)), fbm(q * 0.7 + vec2(5.2, 1.3 - t * 0.04)));
    vec2 r = q + w * 1.7 + vec2(t * 0.03, 0.0);
    float n = fbm(r);
    float below = fbm(r - vec2(0.0, 0.05 * scale));
    float body = smoothstep(-0.12, 0.45, n);
    float wisps = exp(-pow((n - 0.16) / 0.028, 2.0)) + 0.6 * exp(-pow((n + 0.04) / 0.022, 2.0));
    wisps *= smoothstep(-0.25, 0.2, w.x);
    float lit = clamp((n - below) * 7.0, 0.0, 1.0);
    return vec3(body, wisps, lit);
}

// ---- embers ----

// One depth of embers: a grid of cells rising at `speed`, swaying; `share` of the cells hold a spark somewhere near
// their middle. A spark is a hot core trailing a short streak below it (its motion over a film frame) inside a glow;
// faded out before its cell's edge so it is never cut off. Returns (core, glow).
vec2 embers(vec2 p, float scale, float speed, float share, float streak, float t, float seed) {
    vec2 q = p * scale;
    q.y -= t * speed;
    q.x += sin(q.y * 0.45 + seed + t * 0.25) * 0.45;
    vec2 cell = floor(q);
    float h = hash12(cell + seed);
    if (h < 1.0 - share) return vec2(0.0);
    vec2 local = fract(q) - 0.5;
    vec2 at = (vec2(hash12(cell + seed + 1.3), hash12(cell + seed + 2.7)) - 0.5) * 0.4;
    vec2 d = local - at;
    // Distance to the streak: a segment from the spark down to where it was.
    float len = streak * mix(0.6, 1.4, hash12(cell + seed + 4.1));
    float s = clamp(-d.y / max(len, 1e-4), 0.0, 1.0);
    vec2 off = d + vec2(0.0, s * len);
    float r2 = dot(off, off);
    float size = mix(0.6, 1.3, hash12(cell + seed + 5.9));
    float flicker = 0.55 + 0.45 * sin(t * (3.0 + 5.0 * h) + h * 60.0);
    float inside = smoothstep(0.5, 0.36, max(abs(local.x), abs(local.y)));
    float k = flicker * inside * (1.0 - s * 0.6);
    return vec2(exp(-r2 / (0.00035 * size)), exp(-r2 / (0.006 * size)) * 0.35) * k;
}

// Out-of-focus embers right in front of the lens: soft discs with a slightly brighter rim.
float bokeh(vec2 p, float scale, float speed, float share, float t, float seed) {
    vec2 q = p * scale;
    q.y -= t * speed;
    q.x += sin(q.y * 0.3 + seed + t * 0.15) * 0.3;
    vec2 cell = floor(q);
    float h = hash12(cell + seed);
    if (h < 1.0 - share) return 0.0;
    vec2 at = (vec2(hash12(cell + seed + 1.3), hash12(cell + seed + 2.7)) - 0.5) * 0.25;
    float r = mix(0.10, 0.17, hash12(cell + seed + 3.3));
    float d = length(fract(q) - 0.5 - at);
    float disc = smoothstep(r, r * 0.82, d) * (0.75 + 0.25 * smoothstep(r * 0.4, r * 0.9, d));
    return disc * (0.6 + 0.4 * sin(t * 0.8 + h * 30.0));
}

void main() {
    vec2 res = max(ScreenSize, vec2(1.0));
    vec2 frag = gl_FragCoord.xy;
    vec2 uv = frag / res;
    float aspect = res.x / res.y;
    vec2 p = (uv - 0.5) * vec2(aspect, 1.0);  // screen heights, centred; y up
    float t = params.x;
    float calm = params.y;

    // The fire's light: the theme's colour warmed a little, coming from just below the screen and falling off upwards.
    vec3 fire = mix(accent.rgb, vec3(1.0, 0.42, 0.16), 0.35);
    float light = exp(-(p.y + 0.62) * 1.9);
    float flick = 0.85 + 0.15 * gnoise(vec2(t * 0.8, 3.7)) + 0.08 * gnoise(vec2(t * 2.3, 9.1));

    // A slow drift of the camera; the nearer a layer, the more it moves.
    vec2 cam = vec2(sin(t * 0.07), cos(t * 0.05)) * 0.02;

    // Near-black, a little cool, and the fire's glow along the bottom edge.
    vec3 col = vec3(0.008, 0.009, 0.012);
    col += fire * (0.05 * light * light + 0.4 * exp(-(p.y + 0.5) * 8.0)) * flick;

    // Shafts of firelight rising through the haze, leaning slightly.
    float shafts = smoothstep(0.05, 0.55, gnoise(vec2(p.x * 2.6 - p.y * 0.5 + t * 0.02, t * 0.05)));
    col += fire * shafts * light * 0.07 * flick;

    // Smoke at two depths: far (broad, slow, dim) and near (finer, quicker). Grey in the dark, red-orange where the
    // fire reaches it, brightest on its lit undersides and along its wisps; thicker low down.
    vec3 grey = vec3(0.50, 0.52, 0.58);
    vec3 far = smoke(p + cam * 0.3, t, 1.1, 0.05, 3.0);
    vec3 near = smoke(p + cam, t, 2.0, 0.11, 17.0);
    float lowFar = smoothstep(0.8, -0.5, p.y);
    float lowNear = smoothstep(0.55, -0.6, p.y);
    col += (grey * 0.045 * (far.x * 0.6 + far.y * 0.5)
          + fire * light * flick * (far.x * 0.14 + far.y * 0.30 + far.z * far.x * 0.35)) * lowFar * 0.7;
    col += (grey * 0.040 * (near.x * 0.6 + near.y * 0.7)
          + fire * light * flick * (near.x * 0.18 + near.y * 0.55 + near.z * near.x * 0.65)) * lowNear;

    // Embers: far ones tiny, mid ones bright with streaks, and a few blurred ones right in front. Hot cores going
    // from near-white low down to deep red as they cool on the way up; their glow tinted by the fire.
    float heat = smoothstep(0.65, -0.55, p.y);
    vec3 core = mix(fire * 0.9, vec3(1.0, 0.86, 0.62), heat * 0.8);
    vec2 e1 = embers(p + cam * 0.4, 24.0, 0.70, 0.07, 0.10, t, 3.0);
    vec2 e2 = embers(p + cam * 0.8, 11.0, 0.42, 0.10, 0.22, t, 11.0);
    float b = bokeh(p + cam * 1.6, 3.2, 0.14, 0.12, t, 23.0);
    col += (core * (e1.x * 0.6 + e2.x * 1.6) + fire * (e1.y * 0.5 + e2.y * 1.2)) * (0.25 + 0.75 * heat);
    col += fire * b * 0.10 * (0.4 + 0.6 * heat);

    // Grade: the pages' calmer version, a filmic curve, cool shadows against warm light, a heavy vignette, then film
    // grain (stronger in the dark) and dither against banding.
    col *= mix(1.0, 0.65, calm);
    col = col * (2.51 * col + 0.03) / (col * (2.43 * col + 0.59) + 0.14);
    float luma = dot(col, vec3(0.2126, 0.7152, 0.0722));
    col = mix(col * vec3(0.90, 0.97, 1.12), col, smoothstep(0.0, 0.18, luma));
    vec2 v = (uv - 0.5) * vec2(aspect * 0.75, 1.0);
    col *= 1.0 - 0.65 * smoothstep(0.25, 1.05, length(v));
    col += (hash12(frag + fract(t * 7.13) * 97.0) - 0.5) * 0.022 * (1.0 - 0.6 * luma) + (dither(frag) - 0.5) / 255.0;

    fragColor = vec4(max(col, 0.0), accent.a) * ColorModulator;
}
