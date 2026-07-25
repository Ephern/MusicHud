#version 150

uniform mat4 ProjMat;
uniform mat4 ModelViewMat;

layout(std140) uniform MHProgressPosition {
    mat4 u_Translation;
    vec3 u_Layout; // (halfWidth, halfHeight, cornerRadius)
};
layout(std140) uniform MHProgressStyle {
    vec3 u_Gradient; // (gradientLength, rightOffset, transitionBorderRate)
    vec4 u_PlayedColor;
    vec4 u_CurrentColor;
    vec4 u_BackgroundColor;
};
layout(std140) uniform MHDynamicStatus {
    vec4 u_Dynamic1; // (timestamp, playedProgress, switchProgress)
};

in vec3 Position;
in vec4 Color;

out vec2 f_Position;
out vec4 f_Color;

void main() {
    f_Position = Position.xy;
    f_Color = Color;

    vec4 localPos = u_Translation * vec4(Position, 1.0);
    gl_Position = ProjMat * ModelViewMat * vec4(localPos.xy, Position.z, 1.0);
}
