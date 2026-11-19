package io.github.zainbits.vitreous

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.ui.graphics.toArgb
import kotlin.math.cos
import kotlin.math.sin

/** Most shapes one glass pass can draw (a bar's buttons and title, a composer row). */
internal const val MAX_SHAPES = 8

/**
 * Everything after the blur happens in this one pass, for up to [MAX_SHAPES] rounded rectangles
 * at once: anti-aliased coverage (so no clip path is needed), optional liquid merging of nearby
 * shapes, edge refraction, colour boost, tint and the lit rim.
 *
 * Coordinates are the pass's own pixels. `content` is the (already blurred) content behind it.
 */
private val GLASS_AGSL = """
uniform shader content;
uniform float2 uSize;
uniform float uCount;
uniform float uRects[${MAX_SHAPES * 4}];
uniform float uRadii[$MAX_SHAPES];
uniform float uDeform[${MAX_SHAPES * 4}];
uniform float uMerge;
uniform float uBand;
uniform float uDepth;
uniform float uSaturation;
layout(color) uniform half4 uTint;
uniform float uRim;
uniform float uRimWidth;
uniform float2 uLight;
uniform float uDispersion;
uniform float uBlur;

// Distance to one rounded rectangle (negative inside) and its outward edge normal. The normal
// comes from a rounder copy of the shape (radius at least the band) so its direction is smooth
// wherever the refraction band reaches. (ux, uy, st, sq) stretch the shape by st along the unit
// direction u and by sq across it, for the elastic pull.
float3 shape(float2 xy, float x, float y, float w, float hgt, float rad, float ux, float uy, float st, float sq) {
    float2 h = float2(w, hgt) * 0.5;
    float2 p = xy - float2(x, y) - h;
    float2 u = float2(ux, uy);
    float2 v = float2(-uy, ux);
    p = (dot(p, u) / st) * u + (dot(p, v) / sq) * v;
    float m = min(h.x, h.y);
    float r = min(rad, m);
    float2 q = abs(p) - (h - r);
    float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
    float rb = min(max(r, uBand), m);
    float2 qb = abs(p) - (h - rb);
    float2 nb = (qb.x > 0.0 && qb.y > 0.0) ? normalize(qb) : (qb.x > qb.y ? float2(1.0, 0.0) : float2(0.0, 1.0));
    float2 sp = float2(p.x >= 0.0 ? 1.0 : -1.0, p.y >= 0.0 ? 1.0 : -1.0);
    float2 n0 = nb * sp;
    float2 n = (dot(n0, u) / st) * u + (dot(n0, v) / sq) * v;
    n = n / max(length(n), 0.0001);
    return float3(d * min(st, sq), n);
}

float2 clampIn(float2 s) {
    return clamp(s, float2(0.5), uSize - 0.5);
}

// Single-pass blur: 12 taps on a Vogel (golden-angle) disk of radius uBlur. Used when the
// content is a small, already smooth copy scaled up, so few taps look even.
half4 blurAt(float2 s) {
    if (uBlur <= 0.0) {
        return content.eval(s);
    }
    half4 acc = half4(0.0);
    float2 dir = float2(1.0, 0.0);
    // rotation by the golden angle (2.39996 rad)
    float ca = -0.7373688;
    float sa = 0.6754903;
    for (int i = 0; i < 12; i++) {
        float r = sqrt((float(i) + 0.5) / 12.0) * uBlur;
        acc += content.eval(clampIn(s + dir * r));
        dir = float2(dir.x * ca - dir.y * sa, dir.x * sa + dir.y * ca);
    }
    return acc / 12.0;
}

half4 main(float2 xy) {
    float d = 100000.0;
    float2 n = float2(0.0, -1.0);
    for (int i = 0; i < $MAX_SHAPES; i++) {
        if (float(i) < uCount) {
            float3 s = shape(xy, uRects[i * 4], uRects[i * 4 + 1], uRects[i * 4 + 2], uRects[i * 4 + 3], uRadii[i],
                uDeform[i * 4], uDeform[i * 4 + 1], uDeform[i * 4 + 2], uDeform[i * 4 + 3]);
            if (uMerge > 0.0) {
                // Smooth union: shapes closer than uMerge flow into each other like liquid.
                float k = clamp(0.5 + 0.5 * (d - s.x) / uMerge, 0.0, 1.0);
                d = mix(d, s.x, k) - uMerge * k * (1.0 - k);
                float2 mn = mix(n, s.yz, k);
                n = mn / max(length(mn), 0.0001);
            } else if (s.x < d) {
                d = s.x;
                n = s.yz;
            }
        }
    }
    float cover = clamp(0.5 - d, 0.0, 1.0);
    if (cover <= 0.0) {
        return half4(0.0);
    }
    float inside = -d;

    // Lens: 0 inside the band, rising to uDepth at the edge along a circular profile.
    float t = clamp(1.0 - inside / uBand, 0.0, 1.0);
    float bend = uDepth * (1.0 - sqrt(1.0 - t * t));
    float2 s = clampIn(xy - n * bend);

    half4 c = blurAt(s);
    if (uDispersion > 0.0) {
        float2 o = n * bend * uDispersion;
        c.r = blurAt(clampIn(s + o)).r;
        c.b = blurAt(clampIn(s - o)).b;
    }

    // Colour boost, then tint.
    half l = dot(c.rgb, half3(0.2126, 0.7152, 0.0722));
    c.rgb = clamp(mix(half3(l), c.rgb, half(uSaturation)), 0.0, c.a);
    c.rgb = mix(c.rgb, uTint.rgb, uTint.a);
    c.a = mix(c.a, 1.0, uTint.a);

    // Light catching the rim: strongest where the edge faces the light, softer opposite.
    float facing = dot(n, uLight);
    float lit = pow(max(facing, 0.0), 1.5) + 0.45 * pow(max(-facing, 0.0), 1.5);
    float rim = clamp(1.0 - inside / uRimWidth, 0.0, 1.0);
    float glow = uRim * (rim * lit + 0.10 * t * t);
    c.rgb = c.rgb + (c.a - c.rgb) * half(glow);

    return c * half(cover);
}
"""

