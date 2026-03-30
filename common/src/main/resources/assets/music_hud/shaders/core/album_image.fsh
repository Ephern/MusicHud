#version 150

uniform sampler2D Sampler0;  // 当前未模糊图片
uniform sampler2D Sampler1;  // 下一张未模糊图片

layout(std140) uniform HudAlbumParams {
    mat4 u_Translation;
    vec4 u_RectParam;  // (halfWidth, halfHeight, radius, unused)
    vec3 u_TransitionParam;  // (fadeProgress, nextImageAspect, imageAspect)
    mat4 u_BgColors;
//    float u_Progress; removed after 1.21.11
};

in vec2 f_Position;
in vec4 f_Color;

out vec4 fragColor;

// 抗锯齿步进函数
float aastep(float x) {
    vec2 grad = vec2(dFdx(x), dFdy(x));
    float afwidth = 0.7 * length(grad);
    return smoothstep(-afwidth, afwidth, x);
}

vec2 calculateCoverUV(vec2 position, vec2 halfSize, float imageAspect) {
    float rectAspect = halfSize.x / halfSize.y;

    // 精度保护
    const float epsilon = 0.001;
    imageAspect = max(imageAspect, epsilon);
    rectAspect = max(rectAspect, epsilon);

    // 计算缩放因子 - 关键修正
    vec2 scale;
    if (imageAspect > rectAspect) {
        // 图片更宽 -> 垂直方向填满,水平方向裁剪
        scale = vec2(imageAspect / rectAspect, 1.0);
    } else {
        // 图片更高 -> 水平方向填满,垂直方向裁剪
        scale = vec2(1.0, rectAspect / imageAspect);
    }

    // 归一化位置到 [0, 1]
    vec2 normalizedPos = (position / halfSize) * 0.5 + 0.5;

    // 应用缩放并居中 - 关键步骤
    vec2 uv = (normalizedPos - 0.5) / scale + 0.5;

    // 钳制到有效范围
    return clamp(uv, 0.0, 1.0);
}

void main() {
    vec2 halfSize = u_RectParam.xy;
    float radius = u_RectParam.z;

    float fadeProgress = u_TransitionParam.x;
    float currentImageAspect = u_TransitionParam.y;
    float nextImageAspect = u_TransitionParam.z;

    // 计算圆角矩形遮罩
    vec2 d = abs(f_Position) - halfSize + radius;
    float dis = length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - radius;
    float mask = 1.0 - aastep(dis);

    // 计算当前图片的 UV 坐标
    vec2 currentUV = calculateCoverUV(f_Position, halfSize, currentImageAspect);
    vec4 currentImage = texture(Sampler0, currentUV);

    // 处理图片过渡
    vec4 finalImage = currentImage;
    if (fadeProgress > 0.0 && fadeProgress < 1.0) {
        vec2 nextUV = calculateCoverUV(f_Position, halfSize, nextImageAspect);
        vec4 nextImage = texture(Sampler1, nextUV);
        float t = smoothstep(0.0, 1.0, fadeProgress);
        finalImage = mix(currentImage, nextImage, t);
    }

    // 应用圆角遮罩
    fragColor = vec4(finalImage.rgb, finalImage.a * mask);

    if (fragColor.a < 0.002) {
        discard;
    }
}