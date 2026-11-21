#version 150

// u_Source: (1 / sourceWidth, 1 / sourceHeight, sourceWidth, sourceHeight)
// u_Rect: (u0, v0, u1, v1) source crop rectangle
// u_Params.x: 1 = 9-tap downsample, 0 = single linear sample
layout(std140) uniform MHCoverResolve {
    vec4 u_Source;
    vec4 u_Rect;
    vec4 u_Params;
};

uniform sampler2D Sampler0;

in vec2 texCoord;
out vec4 fragColor;

vec2 clampRect(vec2 uv) {
    return clamp(uv, u_Rect.xy, u_Rect.zw);
}

void main() {
    vec2 uv = mix(u_Rect.xy, u_Rect.zw, texCoord);
    if (u_Params.x < 0.5) {
        fragColor = texture(Sampler0, clampRect(uv));
        return;
    }

    vec2 t = u_Source.xy;
    vec4 color = texture(Sampler0, clampRect(uv)) * 4.0;
    color += texture(Sampler0, clampRect(uv + vec2(t.x, 0.0))) * 2.0;
    color += texture(Sampler0, clampRect(uv - vec2(t.x, 0.0))) * 2.0;
    color += texture(Sampler0, clampRect(uv + vec2(0.0, t.y))) * 2.0;
    color += texture(Sampler0, clampRect(uv - vec2(0.0, t.y))) * 2.0;
    color += texture(Sampler0, clampRect(uv + vec2(t.x, t.y)));
    color += texture(Sampler0, clampRect(uv + vec2(-t.x, t.y)));
    color += texture(Sampler0, clampRect(uv + vec2(t.x, -t.y)));
    color += texture(Sampler0, clampRect(uv + vec2(-t.x, -t.y)));
    fragColor = color / 16.0;
}
