package com.hex.sadaharu.client;
import com.hex.sadaharu.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
public final class SadaharuScreen extends AbstractContainerScreen<SadaharuMenu> {
    public SadaharuScreen(SadaharuMenu m,Inventory i,Component title){super(m,i,title);imageWidth=290;imageHeight=322;}
    @Override protected void init(){super.init();
        addRenderableWidget(Button.builder(Component.literal("Set Current Position as Home"),b->send(0)).bounds(leftPos+16,topPos+142,258,20).build());
        addRenderableWidget(Button.builder(Component.literal("Clear home"),b->send(1)).bounds(leftPos+16,topPos+168,124,20).build());
        addRenderableWidget(Button.builder(Component.literal("Night return: "+(menu.automatic()?"On":"Off")),b->send(2)).bounds(leftPos+148,topPos+168,126,20).build());
        addRenderableWidget(Button.builder(Component.literal("Ride Sadaharu"),b->send(3)).bounds(leftPos+16,topPos+200,258,24).build());
        addRenderableWidget(Button.builder(Component.literal(menu.following()?"Following you \u2014 tell him to stay":"Staying put \u2014 tell him to follow"),b->send(5)).bounds(leftPos+16,topPos+230,258,24).build());
        Button home=Button.builder(Component.literal("Send him home"),b->send(4)).bounds(leftPos+16,topPos+260,258,24).build();
        home.active=menu.hasHome();addRenderableWidget(home);
    }
    private void send(int id){if(minecraft!=null&&minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id);}
    @Override protected void renderBg(GuiGraphics g,float pt,int mx,int my){g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xF222252B);g.fill(leftPos,topPos,leftPos+imageWidth,topPos+3,0xFFE95B60);}
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){
        g.drawString(font,"SADAHARU",16,16,0xFFF5F2,false);
        g.drawString(font,"Your enormous, occasionally sensible friend.",16,32,0xAFB4BF,false);
        g.drawString(font,"Satiety "+menu.hunger()/10+"%",16,53,0xEADCBF,false);
        g.drawString(font,menu.mood().name().toLowerCase().replace('_',' '),160,53,0xCCDDAF,false);
        g.fill(16,67,274,71,0xFF41454F);g.fill(16,67,16+(int)(258*menu.hunger()/1000F),71,0xFFE8C889);
        g.drawString(font,"Health "+menu.health()+" / 60  ("+(menu.health()/2)+" hearts)",16,80,0xEFB4B4,false);
        g.fill(16,94,274,98,0xFF41454F);g.fill(16,94,16+(int)(258*Math.min(1F,menu.health()/60F)),98,0xFFD05B5B);
        g.drawWordWrap(font,Component.literal("Home: "+menu.homeText),16,110,258,0xAFB4BF);
        g.drawString(font,"Shift + right-click mounts. "+ClientEvents.CALL.getTranslatedKeyMessage().getString()+" calls him.",16,302,0x8D96A6,false);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float pt){renderBackground(g);super.render(g,mx,my,pt);renderTooltip(g,mx,my);}
}
