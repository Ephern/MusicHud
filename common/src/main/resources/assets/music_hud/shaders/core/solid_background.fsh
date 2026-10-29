#version 150

// u_Style: (halfWidth, halfHeight, cornerRadius, alpha)
layout(std140) uniform MHPosition {
    mat4 u_Translation;
    vec4 u_Style;
};

layout(std140) uniform MHSolidColor {
    vec4 u_Color;
};

in vec2 f_Position;

out vec4 fragColor;

float aastep(float x) {
    vec2 grad = vec2(dFdx(x), dFdy(x));
    float afwidth = 0.7 * length(grad);
    return smoothstep(-afwidth, afwidth, x);
}

void main() {
    vec2 halfSize = u_Style.xy;
    float radius = u_Style.z;

    vec2 d = abs(f_Position) - halfSize + radius;
    float dis = length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - radius;
    float mask = 1.0 - aastep(dis);

    fragColor = vec4(u_Color.rgb, u_Color.a * mask * u_Style.w);
}
