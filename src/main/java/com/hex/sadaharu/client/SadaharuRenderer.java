package com.hex.sadaharu.client;
import com.hex.sadaharu.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
public final class SadaharuRenderer extends MobRenderer<Sadaharu,SadaharuModel> {
    public SadaharuRenderer(EntityRendererProvider.Context c){super(c,new SadaharuModel(c.bakeLayer(SadaharuModel.LAYER)),1.05F);}
    @Override public ResourceLocation getTextureLocation(Sadaharu dog){return HexSadaharu.id("textures/entity/sadaharu.png");}
}
