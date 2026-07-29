package com.alien.common.gameplay.hive.economy;

import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.tag.AlienEntityTypeTags;

/**
 * The one place that answers "is this hive irradiated, and what does that forbid".
 * <h2>Why a single predicate</h2> Job 2 is mostly a list of subsystems the conversion SWITCHES OFF - production, the
 * vat sync, the egg pipeline, the base tier, expansion. Scattering {@code variant == IRRADIATED} checks through all of
 * them would mean many places to be wrong and many to remember. Everything consults this instead.
 * <h2>The economy in one paragraph</h2> A converted hive is a white dwarf. Its jelly is whatever the two old pools
 * merged into and it will NEVER be topped up; its biomass still comes in from kills. It cannot make anyone new - the
 * four entry castes all come from chestbursters and this strain has none, not even simulated - so every purchase is a
 * PROMOTION of somebody who already exists, at a flat 1 biomass + 1 jelly. Headcount is fixed at conversion and only
 * ever falls.
 */
public final class IrradiatedHiveRules {

    /** [stated] "to evolve members in the reserves the cost is 1 biomass + 1 jelly." Flat, regardless of caste. */
    public static final int PROMOTION_BIOMASS_COST = 1;

    public static final int PROMOTION_JELLY_COST = 1;

    private IrradiatedHiveRules() {
        throw new UnsupportedOperationException();
    }

    public static boolean isIrradiated(HiveLocation location) {
        return location.lineageVariantOrNull() == AlienVariant.IRRADIATED;
    }

    /**
     * Whether this alien belongs to the irradiated strain. The entity-side companion to {@link #isIrradiated}, for the
     * queen rules where there may be no location to ask.
     */
    public static boolean isIrradiated(com.alien.common.gameplay.entity.living.alien.Alien alien) {
        return alien.getVariant() == AlienVariant.IRRADIATED;
    }

    /**
     * How many harbingers a hive may hold: ONE PER RAID CHAMBER.
     * <p>
     * [stated] "the harbinger should still be tied to how many raid chambers the hive has, so that would only be 1 or
     * 2." A base hive has one raid chamber; an empress-influenced blueprint appends a second. The cap used to be a
     * hardcoded 1, which meant the empress addition bought nothing.
     * <p>
     * Not irradiated-specific - this is how every hive should have worked.
     */
    public static int harbingerCap(HiveLocation location) {
        var chambers = 0;

        for (var piece : location.structurePieceByChunk().values()) {
            if (piece.contains("chamber_raid")) {
                chambers++;
            }
        }

        return Math.max(1, chambers);
    }

    /**
     * Whether a converted hive is allowed to make this purchase at all.
     * <p>
     * ONLY PROMOTIONS. A purchase that consumes an OVOMORPH is a birth, and a purchase that consumes NOTHING is a birth
     * from thin air - both are how the four entry castes (drone, runner, spitter, predalien) come into being, and all
     * four route through a chestburster this strain does not have. What is left is exactly the set of purchases that
     * eat an existing member and hand back a better one.
     * <p>
     * This is what stops a finite jelly pool being poured into an unbounded population.
     */
    public static boolean allowsPurchase(HiveUnitPurchase purchase) {
        if (purchase.inputEntities().isEmpty()) {
            return false;
        }

        for (var input : purchase.inputEntities()) {
            if (input.entity().is(AlienEntityTypeTags.OVOMORPHS)) {
                return false;
            }
        }

        return true;
    }
}
