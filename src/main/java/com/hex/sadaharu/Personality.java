package com.hex.sadaharu;

import java.util.*;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;

/** Server-only arbitration: danger > retreat > rider > needs > relationship > curiosity > leisure. */
public final class Personality {
    private final Sadaharu dog;
    private List<Entity> nearby=List.of();
    private final LinkedHashMap<UUID,Profile> profiles=new LinkedHashMap<>();
    private LivingEntity threat;
    private Entity interest;
    private BlockPos blockInterest;
    private long retreatUntil,lastOwnerSeen;
    private int nextChoice,stuckTicks,pathFailures;
    private Vec3 lastPosition=Vec3.ZERO;
    private long soundUntil;
    private Vec3 soundPosition;
    private record Profile(boolean hostile,boolean huge,boolean tiny,boolean baby,boolean tame,boolean vehicle,boolean unusual) {}
    public Personality(Sadaharu dog) {this.dog=dog;}
    public void threat(LivingEntity e) {if(e==dog||e.getUUID().equals(dog.ownerId())||!e.isAlive())return;threat=e;dog.memories.remember("threat:"+e.getUUID(),dog.now(),2400);}
    public void investigate(BlockPos p) {if(p.distSqr(dog.blockPosition())<400&&!dog.memories.remembers("block:"+p.asLong(),dog.now()))blockInterest=p.immutable();}
    public void noise(Vec3 p,boolean danger) {if(dog.distanceToSqr(p)<625){soundPosition=p;soundUntil=dog.now()+140;if(danger){dog.setMood(Mood.SCARED);retreatUntil=dog.now()+100;}}}
    private boolean knows(String key) {return dog.memories.remembers(key,dog.now());}
    private void remember(String key,int ticks) {dog.memories.remember(key,dog.now(),ticks);}
    private Profile classify(Entity e) {
        return profiles.computeIfAbsent(e.getUUID(),id->new Profile(e instanceof Enemy,e.getBbHeight()>3.5||e.getBbWidth()>3||e instanceof LivingEntity l&&l.getMaxHealth()>150,e.getBbHeight()<.65,e instanceof AgeableMob a&&a.isBaby(),e instanceof TamableAnimal t&&t.isTame(),e instanceof AbstractMinecart||e instanceof Boat,!net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(e.getType()).getNamespace().equals("minecraft")));
    }
    public void tick() {
        long now=dog.now();ServerPlayer owner=dog.owner();
        if(dog.tickCount%20==0)observe(owner);
        if(threat!=null&&(!threat.isAlive()||threat.level()!=dog.level()||dog.distanceToSqr(threat)>1600))threat=null;
        if(now<retreatUntil) {retreat(owner);return;}
        if(threat!=null&&now>=dog.nextIntervention) {protect();return;}
        if(threat!=null&&!knows("sulk")&&!dog.isVehicle()) {
            // Still cross, still on the intervention cooldown: he glares and pouts instead.
            dog.setMood(Mood.SUSPICIOUS);dog.getLookControl().setLookAt(threat,30,30);
            dog.getNavigation().stop();dog.setAct(Act.POUT);dog.voice("growl",.4F);
            remember("sulk",600);nextChoice=dog.tickCount+Act.POUT.ticks;return;
        }
        if(dog.isVehicle())return;
        if(dog.homebound>0){goHome(owner);return;}
        if(dog.act()==Act.HEAD_BITE||dog.act()==Act.LICK_PLAYER){closeIn(dog.act()==Act.HEAD_BITE?3.5:4.5);return;}
        if(dog.act()==Act.SLEEP||dog.act()==Act.SLEEP_TWITCH) {
            dog.getNavigation().stop();
            if(!dog.level().isNight()||threat!=null||(owner!=null&&owner.distanceToSqr(dog)<100&&holdsFood(owner))) {dog.setAct(Act.WAKE);dog.setMood(Mood.CALM);}
            else if(dog.act()==Act.SLEEP_TWITCH&&dog.tickCount>=dog.actEnd)dog.setAct(Act.SLEEP);   // back under, still asleep
            else if(dog.act()==Act.SLEEP&&dog.getRandom().nextInt(400)==0)dog.setAct(Act.SLEEP_TWITCH);
            else if(dog.tickCount%360==0)dog.voice("sleep",.18F);
            return;
        }
        if(dog.act().resting()||dog.act()==Act.EAT||dog.act()==Act.DRINK||dog.act()==Act.POOP||dog.act()==Act.SCRATCH||dog.act()==Act.STRETCH||dog.act()==Act.STRETCH_FRONT) {
            dog.getNavigation().stop();
            if(owner!=null&&owner.level()==dog.level()&&dog.distanceToSqr(owner)<144&&holdsFood(owner)) {dog.setAct(Act.NONE);nextChoice=0;}
            else return;
        }
        if(dog.swimming()&&swim(owner))return;
        if(dog.tickCount%40==0)checkProgress();
        if(dog.tickCount<nextChoice)return;
        nextChoice=dog.tickCount+40+dog.getRandom().nextInt(60);
        if(food(owner))return;
        if(sleep(owner))return;
        if(relationship(owner))return;
        if(soundUntil>now&&soundPosition!=null) {dog.setMood(Mood.ALERT);look(soundPosition);dog.setAct(Act.BARK);dog.voice("bark",.85F);soundUntil=0;return;}
        if(weather())return;
        if(blockInterest!=null) {BlockPos p=blockInterest;blockInterest=null;remember("block:"+p.asLong(),6000);dog.setMood(Mood.CURIOUS);walk(Vec3.atBottomCenterOf(p),.65);dog.setAct(Act.SNIFF_GROUND);return;}
        if(curiosity())return;
        ambient(owner);
    }
    private void observe(ServerPlayer owner) {
        nearby=dog.level().getEntities(dog,dog.getBoundingBox().inflate(18,9,18),e->e.isAlive()&&!e.isSpectator());
        if(nearby.size()>64)nearby=new ArrayList<>(nearby.subList(0,64));
        for(Entity e:nearby) {
            Profile p=classify(e);
            if(e instanceof Mob m&&((owner!=null&&m.getTarget()==owner)||m.getTarget()==dog))threat(m);
            if(e instanceof Projectile projectile&&projectile.getDeltaMovement().lengthSqr()>.08&&dog.distanceToSqr(e)<36&&!knows("projectile")){dog.setAct(Act.ALERT);remember("projectile",200);}
            if(e instanceof Player player&&!player.isSpectator()) {
                String seen="seen:"+e.getUUID();
                if(knows(seen))remember("familiar:"+e.getUUID(),96000);
                remember(seen,24000);
            }
        }
        while(profiles.size()>96)profiles.remove(profiles.keySet().iterator().next());
        if(owner!=null&&owner.level()==dog.level()) {
            LivingEntity attacker=owner.getLastHurtByMob();
            if(attacker!=null&&owner.tickCount-owner.getLastHurtByMobTimestamp()<100)threat(attacker);
        }
        // Sample a few immediate blocks, never scan the registry or a huge volume.
        if(dog.tickCount%100==0)for(int i=0;i<4;i++) {
            BlockPos p=dog.blockPosition().offset(dog.getRandom().nextInt(11)-5,0,dog.getRandom().nextInt(11)-5);
            var state=dog.level().getBlockState(p);var id=net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(state.getBlock());
            boolean special=state.getBlock() instanceof BaseEntityBlock||state.is(BlockTags.BEDS)||state.getBlock() instanceof DoorBlock||!id.getNamespace().equals("minecraft");
            if(special&&!knows("block:"+p.asLong())){blockInterest=p;break;}
        }
    }
    private void protect() {
        dog.setMood(Mood.PROTECTIVE);
        if(dog.act().resting())dog.setAct(Act.ALERT);
        dog.getLookControl().setLookAt(threat,30,30);
        if(dog.tickCount%20==0) {walk(threat.position(),1.55);dog.voice(dog.getRandom().nextInt(3)==0?"growl":"deep_bark",.75F);}
        if(dog.distanceToSqr(threat)<Math.pow(1.7+threat.getBbWidth()*.5,2)&&dog.hasLineOfSight(threat)) {
            dog.getNavigation().stop();dog.setAct(Act.BITE);
            // Exactly one attempted hit, no melee goal, sweep, thorns or secondary damage.
            threat.hurt(dog.damageSources().mobAttack(dog),2F);
            dog.nextIntervention=dog.now()+900;retreatUntil=dog.now()+200;dog.setMood(Mood.RETREATING);
        }
    }
    private void retreat(ServerPlayer owner) {
        dog.setMood(Mood.RETREATING);
        if(dog.act().resting())dog.setAct(Act.NONE);
        if(dog.tickCount%20!=0)return;
        Vec3 from=threat!=null?threat.position():soundPosition!=null?soundPosition:dog.position().add(1,0,0);
        Vec3 away=dog.position().subtract(from).multiply(1,0,1).normalize();
        Vec3 dest=dog.position().add(away.scale(12));
        if(owner!=null&&owner.level()==dog.level()&&owner.distanceToSqr(from)>dog.distanceToSqr(from))dest=owner.position();
        walk(dest,1.8);
    }
    private boolean food(ServerPlayer owner) {
        Player offering=null;
        if(owner!=null&&owner.level()==dog.level()&&dog.distanceToSqr(owner)<256&&holdsFood(owner))offering=owner;
        else if(dog.hunger()<450)for(Entity e:nearby)if(e instanceof Player p&&holdsFood(p)){offering=p;break;}
        if(offering!=null) {
            dog.setMood(Mood.EXCITED);dog.getLookControl().setLookAt(offering,25,25);
            if(dog.distanceToSqr(offering)>9)walk(offering.position(),1.05);else {dog.getNavigation().stop();dog.setAct(dog.getRandom().nextBoolean()?Act.SIT_PANT:Act.WAG_EXCITED);}
            return true;
        }
        if(dog.hunger()>350)return false;
        for(Entity e:nearby)if(e instanceof ItemEntity item&&(item.getItem().is(HexSadaharu.KIBBLE.get())||item.getItem().isEdible())) {
            dog.setMood(Mood.HUNGRY);
            if(dog.distanceToSqr(e)<7) {item.getItem().shrink(1);dog.setHunger(dog.hunger()+180);dog.setAct(Act.EAT);dog.voice("eat",.5F);}
            else walk(e.position(),.9);
            return true;
        }
        if(!knows("beg")&&owner!=null&&owner.level()==dog.level()&&dog.distanceToSqr(owner)<400) {
            dog.setMood(Mood.HUNGRY);walk(owner.position(),.8);dog.setAct(dog.hunger()<100?Act.POUT:Act.WHINE);dog.voice("whine",.45F);remember("beg",1800);return true;
        }
        return false;
    }
    private boolean sleep(ServerPlayer owner) {
        if(!dog.level().isNight()&&!(owner!=null&&owner.isSleeping()))return false;
        if(dog.level().getDayTime()%24000<13200&&dog.getRandom().nextInt(4)!=0)return false;
        if(threat!=null)return false;
        dog.setMood(Mood.SLEEPY);
        boolean nearHome=dog.home!=null&&dog.homeDimension==dog.level().dimension()&&dog.home.distSqr(dog.blockPosition())<96*96;
        boolean adventuring=owner!=null&&owner.level()==dog.level()&&dog.home!=null&&dog.home.distSqr(owner.blockPosition())>128*128;
        if(dog.automaticHome&&nearHome&&!adventuring&&dog.home.distSqr(dog.blockPosition())>16) {dog.setMood(Mood.RETURNING_HOME);walk(Vec3.atBottomCenterOf(dog.home),.8);return true;}
        if(owner!=null&&owner.level()==dog.level()&&dog.distanceToSqr(owner)>100&&!nearHome){walk(owner.position(),.9);return true;}
        if(dog.onGround()&&!dog.isInWaterRainOrBubble()) {
            if(!knows("yawn")){dog.setAct(Act.YAWN);dog.voice("yawn",.3F);remember("yawn",1200);return true;}
            dog.getNavigation().stop();dog.setAct(Act.SLEEP);dog.setMood(Mood.SLEEPING);return true;
        }
        return false;
    }
    private boolean relationship(ServerPlayer owner) {
        if(owner==null||owner.level()!=dog.level())return false;
        double distance=dog.distanceToSqr(owner);
        if(distance<144) {
            boolean reunion=dog.now()-lastOwnerSeen>1200&&lastOwnerSeen!=0;
            lastOwnerSeen=dog.now();
            if(reunion&&!knows("reunion")){dog.setMood(Mood.EXCITED);walk(owner.position(),1.3);dog.setAct(dog.getRandom().nextBoolean()?Act.WAG_EXCITED:Act.HOP);dog.voice("excited",.7F);remember("reunion",2400);return true;}
            if(owner.getHealth()<owner.getMaxHealth()*.5&&!knows("comfort")){dog.setMood(Mood.ALERT);walk(owner.position(),.8);dog.setAct(Act.TILT_LEFT);remember("comfort",1200);return true;}
        }
        if(dog.following&&distance>64&&distance<16384) {
            dog.setMood(Mood.FOLLOWING_INTEREST);walk(owner.position(),distance>576?1.45:1.05);
            if(dog.onGround()&&!dog.getNavigation().isDone()&&dog.horizontalCollision)hurdle();
            return true;
        }
        if(!dog.following&&distance>400&&distance<4096&&!knows("independent")&&!knows("stay_home")) {dog.setMood(Mood.FOLLOWING_INTEREST);walk(owner.position(),1.2);return true;}
        if(distance<144&&!knows("head_bite")&&dog.getRandom().nextInt(55)==0&&!owner.isSleeping()&&!owner.isPassenger()) {
            interest=owner;dog.gagTarget(owner.getId());dog.setAct(Act.HEAD_BITE);remember("head_bite",6000);return true;
        }
        if(distance<100&&!knows("lick")&&dog.getRandom().nextInt(18)==0&&!owner.isPassenger()) {
            interest=owner;dog.gagTarget(owner.getId());dog.setAct(Act.LICK_PLAYER);remember("lick",1800);return true;
        }
        return false;
    }
    private void goHome(ServerPlayer owner) {
        dog.homebound--;
        dog.setMood(Mood.RETURNING_HOME);
        if(dog.act().resting()||dog.act()==Act.HEAD_BITE||dog.act()==Act.LICK_PLAYER){dog.setAct(Act.NONE);dog.gagTarget(-1);}
        boolean sameDimension=dog.home!=null&&dog.homeDimension==dog.level().dimension();
        if(dog.tickCount%10==0) {
            Vec3 target;
            if(sameDimension)target=Vec3.atBottomCenterOf(dog.home);
            else {
                Vec3 away=owner!=null&&owner.level()==dog.level()?dog.position().subtract(owner.position()).multiply(1,0,1):Vec3.ZERO;
                target=dog.position().add((away.lengthSqr()<.01?new Vec3(1,0,0):away.normalize()).scale(14));
            }
            walk(target,1.05);
        }
        if(sameDimension&&dog.home.distSqr(dog.blockPosition())<64){settleHome();return;}
        // He leaves on his own feet first; the jump home only happens once he is out of sight.
        boolean unseen=owner==null||owner.level()!=dog.level()||dog.distanceToSqr(owner)>1024||!owner.hasLineOfSight(dog);
        if(dog.homebound<=0||(unseen&&dog.homebound<540))settleHome();
    }
    private void settleHome() {
        dog.homebound=0;
        var server=dog.getServer();
        net.minecraft.server.level.ServerLevel destination=dog.home==null||server==null?null:server.getLevel(dog.homeDimension);
        if(destination!=null) {
            Vec3 landing=SafeTravel.landing(destination,dog.home,dog);
            if(landing!=null)SafeTravel.teleport(dog,destination,landing);
        }
        dog.setMood(Mood.CALM);dog.setAct(Act.SNIFF_GROUND);
        remember("stay_home",12000);nextChoice=dog.tickCount+60;
        ServerPlayer owner=dog.owner();
        if(owner!=null)owner.displayClientMessage(net.minecraft.network.chat.Component.literal("Sadaharu has settled in at home."),true);
    }
    private void closeIn(double reach) {
        Entity target=dog.level().getEntity(dog.gagTarget());
        if(!(target instanceof LivingEntity l)||!l.isAlive()||l.isPassenger()||dog.distanceToSqr(l)>64){dog.setAct(Act.NONE);dog.gagTarget(-1);return;}
        dog.getLookControl().setLookAt(l,35,35);
        Vec3 delta=l.position().subtract(dog.position());
        dog.setYRot((float)(Math.atan2(-delta.x,delta.z)*180/Math.PI));dog.yBodyRot=dog.getYRot();dog.yHeadRot=dog.getYRot();
        if(dog.distanceToSqr(l)>reach){if(dog.tickCount%10==0)walk(l.position(),.65);return;}
        dog.getNavigation().stop(); // The client jaw and tongue are aimed at this entity's eye height. No damage or forced player control.
    }
    private boolean weather() {
        if(dog.level().isThundering()&&!knows("thunder")){dog.setMood(Mood.SCARED);dog.setAct(Act.ALERT);dog.voice("whine",.35F);remember("thunder",1600);}
        boolean rain=dog.level().isRainingAt(dog.blockPosition());
        var feet=dog.level().getBlockState(dog.blockPosition());
        if((feet.is(Blocks.SNOW)||dog.level().getBlockState(dog.blockPosition().below()).is(Blocks.SNOW_BLOCK))&&!knows("snow")) {
            dog.setMood(Mood.CURIOUS);dog.setAct(Act.PAW);remember("snow",3000);return true;
        }
        if(dog.isInWater()&&dog.onGround()&&!knows("splash")) {
            dog.setMood(Mood.PLAYFUL);dog.setAct(Act.PAW);remember("splash",2400);
            ((net.minecraft.server.level.ServerLevel)dog.level()).sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH,dog.getX(),dog.getY()+.3,dog.getZ(),12,.8,.1,.8,.1);return true;
        }
        if((rain||dog.level().isDay()&&dog.getRandom().nextInt(5)==0)&&dog.level().canSeeSky(dog.blockPosition())) {
            for(int i=0;i<8;i++){
                BlockPos p=dog.blockPosition().offset(dog.getRandom().nextInt(25)-12,0,dog.getRandom().nextInt(25)-12);
                if(!dog.level().canSeeSky(p)&&dog.getNavigation().isStableDestination(p)){walk(Vec3.atBottomCenterOf(p),.8);dog.setMood(Mood.CALM);return true;}
            }
        }
        if(dog.isInWater()&&!knows("drink")){dog.setAct(Act.DRINK);remember("drink",1800);return true;}
        return false;
    }
    private boolean curiosity() {
        if(dog.getRandom().nextInt(3)!=0)return false;
        for(Entity e:nearby) {
            if(e==dog.getControllingPassenger()||e.getUUID().equals(dog.ownerId())||knows("inspect:"+e.getUUID()))continue;
            Profile p=classify(e);
            if(!dog.hasLineOfSight(e))continue;
            remember("inspect:"+e.getUUID(),2400+dog.getRandom().nextInt(3600));interest=e;
            dog.getLookControl().setLookAt(e,20,20);
            if(p.hostile){dog.setMood(Mood.SUSPICIOUS);dog.setAct(dog.getRandom().nextBoolean()?Act.GROWL:Act.BARK);dog.voice("deep_bark",.6F);return true;}
            if(p.huge||p.unusual){dog.setMood(Mood.SUSPICIOUS);dog.setAct(Act.TILT_LEFT);if(dog.distanceToSqr(e)<36){soundPosition=e.position();retreatUntil=dog.now()+80;}else walk(e.position(),.6);return true;}
            if(p.vehicle){dog.setMood(Mood.CURIOUS);dog.setAct(Act.LOOK);return true;}
            if(p.baby||p.tiny||p.tame){dog.setMood(Mood.PLAYFUL);dog.setAct(Act.TILT_RIGHT);if(dog.distanceToSqr(e)>9)walk(e.position(),.6);return true;}
            if(e instanceof Cat&&!knows("chase_cat")){dog.setMood(Mood.PLAYFUL);walk(e.position(),1.2);remember("chase_cat",3600);nextChoice=dog.tickCount+80;return true;}
            if(e instanceof LivingEntity){dog.setMood(Mood.CURIOUS);walk(e.position(),.65);dog.setAct(Act.SNIFF_AIR);return true;}
        }
        return false;
    }
    private void ambient(ServerPlayer owner) {
        // Once sent home he keeps to it for a while rather than walking straight back.
        if(knows("stay_home")&&dog.home!=null&&dog.homeDimension==dog.level().dimension()&&dog.home.distSqr(dog.blockPosition())>576) {
            dog.setMood(Mood.RETURNING_HOME);walk(Vec3.atBottomCenterOf(dog.home),.85);return;
        }
        dog.setMood(dog.hunger()<250?Mood.HUNGRY:Mood.CALM);
        int roll=dog.getRandom().nextInt(100);
        if(dog.hunger()<250&&roll<14&&!knows("pout")){dog.getNavigation().stop();dog.setAct(Act.POUT);remember("pout",900);nextChoice=dog.tickCount+Act.POUT.ticks+40;return;}
        if(roll==93&&!knows("lick")) {
            for(Entity e:nearby)if(e instanceof Player&&knows("familiar:"+e.getUUID())&&!e.isPassenger()&&dog.distanceToSqr(e)<64) {
                interest=e;dog.gagTarget(e.getId());dog.setAct(Act.LICK_PLAYER);remember("lick",1800);return;
            }
        }
        if(roll==94&&!knows("head_bite")) {
            for(Entity e:nearby)if((e instanceof Player&&knows("familiar:"+e.getUUID())||e instanceof AbstractVillager)&&e instanceof LivingEntity l&&!l.isBaby()&&!e.isPassenger()&&dog.distanceToSqr(e)<64) {
                interest=e;dog.gagTarget(e.getId());dog.setAct(Act.HEAD_BITE);remember("head_bite",6000);return;
            }
        }
        if(roll>=80&&roll<88) {dog.getNavigation().stop();dog.setAct(Act.BARK);dog.voice("bark",.7F);nextChoice=dog.tickCount+Act.BARK.ticks+25;return;}
        if(roll<35){dog.getNavigation().stop();dog.setAct(Act.NONE);nextChoice=dog.tickCount+160+dog.getRandom().nextInt(200);return;}
        if(roll<58){Vec3 pos=DefaultRandomPos.getPos(dog,12,3);if(pos!=null){walk(pos,.7);remember("independent",160);}return;}
        if(roll>96&&!knows("zoomies")){Vec3 p=DefaultRandomPos.getPos(dog,18,3);if(p!=null){walk(p,2.1);dog.setMood(Mood.PLAYFUL);remember("zoomies",6000);nextChoice=dog.tickCount+100;}return;}
        Act[] idle={Act.LOOK,Act.TILT_LEFT,Act.TILT_RIGHT,Act.SNIFF_GROUND,Act.SNIFF_AIR,Act.SCRATCH,Act.SHAKE,Act.STRETCH_FRONT,Act.STRETCH,Act.SIT,Act.SIT_PANT,Act.LIE,Act.CHIN,Act.SIDE,Act.YAWN,Act.PANT,Act.LICK_NOSE,Act.LICK_PAW,Act.PAW,Act.EAR_LEFT,Act.EAR_RIGHT,Act.EAR_BOTH,Act.WAG_SLOW,Act.EAR_FLICK,Act.HEAD_SHAKE,Act.SNEEZE,Act.LOOK_UP,Act.PLAY_BOW,Act.TAIL_CHASE,Act.BARK};
        Act a=idle[dog.getRandom().nextInt(idle.length)];dog.setAct(a);nextChoice=dog.tickCount+a.ticks+60;
        if(a==Act.YAWN)dog.voice("yawn",.25F);
        if(a==Act.BARK)dog.voice("bark",.7F);
        if(a==Act.PLAY_BOW)dog.voice("excited",.6F);
        if(owner!=null&&dog.distanceToSqr(owner)<144)dog.getLookControl().setLookAt(owner,15,15);
    }
    private void look(Vec3 p){dog.getLookControl().setLookAt(p.x,p.y,p.z,20,20);}
    private static boolean holdsFood(Player p) {return p.getMainHandItem().is(HexSadaharu.KIBBLE.get())||p.getOffhandItem().is(HexSadaharu.KIBBLE.get());}
    private boolean walk(Vec3 p,double speed) {
        boolean ok=dog.getNavigation().moveTo(p.x,p.y,p.z,speed);
        if(!ok){pathFailures++;if(pathFailures>2){Vec3 alternate=DefaultRandomPos.getPos(dog,8,2);if(alternate!=null)dog.getNavigation().moveTo(alternate.x,alternate.y,alternate.z,.8);nextChoice=dog.tickCount+80;pathFailures=0;}}
        else pathFailures=0;
        return ok;
    }
    /** A real leap over a wall or a gap, only with room for the whole body above him. */
    private boolean hurdle() {
        if(!dog.onGround()||dog.isInWater()||dog.isVehicle())return false;
        if(!dog.level().noCollision(dog,dog.getBoundingBox().move(0,1.35,0)))return false;
        var path=dog.getNavigation().getPath();
        Vec3 aim=path==null||path.isDone()?null:Vec3.atBottomCenterOf(path.getNextNodePos()).subtract(dog.position()).multiply(1,0,1);
        if(aim==null||aim.lengthSqr()<.04)aim=Vec3.directionFromRotation(0,dog.getYRot());
        aim=aim.normalize().scale(.38);
        dog.setAct(Act.LEAP);
        dog.setDeltaMovement(aim.x,.62,aim.z);dog.hasImpulse=true;dog.hurtMarked=true;
        return true;
    }
    /** Out of his depth: make for the owner if they are reachable, otherwise the nearest shore. */
    private boolean swim(ServerPlayer owner) {
        if(dog.act()!=Act.NONE)dog.setAct(Act.NONE);
        if(dog.tickCount%10!=0)return true;
        if(owner!=null&&owner.level()==dog.level()&&dog.distanceToSqr(owner)<1024&&!owner.isSwimming()) {
            dog.setMood(Mood.FOLLOWING_INTEREST);walk(owner.position(),1.1);return true;
        }
        dog.setMood(Mood.ALERT);
        BlockPos best=null;double nearest=Double.MAX_VALUE;
        for(int i=0;i<12;i++) {
            BlockPos p=dog.blockPosition().offset(dog.getRandom().nextInt(25)-12,0,dog.getRandom().nextInt(25)-12);
            if(dog.level().getFluidState(p).isEmpty()&&dog.getNavigation().isStableDestination(p)) {
                double d=p.distSqr(dog.blockPosition());
                if(d<nearest){nearest=d;best=p;}
            }
        }
        if(best!=null)walk(Vec3.atBottomCenterOf(best),1.2);
        return true;
    }
    private void checkProgress() {
        if(!dog.getNavigation().isDone()&&dog.position().distanceToSqr(lastPosition)<.16) {
            stuckTicks+=40;
            if(stuckTicks>=80){dog.getNavigation().stop();nextChoice=0;stuckTicks=0;Vec3 p=DefaultRandomPos.getPos(dog,8,2);if(p!=null)walk(p,.9);}
            else if(dog.horizontalCollision)hurdle();
        }else stuckTicks=0;
        lastPosition=dog.position();
    }
}
