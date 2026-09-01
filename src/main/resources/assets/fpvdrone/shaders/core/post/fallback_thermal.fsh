#version 150

uniform sampler2D Sampler0; // Main scene color
uniform sampler2D Sampler1; // Scene depth

uniform float nearPlane;
uniform float farPlane;
uniform float ambientTemp;

in vec2 texCoord;

out vec4 fragColor;

float linearize_depth(float depth) {
    float ndc = depth * 2.0 - 1.0;
    return (2.0 * nearPlane * farPlane) / (farPlane + nearPlane - ndc * (farPlane - nearPlane));
}

void main() {
    float depth = texture(Sampler1, texCoord).r;
    vec4 color = texture(Sampler0, texCoord);

    // Sky / void threshold
    if (depth >= 0.99999) {
        float skyTemp = ambientTemp * 0.3;
        fragColor = vec4(skyTemp, skyTemp, skyTemp, 1.0);
        return;
    }

    float linearDepth = linearize_depth(depth);

    // Calculate perceptual luminance
    float luma = dot(color.rgb, vec3(0.299, 0.587, 0.114));

    // Smooth depth fade (atmospheric scattering) instead of a hard border
    // This removes the "boundary" effect and naturally cools distant objects
    float fogDensity = 0.015;
    float depthFactor = exp(-linearDepth * fogDensity);
    
    // Base terrain temperature
    float terrainTemp = ambientTemp + (luma * 0.15) + (depthFactor * 0.25);

    // Better detection for "Hot" blocks (Lava, Torches, Fire, Entities)
    // - Entities drawn by our shader are bright grayscale (R≈G≈B > 0.8)
    // - Lava/Fire are bright red/yellow (R > 0.8, G > 0.4)
    float heatSignal = max(color.r, luma); 
    // If it's bright overall OR intensely red/yellow, boost its temp
    float finalTemp = mix(terrainTemp, heatSignal, smoothstep(0.4, 0.8, heatSignal));

    // Clamp to valid range
    finalTemp = clamp(finalTemp, 0.0, 1.0);

    // Output pure grayscale. The next pass (AGC) will stretch this,
    // and eventually PaletteLUTPass will colorize it into iron/white hot.
    fragColor = vec4(finalTemp, finalTemp, finalTemp, 1.0);
}
