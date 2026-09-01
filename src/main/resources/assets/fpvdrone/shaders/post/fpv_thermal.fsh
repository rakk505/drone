#version 330

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

layout(std140) uniform ThermalConfig {
    vec4 ModeAndAgc;       // palette, AGC mode, manual minimum, manual maximum
    vec4 FocusNoiseFrame;  // focus mode, focus distance, post-NUC FPN reduction, frame
    vec4 CameraEnvironment;// near plane, far plane, ambient temperature, absorption
};

in vec2 texCoord;
out vec4 fragColor;

float linearizeDepth(float depth) {
    float nearPlane = CameraEnvironment.x;
    float farPlane = CameraEnvironment.y;
    float ndc = depth * 2.0 - 1.0;
    return (2.0 * nearPlane * farPlane)
        / max(0.0001, farPlane + nearPlane - ndc * (farPlane - nearPlane));
}

float hash12(vec2 value) {
    vec3 p3 = fract(vec3(value.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 sceneSample(vec2 uv, float blurRadius) {
    vec2 texel = 1.0 / vec2(textureSize(InSampler, 0));
    vec3 center = texture(InSampler, uv).rgb;
    if (blurRadius < 0.1) {
        return center;
    }
    vec2 offset = texel * blurRadius;
    return center * 0.40
        + texture(InSampler, uv + vec2(offset.x, 0.0)).rgb * 0.15
        + texture(InSampler, uv - vec2(offset.x, 0.0)).rgb * 0.15
        + texture(InSampler, uv + vec2(0.0, offset.y)).rgb * 0.15
        + texture(InSampler, uv - vec2(0.0, offset.y)).rgb * 0.15;
}

float thermalValue(vec2 uv) {
    float depth = texture(DepthSampler, uv).r;
    float linearDepth = linearizeDepth(depth);
    float focusMode = FocusNoiseFrame.x;
    float focusError = abs(linearDepth - FocusNoiseFrame.y);
    float blurRadius = focusMode > 1.5 ? clamp(focusError * 0.035, 0.0, 3.0) : 0.0;
    vec3 color = sceneSample(uv, blurRadius);
    float sceneAlpha = texture(InSampler, uv).a;
    float luma = dot(color, vec3(0.299, 0.587, 0.114));

    if (depth >= 0.99999) {
        return CameraEnvironment.z * 0.30;
    }

    float atmospheric = exp(-linearDepth * CameraEnvironment.w);
    float terrain = CameraEnvironment.z + luma * 0.15 + atmospheric * 0.25;
    float heatSignal = max(color.r, luma);
    float temperature = mix(terrain, heatSignal, smoothstep(0.40, 0.80, heatSignal));

    // ThermalMaterialAtlas stores a remapped temperature in alpha. Vanilla directional lighting
    // can shade RGB, but it leaves alpha untouched, so classification survives every block face.
    bool encodedThermalMaterial = sceneAlpha > 0.095 && sceneAlpha < 0.97;
    if (encodedThermalMaterial) {
        temperature = clamp((sceneAlpha - 0.10) / 0.85, 0.0, 1.0);
    }

    float agcMode = ModeAndAgc.y;
    if (agcMode > 1.5) {
        temperature = (temperature - ModeAndAgc.z)
            / max(0.01, ModeAndAgc.w - ModeAndAgc.z);
    } else if (agcMode > 0.5) {
        float roiWeight = 1.0 - smoothstep(0.0, 0.36, length(uv - vec2(0.5)));
        temperature = mix(smoothstep(0.08, 0.88, temperature),
                          smoothstep(0.16, 0.72, temperature), roiWeight);
    } else {
        temperature = smoothstep(0.08, 0.88, temperature);
    }

    vec2 pixel = floor(uv * vec2(textureSize(InSampler, 0)));
    float fixedPattern = (hash12(vec2(pixel.x, 17.0)) - 0.5) * 0.035;
    fixedPattern += (hash12(vec2(pixel.y, 43.0)) - 0.5) * 0.018;
    fixedPattern *= 1.0 - FocusNoiseFrame.z;
    float netd = (hash12(pixel + FocusNoiseFrame.w) - 0.5) * 0.012;
    return clamp(temperature + fixedPattern + netd, 0.0, 1.0);
}

vec3 ironbow(float value) {
    const vec3 c0 = vec3(80.0, 0.0, 120.0) / 255.0;
    const vec3 c1 = vec3(0.0, 40.0, 200.0) / 255.0;
    const vec3 c2 = vec3(0.0, 180.0, 40.0) / 255.0;
    const vec3 c3 = vec3(220.0, 220.0, 0.0) / 255.0;
    const vec3 c4 = vec3(240.0, 120.0, 0.0) / 255.0;
    const vec3 c5 = vec3(220.0, 20.0, 10.0) / 255.0;
    const vec3 c6 = vec3(1.0);
    if (value < 0.20) return mix(c0, c1, value / 0.20);
    if (value < 0.40) return mix(c1, c2, (value - 0.20) / 0.20);
    if (value < 0.60) return mix(c2, c3, (value - 0.40) / 0.20);
    if (value < 0.80) return mix(c3, c4, (value - 0.60) / 0.20);
    if (value < 0.95) return mix(c4, c5, (value - 0.80) / 0.15);
    return mix(c5, c6, (value - 0.95) / 0.05);
}

void main() {
    vec2 centered = texCoord - vec2(0.5);
    float radius2 = dot(centered, centered);
    vec2 distortedUv = clamp(vec2(0.5) + centered * (1.0 + radius2 * 0.035), 0.0, 1.0);
    float temperature = thermalValue(distortedUv);
    float palette = ModeAndAgc.x;
    vec3 color;
    if (palette > 1.5) {
        color = ironbow(temperature);
    } else {
        float mapped = palette > 0.5 ? 1.0 - temperature : temperature;
        color = vec3(4.0 + mapped * 251.0,
                     4.0 + mapped * 246.0,
                     6.0 + mapped * 236.0) / 255.0;
    }
    float vignette = 1.0 - smoothstep(0.30, 0.72, length(centered)) * 0.48;
    // Alpha carries the pre-palette temperature into the bloom extraction pass.
    fragColor = vec4(color * vignette, temperature);
}
