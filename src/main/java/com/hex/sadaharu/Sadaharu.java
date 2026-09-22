package com.hex.sadaharu;

import java.util.*;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.resources.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.navigation.*;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;

public class Sadaharu extends PathfinderMob implements PlayerRideableJumping {
    private static final EntityDataAccessor<Optional<UUID>> OWNER=SynchedEntityData.defineId(Sadaharu.class,EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> MOOD=SynchedEntityData.defineId(Sadaharu.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACT=SynchedEntityData.defineId(Sadaharu.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> START=SynchedEntityData.defineId(Sadaharu.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SATIETY=SynchedEntityData.defineId(Sadaharu.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> GAG_TARGET=SynchedEntityData.defineId(Sadaharu.class,EntityDataSerializers.INT);
    public final Personality personality=new Personality(this);
    public final Memory memories=new Memory();
    @Nullable public BlockPos home;
    public ResourceKey<Level> homeDimension=Level.OVERWORLD;
    public boolean automaticHome=true;
    private int bones, bonesRequired=2, voiceCooldown, hungerClock, poopClock=24000, prepareTicks;
    private float jumpCharge, rideSpeed;
    private boolean wasGround=true, wasWet, intentionalRemoval;
    private BlockPos safePosition;
    public int actEnd;
    public long nextIntervention;
    public float clientSit,clientLie,clientSleep,clientJaw;

    public Sadaharu(EntityType<? extends PathfinderMob> type,Level level) {
        super(type,level);setPersistenceRequired();setMaxUpStep(1.1F);
        bonesRequired=2+random.nextInt(5);
        setPathfindingMalus(BlockPathTypes.LAVA,-1);setPathfindingMalus(BlockPathTypes.DAMAGE_FIRE,-1);
        setPathfindingMalus(BlockPathTypes.DANGER_FIRE,-1);setPathfindingMalus(BlockPathTypes.WATER,6);
        setPathfindingMalus(BlockPathTypes.DOOR_WOOD_CLOSED,-1);setPathfindingMalus(BlockPathTypes.FENCE,-1);
        setPathfindingMalus(BlockPathTypes.DANGER_OTHER,8);
    }
    public static AttributeSupplier.Builder attributes() { return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,40).add(Attributes.MOVEMENT_SPEED,.29).add(Attributes.FOLLOW_RANGE,28).add(Attributes.KNOCKBACK_RESISTANCE,1).add(Attributes.ATTACK_DAMAGE,2); }
    @Override protected void registerGoals() { goalSelector.addGoal(0,new FloatGoal(this)); }
    @Override protected PathNavigation createNavigation(Level l) { return new LargeNavigation(this,l); }
    @Override protected void defineSynchedData() {
        super.defineSynchedData();entityData.define(OWNER,Optional.empty());entityData.define(MOOD,0);entityData.define(ACT,0);entityData.define(START,0);entityData.define(SATIETY,1000);entityData.define(GAG_TARGET,-1);
    }
    @Nullable public UUID ownerId() { return entityData.get(OWNER).orElse(null); }
    public boolean ownedBy(Player p) {return p!=null&&p.getUUID().equals(ownerId());}
    @Nullable public ServerPlayer owner() {return ownerId()!=null&&getServer()!=null?getServer().getPlayerList().getPlayer(ownerId()):null;}
    public void setOwner(UUID id) { if(ownerId()==null)entityData.set(OWNER,Optional.of(id)); }
    public Mood mood() { return Mood.values()[Math.floorMod(entityData.get(MOOD),Mood.values().length)]; }
    public void setMood(Mood m) {entityData.set(MOOD,m.ordinal());}
    public Act act() { return Act.values()[Math.floorMod(entityData.get(ACT),Act.values().length)]; }
    public void setAct(Act a) {entityData.set(ACT,a.ordinal());entityData.set(START,(int)level().getGameTime());actEnd=tickCount+a.ticks;}
    public float actAge(float partial) { return (int)level().getGameTime()-entityData.get(START)+partial; }
    public int hunger() { return entityData.get(SATIETY); }
    public void setHunger(int v) {entityData.set(SATIETY,net.minecraft.util.Mth.clamp(v,0,1000));}
    public int gagTarget() {return entityData.get(GAG_TARGET);}
    public void gagTarget(int id) {entityData.set(GAG_TARGET,id);}
    public long now() { return getServer()!=null?getServer().overworld().getGameTime():level().getGameTime(); }
    public boolean canAmbient() {return !isVehicle()&&onGround()&&(act()==Act.NONE||tickCount>=actEnd);}
    public void voice(String sound,float volume) {
        if(level().isClientSide||voiceCooldown>0)return;
        var ev=ForgeRegistries.SOUND_EVENTS.getValue(HexSadaharu.id(sound));
        if(ev!=null)level().playSound(null,blockPosition(),ev,SoundSource.NEUTRAL,volume,.85F+random.nextFloat()*.25F);
        voiceCooldown=80+random.nextInt(100);
    }
    @Override public void tick() {
        super.tick();fallDistance=0;
        if(level().isClientSide)return;
        if(voiceCooldown>0)voiceCooldown--;
        if(getHealth()!=getMaxHealth()||!Float.isFinite(getHealth()))super.setHealth(getMaxHealth());
        clearFire();setAirSupply(getMaxAirSupply());setTicksFrozen(0);
        if(tickCount%100==0) {CompanionData.get(getServer()).capture(this);memories.expire(now());}
        if(++hungerClock>=120) {hungerClock=0;setHunger(hunger()-1);} // 100 minutes from full to empty.
        if(--poopClock<=0&&canAmbient()&&hunger()>250) {setAct(Act.POOP);poopClock=24000+random.nextInt(24000);}
        if(act()==Act.POOP&&tickCount==actEnd-20) {
            var item=spawnAtLocation(HexSadaharu.POOP.get());if(item!=null){item.lifespan=1200;item.setDeltaMovement(Vec3.ZERO);}
        }
        if(act()!=Act.NONE&&tickCount>=actEnd&&act()!=Act.SLEEP) {setAct(Act.NONE);gagTarget(-1);}
        if(!wasGround&&onGround()) {setAct(Act.LAND);voice("land",.6F);}
        wasGround=onGround();
        if(wasWet&&!isInWaterRainOrBubble()&&canAmbient())setAct(Act.SHAKE);
        wasWet=isInWaterRainOrBubble();
        if(tickCount%20==0) {
            if(onGround()&&level().noCollision(this)&&!isInLava())safePosition=blockPosition();
            if(getY()<level().getMinBuildHeight()-8||!Double.isFinite(getX()+getY()+getZ())||isInLava()||isInWall())rescue();
        }
        if(prepareTicks>0&&--prepareTicks==0)launch();
        if(!isVehicle())personality.tick();
        for(Entity rider:getPassengers()) {rider.fallDistance=0;rider.getPersistentData().putLong("SadaharuLandingGrace",now()+60);}
    }
    private void rescue() {
        ServerLevel l=(ServerLevel)level(); BlockPos near=safePosition;
        ServerPlayer p=owner();if(p!=null&&p.level()==l)near=p.blockPosition();
        if(near==null)near=l.getSharedSpawnPos();
        Vec3 v=SafeTravel.landing(l,near,this);
        if(v!=null)SafeTravel.teleport(this,l,v);
        else {teleportTo(near.getX()+.5,Math.max(l.getMinBuildHeight()+10,near.getY()+5),near.getZ()+.5);setDeltaMovement(Vec3.ZERO);}
    }
    @Override public InteractionResult mobInteract(Player p,InteractionHand hand) {
        ItemStack held=p.getItemInHand(hand);
        if(held.is(Items.BONE)&&ownerId()==null) {
            if(!level().isClientSide) {
                if(!p.getAbilities().instabuild)held.shrink(1);
                bones++;setAct(Act.EAT);
                boolean done=bones>=bonesRequired;
                ((ServerLevel)level()).sendParticles(done?ParticleTypes.HEART:ParticleTypes.SMOKE,getX(),getY()+2.4,getZ(),done?9:3,.6,.3,.6,.03);
                if(done){setOwner(p.getUUID());home=blockPosition();homeDimension=level().dimension();setMood(Mood.EXCITED);CompanionData.get(getServer()).capture(this);voice("excited",.7F);}
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if(held.is(HexSadaharu.KIBBLE.get())) {
            if(!level().isClientSide) {
                if(!p.getAbilities().instabuild)held.shrink(1);setHunger(hunger()+350);setAct(Act.EAT);setMood(Mood.EXCITED);
                memories.remember("fed",now(),12000);memories.remember("familiar:"+p.getUUID(),now(),168000);voice("eat",.6F);
                ((ServerLevel)level()).sendParticles(ParticleTypes.HEART,getX(),getY()+2,getZ(),3,.5,.3,.5,.02);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if(ownedBy(p)) {
            if(!level().isClientSide) {
                if(p.isSecondaryUseActive())mount(p);
                else NetworkHooks.openScreen((ServerPlayer)p,new SimpleMenuProvider((id,inv,player)->new SadaharuMenu(id,inv,this),Component.literal("Sadaharu")),b->{b.writeInt(getId());b.writeUtf(homeLabel());});
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if(!level().isClientSide&&canAmbient()){setAct(Act.SNIFF_AIR);memories.remember("familiar:"+p.getUUID(),now(),48000);}
        return InteractionResult.sidedSuccess(level().isClientSide);
    }
    public String homeLabel() {return home==null?"No home set":home.getX()+", "+home.getY()+", "+home.getZ()+" | "+homeDimension.location();}
    public boolean mount(Player p) {if(!ownedBy(p)||isVehicle())return false;getNavigation().stop();setAct(Act.MOUNT);setMood(Mood.EXCITED);return p.startRiding(this);}
    @Override public LivingEntity getControllingPassenger() {return getFirstPassenger() instanceof Player p&&ownedBy(p)?p:null;}
    @Override protected boolean canAddPassenger(Entity e) {return e instanceof Player p&&ownedBy(p)&&getPassengers().isEmpty();}
    @Override public double getPassengersRidingOffset() {return 1.82+Math.sin(tickCount*.45)*Math.min(.055,rideSpeed*.04);}
    @Override protected void positionRider(Entity rider,Entity.MoveFunction move) {
        if(!hasPassenger(rider))return;
        double yaw=Math.toRadians(getYRot()),forward=.24;
        move.accept(rider,getX()-Math.sin(yaw)*forward,getY()+getPassengersRidingOffset()+rider.getMyRidingOffset(),getZ()+Math.cos(yaw)*forward);
    }
    @Override public Vec3 getDismountLocationForPassenger(LivingEntity rider) {
        if(level() instanceof ServerLevel l) {Vec3 p=SafeTravel.landing(l,blockPosition(),rider);if(p!=null)return p;}
        return super.getDismountLocationForPassenger(rider);
    }
    @Override protected Vec3 getRiddenInput(Player rider,Vec3 input) { return new Vec3(rider.xxa*.45,0,rider.zza<=0?rider.zza*.35:rider.zza); }
    @Override protected void tickRidden(Player rider,Vec3 input) {
        super.tickRidden(rider,input);
        setYRot(net.minecraft.util.Mth.rotLerp(.25F,getYRot(),rider.getYRot()));yRotO=getYRot();yBodyRot=getYRot();yHeadRot=getYRot();setXRot(rider.getXRot()*.2F);
        float target=Math.abs(rider.zza)>.01F?(rider.isSprinting()?1.28F:.86F):0;
        rideSpeed=net.minecraft.util.Mth.approach(rideSpeed,target,target>rideSpeed?.045F:.075F);
        rider.fallDistance=0;
    }
    @Override protected float getRiddenSpeed(Player rider) {return rideSpeed;}
    @Override public boolean canJump() {return isVehicle()&&onGround();}
    @Override public void onPlayerJump(int charge) {jumpCharge=Math.max(.35F,Math.min(1,charge/90F));}
    @Override public void handleStartJump(int charge) {if(canJump()){onPlayerJump(charge);prepareTicks=8;setAct(Act.PREPARE_LEAP);}}
    @Override public void handleStopJump() {}
    private void launch() {
        if(!isVehicle()||!onGround())return;
        double yaw=Math.toRadians(getYRot()),forward=(.5+rideSpeed*1.9)*jumpCharge;
        // With vanilla gravity/drag, 1.45 reaches approximately ten blocks.
        setDeltaMovement(-Math.sin(yaw)*forward,.8+.65*jumpCharge,Math.cos(yaw)*forward);hasImpulse=true;hurtMarked=true;setAct(Act.LEAP);
    }
    @Override public boolean hurt(DamageSource source,float amount) {
        if(!level().isClientSide&&source.getEntity() instanceof LivingEntity l)personality.threat(l);
        return false;
    }
    @Override public boolean isInvulnerableTo(DamageSource d) {return true;}
    @Override public void setHealth(float h) {super.setHealth(Float.isFinite(h)&&h>0?Math.max(h,getMaxHealth()):getMaxHealth());}
    @Override public void die(DamageSource d) {super.setHealth(getMaxHealth());deathTime=0;}
    @Override public void kill() {if(!level().isClientSide)rescue();}
    @Override public boolean causeFallDamage(float distance,float mult,DamageSource d) {return false;}
    @Override public boolean canBeAffected(MobEffectInstance effect) {return false;}
    @Override public boolean removeWhenFarAway(double d) {return false;}
    @Override public void checkDespawn() {}
    @Override public void setRemoved(RemovalReason r) {
        if(!level().isClientSide&&!intentionalRemoval&&(r==RemovalReason.KILLED||r==RemovalReason.DISCARDED))return;
        if(!level().isClientSide&&getServer()!=null&&!intentionalRemoval)CompanionData.get(getServer()).capture(this);
        super.setRemoved(r);
    }
    public void rejectDuplicate() {intentionalRemoval=true;super.setRemoved(RemovalReason.DISCARDED);}
    @Override public void addAdditionalSaveData(CompoundTag n) {super.addAdditionalSaveData(n);writeCompanion(n);}
    public void writeCompanion(CompoundTag n) {
        if(ownerId()!=null)n.putUUID("Owner",ownerId());
        if(home!=null)n.putLong("Home",home.asLong());n.putString("HomeDimension",homeDimension.location().toString());n.putBoolean("AutomaticHome",automaticHome);
        n.putInt("Hunger",hunger());n.putInt("HungerClock",hungerClock);n.putInt("Bones",bones);n.putInt("BonesRequired",bonesRequired);n.putInt("PoopClock",poopClock);
        n.putString("Mood",mood().name());n.putString("Act",act().name());n.putInt("RemainingAct",Math.max(0,actEnd-tickCount));n.put("BehaviorMemory",memories.save());n.putLong("NextIntervention",nextIntervention);
        if(safePosition!=null)n.putLong("SafePosition",safePosition.asLong());
    }
    @Override public void readAdditionalSaveData(CompoundTag n) {
        super.readAdditionalSaveData(n);
        if(n.hasUUID("Owner"))entityData.set(OWNER,Optional.of(n.getUUID("Owner")));
        if(n.contains("Home"))home=BlockPos.of(n.getLong("Home"));
        if(n.contains("HomeDimension"))homeDimension=ResourceKey.create(Registries.DIMENSION,new ResourceLocation(n.getString("HomeDimension")));
        automaticHome=!n.contains("AutomaticHome")||n.getBoolean("AutomaticHome");setHunger(n.contains("Hunger")?n.getInt("Hunger"):1000);
        hungerClock=n.getInt("HungerClock");bones=n.getInt("Bones");bonesRequired=Math.max(2,Math.min(6,n.getInt("BonesRequired")));poopClock=n.contains("PoopClock")?n.getInt("PoopClock"):24000;
        try {setMood(Mood.valueOf(n.getString("Mood")));setAct(Act.valueOf(n.getString("Act")));}catch(IllegalArgumentException ignored){}
        actEnd=tickCount+Math.min(24000,n.getInt("RemainingAct"));memories.load(n.getCompound("BehaviorMemory"));nextIntervention=n.getLong("NextIntervention");
        if(n.contains("SafePosition"))safePosition=BlockPos.of(n.getLong("SafePosition"));setPersistenceRequired();
    }
}
