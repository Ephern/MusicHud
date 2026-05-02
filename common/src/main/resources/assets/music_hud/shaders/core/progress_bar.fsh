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
    float t;
    if (fillWidth < gradientLength) {
        t = clamp(distFromFillRight / fillWidth, 0.0, 1.0);
    } else {
        t = clamp(distFromFillRight / gradientLength, 0.0, 1.0);
    }

    vec4 gradientColor = mix(u_CurrentColor, u_PlayedColor, t);

    float indicatorAlpha;
    if (progress < u_Gradient.z) {
        indicatorAlpha = progress / u_Gradient.z;
    } else if (progress > 1.0 - u_Gradient.z) {
        indicatorAlpha = (1.0 - progress) / u_Gradient.z;
    } else {
        indicatorAlpha = 1.0;
    }

    vec4 dstColor = u_BackgroundColor;
    float dstAlpha = dstColor.a * bgMask;

    vec4 srcColor = gradientColor;
    float srcAlpha = srcColor.a * fillMask * indicatorAlpha;

    float outAlpha = srcAlpha + dstAlpha * (1.0 - srcAlpha);
    vec3 finalRGB;
    if (outAlpha > 0.0) {
        finalRGB = (srcColor.rgb * srcAlpha + dstColor.rgb * dstAlpha * (1.0 - srcAlpha)) / outAlpha;
    } else {
        finalRGB = vec3(0.0);
    }

    if (outAlpha < 0.002) {
        discard;
    }

    fragColor = vec4(finalRGB, outAlpha);
}
