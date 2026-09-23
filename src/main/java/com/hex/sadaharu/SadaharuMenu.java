package com.hex.sadaharu;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

public final class SadaharuMenu extends AbstractContainerMenu {
    @Nullable public final Sadaharu dog;
    public String homeText;
    private final ContainerData data;
    private final Player viewer;
    private int revision;
    public SadaharuMenu(int id,Inventory inv,FriendlyByteBuf b){this(id,inv,inv.player.level().getEntity(b.readInt()) instanceof Sadaharu d?d:null);homeText=b.readUtf();}
    public SadaharuMenu(int id,Inventory inv,@Nullable Sadaharu dog) {
        super(HexSadaharu.MENU.get(),id);this.dog=dog;viewer=inv.player;homeText=dog==null?"No home set":dog.homeLabel();
        data=dog==null||inv.player.level().isClientSide?new SimpleContainerData(7):new ContainerData(){
            public int get(int i){return switch(i){case 0->dog.hunger();case 1->dog.mood().ordinal();case 2->dog.automaticHome?1:0;case 3->dog.home!=null?1:0;case 4->dog.following?1:0;case 5->(int)Math.ceil(dog.getHealth());case 6->revision;default->0;};}
            public void set(int i,int v){}public int getCount(){return 7;}
        };
        addDataSlots(data);
    }
    public int hunger(){return data.get(0);}
    public Mood mood(){return Mood.values()[Math.floorMod(data.get(1),Mood.values().length)];}
    public boolean automatic(){return data.get(2)==1;}
    public boolean hasHome(){return data.get(3)==1;}
    public boolean following(){return data.get(4)==1;}
    public int health(){return data.get(5);}
    public int revision(){return data.get(6);}
    @Override public boolean stillValid(Player p){return dog!=null&&dog.isAlive()&&dog.downed<=0&&dog.ownedBy(p)&&p.distanceToSqr(dog)<100;}
    @Override public ItemStack quickMoveStack(Player p,int slot){return ItemStack.EMPTY;}
    @Override public void broadcastChanges(){
        super.broadcastChanges();
        if(dog!=null&&viewer instanceof ServerPlayer sp){
            String label=dog.homeLabel();
            if(!label.equals(homeText)){homeText=label;Network.CHANNEL.send(PacketDistributor.PLAYER.with(()->sp),new Network.HomeState(containerId,homeText));}
        }
    }
    @Override public boolean clickMenuButton(Player p,int button) {
        if(p.level().isClientSide||!stillValid(p))return false;
        switch(button){
            case 0->{dog.home=dog.blockPosition();dog.homeDimension=dog.level().dimension();}
            case 1->{dog.home=null;dog.homebound=0;dog.memories.remember("stay_home",dog.now(),0);}
            case 2->dog.automaticHome=!dog.automaticHome;
            case 5->{
                dog.following=!dog.following;
                if(dog.following){dog.memories.remember("stay_home",dog.now(),0);dog.homebound=0;dog.setMood(Mood.EXCITED);dog.voice("excited",.6F);}
                else {dog.getNavigation().stop();dog.setMood(Mood.CALM);}
                dog.setAct(Act.NONE);dog.gagTarget(-1);
            }
            case 3->{p.closeContainer();return dog.mount(p);}
            case 4->{
                if(!dog.sendHome())return false;
                p.closeContainer();p.displayClientMessage(net.minecraft.network.chat.Component.literal("Sadaharu sets off for home."),true);return true;
            }
            case 6->{if(!dog.personality.ownerGesture(p,Act.PETTED))return false;}
            case 7->{if(!dog.personality.ownerGesture(p,Act.PLAY_BOW))return false;}
            default->{return false;}
        }
        CompanionData.get(p.getServer()).capture(dog);
        revision=(revision+1)&32767;
        broadcastChanges();
        return true;
    }
}
