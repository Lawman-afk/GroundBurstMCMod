#version 150

// Ground Burst's glow on 1.20.1 (the beam's light, the fireball's core): vanilla's emissive entity fragment shader
// ("eyes": texture x vertex colour), blended additively by its own shader file, as the glow's render type blends it (a
// 1.20.1 shader sets its blending as it is used, over the render type's: the eyes shader's own would not add).

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor;
    fragColor = color * ColorModulator * linear_fog_fade(vertexDistance, FogStart, FogEnd);
}
