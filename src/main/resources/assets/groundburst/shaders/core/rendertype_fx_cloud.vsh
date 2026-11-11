#version 150

// Ground Burst clouds on 1.20.1: vanilla's emissive entity vertex shader ("eyes"): no lighting at all. 1.20.1's fog
// distance takes the model view, and a position drawn as entities are (the view's turn already in it) turned back first.

#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat3 IViewRotMat;
uniform int FogShape;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vertexDistance = fog_distance(ModelViewMat, IViewRotMat * Position, FogShape);
    vertexColor = Color;
    texCoord0 = UV0;
}
