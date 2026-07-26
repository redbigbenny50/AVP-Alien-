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

        ovomorph.setPos(eggLayer.getEggLayingPosition());
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
}
