package com.hex.sadaharu;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
public final class Network {
    private static final String VERSION="1";
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(HexSadaharu.id("companion"),()->VERSION,VERSION::equals,VERSION::equals);
    public record Call() {
        static void encode(Call m,FriendlyByteBuf b){}
        static Call decode(FriendlyByteBuf b){return new Call();}
        static void handle(Call m,Supplier<NetworkEvent.Context> supplier){var c=supplier.get();c.enqueueWork(()->{if(c.getSender()!=null)WorldEvents.call(c.getSender());});c.setPacketHandled(true);}
    }
    public static void init(){CHANNEL.messageBuilder(Call.class,0,NetworkDirection.PLAY_TO_SERVER).encoder(Call::encode).decoder(Call::decode).consumerMainThread(Call::handle).add();}
}
