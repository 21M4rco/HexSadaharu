package com.hex.sadaharu;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
public final class Network {
    private static final String VERSION="2";
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(HexSadaharu.id("companion"),()->VERSION,VERSION::equals,VERSION::equals);
    public record Call() {
        static void encode(Call m,FriendlyByteBuf b){}
        static Call decode(FriendlyByteBuf b){return new Call();}
        static void handle(Call m,Supplier<NetworkEvent.Context> supplier){var c=supplier.get();c.enqueueWork(()->{if(c.getSender()!=null)WorldEvents.call(c.getSender());});c.setPacketHandled(true);}
    }
    public record HomeState(int containerId,String label) {
        static void encode(HomeState m,FriendlyByteBuf b){b.writeVarInt(m.containerId);b.writeUtf(m.label,512);}
        static HomeState decode(FriendlyByteBuf b){return new HomeState(b.readVarInt(),b.readUtf(512));}
        static void handle(HomeState m,Supplier<NetworkEvent.Context> supplier){
            var c=supplier.get();c.enqueueWork(()->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->com.hex.sadaharu.client.MenuUpdates.home(m.containerId,m.label)));c.setPacketHandled(true);
        }
    }
    public static void init(){CHANNEL.messageBuilder(Call.class,0,NetworkDirection.PLAY_TO_SERVER).encoder(Call::encode).decoder(Call::decode).consumerMainThread(Call::handle).add();CHANNEL.messageBuilder(HomeState.class,1,NetworkDirection.PLAY_TO_CLIENT).encoder(HomeState::encode).decoder(HomeState::decode).consumerMainThread(HomeState::handle).add();}
}
