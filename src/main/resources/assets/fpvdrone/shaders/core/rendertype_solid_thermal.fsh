#version 150

// Fragment shader for thermal solid block rendering.
// Heat from torches/lava/etc uses Minecraft's built-in blockLight system
// which propagates automatically through the world (up to 15 blocks from source).
// Works at any camera distance — no custom scanning needed.

uniform sampler2D Sampler0; // thermal atlas (swapped in by mixin)
uniform sampler2D Sampler2; // lightmap

uniform float gameTime;
uniform float biomeTemp;
uniform float ambientTemp;
uniform float weatherMod;

in vec2 texCoord0;
in vec4 vertexColor;
in vec2 lightmapCoord;
in vec3 worldNormal;
in vec3 worldPos;
in float blockLight;
in float skyLight;

out vec4 fragColor;

void main() {
    vec4 thermalData = texture(Sampler0, texCoord0);
    float baseTemp   = thermalData.r;
    float emissivity = thermalData.g;
    float matType    = thermalData.b;

    if (emissivity < 0.02) {
        baseTemp   = 0.12;
        emissivity = 0.90;
        matType    = 0.0;
    }

    // Solar heating — sun direction from day cycle.
    // Minecraft sun orbits in the east-west (X) axis:
    //   dawn (gameTime=0)  → east horizon  → sunDir ≈ (0, 0, 1)
    //   noon (gameTime=0.25) → directly overhead → sunDir = (0, 1, 0)
    //   dusk (gameTime=0.5) → west horizon  → sunDir ≈ (0, 0, -1)
    // sunAngle=0 at noon, so cos(0)=1 → Y=1 (overhead). Night: Y<0 → lambert=0.
    float sunAngle = (gameTime - 0.25) * 2.0 * 3.14159265;
    vec3 sunDir = normalize(vec3(0.0, cos(sunAngle), sin(sunAngle)));
    float lambert = max(dot(normalize(worldNormal), sunDir), 0.0);

    // Metal detection: materialType ≈ 0.5 (MATERIAL_METAL)
    bool isMetal = (matType > 0.4 && matType < 0.6);

    // Solar heat coefficient: metal absorbs ~2.5× more solar energy
    // (high thermal conductivity, fast surface heating)
    float solarCoeff = isMetal ? 0.45 : 0.18;
    float solarHeat = lambert * skyLight * weatherMod * solarCoeff;

    // Metal retains solar heat via dayFactor — warm during day,
    // slowly cools at night due to high thermalMass (0.9)
    // dayFactor: 1.0 at noon (gameTime=0.25), 0.0 at midnight (0.75)
    float dayFactor = max(0.0, cos((gameTime - 0.25) * 2.0 * 3.14159265));
    float metalRetained = isMetal ? dayFactor * 0.18 : 0.0;

    // Block light heating
    float blockHeat = pow(blockLight, 1.5) * 0.65;

    float T_surface = mix(baseTemp, 1.0, blockHeat);
    T_surface = T_surface + solarHeat + metalRetained;

    // Roof cooling: surfaces facing up lose heat to the cold sky
    // (radiative cooling). On real FLIR, roofs are darker than walls.
    // Only apply to building materials (baseTemp > 0.25) — ground/dirt
    // is already cold and shouldn't be pushed to black.
    float upFacing = max(normalize(worldNormal).y, 0.0);
    float roofFactor = smoothstep(0.25, 0.35, baseTemp);
    float roofCool = upFacing * upFacing * 0.22 * roofFactor;
    T_surface = T_surface - roofCool;

    float envReflection = ambientTemp;
    float T_apparent = mix(envReflection, T_surface, emissivity);
    T_apparent = clamp(T_apparent, 0.0, 1.0);

    fragColor = vec4(T_apparent, T_apparent, T_apparent, 1.0);
}
