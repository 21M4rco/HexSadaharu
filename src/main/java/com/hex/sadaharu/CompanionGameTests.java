package com.hex.sadaharu;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.gametest.*;
import java.util.UUID;

@GameTestHolder(HexSadaharu.ID)
@PrefixGameTestTemplate(false)
public final class CompanionGameTests {
    @GameTest(template="empty",timeoutTicks=160)
    public static void immortalUniquePersistent(GameTestHelper h) {
        var level=h.getLevel();var data=CompanionData.get(level.getServer());
        data.dog=null;data.owner=null;
        Sadaharu dog=HexSadaharu.DOG.get().create(level);
        BlockPos p=h.absolutePos(new BlockPos(7,2,7));dog.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,0,0);
        h.assertTrue(level.addFreshEntity(dog),"Initial Sadaharu must join");
        UUID owner=UUID.randomUUID();dog.setOwner(owner);dog.home=p;dog.setHunger(432);dog.memories.remember("test",dog.now(),4000);data.capture(dog);
        dog.hurt(level.damageSources().genericKill(),Float.MAX_VALUE);
        dog.setHealth(0);dog.die(level.damageSources().genericKill());dog.remove(Entity.RemovalReason.DISCARDED);
        h.assertTrue(dog.isAlive()&&!dog.isRemoved(),"Lethal damage and ordinary cleanup cannot remove Sadaharu");
        Sadaharu duplicate=HexSadaharu.DOG.get().create(level);duplicate.moveTo(p.getX()+4,p.getY(),p.getZ(),0,0);
        h.assertTrue(!level.addFreshEntity(duplicate),"A second UUID must be rejected");
        CompoundTag saved=new CompoundTag();dog.saveWithoutId(saved);
        Sadaharu restored=HexSadaharu.DOG.get().create(level);restored.load(saved);
        h.assertTrue(owner.equals(restored.ownerId())&&restored.hunger()==432&&p.equals(restored.home),"Ownership, satiety and home round trip");
        h.assertTrue(restored.memories.remembers("test",dog.now()),"Behavior memory round trips");
        CompanionData copy=CompanionData.load(data.save(new CompoundTag()));
        h.assertTrue(copy.dog.equals(dog.getUUID())&&copy.owner.equals(owner),"Unique world record round trips");
        h.assertTrue(SafeTravel.landing(level,p,dog)!=null,"Full-size safe placement finds the test floor");
        h.runAfterDelay(40,()->{h.assertTrue(dog.isAlive()&&dog.getHealth()>0,"Immortality survives subsequent ticks");dog.rejectDuplicate();data.dog=null;data.owner=null;data.setDirty();h.succeed();});
    }
}
