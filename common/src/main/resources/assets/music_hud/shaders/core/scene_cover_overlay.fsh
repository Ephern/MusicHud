#version 150

// u_Params.x: master fluid opacity
// u_Params.y: background sampling black overlay
layout(std140) uniform MHOverlay {
    vec4 u_Params;
};

// u_Adjust: (natural saturation, highlightDarkening, highlightKnee, highlightFull)
// u_Adjust2: (overallBrightness, applyColorAdjust, unused, gammaToneMapping)
layout(std140) uniform MHBackgroundAdjust {
    vec4 u_Adjust;
    vec4 u_Adjust2;
};

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

in vec2 texCoord;
out vec4 fragColor;

const vec3 LUMA_W = vec3(0.2126, 0.7152, 0.0722);

vec3 applyHighlightDarkening(vec3 rgb, float strength,
                             float highlightKnee, float highlightFull) {
    vec3 c = clamp(rgb, 0.0, 1.0);
    float value = max(c.r, max(c.g, c.b));

    float knee = clamp(highlightKnee, 0.0, 1.0);
    float full = max(highlightFull, knee + 1e-4);
    float highlight = smoothstep(knee, full, value);

    return rgb * (1.0 - clamp(strength, 0.0, 1.0) * highlight);
}

// Vibrance-style saturation: muted colors gain more chroma, already saturated colors
// gain little or none, avoiding the clipping of a traditional luma mix.
vec3 applyNaturalSaturation(vec3 rgb, float saturation) {
    float amount = saturation - 1.0;
    float maxC = max(rgb.r, max(rgb.g, rgb.b));
    float minC = min(rgb.r, min(rgb.g, rgb.b));
    float chroma = maxC - minC;
    float relative = maxC > 1e-4 ? chroma / maxC : 0.0;
    float weight = amount > 0.0 ? 1.0 - smoothstep(0.25, 0.9, relative) : 1.0;
    float luma = dot(rgb, LUMA_W);
    return vec3(luma) + (rgb - vec3(luma)) * (1.0 + amount * weight);
}

// Brightness is applied as a single scale factor so it stays independent from saturation.
// GAMMA applies the curve to the maximum channel instead of each channel separately.
vec3 applyBrightness(vec3 rgb, float brightness, float gammaMode) {
    float b = max(brightness, 0.0);
    if (gammaMode < 0.5) {
        return rgb * b;
    }
    if (b <= 1e-4) {
        return vec3(0.0);
    }
    float value = max(rgb.r, max(rgb.g, rgb.b));
    if (value <= 1e-4) {
        return vec3(0.0);
    }
    float mapped = pow(clamp(value, 0.0, 1.0), 1.0 / b);
    return rgb * (mapped / value);
}

void main() {
    vec4 scene = texture(Sampler0, texCoord);
    vec4 cover = texture(Sampler1, texCoord);

    vec3 coverRgb = applyNaturalSaturation(cover.rgb, u_Adjust.x);
    coverRgb = applyHighlightDarkening(coverRgb, u_Adjust.y, u_Adjust.z, u_Adjust.w);
    coverRgb = clamp(applyBrightness(coverRgb, u_Adjust2.x, u_Adjust2.w), 0.0, 1.0);

    float a = cover.a * u_Params.x;
    vec3 sceneRgb = scene.rgb * (1.0 - clamp(u_Params.y, 0.0, 1.0));
    fragColor = vec4(sceneRgb * (1.0 - a) + coverRgb * u_Params.x, 1.0);
}
