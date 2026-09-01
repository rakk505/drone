#version 150

// Atmospheric fog pass — first in the post-processing chain.
// Operates on physical temperature values before AGC.
// Applies a uniform, very subtle fog blend toward ambient temperature.
// No radial/screen-space depth approximation — that caused view-dependent
// brightness inversion when bright objects were at screen center.

uniform sampler2D Sampler0;  // input thermal image (grayscale)

uniform float absorption;    // 0.003 clear, higher in rain
uniform float ambientTemp;   // current ambient temperature

in vec2 texCoord;
out vec4 fragColor;

void main() {
    float T = texture(Sampler0, texCoord).r;

    // Uniform fog — same amount everywhere on screen.
    // Real thermal imagers don't have screen-position-dependent fog;
    // atmospheric absorption depends on actual 3D distance which we
    // don't have in this post-processing pass.
    // Use a fixed subtle blend factor based on absorption coefficient.
    float fogFactor = clamp(absorption * 10.0, 0.0, 0.08);

    // Blend toward ambient temperature
    float fogged = mix(T, ambientTemp, fogFactor);

    fragColor = vec4(fogged, fogged, fogged, 1.0);
}
