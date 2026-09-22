package com.hex.sadaharu;

import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.*;
import net.minecraftforge.event.entity.*;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;

public final class WorldEvents {
    private static final TicketType<UUID> CALL_TICKET=TicketType.create("hexsadaharu_call",Comparator.comparing(UUID::toString),160);
    private record Call(UUID player,UUID dog,ServerLevel level,ChunkPos chunk,int ticks) {Call next(){return new Call(player,dog,level,chunk,ticks+1);}}
    private static final Map<UUID,Call> calls=new HashMap<>();
    private static final Map<UUID,Long> rate=new HashMap<>();
    @SubscribeEvent public void commands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("hs").then(Commands.literal("spawn").requires(s->s.hasPermission(2)).executes(c->{
            var source=c.getSource();var server=source.getServer();var data=CompanionData.get(server);
            if(data.dog!=null){source.sendFailure(Component.literal("Sadaharu already exists in this world ("+data.dimension.location()+"). His owner can use Call Dog."));return 0;}
            var dog=HexSadaharu.DOG.get().create(source.getLevel());if(dog==null)return 0;
            Vec3 pos=SafeTravel.landing(source.getLevel(),BlockPos.containing(source.getPosition()),dog);
            if(pos==null){source.sendFailure(Component.literal("Sadaharu needs a clear, dry area at least three blocks wide and high nearby."));return 0;}
            dog.moveTo(pos.x,pos.y,pos.z,source.getRotation().y,0);
            // Reserve before addFreshEntity; the join gate sees this same identity.
            data.capture(dog);
            if(!source.getLevel().addFreshEntity(dog)){data.release();source.sendFailure(Component.literal("Could not add Sadaharu."));return 0;}
            source.sendSuccess(()->Component.literal("Sadaharu has arrived. Offer him bones to earn his trust."),true);return 1;
        })));
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void join(EntityJoinLevelEvent e) {
        if(e.getLevel().isClientSide()||!(e.getEntity() instanceof Sadaharu dog))return;
        CompanionData data=CompanionData.get(e.getLevel().getServer());
        Sadaharu existing=data.loaded(e.getLevel().getServer());
        if(data.dog!=null&&!data.dog.equals(dog.getUUID())||existing!=null&&existing!=dog&&!SafeTravel.transferring(dog.getUUID(),(ServerLevel)e.getLevel())) {e.setCanceled(true);dog.rejectDuplicate();return;}
        data.capture(dog);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void attack(LivingAttackEvent e) {if(e.getEntity() instanceof Sadaharu dog&&e.getSource().getEntity() instanceof net.minecraft.world.entity.LivingEntity l)dog.personality.threat(l);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public void damage(LivingDamageEvent e) {if(!(e.getEntity() instanceof Sadaharu)&&e.getSource().getEntity() instanceof Sadaharu)e.setAmount(Math.min(2,Math.max(0,e.getAmount())));}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void death(LivingDeathEvent e) {if(e.getEntity() instanceof Sadaharu dog){e.setCanceled(true);dog.collapse();}}
    @SubscribeEvent public void hurt(LivingHurtEvent e) {
        if(e.getEntity() instanceof ServerPlayer player&&e.getSource().getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker) {
            CompanionData data=CompanionData.get(player.server);Sadaharu dog=data.loaded(player.server);
            if(dog!=null&&dog.ownedBy(player)&&dog.level()==player.level()&&dog.distanceToSqr(player)<1600)dog.personality.threat(attacker);
        }
    }
    @SubscribeEvent public void fall(LivingFallEvent e) {
        if(e.getEntity() instanceof Sadaharu){e.setCanceled(true);return;}
        if(e.getEntity() instanceof ServerPlayer p&&p.getPersistentData().getLong("SadaharuLandingGrace")>=p.server.overworld().getGameTime())e.setCanceled(true);
    }
    @SubscribeEvent public void interact(PlayerInteractEvent.RightClickBlock e) {
        if(e.getEntity() instanceof ServerPlayer p){Sadaharu dog=CompanionData.get(p.server).loaded(p.server);if(dog!=null&&dog.ownedBy(p)&&dog.level()==p.level())dog.personality.investigate(e.getPos());}
    }
    @SubscribeEvent public void explosion(ExplosionEvent.Detonate e) {if(e.getLevel() instanceof ServerLevel l){Sadaharu dog=CompanionData.get(l.getServer()).loaded(l.getServer());if(dog!=null&&dog.level()==l)dog.personality.noise(e.getExplosion().getPosition(),true);}}
    public static void call(ServerPlayer player) {
        long now=player.server.overworld().getGameTime();
        if(rate.getOrDefault(player.getUUID(),-100L)+20>now)return;rate.put(player.getUUID(),now);
        CompanionData data=CompanionData.get(player.server);
        if(data.dog==null||!player.getUUID().equals(data.owner)){player.displayClientMessage(Component.literal("Only Sadaharu’s owner can call him."),true);return;}
        Sadaharu dog=data.loaded(player.server);
        if(dog!=null){arrive(dog,player);return;}

        // This key is an absolute recall. Never wait for the old chunk or old dimension
        // to load: recreate the one reserved identity from its saved companion state here.
        // If the stale disk copy later loads, the uniqueness join gate rejects it.
        if(!materializeRecall(data,player))player.displayClientMessage(Component.literal("Sadaharu could not be recalled."),true);
    }
    private static boolean materializeRecall(CompanionData data,ServerPlayer player) {
        Sadaharu dog=HexSadaharu.DOG.get().create(player.serverLevel());
        if(dog==null||data.dog==null)return false;
        dog.setUUID(data.dog);
        if(!data.memory.isEmpty())dog.readAdditionalSaveData(data.memory.copy());
        if(dog.ownerId()==null&&data.owner!=null)dog.setOwner(data.owner);
        Vec3 p=SafeTravel.recallLanding(player.serverLevel(),player.blockPosition(),dog);
        dog.moveTo(p.x,p.y,p.z,player.getYRot(),0);
        dog.setDeltaMovement(Vec3.ZERO);dog.fallDistance=0;
        if(dog.downed>0)dog.setAct(Act.DOWNED);else {dog.setAct(Act.WAKE);dog.setMood(Mood.EXCITED);}
        if(!player.serverLevel().addFreshEntity(dog))return false;
        data.capture(dog);
        if(dog.downed<=0)dog.voice("excited",.7F);
        return true;
    }
    private static void arrive(Sadaharu dog,ServerPlayer player) {
        Vec3 p=SafeTravel.recallLanding(player.serverLevel(),player.blockPosition(),dog);
        if(!SafeTravel.teleport(dog,player.serverLevel(),p)) {
            CompanionData data=CompanionData.get(player.server);
            if(!dog.isRemoved())dog.rejectDuplicate();
            if(!materializeRecall(data,player))player.displayClientMessage(Component.literal("Sadaharu could not be recalled."),true);
            return;
        }
        Sadaharu recalled=CompanionData.get(player.server).loaded(player.server);
        if(recalled!=null&&recalled.downed>0)recalled.setAct(Act.DOWNED);
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent e) {
        if(e.phase!=TickEvent.Phase.END)return;
        var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();if(server==null)return;
        Iterator<Map.Entry<UUID,Call>> it=calls.entrySet().iterator();
        while(it.hasNext()) {
            var entry=it.next();Call c=entry.getValue();ServerPlayer p=server.getPlayerList().getPlayer(c.player);Sadaharu dog=CompanionData.get(server).loaded(server);
            if(p==null||dog!=null||c.ticks>=120) {
                if(p!=null&&dog!=null)arrive(dog,p);
                else if(p!=null)p.displayClientMessage(Component.literal("Sadaharu’s saved chunk is not ready. Try Call Dog again; no replacement was created."),true);
                c.level.getChunkSource().removeRegionTicket(CALL_TICKET,c.chunk,2,c.dog);it.remove();
            }else entry.setValue(c.next());
        }
    }
    @SubscribeEvent public void stop(ServerStoppedEvent e) {calls.clear();rate.clear();}
}
