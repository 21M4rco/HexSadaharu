package com.hex.sadaharu.client;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import java.util.*;
import org.joml.Vector3f;

/** Immutable curved skin, baked once at model construction; attached to the existing bone rig. */
final class RoundedMesh {
    private record Vertex(float x,float y,float z,float nx,float ny,float nz) {}
    private final List<Vertex> vertices=new ArrayList<>();
    private final float u,v;
    RoundedMesh(JsonObject shape) {
        int material=shape.get("material").getAsInt();
        u=(material%4*128+64)/512F;v=(material/4*128+64)/512F;
        if(shape.has("vertices")) {
            JsonArray points=shape.getAsJsonArray("vertices");
            Vector3f center=new Vector3f();
            for(JsonElement e:points){JsonArray a=e.getAsJsonArray();center.add(n(a,0),n(a,1),n(a,2));}
            center.div(points.size());
            for(JsonElement e:shape.getAsJsonArray("faces")) {
                JsonArray f=e.getAsJsonArray();Vector3f[] p=new Vector3f[4];
                for(int i=0;i<4;i++){JsonArray a=points.get(f.get(Math.min(i,f.size()-1)).getAsInt()).getAsJsonArray();p[i]=new Vector3f(n(a,0),n(a,1),n(a,2));}
                Vector3f normal=new Vector3f(p[1]).sub(p[0]).cross(new Vector3f(p[2]).sub(p[0])).normalize();
                float facing=normal.dot(new Vector3f(p[0]).sub(center));
                if(facing<-.0001F||(Math.abs(facing)<.0001F&&normal.z>0))normal.negate();
                for(Vector3f q:p)vertices.add(new Vertex(q.x/16,q.y/16,q.z/16,normal.x,normal.y,normal.z));
            }
            return;
        }
        JsonArray center=shape.getAsJsonArray("center"),size=shape.getAsJsonArray("size"),taper=shape.getAsJsonArray("taper");
        int segments=shape.get("segments").getAsInt(),rings=shape.get("rings").getAsInt();
        double power=shape.get("power").getAsDouble();
        Vertex[][] grid=new Vertex[rings+1][segments+1];
        for(int i=0;i<=rings;i++)for(int j=0;j<=segments;j++) {
            double lat=-Math.PI/2+Math.PI*i/rings,lon=2*Math.PI*j/segments;
            double y=sp(Math.sin(lat),2/power),g=n(taper,0)+(n(taper,1)-n(taper,0))*(y+1)/2;
            double x=sp(Math.cos(lat),2/power)*sp(Math.cos(lon),2/power),z=sp(Math.cos(lat),2/power)*sp(Math.sin(lon),2/power);
            double rx=n(size,0)/2.,ry=n(size,1)/2.,rz=n(size,2)/2.;
            // Gradient of the superellipsoid, including the varying radius along a tapered limb.
            double nx=sp(x,power-1)/(rx*g),nz=sp(z,power-1)/(rz*g);
            double ny=(sp(y,power-1)-(Math.pow(Math.abs(x),power)+Math.pow(Math.abs(z),power))*(n(taper,1)-n(taper,0))/(2*g))/ry;
            double length=Math.sqrt(nx*nx+ny*ny+nz*nz);
            grid[i][j]=new Vertex((float)((n(center,0)+x*rx*g)/16),(float)((n(center,1)+y*ry)/16),(float)((n(center,2)+z*rz*g)/16),(float)(nx/length),(float)(ny/length),(float)(nz/length));
        }
        for(int i=0;i<rings;i++)for(int j=0;j<segments;j++) {
            vertices.add(grid[i][j]);vertices.add(grid[i+1][j]);vertices.add(grid[i+1][j+1]);vertices.add(grid[i][j+1]);
        }
    }
    private static float n(JsonArray a,int i){return a.get(i).getAsFloat();}
    private static double sp(double n,double p){return Math.copySign(Math.pow(Math.abs(n),p),n);}
    void render(PoseStack pose,VertexConsumer out,int light,int overlay,float red,float green,float blue,float alpha) {
        var matrix=pose.last();
        for(Vertex q:vertices)out.vertex(matrix.pose(),q.x,q.y,q.z).color(red,green,blue,alpha).uv(u,v).overlayCoords(overlay).uv2(light).normal(matrix.normal(),q.nx,q.ny,q.nz).endVertex();
    }
}
