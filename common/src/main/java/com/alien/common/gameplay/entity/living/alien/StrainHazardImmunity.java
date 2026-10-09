package com.alien.common.gameplay.entity.living.alien;

import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.tag.AlienDamageTypesTags;
import net.minecraft.world.damagesource.DamageSource;

/**
 * Which environmental hazards each STRAIN shrugs off, decided at the damage. One table, read by every alien-side entity
 * that has a strain - the xenomorphs through {@code Alien.isInvulnerableTo}, and the two props that are NOT aliens and
 * inherit none of that: the ovipositors (one entity type shared by every strain, so a tag cannot tell a nether queen's
 * sac from a normal one's - it reads the royal's variant instead) and the royal cocoons.
 * <p>
 * His rulings, Sep 22, verbatim where it matters:
 * <ul>
 * <li>Suffocation (Ad Astra {@code oxygen}, Stellaris {@code oxygen}) - every strain.</li>
 * <li>Extreme cold (Ad Astra deals vanilla {@code freeze}) - "irradiated, normal, and aberrant should be immune to cold
 * and the nether would take the damage."</li>
 * <li>Radiation - "aberrant is the only strain that takes the damage."</li>
 * <li>Acid rain - "all strains are immune to it."</li>
 * <li>Cryo fuel - "irradiated should be the only strain that can handle cryofuel directly."</li>
 * <li>Rocket flames - "nether strains should be the only strain that can handle rocket flames."</li>
 * <li>Being run over - "none are immune."</li>
 * <li>Heat is not here: Ad Astra's heat is vanilla fire, and vanilla's own {@code isInvulnerableTo} already refuses
 * {@code #minecraft:is_fire} for anything whose {@code fireImmune()} is true. That is where nether AND irradiated live
 * ({@code Alien.fireImmune}, {@code RoyalCocoon.fireImmune}; the ovipositor mirrors its royal).</li>
 * </ul>
 * Entity-type tags on the space mods' side still stop the hazard from TICKING at all where a tag can express the rule
 * (see {@code AlienEntityTypeTagProvider.addSpaceHazardImmunities}); this is the damage-side backstop and the only
 * route for the props and for the hazards no tag covers (cryo fuel, rocket flames).
 */
public final class StrainHazardImmunity {

    public static boolean isImmune(AlienVariant variant, DamageSource damageSource) {
        if (damageSource.is(AlienDamageTypesTags.SUFFOCATION) || damageSource.is(AlienDamageTypesTags.ACID_RAIN)) {
            return true;
        }

        if (damageSource.is(AlienDamageTypesTags.RADIATION)) {
            return variant != AlienVariant.ABERRANT;
        }

        if (damageSource.is(AlienDamageTypesTags.EXTREME_COLD)) {
            return variant != AlienVariant.NETHER;
        }

        if (damageSource.is(AlienDamageTypesTags.CRYO_FUEL)) {
            return variant == AlienVariant.IRRADIATED;
        }

        if (damageSource.is(AlienDamageTypesTags.ROCKET_FLAMES)) {
            return variant == AlienVariant.NETHER;
        }

        return false;
    }

    private StrainHazardImmunity() {
        throw new UnsupportedOperationException();
    }
}