/**
 * One compiled shader for the whole process. A [RenderEffect] snapshots the uniforms when it is
 * created, so reusing the shader object for every pass is safe on the UI thread.
 */
internal object GlassShader {
    private val shader by lazy { RuntimeShader(GLASS_AGSL) }

    /**
     * @param rects x, y, w, h per shape in pass pixels; [count] shapes are used.
     * @param radii corner radius per shape in pixels.
     */
    fun effect(
        width: Float,
        height: Float,
        rects: FloatArray,
        radii: FloatArray,
        deform: FloatArray,
        count: Int,
        mergePx: Float,
        bandPx: Float,
        depthPx: Float,
        rimWidthPx: Float,
        shaderBlurPx: Float,
        style: GlassStyle,
    ): RenderEffect {
        val s = shader
        s.setFloatUniform("uSize", width, height)
        s.setFloatUniform("uCount", count.toFloat())
        s.setFloatUniform("uRects", rects)
        s.setFloatUniform("uRadii", radii)
        s.setFloatUniform("uDeform", deform)
        s.setFloatUniform("uMerge", mergePx)
        s.setFloatUniform("uBand", bandPx.coerceAtLeast(1f))
        s.setFloatUniform("uDepth", depthPx)
        s.setFloatUniform("uSaturation", style.saturation)
        s.setColorUniform("uTint", style.tint.copy(alpha = style.tintAlpha.coerceIn(0f, 1f)).toArgb())
        s.setFloatUniform("uRim", style.rim)
        s.setFloatUniform("uRimWidth", rimWidthPx.coerceAtLeast(0.5f))
        val a = Math.toRadians(style.lightAngle.toDouble())
        s.setFloatUniform("uLight", cos(a).toFloat(), sin(a).toFloat())
        s.setFloatUniform("uDispersion", style.dispersion)
        s.setFloatUniform("uBlur", shaderBlurPx)
        VitreousDebug.effectBuilds++
        return RenderEffect.createRuntimeShaderEffect(s, "content")
    }

    fun blur(px: Float): RenderEffect = RenderEffect.createBlurEffect(px, px, Shader.TileMode.CLAMP)
}
