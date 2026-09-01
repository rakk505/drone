#version 150

// Fragment shader for thermal cloud rendering.
// Clouds appear as faint grey patches: ~0.08 + small biome temperature contribution.

uniform sampler2D Sampler0; // cloud texture (used for alpha/shape)
uniform float biomeTemp;    // 0-1 biome temperature

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    // Use original cloud texture alpha for shape
    float alpha = texture(Sampler0, texCoord0).a * vertexColor.a;
    if (alpha < 0.1) {
        discard;
    }

    // Cloud temperature: slightly above radiative cold sky, with biome contribution
    float T_cloud = 0.08 + biomeTemp * 0.03;
    fragColor = vec4(T_cloud, T_cloud, T_cloud, alpha);
}
