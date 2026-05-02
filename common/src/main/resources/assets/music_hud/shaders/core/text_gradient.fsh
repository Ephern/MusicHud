#version 150

layout(std140) uniform GradientParams {
    mat4 _ModelView;
    vec4 u_Positions;// p1, p2, p3, p4
    vec3 u_TextMeta;// spread, colorCount, textWidth
    mat4 u_Colors;// c1, c2, c3, (textStartX, 0, 0, 0)
    float u_unused;
};

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord0;
in float worldX;

out vec4 fragColor;

float hermiteSmoothstep(float t) {
    return t * t * (3.0 - 2.0 * t);
}

void main() {
    vec4 glyphMask = texture(Sampler0, texCoord0);
    float alpha = glyphMask.a;
    if (alpha < 0.01) {
        discard;
    }

    float spread = u_TextMeta.x;
    int n = int(u_TextMeta.y);
    float textWidth = u_TextMeta.z;
    if (textWidth < 0.01) textWidth = 0.01;

    float t = (worldX - u_Colors[3].x) / textWidth;
    t = clamp(t, 0.0, 1.0);

    vec4 gradientColor;

    if (t <= u_Positions[0]) {
        gradientColor = u_Colors[0];
    } else if (t >= u_Positions[n - 1]) {
        gradientColor = u_Colors[n - 1];
    } else if (n == 2) {
        float seg = (t - u_Positions[0]) / (u_Positions[1] - u_Positions[0]);
        gradientColor = mix(u_Colors[0], u_Colors[1], hermiteSmoothstep(seg));
    } else if (n == 3) {
        if (t < u_Positions[1]) {
            float seg = (t - u_Positions[0]) / (u_Positions[1] - u_Positions[0]);
            gradientColor = mix(u_Colors[0], u_Colors[1], hermiteSmoothstep(seg));
        } else {
            float seg = (t - u_Positions[1]) / (u_Positions[2] - u_Positions[1]);
            gradientColor = mix(u_Colors[1], u_Colors[2], hermiteSmoothstep(seg));
        }
    }

    gradientColor.a *= alpha;

    if (gradientColor.a < 0.1) {
        discard;
    }

    fragColor = gradientColor;
}
