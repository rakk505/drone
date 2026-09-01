#version 150

// Thermal bloom: extracts pixels above a brightness threshold,
// applies a wide separable blur, then composites additively onto
// the original image. Creates heat glow from warm blocks onto
// cooler neighbors in screen-space.
//
// Pass 0 (extract):  direction = (0,0), mode driven by step uniform
// Pass 1 (blur H):   direction = (1,0)
// Pass 2 (blur V):   direction = (0,1)
// Pass 3 (composite): composites bloom onto original

uniform sampler2D Sampler0; // input texture
uniform sampler2D Sampler1; // original scene (for composite pass)

uniform vec2 direction;   // blur direction or (0,0) for extract/composite
uniform vec2 texSize;     // texture dimensions in pixels
uniform float threshold;  // brightness threshold for bloom extraction
uniform float intensity;  // bloom strength multiplier
uniform int passIndex;    // 0=extract, 1=blurH, 2=blurV, 3=composite

in vec2 texCoord;
out vec4 fragColor;

void main() {
    if (passIndex == 0) {
        // Extract: keep only pixels above threshold
        float val = texture(Sampler0, texCoord).r;
        float bloom = max(val - threshold, 0.0);
        fragColor = vec4(bloom, bloom, bloom, 1.0);

    } else if (passIndex == 1 || passIndex == 2) {
        // Wide separable Gaussian blur, 13-tap, sigma ~4px
        // Gives a soft wide glow suitable for thermal bloom
        vec2 texel = 1.0 / texSize;
        vec2 step = direction * texel;

        // 13-tap kernel weights for sigma ≈ 4.0
        float w[7] = float[](
            0.1964825501511404,
            0.1748840505498498,
            0.1230001090498828,
            0.0683199694498390,
            0.0299532028498834,
            0.0103669589498834,
            0.0028330498498834
        );

        float sum = texture(Sampler0, texCoord).r * w[0];
        for (int i = 1; i <= 6; i++) {
            sum += texture(Sampler0, texCoord + float(i) * step).r * w[i];
            sum += texture(Sampler0, texCoord - float(i) * step).r * w[i];
        }
        sum *= intensity;
        fragColor = vec4(sum, sum, sum, 1.0);

    }
}
