#version 150

// Separable Gaussian blur, σ=1.0px.
// Called twice per frame: once with direction=(1,0) for horizontal,
// once with direction=(0,1) for vertical.
// Req 9.1, 9.2

uniform sampler2D Sampler0;

uniform vec2 direction;  // (1,0) for horizontal, (0,1) for vertical
uniform vec2 texSize;    // texture dimensions in pixels

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 texel = 1.0 / texSize;
    vec2 step = direction * texel;

    // 5-tap Gaussian kernel for σ=1.0:
    // weights: 0.0625, 0.25, 0.375, 0.25, 0.0625
    // (approximation of discrete Gaussian with σ≈1.0)
    float w0 = 0.06136;
    float w1 = 0.24477;
    float w2 = 0.38774;

    float sum = 0.0;
    sum += texture(Sampler0, texCoord - 2.0 * step).r * w0;
    sum += texture(Sampler0, texCoord - 1.0 * step).r * w1;
    sum += texture(Sampler0, texCoord              ).r * w2;
    sum += texture(Sampler0, texCoord + 1.0 * step).r * w1;
    sum += texture(Sampler0, texCoord + 2.0 * step).r * w0;

    fragColor = vec4(sum, sum, sum, 1.0);
}
