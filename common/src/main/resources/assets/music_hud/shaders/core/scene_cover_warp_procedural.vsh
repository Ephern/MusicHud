#version 150

// u_Params.x: deformation gate of the running cycle, 0 = undeformed, 1 = cycle target
layout(std140) uniform MHWarp {
    vec4 u_Params;
};

// Position carries the undeformed canvas uv, UV0 the (already coverage-expanded) target position.
in vec3 Position;
in vec2 UV0;

out vec2 texCoord;

void main() {
    texCoord = Position.xy;
    vec2 p = mix(Position.xy, UV0, clamp(u_Params.x, 0.0, 1.0));
    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
}
