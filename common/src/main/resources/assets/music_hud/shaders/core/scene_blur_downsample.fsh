#version 150

layout(std140) uniform MHBlur {
    vec4 u_Source;
    vec4 u_Params;
};

uniform sampler2D Sampler0;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 t = u_Source.xy;
    vec4 color = texture(Sampler0, texCoord) * 4.0;
    color += texture(Sampler0, texCoord + vec2(t.x, 0.0)) * 2.0;
    color += texture(Sampler0, texCoord - vec2(t.x, 0.0)) * 2.0;
    color += texture(Sampler0, texCoord + vec2(0.0, t.y)) * 2.0;
    color += texture(Sampler0, texCoord - vec2(0.0, t.y)) * 2.0;
    color += texture(Sampler0, texCoord + vec2(t.x, t.y));
    color += texture(Sampler0, texCoord + vec2(-t.x, t.y));
    color += texture(Sampler0, texCoord + vec2(t.x, -t.y));
    color += texture(Sampler0, texCoord + vec2(-t.x, -t.y));
    fragColor = color / 16.0;
}
