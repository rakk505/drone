#version 150

#moj_import <fog.glsl>

// Fragment shader for thermal translucent block rendering.
// Uses blockLight for heat (same as solid/cutout). No custom conduction.

uniform sampler2D Sampler0; // thermal atlas (swapped in by mixin)
uniform sampler2D Sampler2; // lightmap

uniform float gameTime;
uniform float biomeTemp;
uniform float ambientTemp;
uniform float weatherMod;
uniform int frameCount;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;
in vec2 lightmapCoord;
in vec3 worldNormal;
in vec3 worldPos;
in vec3 viewNormal;
in float blockLight;
in float skyLight;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

void main() {
    vec4 thermalData = texture(Sampler0, texCoord0);
    float baseTemp    = thermalData.r;
    float emissivity  = thermalData.g;
    float matType     = thermalData.b;

    float alpha = vertexColor.a * ColorModulator.a;
    if (alpha < 0.01) {
        discard;
    }

    if (emissivity < 0.02) {
        baseTemp   = 0.12;
        emissivity = 0.90;
        matType    = 0.0;
    }

    float blockHeat = pow(blockLight, 1.5) * 0.65;
    float T_apparent = 0.0;

    // Roof cooling factor — surfaces facing up radiate heat to cold sky
    // Only for building materials (baseTemp > 0.25), not ground/dirt
    float upFacing = max(normalize(worldNormal).y, 0.0);
    float roofFactor = smoothstep(0.25, 0.35, baseTemp);
    float roofCool = upFacing * upFacing * 0.22 * roofFactor;

    if (matType > 0.6 && matType < 0.9) {
        // Water
        float wave = hash(texCoord0 * 50.0 + vec2(float(frameCount) * 0.01)) * 0.02;
        T_apparent = baseTemp + wave;
        T_apparent = mix(T_apparent, 1.0, blockHeat);
        T_apparent = T_apparent - roofCool;
    } else if (matType > 0.1 && matType < 0.4) {
        // Glass — opaque and dark in LWIR thermal (8-14μm).
        // Real glass is opaque to long-wave infrared; it appears as a
        // cold, dark surface reflecting a small amount of ambient IR.
        T_apparent = baseTemp;
    } else {
        // Normal / Fire / Metal
        // Solar heating — sun orbits in Y-Z plane, overhead at noon
        float sunAngle = (gameTime - 0.25) * 2.0 * 3.14159265;
        vec3 sunDir = normalize(vec3(0.0, cos(sunAngle), sin(sunAngle)));
        float lambert = max(dot(normalize(worldNormal), sunDir), 0.0);

        // Metal detection: materialType ≈ 0.5 (MATERIAL_METAL)
        bool isMetal = (matType > 0.4 && matType < 0.6);

        // Solar heat: metal absorbs ~2.5× more solar energy
        float solarCoeff = isMetal ? 0.45 : 0.18;
        float solarHeat = lambert * skyLight * weatherMod * solarCoeff;

        // Metal retains solar heat via dayFactor
        float dayFactor = max(0.0, cos((gameTime - 0.25) * 2.0 * 3.14159265));
        float metalRetained = isMetal ? dayFactor * 0.18 : 0.0;

        float T_surface = mix(baseTemp, 1.0, blockHeat);
        T_surface = T_surface + solarHeat + metalRetained;
        T_surface = T_surface - roofCool;

        float envReflection = ambientTemp;
        T_apparent = mix(envReflection, T_surface, emissivity);
    }

    T_apparent = clamp(T_apparent, 0.0, 1.0);
    fragColor = vec4(T_apparent, T_apparent, T_apparent, 1.0);
}
