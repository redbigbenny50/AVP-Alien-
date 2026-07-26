package com.alien.common.gameplay.entity.living.alien.ovomorph;

import com.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.parasite.facehugger.Facehugger;
import com.alien.common.model.alien.HatchState;
import com.alien.common.registry.init.AlienDataSyncKeys;
import com.alien.common.registry.init.AlienSoundEvents;
import com.alien.common.util.AlienPredicates;
import com.alien.compatibility.avp_human.GeneManagerProxy;
import com.blib.api.common.data_sync.v1.DataAccessor;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class HatchManager {

    // How far from the egg to look for the host that triggered the hatch. An egg delivered to a host chamber sits
    // directly in front of its webbed host, so the hugger should take THAT host immediately.
    private static final double TRIGGERING_HOST_RADIUS = 3.0;

    private final HatchDesireManager hatchDesireManager;

    private final Ovomorph ovomorph;

    private final DataAccessor<Integer> remainingHatchDurationInTicks;

    private final DataAccessor<Integer> remainingSpawnDelayInTicks;

    private final DataAccessor<Integer> spawnCount;

    public HatchManager(Ovomorph ovomorph, int hatchDurationInTicks, int spawnDelayInTicks) {
        this.hatchDesireManager = new HatchDesireManager(ovomorph);
        this.ovomorph = ovomorph;

        this.remainingHatchDurationInTicks = new DataAccessor<>(ovomorph, AlienDataSyncKeys.OVOMORPH_HATCH_DURATION_IN_TICKS.get());
        remainingHatchDurationInTicks.set(hatchDurationInTicks);

        this.remainingSpawnDelayInTicks = new DataAccessor<>(ovomorph, AlienDataSyncKeys.OVOMORPH_REMAINING_SPAWN_DELAY_IN_TICKS.get());
        remainingSpawnDelayInTicks.set(spawnDelayInTicks);

        this.spawnCount = new DataAccessor<>(ovomorph, AlienDataSyncKeys.OVOMORPH_SPAWN_COUNT.get());
    }

    public void tick() {
        hatchDesireManager.tick();

        var level = ovomorph.level();

        if (
            // If the running code is client-side...
            level.isClientSide
                // OR the ovomorph is not alive...
                || !ovomorph.isAlive()
                // OR the ovomorph is dead or dying...
                || ovomorph.isDeadOrDying()
        ) {
            // then return, the ovomorph should never attempt to hatch under any of these conditions.
            return;
        }

        if (isHatching()) {
            remainingHatchDurationInTicks.set(Math.max(remainingHatchDurationInTicks.get() - 1, 0));
        }

        if (!isReadyToSpawnFacehuggers()) {
            return;
        }

        // The ovomorph has fully opened visually at this point, so set its state to hatched.
        ovomorph.setHatchState(HatchState.HATCHED);

        var canSpawnMoreFacehuggers = spawnCount.get() < ovomorph.maxSpawnCount.get();

        if (!canSpawnMoreFacehuggers) {
            return;
        }

        remainingSpawnDelayInTicks.set(Math.max(remainingSpawnDelayInTicks.get() - 1, 0));

        if (remainingSpawnDelayInTicks.get() == 0) {
            spawnFacehugger(level);
            // Reset spawn delay.
            remainingSpawnDelayInTicks.reset();
            // Increment the spawns created.
            spawnCount.set(spawnCount.get() + 1);
        }
    }

    public boolean isReadyToSpawnFacehuggers() {
        return remainingHatchDurationInTicks.get() <= 0;
    }

    public boolean isHatching() {
        return ovomorph.getHatchState().contains(HatchState.HATCHING);
    }

    public boolean isHatched() {
        return ovomorph.getHatchState().contains(HatchState.HATCHED);
    }

    public void hatch() {
        if (isHatching() || isHatched()) {
            // If the ovomorph is hatching or has already hatched, then don't bother trying to hatch again.
            return;
        }

        ovomorph.setHatchState(HatchState.HATCHING);
        ovomorph.level().playSound(null, ovomorph, AlienSoundEvents.ENTITY_OVOMORPH_HATCH.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
    }

    public void restore() {
        spawnCount.reset();
        remainingHatchDurationInTicks.reset();
        remainingSpawnDelayInTicks.reset();
        ovomorph.setHatchState(Ovomorph.DEFAULT_HATCH_STATE);
    }

    public HatchDesireManager getHatchDesireManager() {
        return hatchDesireManager;
    }

    private void spawnFacehugger(Level level) {
        var facehuggerType = Facehugger.getType(ovomorph.getVariant(), ovomorph.isRoyal());

        if (facehuggerType == null) {
            Alien.LOGGER.warn(
                "Failed to get a facehugger type for an ovomorph entity Ovomorph Variant: {}, IsRoyal: {}.",
                ovomorph.getVariant(),
                ovomorph.isRoyal()
            );
            return;
        }

        var facehugger = facehuggerType.create(level);

        if (facehugger == null) {
            Alien.LOGGER.warn("Failed to create facehugger entity.");
            return;
        }

        switch (ovomorph.getGeneManager()) {
            case GeneManagerProxy.EMPTY ignored -> {/* NO-OP */}
            case GeneManagerProxy.Wrapper wrapper -> wrapper.transfer(facehugger.getGeneManager(), false);
        }

        var ovomorphAbovePos = ovomorph.blockPosition().above();
        var ovomorphSuffocatingAboveCheck = ovomorph.level()
            .getBlockState(ovomorphAbovePos)
            .isSuffocating(ovomorph.level(), ovomorphAbovePos);
        // Spawns it at the top of the ovomorph if the above block is not a suffocating block, else spawn at the bottom
        // of ovomorph.
        var ovomorphYPos = ovomorphSuffocatingAboveCheck ? ovomorph.position().y : ovomorph.position().y + ovomorph.getBbHeight();
        facehugger.setPos(ovomorph.position().x, ovomorphYPos, ovomorph.position().z);

        // Explicitly set the yaw and pitch to ensure accurate orientation
        facehugger.setYRot(ovomorph.getYRot());
        facehugger.setXRot(ovomorph.getXRot());

        // Synchronize the visual body rotation.
        facehugger.yBodyRot = ovomorph.yBodyRot; // Body rotation
        facehugger.yHeadRot = ovomorph.yHeadRot; // Head rotation

        // Make sure the facehugger persists after being released from the ovomorph.
        facehugger.setPersistenceRequired();

        // If a viable host is right here - e.g. the webbed host this egg was delivered to - hand it straight to the
        // hugger and leap TOWARD it. Without this the hugger scattered in a random direction with no target and
        // only found the host later via the normal acquisition cycle, which looked wrong.
        var triggeringHost = findTriggeringHost(facehugger);

        // Gives the facehugger a jump like movement if the block above is not a suffocating block.
        if (!ovomorphSuffocatingAboveCheck) {
            if (triggeringHost != null) {
                var toHost = triggeringHost.position().subtract(ovomorph.position());
                var horizontal = new Vec3(toHost.x, 0.0, toHost.z);
                var lunge = horizontal.lengthSqr() > 1.0E-4 ? horizontal.normalize().scale(0.35) : Vec3.ZERO;
                facehugger.setDeltaMovement(lunge.x, 0.7, lunge.z);
            } else {
                facehugger.setDeltaMovement(
                    Mth.nextFloat(facehugger.getRandom(), -0.5f, 0.5f),
                    0.7,
                    Mth.nextFloat(facehugger.getRandom(), -0.5f, 0.5f)
                );
            }
        }

        level.addFreshEntity(facehugger);

        if (triggeringHost != null) {
            facehugger.setTarget(triggeringHost);
        }
    }

    /** The nearest viable host beside the hatching egg (the one that triggered it), or null if none. */
    private LivingEntity findTriggeringHost(com.alien.common.gameplay.entity.living.alien.Alien facehugger) {
        var box = ovomorph.getBoundingBox().inflate(TRIGGERING_HOST_RADIUS);
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (var candidate : ovomorph.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (!AlienPredicates.isFreeHost(facehugger, candidate)) {
                continue;
            }
            double distance = candidate.distanceToSqr(ovomorph);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }
}
