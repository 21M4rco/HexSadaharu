package com.hex.sadaharu;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.*;

public final class SafeTravel {
    private static final java.util.Map<java.util.UUID,ServerLevel> transfers=new java.util.HashMap<>();
    public static boolean transferring(java.util.UUID id,ServerLevel target){return transfers.get(id)==target;}
    @Nullable public static Vec3 landing(ServerLevel level, BlockPos near, Entity dog) {
        // Bounded search: feet, full-size clearance and dry solid ground are all checked.
        for(int r=2;r<=10;r+=2) for(int a=0;a<12;a++) {
            double angle=a*Math.PI/6;
            int x=near.getX()+(int)Math.round(Math.cos(angle)*r),z=near.getZ()+(int)Math.round(Math.sin(angle)*r);
            if(!level.getWorldBorder().isWithinBounds(new BlockPos(x,near.getY(),z)))continue;
            for(int dy=3;dy>=-5;dy--) {
                BlockPos p=new BlockPos(x,near.getY()+dy,z);
                Vec3 v=valid(level,p,dog);if(v!=null)return v;
            }
        }
        return null;
    }
    @Nullable private static Vec3 valid(ServerLevel l,BlockPos p,Entity dog) {
        if(p.getY()<l.getMinBuildHeight()+1||p.getY()>l.getMaxBuildHeight()-4)return null;
        if(!l.getBlockState(p.below()).isSolid()||!l.getFluidState(p).isEmpty()||l.getBlockState(p.below()).is(Blocks.MAGMA_BLOCK))return null;
        Vec3 v=Vec3.atBottomCenterOf(p);
        AABB box=dog.getDimensions(dog.getPose()).makeBoundingBox(v);
        if(!l.noCollision(dog,box)||l.containsAnyLiquid(box))return null;
        for(BlockPos q:BlockPos.betweenClosed(p.offset(-1,-1,-1),p.offset(1,-1,1))) if(!l.getBlockState(q).isSolid())return null;
        return v;
    }
    public static boolean teleport(Sadaharu dog, ServerLevel target, Vec3 p) {
        dog.ejectPassengers(); dog.getNavigation().stop();dog.setAct(Act.WAKE);dog.setMood(Mood.EXCITED);
        if(dog.level()!=target) {
            transfers.put(dog.getUUID(),target);
            Entity result;
            try { result=dog.changeDimension(target,new net.minecraftforge.common.util.ITeleporter() {
                @Override public Entity placeEntity(Entity entity,ServerLevel current,ServerLevel dest,float yaw,java.util.function.Function<Boolean,Entity> reposition) {
                    Entity moved=reposition.apply(false);moved.moveTo(p.x,p.y,p.z,yaw,0);return moved;
                }
            }); } finally {transfers.remove(dog.getUUID());}
            if(!(result instanceof Sadaharu s))return false;dog=s;
        } else dog.teleportTo(p.x,p.y,p.z);
        dog.setDeltaMovement(Vec3.ZERO);dog.fallDistance=0;dog.getNavigation().stop();dog.setAct(Act.WAKE);dog.setMood(Mood.EXCITED);
        CompanionData.get(target.getServer()).capture(dog);dog.voice("excited",.7F);return true;
    }
}
