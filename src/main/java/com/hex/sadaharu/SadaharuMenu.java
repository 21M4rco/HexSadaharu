package com.hex.sadaharu;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
public final class SadaharuMenu extends AbstractContainerMenu {
    @Nullable public final Sadaharu dog;
    public String homeText;
    private final ContainerData data;
    public SadaharuMenu(int id,Inventory inv,FriendlyByteBuf b){this(id,inv,inv.player.level().getEntity(b.readInt()) instanceof Sadaharu d?d:null);homeText=b.readUtf();}
    public SadaharuMenu(int id,Inventory inv,@Nullable Sadaharu dog) {
        super(HexSadaharu.MENU.get(),id);this.dog=dog;homeText=dog==null?"No home set":dog.homeLabel();
        data=dog==null||inv.player.level().isClientSide?new SimpleContainerData(6):new ContainerData(){
            public int get(int i){return switch(i){case 0->dog.hunger();case 1->dog.mood().ordinal();case 2->dog.automaticHome?1:0;case 3->dog.home==null?0:dog.home.getX();case 4->dog.home==null?Integer.MIN_VALUE:dog.home.getY();case 5->dog.home==null?0:dog.home.getZ();default->0;};}
            public void set(int i,int v){}public int getCount(){return 6;}
        };
        addDataSlots(data);
    }
    public int hunger(){return data.get(0);}
    public Mood mood(){return Mood.values()[Math.floorMod(data.get(1),Mood.values().length)];}
    public boolean automatic(){return data.get(2)==1;}
    @Override public boolean stillValid(Player p){return dog!=null&&dog.isAlive()&&dog.ownedBy(p)&&p.distanceToSqr(dog)<100;}
    @Override public ItemStack quickMoveStack(Player p,int slot){return ItemStack.EMPTY;}
    @Override public boolean clickMenuButton(Player p,int button) {
        if(p.level().isClientSide||!stillValid(p))return false;
        switch(button){case 0->{dog.home=dog.blockPosition();dog.homeDimension=dog.level().dimension();}case 1->dog.home=null;case 2->dog.automaticHome=!dog.automaticHome;case 3->{p.closeContainer();return dog.mount(p);}default->{return false;}}
        CompanionData.get(p.getServer()).capture(dog);
        // Reopen to synchronize the dimension/coordinates exactly (container shorts cannot hold world coordinates).
        if(p instanceof net.minecraft.server.level.ServerPlayer sp)net.minecraftforge.network.NetworkHooks.openScreen(sp,new net.minecraft.world.SimpleMenuProvider((id,inv,player)->new SadaharuMenu(id,inv,dog),net.minecraft.network.chat.Component.literal("Sadaharu")),b->{b.writeInt(dog.getId());b.writeUtf(dog.homeLabel());});
        return true;
    }
}
