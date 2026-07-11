package com.alien.common.gameplay.hive.structure;

import com.alien.common.model.alien.FreeMob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Plants a delivered host into a host-chamber web spot (see {@link HostChamberSlots}). The captive is placed feet-first
 * inside the authored wall webbing - which slows it, since {@code resin_web} is {@code noCollision} - turned to face
 * the open interior where its egg is later set, and kept from despawning.
 * <p>
 * Players stay mobile-but-slowed (the webbing alone) and can cut themselves free with a tool. Mobs are additionally
 * frozen: {@link Mob#setNoAi(boolean)} halts any brain- or goal-driven mob universally, and
 * {@link FreeMob#removeFreedom()} applies the hive's own incapacitation idiom on top. The freeze is lifted downstream
 * once a facehugger attaches.
 */
public final class HostParking {

    private HostParking() {}

    /** Embed {@code host} at {@code spot}, facing {@code facing}. Server-side. */
    public static void embed(ServerLevel level, LivingEntity host, BlockPos spot, Direction facing) {
        float yaw = facing.toYRot();
        host.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, yaw, 0.0F);
        host.setYBodyRot(yaw);
        host.setYHeadRot(yaw);
        host.setDeltaMovement(Vec3.ZERO);
        host.hasImpulse = true;
        host.fallDistance = 0.0F;

        if (host instanceof Mob mob) {
            mob.setPersistenceRequired();
            mob.setNoAi(true);
            if (mob instanceof FreeMob freeMob) {
                freeMob.removeFreedom();
            }
        }
    }
}
