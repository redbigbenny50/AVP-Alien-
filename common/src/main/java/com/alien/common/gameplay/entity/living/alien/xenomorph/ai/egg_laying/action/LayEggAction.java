package com.alien.common.gameplay.entity.living.alien.xenomorph.ai.egg_laying.action;

import com.alien.common.gameplay.entity.living.alien.ovomorph.Ovomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.egg_laying.EggLayer;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.egg_laying.EggLayingSensors;
import com.alien.common.gameplay.hive.spawning.HiveLocationSpawnGate;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.init.AlienSoundEvents;
import com.alien.compatibility.avp_human.AVPHuman;
import com.alien.compatibility.avp_human.GeneManagerProxy;
import com.human.common.model.GeneCarrier;
import com.human.common.util.GeneIntegrityUtil;
import com.just.ai.goap.action.Action;
import net.minecraft.sounds.SoundSource;

public class LayEggAction {

    public static Action.Signal perform(Action.Context<? extends EggLayer> context) {
        var eggLayer = context.getActor();

        eggLayer.resetEggLayCooldown();

        var level = eggLayer.asEntity().level();
        var variant = shouldBeAberrant(eggLayer) ? AlienVariant.ABERRANT : eggLayer.getVariant();
        var ovomorphType = Ovomorph.getType(variant, false);

        // Physically saturated hive: the queen keeps producing, but the egg goes into the RESERVE bank (up to
        // RESERVE_EGG_CAP) instead of the world - the abstract stock purchases draw on before touching the nurseries.
        var location = HiveLocationSpawnGate.locationContaining(level, eggLayer.asEntity().blockPosition());

        // A physical egg needs BOTH bed/ring room AND a clear spot to sit. If the hive is physically saturated OR her
        // lay spot is choked by un-hauled rooted eggs, produce into the RESERVE bank instead of the world - so a
        // blocked ring banks stock rather than stopping production (and its banking) outright. If the reserve is also
        // full there is genuinely nowhere to put it: abort rather than stack an egg onto a choked spot. [flag for
        // review]
        // A pacified captive breeder is gated differently: her lay spot is a single slot with no hauling drones,
        // so she lays a physical egg ONLY when the zone is clear of any egg (rooted or not), and while it is
        // blocked she banks up to CAPTIVE_RESERVE_EGG_CAP and then stops. This keeps her from stacking eggs
        // endlessly without touching the founding-hive economy below.
        if (eggLayer.isInhibited()) {
            var captiveCanPlace = location != null
                && ovomorphType != null
                && EggLayingSensors.layZoneClearForCaptive(eggLayer);
            if (!captiveCanPlace) {
                if (
                    location != null && ovomorphType != null
                        && EggLayingSensors.hasCaptiveReserveCapacity(eggLayer, location)
                        && location.localReserves().addReturningMember(ovomorphType, 1)
                ) {
                    level.playSound(
                        null,
                        eggLayer.asEntity(),
                        AlienSoundEvents.ENTITY_OVOMORPH_LAID.get(),
                        SoundSource.HOSTILE,
                        1.0F,
                        1.0F
                    );
                    return Action.Signal.CONTINUE;
                }
                return Action.Signal.ABORT;
            }
            // else: fall through to the physical-lay path below (zone is clear).
        }

        var canPlacePhysical = location != null
            && ovomorphType != null
            && (eggLayer.isInhibited()
                ? EggLayingSensors.layZoneClearForCaptive(eggLayer)
                : (EggLayingSensors.hasPhysicalOvomorphCapacity(eggLayer, location)
                    && EggLayingSensors.noEggsNearby(eggLayer)));

        if (!canPlacePhysical) {
            // Non-captive divert to reserve (captive already handled + returned above).
            if (location != null && ovomorphType != null && location.localReserves().addReturningMember(ovomorphType, 1)) {
                level.playSound(null, eggLayer.asEntity(), AlienSoundEvents.ENTITY_OVOMORPH_LAID.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
                return Action.Signal.CONTINUE;
            }
            return Action.Signal.ABORT;
        }

        var ovomorph = ovomorphType == null ? null : ovomorphType.create(level);

        if (ovomorph == null) {
            return Action.Signal.ABORT;
        }

        // SNAP TO THE BLOCK CENTRE. The lay position is a free Vec3 offset from the royal's facing, so it lands
        // wherever the maths puts it - which is almost never a block centre, and an egg is a block-sized thing
        // that reads as misplaced the moment it straddles a seam. Centring on X/Z costs nothing, makes the offset
        // constants only need to be right to within half a block, and applies to every royal rather than being
        // tuned per-model. Y is left exactly as computed so she still lays at the height she was going to.
        var layPosition = eggLayer.getEggLayingPosition();
        // END-STYLE PLACEMENT - [stated] "the aliens though would place the eggs around her": the egg lands on the
        // nearest FREE spot of the hive's own resin around the royal (radius 8), reading as the workers carrying it
        // out onto the creep. No resin nearby yet (a young fortress) falls back to the normal exit spot - resin
        // spread catches up.
        var endLocation = com.alien.common.gameplay.hive.location.HiveLocationRegistry.INSTANCE.getByChunk(
            level.dimension(),
            eggLayer.asEntity().chunkPosition()
        );
        if (endLocation != null && endLocation.isEndStyleHive()) {
            var resinSpot = findFreeResinSpotNear(level, eggLayer.asEntity().blockPosition(), eggLayer.getVariant());
            if (resinSpot != null) {
                layPosition = new net.minecraft.world.phys.Vec3(
                    resinSpot.getX() + 0.5,
                    resinSpot.getY(),
                    resinSpot.getZ() + 0.5
                );
            }
        }
        ovomorph.setPos(
            Math.floor(layPosition.x) + 0.5,
            layPosition.y,
            Math.floor(layPosition.z) + 0.5
        );
        ovomorph.setPersistenceRequired();
        ovomorph.isRooted.set(false);

        switch (eggLayer.getGeneManager()) {
            case GeneManagerProxy.EMPTY ignored -> { /* NO-OP */ }
            case GeneManagerProxy.Wrapper wrapper -> wrapper.transfer(ovomorph.getGeneManager(), false);
        }

        level.playSound(null, eggLayer.asEntity(), AlienSoundEvents.ENTITY_OVOMORPH_LAID.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
        level.addFreshEntity(ovomorph);

        // Parallel banking: every physical lay banks a reserve egg too, so the working stock grows alongside
        // the visible eggs from the very first lay (the first drone has to come from somewhere). Once the
        // physical spots are full, the divert branch above takes over and lays go reserve-only.
        // Parallel banking respects the captive cap for a captive breeder, the full reserve cap otherwise.
        var reserveHasRoom = eggLayer.isInhibited()
            ? EggLayingSensors.hasCaptiveReserveCapacity(eggLayer, location)
            : EggLayingSensors.hasReserveOvomorphCapacity(eggLayer, location);
        if (location != null && reserveHasRoom) {
            location.localReserves().addReturningMember(ovomorphType, 1);
        }

        return Action.Signal.CONTINUE;
    }

    private static boolean shouldBeAberrant(EggLayer eggLayer) {
        if (!AVPHuman.MOD.isLoaded() || !(eggLayer instanceof GeneCarrier geneCarrier)) {
            return false;
        }

        var geneDecayLevel = GeneIntegrityUtil.getGeneDecayLevel(geneCarrier);

        return switch (geneDecayLevel) {
            case FATAL, VOLATILE -> true;
            case STABLE -> false;
            case UNSTABLE -> {
                var totalGeneIntegrity = Math.abs(GeneIntegrityUtil.getTotalGeneticIntegrity(geneCarrier));
                var chance = totalGeneIntegrity - Math.floor(totalGeneIntegrity);

                yield eggLayer.asEntity().getRandom().nextDouble() < chance;
            }
        };
    }

    private LayEggAction() {
        throw new UnsupportedOperationException();
    }

    /** Nearest open, standable cell whose floor is the hive's own resin, spiralling out to radius 8. */
    private static net.minecraft.core.BlockPos findFreeResinSpotNear(
        net.minecraft.world.level.Level level,
        net.minecraft.core.BlockPos center,
        com.alien.common.model.alien.variant.AlienVariant variant
    ) {
        var resinTag = switch (variant) {
            case NORMAL -> com.alien.common.registry.tag.AlienBlockTags.NORMAL_RESIN;
            case NETHER -> com.alien.common.registry.tag.AlienBlockTags.NETHER_RESIN;
            case ABERRANT -> com.alien.common.registry.tag.AlienBlockTags.ABERRANT_RESIN;
            case IRRADIATED -> com.alien.common.registry.tag.AlienBlockTags.IRRADIATED_RESIN;
        };
        for (int r = 1; r <= 8; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue; // ring only - nearest first
                    }
                    for (int dy = -2; dy <= 2; dy++) {
                        var pos = center.offset(dx, dy, dz);
                        if (
                            level.getBlockState(pos.below()).is(resinTag)
                                && level.getBlockState(pos).isAir()
                                && level.getBlockState(pos.above()).isAir()
                        ) {
                            return pos;
                        }
                    }
                }
            }
        }
        return null;
    }
}
