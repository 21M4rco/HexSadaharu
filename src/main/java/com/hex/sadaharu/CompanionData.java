package com.hex.sadaharu;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/** The overworld owns the one-per-save identity, including while its entity is unloaded. */
public final class CompanionData extends SavedData {
    @Nullable public UUID dog, owner;
    /** Transient handle on the entity currently in memory; never serialized. */
    @Nullable private Sadaharu live;
    public ResourceKey<Level> dimension=Level.OVERWORLD;
    public BlockPos position=BlockPos.ZERO;
    public CompoundTag memory=new CompoundTag();
    public static CompanionData get(MinecraftServer s) { return s.overworld().getDataStorage().computeIfAbsent(CompanionData::load,CompanionData::new,"hexsadaharu_unique"); }
    public static CompanionData load(CompoundTag n) {
        CompanionData d=new CompanionData();
        if(n.hasUUID("Dog")) d.dog=n.getUUID("Dog"); if(n.hasUUID("Owner"))d.owner=n.getUUID("Owner");
        if(n.contains("Dimension")) d.dimension=ResourceKey.create(Registries.DIMENSION,new ResourceLocation(n.getString("Dimension")));
        d.position=BlockPos.of(n.getLong("Position"));d.memory=n.getCompound("Memory");return d;
    }
    @Override public CompoundTag save(CompoundTag n) {
        if(dog!=null)n.putUUID("Dog",dog); if(owner!=null)n.putUUID("Owner",owner);
        n.putString("Dimension",dimension.location().toString());n.putLong("Position",position.asLong());n.put("Memory",memory.copy());return n;
    }
    public void capture(Sadaharu s) {
        if(dog!=null&&!dog.equals(s.getUUID()))return;
        live=s;
        dog=s.getUUID();owner=s.ownerId();dimension=s.level().dimension();position=s.blockPosition();
        CompoundTag n=new CompoundTag();s.writeCompanion(n);memory=n;setDirty();
    }
    /**
     * A level only answers {@link ServerLevel#getEntity(UUID)} once the owning chunk has been promoted,
     * and that promotion is queued onto the server thread rather than applied inline. Right after a
     * dimension transfer the entity therefore exists but is not yet indexed, so the live handle is
     * consulted first and the per-level scan is only the fallback.
     */
    @Nullable public Sadaharu loaded(MinecraftServer server) {
        if(dog==null)return null;
        if(live!=null&&!live.isRemoved()&&dog.equals(live.getUUID())&&live.level() instanceof ServerLevel l&&l.getServer()==server)return live;
        live=null;
        for(ServerLevel l:server.getAllLevels())if(l.getEntity(dog) instanceof Sadaharu s&&!s.isRemoved()){live=s;return s;}
        return null;
    }
    /** Forgets both the reserved identity and the live handle; used when a duplicate is rejected. */
    public void release() {dog=null;owner=null;live=null;setDirty();}
}
