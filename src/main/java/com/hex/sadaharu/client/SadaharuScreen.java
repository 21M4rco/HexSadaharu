package com.hex.sadaharu.client;
import com.hex.sadaharu.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import java.util.ArrayList;
import java.util.List;
public final class SadaharuScreen extends AbstractContainerScreen<SadaharuMenu> {
    private Button clear,night,follow,home,pet,play;
    private final List<Button> controls=new ArrayList<>();
    private int waitingRevision=-1,waitingTicks,lastAction=-1,feedbackTicks;
    public SadaharuScreen(SadaharuMenu m,Inventory i,Component title){super(m,i,title);imageWidth=290;imageHeight=238;}
    private Button add(Button button){controls.add(button);return addRenderableWidget(button);}
    @Override protected void init(){super.init();controls.clear();
        add(Button.builder(Component.literal("Set this spot as home"),b->send(0)).bounds(leftPos+16,topPos+104,258,20).build());
        clear=add(Button.builder(Component.empty(),b->send(1)).bounds(leftPos+16,topPos+128,124,20).build());
        night=add(Button.builder(Component.empty(),b->send(2)).bounds(leftPos+148,topPos+128,126,20).build());
        add(Button.builder(Component.literal("Ride Sadaharu"),b->send(3)).bounds(leftPos+16,topPos+152,124,20).build());
        home=add(Button.builder(Component.literal("Send him home"),b->send(4)).bounds(leftPos+148,topPos+152,126,20).build());
        follow=add(Button.builder(Component.empty(),b->send(5)).bounds(leftPos+16,topPos+176,258,20).build());
        pet=add(Button.builder(Component.literal("Pet him"),b->send(6)).bounds(leftPos+16,topPos+200,124,20).build());
        play=add(Button.builder(Component.literal("Let's play!"),b->send(7)).bounds(leftPos+148,topPos+200,126,20).build());
        refresh();
    }
    private void send(int id){
        if(minecraft!=null&&minecraft.gameMode!=null&&waitingRevision<0){
            waitingRevision=menu.revision();waitingTicks=0;lastAction=id;feedbackTicks=0;
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id);refresh();
        }
    }
    private void refresh(){
        if(follow==null)return;
        if(waitingRevision>=0&&menu.revision()!=waitingRevision){waitingRevision=-1;feedbackTicks=50;}
        boolean ready=waitingRevision<0;
        for(Button button:controls)button.active=ready;
        clear.active=ready&&menu.hasHome();home.active=ready&&menu.hasHome();
        clear.setMessage(Component.literal(menu.hasHome()?"Clear home":"No home set"));
        night.setMessage(Component.literal("Night return: "+(menu.automatic()?"ON":"OFF")));
        follow.setMessage(Component.literal(menu.following()?"Following you  |  Click to stay":"Staying nearby  |  Click to follow"));
        pet.setMessage(Component.literal(feedbackTicks>0&&lastAction==6?"Happy dog!":"Pet him"));
        play.setMessage(Component.literal(feedbackTicks>0&&lastAction==7?"Playtime!":"Let's play!"));
        if(waitingRevision>=0){Button pending=switch(lastAction){case 1->clear;case 2->night;case 5->follow;case 6->pet;case 7->play;default->null;};if(pending!=null)pending.setMessage(Component.literal("One moment..."));}
    }
    @Override protected void containerTick(){super.containerTick();if(feedbackTicks>0)feedbackTicks--;if(waitingRevision>=0&&++waitingTicks>60)waitingRevision=-1;refresh();}
    @Override protected void renderBg(GuiGraphics g,float pt,int mx,int my){g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xF222252B);g.fill(leftPos,topPos,leftPos+imageWidth,topPos+3,0xFFE95B60);}
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){
        g.drawString(font,"SADAHARU",16,13,0xFFF5F2,false);
        g.drawString(font,"A very big dog. A very soft spot for you.",16,27,0xAFB4BF,false);
        g.drawString(font,"Satiety "+menu.hunger()/10+"%",16,43,0xEADCBF,false);
        g.drawString(font,"Health "+menu.health()+" / 60",160,43,0xEFB4B4,false);
        g.fill(16,55,140,59,0xFF41454F);g.fill(16,55,16+(int)(124*menu.hunger()/1000F),59,0xFFE8C889);
        g.fill(160,55,274,59,0xFF41454F);g.fill(160,55,160+(int)(114*Math.min(1F,menu.health()/60F)),59,0xFFD05B5B);
        g.drawString(font,menu.mood().name().toLowerCase().replace('_',' '),16,65,0xCCDDAF,false);
        String label="Home: "+menu.homeText;
        g.drawString(font,font.plainSubstrByWidth(label,258),16,80,0xAFB4BF,false);
        if(feedbackTicks>0&&lastAction<=2)g.drawString(font,lastAction==0?"Home saved!":lastAction==1?"Home cleared":"Night return updated",16,92,0xCCDDAF,false);
        g.drawString(font,"Shift + right-click rides. "+ClientEvents.CALL.getTranslatedKeyMessage().getString()+" calls him.",16,226,0x8D96A6,false);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float pt){refresh();renderBackground(g);super.render(g,mx,my,pt);renderTooltip(g,mx,my);if(mx>=leftPos+16&&mx<leftPos+274&&my>=topPos+78&&my<topPos+91)g.renderTooltip(font,Component.literal(menu.homeText),mx,my);}
}
