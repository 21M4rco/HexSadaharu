package com.hex.sadaharu.client;
import com.hex.sadaharu.SadaharuMenu;
import net.minecraft.client.Minecraft;
public final class MenuUpdates {
    public static void home(int id,String label){
        var player=Minecraft.getInstance().player;
        if(player!=null&&player.containerMenu instanceof SadaharuMenu menu&&menu.containerId==id)menu.homeText=label;
    }
}
