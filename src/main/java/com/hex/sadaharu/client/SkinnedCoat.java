package com.hex.sadaharu.client;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import java.util.*;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Welded coat with linear blend skinning. Bind data is baked once, never per frame. */
final class SkinnedCoat {
    private final List<List<ModelPart>> chains=new ArrayList<>();
    private final Matrix4f[] inverseBind, transforms;
    private final Matrix3f[] normalTransforms;
    private final float[][] points,normals,weights;
    private final int[][] joints,faces;
    private final float[] shades;
    private final Vector3f[] posedPoints,posedNormals;
    private final float u,v;

    SkinnedCoat(JsonObject data,Map<String,List<ModelPart>> rig) {
        for(JsonElement name:data.getAsJsonArray("bones"))chains.add(rig.get(name.getAsString()));
        inverseBind=new Matrix4f[chains.size()];transforms=new Matrix4f[chains.size()];normalTransforms=new Matrix3f[chains.size()];
        PoseStack bind=new PoseStack();
        for(int i=0;i<chains.size();i++) {
            bind.pushPose();for(ModelPart bone:chains.get(i))bone.translateAndRotate(bind);
            inverseBind[i]=new Matrix4f(bind.last().pose()).invert();transforms[i]=new Matrix4f();normalTransforms[i]=new Matrix3f();bind.popPose();
        }
        JsonArray verts=data.getAsJsonArray("vertices"),ns=data.getAsJsonArray("normals"),ws=data.getAsJsonArray("weights"),fs=data.getAsJsonArray("faces");
        int count=verts.size();points=new float[count][3];normals=new float[count][3];weights=new float[count][];joints=new int[count][];
        shades=new float[count];posedPoints=new Vector3f[count];posedNormals=new Vector3f[count];
        for(int i=0;i<count;i++) {
            for(int k=0;k<3;k++){points[i][k]=verts.get(i).getAsJsonArray().get(k).getAsFloat()/16;normals[i][k]=ns.get(i).getAsJsonArray().get(k).getAsFloat();}
            JsonArray influences=ws.get(i).getAsJsonArray();weights[i]=new float[influences.size()];joints[i]=new int[influences.size()];
            for(int k=0;k<influences.size();k++){JsonArray pair=influences.get(k).getAsJsonArray();joints[i][k]=pair.get(0).getAsInt();weights[i][k]=pair.get(1).getAsFloat();}
            shades[i]=data.getAsJsonArray("shade").get(i).getAsFloat();posedPoints[i]=new Vector3f();posedNormals[i]=new Vector3f();
        }
        faces=new int[fs.size()][3];for(int i=0;i<fs.size();i++)for(int k=0;k<3;k++)faces[i][k]=fs.get(i).getAsJsonArray().get(k).getAsInt();
        int material=data.get("material").getAsInt();u=(material%4*128+64)/512F;v=(material/4*128+64)/512F;
    }

    void render(PoseStack pose,VertexConsumer out,int light,int overlay,float red,float green,float blue,float alpha) {
        for(int i=0;i<chains.size();i++) {
            pose.pushPose();for(ModelPart bone:chains.get(i))bone.translateAndRotate(pose);
            transforms[i].set(pose.last().pose()).mul(inverseBind[i]);
            transforms[i].normal(normalTransforms[i]);pose.popPose();
        }
        Vector3f temp=new Vector3f();
        for(int i=0;i<points.length;i++) {
            Vector3f p=posedPoints[i].zero(),n=posedNormals[i].zero();
            for(int k=0;k<joints[i].length;k++) {
                int joint=joints[i][k];float weight=weights[i][k];
                temp.set(points[i]);transforms[joint].transformPosition(temp);p.fma(weight,temp);
                temp.set(normals[i]);normalTransforms[joint].transform(temp);n.fma(weight,temp);
            }
            n.normalize();
        }
        // Minecraft's entity buffer consumes quads; repeat triangle's final vertex.
        for(int[] face:faces)for(int k=0;k<4;k++) {
            int i=face[Math.min(k,2)];Vector3f p=posedPoints[i],n=posedNormals[i];float shade=shades[i];
            out.vertex(p.x,p.y,p.z).color(red*shade,green*shade,blue*shade,alpha).uv(u,v).overlayCoords(overlay).uv2(light).normal(n.x,n.y,n.z).endVertex();
        }
    }
}

