package com.hex.sadaharu.client;

import com.hex.sadaharu.HexSadaharu;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;

/** Opt-in Actions-only render smoke test. Never opens during ordinary play. */
public final class ModelReviewScreen extends Screen {
    private final SadaharuModel model;
    private int frames;
    public ModelReviewScreen(){super(Component.literal("Sadaharu model review"));model=new SadaharuModel(Minecraft.getInstance().getEntityModels().bakeLayer(SadaharuModel.LAYER));}
    @Override public void render(GuiGraphics g,int mx,int my,float partial) {
        g.fill(0,0,width,height,0xFFCFD9E6);
        g.drawString(font,"SADAHARU / Minecraft runtime model",12,10,0x25344A,false);
        String[] names={"Front","Side","Rear","Sitting","Mouth open","Bounding"};
        float[] angles={0,90,180,25,15,55};
        int cellW=width/3,cellH=(height-30)/2;
        for(int i=0;i<6;i++) {
            model.root().getAllParts().forEach(ModelPart::resetPose);
            if(i==3){model.part("body").y+=6;model.part("body").xRot-=.3F;model.part("neck").xRot+=.3F;
                for(String s:new String[]{"front_left","front_right"}){model.part(s+"_leg").y-=4;model.part(s+"_leg").xRot+=.3F;}
                for(String s:new String[]{"rear_left","rear_right"}){model.part(s+"_leg").xRot-=1.1F;model.part(s+"_paw").xRot+=1.3F;}}
            if(i==4){model.part("lower_jaw").xRot+=.95F;model.part("head").xRot-=.2F;}
            if(i==5){model.part("body").xRot-=.18F;model.part("body").y-=3;for(String s:new String[]{"front_left","front_right"})model.part(s+"_leg").xRot-=1.05F;for(String s:new String[]{"rear_left","rear_right"})model.part(s+"_leg").xRot+=.9F;}
            int x=(i%3)*cellW,y=30+(i/3)*cellH;
            g.drawString(font,names[i],x+12,y+5,0x25344A,false);
            PoseStack p=g.pose();p.pushPose();p.translate(x+cellW*.5,y+cellH*.62,200);
            float scale=Math.min(cellW/4.8F,cellH/4F);p.scale(scale,scale,scale);
            p.mulPose(Axis.ZP.rotationDegrees(180));p.mulPose(Axis.XP.rotationDegrees(-10));p.mulPose(Axis.YP.rotationDegrees(angles[i]));
            com.mojang.blaze3d.platform.Lighting.setupForEntityInInventory();
            model.root().render(p,g.bufferSource().getBuffer(RenderType.entityCutoutNoCull(HexSadaharu.id("textures/entity/sadaharu.png"))),15728880,OverlayTexture.NO_OVERLAY);
            g.flush();p.popPose();
        }
        frames++;
        if(frames==60)Screenshot.grab(minecraft.gameDirectory,"sadaharu-runtime-review.png",minecraft.getMainRenderTarget(),message->{});
        if(frames>180)minecraft.stop();
    }
    @Override public boolean isPauseScreen(){return false;}
}
