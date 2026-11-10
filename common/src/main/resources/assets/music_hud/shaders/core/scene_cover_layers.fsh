#version 150

// u_Canvas.x: canvas aspect ratio (width / height)
layout(std140) uniform MHCover {
    vec4 u_Layer0Pos;
    vec4 u_Layer0Shape;
    vec4 u_Layer1Pos;
    vec4 u_Layer1Shape;
    vec4 u_Layer2Pos;
    vec4 u_Layer2Shape;
    vec4 u_Canvas;
};

uniform sampler2D Sampler0;

in vec2 texCoord;
out vec4 fragColor;

vec4 applyLayer(vec4 base, vec2 p, vec4 pos, vec4 shape) {
    if (shape.w < 0.5) {
        return base;
    }
    vec2 d = p - pos.xy;
    float c = pos.z;
    float s = pos.w;
    vec2 rp = mat2(c, -s, s, c) * d;
    vec2 uv = rp / (shape.xy * 2.0) + 0.5;
    float inside = step(0.0, uv.x) * step(uv.x, 1.0)
            * step(0.0, uv.y) * step(uv.y, 1.0);
    vec4 src = texture(Sampler0, clamp(uv, 0.0, 1.0));
    float a = shape.z * src.a * inside;
    return vec4(src.rgb * a + base.rgb * (1.0 - a), a + base.a * (1.0 - a));
}

void main() {
    float aspect = u_Canvas.x;
    vec2 canvasHalf = aspect >= 1.0
            ? vec2(1.0, 1.0 / aspect)
            : vec2(aspect, 1.0);
    vec2 p = (texCoord * 2.0 - 1.0) * canvasHalf;

    vec4 color = vec4(0.0);
    color = applyLayer(color, p, u_Layer0Pos, u_Layer0Shape);
    color = applyLayer(color, p, u_Layer1Pos, u_Layer1Shape);
    color = applyLayer(color, p, u_Layer2Pos, u_Layer2Shape);
    // The fluid layer is an opaque image composited over black.
    fragColor = vec4(color.rgb, 1.0);
}
