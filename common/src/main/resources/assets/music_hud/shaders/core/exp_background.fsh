#version 150

layout(std140) uniform MHPosition {
    mat4 u_Translation;
    vec4 u_Style;
};

uniform sampler2D Sampler0;

in vec2 f_Position;
in vec4 f_Color;

out vec4 fragColor;

const float BRIGHTNESS = 0.5;

float aastep(float x) {
    vec2 grad = vec2(dFdx(x), dFdy(x));
    float afwidth = 0.7 * length(grad);
    return smoothstep(-afwidth, afwidth, x);
}

void main() {
    vec2 halfSize = u_Style.xy;
    float radius = u_Style.z;

    vec2 uv = clamp(f_Position / (halfSize * 2.0) + 0.5, 0.0, 1.0);
    vec4 src = texture(Sampler0, uv);

    vec2 d = abs(f_Position) - halfSize + radius;
    float dis = length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - radius;
    float mask = 1.0 - aastep(dis);

    fragColor = vec4(src.rgb * BRIGHTNESS, src.a * mask * u_Style.w);
}
