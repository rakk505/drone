#version 150

// Vignette + Barrel Distortion — final visual effect before OSD.
// Very subtle edge darkening and minimal barrel distortion.
// Real thermal imagers have gentle lens falloff, not a dark circle.

uniform sampler2D Sampler0;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 center = vec2(0.5, 0.5);
    vec2 offset = texCoord - center;

    // Very subtle barrel distortion
    float k = 0.02;
    float r2 = dot(offset, offset);
    vec2 distorted = center + offset * (1.0 + k * r2);

    // Clamp to valid UV range
    vec3 color;
    if (distorted.x < 0.0 || distorted.x > 1.0 || distorted.y < 0.0 || distorted.y > 1.0) {
        color = vec3(0.0);
    } else {
        color = texture(Sampler0, distorted).rgb;
    }

    // Very gentle vignette — only darkens the extreme corners
    // r ranges from 0 (center) to ~0.7 (corner)
    float r = length(offset);
    float vignette = 1.0 - smoothstep(0.6, 1.2, r);

    fragColor = vec4(color * vignette, 1.0);
}
