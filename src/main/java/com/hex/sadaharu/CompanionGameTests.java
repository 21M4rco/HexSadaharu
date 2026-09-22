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
        h.assertTrue(dog.getMaxHealth()==60,"Thirty hearts of health");
        // Ordinary damage is real damage now. The exact figure after mitigation is vanilla's
        // business, so assert the direction and report the numbers if it ever fails.
        dog.setHealth(dog.getMaxHealth());dog.invulnerableTime=0;
        float before=dog.getHealth();
        boolean landed=dog.hurt(level.damageSources().generic(),9);
        h.assertTrue(landed&&dog.getHealth()<before&&dog.getHealth()>0,
            "An ordinary hit takes health off him (landed="+landed+", "+before+" -> "+dog.getHealth()+" of "+dog.getMaxHealth()+", down="+dog.downed+")");
        // A lethal blow puts him down; it must never remove the one reserved entity.
        dog.setHealth(dog.getMaxHealth());dog.invulnerableTime=0;
        dog.hurt(level.damageSources().genericKill(),Float.MAX_VALUE);
        h.assertTrue(dog.downed>0,"A lethal blow knocks him down (down="+dog.downed+", health="+dog.getHealth()+")");
        h.assertTrue(dog.isAlive()&&dog.getHealth()>0&&!dog.isRemoved(),
            "Being knocked down never removes him or zeroes his health (alive="+dog.isAlive()+", health="+dog.getHealth()+", removed="+dog.isRemoved()+")");
        dog.invulnerableTime=0;
        h.assertTrue(!dog.hurt(level.damageSources().genericKill(),Float.MAX_VALUE),"He takes no further damage while he is down");
        dog.remove(Entity.RemovalReason.DISCARDED);
        h.assertTrue(dog.isAlive()&&!dog.isRemoved(),"Ordinary cleanup still cannot remove Sadaharu");
        // He picks himself up at his home, not where he fell.
        dog.home=p;dog.homeDimension=level.dimension();dog.recover();
        h.assertTrue(dog.downed==0&&dog.getHealth()==dog.getMaxHealth(),"Recovery restores him to full health");
        h.assertTrue(dog.level()==level&&dog.blockPosition().distSqr(p)<400,"Recovery puts him at his home");
        Sadaharu duplicate=HexSadaharu.DOG.get().create(level);duplicate.moveTo(p.getX()+4,p.getY(),p.getZ(),0,0);
        h.assertTrue(!level.addFreshEntity(duplicate),"A second UUID must be rejected");
        dog.following=false;
        CompoundTag saved=new CompoundTag();dog.saveWithoutId(saved);
        Sadaharu restored=HexSadaharu.DOG.get().create(level);restored.load(saved);
        h.assertTrue(owner.equals(restored.ownerId())&&restored.hunger()==432&&p.equals(restored.home),"Ownership, satiety and home round trip");
        h.assertTrue(!restored.following,"The follow instruction round trips");
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
        dog.following=true;
        h.assertTrue(dog.sendHome()&&!dog.following,"Sending him home cancels following, which is the opposite instruction");
        dog.homebound=0;dog.following=true;
        h.assertTrue(dog.getJumpPower()>.5F,"He jumps higher than a vanilla 0.42, or a fence beats him");
        h.assertTrue(!dog.afloat()&&!dog.swimming(),"He is not swimming while stood on dry ground");
        // A dream must put him back under rather than ending the night's sleep.
        dog.setAct(Act.SLEEP_TWITCH);dog.actEnd=dog.tickCount;
        h.assertTrue(dog.act().resting(),"A dream twitch still counts as rest");
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
