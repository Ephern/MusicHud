#version 150

uniform sampler2D Sampler0;  // 当前图片
uniform sampler2D Sampler1;  // 下一张图片

layout(std140) uniform HudBackgroundParams {
    mat4 u_Translation;
    vec4 u_RectParam;  // (halfWidth, halfHeight, radius, unused)
    vec3 u_TransitionParam;  // (fadeProgress, nextImageAspect, imageAspect)
    mat4 u_BgColors;
//    float u_Progress; removed after 1.21.11
};

in vec2 f_Position;
in vec4 f_Color;

out vec4 fragColor;

float aastep(float x) {
    vec2 grad = vec2(dFdx(x), dFdy(x));
    float afwidth = 0.7 * length(grad);
    return smoothstep(-afwidth, afwidth, x);
}

vec4 dither(vec4 color) {
    vec2 A = gl_FragCoord.xy;
    vec2 B = floor(A);
    float U = fract(B.x * 0.5 + B.y * B.y * 0.75);
    vec2 C = A * 0.5;
    vec2 D = floor(C);
    float V = fract(D.x * 0.5 + D.y * D.y * 0.75);
    vec2 E = C * 0.5;
    vec2 F = floor(E);
    float W = fract(F.x * 0.5 + F.y * F.y * 0.75);
    float dithering = ((W * 0.25 + V) * 0.25 + U) - (63.0 / 128.0);
    return vec4(clamp(color.rgb + dithering * (1.0 / 255.0), 0.0, 1.0), color.a);
}

vec2 calculateCoverUV(vec2 position, vec2 halfSize, float imageAspect) {
    float rectAspect = halfSize.x / halfSize.y;

    // 简单的cover计算
    vec2 scale;
    if (imageAspect > rectAspect) {
        // 图片宽，需要横向裁剪
        scale = vec2(rectAspect / imageAspect, 1.0);
    } else {
        // 图片高，需要纵向裁剪
        scale = vec2(1.0, imageAspect / rectAspect);
    }

    vec2 normalizedPos = (position / halfSize) * 0.5 + 0.5;

    // 关键修正：从中心缩放
    vec2 uv = (normalizedPos - 0.5) * scale + 0.5;

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

    // 四角渐变背景色
    vec2 t = (f_Position + halfSize) / (halfSize * 2.0);

    // 从 u_BgColors 获取四个角的颜色(在线性空间中插值)
    vec3 colorTL = pow(u_BgColors[0].rgb, vec3(2.2));
    vec3 colorTR = pow(u_BgColors[1].rgb, vec3(2.2));
    vec3 colorBR = pow(u_BgColors[2].rgb, vec3(2.2));
    vec3 colorBL = pow(u_BgColors[3].rgb, vec3(2.2));

    // 同时获取四个角的 alpha 值
    float alphaTL = u_BgColors[0].a;
    float alphaTR = u_BgColors[1].a;
    float alphaBR = u_BgColors[2].a;
    float alphaBL = u_BgColors[3].a;

    // 双线性插值 RGB
    vec3 colorTop = mix(colorTL, colorTR, t.x);
    vec3 colorBottom = mix(colorBL, colorBR, t.x);
    vec3 gradientColor = mix(colorTop, colorBottom, t.y);

    // 双线性插值 alpha
    float alphaTop = mix(alphaTL, alphaTR, t.x);
    float alphaBottom = mix(alphaBL, alphaBR, t.x);
    float backgroundAlpha = mix(alphaTop, alphaBottom, t.y);

    // 转换回 sRGB
    vec3 backgroundColor = pow(gradientColor, vec3(1.0/2.2));

    // 叠加到图片上
    vec3 finalRGB = mix(finalImage.rgb, backgroundColor, backgroundAlpha);

    // 正确处理最终 alpha
    float finalAlpha = max(finalImage.a, backgroundAlpha) * mask;

    fragColor = dither(vec4(finalRGB, finalAlpha));
    if (fragColor.a < 0.002) {
        discard;
    }

}
