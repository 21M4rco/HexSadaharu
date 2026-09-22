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
        if(dog!=null&&dog.downed>0){player.displayClientMessage(Component.literal("Sadaharu is down; he will pick himself up shortly."),true);return;}
        if(dog!=null){arrive(dog,player);return;}
        ServerLevel from=player.server.getLevel(data.dimension);
        if(from==null){player.displayClientMessage(Component.literal("Sadaharu’s dimension is unavailable."),true);return;}
        if(calls.containsKey(player.getUUID()))return;
        ChunkPos chunk=new ChunkPos(data.position);
        from.getChunkSource().addRegionTicket(CALL_TICKET,chunk,2,data.dog);
        from.getChunk(chunk.x,chunk.z); // Request existing chunk; entity loading completes asynchronously on following ticks.
        calls.put(player.getUUID(),new Call(player.getUUID(),data.dog,from,chunk,0));
    }
    private static void arrive(Sadaharu dog,ServerPlayer player) {
        Vec3 p=SafeTravel.landing(player.serverLevel(),player.blockPosition(),dog);
        if(p==null){player.displayClientMessage(Component.literal("Move to a wider clear space so Sadaharu can arrive safely."),true);return;}
        if(!SafeTravel.teleport(dog,player.serverLevel(),p))player.displayClientMessage(Component.literal("Sadaharu could not cross dimensions here."),true);
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
