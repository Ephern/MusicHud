#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

// u_Layout: (halfWidth, halfHeight, cornerRadius)
layout(std140) uniform MHPosition {
    mat4 u_Translation;
    vec3 u_Layout;
};

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;

layout(location = 0) out vec2 f_Position;
layout(location = 1) out vec4 f_Color;

void main() {
    f_Position = Position.xy;
    f_Color = Color;

    vec4 localPos = u_Translation * vec4(Position, 1.0);
    gl_Position = ProjMat * ModelViewMat * vec4(localPos.xy, Position.z, 1.0);
}
