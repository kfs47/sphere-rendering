#version 330
out vec4 vFragColor;

uniform vec3 Color;
uniform vec3 lightDir;

void main(void)
{
    // calculate normal from texture coordinates
    vec3 N;
    N.xy = gl_PointCoord* 2.0 - vec2(1.0);    
    float mag = dot(N.xy, N.xy);
    if (mag > 1.0) discard;   // kill pixels outside circle
    N.z = sqrt(1.0-mag);

    // calculate lighting
    float diffuse = max(0.0, dot(lightDir, N));

    vFragColor = vec4(Color,1) * diffuse;
}