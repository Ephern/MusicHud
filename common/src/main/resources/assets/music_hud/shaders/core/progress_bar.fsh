#version 150

layout(std140) uniform MHProgressPosition {
    mat4 u_Translation;
    vec3 u_Layout; // (halfWidth, halfHeight, cornerRadius)
};
layout(std140) uniform MHProgressStyle {
    vec3 u_Gradient; // (gradientLength, rightOffset, transitionBorderRate)
    vec4 u_PlayedColor;
    vec4 u_CurrentColor;
    vec4 u_BackgroundColor;
};
layout(std140) uniform MHDynamicStatus {
    vec4 u_Dynamic1; // (timestamp, playedProgress, switchProgress)
};

in vec2 f_Position;
in vec4 f_Color;

out vec4 fragColor;

float aastep(float x) {
    vec2 grad = vec2(dFdx(x), dFdy(x));
    float afwidth = 0.7 * length(grad);
    return smoothstep(-afwidth, afwidth, x);
}

float roundedRectSDF(vec2 position, vec2 halfSize, float radius) {
    vec2 d = abs(position) - halfSize + radius;
    return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - radius;
}

void main() {
    vec2 halfSize = vec2(u_Layout.x, u_Layout.y);
    float radius = u_Layout.z;
    float progress = clamp(u_Dynamic1[1], 0.0, 1.0);

    float gradientLength = u_Gradient.x;
    float gradientRightOffset = u_Gradient.y;

    float bgDis = roundedRectSDF(f_Position, halfSize, radius);
    float bgMask = 1.0 - aastep(bgDis);

    float fillWidth = halfSize.x * progress;
    float fillOffsetX = halfSize.x - fillWidth;
    vec2 fillPos = f_Position + vec2(fillOffsetX, 0.0);

    float fillDis = roundedRectSDF(fillPos, vec2(fillWidth, halfSize.y), radius);
    float fillMask = 1.0 - aastep(fillDis);

    float distFromFillRight = fillWidth - fillPos.x;
    float narrow = step(fillWidth, gradientLength);
    float denom = mix(gradientLength, fillWidth, narrow);
    float t = clamp(distFromFillRight / denom, 0.0, 1.0);

    vec4 gradientColor = mix(u_CurrentColor, u_PlayedColor, t);

    float fadingIn = 1.0 - step(u_Gradient.z, progress);
    float full = step(u_Gradient.z, progress) * step(progress, 1.0 - u_Gradient.z);
    float fadingOut = 1.0 - step(progress, 1.0 - u_Gradient.z);
    float indicatorAlpha = fadingIn * progress / u_Gradient.z
                         + full * 1.0
                         + fadingOut * (1.0 - progress) / u_Gradient.z;

    vec4 dstColor = u_BackgroundColor;
    float dstAlpha = dstColor.a * bgMask;

    vec4 srcColor = gradientColor;
    float srcAlpha = srcColor.a * fillMask * indicatorAlpha;

    float outAlpha = srcAlpha + dstAlpha * (1.0 - srcAlpha);
    vec3 finalRGB = (srcColor.rgb * srcAlpha + dstColor.rgb * dstAlpha * (1.0 - srcAlpha)) / max(outAlpha, 0.0001);

    fragColor = vec4(finalRGB, outAlpha);
}
