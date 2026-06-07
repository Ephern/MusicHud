#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

layout(std140) uniform MHAlbumPosition {
    mat4 u_Translation;
    vec3 u_Layout; // (halfWidth, halfHeight, cornerRadius)
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

vec2 calculateCoverUV(vec2 position, vec2 halfSize) {
    float rectAspect = halfSize.x / halfSize.y;
    const float epsilon = 0.001;
    const float imageAspect = 1.0;
    rectAspect = max(rectAspect, epsilon);
    float portrait = step(rectAspect, imageAspect);
    vec2 scale = mix(vec2(1.0, rectAspect / imageAspect), vec2(imageAspect / rectAspect, 1.0), portrait);
    vec2 normalizedPos = (position / halfSize) * 0.5 + 0.5;
    vec2 uv = (normalizedPos - 0.5) / scale + 0.5;
    return clamp(uv, 0.0, 1.0);
}

void main() {
    vec2 halfSize = u_Layout.xy;
    float radius = u_Layout.z;
    float fadeProgress = u_Dynamic1[2];

    vec2 d = abs(f_Position) - halfSize + radius;
    float dis = length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - radius;
    float mask = 1.0 - aastep(dis);

    vec2 currentUV = calculateCoverUV(f_Position, halfSize);
    vec4 currentImage = texture(Sampler0, currentUV);

    vec2 nextUV = calculateCoverUV(f_Position, halfSize);
    vec4 nextImage = texture(Sampler1, nextUV);
    float t = smoothstep(0.0, 1.0, fadeProgress);
    vec4 finalImage = mix(currentImage, nextImage, t);

    fragColor = vec4(finalImage.rgb, finalImage.a * mask);
}
