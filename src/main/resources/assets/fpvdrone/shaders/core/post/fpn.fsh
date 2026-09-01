#version 150

// FPN (Fixed Pattern Noise) — static screen-space noise.
// Does NOT move with camera rotation — simulates manufacturing defects.
// fpnReduction is set by NUC events (0.0 = full noise, 0.4 = 40% reduced after NUC).
// Req 13.2

uniform sampler2D Sampler0;

uniform vec2 texSize;
uniform float fpnReduction;

in vec2 texCoord;
out vec4 fragColor;

// Deterministic hash based only on screen pixel position (no frame seed).
// This produces a static pattern that stays fixed on screen.
float staticHash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

void main() {
    float T = texture(Sampler0, texCoord).r;

    // Static noise from pixel coordinates only
    vec2 pixelPos = floor(texCoord * texSize);
    float noise = staticHash(pixelPos) * 2.0 - 1.0; // range [-1, 1]

    // Base FPN amplitude — extremely subtle to avoid visible patterns.
    // Previous value (0.002) was too high after AGC amplification.
    float amplitude = 0.0003 * (1.0 - fpnReduction);
    float result = clamp(T + noise * amplitude, 0.0, 1.0);

    fragColor = vec4(result, result, result, 1.0);
}
