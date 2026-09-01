#version 150

// Vertex shader for thermal entity rendering.
// Entity vertex format: Position, Color, UV0, UV1 (overlay), UV2 (lightmap), Normal

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 texCoord0;
out vec4 vertexColor;

#moj_import <light.glsl>

uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord0 = UV0;
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, Color);
}
