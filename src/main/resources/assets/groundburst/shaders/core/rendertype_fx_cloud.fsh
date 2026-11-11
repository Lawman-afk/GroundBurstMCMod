#version 150

// Ground Burst clouds on 1.20.1 (as on 1.21.1): vanilla's emissive entity fragment shader ("eyes": texture x vertex colour), with
// the faint fringe cut out, as main's cloud shader does, so a cloud can write depth (vanilla's own clouds, drawn later,
// then stay behind it) without its clear pixels hiding what is drawn behind them after it.

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
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (color.a < 0.16) {
        discard;
    }
    fragColor = color * linear_fog_fade(vertexDistance, FogStart, FogEnd);
}
