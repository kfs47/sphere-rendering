uniform mat4 MVP;          
uniform mat4 MV;

attribute vec3 position;   
attribute float velocity;  
attribute float radius;

varying float speed;

void main(){
   gl_PointSize = radius;
   speed = velocity;

   gl_Position = MVP * vec4(position, 1.0);
}