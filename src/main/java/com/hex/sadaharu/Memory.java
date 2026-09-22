package com.hex.sadaharu;
import java.util.*;
import net.minecraft.nbt.*;
/** Bounded, persisted timestamps; uses overworld game time, not wall time. */
public final class Memory {
    private final LinkedHashMap<String,Long> until=new LinkedHashMap<>();
    public boolean remembers(String key,long now) { return until.getOrDefault(key,0L)>now; }
    public void remember(String key,long now,long duration) {
        until.remove(key);until.put(key,now+duration);
        while(until.size()>128)until.remove(until.keySet().iterator().next());
    }
    public void expire(long now) { until.entrySet().removeIf(e->e.getValue()<=now); }
    public CompoundTag save() { CompoundTag n=new CompoundTag();until.forEach(n::putLong);return n; }
    public void load(CompoundTag n) {until.clear(); for(String k:n.getAllKeys()) {if(until.size()>=128)break;until.put(k,n.getLong(k));} }
}
