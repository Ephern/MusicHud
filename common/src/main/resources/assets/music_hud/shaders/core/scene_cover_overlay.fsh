#version 150

// u_Params.x: master fluid opacity
// u_Params.y: background sampling black overlay
layout(std140) uniform MHOverlay {
    vec4 u_Params;
};

// u_Adjust: (saturation, highlightDarkening, highlightKnee, highlightFull)
// u_Adjust2: (overallBrightness, applyColorAdjust, unused, unused)
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

void main() {
    vec4 scene = texture(Sampler0, texCoord);
    vec4 cover = texture(Sampler1, texCoord);

    float luma = dot(cover.rgb, LUMA_W);
    vec3 coverRgb = mix(vec3(luma), cover.rgb, u_Adjust.x);
    coverRgb = applyHighlightDarkening(coverRgb, u_Adjust.y, u_Adjust.z, u_Adjust.w);
    coverRgb = clamp(coverRgb * u_Adjust2.x, 0.0, 1.0);

    float a = cover.a * u_Params.x;
    vec3 sceneRgb = scene.rgb * (1.0 - clamp(u_Params.y, 0.0, 1.0));
    fragColor = vec4(sceneRgb * (1.0 - a) + coverRgb * u_Params.x, 1.0);
}
