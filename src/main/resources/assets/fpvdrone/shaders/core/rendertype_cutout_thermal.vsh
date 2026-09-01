#version 150

// Vertex shader for thermal cutout block rendering (same as solid)

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat3 IViewRotMat;
uniform vec3 ChunkOffset;
uniform sampler2D Sampler2;

out vec2 texCoord0;
out vec4 vertexColor;
out vec2 lightmapCoord;
out vec3 worldNormal;
out float blockLight;
out float skyLight;

#moj_import <light.glsl>

void main() {
    vec3 pos = Position + ChunkOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    texCoord0 = UV0;
    vertexColor = Color * minecraft_sample_lightmap(Sampler2, UV2);

    lightmapCoord = clamp(vec2(UV2) / 256.0, vec2(0.0), vec2(1.0));
    blockLight = lightmapCoord.x;
    skyLight = lightmapCoord.y;
    worldNormal = Normal;
}
