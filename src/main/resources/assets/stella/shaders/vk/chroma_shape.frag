#version 450

layout(location = 0) in vec4 vColor;
layout(location = 1) in float vCoverage;

layout(push_constant) uniform PushConstants {
    layout(offset = 64) float uTime;
    layout(offset = 68) float uChromaSize;
    layout(offset = 72) float uSaturation;
};

layout(location = 0) out vec4 fragColor;

vec3 hsv2rgb(vec3 c) {
    vec3 rgb = clamp(abs(mod(c.x * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
    rgb = rgb * rgb * (3.0 - 2.0 * rgb);
    return c.z * mix(vec3(1.0), rgb, c.y);
}

void main() {
    float fragCoord = gl_FragCoord.x - gl_FragCoord.y;
    float hue = fract((fragCoord / uChromaSize) - uTime);

    float a = vColor.a * vCoverage;
    vec3 rgb = hsv2rgb(vec3(hue, uSaturation, 1.0));
    fragColor = vec4(rgb * a, a);
}
