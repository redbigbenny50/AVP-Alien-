package com.alien.common.util;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.key.AlienDamageTypeKeys;

/**
 * The one place that decides what heavy radiation does to a xenomorph.
 * <p>
 * The rule is three-way and has always been the same, whatever the source — a splash of irradiation potion, the fallout
 * biome, or a warhead:
 * <ul>
 * <li>NORMAL and NETHER transmute into IRRADIATED.</li>
 * <li>ABERRANT <b>dies</b>. That strain is the weak line: it is the only one radiation can touch at all, which is
 * exactly why it is the only one that cannot survive a dose this size. Converting an aberrant would hand the weakest
 * strain an upgrade for standing in fallout.</li>
 * <li>IRRADIATED is already bathing in its own element and is left alone.</li>
 * </ul>
 * <p>
 * This lived only inside {@code RadiationResistanceStatusEffect} while the biome tick called
 * {@link AlienTransitionUtil#transitionIntoVariant} directly and unguarded, so the potion killed aberrants and the
 * fallout promoted them. Both now come through here.
 */
public final class AlienIrradiationUtil {

    private AlienIrradiationUtil() {
        throw new UnsupportedOperationException();
    }

    /**
     * Applies the three-way rule. Server-side only; callers already on the client are a no-op via the transition util,
     * and the kill branch checks for itself.
     *
     * @return true when the alien was killed or transmuted, i.e. the caller must not keep using this entity — a
     *         transmute discards it and spawns a replacement.
     */
    public static boolean irradiate(Alien alien) {
        if (alien.level().isClientSide) {
            return false;
        }

        switch (alien.getVariant()) {
            case ABERRANT -> {
                alien.hurt(
                    alien.damageSources().source(AlienDamageTypeKeys.RADIATION_SICKNESS),
                    Float.MAX_VALUE
                );

                return true;
            }
            case NORMAL, NETHER -> {
                AlienTransitionUtil.transitionIntoVariant(alien, AlienVariant.IRRADIATED);

                return true;
            }
            case IRRADIATED -> {
                return false;
            }
        }

        return false;
    }
}
