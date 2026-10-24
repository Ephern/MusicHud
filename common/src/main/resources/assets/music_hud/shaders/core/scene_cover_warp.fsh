#version 150

layout(std140) uniform MHCoverWarp {
    // x: gridSize, y: enabled, z: strength, w: reserved
    vec4 u_Params;
    // 9x9 maximum control grid, xy = normalized cover UV offset
    vec4 u_Offsets[81];
};

uniform sampler2D Sampler0;

in vec2 texCoord;
out vec4 fragColor;

vec4 catmullRomWeights(float t) {
    float t2 = t * t;
    float t3 = t2 * t;
    return vec4(
            -0.5 * t3 + t2 - 0.5 * t,
            1.5 * t3 - 2.5 * t2 + 1.0,
            -1.5 * t3 + 2.0 * t2 + 0.5 * t,
            0.5 * t3 - 0.5 * t2);
}

void main() {
    if (u_Params.y < 0.5) {
        fragColor = texture(Sampler0, texCoord);
        return;
    }

    int n = int(u_Params.x + 0.5);
    if (n < 2) {
        fragColor = texture(Sampler0, texCoord);
        return;
    }

    float gx = texCoord.x * float(n - 1);
    float gy = texCoord.y * float(n - 1);
    int ix = int(floor(gx));
    int iy = int(floor(gy));
    vec4 wx = catmullRomWeights(gx - float(ix));
    vec4 wy = catmullRomWeights(gy - float(iy));

    vec2 offset = vec2(0.0);
    for (int j = 0; j < 4; j++) {
        int y = clamp(iy + j - 1, 0, n - 1);
        for (int i = 0; i < 4; i++) {
            int x = clamp(ix + i - 1, 0, n - 1);
            offset += u_Offsets[y * n + x].xy * wx[i] * wy[j];
        }
    }

    offset *= u_Params.z;
    vec2 uv = clamp(texCoord - offset, 0.0, 1.0);
    fragColor = texture(Sampler0, uv);
}
