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
    vec3 color = texture(Sampler0, texCoord).rgb * 4.0;
    color += texture(Sampler0, texCoord + vec2(t.x, 0.0)).rgb * 2.0;
    color += texture(Sampler0, texCoord - vec2(t.x, 0.0)).rgb * 2.0;
    color += texture(Sampler0, texCoord + vec2(0.0, t.y)).rgb * 2.0;
    color += texture(Sampler0, texCoord - vec2(0.0, t.y)).rgb * 2.0;
    color += texture(Sampler0, texCoord + vec2(t.x, t.y)).rgb;
    color += texture(Sampler0, texCoord + vec2(-t.x, t.y)).rgb;
    color += texture(Sampler0, texCoord + vec2(t.x, -t.y)).rgb;
    color += texture(Sampler0, texCoord + vec2(-t.x, -t.y)).rgb;
    fragColor = vec4(color / 16.0, 1.0);
}
