#version 150

layout(std140) uniform MHBlur {
    vec4 u_Source;
    vec4 u_Params;
};

uniform sampler2D Sampler0;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 uv = clamp(texCoord, u_Source.xy * 0.5, vec2(1.0) - u_Source.xy * 0.5);
    vec2 o = u_Source.xy;
    vec4 color = texture(Sampler0, uv) * 4.0;
    color += texture(Sampler0, uv + vec2(o.x, 0.0)) * 2.0;
    color += texture(Sampler0, uv - vec2(o.x, 0.0)) * 2.0;
    color += texture(Sampler0, uv + vec2(0.0, o.y)) * 2.0;
    color += texture(Sampler0, uv - vec2(0.0, o.y)) * 2.0;
    color += texture(Sampler0, uv + vec2(o.x, o.y));
    color += texture(Sampler0, uv + vec2(-o.x, o.y));
    color += texture(Sampler0, uv + vec2(o.x, -o.y));
    color += texture(Sampler0, uv + vec2(-o.x, -o.y));
    fragColor = color / 16.0;
}
