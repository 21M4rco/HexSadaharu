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
        restored.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        h.assertTrue(restored.isRemoved(),"Non-destructive chunk unload remains permitted");
        h.assertTrue(data.dog.equals(dog.getUUID()),"Chunk unload does not erase the reserved identity");
        CompanionData copy=CompanionData.load(data.save(new CompoundTag()));
        h.assertTrue(copy.dog.equals(dog.getUUID())&&copy.owner.equals(owner),"Unique world record round trips");
        h.assertTrue(SafeTravel.landing(level,p,dog)!=null,"Full-size safe placement finds the test floor");
        for(Act a:Act.values()){dog.setAct(a);h.assertTrue(dog.act()==a,"Act "+a+" survives the synched encoding");}
        for(String voice:HexSadaharu.VOICES)h.assertTrue(net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.getValue(HexSadaharu.id(voice))!=null,"Sound event "+voice+" is registered");
        // A casual interaction aimed at nobody has to release itself rather than freezing him.
        for(Act gesture:new Act[]{Act.LICK_PLAYER,Act.HEAD_BITE}) {
            dog.setAct(gesture);dog.gagTarget(Integer.MAX_VALUE);dog.personality.tick();
            h.assertTrue(dog.act()==Act.NONE&&dog.gagTarget()==-1,gesture+" with no target clears itself");
        }
        dog.setAct(Act.POUT);
        h.assertTrue(dog.act()==Act.POUT&&!dog.act().resting(),"Pouting is a timed gesture, not a resting pose that stalls the loop");
        dog.setAct(Act.NONE);
        h.runAfterDelay(40,()->{
            h.assertTrue(dog.isAlive()&&dog.getHealth()>0,"Immortality survives subsequent ticks");
            var destination=level.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
            h.assertTrue(destination!=null,"Cross-dimensional test destination exists");
            var landing=new net.minecraft.world.phys.Vec3(8.5,110,8.5);
            destination.getChunk(0,0);
            h.assertTrue(SafeTravel.teleport(dog,destination,landing),"Cross-dimensional transfer succeeds");
            Sadaharu transferred=data.loaded(level.getServer());
            h.assertTrue(transferred!=null&&transferred.getUUID().equals(dog.getUUID())&&transferred.level()==destination,"Transfer retains exactly the same UUID");
            h.assertTrue(owner.equals(transferred.ownerId())&&p.equals(transferred.home),"Transfer retains owner and home");
            h.assertTrue(SafeTravel.teleport(transferred,level,net.minecraft.world.phys.Vec3.atBottomCenterOf(p)),"Return transfer succeeds");
            data.loaded(level.getServer()).rejectDuplicate();data.dog=null;data.owner=null;data.setDirty();h.succeed();
        });
    }
}
