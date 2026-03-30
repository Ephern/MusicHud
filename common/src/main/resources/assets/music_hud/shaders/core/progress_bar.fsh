#version 150

layout(std140) uniform HudProgressParams {
    mat4 u_LocalMat;
    vec4 u_ProgressData;  // (halfWidth, halfHeight, radius, progress)
    vec3 u_GradientOffsets;  // (gradientLength, rightOffset, transitionBorderRate)
    mat4 u_GradientColors;  // 四个角的颜色 (RGBA Vector4f)
//    float u_TransitionBorderRate; removed after 1.21.11
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
    vec2 halfSize = vec2(u_ProgressData.x, u_ProgressData.y);
    float radius = u_ProgressData.z;
    float progress = clamp(u_ProgressData.w, 0.0, 1.0);

    float gradientLength = u_GradientOffsets.x;
    float gradientRightOffset = u_GradientOffsets.y;

    // 1. 计算背景遮罩
    float bgDis = roundedRectSDF(f_Position, halfSize, radius);
    float bgMask = 1.0 - aastep(bgDis);

    // 2. 计算填充区域
    float fillWidth = halfSize.x * progress;
    float fillOffsetX = halfSize.x - fillWidth;
    vec2 fillPos = f_Position + vec2(fillOffsetX, 0.0);

    float fillDis = roundedRectSDF(fillPos, vec2(fillWidth, halfSize.y), radius);
    float fillMask = 1.0 - aastep(fillDis);

    // 3. 计算渐变色 (跟随填充区域)
    // 关键:计算当前像素在填充区域内的相对位置
    // fillPos.x 的范围是 [-fillWidth, fillWidth](相对于填充区域中心)
    // 我们需要从右边缘(fillWidth)开始计算距离
    float distFromFillRight = fillWidth - fillPos.x;

    // 渐变插值参数
    float t;
    if (fillWidth < gradientLength) {
        // 空间不够时,只显示渐变的可见部分
        // 渐变从右边缘开始,但只显示 fillWidth 长度
        t = clamp(distFromFillRight / fillWidth, 0.0, 1.0);
    } else {
        // 空间足够时,渐变固定长度在右侧
        t = clamp(distFromFillRight / gradientLength, 0.0, 1.0);
    }

    // 从 u_GradientColors 矩阵获取颜色
    vec4 fillColorLeft = u_GradientColors[0];   // 左侧颜色
    vec4 fillColorRight = u_GradientColors[1];  // 右侧颜色
    vec4 bgColor = u_GradientColors[2];

    // 线性插值渐变色(从右到左)
    vec4 gradientColor = mix(fillColorRight, fillColorLeft, t);

    // 4. 关键:基于边界的透明度变化(仅应用于填充指示器)
    // 计算填充区域内每个像素相对于填充区域左边缘的归一化位置
    float normalizedFillPos = (fillPos.x + fillWidth) / (2.0 * fillWidth);  // 范围 [0, 1]

    float indicatorAlpha;
    if (progress < u_GradientOffsets.z) {
        indicatorAlpha = progress / u_GradientOffsets.z;
    } else if (progress > 1 - u_GradientOffsets.z) {
        indicatorAlpha = (1 - progress) / u_GradientOffsets.z;
    } else {
        indicatorAlpha = 1.0;
    }

    vec4 dstColor = bgColor;
    float dstAlpha = dstColor.a * bgMask;

    vec4 srcColor = gradientColor;
    float srcAlpha = srcColor.a * fillMask * indicatorAlpha;

    // Porter-Duff "Source Over" 公式
    float outAlpha = srcAlpha + dstAlpha * (1.0 - srcAlpha);

    vec3 finalRGB;
    if (outAlpha > 0.0) {
        // 预乘 alpha 混合
        finalRGB = (srcColor.rgb * srcAlpha + dstColor.rgb * dstAlpha * (1.0 - srcAlpha)) / outAlpha;
    } else {
        finalRGB = vec3(0.0);
    }

    if (outAlpha < 0.002) {
        discard;
    }

    fragColor = vec4(finalRGB, outAlpha);
}