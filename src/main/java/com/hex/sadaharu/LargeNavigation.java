package com.hex.sadaharu;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
/** Vanilla's walk-node evaluator expands every node to the actual 2.15-wide footprint. */
public final class LargeNavigation extends GroundPathNavigation {
    public LargeNavigation(Mob mob,Level level) {super(mob,level);setCanOpenDoors(false);setCanPassDoors(false);setCanFloat(true);}
    @Override public boolean isStableDestination(BlockPos pos) {
        return super.isStableDestination(pos)&&level.noCollision(mob,mob.getDimensions(mob.getPose()).makeBoundingBox(net.minecraft.world.phys.Vec3.atBottomCenterOf(pos)));
    }
}
