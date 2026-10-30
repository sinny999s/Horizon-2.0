#version 330
#extension GL_ARB_separate_shader_objects : require

// The original's core/default/pos_color.fsh.
layout(location = 0) in vec4 vertexColor;

layout(location = 0) out vec4 color;

void main() {
    color = vertexColor;
}
