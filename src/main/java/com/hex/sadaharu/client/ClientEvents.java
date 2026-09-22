package com.hex.sadaharu.client;
import com.hex.sadaharu.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;
@Mod.EventBusSubscriber(modid=HexSadaharu.ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class ClientEvents {
    public static final KeyMapping CALL=new KeyMapping("key.hexsadaharu.call",GLFW.GLFW_KEY_H,"key.categories.hexsadaharu");
    @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions e){e.registerLayerDefinition(SadaharuModel.LAYER,SadaharuModel::layer);}
    @SubscribeEvent public static void renderer(EntityRenderersEvent.RegisterRenderers e){e.registerEntityRenderer(HexSadaharu.DOG.get(),SadaharuRenderer::new);}
    @SubscribeEvent public static void keys(RegisterKeyMappingsEvent e){e.register(CALL);}
    @SubscribeEvent public static void setup(FMLClientSetupEvent e){e.enqueueWork(()->MenuScreens.register(HexSadaharu.MENU.get(),SadaharuScreen::new));}
    @Mod.EventBusSubscriber(modid=HexSadaharu.ID,value=Dist.CLIENT)
    public static final class Ticks {
        private static int reviewWait,reviewLife;
        @SubscribeEvent public static void review(TickEvent.ClientTickEvent e){
            if(e.phase!=TickEvent.Phase.END||!Boolean.getBoolean("hexsadaharu.review"))return;
            Minecraft mc=Minecraft.getInstance();
            // The review run always ends itself, even if the screen it waits for never arrives.
            if(++reviewLife>2400){mc.stop();return;}
            if(mc.getOverlay()==null&&mc.screen!=null&&!(mc.screen instanceof ModelReviewScreen)&&++reviewWait>80)mc.setScreen(new ModelReviewScreen());
        }
        @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){if(e.phase==TickEvent.Phase.END)while(CALL.consumeClick()){if(Minecraft.getInstance().player!=null&&Minecraft.getInstance().screen==null)Network.CHANNEL.sendToServer(new Network.Call());}}
    }
}
