package com.hex.sadaharu.client;

import com.google.gson.*;
import com.hex.sadaharu.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/** Custom articulated rig. Geometry and the review renders share one JSON source of truth. */
public final class SadaharuModel extends HierarchicalModel<Sadaharu> {
    public static final ModelLayerLocation LAYER=new ModelLayerLocation(HexSadaharu.id("sadaharu"),"main");
    private final ModelPart root;
    private final Map<String,ModelPart> bones=new HashMap<>();
    private static JsonArray definitions;
    public static LayerDefinition layer() {
        try(var in=Minecraft.getInstance().getResourceManager().getResource(HexSadaharu.id("models/entity/sadaharu.json")).orElseThrow().open();var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){
            definitions=JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("bones");
            MeshDefinition mesh=new MeshDefinition();Map<String,PartDefinition> parts=new HashMap<>();
            for(JsonElement e:definitions) {
                JsonObject b=e.getAsJsonObject();CubeListBuilder cubes=CubeListBuilder.create();
                for(JsonElement ce:b.getAsJsonArray("cubes")) {
                    JsonObject c=ce.getAsJsonObject();JsonArray o=c.getAsJsonArray("origin"),s=c.getAsJsonArray("size");int mat=c.get("material").getAsInt();
                    cubes.texOffs(mat%4*128,mat/4*128).addBox(f(o,0),f(o,1),f(o,2),f(s,0),f(s,1),f(s,2));
                }
                JsonArray p=b.getAsJsonArray("pivot"),r=b.getAsJsonArray("rotation");
                PartDefinition parent=b.get("parent").isJsonNull()?mesh.getRoot():parts.get(b.get("parent").getAsString());
                parts.put(b.get("name").getAsString(),parent.addOrReplaceChild(b.get("name").getAsString(),cubes,PartPose.offsetAndRotation(f(p,0),f(p,1),f(p,2),f(r,0)*Mth.DEG_TO_RAD,f(r,1)*Mth.DEG_TO_RAD,f(r,2)*Mth.DEG_TO_RAD)));
            }
            return LayerDefinition.create(mesh,512,512);
        }catch(IOException e){throw new IllegalStateException("Sadaharu rig could not load",e);}
    }
    private static float f(JsonArray a,int i){return a.get(i).getAsFloat();}
    public SadaharuModel(ModelPart root) {
        this.root=root;
        for(JsonElement e:definitions){JsonObject b=e.getAsJsonObject();String name=b.get("name").getAsString();ModelPart parent=b.get("parent").isJsonNull()?root:bones.get(b.get("parent").getAsString());bones.put(name,parent.getChild(name));}
    }
    public ModelPart part(String name){return bones.get(name);}
    @Override public ModelPart root(){return root;}
    private static float sin(float t){return Mth.sin(t);}
    private static float ease(float t){t=Mth.clamp(t,0,1);return t*t*(3-2*t);}
    private void rot(String bone,float x,float y,float z){ModelPart p=part(bone);p.xRot+=x;p.yRot+=y;p.zRot+=z;}
    private void move(String bone,float x,float y,float z){ModelPart p=part(bone);p.x+=x;p.y+=y;p.z+=z;}
    @Override public void setupAnim(Sadaharu dog,float limb,float amount,float time,float yaw,float pitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        Act act=dog.act();float age=dog.actAge(time-dog.tickCount);
        float envelope=ease(age/10)*(act==Act.SLEEP?1:ease((act.ticks-age)/12));
        float targetSit=(act==Act.SIT||act==Act.SIT_PANT||act==Act.POOP)?1:0;
        float targetLie=(act==Act.LIE||act==Act.CHIN||act==Act.SIDE||act==Act.SLEEP||act==Act.SLEEP_TWITCH)?1:0;
        float blend=1-(float)Math.pow(.78,Math.max(.1,Minecraft.getInstance().getDeltaFrameTime()));
        dog.clientSit=Mth.lerp(blend,dog.clientSit,targetSit);dog.clientLie=Mth.lerp(blend,dog.clientLie,targetLie);
        dog.clientSleep=Mth.lerp(blend,dog.clientSleep,act==Act.SLEEP?1:0);
        float sit=dog.clientSit,lie=dog.clientLie,sleep=dog.clientSleep;
        float moving=Mth.clamp(amount*2.2F,0,1)*(1-Math.max(sit,lie));
        float speed=(float)dog.getDeltaMovement().horizontalDistance();
        float bound=ease((speed-.22F)*4);
        rot("head",pitch*Mth.DEG_TO_RAD*.45F*(1-sleep),yaw*Mth.DEG_TO_RAD*.65F*(1-sleep),0);
        rot("neck",0,yaw*Mth.DEG_TO_RAD*.15F*(1-sleep),0);
        // Heavy diagonal walk gradually turns into a paired supernatural bound.
        for(int i=0;i<4;i++) {
            String name=new String[]{"front_left","front_right","rear_left","rear_right"}[i];
            float walkPhase=limb*.65F+(i==0||i==3?0:Mth.PI);
            float runPhase=limb*.7F+(i<2?0:1.9F)+(i%2)*.22F;
            float stride=Mth.lerp(bound,sin(walkPhase)*.5F,sin(runPhase)*1.02F)*moving;
            rot(name+"_leg",stride,0,0);rot(name+"_paw",Math.max(0,-stride)*.55F,0,0);
        }
        move("body",0,-Math.abs(sin(limb*.7F))*moving*(.7F+bound*1.3F),0);
        rot("body",sin(limb*.7F)*moving*bound*.09F,0,sin(limb*.33F)*moving*.025F);
        move("chest",0,sin(time*.075F)*(act==Act.SLEEP?.3F:.16F),0);part("chest").xScale=1+sin(time*.075F)*.006F;
        // Sit: haunches drop, front paws hold weight, rear paws fold out.
        move("body",0,6*sit,1.5F*sit);rot("body",-.3F*sit,0,0);
        rot("neck",.3F*sit,0,0);move("head",0,-1.5F*sit,0);
        for(String s:new String[]{"front_left","front_right"}){rot(s+"_leg",.3F*sit,0,0);move(s+"_leg",0,-4*sit,0);}
        for(String s:new String[]{"rear_left","rear_right"}){rot(s+"_leg",-1.1F*sit,0,s.equals("rear_left")?-.18F*sit:.18F*sit);rot(s+"_paw",1.3F*sit,0,0);}
        // Lie and sleep: elbows fold first, then chest settles; the head remains softly alive.
        move("body",0,10*lie,0);rot("body",.055F*lie,0,0);move("neck",0,2*lie,0);
        for(String s:new String[]{"front_left","front_right"}){rot(s+"_leg",-1.38F*lie,0,0);rot(s+"_paw",1.3F*lie,0,0);}
        for(String s:new String[]{"rear_left","rear_right"}){rot(s+"_leg",1.2F*lie,0,0);rot(s+"_paw",-.8F*lie,0,0);}
        move("head",0,2.5F*sleep,-1*sleep);rot("neck",.17F*sleep,0,0);rot("head",.12F*sleep,-.13F*sleep,-.08F*sleep);
        // resetPose does not restore scale, and only anger() touches the eyes now.
        part("eye_left").yScale=1;part("eye_right").yScale=1;
        // Always-on micro-life, independent of whatever gesture is playing.
        if(sin(time*.026F+1.3F)>.94F)move("nose",0,sin(time*1.3F)*.16F,0);
        rot("body",0,0,sin(time*.011F)*.014F);
        rot("tail_0",0,sin(time*.019F)*.05F,0);
        if(act==Act.NONE&&moving<.05F&&sit<.05F&&lie<.05F)rot("head",sin(time*.0075F)*.045F,sin(time*.013F)*.11F,sin(time*.0091F)*.04F);
        if(dog.mood()==Mood.RETURNING_HOME){rot("tail_0",.22F,0,0);rot("ear_left",.1F,0,0);rot("ear_right",.1F,0,0);}
        float excited=(dog.mood()==Mood.EXCITED||dog.mood()==Mood.PLAYFUL||act==Act.WAG_EXCITED)?1:0;
        for(int i=0;i<4;i++)rot("tail_"+i, sin(time*.05F-i*.5F)*.04F,sin(time*(excited>.5F?.48F:.12F)-i*.5F)*(.12F+excited*.24F)*(1-sleep*.8F),0);
        if(sin(time*.032F)>.96F){rot("ear_left",sin(time*.9F)*.12F,0,.06F);}
        if(sin(time*.039F+2)>.975F){rot("ear_right",sin(time*1.1F)*.1F,0,-.07F);}
        float jaw=0,squint=0;
        switch(act) {
            case LOOK -> rot("head",0,sin(age*.05F)*.45F*envelope,0);
            case TILT_LEFT -> rot("head",0,0,-.3F*envelope);
            case TILT_RIGHT -> rot("head",0,0,.3F*envelope);
            case SNIFF_AIR -> {rot("head",-.22F*envelope,0,0);move("nose",0,0,sin(age*.8F)*.14F*envelope);}
            case SNIFF_GROUND -> {rot("neck",.65F*envelope,0,0);move("head",0,3*envelope,0);rot("head",sin(age*.45F)*.04F*envelope,0,0);}
            case SCRATCH -> {rot("rear_left_leg",-1.25F*envelope,0,-1.0F*envelope);rot("rear_left_paw",sin(age*.9F)*.6F*envelope,0,0);rot("head",0,0,.25F*envelope);}
            case SHAKE -> {rot("body",0,sin(age*1.2F)*.16F*envelope,sin(age*1.2F)*.12F*envelope);rot("head",0,sin(age*1.2F+.8F)*.45F*envelope,0);rot("ear_left",0,0,sin(age*1.2F)*.25F*envelope);rot("ear_right",0,0,sin(age*1.2F)*.25F*envelope);}
            case STRETCH_FRONT, STRETCH -> {rot("body",.24F*envelope,0,0);move("chest",0,5*envelope,0);rot("front_left_leg",-1.0F*envelope,0,0);rot("front_right_leg",-1.0F*envelope,0,0);if(act==Act.STRETCH){rot("rear_left_leg",.6F*envelope,0,0);rot("rear_right_leg",.6F*envelope,0,0);}jaw=.2F*envelope;}
            case SIT_PANT, PANT -> {jaw=(.24F+sin(age*.3F)*.025F)*envelope;move("tongue",0,0,-2.6F*envelope);rot("tongue",sin(age*.25F)*.1F,0,0);}
            case CHIN -> {move("head",0,5*envelope,-1*envelope);rot("head",-.18F*envelope,0,0);}
            case SIDE -> {rot("body",0,0,.75F*envelope);move("body",2*envelope,0,0);rot("head",0,0,-.18F*envelope);}
            case SLEEP, SLEEP_TWITCH -> {if(sin(time*.017F)>.9F){rot("front_left_paw",sin(time*.9F)*.08F,0,0);rot("ear_right",0,0,sin(time*.65F)*.1F);}}
            case WAKE -> {rot("head",-.2F*envelope,0,0);rot("ear_left",0,0,.15F*envelope);rot("ear_right",0,0,-.15F*envelope);jaw=.3F*envelope;}
            case YAWN -> {jaw=.95F*envelope;rot("head",-.35F*envelope,0,0);rot("tongue",-.4F*envelope,0,0);squint=.7F*envelope;}
            case LICK_NOSE -> {jaw=.13F*envelope;move("tongue",0,-1.2F*envelope,-5*envelope);rot("tongue",-1*envelope,0,0);}
            case LICK_PAW -> {rot("front_left_leg",-1.15F*envelope,0,0);rot("neck",.4F*envelope,0,-.17F*envelope);jaw=.25F*envelope;move("tongue",0,0,-(1+sin(age*.5F))*envelope);}
            case PAW -> rot("front_right_leg",(-.4F+sin(age*.28F)*.3F)*envelope,0,0);
            case HOP -> move("body",0,-Math.abs(sin(age*.12F))*3*envelope,0);
            case ALERT, STARE -> {rot("ear_left",0,0,.12F*envelope);rot("ear_right",0,0,-.12F*envelope);rot("head",-.12F*envelope,0,0);}
            case GROWL -> {jaw=.3F*envelope;anger(envelope);rot("head",sin(age*.65F)*.025F,0,0);}
            case BARK, WHINE -> {jaw=(act==Act.BARK?.65F:.2F)*Math.max(0,sin(age*.22F))*envelope;rot("neck",-.12F*envelope,0,0);}
            case BITE -> {jaw=Math.max(0,sin(age/24*Mth.PI))*.95F;move("neck",0,0,-2.5F*envelope);anger(envelope);}
            case HEAD_BITE -> {
                Entity victim=dog.level().getEntity(dog.gagTarget());
                if(victim!=null&&dog.distanceToSqr(victim)<4.5){
                    jaw=.8F*envelope;rot("upper_jaw",-.15F*envelope,0,0);
                    float eye=(float)(victim.getEyeY()-dog.getY());move("head",0,(1.65F-eye)*16*envelope,0);
                    rot("head",-.18F*envelope,0,0);
                    if(age>65)jaw=.35F*envelope;
                } else jaw=.25F*envelope;
            }
            case EAT -> {rot("neck",.35F*envelope,0,0);jaw=(.18F+Math.abs(sin(age*.4F))*.28F)*envelope;if(age>65)rot("head",-.18F*envelope,0,0);}
            case DRINK -> {rot("neck",.7F*envelope,0,0);move("head",0,3*envelope,0);jaw=.2F*envelope;move("tongue",0,0,-Math.abs(sin(age*.6F))*3*envelope);}
            case POOP -> {rot("tail_0",-.75F*envelope,0,0);rot("body",.15F*envelope,0,0);rot("head",0,.25F*envelope,0);}
            case MOUNT -> {rot("head",0,.6F*envelope,0);rot("ear_right",0,0,-.2F*envelope);}
            case PREPARE_LEAP -> {move("body",0,4*envelope,0);rot("rear_left_leg",-1*envelope,0,0);rot("rear_right_leg",-1*envelope,0,0);rot("body",-.18F*envelope,0,0);}
            case LAND -> {float impact=Math.max(0,sin(age/16*Mth.PI));move("body",0,4.2F*impact,0);rot("front_left_leg",-.5F*impact,0,0);rot("front_right_leg",-.5F*impact,0,0);rot("head",.13F*impact,0,0);}
            case EAR_LEFT -> rot("ear_left",sin(age*.5F)*.22F*envelope,0,.12F*envelope);
            case EAR_RIGHT -> rot("ear_right",sin(age*.5F)*.22F*envelope,0,-.12F*envelope);
            case EAR_BOTH -> {rot("ear_left",sin(age*.5F)*.22F*envelope,0,.12F*envelope);rot("ear_right",sin(age*.5F)*.22F*envelope,0,-.12F*envelope);}
            case EAR_FLICK -> {rot("ear_left",sin(age*1.4F)*.34F*envelope,0,.2F*envelope);rot("head",0,0,.05F*envelope);}
            case HEAD_SHAKE -> {float s=sin(age*1.1F)*envelope;rot("head",0,s*.5F,s*.12F);rot("ear_left",0,0,s*.34F);rot("ear_right",0,0,s*.34F);}
            case SNEEZE -> {
                float wind=ease(age/9),snap=Math.max(0,sin((age-9)*.5F));
                rot("neck",(.3F*wind-.34F*snap)*envelope,0,0);rot("head",-.4F*snap*envelope,0,0);
                rot("ear_left",0,0,.3F*snap*envelope);rot("ear_right",0,0,-.3F*snap*envelope);
                jaw=.3F*snap*envelope;squint=Math.max(.55F*wind,.7F*snap)*envelope;
            }
            case LOOK_UP -> {rot("neck",-.42F*envelope,0,0);rot("head",-.25F*envelope,0,0);rot("ear_left",-.15F*envelope,0,.1F*envelope);rot("ear_right",-.15F*envelope,0,-.1F*envelope);}
            case PLAY_BOW -> {
                rot("body",-.42F*envelope,0,0);move("body",0,5*envelope,0);rot("neck",.25F*envelope,0,0);
                for(String s:new String[]{"front_left","front_right"}){rot(s+"_leg",.85F*envelope,0,0);rot(s+"_paw",-.7F*envelope,0,0);}
                for(String s:new String[]{"rear_left","rear_right"})rot(s+"_leg",-.2F*envelope,0,0);
                rot("tail_0",-.6F*envelope,0,0);jaw=.2F*envelope;
            }
            case TAIL_CHASE -> {
                rot("body",0,sin(age*.2F)*.3F*envelope,0);rot("neck",0,.8F*envelope,0);rot("head",0,.5F*envelope,.2F*envelope);
                for(int i=0;i<4;i++)rot("tail_"+i,0,-.35F*envelope,0);
            }
            case LICK_PLAYER -> {
                Entity friend=dog.level().getEntity(dog.gagTarget());
                if(friend!=null&&dog.distanceToSqr(friend)<6.5) {
                    float eye=(float)(friend.getEyeY()-dog.getY());move("head",0,(1.55F-eye)*13*envelope,0);
                }
                // Three or four upward sweeps of the tongue, with the tail going the whole time.
                float sweep=Math.max(0,sin(age*.42F));
                rot("neck",-.1F*envelope,0,0);rot("head",(-.12F+sweep*.1F)*envelope,0,.14F*envelope);
                jaw=(.16F+sweep*.2F)*envelope;
                move("tongue",0,-1.6F*sweep*envelope,-(2.6F+sweep*4.2F)*envelope);
                rot("tongue",(-1.15F+sweep*.8F)*envelope,0,.1F*envelope);
                rot("ear_left",.14F*envelope,0,.1F*envelope);rot("ear_right",.14F*envelope,0,-.1F*envelope);
                for(int i=0;i<4;i++)rot("tail_"+i,0,sin(age*.5F-i*.5F)*.3F*envelope,0);
            }
            case POUT -> {
                // Sulking at something reads as a scowl; sulking for food reads as pleading.
                boolean cross=dog.mood()==Mood.SUSPICIOUS||dog.mood()==Mood.PROTECTIVE||dog.mood()==Mood.ALERT;
                rot("neck",.3F*envelope,0,0);move("head",0,2.2F*envelope,0);
                rot("head",.16F*envelope,sin(age*.05F)*.16F*envelope,0);
                rot("ear_left",.44F*envelope,0,.32F*envelope);rot("ear_right",.44F*envelope,0,-.32F*envelope);
                rot("tail_0",.42F*envelope,0,0);rot("tail_1",.2F*envelope,0,0);
                for(String s:new String[]{"front_left","front_right"})rot(s+"_leg",.12F*envelope,0,0);
                jaw=.05F*envelope;squint=(cross?.3F:.16F)*envelope;
                if(cross)anger(envelope);
                else {rot("brow_left",0,0,.36F*envelope);rot("brow_right",0,0,-.36F*envelope);}
            }
            default -> {}
        }
        if(!dog.onGround()&&!dog.isInWater()) {
            float vy=(float)dog.getDeltaMovement().y;rot("body",Mth.clamp(-vy*.2F,-.25F,.2F),0,0);
            for(String s:new String[]{"front_left","front_right"})rot(s+"_leg",vy<-.3F?-.9F:-1.05F,0,0);
            for(String s:new String[]{"rear_left","rear_right"})rot(s+"_leg",vy>0?.9F:.25F,0,0);
            rot("tail_0",.35F,0,0);
        }
        dog.clientJaw=Mth.lerp(blend,dog.clientJaw,jaw);rot("lower_jaw",dog.clientJaw,0,0);
        // Lids rest folded back inside the skull; closing them is a rotation towards zero.
        float shut=Math.max(Math.max(blink(time*.05F+dog.getId()*.83F),sleep),squint);
        part("eyelid_left").xRot*=1-shut;part("eyelid_right").xRot*=1-shut;
    }
    private static float pulse(float x,float start,float len){float u=(x-start)/len;return u<0||u>1?0:sin(u*Mth.PI);}
    /** About one blink every four seconds, with an occasional quick double. */
    private static float blink(float t){
        float phase=t%4.1F;
        return Math.max(pulse(phase,0,.34F),sin(t*.31F)>.35F?pulse(phase,.52F,.28F):0);
    }
    private void anger(float w){rot("brow_left",0,0,-.4F*w);rot("brow_right",0,0,.4F*w);part("eye_left").yScale=1-.25F*w;part("eye_right").yScale=1-.25F*w;}
}
